
package com.dtrps.modules.discovery.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.discovery.repository
    .RecommendationCandidateRepository;
import com.dtrps.modules.discovery.repository
    .RecommendationCandidateRepository.Candidate;
import com.dtrps.modules.discovery.service
    .PlanProfileResolver.PlanProfile;
import com.dtrps.modules.discovery.service
    .PlaceScorer.ScoreBreakdown;
import com.dtrps.modules.discovery.service
    .PlaceScorer.ScoredCandidate;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.repository.PlaceImageRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {

    private static final Logger log =
        LoggerFactory.getLogger(RecommendationService.class);

    private static final double CANDIDATE_MULTIPLIER = 1.5;

    private final PlanProfileResolver profileResolver;
    private final PreferenceQueryBuilder queryBuilder;
    private final EmbeddingGateway embeddingGateway;
    private final RecommendationCandidateRepository candidates;
    private final PlaceScorer scorer;
    private final PlaceImageRepository images;

    public RecommendationService(
            PlanProfileResolver profileResolver,
            PreferenceQueryBuilder queryBuilder,
            EmbeddingGateway embeddingGateway,
            RecommendationCandidateRepository candidates,
            PlaceScorer scorer,
            PlaceImageRepository images) {

        this.profileResolver = profileResolver;
        this.queryBuilder = queryBuilder;
        this.embeddingGateway = embeddingGateway;
        this.candidates = candidates;
        this.scorer = scorer;
        this.images = images;
    }

    public RecommendationResponse recommend(
            Long userId,
            Long planId) {

        // 1. Kiểm tra quyền sở hữu và lấy Travel Plan.
        PlanProfile profile =
            profileResolver.resolve(userId, planId);

        if (profile.days() < 1 || profile.days() > 60) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "Chuyến đi cần từ 1 đến 60 ngày"
            );
        }

        // 2. Xác định số địa điểm cần thiết.
        int perDay = placesPerDay(profile.travelPace());

        int needed = Math.min(
            100,
            profile.days() * perDay
        );

        // 3. Dựng câu truy vấn ngữ nghĩa.
        String queryText = queryBuilder.build(profile);

        boolean degraded = false;
        List<String> warnings = new ArrayList<>();

        List<Candidate> availableCandidates;

        // 4. OpenAI + PostgreSQL Cosine Similarity.
        try {
            float[] queryVector =
                embeddingGateway.embed(queryText);

            availableCandidates =
                candidates.findCandidates(
                    profile.planId(),
                    profile.destination(),
                    profile.lodgingCodes(),
                    queryVector
                );

        } catch (ApiException ex) {

            // Chỉ fallback nếu Gateway AI không hoạt động.
            // Không che giấu lỗi SQL hoặc lỗi lập trình.
            degraded = true;

            log.warn(
                "AI embedding unavailable for planId={}",
                planId
            );

            warnings.add(
                "Dịch vụ AI tạm thời không khả dụng. "
                + "Kết quả được xếp hạng theo loại hình và ngân sách."
            );

            availableCandidates =
                candidates.findRuleOnlyCandidates(
                    profile.planId(),
                    profile.destination(),
                    profile.lodgingCodes()
                );
        }

        // 5. Loại Bar & Pub nếu chắc chắn có trẻ nhỏ.
        if (travellingWithChildren(profile.companionType())) {
            availableCandidates = availableCandidates.stream()
                .filter(c -> !hasCategory(c, "barPub"))
                .toList();
        }

        int available = availableCandidates.size();

        // 6. Weighted Scoring.
        List<ScoredCandidate> scored = new ArrayList<>();

        for (Candidate candidate : availableCandidates) {
            scored.add(scorer.score(candidate, profile));
        }

        scored.sort(
            Comparator.comparingDouble(
                ScoredCandidate::score
            ).reversed().thenComparing(
                s -> s.candidate().placeId()
            )
        );

        // 7. Lấy tối đa 1.5 lần số điểm cần.
        int recommendedCount = Math.min(
            available,
            (int) Math.ceil(
                needed * CANDIDATE_MULTIPLIER
            )
        );

        // 8. Xếp thẳng theo điểm: phù hợp nhất đứng đầu, giảm dần.
        List<ScoredCandidate> ranked =
            scored.subList(0, recommendedCount);

        // 9. Lấy ảnh chính cho các địa điểm.
        List<Long> placeIds = ranked.stream()
            .map(s -> s.candidate().placeId())
            .toList();

        Map<Long, String> imageUrls = new HashMap<>();

        if (!placeIds.isEmpty()) {
            for (PlaceImage image :
                    images.findForPlaces(placeIds)) {

                imageUrls.putIfAbsent(
                    image.getPlace().getPlaceId(),
                    image.getImageUrl()
                );
            }
        }

        // 10. Chuyển thành response cho Frontend.
        List<RecommendedPlace> items = new ArrayList<>();

        for (int i = 0; i < ranked.size(); i++) {

            ScoredCandidate scoredPlace =
                ranked.get(i);

            Candidate candidate =
                scoredPlace.candidate();

            items.add(new RecommendedPlace(
                i + 1,
                candidate.placeId(),
                candidate.name(),
                candidate.address(),
                candidate.primaryCategoryCode(),
                candidate.categoryCodes(),
                imageUrls.get(candidate.placeId()),
                candidate.priceType(),
                candidate.referencePrice(),
                candidate.latitude(),
                candidate.longitude(),
                scoredPlace.score(),
                scoredPlace.breakdown()
            ));
        }

        // 11. Cảnh báo thiếu địa điểm.
        if (available < needed) {
            warnings.add(
                "Chỉ có " + available
                + " địa điểm phù hợp điều kiện lọc, "
                + "trong khi kế hoạch cần khoảng "
                + needed + " địa điểm."
            );
        }

        return new RecommendationResponse(
            profile.planId(),
            needed,
            available,
            degraded,
            List.copyOf(items),
            List.copyOf(warnings)
        );
    }

    private int placesPerDay(String pace) {
        if (pace == null) {
            return 4;
        }

        return switch (pace.toUpperCase()) {
            case "RELAXED" -> 3;
            case "PACKED" -> 6;
            default -> 4;
        };
    }

    private boolean travellingWithChildren(String type) {

        if (type == null) {
            return false;
        }

        String value = type.trim().toLowerCase();

        return value.contains("family_kids")
            || value.contains("family kids")
            || value.contains("with kids")
            || value.contains("có trẻ nhỏ");
    }

    private boolean hasCategory(
            Candidate candidate,
            String categoryCode) {

        return candidate.categoryCodes() != null
            && candidate.categoryCodes().stream()
                .anyMatch(code ->
                    code != null
                    && code.equalsIgnoreCase(categoryCode)
                );
    }

    public record RecommendedPlace(
        int rank,
        Long placeId,
        String name,
        String address,
        String categoryCode,
        Set<String> categoryCodes,
        String imageUrl,
        String priceType,
        BigDecimal referencePrice,
        Double latitude,
        Double longitude,
        double score,
        ScoreBreakdown breakdown
    ) {}

    public record RecommendationResponse(
        Long planId,
        int needed,
        int available,
        boolean degraded,
        List<RecommendedPlace> items,
        List<String> warnings
    ) {}
}
