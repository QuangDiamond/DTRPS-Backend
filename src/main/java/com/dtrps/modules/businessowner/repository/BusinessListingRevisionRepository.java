package com.dtrps.modules.businessowner.repository;

import com.dtrps.modules.businessowner.entity.BusinessListingRevision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BusinessListingRevisionRepository
    extends JpaRepository<BusinessListingRevision, Long>,
        JpaSpecificationExecutor<BusinessListingRevision> {
  @EntityGraph(
      attributePaths = {"listing", "listing.business", "category", "category.parent", "province"})
  List<BusinessListingRevision> findByListingListingIdOrderByRevisionIdDesc(Long listingId);

  @EntityGraph(
      attributePaths = {
        "listing",
        "listing.business",
        "listing.business.user",
        "listing.place",
        "category",
        "category.parent",
        "province"
      })
  Optional<BusinessListingRevision> findWithDetailsByRevisionId(Long revisionId);

  @Override
  @EntityGraph(
      attributePaths = {"listing", "listing.business", "category", "category.parent", "province"})
  Page<BusinessListingRevision> findAll(
      Specification<BusinessListingRevision> spec, Pageable pageable);

  boolean existsByListingListingIdAndStatus(Long listingId, String status);

  long countByStatus(String status);
}
