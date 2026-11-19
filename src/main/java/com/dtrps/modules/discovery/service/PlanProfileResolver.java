
package com.dtrps.modules.discovery.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.SpecialNeed;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.user.repository.PreferenceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final PreferenceRepository preferences;

    public PlanProfileResolver(
            TravelPlanRepository plans,
            PlaceCategoryRepository categories,
            PreferenceRepository preferences) {
        this.plans = plans;
        this.categories = categories;
        this.preferences = preferences;
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

        // Giữ nguyên mọi loại hình người dùng chọn (kể cả lưu trú),
        // chỉ bỏ mã không hợp lệ.
        List<String> eligibleCodes = codes.stream()
            .filter(Objects::nonNull)
            .filter(categoryMap::containsKey)
            .distinct()
            .toList();

        // Các mã lưu trú người dùng chủ động chọn: chỉ khi có mã này
        // thì địa điểm lưu trú tương ứng mới được đưa vào gợi ý.
        List<String> lodgingCodes = eligibleCodes.stream()
            .filter(code -> isLodging(categoryMap.get(code)))
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
            parseCompanion(plan.getDescription()),
            lodgingCodes,
            // Câu trả lời cá nhân hóa (mục đích/phong cách/trải nghiệm/kỷ niệm) đã lưu trên plan.
            plan.getExperiencePreferences() == null
                ? List.of()
                : List.copyOf(plan.getExperiencePreferences()),
            // Yêu cầu đặc biệt nằm trong hồ sơ "Sở thích du lịch" của người dùng.
            preferences.findByUserUserId(userId)
                .map(p -> p.getSpecialNeeds().stream()
                    .map(SpecialNeed::getCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet()))
                .orElse(Set.of())
        );
    }

    private boolean isLodging(PlaceCategory category) {
        if ("lodging".equalsIgnoreCase(category.getCode())) {
            return true;
        }

        return category.getParent() != null
            && "lodging".equalsIgnoreCase(
                category.getParent().getCode()
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
        String companionType,
        List<String> lodgingCodes,
        List<String> experienceCodes,
        Set<String> specialNeeds
    ) {
        /** Có trẻ nhỏ: yêu cầu đặc biệt "kids" hoặc đi theo hình thức "Gia đình" (form ghi chú: có trẻ nhỏ). */
        public boolean withKids() {
            if (specialNeeds.contains("kids")) {
                return true;
            }
            String c = companionType == null ? "" : companionType.trim().toLowerCase();
            return c.contains("gia đình") || c.contains("family") || c.contains("trẻ nhỏ");
        }

        /** Cần tránh nơi phải leo trèo, đi bộ nhiều. */
        public boolean limitedMobility() {
            return specialNeeds.contains("elderly")
                || specialNeeds.contains("limitedMobility")
                || specialNeeds.contains("disability");
        }

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
