package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.PlaceCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceCategoryRepository extends JpaRepository<PlaceCategory, Long> {
  @EntityGraph(attributePaths = "parent")
  Optional<PlaceCategory> findByCode(String code);

  @EntityGraph(attributePaths = "parent")
  List<PlaceCategory> findByStatusOrderByCategoryIdAsc(String status);
}
