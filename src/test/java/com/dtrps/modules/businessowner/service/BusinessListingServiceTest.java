package com.dtrps.modules.businessowner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.businessowner.dto.BusinessListingRequest;
import com.dtrps.modules.businessowner.dto.BusinessListingResponse;
import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.businessowner.entity.BusinessProfile;
import com.dtrps.modules.businessowner.repository.BusinessListingRepository;
import com.dtrps.modules.businessowner.repository.BusinessProfileRepository;
import com.dtrps.modules.poi.dto.ImageDto;
import com.dtrps.modules.poi.dto.PlaceDetails;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.Province;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.poi.service.PlaceDetailsService;
import com.dtrps.modules.poi.service.PlaceReferenceResolver;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
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

/** Unit test BusinessListingService — sheet BusinessOwner, UTC020–UTC032 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class BusinessListingServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Long OWNER_ID = 7L;
  private static final Long ADMIN_ID = 1L;
  private static final Long LISTING_ID = 11L;
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Mock private BusinessListingRepository listings;
  @Mock private BusinessProfileRepository profiles;
  @Mock private PlaceRepository places;
  @Mock private PlaceReferenceResolver references;
  @Mock private PlaceDetailsService placeDetails;
  @Mock private UserRepository users;
  @Mock private AuditService auditService;
  @Mock private PartnerNotices notices;

  private BusinessListingService service;
  private BusinessProfile business;
  private BusinessListing listing;

  @BeforeEach
  void setUp() {
    service =
        new BusinessListingService(
            listings,
            profiles,
            places,
            references,
            placeDetails,
            users,
            auditService,
            Clock.fixed(NOW, ZoneOffset.UTC),
            notices);
    User owner = new User();
    owner.setUserId(OWNER_ID);
    business = new BusinessProfile();
    business.setBusinessId(3L);
    business.setBusinessName("Cty Du lịch A");
    business.setUser(owner);
    business.setStatus("ACTIVE");

    Place place = new Place();
    place.setPlaceId(101L);
    place.setName("Homestay A");
    place.setStatus("DRAFT");
    listing = new BusinessListing();
    listing.setListingId(LISTING_ID);
    listing.setBusiness(business);
    listing.setPlace(place);
    listing.setStatus("PENDING");

    PlaceCategory homestay = new PlaceCategory();
    homestay.setCategoryId(5L);
    homestay.setCode("homestay");
    homestay.setName("Homestay");
    lenient().when(references.types(any(), any())).thenReturn(List.of(homestay));
    lenient().when(references.province(anyString())).thenReturn(new Province());
    lenient().when(placeDetails.normalize(any(), any(), any())).thenReturn(PlaceDetails.empty());
    lenient().when(placeDetails.read(any())).thenReturn(PlaceDetails.empty());
    lenient().when(profiles.findByUserUserId(OWNER_ID)).thenReturn(Optional.of(business));
    lenient()
        .when(listings.findWithDetailsByListingId(LISTING_ID))
        .thenReturn(Optional.of(listing));
  }

  private static BusinessListingRequest request(
      Double latitude, String website, List<ImageDto> images) {
    return new BusinessListingRequest(
        " Homestay A ",
        null,
        List.of("homestay"),
        "48",
        " 12 Bạch Đằng, Đà Nẵng ",
        null,
        latitude,
        108.2,
        null,
        website,
        null,
        null,
        images);
  }

  private static BusinessListingRequest valid() {
    return request(16.05, null, null);
  }

  private static Set<String> messages(Object request) {
    return VALIDATOR.validate(request).stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  @Nested
  @DisplayName("submit")
  class Submit {
    @Test
    @DisplayName("UTC020 ACTIVE profile: listing PENDING, place DRAFT, admins notified")
    void active_submits() {
      assertThat(messages(valid())).isEmpty();

      BusinessListingResponse response = service.submit(OWNER_ID, valid());

      ArgumentCaptor<BusinessListing> captor = ArgumentCaptor.forClass(BusinessListing.class);
      verify(listings).saveAndFlush(captor.capture());
      BusinessListing saved = captor.getValue();
      assertThat(saved.getStatus()).isEqualTo("PENDING");
      assertThat(saved.getPlace().getStatus()).isEqualTo("DRAFT");
      assertThat(saved.getPlace().getName()).isEqualTo("Homestay A");
      assertThat(saved.getPlace().getAddress()).isEqualTo("12 Bạch Đằng, Đà Nẵng");
      assertThat(response.status()).isEqualTo("PENDING");
      verify(notices).listingSubmitted(saved, false);
    }

    @Test
    @DisplayName("UTC021 Profile PENDING → 403")
    void pendingProfile_forbidden() {
      business.setStatus("PENDING");

      ApiException e = error(() -> service.submit(OWNER_ID, valid()));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(e.getMessage())
          .isEqualTo("Hồ sơ doanh nghiệp của bạn cần được duyệt trước khi đăng ký địa điểm");
      verify(listings, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UTC022 Website without http(s) → 400 (validation)")
    void badWebsite_invalid() {
      assertThat(messages(request(16.05, "abc.vn", null)))
          .containsExactly("Website phải bắt đầu bằng http:// hoặc https://");
    }

    @Test
    @DisplayName("UTC023 Latitude 90.1 → 400 (validation); 90 is accepted")
    void latitudeBoundary() {
      assertThat(messages(request(90.1, null, null))).containsExactly("Vĩ độ phải từ -90 đến 90");
      assertThat(messages(request(90.0, null, null))).isEmpty();
    }

    @Test
    @DisplayName("UTC024 11 images → 400 (validation); 10 are accepted")
    void imagesBoundary() {
      List<ImageDto> eleven =
          IntStream.range(0, 11)
              .mapToObj(i -> new ImageDto("https://res.cloudinary.com/x/" + i + ".jpg", false))
              .toList();
      assertThat(messages(request(16.05, null, eleven)))
          .containsExactly("Tối đa 10 ảnh cho một địa điểm");
      assertThat(messages(request(16.05, null, eleven.subList(0, 10)))).isEmpty();
    }
  }

  @Nested
  @DisplayName("updateOwn")
  class UpdateOwn {
    @Test
    @DisplayName("UTC025 Own REJECTED listing edited → back to PENDING")
    void rejected_resubmits() {
      listing.setStatus("REJECTED");

      BusinessListingResponse response = service.updateOwn(OWNER_ID, LISTING_ID, valid());

      assertThat(listing.getStatus()).isEqualTo("PENDING");
      assertThat(response.status()).isEqualTo("PENDING");
      verify(auditService).record(OWNER_ID, "LISTING_RESUBMITTED", "BusinessListing", LISTING_ID);
      verify(notices).listingSubmitted(listing, true);
    }

    @Test
    @DisplayName("UTC026 Listing APPROVED → 409 (must send a revision)")
    void approved_conflict() {
      listing.setStatus("APPROVED");

      ApiException e = error(() -> service.updateOwn(OWNER_ID, LISTING_ID, valid()));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage())
          .isEqualTo("Địa điểm đã được duyệt — vui lòng gửi đề xuất chỉnh sửa để Admin xét duyệt");
    }

    @Test
    @DisplayName("UTC027 Listing of another partner → 404")
    void otherOwner_notFound() {
      User other = new User();
      other.setUserId(8L);
      BusinessProfile otherBusiness = new BusinessProfile();
      otherBusiness.setUser(other);
      listing.setBusiness(otherBusiness);

      ApiException e = error(() -> service.updateOwn(OWNER_ID, LISTING_ID, valid()));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy địa điểm");
    }
  }

  @Nested
  @DisplayName("approve")
  class Approve {
    @Test
    @DisplayName("UTC028 PENDING listing, business ACTIVE → APPROVED, place ACTIVE, notified")
    void pending_approved() {
      when(users.getReferenceById(ADMIN_ID)).thenReturn(new User());

      service.approve(ADMIN_ID, LISTING_ID);

      assertThat(listing.getStatus()).isEqualTo("APPROVED");
      assertThat(listing.getPlace().getStatus()).isEqualTo("ACTIVE");
      assertThat(listing.getApprovedAt()).isEqualTo(NOW);
      verify(notices).listingDecided(listing, true, null);
    }

    @Test
    @DisplayName("UTC029 Business SUSPENDED → 409")
    void suspendedBusiness_conflict() {
      business.setStatus("SUSPENDED");

      ApiException e = error(() -> service.approve(ADMIN_ID, LISTING_ID));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Doanh nghiệp của địa điểm này đang không hoạt động");
      assertThat(listing.getPlace().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("UTC030 Listing already APPROVED → 409")
    void notPending_conflict() {
      listing.setStatus("APPROVED");

      ApiException e = error(() -> service.approve(ADMIN_ID, LISTING_ID));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Chỉ có thể duyệt hoặc từ chối địa điểm đang chờ duyệt");
    }
  }

  @Nested
  @DisplayName("reject")
  class Reject {
    @Test
    @DisplayName("UTC031 PENDING listing → REJECTED with the reason")
    void pending_rejected() {
      BusinessListingResponse response = service.reject(ADMIN_ID, LISTING_ID, " Ảnh không rõ ");

      assertThat(listing.getStatus()).isEqualTo("REJECTED");
      assertThat(response.statusNote()).isEqualTo("Ảnh không rõ");
      verify(notices).listingDecided(listing, false, "Ảnh không rõ");
    }

    @Test
    @DisplayName("UTC032 Unknown listing → 404")
    void missing_notFound() {
      ApiException e = error(() -> service.reject(ADMIN_ID, 999_999L, "x"));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy địa điểm");
      verify(auditService, never()).record(anyLong(), anyString(), anyString(), anyLong(), eq("x"));
    }
  }
}
