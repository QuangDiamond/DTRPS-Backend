package com.dtrps.modules.poi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.businessowner.repository.BusinessListingRepository;
import com.dtrps.modules.poi.dto.AdminPlaceRequest;
import com.dtrps.modules.poi.dto.ImageDto;
import com.dtrps.modules.poi.dto.OpeningHourDto;
import com.dtrps.modules.poi.dto.PlaceDetails;
import com.dtrps.modules.poi.dto.PriceDto;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.entity.PlacePrice;
import com.dtrps.modules.poi.entity.Province;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlaceOpeningHourRepository;
import com.dtrps.modules.poi.repository.PlacePriceRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Unit test AdminPlaceEditService — sheet Poi, UTC008–UTC023 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class AdminPlaceEditServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Long ADMIN_ID = 1L;
  private static final Long PLACE_ID = 101L;
  private static final String IMAGE = "https://res.cloudinary.com/f3wrjuzb/image/upload/a.jpg";
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Mock private PlaceRepository places;
  @Mock private PlaceImageRepository images;
  @Mock private PlacePriceRepository prices;
  @Mock private PlaceOpeningHourRepository hours;
  @Mock private BusinessListingRepository listings;
  @Mock private PlaceDetailsService placeDetails;
  @Mock private PlaceReferenceResolver references;
  @Mock private AdminPlaceService catalogue;
  @Mock private AuditService auditService;

  private AdminPlaceEditService service;
  private Place place;

  @BeforeEach
  void setUp() {
    service =
        new AdminPlaceEditService(
            places,
            images,
            prices,
            hours,
            listings,
            placeDetails,
            references,
            catalogue,
            auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));
    place = new Place();
    place.setPlaceId(PLACE_ID);
    place.setName("Bảo tàng Chăm");
    place.setStatus("ACTIVE");
    PlaceCategory museum = new PlaceCategory();
    museum.setCategoryId(30L);
    museum.setCode("museum");
    lenient().when(places.findById(PLACE_ID)).thenReturn(Optional.of(place));
    lenient().when(references.types(any(), any())).thenReturn(List.of(museum));
    lenient().when(references.province(anyString())).thenReturn(new Province());
    lenient().when(placeDetails.normalize(any(), any(), any())).thenReturn(PlaceDetails.empty());
    lenient().when(placeDetails.read(any())).thenReturn(PlaceDetails.empty());
    lenient().when(placeDetails.maxImages()).thenReturn(10);
  }

  private static AdminPlaceRequest request(String name, String status) {
    return new AdminPlaceRequest(
        name,
        null,
        List.of("museum"),
        "48",
        " 02 Đường 2 Tháng 9 ",
        null,
        16.06,
        108.22,
        null,
        null,
        status,
        null,
        null,
        null);
  }

  private static List<OpeningHourDto> days(int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(day -> new OpeningHourDto(day, "08:00", "17:00", false))
        .toList();
  }

  private static Set<String> messages(Object request) {
    return VALIDATOR.validate(request).stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  private void partnerOwned() {
    when(listings.existsByPlacePlaceId(PLACE_ID)).thenReturn(true);
  }

  @Nested
  @DisplayName("create")
  class Create {
    @Test
    @DisplayName("UTC008 Valid POI without opening hours → DRAFT")
    void valid_draft() {
      AdminPlaceRequest request = request(" Bảo tàng Chăm ", null);
      assertThat(messages(request)).isEmpty();

      service.create(ADMIN_ID, request);

      ArgumentCaptor<Place> captor = ArgumentCaptor.forClass(Place.class);
      verify(places).save(captor.capture());
      assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
      assertThat(captor.getValue().getName()).isEqualTo("Bảo tàng Chăm");
      assertThat(captor.getValue().getAddress()).isEqualTo("02 Đường 2 Tháng 9");
    }

    @Test
    @DisplayName("UTC009 Opening hours for 6 days only → 400")
    void sixDays_badRequest() {
      when(placeDetails.normalize(any(), any(), any()))
          .thenReturn(new PlaceDetails(days(6), List.of(), List.of()));

      ApiException e = error(() -> service.create(ADMIN_ID, request("Bảo tàng Chăm", null)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage())
          .isEqualTo("Vui lòng gửi giờ mở cửa của cả 7 ngày (Thứ 2 đến Chủ nhật)");
      verify(places, never()).save(any());
    }

    @Test
    @DisplayName("UTC010 status=\"OPEN\" → 400 (validation)")
    void badStatus_invalid() {
      assertThat(messages(request("Bảo tàng Chăm", "OPEN")))
          .containsExactly("Trạng thái phải là ACTIVE, DRAFT, PAUSED hoặc MAINTENANCE");
    }
  }

  @Nested
  @DisplayName("update")
  class Update {
    @Test
    @DisplayName("UTC011 Admin place, new name → saved and audited")
    void adminPlace_saved() {
      service.update(ADMIN_ID, PLACE_ID, request("Bảo tàng Điêu khắc Chăm", null));

      assertThat(place.getName()).isEqualTo("Bảo tàng Điêu khắc Chăm");
      assertThat(place.getStatus()).isEqualTo("ACTIVE");
      assertThat(place.getUpdatedAt()).isEqualTo(NOW);
      verify(auditService).record(ADMIN_ID, "PLACE_UPDATED", "places", PLACE_ID);
    }

    @Test
    @DisplayName("UTC012 Partner's place → 409")
    void partnerPlace_conflict() {
      partnerOwned();

      ApiException e = error(() -> service.update(ADMIN_ID, PLACE_ID, request("X", null)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage())
          .isEqualTo("Địa điểm của đối tác chỉ thay đổi qua bản sửa do đối tác gửi");
      assertThat(place.getName()).isEqualTo("Bảo tàng Chăm");
    }

    @Test
    @DisplayName("UTC013 Unknown place → 404")
    void missing_notFound() {
      ApiException e = error(() -> service.update(ADMIN_ID, 999_999L, request("X", null)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy địa điểm");
    }
  }

  @Nested
  @DisplayName("addImage")
  class AddImage {
    @Test
    @DisplayName("UTC014 Place with 2 images → 3rd image added, not the cover")
    void third_added() {
      when(images.countByPlacePlaceId(PLACE_ID)).thenReturn(2L);

      service.addImage(ADMIN_ID, PLACE_ID, new ImageDto(" " + IMAGE + " ", false));

      ArgumentCaptor<PlaceImage> captor = ArgumentCaptor.forClass(PlaceImage.class);
      verify(images).save(captor.capture());
      assertThat(captor.getValue().getImageUrl()).isEqualTo(IMAGE);
      assertThat(captor.getValue().getIsPrimary()).isFalse();
      verify(images, never()).clearPrimary(any());
    }

    @Test
    @DisplayName("UTC015 Maximum of 10 images reached → 400")
    void full_badRequest() {
      when(images.countByPlacePlaceId(PLACE_ID)).thenReturn(10L);

      ApiException e =
          error(() -> service.addImage(ADMIN_ID, PLACE_ID, new ImageDto(IMAGE, false)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Tối đa 10 ảnh cho một địa điểm");
    }

    @Test
    @DisplayName("UTC016 Image already in the place → 409")
    void duplicate_conflict() {
      when(images.existsByPlacePlaceIdAndImageUrl(PLACE_ID, IMAGE)).thenReturn(true);

      ApiException e =
          error(() -> service.addImage(ADMIN_ID, PLACE_ID, new ImageDto(IMAGE, false)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Ảnh này đã có trong địa điểm");
    }

    @Test
    @DisplayName("UTC017 http:// URL → 400 (validation)")
    void httpUrl_invalid() {
      assertThat(messages(new ImageDto("http://a.jpg", false)))
          .containsExactly("Đường dẫn ảnh phải bắt đầu bằng https://");
    }
  }

  @Nested
  @DisplayName("deleteImage")
  class DeleteImage {
    @Test
    @DisplayName("UTC018 Delete the cover → next image becomes the cover")
    void cover_promotesNext() {
      PlaceImage cover = new PlaceImage();
      cover.setIsPrimary(true);
      PlaceImage next = new PlaceImage();
      next.setIsPrimary(false);
      when(images.findByImageIdAndPlacePlaceId(5L, PLACE_ID)).thenReturn(Optional.of(cover));
      when(images.findFirstByPlacePlaceIdOrderByImageIdAsc(PLACE_ID)).thenReturn(Optional.of(next));

      service.deleteImage(ADMIN_ID, PLACE_ID, 5L);

      verify(images).delete(cover);
      assertThat(next.getIsPrimary()).isTrue();
    }

    @Test
    @DisplayName("UTC019 Unknown image → 404")
    void missing_notFound() {
      ApiException e = error(() -> service.deleteImage(ADMIN_ID, PLACE_ID, 999L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy ảnh");
    }
  }

  @Nested
  @DisplayName("addPrice")
  class AddPrice {
    @Test
    @DisplayName("UTC020 Valid price → added, type upper-cased, VND by default")
    void valid_added() {
      service.addPrice(
          ADMIN_ID, PLACE_ID, new PriceDto(" Vé người lớn ", new BigDecimal("50000"), null, null));

      ArgumentCaptor<PlacePrice> captor = ArgumentCaptor.forClass(PlacePrice.class);
      verify(prices).save(captor.capture());
      assertThat(captor.getValue().getPriceType()).isEqualTo("VÉ NGƯỜI LỚN");
      assertThat(captor.getValue().getCurrency()).isEqualTo("VND");
    }

    @Test
    @DisplayName("UTC021 21st price → 400")
    void twentyFirst_badRequest() {
      when(prices.countByPlacePlaceId(PLACE_ID)).thenReturn(20L);

      ApiException e =
          error(
              () ->
                  service.addPrice(
                      ADMIN_ID, PLACE_ID, new PriceDto("Vé", new BigDecimal("1000"), "VND", null)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Tối đa 20 mức giá");
    }

    @Test
    @DisplayName("UTC022 amount=0 (free) → valid and added")
    void zero_valid() {
      PriceDto free = new PriceDto("Vé trẻ em", BigDecimal.ZERO, "VND", null);
      assertThat(messages(free)).isEmpty();

      service.addPrice(ADMIN_ID, PLACE_ID, free);

      verify(prices).save(any(PlacePrice.class));
    }

    @Test
    @DisplayName("UTC023 amount=-1 → 400 (validation)")
    void negative_invalid() {
      assertThat(messages(new PriceDto("Vé", new BigDecimal("-1"), "VND", null)))
          .containsExactly("Mức giá không được âm");
    }
  }
}
