package com.dtrps.modules.payment.repository;

import com.dtrps.modules.payment.entity.SubscriptionRenewalWindow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRenewalWindowRepository
    extends JpaRepository<SubscriptionRenewalWindow, Long> {}
