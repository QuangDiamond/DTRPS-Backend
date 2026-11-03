package com.dtrps.modules.poi.dto;

import java.util.Map;

/**
 * Catalogue counters; {@code paused} covers PAUSED and MAINTENANCE, {@code byGroup} is by group
 * code.
 */
public record AdminPlaceStats(
    long total,
    long active,
    long draft,
    long paused,
    long partner,
    long admin,
    long newThisMonth,
    Map<String, Long> byGroup) {}
