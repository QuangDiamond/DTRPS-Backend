package com.dtrps.modules.poi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A place the admin creates or edits directly (no partner, no review). On create, missing lists
 * mean none; on update, a missing list (null) keeps what the place has. Opening hours, when given,
 * cover all 7 days. {@code status} defaults to DRAFT on create and is kept on update.
 */
public record AdminPlaceRequest(
    @NotBlank(message = "Vui lòng nhập tên địa điểm")
        @Size(min = 2, max = 255, message = "Tên địa điểm phải có từ 2 đến 255 ký tự")
        String name,
    /** Main type alone (older clients); ignored when categoryCodes is given */
    @Size(max = 50) String categoryCode,
    /** Every type ticked, the first one is the main type */
    @Size(max = 30, message = "Chọn quá nhiều loại hình")
        List<@Size(max = 50) String> categoryCodes,
    @NotBlank(message = "Vui lòng chọn tỉnh/thành") @Size(max = 50) String provinceCode,
    @NotBlank(message = "Vui lòng nhập địa chỉ")
        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address,
    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự") String description,
    @NotNull(message = "Vui lòng nhập vĩ độ")
        @DecimalMin(value = "-90", message = "Vĩ độ phải từ -90 đến 90")
        @DecimalMax(value = "90", message = "Vĩ độ phải từ -90 đến 90")
        Double latitude,
    @NotNull(message = "Vui lòng nhập kinh độ")
        @DecimalMin(value = "-180", message = "Kinh độ phải từ -180 đến 180")
        @DecimalMax(value = "180", message = "Kinh độ phải từ -180 đến 180")
        Double longitude,
    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Số điện thoại không hợp lệ") String phone,
    @Size(max = 500, message = "Đường dẫn website quá dài")
        @Pattern(
            regexp = "^https?://\\S+$",
            message = "Website phải bắt đầu bằng http:// hoặc https://")
        String website,
    @Pattern(
            regexp = "^(ACTIVE|DRAFT|PAUSED|MAINTENANCE)$",
            message = "Trạng thái phải là ACTIVE, DRAFT, PAUSED hoặc MAINTENANCE")
        String status,
    @Valid @Size(max = 7, message = "Tối đa 7 ngày trong tuần") List<OpeningHourDto> openingHours,
    @Valid @Size(max = 20, message = "Tối đa 20 mức giá") List<PriceDto> prices,
    @Valid @Size(max = 10, message = "Tối đa 10 ảnh cho một địa điểm") List<ImageDto> images) {}
