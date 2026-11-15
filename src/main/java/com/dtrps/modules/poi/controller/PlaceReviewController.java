package com.dtrps.modules.poi.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.poi.dto.PlaceReviewDtos;
import com.dtrps.modules.poi.service.PlaceReviewService;
import com.dtrps.modules.poi.service.ReviewReportService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Đánh giá một địa điểm: công khai xem danh sách, khách đăng nhập chấm 1-5 sao + nhận xét + tối đa
 * 5 ảnh. Gửi một lần, không sửa/xóa lại được.
 */
@RestController
@Validated
@RequestMapping("/api/places/{placeId}/reviews")
public class PlaceReviewController {
  private static final int MAX_PAGE_SIZE = 50;

  private final PlaceReviewService reviewService;
  private final ReviewReportService reportService;

  public PlaceReviewController(
      PlaceReviewService reviewService, ReviewReportService reportService) {
    this.reviewService = reviewService;
    this.reportService = reportService;
  }

  @GetMapping
  public PageResponse<PlaceReviewDtos.ReviewResponse> list(
      @PathVariable Long placeId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return reviewService.list(placeId, PageRequest.of(page, size));
  }

  /** Đánh giá đã có của khách (nếu có) + có được viết đánh giá mới cho địa điểm này không. */
  @GetMapping("/me")
  public PlaceReviewDtos.ReviewStatusResponse status(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long placeId) {
    return reviewService.status(CurrentUser.id(jwt), placeId);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public PlaceReviewDtos.ReviewResponse create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long placeId,
      @RequestParam @Min(1) @Max(5) short rating,
      @RequestParam(required = false) @Size(max = 2000, message = "Nhận xét tối đa 2000 ký tự")
          String comment,
      @RequestParam(value = "photos", required = false) List<MultipartFile> photos) {
    return reviewService.create(CurrentUser.id(jwt), placeId, rating, comment, photos);
  }

  // ===== Báo cáo đánh giá của người khác (cần đăng nhập) =====

  /** Id các đánh giá ở địa điểm này mà người dùng đã báo cáo. */
  @GetMapping("/reported")
  public List<Long> reported(@AuthenticationPrincipal Jwt jwt, @PathVariable Long placeId) {
    return reportService.reportedByMe(CurrentUser.id(jwt), placeId);
  }

  @PostMapping("/{reviewId}/reports")
  @ResponseStatus(HttpStatus.CREATED)
  public void report(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long placeId,
      @PathVariable Long reviewId,
      @Valid @RequestBody PlaceReviewDtos.ReportRequest request) {
    reportService.report(CurrentUser.id(jwt), placeId, reviewId, request.reason(), request.note());
  }
}
