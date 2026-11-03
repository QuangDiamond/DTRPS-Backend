package com.dtrps.modules.businessowner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code reasonCode} groups rejections for filtering; {@code partnerFeedback} is what the owner
 * reads to fix the change; {@code note} is the required review note (stored in review_note,
 * internal to admins).
 */
public record RejectRevisionRequest(
    @NotBlank(message = "Vui lòng chọn nhóm lý do")
        @Pattern(
            regexp = "pricing|location|photos|duplicate|other",
            message = "Nhóm lý do không hợp lệ")
        String reasonCode,
    @NotBlank(message = "Vui lòng nhập góp ý cho đối tác")
        @Size(max = 2000, message = "Góp ý tối đa 2000 ký tự")
        String partnerFeedback,
    @NotBlank(message = "Vui lòng nhập ghi chú duyệt (review note)")
        @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
        String note) {}
