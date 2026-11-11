
package com.dtrps.modules.itinerary.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.PlanPlaceDtos;
import com.dtrps.modules.itinerary.entity.PlanPlace;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.PlanPlaceRepository;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.itinerary.repository.TripProgressRepository;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.entity.PlacePrice;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlacePriceRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanPlaceService {

    private final TravelPlanRepository plans;
    private final PlanPlaceRepository selected;
    private final PlaceRepository places;
    private final PlaceImageRepository images;
    private final PlacePriceRepository prices;
    private final TripProgressRepository tripProgressRepository;

    public PlanPlaceService(
            TravelPlanRepository plans,
            PlanPlaceRepository selected,
            PlaceRepository places,
            PlaceImageRepository images,
            PlacePriceRepository prices,
            TripProgressRepository tripProgressRepository
    ) {
        this.plans = plans;
        this.selected = selected;
        this.places = places;
        this.images = images;
        this.prices = prices;
        this.tripProgressRepository = tripProgressRepository;
    }

    /**
     * Danh sách phù hợp sơ bộ theo tỉnh/thành và category.
     * Đây là catalogue filtering, không phải AI ranking.
     */
    @Transactional(readOnly = true)
    public List<PlanPlaceDtos.PlaceResponse> candidates(
            Long userId,
            Long planId
    ) {
        TravelPlan plan = requirePlan(userId, planId);

        String destination = plan.getDestination()
                .trim()
                .toLowerCase(Locale.ROOT);

        Specification<Place> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), "ACTIVE"),
                cb.or(
                        cb.equal(
                                cb.lower(
                                        root.join("province", JoinType.LEFT)
                                                .get("name")),
                                destination),
                        cb.equal(
                                cb.lower(
                                        root.join("province", JoinType.LEFT)
                                                .get("code")),
                                destination)
                )
        );

        List<String> categoryCodes = plan.getCategoryCodes();

        if (categoryCodes != null && !categoryCodes.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    root.join("category", JoinType.INNER)
                            .get("code")
                            .in(categoryCodes));
        }

        List<Place> result = places.findAll(
                spec,
                PageRequest.of(
                        0,
                        100,
                        Sort.by("placeId").ascending()
                )
        ).getContent();

        Set<Long> selectedIds = selected
                .findByPlanPlanIdOrderByPriorityDescPlanPlaceIdAsc(planId)
                .stream()
                .map(p -> p.getPlace().getPlaceId())
                .collect(Collectors.toSet());

        return toResponses(result, selectedIds, "CATALOG");
    }

    /**
     * Xem danh sách địa điểm Traveller đã chọn.
     * Cho phép xem kể cả khi chuyến đi đang diễn ra.
     */
    @Transactional(readOnly = true)
    public PlanPlaceDtos.SelectedResponse list(
            Long userId,
            Long planId
    ) {
        requirePlan(userId, planId);

        List<PlanPlace> rows = selected
                .findByPlanPlanIdOrderByPriorityDescPlanPlaceIdAsc(planId);

        List<PlanPlaceDtos.PlaceResponse> result = toResponses(
                rows.stream()
                        .map(PlanPlace::getPlace)
                        .toList(),
                Set.of(),
                "SELECTED"
        ).stream()
                .map(p -> new PlanPlaceDtos.PlaceResponse(
                        p.placeId(),
                        p.name(),
                        p.address(),
                        p.province(),
                        p.categoryCode(),
                        p.categoryName(),
                        p.imageUrl(),
                        p.estimatedCost(),
                        p.currency(),
                        p.latitude(),
                        p.longitude(),
                        true,
                        "SELECTED"
                ))
                .toList();

        return new PlanPlaceDtos.SelectedResponse(
                planId,
                result.size(),
                result
        );
    }

    /**
     * Thêm địa điểm vào Travel Plan.
     * Không cho thêm khi chuyến đi đã bắt đầu.
     */
    @Transactional
    public PlanPlaceDtos.PlaceResponse add(
            Long userId,
            Long planId,
            Long placeId
    ) {
        TravelPlan plan = plans.lockById(planId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy kế hoạch"
                ));

        checkOwner(plan, userId);

        // Kiểm tra trạng thái trước khi thêm địa điểm.
        ensurePlanEditable(plan);

        Place place = places.findWithDetailsByPlaceId(placeId)
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Địa điểm không tồn tại hoặc chưa ACTIVE"
                ));

        if (!selected.existsByPlanPlanIdAndPlacePlaceId(
                planId,
                placeId
        )) {
            PlanPlace row = new PlanPlace();

            row.setPlan(plan);
            row.setPlace(place);
            row.setSource("USER");
            row.setPriority(0);

            selected.saveAndFlush(row);
        }

        return toResponses(
                List.of(place),
                Set.of(placeId),
                "SELECTED"
        ).get(0);
    }

    /**
     * Xóa địa điểm khỏi danh sách đã chọn.
     * Không cho xóa khi chuyến đi đã bắt đầu.
     */
    @Transactional
    public void remove(
            Long userId,
            Long planId,
            Long placeId
    ) {
        TravelPlan plan = plans.lockById(planId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy kế hoạch"
                ));

        checkOwner(plan, userId);

        // Kiểm tra trạng thái trước khi xóa địa điểm.
        ensurePlanEditable(plan);

        PlanPlace row = selected
                .findByPlanPlanIdAndPlacePlaceId(planId, placeId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Địa điểm chưa được chọn"
                ));

        selected.delete(row);
    }

    /**
     * Chặn thêm/xóa địa điểm sau khi chuyến đi bắt đầu.
     *
     * add() và remove() đã khóa TravelPlan trước khi gọi
     * phương thức này, đồng bộ với TripProgressService
     * và ItineraryService.
     */
    private void ensurePlanEditable(TravelPlan plan) {

        // Chỉ DRAFT và PLANNED mới được thay đổi địa điểm.
        if (!TravelPlanStatus.isEditable(plan.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Không thể thay đổi địa điểm khi chuyến đi "
                            + "đang diễn ra hoặc đã kết thúc"
            );
        }

        // Kiểm tra tiến độ ở tất cả phiên bản Itinerary,
        // kể cả các phiên bản ARCHIVED.
        boolean tripStarted = tripProgressRepository
                .findByItineraryPlanPlanIdOrderByProgressIdAsc(
                        plan.getPlanId()
                )
                .stream()
                .anyMatch(progress ->
                        progress.getStartedAt() != null
                                || progress.getCompletedAt() != null
                                || !"NOT_STARTED".equals(
                                progress.getStatus())
                );

        if (tripStarted) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Chuyến đi đã bắt đầu, không thể "
                            + "thay đổi danh sách địa điểm"
            );
        }
    }

    /**
     * Tìm kế hoạch thuộc đúng Traveller.
     */
    private TravelPlan requirePlan(
            Long userId,
            Long planId
    ) {
        return plans.findByPlanIdAndUserUserId(planId, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy kế hoạch du lịch"
                ));
    }

    /**
     * Kiểm tra quyền sở hữu.
     */
    private void checkOwner(
            TravelPlan plan,
            Long userId
    ) {
        if (!plan.getUser().getUserId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy kế hoạch du lịch"
            );
        }
    }

    /**
     * Chuyển danh sách Place thành DTO.
     * Giữ nguyên logic lấy ảnh và giá tham khảo.
     */
    private List<PlanPlaceDtos.PlaceResponse> toResponses(
            List<Place> result,
            Set<Long> selectedIds,
            String source
    ) {
        if (result.isEmpty()) {
            return List.of();
        }

        List<Long> ids = result.stream()
                .map(Place::getPlaceId)
                .toList();

        Map<Long, List<PlaceImage>> covers =
                images.findForPlaces(ids)
                        .stream()
                        .collect(Collectors.groupingBy(
                                i -> i.getPlace().getPlaceId()
                        ));

        return result.stream()
                .map(place -> {

                    List<PlaceImage> pictures =
                            covers.getOrDefault(
                                    place.getPlaceId(),
                                    List.of()
                            );

                    List<PlacePrice> fees =
                            prices.findByPlacePlaceIdOrderByPriceIdAsc(
                                    place.getPlaceId()
                            );

                    // Giá thấp nhất chỉ là giá tham khảo.
                    BigDecimal estimated = fees.stream()
                            .filter(f -> "VND".equals(f.getCurrency()))
                            .map(PlacePrice::getAmount)
                            .min(Comparator.naturalOrder())
                            .orElse(null);

                    return new PlanPlaceDtos.PlaceResponse(
                            place.getPlaceId(),
                            place.getName(),
                            place.getAddress(),
                            place.getProvince() == null
                                    ? null
                                    : place.getProvince().getName(),
                            place.getCategory() == null
                                    ? null
                                    : place.getCategory().getCode(),
                            place.getCategory() == null
                                    ? null
                                    : place.getCategory().getName(),
                            pictures.isEmpty()
                                    ? null
                                    : pictures.get(0).getImageUrl(),
                            estimated,
                            "VND",
                            place.getLatitude(),
                            place.getLongitude(),
                            selectedIds.contains(place.getPlaceId()),
                            source
                    );
                })
                .toList();
    }
}
