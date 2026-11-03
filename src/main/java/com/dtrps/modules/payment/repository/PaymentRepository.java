package com.dtrps.modules.payment.repository;

import com.dtrps.modules.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {}
