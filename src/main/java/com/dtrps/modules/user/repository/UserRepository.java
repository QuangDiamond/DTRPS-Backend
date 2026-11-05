package com.dtrps.modules.user.repository;

import com.dtrps.modules.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
  @EntityGraph(attributePaths = "roles")
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  @Override
  @EntityGraph(attributePaths = "roles")
  Page<User> findAll(Specification<User> spec, Pageable pageable);

  long countByStatus(String status);

  long countByCreatedAtGreaterThanEqual(Instant since);

  @Query("select count(distinct u) from User u join u.roles r where r.roleName = :roleName")
  long countByRole(@Param("roleName") String roleName);

  /** Ids of active users holding the role, e.g. every admin to notify */
  @Query(
      "select distinct u.userId from User u join u.roles r"
          + " where r.roleName = :roleName and u.status = 'ACTIVE'")
  List<Long> findActiveIdsByRole(@Param("roleName") String roleName);
}
