package com.dtrps.modules.poi.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.poi.dto.AdminCategoryGroup;
import com.dtrps.modules.poi.dto.AdminCategoryGroup.AdminCategory;
import com.dtrps.modules.poi.dto.AdminCategoryRequest;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Place types (the subtypes under the 4 fixed service groups): add, rename, turn on or off. Codes
 * are fixed once created because places and clients refer to them.
 */
@Service
public class AdminCategoryService {
  static final String AUDIT_ENTITY = "place_categories";
  static final String ACTION_CREATED = "CATEGORY_CREATED";
  static final String ACTION_UPDATED = "CATEGORY_UPDATED";
  static final String ACTION_STATUS = "CATEGORY_STATUS_CHANGED";
  static final String ACTION_DELETED = "CATEGORY_DELETED";

  private static final String NOT_FOUND = "Không tìm thấy loại địa điểm";
  private static final String UNKNOWN_GROUP = "Nhóm dịch vụ không hợp lệ";
  private static final String GROUP_FIXED =
      "Không thể sửa nhóm dịch vụ, chỉ sửa được loại bên trong";
  private static final String MISSING_CODE = "Vui lòng nhập mã loại";
  private static final String DUPLICATE_CODE = "Mã loại đã tồn tại";
  private static final String DUPLICATE_NAME = "Tên loại đã tồn tại";
  private static final String IN_USE =
      "Loại hình đang có địa điểm sử dụng nên không xoá được — hãy tắt loại hình thay vì xoá";

  private final PlaceCategoryRepository categoryRepository;
  private final PlaceRepository placeRepository;
  private final AuditService auditService;

  public AdminCategoryService(
      PlaceCategoryRepository categoryRepository,
      PlaceRepository placeRepository,
      AuditService auditService) {
    this.categoryRepository = categoryRepository;
    this.placeRepository = placeRepository;
    this.auditService = auditService;
  }

  /**
   * The 4 service groups in their fixed order, each with all of its types (inactive included) and
   * their usage. Stray top-level rows outside the 4 groups are not shown.
   */
  @Transactional(readOnly = true)
  public List<AdminCategoryGroup> list() {
    Map<Long, Long> usage = new HashMap<>();
    for (Object[] row : placeRepository.countByCategory()) {
      if (row[0] != null) {
        usage.put((Long) row[0], ((Number) row[1]).longValue());
      }
    }
    Map<Long, PlaceCategory> groups = new LinkedHashMap<>();
    Map<Long, List<AdminCategory>> types = new HashMap<>();
    for (PlaceCategory category : categoryRepository.findAllByOrderByCategoryIdAsc()) {
      if (category.getParent() == null) {
        groups.put(category.getCategoryId(), category);
      } else {
        types
            .computeIfAbsent(category.getParent().getCategoryId(), id -> new ArrayList<>())
            .add(toItem(category, usage.getOrDefault(category.getCategoryId(), 0L)));
      }
    }
    return groups.values().stream()
        .filter(group -> ReferenceDataService.GROUP_ORDER.contains(group.getCode()))
        .sorted(Comparator.comparingInt(group -> ReferenceDataService.groupRank(group.getCode())))
        .map(
            group ->
                new AdminCategoryGroup(
                    group.getCategoryId(),
                    group.getCode(),
                    group.getName(),
                    group.getStatus(),
                    types.getOrDefault(group.getCategoryId(), List.of())))
        .toList();
  }

  @Transactional
  public AdminCategory create(Long adminId, AdminCategoryRequest request) {
    if (request.code() == null || request.code().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, MISSING_CODE);
    }
    String code = request.code().trim();
    String name = request.name().trim();
    if (categoryRepository.existsByCodeIgnoreCase(code)) {
      throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_CODE);
    }
    if (categoryRepository.existsByNameIgnoreCase(name)) {
      throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_NAME);
    }
    PlaceCategory category = new PlaceCategory();
    category.setParent(group(request.groupCode()));
    category.setCode(code);
    category.setName(name);
    category.setDescription(blankToNull(request.description()));
    categoryRepository.saveAndFlush(category);
    auditService.record(adminId, ACTION_CREATED, AUDIT_ENTITY, category.getCategoryId(), code);
    return toItem(category, 0);
  }

  /** Name, description and group can change; the code stays. */
  @Transactional
  public AdminCategory update(Long adminId, Long categoryId, AdminCategoryRequest request) {
    PlaceCategory category = type(categoryId);
    String name = request.name().trim();
    if (categoryRepository.existsByNameIgnoreCaseAndCategoryIdNot(name, categoryId)) {
      throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_NAME);
    }
    category.setParent(group(request.groupCode()));
    category.setName(name);
    category.setDescription(blankToNull(request.description()));
    categoryRepository.flush();
    auditService.record(adminId, ACTION_UPDATED, AUDIT_ENTITY, categoryId);
    return toItem(category, usageOf(categoryId));
  }

  /** INACTIVE hides the type from place forms; places already using it keep it. */
  @Transactional
  public AdminCategory changeStatus(Long adminId, Long categoryId, String status) {
    PlaceCategory category = type(categoryId);
    category.setStatus(status);
    categoryRepository.flush();
    auditService.record(adminId, ACTION_STATUS, AUDIT_ENTITY, categoryId, status);
    return toItem(category, usageOf(categoryId));
  }

  /**
   * Removes a type no place uses (e.g. one added by mistake). A type in use can only be turned off,
   * so existing places keep their type.
   */
  @Transactional
  public void delete(Long adminId, Long categoryId) {
    PlaceCategory category = type(categoryId);
    if (placeRepository.existsByCategoryCategoryId(categoryId)) {
      throw new ApiException(HttpStatus.CONFLICT, IN_USE);
    }
    categoryRepository.delete(category);
    categoryRepository.flush();
    auditService.record(adminId, ACTION_DELETED, AUDIT_ENTITY, categoryId, category.getCode());
  }

  // ===== Helpers =====

  /** A subtype; the 4 groups themselves are fixed. */
  private PlaceCategory type(Long categoryId) {
    PlaceCategory category =
        categoryRepository
            .findById(categoryId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    if (category.getParent() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, GROUP_FIXED);
    }
    return category;
  }

  private PlaceCategory group(String code) {
    return categoryRepository
        .findByCode(code.trim())
        .filter(found -> found.getParent() == null)
        .filter(found -> ReferenceDataService.GROUP_ORDER.contains(found.getCode()))
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, UNKNOWN_GROUP));
  }

  private long usageOf(Long categoryId) {
    for (Object[] row : placeRepository.countByCategory()) {
      if (categoryId.equals(row[0])) {
        return ((Number) row[1]).longValue();
      }
    }
    return 0;
  }

  private static AdminCategory toItem(PlaceCategory category, long placeCount) {
    return new AdminCategory(
        category.getCategoryId(),
        category.getCode(),
        category.getName(),
        category.getDescription(),
        category.getStatus(),
        placeCount);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
