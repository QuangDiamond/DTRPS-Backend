package com.dtrps.modules.businessowner.dto;

/** Counts for the admin dashboard cards. {@code rejected} is the INACTIVE status. */
public record BusinessProfileStatsResponse(
    long total, long pending, long active, long suspended, long rejected, long newThisMonth) {}
