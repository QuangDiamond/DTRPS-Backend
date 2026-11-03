package com.dtrps.modules.user.dto;

/** Account counters for the admin user list; "this month" is in Vietnam time. */
public record UserStatsResponse(
    long total,
    long active,
    long suspended,
    long travellers,
    long businessOwners,
    long admins,
    long newThisMonth) {}
