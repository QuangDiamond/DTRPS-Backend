package com.dtrps.modules.poi.service;

import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.poi.entity.PlaceReview;
import com.dtrps.modules.poi.entity.PlaceReviewReport;
import com.dtrps.modules.poi.repository.PlaceReviewReportRepository;
import com.dtrps.modules.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Đóng các báo cáo đang chờ của một đánh giá và báo kết quả cho từng người đã báo cáo. Dùng chung
 * khi admin ẩn đánh giá (ở tab nào cũng vậy) và khi admin bỏ qua báo cáo.
 */
@Component
class ReviewReportResolver {
  static final String PENDING = "PENDING";
  static final String RESOLVED = "RESOLVED";
  static final String DISMISSED = "DISMISSED";

  private final PlaceReviewReportRepository reportRepository;
  private final UserRepository userRepository;
  private final NotificationService notifications;
  private final Clock clock;

  ReviewReportResolver(
      PlaceReviewReportRepository reportRepository,
      UserRepository userRepository,
      NotificationService notifications,
      Clock clock) {
    this.reportRepository = reportRepository;
    this.userRepository = userRepository;
    this.notifications = notifications;
    this.clock = clock;
  }

  /**
   * {@code hidden}: true = đánh giá đã bị ẩn (RESOLVED), false = không vi phạm (DISMISSED). Trả số
   * báo cáo đã đóng.
   */
  int resolvePending(PlaceReview review, Long adminId, boolean hidden) {
    List<PlaceReviewReport> pending =
        reportRepository.findByReviewReviewIdAndStatus(review.getReviewId(), PENDING);
    if (pending.isEmpty()) {
      return 0;
    }
    Instant now = clock.instant();
    for (PlaceReviewReport report : pending) {
      report.setStatus(hidden ? RESOLVED : DISMISSED);
      report.setHandledBy(userRepository.getReferenceById(adminId));
      report.setHandledAt(now);
    }
    reportRepository.flush();

    String place = review.getPlace().getName();
    Notice notice =
        new Notice(
            hidden ? "REVIEW_REPORT_RESOLVED" : "REVIEW_REPORT_DISMISSED",
            hidden ? "Báo cáo của bạn đã được xử lý" : "Báo cáo của bạn đã được xem xét",
            hidden
                ? "Đánh giá bạn báo cáo về \"" + place + "\" đã bị ẩn. Cảm ơn bạn đã giúp cộng đồng!"
                : "Đánh giá bạn báo cáo về \""
                    + place
                    + "\" đã được xem xét và không vi phạm quy định.",
            "/explore/" + review.getPlace().getPlaceId(),
            data(review));
    pending.stream()
        .map(report -> report.getReporter().getUserId())
        .distinct()
        .forEach(reporterId -> notifications.notifyUser(reporterId, notice));
    return pending.size();
  }

  static Map<String, Object> data(PlaceReview review) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("reviewId", review.getReviewId());
    data.put("placeId", review.getPlace().getPlaceId());
    data.put("status", review.getStatus());
    return data;
  }
}
