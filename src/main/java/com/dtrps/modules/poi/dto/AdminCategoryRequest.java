package com.dtrps.modules.poi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A place type (subtype) under one of the service groups. {@code code} is fixed once created, so it
 * is ignored on update.
 */
public record AdminCategoryRequest(
    @NotBlank(message = "Vui lòng chọn nhóm dịch vụ") @Size(max = 50) String groupCode,
    @Size(min = 2, max = 50, message = "Mã loại phải có từ 2 đến 50 ký tự")
        @Pattern(
            regexp = "^[a-z0-9_]+$",
            message = "Mã loại chỉ gồm chữ thường không dấu, số và dấu gạch dưới")
        String code,
    @NotBlank(message = "Vui lòng nhập tên loại")
        @Size(min = 2, max = 100, message = "Tên loại phải có từ 2 đến 100 ký tự")
        String name,
    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự") String description) {}
