package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.SpecialNeed;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialNeedRepository extends JpaRepository<SpecialNeed, Long> {
  List<SpecialNeed> findByCodeIn(Collection<String> codes);
}
