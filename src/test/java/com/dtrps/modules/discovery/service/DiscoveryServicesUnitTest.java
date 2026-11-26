package com.dtrps.modules.discovery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.businessowner.entity.BusinessProfile;
import com.dtrps.modules.businessowner.repository.BusinessListingRepository;
import com.dtrps.modules.discovery.config.OpenAiProperties;
import com.dtrps.modules.discovery.entity.PlaceEmbedding;
import com.dtrps.modules.discovery.repository.EmbeddingPlaceRepository;
import com.dtrps.modules.discovery.repository.PlaceEmbeddingRepository;
import com.dtrps.modules.discovery.repository.RecommendationCandidateRepository;
import com.dtrps.modules.discovery.repository.RecommendationCandidateRepository.Candidate;
import com.dtrps.modules.discovery.service.PlaceScorer.ScoreBreakdown;
import com.dtrps.modules.discovery.service.PlaceScorer.ScoredCandidate;
import com.dtrps.modules.discovery.service.PlanProfileResolver.PlanProfile;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class DiscoveryServicesUnitTest {

  // ===== RecommendationService.recommend =====

  private static PlanProfile profile(int days, String pace, Set<String> needs, String companion) {
    return new PlanProfile(
        10L,
        "Đà Nẵng",
        days,
        2,
        new BigDecimal("5000000"),
        "VND",
        pace,
        List.of("scenicSpot"),
        List.of("Danh lam thắng cảnh"),
        companion,
        List.of(),
        List.of(),
        needs);
  }

  private static Candidate candidate(long id, String name, String primaryCat, Set<String> cats) {
    return new Candidate(
        id,
        name,
        "Đà Nẵng",
        primaryCat,
        cats,
        "FREE",
        BigDecimal.ZERO,
        16.05,
        108.20,
        0.85,
        false,
        "Địa điểm du lịch nổi bật tại Đà Nẵng");
  }

  @Test
  void recommend_normalFlow_ranksCandidatesAndWarnsWhenShort() {
    var resolver = mock(PlanProfileResolver.class);
    var queryBuilder = mock(PreferenceQueryBuilder.class);
    var gateway = mock(EmbeddingGateway.class);
    var repo = mock(RecommendationCandidateRepository.class);
    var scorer = mock(PlaceScorer.class);
    var explainer = new RecommendationExplainer();
    var images = mock(PlaceImageRepository.class);

    var prof = profile(2, "BALANCED", Set.of(), "Cặp đôi");
    var c1 = candidate(1L, "Bà Nà Hills", "scenicSpot", Set.of("scenicSpot"));
    var c2 = candidate(2L, "Cầu Rồng", "scenicSpot", Set.of("scenicSpot"));
    when(resolver.resolve(1L, 10L)).thenReturn(prof);
    when(queryBuilder.build(prof)).thenReturn("Đà Nẵng danh lam thắng cảnh");
    when(gateway.embed(any())).thenReturn(new float[] {0.1f, 0.2f});
    when(repo.findCandidates(eq(10L), eq("Đà Nẵng"), anyList(), any())).thenReturn(List.of(c1, c2));
    when(scorer.score(c1, prof))
        .thenReturn(new ScoredCandidate(c1, 0.72, new ScoreBreakdown(0.7, 0.8, 1.0, 1.0), false));
    when(scorer.score(c2, prof))
        .thenReturn(new ScoredCandidate(c2, 0.91, new ScoreBreakdown(0.9, 1.0, 1.0, 1.0), false));
    when(images.findForPlaces(anyCollection())).thenReturn(List.of());

    var service =
        new RecommendationService(resolver, queryBuilder, gateway, repo, scorer, explainer, images);
    var res = service.recommend(1L, 10L);

    assertThat(res.degraded()).isFalse();
    assertThat(res.needed()).isEqualTo(8);
    assertThat(res.available()).isEqualTo(2);
    assertThat(res.items()).hasSize(2);
    assertThat(res.items().get(0).placeId()).isEqualTo(2L);
    assertThat(res.items().get(0).rank()).isEqualTo(1);
    assertThat(res.warnings()).anyMatch(w -> w.contains("Chỉ có 2 địa điểm"));
  }

  @Test
  void recommend_invalidDaysZero_throwsBadRequest() {
    var resolver = mock(PlanProfileResolver.class);
    when(resolver.resolve(1L, 10L)).thenReturn(profile(0, "BALANCED", Set.of(), null));

    var service =
        new RecommendationService(
            resolver,
            mock(PreferenceQueryBuilder.class),
            mock(EmbeddingGateway.class),
            mock(RecommendationCandidateRepository.class),
            mock(PlaceScorer.class),
            new RecommendationExplainer(),
            mock(PlaceImageRepository.class));

    assertThatThrownBy(() -> service.recommend(1L, 10L))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("1 đến 60 ngày");
  }

  @Test
  void recommend_invalidDaysOver60_throwsBadRequest() {
    var resolver = mock(PlanProfileResolver.class);
    when(resolver.resolve(1L, 10L)).thenReturn(profile(61, "BALANCED", Set.of(), null));

    var service =
        new RecommendationService(
            resolver,
            mock(PreferenceQueryBuilder.class),
            mock(EmbeddingGateway.class),
            mock(RecommendationCandidateRepository.class),
            mock(PlaceScorer.class),
            new RecommendationExplainer(),
            mock(PlaceImageRepository.class));

    assertThatThrownBy(() -> service.recommend(1L, 10L))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("1 đến 60 ngày");
  }

  @Test
  void recommend_embeddingUnavailable_fallsBackToRuleOnlyDegradedMode() {
    var resolver = mock(PlanProfileResolver.class);
    var queryBuilder = mock(PreferenceQueryBuilder.class);
    var gateway = mock(EmbeddingGateway.class);
    var repo = mock(RecommendationCandidateRepository.class);
    var scorer = mock(PlaceScorer.class);
    var images = mock(PlaceImageRepository.class);

    var prof = profile(1, "RELAXED", Set.of(), null);
    var c1 = candidate(1L, "Biển Mỹ Khê", "scenicSpot", Set.of("scenicSpot"));
    when(resolver.resolve(1L, 10L)).thenReturn(prof);
    when(queryBuilder.build(prof)).thenReturn("Đà Nẵng");
    when(gateway.embed(any()))
        .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "OpenAI unavailable"));
    when(repo.findRuleOnlyCandidates(eq(10L), eq("Đà Nẵng"), anyList())).thenReturn(List.of(c1));
    when(scorer.score(c1, prof))
        .thenReturn(new ScoredCandidate(c1, 0.75, new ScoreBreakdown(0.0, 1.0, 1.0, 1.0), false));
    when(images.findForPlaces(anyCollection())).thenReturn(List.of());

    var service =
        new RecommendationService(
            resolver, queryBuilder, gateway, repo, scorer, new RecommendationExplainer(), images);
    var res = service.recommend(1L, 10L);

    assertThat(res.degraded()).isTrue();
    assertThat(res.needed()).isEqualTo(3);
    assertThat(res.warnings()).anyMatch(w -> w.contains("Dịch vụ AI tạm thời không khả dụng"));
    verify(repo).findRuleOnlyCandidates(eq(10L), eq("Đà Nẵng"), anyList());
  }

  @Test
  void recommend_withKids_filtersOutBarPubCandidates() {
    var resolver = mock(PlanProfileResolver.class);
    var queryBuilder = mock(PreferenceQueryBuilder.class);
    var gateway = mock(EmbeddingGateway.class);
    var repo = mock(RecommendationCandidateRepository.class);
    var scorer = mock(PlaceScorer.class);
    var images = mock(PlaceImageRepository.class);

    var prof = profile(1, "PACKED", Set.of("kids"), "Gia đình");
    var bar = candidate(1L, "Sky Bar", "barPub", Set.of("barPub"));
    var park = candidate(2L, "Công viên Châu Á", "scenicSpot", Set.of("scenicSpot"));
    when(resolver.resolve(1L, 10L)).thenReturn(prof);
    when(queryBuilder.build(prof)).thenReturn("Đà Nẵng gia đình");
    when(gateway.embed(any())).thenReturn(new float[] {0.1f});
    when(repo.findCandidates(eq(10L), eq("Đà Nẵng"), anyList(), any()))
        .thenReturn(List.of(bar, park));
    when(scorer.score(park, prof))
        .thenReturn(new ScoredCandidate(park, 0.85, new ScoreBreakdown(0.8, 1.0, 1.0, 1.0), false));
    when(images.findForPlaces(anyCollection())).thenReturn(List.of());

    var service =
        new RecommendationService(
            resolver, queryBuilder, gateway, repo, scorer, new RecommendationExplainer(), images);
    var res = service.recommend(1L, 10L);

    assertThat(res.needed()).isEqualTo(6);
    assertThat(res.items()).extracting(RecommendationService.RecommendedPlace::placeId)
        .containsExactly(2L);
  }

  // ===== PlaceEmbeddingService.rebuild & syncPlaces =====

  @Test
  void rebuild_limitOutOfRange_throwsIllegalArgument() {
    var service =
        new PlaceEmbeddingService(
            mock(EmbeddingPlaceRepository.class),
            mock(PlaceEmbeddingRepository.class),
            mock(PlaceEmbeddingTextBuilder.class),
            mock(EmbeddingGateway.class),
            new OpenAiProperties(null, null, null),
            mock(TransactionTemplate.class));

    assertThatThrownBy(() -> service.rebuild(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.rebuild(501)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void syncPlaces_emptyOrNullIds_returnsZeroCounts() {
    var service =
        new PlaceEmbeddingService(
            mock(EmbeddingPlaceRepository.class),
            mock(PlaceEmbeddingRepository.class),
            mock(PlaceEmbeddingTextBuilder.class),
            mock(EmbeddingGateway.class),
            new OpenAiProperties(null, null, null),
            mock(TransactionTemplate.class));

    assertThat(service.syncPlaces(null)).isEqualTo(new PlaceEmbeddingService.RebuildResult(0, 0, 0));
    assertThat(service.syncPlaces(List.of()))
        .isEqualTo(new PlaceEmbeddingService.RebuildResult(0, 0, 0));
  }

  @Test
  void rebuild_skipsBlankDescriptionAndEmbedsValidPlace() {
    var placeRepo = mock(EmbeddingPlaceRepository.class);
    var embRepo = mock(PlaceEmbeddingRepository.class);
    var textBuilder = mock(PlaceEmbeddingTextBuilder.class);
    var gateway = mock(EmbeddingGateway.class);
    var props = new OpenAiProperties("key", "https://api.openai.com/v1", "text-embedding-3-small");
    var tx = mock(TransactionTemplate.class);

    Place pNoDesc = new Place();
    pNoDesc.setPlaceId(1L);
    pNoDesc.setDescription("   ");

    Place pValid = new Place();
    pValid.setPlaceId(2L);
    pValid.setName("Hồ Gươm");
    pValid.setDescription("Hồ Hoàn Kiếm giữa lòng Hà Nội");

    when(placeRepo.findActiveWithCategories()).thenReturn(List.of(pNoDesc, pValid));
    when(embRepo.findByPlacePlaceIdIn(anyList())).thenReturn(List.of());
    when(textBuilder.build(pValid)).thenReturn("Hồ Gươm - Hà Nội");
    when(gateway.embedAll(anyList())).thenReturn(List.of(new float[] {0.5f, 0.6f}));
    doAnswer(
            inv -> {
              Consumer<TransactionStatus> cb = inv.getArgument(0);
              cb.accept(null);
              return null;
            })
        .when(tx)
        .executeWithoutResult(any());

    var service = new PlaceEmbeddingService(placeRepo, embRepo, textBuilder, gateway, props, tx);
    var result = service.rebuild(100);

    assertThat(result.embedded()).isEqualTo(1);
    assertThat(result.skipped()).isEqualTo(0);
    assertThat(result.failed()).isEqualTo(1);
    verify(embRepo).saveAll(anyList());
  }

  // ===== PartnerPlaceService.forPlan =====

  @Test
  void partnerPlaces_planNotFound_throwsNotFound() {
    var plans = mock(TravelPlanRepository.class);
    when(plans.findByPlanIdAndUserUserId(99L, 1L)).thenReturn(Optional.empty());

    var service =
        new PartnerPlaceService(
            plans,
            mock(BusinessListingRepository.class),
            mock(PlaceImageRepository.class),
            mock(PlaceReviewRepository.class));

    assertThatThrownBy(() -> service.forPlan(1L, 99L))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Không tìm thấy kế hoạch du lịch");
  }

  @Test
  void partnerPlaces_blankDestination_returnsEmptyList() {
    var plans = mock(TravelPlanRepository.class);
    TravelPlan plan = new TravelPlan();
    plan.setPlanId(10L);
    plan.setDestination("   ");
    when(plans.findByPlanIdAndUserUserId(10L, 1L)).thenReturn(Optional.of(plan));

    var service =
        new PartnerPlaceService(
            plans,
            mock(BusinessListingRepository.class),
            mock(PlaceImageRepository.class),
            mock(PlaceReviewRepository.class));

    assertThat(service.forPlan(1L, 10L)).isEmpty();
  }

  @Test
  void partnerPlaces_deduplicatesByPlaceAndIncludesRatingAndCover() {
    var plans = mock(TravelPlanRepository.class);
    var listings = mock(BusinessListingRepository.class);
    var images = mock(PlaceImageRepository.class);
    var reviews = mock(PlaceReviewRepository.class);

    TravelPlan plan = new TravelPlan();
    plan.setPlanId(10L);
    plan.setDestination("Đà Nẵng");
    when(plans.findByPlanIdAndUserUserId(10L, 1L)).thenReturn(Optional.of(plan));

    PlaceCategory cat = new PlaceCategory();
    cat.setCode("hotel");
    cat.setName("Khách sạn");

    Place place = new Place();
    place.setPlaceId(50L);
    place.setName("Furama Resort");
    place.setAddress("Võ Nguyên Giáp");
    place.setCategory(cat);

    BusinessProfile bp = new BusinessProfile();
    bp.setBusinessName("Furama Corp");

    BusinessListing l1 = new BusinessListing();
    l1.setPlace(place);
    l1.setBusiness(bp);
    BusinessListing l2 = new BusinessListing();
    l2.setPlace(place);
    l2.setBusiness(bp);

    PlaceImage img = new PlaceImage();
    img.setPlace(place);
    img.setImageUrl("https://cdn.example/furama.jpg");

    when(listings.findVerifiedInProvince("đà nẵng")).thenReturn(List.of(l1, l2));
    when(images.findForPlaces(Set.of(50L))).thenReturn(List.of(img));
    when(reviews.ratingsFor(Set.of(50L)))
        .thenReturn(List.<Object[]>of(new Object[] {50L, 4.76, 12L}));

    var service = new PartnerPlaceService(plans, listings, images, reviews);
    var result = service.forPlan(1L, 10L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).placeId()).isEqualTo(50L);
    assertThat(result.get(0).rating()).isEqualTo(4.8);
    assertThat(result.get(0).reviewCount()).isEqualTo(12L);
    assertThat(result.get(0).coverUrl()).isEqualTo("https://cdn.example/furama.jpg");
    assertThat(result.get(0).verified()).isTrue();
  }
}
