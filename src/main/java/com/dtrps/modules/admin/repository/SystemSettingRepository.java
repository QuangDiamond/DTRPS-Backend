package com.dtrps.modules.admin.repository;

import com.dtrps.modules.admin.entity.SystemSetting;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
  /** Every setting with the admin who last saved it (one query for the settings cache). */
  @Query("select s from SystemSetting s left join fetch s.updatedBy")
  List<SystemSetting> findAllWithUpdater();
}
