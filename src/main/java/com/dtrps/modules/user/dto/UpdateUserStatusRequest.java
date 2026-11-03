package com.dtrps.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code reason} is optional; when locking it is stored in the audit log and shown to admins. */
public record UpdateUserStatusRequest(
    @NotBlank(message = "Vui lòng chọn trạng thái")
        @Pattern(regexp = "ACTIVE|SUSPENDED", message = "Trạng thái không hợp lệ")
        String status,
    @Size(max = 500, message = "Lý do tối đa 500 ký tự") String reason) {}
