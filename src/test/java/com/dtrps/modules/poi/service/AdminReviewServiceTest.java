package com.dtrps.modules.poi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.itinerary.repository.TripFeedbackRepository;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.poi.dto.AdminReviewDtos;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceReview;
import com.dtrps.modules.poi.repository.PlaceReviewImageRepository;
import com.dtrps.modules.poi.repository.PlaceReviewReportRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import com.dtrps.modules.user.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Unit test AdminReviewService.changeStatus — sheet Poi, UTC030–UTC032 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class AdminReviewServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Long ADMIN_ID = 1L;
  private static final Long AUTHOR_ID = 9L;
  private static final Long REVIEW_ID = 60L;

  @Mock private PlaceReviewRepository reviews;
  @Mock private PlaceReviewImageRepository reviewImages;
  @Mock private TripFeedbackRepository feedback;
  @Mock private PlaceReviewReportRepository reports;
  @Mock private ReviewReportResolver resolver;
  @Mock private AuditService auditService;
  @Mock private NotificationService notifications;

  private AdminReviewService service;
  private PlaceReview review;

  @BeforeEach
  void setUp() {
    service =
        new AdminReviewService(
            reviews,
            reviewImages,
            feedback,
            reports,
            resolver,
            auditService,
            notifications,
            Clock.fixed(NOW, ZoneOffset.UTC));
    User author = new User();
    author.setUserId(AUTHOR_ID);
    Place place = new Place();
    place.setPlaceId(101L);
    place.setName("Bảo tàng Chăm");
    review = new PlaceReview();
    review.setReviewId(REVIEW_ID);
    review.setUser(author);
    review.setPlace(place);
    lenient().when(reviews.findById(REVIEW_ID)).thenReturn(Optional.of(review));
  }

  @Test
  @DisplayName(
      "UTC030 VISIBLE review hidden with a reason: audited, author notified, reports closed")
  void visible_hidden() {
    AdminReviewDtos.PlaceReviewItem item =
        service.changeStatus(ADMIN_ID, REVIEW_ID, "HIDDEN", " Ngôn từ xúc phạm ");

    assertThat(review.getStatus()).isEqualTo("HIDDEN");
    assertThat(item.hiddenReason()).isEqualTo("Ngôn từ xúc phạm");
    verify(auditService)
        .record(
            eq(ADMIN_ID), eq("PLACE_REVIEW_HIDDEN"), any(), eq(REVIEW_ID), eq("Ngôn từ xúc phạm"));
    verify(resolver).resolvePending(review, ADMIN_ID, true);
    ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
    verify(notifications).notifyUser(eq(AUTHOR_ID), captor.capture());
    assertThat(captor.getValue().type()).isEqualTo("REVIEW_HIDDEN");
    assertThat(captor.getValue().message()).endsWith("Lý do: Ngôn từ xúc phạm");
  }

  @Test
  @DisplayName("UTC031 Review already HIDDEN → 409")
  void sameStatus_conflict() {
    review.setStatus("HIDDEN");

    ApiException e =
        catchThrowableOfType(
            () -> service.changeStatus(ADMIN_ID, REVIEW_ID, "HIDDEN", "x"), ApiException.class);

    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(e.getMessage()).isEqualTo("Đánh giá đã ở trạng thái này");
    verify(notifications, never()).notifyUser(anyLong(), any());
  }

  @Test
  @DisplayName("UTC032 Unknown review → 404")
  void missing_notFound() {
    ApiException e =
        catchThrowableOfType(
            () -> service.changeStatus(ADMIN_ID, 999_999L, "HIDDEN", "x"), ApiException.class);

    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(e.getMessage()).isEqualTo("Không tìm thấy đánh giá");
  }

  @Test
  @DisplayName("Restoring a hidden review does not touch reports")
  void restore_keepsReports() {
    review.setStatus("HIDDEN");

    service.changeStatus(ADMIN_ID, REVIEW_ID, "VISIBLE", "Đã xem lại");

    assertThat(review.getStatus()).isEqualTo("VISIBLE");
    verify(resolver, never()).resolvePending(any(), anyLong(), anyBoolean());
  }
}
