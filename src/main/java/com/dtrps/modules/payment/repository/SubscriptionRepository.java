package com.dtrps.modules.payment.repository;

import com.dtrps.modules.payment.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {}
