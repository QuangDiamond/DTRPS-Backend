package com.dtrps.modules.payment.repository;

import com.dtrps.modules.payment.entity.PaymentIssue;
import jakarta.persistence.LockModeType;
import java.util.Collection;
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

public interface PaymentIssueRepository
    extends JpaRepository<PaymentIssue, Long>, JpaSpecificationExecutor<PaymentIssue> {

  @Override
  @EntityGraph(attributePaths = {"payment", "payment.subscription", "payment.subscription.business"})
  Page<PaymentIssue> findAll(Specification<PaymentIssue> spec, Pageable pageable);

  @EntityGraph(attributePaths = {"payment", "payment.subscription", "payment.subscription.business"})
  Optional<PaymentIssue> findWithDetailsByIssueId(Long issueId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from PaymentIssue i where i.issueId = :id")
  Optional<PaymentIssue> lockById(@Param("id") Long id);

  boolean existsByPaymentPaymentIdAndStatus(Long paymentId, String status);

  Optional<PaymentIssue> findFirstByPaymentPaymentIdAndStatus(Long paymentId, String status);

  /** Phiếu của các đơn, mới nhất trước */
  List<PaymentIssue> findByPaymentPaymentIdInOrderByIssueIdDesc(Collection<Long> paymentIds);

  long countByStatus(String status);
}
