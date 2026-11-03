package com.dtrps.modules.poi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The whole week at once: exactly 7 rows, day 1 (Monday) to 7 (Sunday), closed days flagged. */
public record OpeningHoursRequest(
    @NotNull(message = "Vui lòng gửi giờ mở cửa của cả 7 ngày")
        @Size(min = 7, max = 7, message = "Vui lòng gửi giờ mở cửa của cả 7 ngày")
        @Valid
        List<OpeningHourDto> days) {}
