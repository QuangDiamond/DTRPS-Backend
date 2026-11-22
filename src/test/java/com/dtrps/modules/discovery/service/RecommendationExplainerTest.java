package com.dtrps.modules.discovery.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dtrps.modules.discovery.repository.RecommendationCandidateRepository.Candidate;
import com.dtrps.modules.discovery.service.PlaceScorer.ScoreBreakdown;
import com.dtrps.modules.discovery.service.PlaceScorer.ScoredCandidate;
import com.dtrps.modules.discovery.service.PlanProfileResolver.PlanProfile;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationExplainerTest {

  private final RecommendationExplainer explainer = new RecommendationExplainer();

  private static final String CHUA_HUONG =
      "Chùa Hương (Hương Sơn) là quần thể văn hóa – tôn giáo giữa núi rừng đá vôi. Hành trình quen thuộc là đi đò"
          + " dọc suối Yến rồi leo núi hoặc đi cáp treo lên động Hương Tích. Đây là danh lam thắng cảnh và văn hóa"
          + " & lịch sử hợp gia đình, người lớn tuổi, nhóm bạn và cặp đôi, nhịp độ cân bằng.";

  private static PlanProfile profile(List<String> experiences, Set<String> needs, String companion) {
    return new PlanProfile(1L, "Hà Nội", 3, 2, new BigDecimal("6000000"), "VND", "BALANCED",
        List.of("scenicSpot", "heritage"), List.of("Danh lam thắng cảnh", "Di tích & Di sản"),
        companion, List.of(), experiences, needs);
  }

  private static ScoredCandidate scored(BigDecimal price, double budgetScore, boolean strenuous) {
    Candidate c = new Candidate(1L, "Chùa Hương", "Xã Hương Sơn", "scenicSpot", Set.of("scenicSpot", "heritage"),
        "TICKET", price, 20.6, 105.7, 0.52, strenuous, CHUA_HUONG);
    return new ScoredCandidate(c, 0.78, new ScoreBreakdown(0.77, 1.0, budgetScore, 1.0), false);
  }

  @Test
  void explainsCategoryCompanionExperiencesAndCost() {
    var why = explainer.explain(
        scored(BigDecimal.ZERO, 1.0, false),
        profile(List.of("PURPOSE_CULTURE", "PURPOSE_ADVENTURE"), Set.of(), "Cặp đôi"));

    assertThat(why.matchPercent()).isEqualTo(78);
    assertThat(why.matchLabel()).isEqualTo("Rất phù hợp");
    assertThat(why.reasons()).hasSizeLessThanOrEqualTo(4);
    assertThat(why.reasons().get(0)).contains("Danh lam thắng cảnh").contains("Di tích & Di sản");
    assertThat(why.reasons()).contains("Phù hợp cho cặp đôi", "Giúp tìm hiểu văn hóa & lịch sử");
    assertThat(why.cautions()).isEmpty();
  }

  @Test
  void warnsAboutClimbingAndHighCost() {
    var why = explainer.explain(
        scored(new BigDecimal("1200000"), 0.3, true),
        profile(List.of(), Set.of("elderly"), "Gia đình"));

    assertThat(why.reasons()).contains("Phù hợp người lớn tuổi");
    assertThat(why.cautions()).hasSize(2);
    assertThat(why.cautions()).anyMatch(c -> c.contains("leo dốc"));
    assertThat(why.cautions()).anyMatch(c -> c.contains("1.200.000"));
  }
}
