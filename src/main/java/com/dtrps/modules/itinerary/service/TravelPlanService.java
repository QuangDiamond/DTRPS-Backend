
package com.dtrps.modules.itinerary.service;

import com.dtrps.exception.ApiException;

import com.dtrps.modules.itinerary.dto.TravelPlanDtos;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;

import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;

import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TravelPlanService {

    private final TravelPlanRepository planRepository;
    private final UserRepository userRepository;
    private final PlaceCategoryRepository categoryRepository;

    public TravelPlanService(
            TravelPlanRepository planRepository,
            UserRepository userRepository,
            PlaceCategoryRepository categoryRepository
    ) {
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    /**
     * BƯỚC 1: Tạo kế hoạch du lịch
     * Có lưu sở thích từ bảng place_categories.
     */
    @Transactional
    public TravelPlanDtos.PlanResponse create(
            Long userId,
            TravelPlanDtos.CreateRequest request
    ) {

        // Kiểm tra người dùng
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy người dùng"
                ));

        // Kiểm tra thời gian chuyến đi
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();

        if (
                startDate == null ||
                        endDate == null ||
                        endDate.isBefore(startDate)
        ) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu"
            );
        }

        // Xác thực categoryCodes từ place_categories
        List<String> categoryCodes =
                validateCategoryCodes(request.categoryCodes());

        // Tạo TravelPlan
        TravelPlan plan = new TravelPlan();

        plan.setUser(user);

        plan.setTitle(request.title().trim());
        plan.setDestination(request.destination().trim());

        plan.setStartDate(startDate);
        plan.setEndDate(endDate);

        plan.setBudget(request.budget());

        String currency = request.currency();

        plan.setCurrency(
                currency == null
                        ? "VND"
                        : currency.toUpperCase(Locale.ROOT)
        );

        plan.setGroupSize(
                request.groupSize() == null
                        ? 1
                        : request.groupSize()
        );

        // Description chỉ chứa mô tả thông thường
        plan.setDescription(request.description());

        // Lưu danh sách mã vào cột category_codes
        plan.setCategoryCodes(
                new ArrayList<>(categoryCodes)
        );

        plan.setTravelPace(request.travelPace() == null ? "BALANCED" : request.travelPace());
        plan.setStatus("DRAFT");

        TravelPlan savedPlan =
                planRepository.saveAndFlush(plan);

        return toResponse(savedPlan);
    }

    /**
     * Xác thực danh mục sở thích.
     *
     * Chỉ nhận danh mục con ACTIVE,
     * có nhóm cha cũng ACTIVE.
     */
    private List<String> validateCategoryCodes(
            List<String> requestedCodes
    ) {

        if (
                requestedCodes == null ||
                        requestedCodes.isEmpty()
        ) {
            return List.of();
        }

        if (requestedCodes.size() > 50) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ được chọn tối đa 50 danh mục"
            );
        }

        // Lấy danh mục ACTIVE trong database
        List<PlaceCategory> activeCategories =
                categoryRepository
                        .findByStatusOrderByCategoryIdAsc("ACTIVE");

        // Chỉ cho phép mã danh mục con,
        // không cho mã nhóm cha.
        Set<String> validCodes =
                activeCategories.stream()
                        .filter(category ->
                                category.getParent() != null
                        )
                        .filter(category ->
                                "ACTIVE".equals(
                                        category.getParent().getStatus()
                                )
                        )
                        .map(PlaceCategory::getCode)
                        .collect(Collectors.toSet());

        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();

        for (String code : requestedCodes) {

            if (
                    code == null ||
                            code.isBlank() ||
                            code.length() > 50 ||
                            !validCodes.contains(code)
            ) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Danh mục trải nghiệm không hợp lệ: " + code
                );
            }

            // Không cho phép trùng mã
            if (!seen.add(code)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Danh mục trải nghiệm bị trùng: " + code
                );
            }

            result.add(code);
        }

        return result;
    }

    /**
     * Danh sách kế hoạch của Traveller.
     */
    @Transactional(readOnly = true)
    public List<TravelPlanDtos.PlanResponse> getMyPlans(
            Long userId
    ) {

        return planRepository
                .findByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Xem chi tiết kế hoạch.
     */
    @Transactional(readOnly = true)
    public TravelPlanDtos.PlanResponse getById(
            Long userId,
            Long planId
    ) {

        TravelPlan plan = planRepository
                .findByPlanIdAndUserUserId(planId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy kế hoạch du lịch"
                ));

        return toResponse(plan);
    }

    /**
     * Entity -> DTO
     */
    private TravelPlanDtos.PlanResponse toResponse(
            TravelPlan plan
    ) {

        List<String> categories =
                plan.getCategoryCodes() == null
                        ? List.of()
                        : List.copyOf(plan.getCategoryCodes());

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
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }
}