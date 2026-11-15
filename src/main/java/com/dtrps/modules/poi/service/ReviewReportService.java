package com.dtrps.modules.poi.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.poi.dto.AdminReviewDtos;
import com.dtrps.modules.poi.entity.PlaceReview;
import com.dtrps.modules.poi.entity.PlaceReviewReport;
import com.dtrps.modules.poi.repository.PlaceReviewReportRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Báo cáo đánh giá địa điểm.
 *
 * <ul>
 *   <li>Người dùng báo cáo đánh giá đang hiện của người khác, mỗi người một lần cho mỗi đánh giá.
 *   <li>Admin nhận thông báo ở lượt báo cáo đầu tiên của đánh giá (các lượt sau chỉ làm mới số
 *       đếm), rồi ẩn đánh giá hoặc bỏ qua. Người báo cáo nhận thông báo kết quả.
 *   <li>Không tự ẩn đánh giá dù nhiều người báo cáo — chỉ admin quyết định.
 * </ul>
 */
@Service
public class ReviewReportService {
  static final String VISIBLE = "VISIBLE";
  static final String HIDDEN = "HIDDEN";
  static final String OTHER = "OTHER";
  static final String ACTION_DISMISSED = "PLACE_REVIEW_REPORTS_DISMISSED";

  /** Tên lý do hiện trong thông báo cho admin */
  static final Map<String, String> REASON_LABELS =
      Map.of(
          "SPAM", "Spam / quảng cáo",
          "OFFENSIVE", "Ngôn từ xúc phạm, thù ghét",
          "INAPPROPRIATE", "Nội dung hoặc ảnh phản cảm",
          "MISLEADING", "Sai sự thật / không liên quan",
          "FAKE", "Đánh giá giả",
          "PRIVACY", "Lộ thông tin cá nhân",
          OTHER, "Khác");

  static final String REVIEW_NOT_FOUND = "Không tìm thấy đánh giá";
  static final String NOT_VISIBLE = "Đánh giá này không còn hiển thị";
  static final String OWN_REVIEW = "Bạn không thể báo cáo đánh giá của chính mình";
  static final String ALREADY_REPORTED = "Bạn đã báo cáo đánh giá này rồi";
  static final String NOTE_REQUIRED = "Vui lòng ghi rõ lý do khi chọn \"Khác\"";
  static final String NO_PENDING = "Đánh giá này không có báo cáo chờ xử lý";
  static final String HIDE_REASON_REQUIRED = "Vui lòng nhập lý do ẩn đánh giá";

  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final Instant NO_START = Instant.EPOCH;
  private static final Instant NO_END = LocalDate.of(3000, 1, 1).atStartOfDay(VIETNAM).toInstant();

  private final PlaceReviewRepository reviewRepository;
  private final PlaceReviewReportRepository reportRepository;
  private final UserRepository userRepository;
  private final AdminReviewService adminReviews;
  private final ReviewReportResolver resolver;
  private final AuditService auditService;
  private final NotificationService notifications;

  public ReviewReportService(
      PlaceReviewRepository reviewRepository,
      PlaceReviewReportRepository reportRepository,
      UserRepository userRepository,
      AdminReviewService adminReviews,
      ReviewReportResolver resolver,
      AuditService auditService,
      NotificationService notifications) {
    this.reviewRepository = reviewRepository;
    this.reportRepository = reportRepository;
    this.userRepository = userRepository;
    this.adminReviews = adminReviews;
    this.resolver = resolver;
    this.auditService = auditService;
    this.notifications = notifications;
  }

  // ===== Người dùng =====

  @Transactional
  public void report(Long userId, Long placeId, Long reviewId, String reason, String note) {
    PlaceReview review =
        reviewRepository
            .findById(reviewId)
            .filter(found -> found.getPlace().getPlaceId().equals(placeId))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, REVIEW_NOT_FOUND));
    if (!VISIBLE.equals(review.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, NOT_VISIBLE);
    }
    if (review.getUser().getUserId().equals(userId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, OWN_REVIEW);
    }
    if (reportRepository.existsByReviewReviewIdAndReporterUserId(reviewId, userId)) {
      throw new ApiException(HttpStatus.CONFLICT, ALREADY_REPORTED);
    }
    String cleanNote = note == null || note.isBlank() ? null : note.trim();
    if (OTHER.equals(reason) && cleanNote == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, NOTE_REQUIRED);
    }

    // Lượt đầu tiên của đợt báo cáo mới → thông báo cho admin; các lượt sau chỉ làm mới số đếm
    boolean first = !reportRepository.existsByReviewReviewIdAndStatus(reviewId, ReviewReportResolver.PENDING);

    User reporter = userRepository.getReferenceById(userId);
    PlaceReviewReport report = new PlaceReviewReport();
    report.setReview(review);
    report.setReporter(reporter);
    report.setReason(reason);
    report.setNote(cleanNote);
    reportRepository.save(report);

    String place = review.getPlace().getName();
    Map<String, Object> data = ReviewReportResolver.data(review);
    if (first) {
      notifications.notifyAdmins(
          new Notice(
              "PLACE_REVIEW_REPORTED",
              "Đánh giá bị báo cáo",
              "Một đánh giá về \""
                  + place
                  + "\" bị báo cáo: "
                  + REASON_LABELS.getOrDefault(reason, reason)
                  + ".",
              "/admin/reviews?tab=reports",
              data),
          null);
    } else {
      notifications.refreshAdmins(
          new Notice(
              "PLACE_REVIEW_REPORT_ADDED",
              "Đánh giá bị báo cáo thêm",
              "Thêm một lượt báo cáo đánh giá về \"" + place + "\".",
              null,
              data));
    }
  }

  /** Đánh giá ở địa điểm này mà người dùng đã báo cáo (để hiện "Đã báo cáo"). */
  @Transactional(readOnly = true)
  public List<Long> reportedByMe(Long userId, Long placeId) {
    return reportRepository.reviewIdsReportedBy(userId, placeId);
  }

  // ===== Admin =====

  /**
   * Đánh giá có báo cáo ở trạng thái {@code status}, gộp theo đánh giá. {@code sort}: "newest"
   * (báo cáo gần nhất trước) hoặc mặc định nhiều lượt báo cáo nhất trước.
   */
  @Transactional(readOnly = true)
  public PageResponse<AdminReviewDtos.ReportedReviewItem> list(
      String status,
      String reason,
      Long placeId,
      LocalDate from,
      LocalDate to,
      String sort,
      Pageable pageable) {
    String wanted = status == null || status.isBlank() ? ReviewReportResolver.PENDING : status;
    List<String> reasons =
        reason == null || reason.isBlank() ? List.copyOf(REASON_LABELS.keySet()) : List.of(reason);
    Instant start = from == null ? NO_START : from.atStartOfDay(VIETNAM).toInstant();
    Instant end = to == null ? NO_END : to.plusDays(1).atStartOfDay(VIETNAM).toInstant();
    long place = placeId == null ? 0L : placeId;

    Page<Object[]> page =
        "newest".equals(sort)
            ? reportRepository.groupedByNewest(wanted, reasons, start, end, place, pageable)
            : reportRepository.groupedByMostReports(wanted, reasons, start, end, place, pageable);
    List<Long> ids = page.getContent().stream().map(row -> (Long) row[0]).toList();
    if (ids.isEmpty()) {
      return new PageResponse<>(List.of(), page.getTotalElements(), page.getNumber(), page.getSize());
    }

    Map<Long, PlaceReview> reviews =
        reviewRepository.findByReviewIdIn(ids).stream()
            .collect(Collectors.toMap(PlaceReview::getReviewId, Function.identity()));
    List<PlaceReview> ordered = ids.stream().map(reviews::get).toList();
    Map<Long, AdminReviewDtos.PlaceReviewItem> items =
        adminReviews.items(ordered).stream()
            .collect(Collectors.toMap(AdminReviewDtos.PlaceReviewItem::reviewId, Function.identity()));
    Map<Long, List<PlaceReviewReport>> reportsByReview =
        reportRepository.findByReviewReviewIdInAndStatusOrderByCreatedAtDesc(ids, wanted).stream()
            .filter(report -> reasons.contains(report.getReason()))
            .collect(Collectors.groupingBy(report -> report.getReview().getReviewId()));

    List<AdminReviewDtos.ReportedReviewItem> result =
        page.getContent().stream()
            .map(
                row -> {
                  Long reviewId = (Long) row[0];
                  List<PlaceReviewReport> reports = reportsByReview.getOrDefault(reviewId, List.of());
                  return new AdminReviewDtos.ReportedReviewItem(
                      items.get(reviewId),
                      wanted,
                      (Long) row[1],
                      (Instant) row[2],
                      reasonCounts(reports),
                      reports.stream().map(ReviewReportService::toEntry).toList());
                })
            .toList();
    return new PageResponse<>(result, page.getTotalElements(), page.getNumber(), page.getSize());
  }

  /**
   * {@code HIDE}: ẩn đánh giá (người viết nhận lý do) và đóng báo cáo. {@code DISMISS}: đánh giá
   * không vi phạm, đóng báo cáo. Người báo cáo nhận kết quả trong cả hai trường hợp.
   */
  @Transactional
  public void resolve(Long adminId, Long reviewId, String action, String reason) {
    PlaceReview review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, REVIEW_NOT_FOUND));
    if (!reportRepository.existsByReviewReviewIdAndStatus(reviewId, ReviewReportResolver.PENDING)) {
      throw new ApiException(HttpStatus.CONFLICT, NO_PENDING);
    }
    String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();

    if ("HIDE".equals(action)) {
      if (cleanReason == null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, HIDE_REASON_REQUIRED);
      }
      if (VISIBLE.equals(review.getStatus())) {
        // Ẩn như ở tab đánh giá địa điểm: báo người viết + tự đóng báo cáo
        adminReviews.changeStatus(adminId, reviewId, HIDDEN, cleanReason);
        return;
      }
      // Đã bị ẩn trước đó → chỉ còn đóng báo cáo
      resolver.resolvePending(review, adminId, true);
    } else {
      int closed = resolver.resolvePending(review, adminId, false);
      auditService.record(
          adminId,
          ACTION_DISMISSED,
          AdminReviewService.AUDIT_ENTITY,
          reviewId,
          closed + " báo cáo" + (cleanReason == null ? "" : ": " + cleanReason));
    }
    notifications.refreshAdmins(
        new Notice(
            "PLACE_REVIEW_REPORTS_HANDLED",
            "Báo cáo đánh giá đã được xử lý",
            "Báo cáo về đánh giá ở \"" + review.getPlace().getName() + "\" đã được xử lý.",
            null,
            ReviewReportResolver.data(review)));
  }

  // ===== Helpers =====

  /** Mã lý do → số lượt, nhiều nhất đứng đầu */
  private static Map<String, Long> reasonCounts(List<PlaceReviewReport> reports) {
    return reports.stream()
        .collect(Collectors.groupingBy(PlaceReviewReport::getReason, Collectors.counting()))
        .entrySet()
        .stream()
        .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }

  private static AdminReviewDtos.ReportEntry toEntry(PlaceReviewReport report) {
    User reporter = report.getReporter();
    return new AdminReviewDtos.ReportEntry(
        report.getReportId(),
        new AdminReviewDtos.Author(
            reporter.getUserId(),
            reporter.getFullName(),
            reporter.getEmail(),
            reporter.getAvatarUrl()),
        report.getReason(),
        report.getNote(),
        report.getCreatedAt());
  }
}
