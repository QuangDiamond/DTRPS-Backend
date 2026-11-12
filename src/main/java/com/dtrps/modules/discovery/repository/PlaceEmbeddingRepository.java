package com.dtrps.modules.discovery.repository;

import com.dtrps.modules.discovery.entity.PlaceEmbedding;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceEmbeddingRepository extends JpaRepository<PlaceEmbedding, Long> {

  List<PlaceEmbedding> findByPlacePlaceIdIn(Collection<Long> placeIds);
}
