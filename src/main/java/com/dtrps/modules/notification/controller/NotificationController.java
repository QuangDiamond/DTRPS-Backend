package com.dtrps.modules.notification.controller;

import com.dtrps.modules.notification.dto.NotificationPage;
import com.dtrps.modules.notification.dto.NotificationResponse;
import com.dtrps.modules.notification.service.LiveEventHub;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** The signed-in user's notifications and their live event stream. */
@RestController
@RequestMapping("/api/notifications")
@Validated
@Tag(name = "Notifications", description = "Thông báo và cập nhật realtime")
public class NotificationController {
  private static final String ADMIN_AUTHORITY = "ROLE_" + NotificationService.ROLE_ADMIN;

  private final NotificationService notificationService;
  private final LiveEventHub hub;

  public NotificationController(NotificationService notificationService, LiveEventHub hub) {
    this.notificationService = notificationService;
    this.hub = hub;
  }

  @GetMapping
  @Operation(summary = "Thông báo của tôi, mới nhất trước")
  public NotificationPage list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "false") boolean unread,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return notificationService.list(CurrentUser.id(jwt), unread, page, size);
  }

  @GetMapping("/unread-count")
  @Operation(summary = "Số thông báo chưa đọc")
  public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("unread", notificationService.unreadCount(CurrentUser.id(jwt)));
  }

  @PostMapping("/{id}/read")
  @Operation(summary = "Đánh dấu một thông báo đã đọc")
  public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    return notificationService.markRead(CurrentUser.id(jwt), id);
  }

  @PostMapping("/read-all")
  @Operation(summary = "Đánh dấu tất cả đã đọc")
  public Map<String, Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("updated", notificationService.markAllRead(CurrentUser.id(jwt)));
  }

  /**
   * Server-Sent Events: a "ready" event, then a "notification" event (JSON LiveEvent) whenever
   * something concerning this user changes. The stream closes after 30 minutes; reconnect.
   */
  @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "Luồng sự kiện realtime (SSE)")
  public SseEmitter stream(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
    boolean admin =
        authentication.getAuthorities().stream()
            .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
    return hub.subscribe(CurrentUser.id(jwt), admin);
  }
}
