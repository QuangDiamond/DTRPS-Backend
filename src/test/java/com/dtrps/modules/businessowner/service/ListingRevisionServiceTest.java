package com.dtrps.modules.businessowner.service;

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
import com.dtrps.modules.businessowner.dto.BusinessListingRequest;
import com.dtrps.modules.businessowner.dto.ListingRevisionResponse;
import com.dtrps.modules.businessowner.dto.RejectRevisionRequest;
import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.businessowner.entity.BusinessListingRevision;
import com.dtrps.modules.businessowner.entity.BusinessProfile;
import com.dtrps.modules.businessowner.repository.BusinessListingRevisionRepository;
import com.dtrps.modules.poi.dto.PlaceDetails;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.Province;
import com.dtrps.modules.poi.service.PlaceDetailsService;
import com.dtrps.modules.poi.service.PlaceReferenceResolver;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Unit test ListingRevisionService — sheet BusinessOwner, UTC033–UTC041 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class ListingRevisionServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Long OWNER_ID = 7L;
  private static final Long ADMIN_ID = 1L;
  private static final Long LISTING_ID = 11L;
  private static final Long REVISION_ID = 40L;
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Mock private BusinessListingService listingService;
  @Mock private BusinessListingRevisionRepository revisions;
  @Mock private PlaceReferenceResolver references;
  @Mock private PlaceDetailsService placeDetails;
  @Mock private UserRepository users;
  @Mock private AuditService auditService;
  @Mock private PartnerNotices notices;

  private ListingRevisionService service;
  private BusinessListing listing;
  private PlaceCategory homestay;

  @BeforeEach
  void setUp() {
    service =
        new ListingRevisionService(
            listingService,
            revisions,
            references,
            placeDetails,
            users,
            auditService,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            notices);
    User owner = new User();
    owner.setUserId(OWNER_ID);
    BusinessProfile business = new BusinessProfile();
    business.setBusinessId(3L);
    business.setBusinessName("Cty Du lịch A");
    business.setUser(owner);
    business.setStatus("ACTIVE");
    Place place = new Place();
    place.setPlaceId(101L);
    place.setName("Homestay A");
    listing = new BusinessListing();
    listing.setListingId(LISTING_ID);
    listing.setBusiness(business);
    listing.setPlace(place);
    listing.setStatus("APPROVED");

    homestay = new PlaceCategory();
    homestay.setCategoryId(5L);
    homestay.setCode("homestay");
    homestay.setName("Homestay");
    lenient().when(listingService.findOwn(OWNER_ID, LISTING_ID)).thenReturn(listing);
    lenient().when(listingService.normalizedDetails(any())).thenReturn(PlaceDetails.empty());
    lenient().when(references.types(any(), any())).thenReturn(List.of(homestay));
    lenient().when(references.province(anyString())).thenReturn(new Province());
    lenient().when(users.getReferenceById(any())).thenReturn(new User());
  }

  private static BusinessListingRequest request() {
    return new BusinessListingRequest(
        " Homestay A mới ",
        null,
        List.of("homestay"),
        "48",
        " 15 Bạch Đằng ",
        null,
        16.05,
        108.2,
        " 0912345678 ",
        null,
        null,
        null,
        null);
  }

  private BusinessListingRevision pendingRevision() {
    BusinessListingRevision revision = new BusinessListingRevision();
    revision.setRevisionId(REVISION_ID);
    revision.setListing(listing);
    revision.setStatus("PENDING");
    revision.setName("Homestay A mới");
    revision.setAddress("15 Bạch Đằng");
    revision.setLatitude(16.06);
    revision.setLongitude(108.21);
    revision.setCategory(homestay);
    revision.setPayload("{\"phone\":\"0912345678\",\"website\":null}");
    lenient()
        .when(revisions.findWithDetailsByRevisionId(REVISION_ID))
        .thenReturn(Optional.of(revision));
    return revision;
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
  @DisplayName("propose")
  class Propose {
    @Test
    @DisplayName("UTC033 APPROVED listing, no pending proposal → revision PENDING, place unchanged")
    void approved_createsRevision() {
      ListingRevisionResponse response = service.propose(OWNER_ID, LISTING_ID, request());

      ArgumentCaptor<BusinessListingRevision> captor =
          ArgumentCaptor.forClass(BusinessListingRevision.class);
      verify(revisions).saveAndFlush(captor.capture());
      BusinessListingRevision saved = captor.getValue();
      assertThat(saved.getStatus()).isEqualTo("PENDING");
      assertThat(saved.getName()).isEqualTo("Homestay A mới");
      assertThat(saved.getPayload()).contains("\"phone\":\"0912345678\"");
      assertThat(listing.getPlace().getName()).isEqualTo("Homestay A");
      assertThat(response.status()).isEqualTo("PENDING");
      verify(notices).revisionSubmitted(saved);
    }

    @Test
    @DisplayName("UTC034 Listing still PENDING → 409")
    void notApproved_conflict() {
      listing.setStatus("PENDING");

      ApiException e = error(() -> service.propose(OWNER_ID, LISTING_ID, request()));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage())
          .isEqualTo("Địa điểm chưa được duyệt — hãy sửa trực tiếp thông tin đăng ký");
    }

    @Test
    @DisplayName("UTC035 One revision already PENDING → 409")
    void pendingExists_conflict() {
      when(revisions.existsByListingListingIdAndStatus(LISTING_ID, "PENDING")).thenReturn(true);

      ApiException e = error(() -> service.propose(OWNER_ID, LISTING_ID, request()));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage())
          .isEqualTo(
              "Đang có một đề xuất chỉnh sửa chờ duyệt — hãy rút lại trước khi gửi đề xuất mới");
      verify(revisions, never()).saveAndFlush(any());
    }
  }

  @Nested
  @DisplayName("approve")
  class Approve {
    @Test
    @DisplayName("UTC036 PENDING revision → content copied to the public place")
    void pending_copiesToPlace() {
      BusinessListingRevision revision = pendingRevision();

      service.approve(ADMIN_ID, REVISION_ID, " ok ");

      Place place = listing.getPlace();
      assertThat(place.getName()).isEqualTo("Homestay A mới");
      assertThat(place.getAddress()).isEqualTo("15 Bạch Đằng");
      assertThat(place.getPhone()).isEqualTo("0912345678");
      assertThat(place.getCategory()).isSameAs(homestay);
      assertThat(revision.getStatus()).isEqualTo("APPROVED");
      assertThat(revision.getReviewedAt()).isEqualTo(NOW);
      assertThat(revision.getReviewNote()).isEqualTo("ok");
      verify(placeDetails).replace(any(Place.class), any(PlaceDetails.class));
      verify(notices).revisionDecided(revision, true, null);
    }

    @Test
    @DisplayName("UTC037 Revision already APPROVED → 409")
    void handled_conflict() {
      pendingRevision().setStatus("APPROVED");

      ApiException e = error(() -> service.approve(ADMIN_ID, REVISION_ID, null));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Đề xuất này đã được xử lý");
      assertThat(listing.getPlace().getName()).isEqualTo("Homestay A");
    }

    @Test
    @DisplayName("UTC038 Unknown revision → 404")
    void missing_notFound() {
      ApiException e = error(() -> service.approve(ADMIN_ID, 999_999L, null));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy đề xuất chỉnh sửa");
    }
  }

  @Nested
  @DisplayName("reject")
  class Reject {
    @Test
    @DisplayName("UTC039 PENDING revision → REJECTED with reason code and feedback")
    void pending_rejected() {
      BusinessListingRevision revision = pendingRevision();
      RejectRevisionRequest request =
          new RejectRevisionRequest("pricing", " Giá chưa đúng ", " x ");
      assertThat(messages(request)).isEmpty();

      ListingRevisionResponse response = service.reject(ADMIN_ID, REVISION_ID, request);

      assertThat(revision.getStatus()).isEqualTo("REJECTED");
      assertThat(revision.getRejectReasonCode()).isEqualTo("pricing");
      assertThat(response.partnerFeedback()).isEqualTo("Giá chưa đúng");
      verify(notices).revisionDecided(revision, false, "Giá chưa đúng");
    }

    @Test
    @DisplayName("UTC040 Unknown reason code → 400 (validation)")
    void badReasonCode_invalid() {
      assertThat(messages(new RejectRevisionRequest("abc", "Giá chưa đúng", "x")))
          .containsExactly("Nhóm lý do không hợp lệ");
    }

    @Test
    @DisplayName("UTC041 Feedback of 2001 characters → 400; 2000 are accepted")
    void feedbackBoundary() {
      assertThat(messages(new RejectRevisionRequest("pricing", "a".repeat(2001), "x")))
          .containsExactly("Góp ý tối đa 2000 ký tự");
      assertThat(messages(new RejectRevisionRequest("pricing", "a".repeat(2000), "x"))).isEmpty();
    }
  }
}
