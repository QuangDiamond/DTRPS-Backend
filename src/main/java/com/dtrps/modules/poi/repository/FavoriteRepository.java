package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.Favorite;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FavoriteRepository
    extends JpaRepository<Favorite, Long>, JpaSpecificationExecutor<Favorite> {

  @Override
  @EntityGraph(
      attributePaths = {"place", "place.category", "place.category.parent", "place.province"})
  Page<Favorite> findAll(Specification<Favorite> spec, Pageable pageable);

  Optional<Favorite> findByUserUserIdAndPlacePlaceId(Long userId, Long placeId);

  boolean existsByUserUserIdAndPlacePlaceId(Long userId, Long placeId);
}
