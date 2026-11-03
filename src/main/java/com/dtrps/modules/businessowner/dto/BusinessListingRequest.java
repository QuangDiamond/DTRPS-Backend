package com.dtrps.modules.businessowner.dto;

import com.dtrps.modules.poi.dto.ImageDto;
import com.dtrps.modules.poi.dto.OpeningHourDto;
import com.dtrps.modules.poi.dto.PriceDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The place a business owner submits for approval (one per business). */
public record BusinessListingRequest(
    @NotBlank(message = "Vui lòng nhập tên địa điểm")
        @Size(min = 2, max = 255, message = "Tên địa điểm phải có từ 2 đến 255 ký tự")
        String name,
    @NotBlank(message = "Vui lòng chọn loại hình địa điểm") @Size(max = 50) String categoryCode,
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
    /** Bỏ trống = không có giờ mở cửa */
    @Valid @Size(max = 7, message = "Tối đa 7 ngày trong tuần") List<OpeningHourDto> openingHours,
    @Valid @Size(max = 20, message = "Tối đa 20 mức giá") List<PriceDto> prices,
    @Valid @Size(max = 10, message = "Tối đa 10 ảnh cho một địa điểm") List<ImageDto> images) {}
