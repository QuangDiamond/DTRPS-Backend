package com.dtrps.modules.poi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** INACTIVE hides the type from place forms; places already using it keep it. */
public record CategoryStatusRequest(
    @NotBlank(message = "Vui lòng chọn trạng thái")
        @Pattern(
            regexp = "^(ACTIVE|INACTIVE)$",
            message = "Trạng thái phải là ACTIVE hoặc INACTIVE")
        String status) {}
