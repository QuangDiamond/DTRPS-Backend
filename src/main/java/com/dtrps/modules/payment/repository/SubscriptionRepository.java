package com.dtrps.modules.payment.repository;

import com.dtrps.modules.payment.entity.Subscription;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository
    extends JpaRepository<Subscription, Long>, JpaSpecificationExecutor<Subscription> {

  @Override
  @EntityGraph(attributePaths = "business")
  Page<Subscription> findAll(Specification<Subscription> spec, Pageable pageable);

  /** Khoá đăng ký để hai tab không chọn vượt số lượt quảng cáo */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from Subscription s where s.business.businessId = :businessId"
          + " and s.status = 'ACTIVE'")
  Optional<Subscription> lockActiveByBusiness(@Param("businessId") Long businessId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Subscription s where s.subscriptionId = :id")
  Optional<Subscription> lockById(@Param("id") Long id);

  Optional<Subscription> findFirstByBusinessBusinessIdAndStatus(Long businessId, String status);

  boolean existsByBusinessBusinessIdAndStatus(Long businessId, String status);

  @Query(
      "select s.subscriptionId from Subscription s where s.status = 'ACTIVE'"
          + " and s.endDate <= :now")
  List<Long> findExpiredIds(@Param("now") Instant now);

  /** Đăng ký sắp hết hạn chưa mở cửa sổ gia hạn cho kỳ hiện tại */
  @Query(
      "select s.subscriptionId from Subscription s where s.status = 'ACTIVE'"
          + " and s.endDate <= :limit and not exists ("
          + "select w.windowId from SubscriptionRenewalWindow w"
          + " where w.subscription = s and w.closesAt >= s.endDate)")
  List<Long> findNeedingRenewalWindow(@Param("limit") Instant limit);
}
