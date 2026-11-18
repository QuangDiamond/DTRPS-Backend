package com.dtrps.modules.advertisement.repository;

import com.dtrps.modules.advertisement.entity.Advertisement;
import java.time.Instant;
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

public interface AdvertisementRepository
    extends JpaRepository<Advertisement, Long>, JpaSpecificationExecutor<Advertisement> {

  @Override
  @EntityGraph(
      attributePaths = {"listing", "listing.place", "listing.business", "subscription"})
  Page<Advertisement> findAll(Specification<Advertisement> spec, Pageable pageable);

  @EntityGraph(
      attributePaths = {
        "listing",
        "listing.place",
        "listing.business",
        "listing.business.user",
        "subscription"
      })
  Optional<Advertisement> findWithDetailsByAdvertisementId(Long advertisementId);

  /** Quảng cáo đang chiếm lượt của một đăng ký */
  @EntityGraph(attributePaths = {"listing", "listing.place"})
  List<Advertisement> findBySubscriptionSubscriptionIdAndStatusIn(
      Long subscriptionId, Collection<String> statuses);

  /** [subscriptionId, số lượt đang dùng] */
  @Query(
      "select a.subscription.subscriptionId, count(a) from Advertisement a"
          + " where a.subscription.subscriptionId in :ids and a.status in ('PUBLISHED', 'SUSPENDED')"
          + " group by a.subscription.subscriptionId")
  List<Object[]> usedSlots(@Param("ids") Collection<Long> ids);

  /** Chỉ những địa điểm còn đủ mọi điều kiện mới được hiện ở trang chủ */
  @Query(
      "select a from Advertisement a join fetch a.listing l join fetch l.place p"
          + " left join fetch p.province left join fetch p.category"
          + " where a.status = 'PUBLISHED' and a.startAt <= :now and a.endAt > :now"
          + " and a.subscription.status = 'ACTIVE' and l.status = 'APPROVED'"
          + " and l.business.status = 'ACTIVE' and p.status = 'ACTIVE'")
  List<Advertisement> findFeatured(@Param("now") Instant now);
}
