package com.dtrps.modules.user.repository;

import com.dtrps.modules.user.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {}
