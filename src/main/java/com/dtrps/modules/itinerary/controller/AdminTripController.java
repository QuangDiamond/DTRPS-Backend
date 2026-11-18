package com.dtrps.modules.itinerary.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.itinerary.dto.AdminTripDtos;
import com.dtrps.modules.itinerary.service.AdminTripService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Quản lý chuyến đi của mọi người dùng — admin chỉ xem, không sửa. */
@RestController
@Validated
@RequestMapping("/api/admin/trips")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTripController {
  private static final int MAX_PAGE_SIZE = 100;

  private final AdminTripService tripService;

  public AdminTripController(AdminTripService tripService) {
    this.tripService = tripService;
  }

  /**
   * {@code status}: PLANNING (nháp + đã lên kế hoạch), IN_PROGRESS, OVERDUE (đang đi, quá ngày về),
   * COMPLETED, CANCELLED. {@code sort}: newest (mặc định), oldest, startSoon, startLate.
   */
  @GetMapping
  public PageResponse<AdminTripDtos.TripSummary> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false)
          @Pattern(
              regexp = "^(PLANNING|IN_PROGRESS|OVERDUE|COMPLETED|CANCELLED)$",
              message = "Trạng thái chuyến đi không hợp lệ")
          String status,
      @RequestParam(required = false) String destination,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false)
          @Pattern(
              regexp = "^(newest|oldest|startSoon|startLate)$",
              message = "Thứ tự sắp xếp không hợp lệ")
          String sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return tripService.list(
        new AdminTripService.TripFilter(keyword, status, destination, from, to),
        PageRequest.of(page, size, order(sort)));
  }

  @GetMapping("/stats")
  public AdminTripDtos.Stats stats() {
    return tripService.stats();
  }

  /** Các điểm đến đã có chuyến đi, cho ô lọc. */
  @GetMapping("/destinations")
  public List<String> destinations() {
    return tripService.destinations();
  }

  @GetMapping("/{planId}")
  public AdminTripDtos.TripDetail get(@PathVariable Long planId) {
    return tripService.get(planId);
  }

  /** Luôn kèm id để các trang không trùng / sót dòng khi cùng thời điểm. */
  private static Sort order(String sort) {
    Sort byId = Sort.by(Sort.Direction.DESC, "planId");
    if (sort == null) {
      return Sort.by(Sort.Direction.DESC, "createdAt").and(byId);
    }
    return switch (sort) {
      case "oldest" -> Sort.by("createdAt").and(Sort.by("planId"));
      case "startSoon" -> Sort.by("startDate").and(byId);
      case "startLate" -> Sort.by(Sort.Direction.DESC, "startDate").and(byId);
      default -> Sort.by(Sort.Direction.DESC, "createdAt").and(byId);
    };
  }
}
