package com.dtrps.modules.payment.repository;

import com.dtrps.modules.payment.entity.AdPackage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdPackageRepository extends JpaRepository<AdPackage, Long> {
  List<AdPackage> findByStatusOrderBySortOrderAscPackageIdAsc(String status);

  List<AdPackage> findAllByOrderBySortOrderAscPackageIdAsc();
}
