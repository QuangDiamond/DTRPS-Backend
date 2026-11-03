package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.TransportMode;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportModeRepository extends JpaRepository<TransportMode, Long> {
  List<TransportMode> findByCodeIn(Collection<String> codes);
}
