package com.dtrps.modules.notification.dto;

import java.util.Map;

/** What a service wants to tell someone: saved as a notification and pushed live. */
public record Notice(
    String type, String title, String message, String link, Map<String, Object> data) {

  public LiveEvent toEvent(Long notificationId) {
    return new LiveEvent(type, notificationId, title, message, link, data);
  }
}
