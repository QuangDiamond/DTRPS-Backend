package com.dtrps.modules.poi.dto;

import java.util.Map;

/**
 * Catalogue counters; {@code paused} covers PAUSED and MAINTENANCE, {@code byGroup} is by group
 * code. Chưa công khai = {@code pendingApproval} (địa điểm đối tác chờ duyệt niêm yết) + {@code
 * hidden} (mọi địa điểm không hiển thị khác: tạm ngưng, bảo trì, nháp của admin…).
 */
public record AdminPlaceStats(
    long total,
    long active,
    long draft,
    long paused,
    long partner,
    long admin,
    long newThisMonth,
    Map<String, Long> byGroup,
    long pendingApproval,
    long hidden) {}
