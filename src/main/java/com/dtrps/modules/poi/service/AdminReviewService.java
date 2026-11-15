package com.dtrps.modules.poi.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.entity.TripFeedback;
import com.dtrps.modules.itinerary.repository.TripFeedbackRepository;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.poi.dto.AdminReviewDtos;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceReview;
import com.dtrps.modules.poi.entity.PlaceReviewImage;
import com.dtrps.modules.poi.repository.PlaceReviewImageRepository;
import com.dtrps.modules.poi.repository.PlaceReviewReportRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import com.dtrps.modules.user.entity.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý đánh giá của admin.
 *
 * <ul>
 *   <li>Đánh giá địa điểm (công khai): xem mọi trạng thái, ẩn / hiện lại. Mỗi lần đổi bắt buộc có lý
 *       do — lý do lưu trong audit log và gửi tới người viết qua thông báo realtime.
 *   <li>Đánh giá chuyến đi (chỉ khách và admin xem được): chỉ xem.
 * </ul>
 *
 * Mọi thay đổi gửi sự kiện làm mới tới các tab admin khác để danh sách tự cập nhật.
 */
@Service
public class AdminReviewService {
  static final String VISIBLE = "VISIBLE";
  static final String HIDDEN = "HIDDEN";

  static final String AUDIT_ENTITY = "place_reviews";
  static final String ACTION_HIDDEN = "PLACE_REVIEW_HIDDEN";
  static final String ACTION_RESTORED = "PLACE_REVIEW_RESTORED";

  /** Loại sự kiện realtime (FE lọc theo tiền tố) */
  public static final String EVENT_PLACE_REVIEW_CREATED = "PLACE_REVIEW_CREATED";
  public static final String EVENT_TRIP_FEEDBACK_CREATED = "TRIP_FEEDBACK_CREATED";

  static final String REVIEW_NOT_FOUND = "Không tìm thấy đánh giá";
  static final String SAME_STATUS = "Đánh giá đã ở trạng thái này";

  private static final Duration LOW_RATING_WINDOW = Duration.ofDays(7);
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final short LOW_RATING = 2;

  private final PlaceReviewRepository reviewRepository;
  private final PlaceReviewImageRepository imageRepository;
  private final TripFeedbackRepository feedbackRepository;
  private final PlaceReviewReportRepository reportRepository;
  private final ReviewReportResolver reportResolver;
  private final AuditService auditService;
  private final NotificationService notifications;
  private final Clock clock;

  public AdminReviewService(
      PlaceReviewRepository reviewRepository,
      PlaceReviewImageRepository imageRepository,
      TripFeedbackRepository feedbackRepository,
      PlaceReviewReportRepository reportRepository,
      ReviewReportResolver reportResolver,
      AuditService auditService,
      NotificationService notifications,
      Clock clock) {
    this.reviewRepository = reviewRepository;
    this.imageRepository = imageRepository;
    this.feedbackRepository = feedbackRepository;
    this.reportRepository = reportRepository;
    this.reportResolver = reportResolver;
    this.auditService = auditService;
    this.notifications = notifications;
    this.clock = clock;
  }

  // ===== Đánh giá địa điểm =====

  /**
   * Bộ lọc đánh giá địa điểm. {@code keyword}: nhận xét, tên / email người viết, tên địa điểm.
   * {@code minRating}..{@code maxRating}: khoảng số sao. {@code group} / {@code category}: nhóm dịch
   * vụ / loại hình (khớp mọi loại hình của địa điểm). {@code from}..{@code to}: ngày gửi (giờ Việt
   * Nam, gồm cả hai đầu).
   */
  public record PlaceReviewFilter(
      String keyword,
      Integer minRating,
      Integer maxRating,
      String status,
      Long placeId,
      String group,
      String category,
      String province,
      LocalDate from,
      LocalDate to) {}

  /** {@code keyword}: nhận xét, tên / email người viết, tên / điểm đến của chuyến đi. */
  public record TripFeedbackFilter(
      String keyword, Integer minRating, Integer maxRating, LocalDate from, LocalDate to) {}

  @Transactional(readOnly = true)
  public PageResponse<AdminReviewDtos.PlaceReviewItem> placeReviews(
      PlaceReviewFilter filter, Pageable pageable) {
    Specification<PlaceReview> spec = common(filter.minRating(), filter.maxRating(), filter.from(), filter.to());
    String like = likePattern(filter.keyword());
    if (like != null) {
      spec =
          spec.and(
              (root, query, cb) -> {
                Join<PlaceReview, User> user = root.join("user");
                return cb.or(
                    containsIgnoreCase(cb, root.get("comment"), like),
                    containsIgnoreCase(cb, user.get("fullName"), like),
                    containsIgnoreCase(cb, user.get("email"), like),
                    containsIgnoreCase(cb, root.join("place").get("name"), like));
              });
    }
    if (hasText(filter.status())) {
      String wanted = filter.status().trim().toUpperCase(Locale.ROOT);
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), wanted));
    }
    if (filter.placeId() != null) {
      Long placeId = filter.placeId();
      spec = spec.and((root, query, cb) -> cb.equal(root.get("place").get("placeId"), placeId));
    }
    if (hasText(filter.group())) {
      String group = filter.group().trim();
      spec =
          spec.and(
              (root, query, cb) ->
                  PlaceTypeFilter.hasType(root.<PlaceReview, Place>join("place"), query, cb, "parent", group));
    }
    if (hasText(filter.category())) {
      String category = filter.category().trim();
      spec =
          spec.and(
              (root, query, cb) ->
                  PlaceTypeFilter.hasType(root.<PlaceReview, Place>join("place"), query, cb, null, category));
    }
    if (hasText(filter.province())) {
      String province = filter.province().trim();
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.equal(root.get("place").get("province").get("code"), province));
    }

    Page<PlaceReview> page = reviewRepository.findAll(spec, pageable);
    return new PageResponse<>(
        items(page.getContent()), page.getTotalElements(), page.getNumber(), page.getSize());
  }

  /** Đánh giá (đã nạp người viết + địa điểm) kèm ảnh và lý do ẩn — giữ nguyên thứ tự. */
  List<AdminReviewDtos.PlaceReviewItem> items(List<PlaceReview> reviews) {
    Map<Long, List<String>> images = imagesOf(reviews);
    return reviews.stream()
        .map(review -> toItem(review, images.getOrDefault(review.getReviewId(), List.of())))
        .toList();
  }

  /**
   * Ẩn ({@code HIDDEN}) hoặc hiện lại ({@code VISIBLE}) một đánh giá địa điểm. Điểm trung bình của
   * địa điểm chỉ tính đánh giá VISIBLE nên tự cập nhật. Người viết nhận thông báo kèm lý do.
   */
  @Transactional
  public AdminReviewDtos.PlaceReviewItem changeStatus(
      Long adminId, Long reviewId, String status, String reason) {
    PlaceReview review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, REVIEW_NOT_FOUND));
    if (status.equals(review.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, SAME_STATUS);
    }
    String cleanReason = reason.trim();
    boolean hiding = HIDDEN.equals(status);

    review.setStatus(status);
    reviewRepository.flush();
    auditService.record(
        adminId, hiding ? ACTION_HIDDEN : ACTION_RESTORED, AUDIT_ENTITY, reviewId, cleanReason);
    if (hiding) {
      // Đánh giá đang bị báo cáo → đóng báo cáo, báo kết quả cho người báo cáo
      reportResolver.resolvePending(review, adminId, true);
    }

    String placeName = review.getPlace().getName();
    Notice notice =
        new Notice(
            hiding ? "REVIEW_HIDDEN" : "REVIEW_RESTORED",
            hiding ? "Đánh giá của bạn đã bị ẩn" : "Đánh giá của bạn đã được hiển thị lại",
            "Đánh giá của bạn về \""
                + placeName
                + (hiding ? "\" đã bị ẩn. Lý do: " : "\" đã hiển thị lại. Lý do: ")
                + cleanReason,
            "/explore/" + review.getPlace().getPlaceId(),
            reviewData(review));
    notifications.notifyUser(review.getUser().getUserId(), notice);
    notifications.refreshAdmins(
        new Notice(
            hiding ? "PLACE_REVIEW_HIDDEN" : "PLACE_REVIEW_RESTORED",
            notice.title(),
            notice.message(),
            null,
            reviewData(review)));

    List<String> imageUrls =
        imageRepository.findByReviewReviewIdOrderByCreatedAtAsc(reviewId).stream()
            .map(PlaceReviewImage::getImageUrl)
            .toList();
    return toItem(review, imageUrls, hiding ? cleanReason : null);
  }

  // ===== Đánh giá chuyến đi =====

  @Transactional(readOnly = true)
  public PageResponse<AdminReviewDtos.TripFeedbackItem> tripFeedback(
      TripFeedbackFilter filter, Pageable pageable) {
    Specification<TripFeedback> spec = common(filter.minRating(), filter.maxRating(), filter.from(), filter.to());
    String like = likePattern(filter.keyword());
    if (like != null) {
      spec =
          spec.and(
              (root, query, cb) -> {
                Join<TripFeedback, User> user = root.join("user");
                Join<TripFeedback, TravelPlan> plan = root.join("plan");
                return cb.or(
                    containsIgnoreCase(cb, root.get("comment"), like),
                    containsIgnoreCase(cb, user.get("fullName"), like),
                    containsIgnoreCase(cb, user.get("email"), like),
                    containsIgnoreCase(cb, plan.get("title"), like),
                    containsIgnoreCase(cb, plan.get("destination"), like));
              });
    }
    Page<TripFeedback> page = feedbackRepository.findAll(spec, pageable);
    List<AdminReviewDtos.TripFeedbackItem> items =
        page.getContent().stream().map(AdminReviewService::toItem).toList();
    return new PageResponse<>(items, page.getTotalElements(), page.getNumber(), page.getSize());
  }

  // ===== Số liệu =====

  @Transactional(readOnly = true)
  public AdminReviewDtos.Stats stats() {
    long visible = reviewRepository.countByStatus(VISIBLE);
    long hidden = reviewRepository.countByStatus(HIDDEN);
    return new AdminReviewDtos.Stats(
        visible + hidden,
        visible,
        hidden,
        round(reviewRepository.averageRating()),
        reviewRepository.countByRatingLessThanEqualAndCreatedAtAfter(
            LOW_RATING, clock.instant().minus(LOW_RATING_WINDOW)),
        feedbackRepository.count(),
        round(feedbackRepository.averageRating()),
        reportRepository.countPendingReviews());
  }

  // ===== Sự kiện realtime khi khách gửi đánh giá mới =====

  /** Gọi trong transaction tạo đánh giá: danh sách của admin tự cập nhật sau khi commit. */
  public void placeReviewCreated(PlaceReview review) {
    notifications.refreshAdmins(
        new Notice(
            EVENT_PLACE_REVIEW_CREATED,
            "Đánh giá địa điểm mới",
            "Có đánh giá mới cho \"" + review.getPlace().getName() + "\".",
            null,
            reviewData(review)));
  }

  public void tripFeedbackCreated(TripFeedback feedback) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("feedbackId", feedback.getFeedbackId());
    data.put("planId", feedback.getPlan().getPlanId());
    notifications.refreshAdmins(
        new Notice(
            EVENT_TRIP_FEEDBACK_CREATED,
            "Đánh giá chuyến đi mới",
            "Có đánh giá mới cho chuyến đi \"" + feedback.getPlan().getTitle() + "\".",
            null,
            data));
  }

  // ===== Helpers =====

  /** Khoảng số sao + khoảng ngày gửi — dùng chung cho đánh giá địa điểm và chuyến đi. */
  private static <T> Specification<T> common(
      Integer minRating, Integer maxRating, LocalDate from, LocalDate to) {
    Specification<T> spec = (root, query, cb) -> cb.conjunction();
    if (minRating != null) {
      short min = minRating.shortValue();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("rating"), min));
    }
    if (maxRating != null) {
      short max = maxRating.shortValue();
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("rating"), max));
    }
    if (from != null) {
      Instant start = from.atStartOfDay(VIETNAM).toInstant();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
    }
    if (to != null) {
      Instant end = to.plusDays(1).atStartOfDay(VIETNAM).toInstant();
      spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
    }
    return spec;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private Map<Long, List<String>> imagesOf(List<PlaceReview> reviews) {
    List<Long> ids = reviews.stream().map(PlaceReview::getReviewId).toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    return imageRepository.findForReviews(ids).stream()
        .collect(
            Collectors.groupingBy(
                image -> image.getReview().getReviewId(),
                Collectors.mapping(PlaceReviewImage::getImageUrl, Collectors.toList())));
  }

  private AdminReviewDtos.PlaceReviewItem toItem(PlaceReview review, List<String> imageUrls) {
    String reason =
        HIDDEN.equals(review.getStatus())
            ? auditService
                .latestDescription(AUDIT_ENTITY, review.getReviewId(), ACTION_HIDDEN)
                .orElse(null)
            : null;
    return toItem(review, imageUrls, reason);
  }

  private static AdminReviewDtos.PlaceReviewItem toItem(
      PlaceReview review, List<String> imageUrls, String hiddenReason) {
    return new AdminReviewDtos.PlaceReviewItem(
        review.getReviewId(),
        author(review.getUser()),
        new AdminReviewDtos.PlaceRef(review.getPlace().getPlaceId(), review.getPlace().getName()),
        review.getRating(),
        review.getComment(),
        imageUrls,
        review.getStatus(),
        hiddenReason,
        review.getCreatedAt(),
        review.getUpdatedAt());
  }

  private static AdminReviewDtos.TripFeedbackItem toItem(TripFeedback feedback) {
    TravelPlan plan = feedback.getPlan();
    return new AdminReviewDtos.TripFeedbackItem(
        feedback.getFeedbackId(),
        author(feedback.getUser()),
        new AdminReviewDtos.PlanRef(
            plan.getPlanId(),
            plan.getTitle(),
            plan.getDestination(),
            plan.getStartDate(),
            plan.getEndDate()),
        feedback.getRating(),
        feedback.getComment(),
        feedback.getCreatedAt());
  }

  private static AdminReviewDtos.Author author(User user) {
    return new AdminReviewDtos.Author(
        user.getUserId(), user.getFullName(), user.getEmail(), user.getAvatarUrl());
  }

  private static Map<String, Object> reviewData(PlaceReview review) {
    return ReviewReportResolver.data(review);
  }

  private static String likePattern(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
  }

  private static Predicate containsIgnoreCase(
      CriteriaBuilder cb, jakarta.persistence.criteria.Expression<String> field, String like) {
    return cb.like(cb.lower(field), like);
  }

  private static Double round(Double value) {
    return value == null ? null : Math.round(value * 10) / 10.0;
  }
}
