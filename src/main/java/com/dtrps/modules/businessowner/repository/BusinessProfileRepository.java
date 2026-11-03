package com.dtrps.modules.businessowner.repository;

import com.dtrps.modules.businessowner.entity.BusinessProfile;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BusinessProfileRepository
    extends JpaRepository<BusinessProfile, Long>, JpaSpecificationExecutor<BusinessProfile> {
  @EntityGraph(attributePaths = "user")
  Optional<BusinessProfile> findByUserUserId(Long userId);

  @EntityGraph(attributePaths = "user")
  Optional<BusinessProfile> findWithUserByBusinessId(Long businessId);

  @Override
  @EntityGraph(attributePaths = "user")
  Page<BusinessProfile> findAll(Specification<BusinessProfile> spec, Pageable pageable);

  boolean existsByUserUserId(Long userId);

  long countByStatus(String status);

  long countByCreatedAtGreaterThanEqual(Instant since);

  boolean existsByTaxCode(String taxCode);

  boolean existsByTaxCodeAndBusinessIdNot(String taxCode, Long businessId);
}
