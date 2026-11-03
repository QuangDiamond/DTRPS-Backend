package com.dtrps.modules.poi.repository;

import com.dtrps.modules.poi.entity.Interest;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterestRepository extends JpaRepository<Interest, Long> {
  List<Interest> findByCodeIn(Collection<String> codes);
}
