package com.dtrps.modules.businessowner.repository;

import com.dtrps.modules.businessowner.entity.BusinessListing;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BusinessListingRepository
    extends JpaRepository<BusinessListing, Long>, JpaSpecificationExecutor<BusinessListing> {

  @EntityGraph(
      attributePaths = {
        "business",
        "place",
        "place.category",
        "place.category.parent",
        "place.province"
      })
  List<BusinessListing> findByBusinessUserUserIdOrderByListingIdDesc(Long userId);

  @EntityGraph(
      attributePaths = {
        "business",
        "place",
        "place.category",
        "place.category.parent",
        "place.province"
      })
  Optional<BusinessListing> findWithDetailsByListingId(Long listingId);

  @Override
  @EntityGraph(
      attributePaths = {
        "business",
        "place",
        "place.category",
        "place.category.parent",
        "place.province"
      })
  Page<BusinessListing> findAll(Specification<BusinessListing> spec, Pageable pageable);

  /** Listings (with their business) of the given places — places without one are admin POIs */
  @EntityGraph(attributePaths = {"business", "place"})
  List<BusinessListing> findByPlacePlaceIdIn(Collection<Long> placeIds);

  boolean existsByPlacePlaceId(Long placeId);

  long countByStatus(String status);
}
