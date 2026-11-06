
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

    // Ngưỡng thử nghiệm, điều chỉnh sau khi đánh giá.
    private static final double COSINE_LOW = 0.25;
    private static final double COSINE_HIGH = 0.85;
    private static final double NEUTRAL = 0.50;

   

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

        return new ScoredCandidate(
            candidate,
            round4(clamp(total)),
            new ScoreBreakdown(
                semantic == null ? null : round4(semantic),
                round4(category),
                round4(budget)
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

    // Chỉ đánh giá những loại giá phù hợp
    // với danh sách địa điểm tham quan, ăn uống.
    String type = priceType.trim().toUpperCase(Locale.ROOT);

    if (!type.equals("TICKET")
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
        double budgetScore
    ) {}

    public record ScoredCandidate(
        Candidate candidate,
        double score,
        ScoreBreakdown breakdown,
        boolean degraded
    ) {}
}
