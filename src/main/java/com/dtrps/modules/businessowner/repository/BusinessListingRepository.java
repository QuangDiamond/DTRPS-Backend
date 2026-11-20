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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /**
   * Địa điểm của đối tác đã xác minh tại một tỉnh/thành: niêm yết APPROVED, doanh nghiệp ACTIVE,
   * địa điểm ACTIVE. {@code destination} là tên hoặc mã tỉnh đã viết thường, bỏ khoảng trắng hai đầu.
   */
  @Query(
      "select l from BusinessListing l join fetch l.business b join fetch l.place p"
          + " left join fetch p.category c join p.province pr"
          + " where l.status = 'APPROVED' and b.status = 'ACTIVE' and p.status = 'ACTIVE'"
          + " and (lower(trim(pr.name)) = :destination or lower(trim(pr.code)) = :destination)"
          + " order by l.approvedAt desc nulls last, l.listingId desc")
  List<BusinessListing> findVerifiedInProvince(@Param("destination") String destination);

  boolean existsByPlacePlaceId(Long placeId);

  long countByStatus(String status);

  /** Niêm yết ở trạng thái {@code status} mà địa điểm đang ở {@code placeStatus} */
  long countByStatusAndPlaceStatus(String status, String placeStatus);
}
