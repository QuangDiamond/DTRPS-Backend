package com.dtrps.modules.notification.dto;

import java.util.List;
import java.util.Map;

/**
 * A page of the signed-in user's notifications, newest first, plus how many are still unread.
 * {@code categories}: every tab the user has notifications in (tab order) → unread in that tab.
 */
public record NotificationPage(
    List<NotificationResponse> items,
    long total,
    int page,
    int size,
    long unread,
    Map<String, Long> categories) {}
