package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.PlaceCategory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceCategoryRepository extends JpaRepository<PlaceCategory, Long> {
  @EntityGraph(attributePaths = "parent")
  Optional<PlaceCategory> findByCode(String code);

  List<PlaceCategory> findByCodeIn(Collection<String> codes);

  @EntityGraph(attributePaths = "parent")
  List<PlaceCategory> findByStatusOrderByCategoryIdAsc(String status);

  /** Subtypes only (a group's children), active, in seed order — for the interests picker. */
  List<PlaceCategory> findByStatusAndParentIsNotNullOrderByCategoryIdAsc(String status);

  @EntityGraph(attributePaths = "parent")
  List<PlaceCategory> findAllByOrderByCategoryIdAsc();

  boolean existsByCodeIgnoreCase(String code);

  boolean existsByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCaseAndCategoryIdNot(String name, Long categoryId);
}
