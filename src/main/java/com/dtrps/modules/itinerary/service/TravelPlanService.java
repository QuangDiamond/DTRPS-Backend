package com.dtrps.modules.itinerary.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.TravelPlanDtos;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.ItineraryRepository;
import com.dtrps.modules.itinerary.repository.PlanInterestRepository;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo, xem, sửa, xóa và đổi trạng thái kế hoạch du lịch.
 *
 * <p>Mọi thao tác chỉ dành cho chủ kế hoạch. Kế hoạch của người khác trả 404 giống như không tồn
 * tại, để không lộ ID.
 */
@Service
public class TravelPlanService {

  static final String PLAN_NOT_FOUND = "Không tìm thấy kế hoạch du lịch";
  static final String INVALID_DATES = "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu";
  static final String NOT_EDITABLE =
      "Chỉ sửa được kế hoạch ở trạng thái Nháp hoặc Đã lên kế hoạch";
  static final String ITINERARY_LOCKS_TRIP =
      "Kế hoạch đã có lịch trình nên không thể đổi điểm đến, ngày đi hoặc ngày về";
  static final String NOT_DELETABLE = "Không thể xóa kế hoạch khi chuyến đi đang diễn ra";

  private static final int MAX_CODES = 50;

  private final TravelPlanRepository planRepository;
  private final PlanInterestRepository planInterestRepository;
  private final ItineraryRepository itineraryRepository;
  private final UserRepository userRepository;
  private final PlaceCategoryRepository categoryRepository;

  public TravelPlanService(
      TravelPlanRepository planRepository,
      PlanInterestRepository planInterestRepository,
      ItineraryRepository itineraryRepository,
      UserRepository userRepository,
      PlaceCategoryRepository categoryRepository) {
    this.planRepository = planRepository;
    this.planInterestRepository = planInterestRepository;
    this.itineraryRepository = itineraryRepository;
    this.userRepository = userRepository;
    this.categoryRepository = categoryRepository;
  }

  // ---------------------------------------------------------------- Tạo

  @Transactional
  public TravelPlanDtos.PlanResponse create(Long userId, TravelPlanDtos.CreateRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));

    TravelPlan plan = new TravelPlan();
    plan.setUser(user);
    plan.setStatus(TravelPlanStatus.DRAFT);
    applyFields(plan, request);

    return toResponse(planRepository.saveAndFlush(plan));
  }

  // ---------------------------------------------------------------- Xem

  @Transactional(readOnly = true)
  public List<TravelPlanDtos.PlanResponse> getMyPlans(Long userId) {
    return planRepository.findByUserUserIdOrderByCreatedAtDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public TravelPlanDtos.PlanResponse getById(Long userId, Long planId) {
    return toResponse(findOwnedPlan(userId, planId));
  }

  // ---------------------------------------------------------------- Sửa

  @Transactional
  public TravelPlanDtos.PlanResponse update(
      Long userId, Long planId, TravelPlanDtos.UpdateRequest request) {
    TravelPlan plan = findOwnedPlan(userId, planId);

    if (!TravelPlanStatus.isEditable(plan.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, NOT_EDITABLE);
    }

    // Lịch trình đã sinh theo điểm đến và số ngày của kế hoạch,
    // đổi các trường này sẽ làm lịch trình sai.
    boolean tripChanged =
        !Objects.equals(plan.getDestination(), trimmed(request.destination()))
            || !Objects.equals(plan.getStartDate(), request.startDate())
            || !Objects.equals(plan.getEndDate(), request.endDate());
    if (tripChanged && itineraryRepository.existsByPlanPlanId(planId)) {
      throw new ApiException(HttpStatus.CONFLICT, ITINERARY_LOCKS_TRIP);
    }

    applyFields(plan, request);
    return toResponse(planRepository.saveAndFlush(plan));
  }

  // ---------------------------------------------------------------- Xóa

  @Transactional
  public void delete(Long userId, Long planId) {
    TravelPlan plan = findOwnedPlan(userId, planId);

    if (!TravelPlanStatus.isDeletable(plan.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, NOT_DELETABLE);
    }

    // Thứ tự quan trọng: bảng con trước, bảng cha sau.
    planRepository.deleteActivityProgressByPlanId(planId);
    planRepository.deleteTripProgressByPlanId(planId);
    planRepository.deleteItinerarySharesByPlanId(planId);
    planRepository.deleteItineraryItemsByPlanId(planId);
    planRepository.deleteItineraryDaysByPlanId(planId);
    planRepository.deleteItinerariesByPlanId(planId);
    planRepository.deletePlanPlacesByPlanId(planId);
    planInterestRepository.deleteByPlanId(planId); // dòng cũ, nếu có
    planRepository.deleteTripFeedbackByPlanId(planId);
    planRepository.deletePlanById(planId);
  }

  // ---------------------------------------------------------------- Đổi trạng thái

  @Transactional
  public TravelPlanDtos.PlanResponse changeStatus(Long userId, Long planId, String newStatus) {
    TravelPlan plan = findOwnedPlan(userId, planId);
    String current = plan.getStatus();

    if (!TravelPlanStatus.isKnown(newStatus)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ");
    }
    if (newStatus.equals(current)) {
      throw new ApiException(
          HttpStatus.CONFLICT, "Kế hoạch đã ở trạng thái " + label(current));
    }
    if (!TravelPlanStatus.canTransition(current, newStatus)) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "Không thể chuyển kế hoạch từ "
              + label(current)
              + " sang "
              + label(newStatus)
              + transitionHint(current));
    }

    plan.setStatus(newStatus);
    return toResponse(planRepository.saveAndFlush(plan));
  }

  // ---------------------------------------------------------------- Hàm phụ

  private TravelPlan findOwnedPlan(Long userId, Long planId) {
    return planRepository
        .findByPlanIdAndUserUserId(planId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PLAN_NOT_FOUND));
  }

  /** Gán các trường chung của tạo và sửa. Không đụng tới user và status. */
  private void applyFields(TravelPlan plan, TravelPlanDtos.PlanFields fields) {
    if (fields.startDate() == null
        || fields.endDate() == null
        || fields.endDate().isBefore(fields.startDate())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_DATES);
    }

    plan.setTitle(trimmed(fields.title()));
    plan.setDestination(trimmed(fields.destination()));
    plan.setStartDate(fields.startDate());
    plan.setEndDate(fields.endDate());
    plan.setBudget(fields.budget());
    plan.setCurrency(
        fields.currency() == null ? "VND" : fields.currency().toUpperCase(Locale.ROOT));
    plan.setGroupSize(fields.groupSize() == null ? 1 : fields.groupSize());
    plan.setDescription(fields.description());
    plan.setCategoryCodes(new ArrayList<>(validateCategoryCodes(fields.categoryCodes())));
    plan.setTravelPace(fields.travelPace() == null ? "BALANCED" : fields.travelPace());
  }

  /**
   * Sở thích của chuyến: chỉ nhận loại địa điểm con ACTIVE, có nhóm cha cũng ACTIVE (giống sở
   * thích của người dùng).
   */
  private List<String> validateCategoryCodes(List<String> requestedCodes) {
    if (requestedCodes == null || requestedCodes.isEmpty()) {
      return List.of();
    }
    checkCodeList(requestedCodes, "Danh mục trải nghiệm");

    Set<String> validCodes =
        categoryRepository.findByStatusOrderByCategoryIdAsc("ACTIVE").stream()
            .filter(category -> category.getParent() != null)
            .filter(category -> "ACTIVE".equals(category.getParent().getStatus()))
            .map(PlaceCategory::getCode)
            .collect(Collectors.toSet());

    for (String code : requestedCodes) {
      if (!validCodes.contains(code)) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST, "Danh mục trải nghiệm không hợp lệ: " + code);
      }
    }
    return List.copyOf(requestedCodes);
  }

  /** Kiểm tra số lượng, mã rỗng và mã trùng. */
  private static void checkCodeList(List<String> codes, String label) {
    if (codes.size() > MAX_CODES) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, label + ": chỉ được chọn tối đa " + MAX_CODES + " mục");
    }
    Set<String> seen = new HashSet<>();
    for (String code : codes) {
      if (code == null || code.isBlank() || code.length() > MAX_CODES) {
        throw new ApiException(HttpStatus.BAD_REQUEST, label + " không hợp lệ: " + code);
      }
      if (!seen.add(code)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, label + " bị trùng: " + code);
      }
    }
  }

  private static String trimmed(String value) {
    return value == null ? null : value.trim();
  }

  private static String label(String status) {
    return switch (status) {
      case TravelPlanStatus.DRAFT -> "Nháp";
      case TravelPlanStatus.PLANNED -> "Đã lên kế hoạch";
      case TravelPlanStatus.IN_PROGRESS -> "Đang diễn ra";
      case TravelPlanStatus.COMPLETED -> "Hoàn thành";
      case TravelPlanStatus.CANCELLED -> "Đã hủy";
      default -> String.valueOf(status);
    };
  }

  private static String transitionHint(String current) {
    List<String> next = TravelPlanStatus.allowedNext(current);
    if (next.isEmpty()) {
      return ". Kế hoạch đã kết thúc, không thể đổi trạng thái nữa";
    }
    return ". Chỉ được chuyển sang: "
        + next.stream().map(TravelPlanService::label).collect(Collectors.joining(", "));
  }

  private TravelPlanDtos.PlanResponse toResponse(TravelPlan plan) {
    List<String> categories =
        plan.getCategoryCodes() == null ? List.of() : List.copyOf(plan.getCategoryCodes());

    return new TravelPlanDtos.PlanResponse(
        plan.getPlanId(),
        plan.getUser().getUserId(),
        plan.getTitle(),
        plan.getDestination(),
        plan.getStartDate(),
        plan.getEndDate(),
        plan.getBudget(),
        plan.getCurrency(),
        plan.getGroupSize(),
        plan.getDescription(),
        categories,
        plan.getTravelPace(),
        plan.getStatus(),
        TravelPlanStatus.allowedNext(plan.getStatus()),
        plan.getCreatedAt(),
        plan.getUpdatedAt());
  }
}
