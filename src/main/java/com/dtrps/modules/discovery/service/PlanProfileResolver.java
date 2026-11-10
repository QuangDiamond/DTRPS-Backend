
package com.dtrps.modules.discovery.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanProfileResolver {

    private static final Pattern COMPANION_PATTERN =
        Pattern.compile(
            "Hình thức đồng hành\\s*:\\s*([^\\r\\n.;]+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );

    private final TravelPlanRepository plans;
    private final PlaceCategoryRepository categories;

    public PlanProfileResolver(
            TravelPlanRepository plans,
            PlaceCategoryRepository categories) {
        this.plans = plans;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public PlanProfile resolve(Long userId, Long planId) {

        TravelPlan plan = plans
            .findByPlanIdAndUserUserId(planId, userId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy kế hoạch du lịch"
            ));

        Map<String, PlaceCategory> categoryMap =
            categories.findByStatusOrderByCategoryIdAsc("ACTIVE")
                .stream()
                .collect(Collectors.toMap(
                    PlaceCategory::getCode,
                    Function.identity()
                ));

        List<String> codes = plan.getCategoryCodes() == null
            ? List.of()
            : plan.getCategoryCodes();

        // Loại mã không hợp lệ và nhóm lưu trú.
        List<String> eligibleCodes = codes.stream()
            .filter(Objects::nonNull)
            .filter(categoryMap::containsKey)
            .filter(code -> {
                PlaceCategory c = categoryMap.get(code);
                return c.getParent() == null
                    || !"lodging".equalsIgnoreCase(
                        c.getParent().getCode()
                    );
            })
            .distinct()
            .toList();

        List<String> names = eligibleCodes.stream()
            .map(code -> categoryMap.get(code).getName())
            .toList();

        int days = (int) ChronoUnit.DAYS.between(
            plan.getStartDate(),
            plan.getEndDate()
        ) + 1;

        return new PlanProfile(
            plan.getPlanId(),
            plan.getDestination(),
            days,
            plan.getGroupSize(),
            plan.getBudget(),
            plan.getCurrency(),
            plan.getTravelPace(),
            eligibleCodes,
            names,
            parseCompanion(plan.getDescription())
        );
    }

    private String parseCompanion(String description) {
        if (description == null || description.isBlank()) {
            return "UNKNOWN";
        }

        Matcher matcher =
            COMPANION_PATTERN.matcher(description);

        if (!matcher.find()) {
            return "UNKNOWN";
        }

        return matcher.group(1).trim();
    }

    public record PlanProfile(
        Long planId,
        String destination,
        int days,
        int groupSize,
        BigDecimal totalBudget,
        String currency,
        String travelPace,
        List<String> categoryCodes,
        List<String> categoryNames,
        String companionType
    ) {
        public BigDecimal budgetPerPerson() {
            if (totalBudget == null || groupSize <= 0) {
                return null;
            }

            return totalBudget.divide(
                BigDecimal.valueOf(groupSize),
                2,
                RoundingMode.HALF_UP
            );
        }
    }
}
