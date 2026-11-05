package com.dtrps.modules.itinerary.controller;

import com.dtrps.modules.itinerary.dto.TravelPlanDtos;
import com.dtrps.modules.itinerary.service.TravelPlanService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Kế hoạch du lịch của người dùng đang đăng nhập. Chỉ chủ kế hoạch thấy và thao tác được. */
@RestController
@RequestMapping("/api/travel-plans")
public class TravelPlanController {

  private final TravelPlanService planService;

  public TravelPlanController(TravelPlanService planService) {
    this.planService = planService;
  }

  /** POST /api/travel-plans: tạo kế hoạch mới, trạng thái DRAFT. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TravelPlanDtos.PlanResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TravelPlanDtos.CreateRequest request) {
    return planService.create(CurrentUser.id(jwt), request);
  }

  /** GET /api/travel-plans: danh sách kế hoạch của tôi, mới nhất trước. */
  @GetMapping
  public List<TravelPlanDtos.PlanResponse> getMyPlans(@AuthenticationPrincipal Jwt jwt) {
    return planService.getMyPlans(CurrentUser.id(jwt));
  }

  /** GET /api/travel-plans/{planId}: chi tiết một kế hoạch. */
  @GetMapping("/{planId}")
  public TravelPlanDtos.PlanResponse getById(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
    return planService.getById(CurrentUser.id(jwt), planId);
  }

  /** PUT /api/travel-plans/{planId}: sửa kế hoạch (chỉ khi DRAFT hoặc PLANNED). */
  @PutMapping("/{planId}")
  public TravelPlanDtos.PlanResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long planId,
      @Valid @RequestBody TravelPlanDtos.UpdateRequest request) {
    return planService.update(CurrentUser.id(jwt), planId, request);
  }

  /** DELETE /api/travel-plans/{planId}: xóa kế hoạch kèm lịch trình (không xóa khi IN_PROGRESS). */
  @DeleteMapping("/{planId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
    planService.delete(CurrentUser.id(jwt), planId);
  }

  /** PATCH /api/travel-plans/{planId}/status: đổi trạng thái theo đúng thứ tự, không đi ngược. */
  @PatchMapping("/{planId}/status")
  public TravelPlanDtos.PlanResponse changeStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long planId,
      @Valid @RequestBody TravelPlanDtos.ChangeStatusRequest request) {
    return planService.changeStatus(CurrentUser.id(jwt), planId, request.status());
  }
}
