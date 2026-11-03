package com.dtrps.modules.poi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** A reference price, e.g. priceType "ROOM_NIGHT", "TICKET", "AVG_MEAL". */
public record PriceDto(
    @NotBlank(message = "Vui lòng nhập loại giá")
        @Size(max = 100, message = "Loại giá tối đa 100 ký tự")
        String priceType,
    @NotNull(message = "Vui lòng nhập mức giá")
        @DecimalMin(value = "0", message = "Mức giá không được âm")
        BigDecimal amount,
    @Pattern(regexp = "^[A-Z]{3}$", message = "Mã tiền tệ gồm 3 chữ in hoa, ví dụ VND")
        String currency,
    @Size(max = 500, message = "Ghi chú giá tối đa 500 ký tự") String description) {}
