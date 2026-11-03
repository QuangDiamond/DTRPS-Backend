package com.dtrps.modules.businessowner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The reason is shown to the business owner so they can fix the profile and submit again. */
public record RejectBusinessProfileRequest(
    @NotBlank(message = "Vui lòng nhập lý do từ chối")
        @Size(max = 1000, message = "Lý do tối đa 1000 ký tự")
        String reason) {}
