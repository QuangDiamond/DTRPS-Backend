package com.dtrps.modules.businessowner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Shown to the business owner so they can fix the place and submit again. */
public record RejectListingRequest(
    @NotBlank(message = "Vui lòng nhập lý do từ chối")
        @Size(max = 1000, message = "Lý do tối đa 1000 ký tự")
        String reason) {}
