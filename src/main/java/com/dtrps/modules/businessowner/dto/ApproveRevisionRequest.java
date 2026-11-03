package com.dtrps.modules.businessowner.dto;

import jakarta.validation.constraints.Size;

/** Optional internal note kept with the approval. */
public record ApproveRevisionRequest(
    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự") String note) {}
