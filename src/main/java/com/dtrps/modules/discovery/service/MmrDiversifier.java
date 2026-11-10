
package com.dtrps.modules.discovery.service;

import com.dtrps.modules.discovery.repository
    .RecommendationCandidateRepository.Candidate;

import com.dtrps.modules.discovery.service
    .PlaceScorer.ScoredCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class MmrDiversifier {

    private static final double LAMBDA = 0.75;
    private static final double EPSILON = 1e-10;

    
public List<ScoredCandidate> diversify(
        List<ScoredCandidate> candidates,
        int limit,
        int needed) {


        if (candidates == null
                || candidates.isEmpty()
                || limit <= 0) {
            return List.of();
        }

        // Không cho một Place xuất hiện nhiều lần.
        Map<Long, ScoredCandidate> unique =
            new LinkedHashMap<>();

        for (ScoredCandidate scored : candidates) {
            if (scored != null
                    && scored.candidate() != null
                    && scored.candidate().placeId() != null) {

                unique.putIfAbsent(
                    scored.candidate().placeId(),
                    scored
                );
            }
        }

        List<ScoredCandidate> remaining =
            new ArrayList<>(unique.values());

        remaining.sort(
            Comparator.comparingDouble(
                ScoredCandidate::score
            ).reversed().thenComparing(
                s -> s.candidate().placeId()
            )
        );

        int target = Math.min(limit, remaining.size());

        // Giới hạn mềm: ưu tiên không lấy quá 40%
        // số địa điểm thuộc cùng loại hình chính.
       
// Tính giới hạn dựa trên số Place chính
// cần cho chuyến đi, không phải tổng dự phòng.
int coreCount = Math.min(
    target,
    Math.max(1, needed)
);

int categoryCap = Math.max(
    2,
    (int) Math.ceil(coreCount * 0.40)
);

        List<ScoredCandidate> selected =
            new ArrayList<>();

        Map<String, Integer> categoryCounts =
            new HashMap<>();

        while (selected.size() < target
                && !remaining.isEmpty()) {

            List<ScoredCandidate> eligible =
                new ArrayList<>();

            for (ScoredCandidate item : remaining) {
                String category =
                    primaryCategory(item.candidate());

                int count = categoryCounts.getOrDefault(
                    category, 0
                );

                if (count < categoryCap) {
                    eligible.add(item);
                }
            }

            // Nếu không còn lựa chọn phù hợp giới hạn,
            // nới giới hạn để vẫn đủ số Place.
            if (eligible.isEmpty()) {
                eligible = remaining;
            }

            ScoredCandidate best = null;
            double bestMmr = Double.NEGATIVE_INFINITY;

            for (ScoredCandidate item : eligible) {

                double mmr = selected.isEmpty()
                    ? item.score()
                    : LAMBDA * item.score()
                      - (1.0 - LAMBDA)
                      * maxSimilarity(item, selected);

                if (best == null
                        || mmr > bestMmr + EPSILON
                        || (Math.abs(mmr - bestMmr) <= EPSILON
                            && item.score() > best.score())) {

                    best = item;
                    bestMmr = mmr;
                }
            }

            if (best == null) {
                break;
            }

            selected.add(best);
            remaining.remove(best);

            String category =
                primaryCategory(best.candidate());

            categoryCounts.merge(category, 1, Integer::sum);
        }

        // Giữ nguyên Weighted Score gốc.
        // Vị trí trong danh sách là thứ hạng sau MMR.
        return List.copyOf(selected);
    }

    private double maxSimilarity(
            ScoredCandidate candidate,
            List<ScoredCandidate> selected) {

        double maximum = 0.0;

        for (ScoredCandidate existing : selected) {
            double similarity = cosine(
                candidate.candidate().placeVector(),
                existing.candidate().placeVector()
            );

            maximum = Math.max(maximum, similarity);
        }

        return maximum;
    }

    private double cosine(float[] a, float[] b) {

        if (a == null
                || b == null
                || a.length == 0
                || a.length != b.length) {
            return 0.0;
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {

            if (!Float.isFinite(a[i])
                    || !Float.isFinite(b[i])) {
                return 0.0;
            }

            dot += (double) a[i] * b[i];

            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }

        double similarity =
            dot / Math.sqrt(normA * normB);

        // Chỉ phạt độ tương đồng dương.
        return Math.max(0.0, Math.min(1.0, similarity));
    }

    private String primaryCategory(Candidate candidate) {

        String code = candidate.primaryCategoryCode();

        if (code != null && !code.isBlank()) {
            return code.trim().toLowerCase(Locale.ROOT);
        }

        if (candidate.categoryCodes() != null) {
            return candidate.categoryCodes().stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toLowerCase(Locale.ROOT))
                .sorted()
                .findFirst()
                .orElse("unknown");
        }

        return "unknown";
    }
}
