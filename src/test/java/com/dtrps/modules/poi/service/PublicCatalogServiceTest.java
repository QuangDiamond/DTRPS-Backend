package com.dtrps.modules.poi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.poi.dto.PlaceDetails;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.Province;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import com.dtrps.modules.poi.repository.ProvinceRepository;
import com.dtrps.modules.poi.repository.SpecialNeedRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PublicCatalogServiceTest {

  @Test
  void placeGet_activePlace_returnsDetailWithRating() {
    var placeRepo = mock(PlaceRepository.class);
    var imgRepo = mock(PlaceImageRepository.class);
    var reviewRepo = mock(PlaceReviewRepository.class);
    var details = mock(PlaceDetailsService.class);

    Place p = new Place();
    p.setPlaceId(1L);
    p.setName("Hồ Gươm");
    p.setStatus("ACTIVE");
    p.setDescription("Trung tâm Hà Nội");

    when(placeRepo.findWithDetailsByPlaceId(1L)).thenReturn(Optional.of(p));
    when(imgRepo.findForPlaces(List.of(1L))).thenReturn(List.of());
    when(reviewRepo.ratingsFor(List.of(1L)))
        .thenReturn(List.<Object[]>of(new Object[] {1L, 4.66, 10L}));
    when(details.read(1L))
        .thenReturn(new PlaceDetails(List.of(), List.of(), List.of()));

    var service = new PlaceService(placeRepo, imgRepo, reviewRepo, details);
    var res = service.get(1L);

    assertThat(res.summary().id()).isEqualTo(1L);
    assertThat(res.summary().rating()).isEqualTo(4.7);
    assertThat(res.summary().reviewCount()).isEqualTo(10L);
  }

  @Test
  void placeGet_draftOrMissing_throwsNotFound() {
    var placeRepo = mock(PlaceRepository.class);
    Place draft = new Place();
    draft.setPlaceId(2L);
    draft.setStatus("DRAFT");
    when(placeRepo.findWithDetailsByPlaceId(2L)).thenReturn(Optional.of(draft));
    when(placeRepo.findWithDetailsByPlaceId(99L)).thenReturn(Optional.empty());

    var service =
        new PlaceService(
            placeRepo,
            mock(PlaceImageRepository.class),
            mock(PlaceReviewRepository.class),
            mock(PlaceDetailsService.class));

    assertThatThrownBy(() -> service.get(2L))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining(PlaceService.PLACE_NOT_FOUND);
    assertThatThrownBy(() -> service.get(99L))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining(PlaceService.PLACE_NOT_FOUND);
  }

  @Test
  void referenceData_provincesSortedVietnameseAndCategoriesGroupedInOrder() {
    var provinceRepo = mock(ProvinceRepository.class);
    var catRepo = mock(PlaceCategoryRepository.class);
    var needRepo = mock(SpecialNeedRepository.class);

    Province hn = new Province();
    hn.setProvinceId(1L);
    hn.setCode("ha-noi");
    hn.setName("Hà Nội");
    Province dn = new Province();
    dn.setProvinceId(2L);
    dn.setCode("da-nang");
    dn.setName("Đà Nẵng");
    when(provinceRepo.findAll()).thenReturn(List.of(hn, dn));

    PlaceCategory fnb = new PlaceCategory();
    fnb.setCategoryId(10L);
    fnb.setCode("fnb");
    fnb.setName("Ăn uống");
    PlaceCategory cafe = new PlaceCategory();
    cafe.setCategoryId(11L);
    cafe.setCode("cafe");
    cafe.setName("Cà phê");
    cafe.setParent(fnb);

    PlaceCategory lodging = new PlaceCategory();
    lodging.setCategoryId(20L);
    lodging.setCode("lodging");
    lodging.setName("Lưu trú");
    PlaceCategory hotel = new PlaceCategory();
    hotel.setCategoryId(21L);
    hotel.setCode("hotel");
    hotel.setName("Khách sạn");
    hotel.setParent(lodging);

    PlaceCategory emptyGroup = new PlaceCategory();
    emptyGroup.setCategoryId(30L);
    emptyGroup.setCode("shopping");
    emptyGroup.setName("Mua sắm");

    when(catRepo.findByStatusOrderByCategoryIdAsc("ACTIVE"))
        .thenReturn(List.of(fnb, cafe, lodging, hotel, emptyGroup));

    var service = new ReferenceDataService(provinceRepo, catRepo, needRepo);

    assertThat(service.provinces()).extracting(p -> p.code()).containsExactly("da-nang", "ha-noi");
    assertThat(service.categories())
        .extracting(g -> g.code())
        .containsExactly("lodging", "fnb");
  }
}
