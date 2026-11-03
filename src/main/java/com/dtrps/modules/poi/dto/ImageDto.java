package com.dtrps.modules.poi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A place photo. At most one is primary (the cover); if none is, the first one becomes it. */
public record ImageDto(
    @NotBlank(message = "Thiếu đường dẫn ảnh")
        @Size(max = 2048, message = "Đường dẫn ảnh quá dài")
        @Pattern(regexp = "^https://\\S+$", message = "Đường dẫn ảnh phải bắt đầu bằng https://")
        String url,
    boolean primary) {}
