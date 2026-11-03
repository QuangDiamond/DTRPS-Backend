package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.Province;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinceRepository extends JpaRepository<Province, Long> {
  Optional<Province> findByCode(String code);
}
