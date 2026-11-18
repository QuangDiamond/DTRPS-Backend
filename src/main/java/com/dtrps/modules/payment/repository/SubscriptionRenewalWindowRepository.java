package com.dtrps.modules.payment.repository;

import com.dtrps.modules.payment.entity.SubscriptionRenewalWindow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRenewalWindowRepository
    extends JpaRepository<SubscriptionRenewalWindow, Long> {
  Optional<SubscriptionRenewalWindow> findFirstBySubscriptionSubscriptionIdAndStatus(
      Long subscriptionId, String status);

  List<SubscriptionRenewalWindow> findBySubscriptionSubscriptionIdAndStatus(
      Long subscriptionId, String status);
}
