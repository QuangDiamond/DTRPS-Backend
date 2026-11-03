package com.dtrps.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateProfileRequest(
    @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(min = 2, max = 150, message = "Họ tên phải có từ 2 đến 150 ký tự")
        String name,
    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Số điện thoại không hợp lệ")
        String phoneNumber,
    @Past(message = "Ngày sinh phải ở trong quá khứ") LocalDate dateOfBirth,
    @Size(max = 2048, message = "Đường dẫn ảnh quá dài")
        @Pattern(regexp = "^https://\\S+$", message = "Đường dẫn ảnh không hợp lệ")
        String avatarUrl) {}
