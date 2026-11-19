package com.dtrps.modules.poi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceReview;
import com.dtrps.modules.poi.entity.PlaceReviewReport;
import com.dtrps.modules.poi.repository.PlaceReviewReportRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Unit test ReviewReportService — sheet Poi, UTC033–UTC041 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class ReviewReportServiceTest {
  private static final Long ADMIN_ID = 1L;
  private static final Long AUTHOR_ID = 9L;
  private static final Long REPORTER_ID = 12L;
  private static final Long PLACE_ID = 101L;
  private static final Long REVIEW_ID = 60L;

  @Mock private PlaceReviewRepository reviews;
  @Mock private PlaceReviewReportRepository reports;
  @Mock private UserRepository users;
  @Mock private AdminReviewService adminReviews;
  @Mock private ReviewReportResolver resolver;
  @Mock private AuditService auditService;
  @Mock private NotificationService notifications;

  private ReviewReportService service;
  private PlaceReview review;

  @BeforeEach
  void setUp() {
    service =
        new ReviewReportService(
            reviews, reports, users, adminReviews, resolver, auditService, notifications);
    User author = new User();
    author.setUserId(AUTHOR_ID);
    Place place = new Place();
    place.setPlaceId(PLACE_ID);
    place.setName("Bảo tàng Chăm");
    review = new PlaceReview();
    review.setReviewId(REVIEW_ID);
    review.setUser(author);
    review.setPlace(place);
    lenient().when(reviews.findById(REVIEW_ID)).thenReturn(Optional.of(review));
    lenient().when(users.getReferenceById(any())).thenReturn(new User());
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  @Nested
  @DisplayName("report")
  class Report {
    @Test
    @DisplayName("UTC033 First report of a visible review → saved, admins notified")
    void first_notifiesAdmins() {
      service.report(REPORTER_ID, PLACE_ID, REVIEW_ID, "SPAM", " ");

      ArgumentCaptor<PlaceReviewReport> captor = ArgumentCaptor.forClass(PlaceReviewReport.class);
      verify(reports).save(captor.capture());
      assertThat(captor.getValue().getReason()).isEqualTo("SPAM");
      assertThat(captor.getValue().getNote()).isNull();
      ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
      verify(notifications).notifyAdmins(notice.capture(), isNull());
      assertThat(notice.getValue().type()).isEqualTo("PLACE_REVIEW_REPORTED");
      assertThat(notice.getValue().message()).contains("Spam / quảng cáo");
    }

    @Test
    @DisplayName("UTC034 Reporting one's own review → 403")
    void ownReview_forbidden() {
      ApiException e = error(() -> service.report(AUTHOR_ID, PLACE_ID, REVIEW_ID, "SPAM", null));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(e.getMessage()).isEqualTo("Bạn không thể báo cáo đánh giá của chính mình");
    }

    @Test
    @DisplayName("UTC035 Same user reports twice → 409")
    void twice_conflict() {
      when(reports.existsByReviewReviewIdAndReporterUserId(REVIEW_ID, REPORTER_ID))
          .thenReturn(true);

      ApiException e = error(() -> service.report(REPORTER_ID, PLACE_ID, REVIEW_ID, "FAKE", null));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Bạn đã báo cáo đánh giá này rồi");
      verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("UTC036 Reason OTHER without a note → 400")
    void otherWithoutNote_badRequest() {
      ApiException e = error(() -> service.report(REPORTER_ID, PLACE_ID, REVIEW_ID, "OTHER", ""));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Vui lòng ghi rõ lý do khi chọn \"Khác\"");
    }

    @Test
    @DisplayName("UTC037 Review already HIDDEN → 409")
    void hiddenReview_conflict() {
      review.setStatus("HIDDEN");

      ApiException e = error(() -> service.report(REPORTER_ID, PLACE_ID, REVIEW_ID, "SPAM", null));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Đánh giá này không còn hiển thị");
    }

    @Test
    @DisplayName("Later reports in the same round only refresh the admin counters")
    void laterReport_onlyRefreshes() {
      when(reports.existsByReviewReviewIdAndStatus(REVIEW_ID, "PENDING")).thenReturn(true);

      service.report(REPORTER_ID, PLACE_ID, REVIEW_ID, "SPAM", null);

      verify(notifications, never()).notifyAdmins(any(), any());
      verify(notifications).refreshAdmins(any(Notice.class));
    }
  }

  @Nested
  @DisplayName("resolve")
  class Resolve {
    @BeforeEach
    void pendingReports() {
      lenient()
          .when(reports.existsByReviewReviewIdAndStatus(REVIEW_ID, "PENDING"))
          .thenReturn(true);
    }

    @Test
    @DisplayName("UTC038 HIDE with a reason → review hidden through AdminReviewService")
    void hide_hidesReview() {
      service.resolve(ADMIN_ID, REVIEW_ID, "HIDE", " Spam ");

      verify(adminReviews).changeStatus(ADMIN_ID, REVIEW_ID, "HIDDEN", "Spam");
    }

    @Test
    @DisplayName("UTC039 DISMISS → reports closed, review stays VISIBLE")
    void dismiss_closesReports() {
      when(resolver.resolvePending(review, ADMIN_ID, false)).thenReturn(2);

      service.resolve(ADMIN_ID, REVIEW_ID, "DISMISS", null);

      assertThat(review.getStatus()).isEqualTo("VISIBLE");
      verify(adminReviews, never()).changeStatus(anyLong(), anyLong(), anyString(), anyString());
      verify(auditService)
          .record(eq(ADMIN_ID), anyString(), anyString(), eq(REVIEW_ID), eq("2 báo cáo"));
    }

    @Test
    @DisplayName("UTC040 HIDE without a reason → 400")
    void hideWithoutReason_badRequest() {
      ApiException e = error(() -> service.resolve(ADMIN_ID, REVIEW_ID, "HIDE", ""));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Vui lòng nhập lý do ẩn đánh giá");
      verify(resolver, never()).resolvePending(any(), anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("UTC041 No pending report → 409")
    void noPending_conflict() {
      when(reports.existsByReviewReviewIdAndStatus(REVIEW_ID, "PENDING")).thenReturn(false);

      ApiException e = error(() -> service.resolve(ADMIN_ID, REVIEW_ID, "DISMISS", null));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Đánh giá này không có báo cáo chờ xử lý");
    }
  }
}
