package com.dtrps.modules.businessowner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What a user fills in to register, or later edit, their business profile. */
public record BusinessProfileRequest(
    @NotBlank(message = "Vui lòng nhập tên doanh nghiệp")
        @Size(min = 2, max = 255, message = "Tên doanh nghiệp phải có từ 2 đến 255 ký tự")
        String businessName,
    @NotBlank(message = "Vui lòng nhập mã số thuế")
        @Pattern(
            regexp = "^[0-9]{10}(-[0-9]{3})?$",
            message = "Mã số thuế gồm 10 số, hoặc 10 số kèm mã chi nhánh dạng -001")
        String taxCode,
    @NotBlank(message = "Vui lòng nhập email liên hệ")
        @Email(message = "Email liên hệ không hợp lệ")
        @Size(max = 255, message = "Email liên hệ quá dài")
        String contactEmail,
    @NotBlank(message = "Vui lòng nhập số điện thoại liên hệ")
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Số điện thoại không hợp lệ")
        String contactPhone) {}
