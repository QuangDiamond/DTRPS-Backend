package com.dtrps.modules.poi.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.poi.dto.AdminPlaceDetail;
import com.dtrps.modules.poi.dto.AdminPlaceRequest;
import com.dtrps.modules.poi.dto.AdminPlaceStats;
import com.dtrps.modules.poi.dto.AdminPlaceSummary;
import com.dtrps.modules.poi.dto.ImageDto;
import com.dtrps.modules.poi.dto.OpeningHoursRequest;
import com.dtrps.modules.poi.dto.PlaceStatusRequest;
import com.dtrps.modules.poi.dto.PriceDto;
import com.dtrps.modules.poi.service.AdminPlaceEditService;
import com.dtrps.modules.poi.service.AdminPlaceService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every place in the system (admin POIs and partner places), newest update first. The admin adds
 * and edits its own places here; every change returns the updated place.
 */
@RestController
@Validated
@RequestMapping("/api/admin/places")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlaceController {
  private static final int MAX_PAGE_SIZE = 100;

  private final AdminPlaceService placeService;
  private final AdminPlaceEditService editService;

  public AdminPlaceController(AdminPlaceService placeService, AdminPlaceEditService editService) {
    this.placeService = placeService;
    this.editService = editService;
  }

  @GetMapping
  public PageResponse<AdminPlaceSummary> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String group,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String province,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return placeService.list(
        keyword,
        group,
        category,
        province,
        status,
        source,
        PageRequest.of(page, size, order(sort)));
  }

  /**
   * Thứ tự danh sách. Mặc định theo mã địa điểm: sửa ảnh / giá / giờ không làm địa điểm nhảy trang.
   * Luôn kèm placeId để các trang không trùng / sót dòng khi hai địa điểm có cùng giá trị.
   */
  private static Sort order(String sort) {
    Sort byId = Sort.by("placeId");
    if (sort == null) return byId;
    return switch (sort) {
      case "updated" -> Sort.by(Sort.Direction.DESC, "updatedAt").and(byId);
      case "created" ->
          Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "placeId"));
      case "name" -> Sort.by("name").and(byId);
      default -> byId;
    };
  }

  @GetMapping("/stats")
  public AdminPlaceStats stats() {
    return placeService.stats();
  }

  @GetMapping("/{id}")
  public AdminPlaceDetail get(@PathVariable Long id) {
    return placeService.get(id);
  }

  // ===== Admin places: create and edit (partner places answer 409) =====

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AdminPlaceDetail create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AdminPlaceRequest request) {
    return editService.create(CurrentUser.id(jwt), request);
  }

  /** Lists left out of the body keep their current content. */
  @PutMapping("/{id}")
  public AdminPlaceDetail update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody AdminPlaceRequest request) {
    return editService.update(CurrentUser.id(jwt), id, request);
  }

  @PutMapping("/{id}/status")
  public AdminPlaceDetail changeStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody PlaceStatusRequest request) {
    return editService.changeStatus(CurrentUser.id(jwt), id, request.status());
  }

  @PutMapping("/{id}/opening-hours")
  public AdminPlaceDetail replaceHours(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody OpeningHoursRequest request) {
    return editService.replaceHours(CurrentUser.id(jwt), id, request.days());
  }

  @PostMapping("/{id}/images")
  public AdminPlaceDetail addImage(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody ImageDto request) {
    return editService.addImage(CurrentUser.id(jwt), id, request);
  }

  @DeleteMapping("/{id}/images/{imageId}")
  public AdminPlaceDetail deleteImage(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long imageId) {
    return editService.deleteImage(CurrentUser.id(jwt), id, imageId);
  }

  @PutMapping("/{id}/images/{imageId}/primary")
  public AdminPlaceDetail setPrimaryImage(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long imageId) {
    return editService.setPrimaryImage(CurrentUser.id(jwt), id, imageId);
  }

  @PostMapping("/{id}/prices")
  public AdminPlaceDetail addPrice(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody PriceDto request) {
    return editService.addPrice(CurrentUser.id(jwt), id, request);
  }

  @PutMapping("/{id}/prices/{priceId}")
  public AdminPlaceDetail updatePrice(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @PathVariable Long priceId,
      @Valid @RequestBody PriceDto request) {
    return editService.updatePrice(CurrentUser.id(jwt), id, priceId, request);
  }

  @DeleteMapping("/{id}/prices/{priceId}")
  public AdminPlaceDetail deletePrice(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @PathVariable Long priceId) {
    return editService.deletePrice(CurrentUser.id(jwt), id, priceId);
  }
}
