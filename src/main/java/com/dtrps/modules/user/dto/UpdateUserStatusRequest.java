package com.dtrps.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserStatusRequest(
    @NotBlank(message = "Vui lòng chọn trạng thái")
        @Pattern(regexp = "ACTIVE|SUSPENDED", message = "Trạng thái không hợp lệ")
        String status) {}
