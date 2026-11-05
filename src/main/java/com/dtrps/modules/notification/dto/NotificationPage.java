package com.dtrps.modules.notification.dto;

import java.util.List;

/** A page of the signed-in user's notifications, newest first, plus how many are still unread. */
public record NotificationPage(
    List<NotificationResponse> items, long total, int page, int size, long unread) {}
