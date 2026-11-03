package com.dtrps.modules.businessowner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** SUSPENDED locks an active business and needs a reason; ACTIVE unlocks it. */
public record UpdateBusinessStatusRequest(
    @NotBlank(message = "Vui lòng chọn trạng thái")
        @Pattern(regexp = "ACTIVE|SUSPENDED", message = "Trạng thái không hợp lệ")
        String status,
    @Size(max = 1000, message = "Lý do tối đa 1000 ký tự") String reason) {}
