package com.dtrps.modules.poi.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.poi.dto.AdminReviewDtos;
import com.dtrps.modules.poi.service.AdminReviewService;
import com.dtrps.modules.poi.service.ReviewReportService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý đánh giá: đánh giá địa điểm (ẩn / hiện lại kèm lý do) và đánh giá chuyến đi (chỉ xem).
 * Mặc định mới nhất đứng đầu; {@code sort}: newest, oldest, lowest (sao thấp trước), highest.
 */
@RestController
@Validated
@RequestMapping("/api/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {
  private static final int MAX_PAGE_SIZE = 100;
  private static final String SORT_PATTERN = "^(newest|oldest|lowest|highest)$";
  private static final String SORT_MESSAGE = "Thứ tự phải là newest, oldest, lowest hoặc highest";

  private final AdminReviewService reviewService;
  private final ReviewReportService reportService;

  public AdminReviewController(
      AdminReviewService reviewService, ReviewReportService reportService) {
    this.reviewService = reviewService;
    this.reportService = reportService;
  }

  @GetMapping("/stats")
  public AdminReviewDtos.Stats stats() {
    return reviewService.stats();
  }

  /**
   * {@code minRating}..{@code maxRating}: khoảng số sao. {@code group} / {@code category}: mã nhóm
   * dịch vụ / loại hình. {@code from}..{@code to}: ngày gửi (yyyy-MM-dd, gồm cả hai đầu).
   */
  @GetMapping("/places")
  public PageResponse<AdminReviewDtos.PlaceReviewItem> placeReviews(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) @Min(1) @Max(5) Integer minRating,
      @RequestParam(required = false) @Min(1) @Max(5) Integer maxRating,
      @RequestParam(required = false)
          @Pattern(
              regexp = "^(VISIBLE|HIDDEN)$",
              message = "Trạng thái phải là VISIBLE hoặc HIDDEN")
          String status,
      @RequestParam(required = false) Long placeId,
      @RequestParam(required = false) String group,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String province,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) @Pattern(regexp = SORT_PATTERN, message = SORT_MESSAGE)
          String sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return reviewService.placeReviews(
        new AdminReviewService.PlaceReviewFilter(
            keyword, minRating, maxRating, status, placeId, group, category, province, from, to),
        PageRequest.of(page, size, order(sort, "reviewId")));
  }

  @PutMapping("/places/{reviewId}/status")
  public AdminReviewDtos.PlaceReviewItem changeStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long reviewId,
      @Valid @RequestBody AdminReviewDtos.StatusRequest request) {
    return reviewService.changeStatus(
        CurrentUser.id(jwt), reviewId, request.status(), request.reason());
  }

  @GetMapping("/trips")
  public PageResponse<AdminReviewDtos.TripFeedbackItem> tripFeedback(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) @Min(1) @Max(5) Integer minRating,
      @RequestParam(required = false) @Min(1) @Max(5) Integer maxRating,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) @Pattern(regexp = SORT_PATTERN, message = SORT_MESSAGE)
          String sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return reviewService.tripFeedback(
        new AdminReviewService.TripFeedbackFilter(keyword, minRating, maxRating, from, to),
        PageRequest.of(page, size, order(sort, "feedbackId")));
  }

  // ===== Đánh giá bị người dùng báo cáo =====

  /**
   * Gộp theo đánh giá. {@code status}: PENDING (mặc định), RESOLVED (đã ẩn), DISMISSED (bỏ qua).
   * {@code sort}: most (nhiều lượt báo cáo nhất, mặc định) hoặc newest (báo cáo gần nhất).
   */
  @GetMapping("/reports")
  public PageResponse<AdminReviewDtos.ReportedReviewItem> reports(
      @RequestParam(required = false)
          @Pattern(
              regexp = "^(PENDING|RESOLVED|DISMISSED)$",
              message = "Trạng thái phải là PENDING, RESOLVED hoặc DISMISSED")
          String status,
      @RequestParam(required = false)
          @Pattern(
              regexp = "^(SPAM|OFFENSIVE|INAPPROPRIATE|MISLEADING|FAKE|PRIVACY|OTHER)$",
              message = "Lý do báo cáo không hợp lệ")
          String reason,
      @RequestParam(required = false) Long placeId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false)
          @Pattern(regexp = "^(most|newest)$", message = "Thứ tự phải là most hoặc newest")
          String sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return reportService.list(
        status, reason, placeId, from, to, sort, PageRequest.of(page, size));
  }

  @PutMapping("/reports/{reviewId}")
  public void resolveReports(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long reviewId,
      @Valid @RequestBody AdminReviewDtos.ResolveRequest request) {
    reportService.resolve(CurrentUser.id(jwt), reviewId, request.action(), request.reason());
  }

  /** Luôn kèm createdAt + id để các trang không trùng / sót dòng khi cùng số sao / thời điểm. */
  private static Sort order(String sort, String idField) {
    Sort newest =
        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, idField));
    if (sort == null) return newest;
    return switch (sort) {
      case "oldest" ->
          Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, idField));
      case "lowest" -> Sort.by(Sort.Direction.ASC, "rating").and(newest);
      case "highest" -> Sort.by(Sort.Direction.DESC, "rating").and(newest);
      default -> newest;
    };
  }
}
