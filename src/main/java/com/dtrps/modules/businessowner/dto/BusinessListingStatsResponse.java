package com.dtrps.modules.businessowner.dto;

public record BusinessListingStatsResponse(
    long total, long pending, long approved, long rejected, long suspended) {}
