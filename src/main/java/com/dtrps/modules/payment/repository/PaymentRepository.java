package com.dtrps.modules.payment.repository;

import com.dtrps.modules.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
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

public interface PaymentRepository
    extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

  @Override
  @EntityGraph(attributePaths = {"subscription", "subscription.business", "user"})
  Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

  /** Khoá dòng để webhook, trang kết quả và job không xử lý trùng một đơn */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from Payment p where p.paymentGateway = :gateway"
          + " and p.transactionReference = :reference")
  Optional<Payment> lockByReference(
      @Param("gateway") String gateway, @Param("reference") String reference);

  @EntityGraph(
      attributePaths = {"subscription", "subscription.business", "subscription.business.user"})
  Optional<Payment> findByPaymentGatewayAndTransactionReference(String gateway, String reference);

  @EntityGraph(attributePaths = {"subscription", "subscription.business", "user"})
  Optional<Payment> findWithDetailsByPaymentId(Long paymentId);

  @EntityGraph(attributePaths = "subscription")
  Page<Payment> findBySubscriptionBusinessBusinessIdOrderByPaymentIdDesc(
      Long businessId, Pageable pageable);

  @EntityGraph(attributePaths = "subscription")
  List<Payment> findBySubscriptionBusinessBusinessIdAndStatusOrderByPaymentIdDesc(
      Long businessId, String status);

  /** Đơn đang chờ cần hỏi lại PayOS (đơn đã đánh dấu cần kiểm tra thì để admin xử lý) */
  @Query(
      "select p.transactionReference from Payment p where p.status = 'PENDING'"
          + " and p.paymentGateway = :gateway and p.needsReview = false and p.createdAt < :before"
          + " order by p.paymentId asc")
  List<String> findReconcilable(
      @Param("gateway") String gateway, @Param("before") Instant before, Pageable pageable);

  @Query(value = "select nextval('payment_order_code_seq')", nativeQuery = true)
  long nextOrderCode();

  @Query(
      "select coalesce(sum(p.amount), 0) from Payment p"
          + " where p.status = 'SUCCESS' and p.paidAt >= :from")
  BigDecimal revenueSince(@Param("from") Instant from);

  long countByStatus(String status);

  long countByNeedsReviewTrue();
}
