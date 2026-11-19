
package com.dtrps.modules.discovery.service;

import com.dtrps.modules.discovery.repository.RecommendationCandidateRepository.Candidate;
import com.dtrps.modules.discovery.service.PlanProfileResolver.PlanProfile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PlaceScorer {

    private static final double SEMANTIC_WEIGHT = 0.65;
    private static final double CATEGORY_WEIGHT = 0.20;
    private static final double BUDGET_WEIGHT = 0.15;

    // Cosine thực tế giữa câu nhu cầu và mô tả địa điểm nằm khoảng 0,28–0,55
    // (text-embedding-3-small, văn bản tiếng Việt), nên quy đổi 0,25 → 0 và 0,60 → 1.
    private static final double COSINE_LOW = 0.25;
    private static final double COSINE_HIGH = 0.60;
    private static final double NEUTRAL = 0.50;

    /** Nơi phải leo trèo/vận động mạnh khi người dùng là người lớn tuổi, hạn chế vận động, khuyết tật. */
    private static final double MOBILITY_PENALTY = 0.80;

    public ScoredCandidate score(
            Candidate candidate,
            PlanProfile profile) {

        Objects.requireNonNull(candidate, "candidate không được null");
        Objects.requireNonNull(profile, "profile không được null");

        double category = categoryScore(candidate, profile);
        double budget = budgetScore(candidate, profile);

        Double similarity = candidate.cosineSimilarity();

        Double semantic =
            similarity != null && Double.isFinite(similarity)
                ? clamp(
                    (similarity - COSINE_LOW)
                    / (COSINE_HIGH - COSINE_LOW)
                  )
                : null;

        // OpenAI lỗi hoặc thiếu vector:
        // chỉ dùng Category + Budget và chuẩn hóa lại trọng số.
        boolean degraded = semantic == null;

        double total = degraded
            ? (CATEGORY_WEIGHT * category + BUDGET_WEIGHT * budget)
                / (CATEGORY_WEIGHT + BUDGET_WEIGHT)
            : SEMANTIC_WEIGHT * semantic
                + CATEGORY_WEIGHT * category
                + BUDGET_WEIGHT * budget;

        double adjustment =
            profile.limitedMobility() && candidate.strenuous() ? MOBILITY_PENALTY : 1.0;
        total *= adjustment;

        return new ScoredCandidate(
            candidate,
            round4(clamp(total)),
            new ScoreBreakdown(
                semantic == null ? null : round4(semantic),
                round4(category),
                round4(budget),
                adjustment
            ),
            degraded
        );
    }

    private double categoryScore(
            Candidate candidate,
            PlanProfile profile) {

        Set<String> preferred =
            normalized(profile.categoryCodes());

        Set<String> actual =
            normalized(candidate.categoryCodes());

        if (preferred.isEmpty() || actual.isEmpty()) {
            return NEUTRAL;
        }

        long matches = actual.stream()
            .filter(preferred::contains)
            .count();

        return (double) matches / actual.size();
    }

    private Set<String> normalized(Iterable<String> codes) {
        Set<String> result = new HashSet<>();

        if (codes == null) {
            return result;
        }

        for (String code : codes) {
            if (code != null && !code.isBlank()) {
                result.add(
                    code.trim().toLowerCase(Locale.ROOT)
                );
            }
        }

        return result;
    }

    
private double budgetScore(
        Candidate candidate,
        PlanProfile profile) {

    BigDecimal budget = profile.totalBudget();
    BigDecimal price = candidate.referencePrice();

    if (budget == null
            || budget.signum() <= 0
            || profile.groupSize() <= 0
            || profile.days() <= 0
            || !"VND".equalsIgnoreCase(profile.currency())
            || price == null
            || price.signum() < 0) {
        return NEUTRAL;
    }

    String priceType = candidate.priceType();

    if (priceType == null) {
        return NEUTRAL;
    }

    String type = priceType.trim().toUpperCase(Locale.ROOT);
    boolean roomNight = type.equals("ROOM_NIGHT");

    // Vé/bữa ăn/đồ uống tính theo người; giá phòng quy về mỗi người (2 người/phòng).
    if (!roomNight
            && !type.equals("TICKET")
            && !type.equals("AVG_MEAL")
            && !type.equals("AVG_DRINK")) {
        return NEUTRAL;
    }

    // Ngân sách tính theo đầu người/ngày.
    long personDays =
        (long) profile.groupSize() * profile.days();

    BigDecimal dailyBudget = budget.divide(
        BigDecimal.valueOf(personDays),
        8,
        RoundingMode.HALF_UP
    );

    // Giá 0 tạm được xem là miễn phí.
    if (price.signum() == 0) {
        return 1.0;
    }

    if (roomNight) {
        int rooms = (profile.groupSize() + 1) / 2;
        price = price.multiply(BigDecimal.valueOf(rooms))
            .divide(BigDecimal.valueOf(profile.groupSize()), 8, RoundingMode.HALF_UP);
    }

    // Score = dailyBudget / (dailyBudget + price)
    return dailyBudget.divide(
        dailyBudget.add(price),
        8,
        RoundingMode.HALF_UP
    ).doubleValue();
}


  

   

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    public record ScoreBreakdown(
        Double semanticScore,
        double categoryScore,
        double budgetScore,
        /** Hệ số điều chỉnh theo yêu cầu đặc biệt (1 = không đổi, 0,8 = trừ 20%). */
        double adjustment
    ) {}

    public record ScoredCandidate(
        Candidate candidate,
        double score,
        ScoreBreakdown breakdown,
        boolean degraded
    ) {}
}
