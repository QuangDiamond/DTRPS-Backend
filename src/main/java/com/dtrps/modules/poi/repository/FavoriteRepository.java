package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {}
