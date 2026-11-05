package com.dtrps.modules.notification.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.dto.NotificationPage;
import com.dtrps.modules.notification.dto.NotificationResponse;
import com.dtrps.modules.user.entity.Notification;
import com.dtrps.modules.user.repository.NotificationRepository;
import com.dtrps.modules.user.repository.UserRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Saves notifications and pushes them to open browser tabs. Pushes wait for the surrounding
 * transaction to commit, so whoever reloads on an event already sees the new data — and nothing is
 * pushed for a change that was rolled back.
 */
@Service
public class NotificationService {
  public static final String ROLE_ADMIN = "ADMIN";
  private static final String NOT_FOUND = "Không tìm thấy thông báo";

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final LiveEventHub hub;
  private final Clock clock;

  public NotificationService(
      NotificationRepository notificationRepository,
      UserRepository userRepository,
      LiveEventHub hub,
      Clock clock) {
    this.notificationRepository = notificationRepository;
    this.userRepository = userRepository;
    this.hub = hub;
    this.clock = clock;
  }

  // ===== Sending =====

  /** Saves the notice for one user and pushes it to their open tabs. */
  @Transactional
  public void notifyUser(Long userId, Notice notice) {
    Notification saved = save(userId, notice);
    afterCommit(() -> hub.sendToUser(userId, notice.toEvent(saved.getNotificationId())));
  }

  /**
   * Saves the notice for every active admin except {@code actorId} (the admin who acted, or null),
   * pushes it to their tabs, and sends the acting admin's other tabs a silent refresh.
   */
  @Transactional
  public void notifyAdmins(Notice notice, Long actorId) {
    List<Long> admins = userRepository.findActiveIdsByRole(ROLE_ADMIN);
    for (Long adminId : admins) {
      if (adminId.equals(actorId)) {
        continue;
      }
      Notification saved = save(adminId, notice);
      afterCommit(() -> hub.sendToUser(adminId, notice.toEvent(saved.getNotificationId())));
    }
    if (actorId != null) {
      afterCommit(() -> hub.sendToUser(actorId, notice.toEvent(null)));
    }
  }

  /**
   * Refresh-only event for every admin tab (nothing saved), e.g. a review was finished: queues and
   * open review pages update, and a second admin on the same item sees it was handled.
   */
  public void refreshAdmins(Notice notice) {
    afterCommit(() -> hub.sendToAdmins(notice.toEvent(null), null));
  }

  /** Refresh-only event for one user's tabs (nothing saved). */
  public void refreshUser(Long userId, Notice notice) {
    afterCommit(() -> hub.sendToUser(userId, notice.toEvent(null)));
  }

  // ===== Reading =====

  @Transactional(readOnly = true)
  public NotificationPage list(Long userId, boolean unreadOnly, int page, int size) {
    PageRequest request = PageRequest.of(page, size);
    Page<Notification> found =
        unreadOnly
            ? notificationRepository
                .findByUserUserIdAndIsReadFalseOrderByCreatedAtDescNotificationIdDesc(
                    userId, request)
            : notificationRepository.findByUserUserIdOrderByCreatedAtDescNotificationIdDesc(
                userId, request);
    return new NotificationPage(
        found.getContent().stream().map(NotificationService::toResponse).toList(),
        found.getTotalElements(),
        page,
        size,
        notificationRepository.countByUserUserIdAndIsReadFalse(userId));
  }

  @Transactional(readOnly = true)
  public long unreadCount(Long userId) {
    return notificationRepository.countByUserUserIdAndIsReadFalse(userId);
  }

  @Transactional
  public NotificationResponse markRead(Long userId, Long notificationId) {
    Notification notification =
        notificationRepository
            .findByNotificationIdAndUserUserId(notificationId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    notification.setIsRead(true);
    return toResponse(notification);
  }

  @Transactional
  public int markAllRead(Long userId) {
    return notificationRepository.markAllRead(userId);
  }

  // ===== Helpers =====

  private Notification save(Long userId, Notice notice) {
    Notification notification = new Notification();
    notification.setUser(userRepository.getReferenceById(userId));
    notification.setType(notice.type());
    notification.setTitle(notice.title());
    notification.setMessage(notice.message());
    notification.setLink(notice.link());
    notification.setIsRead(false);
    notification.setSentAt(clock.instant());
    return notificationRepository.save(notification);
  }

  private static void afterCommit(Runnable push) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              push.run();
            }
          });
    } else {
      push.run();
    }
  }

  private static NotificationResponse toResponse(Notification notification) {
    return new NotificationResponse(
        notification.getNotificationId(),
        notification.getType(),
        notification.getTitle(),
        notification.getMessage(),
        notification.getLink(),
        Boolean.TRUE.equals(notification.getIsRead()),
        notification.getCreatedAt());
  }
}
