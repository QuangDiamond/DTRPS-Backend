package com.dtrps.modules.admin.repository;

import com.dtrps.modules.admin.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {}
