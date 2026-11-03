package com.dtrps.modules.poi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** One weekday's hours. dayOfWeek: 1 = Monday … 7 = Sunday. Times are HH:mm, null when closed. */
public record OpeningHourDto(
    @NotNull(message = "Thiếu ngày trong tuần")
        @Min(value = 1, message = "Ngày trong tuần từ 1 (Thứ 2) đến 7 (Chủ nhật)")
        @Max(value = 7, message = "Ngày trong tuần từ 1 (Thứ 2) đến 7 (Chủ nhật)")
        Integer dayOfWeek,
    @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "Giờ mở cửa dạng HH:mm")
        String openTime,
    @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "Giờ đóng cửa dạng HH:mm")
        String closeTime,
    boolean closed) {}
