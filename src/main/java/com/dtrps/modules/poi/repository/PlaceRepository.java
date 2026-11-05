package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.Place;
import java.time.Instant;
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

public interface PlaceRepository
    extends JpaRepository<Place, Long>, JpaSpecificationExecutor<Place> {

  @Override
  @EntityGraph(attributePaths = {"category", "category.parent", "province"})
  Page<Place> findAll(Specification<Place> spec, Pageable pageable);

  @EntityGraph(attributePaths = {"category", "category.parent", "province"})
  Optional<Place> findWithDetailsByPlaceId(Long placeId);

  long countByStatus(String status);

  long countByCreatedAtGreaterThanEqual(Instant since);

  /** [group code, count] for places whose category sits under a group */
  @Query(
      "select c.parent.code, count(p) from Place p join p.category c"
          + " where c.parent is not null group by c.parent.code")
  List<Object[]> countByGroup();

  /** [category id, count] for every type in use, as a place's main type or one of its others */
  @Query(
      "select c.categoryId, count(distinct p) from Place p join p.categories c"
          + " group by c.categoryId")
  List<Object[]> countByCategory();

  /** A place uses the type, as its main type or one of its others. */
  @Query(
      "select count(p) > 0 from Place p where p.category.categoryId = :categoryId"
          + " or exists (select 1 from Place q join q.categories c"
          + " where q = p and c.categoryId = :categoryId)")
  boolean usesCategory(@Param("categoryId") Long categoryId);
}
