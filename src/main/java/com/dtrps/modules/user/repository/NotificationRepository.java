package com.dtrps.modules.user.repository;

import com.dtrps.modules.user.entity.Notification;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  Page<Notification> findByUserUserIdOrderByCreatedAtDescNotificationIdDesc(
      Long userId, Pageable pageable);

  Page<Notification> findByUserUserIdAndIsReadFalseOrderByCreatedAtDescNotificationIdDesc(
      Long userId, Pageable pageable);

  long countByUserUserIdAndIsReadFalse(Long userId);

  Optional<Notification> findByNotificationIdAndUserUserId(Long notificationId, Long userId);

  @Modifying
  @Query(
      "update Notification n set n.isRead = true where n.user.userId = :userId and n.isRead = false")
  int markAllRead(@Param("userId") Long userId);
}
