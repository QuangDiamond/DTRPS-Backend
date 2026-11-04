package com.dtrps.modules.user.repository;

import com.dtrps.modules.user.entity.Preference;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferenceRepository extends JpaRepository<Preference, Long> {
  @EntityGraph(attributePaths = {"interests", "specialNeeds"})
  Optional<Preference> findByUserUserId(Long userId);
}
