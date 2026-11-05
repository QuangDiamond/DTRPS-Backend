package com.dtrps.modules.itinerary.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public final class PlanPlaceDtos {
    private PlanPlaceDtos() {}

    public record AddRequest(@NotNull @Positive Long placeId) {}

    /** CATALOG = địa điểm phù hợp bộ lọc sơ bộ; không phải kết quả AI Embedding. */
    public record PlaceResponse(
            Long placeId,
            String name,
            String address,
            String province,
            String categoryCode,
            String categoryName,
            String imageUrl,
            BigDecimal estimatedCost,
            String currency,
            Double latitude,
            Double longitude,
            boolean selected,
            String source) {}

    public record SelectedResponse(Long planId, int count, List<PlaceResponse> places) {}
}