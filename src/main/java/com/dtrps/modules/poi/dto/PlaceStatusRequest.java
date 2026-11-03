package com.dtrps.modules.poi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** ACTIVE shows the place to travellers; DRAFT, PAUSED and MAINTENANCE hide it. */
public record PlaceStatusRequest(
    @NotBlank(message = "Vui lòng chọn trạng thái")
        @Pattern(
            regexp = "^(ACTIVE|DRAFT|PAUSED|MAINTENANCE)$",
            message = "Trạng thái phải là ACTIVE, DRAFT, PAUSED hoặc MAINTENANCE")
        String status) {}
