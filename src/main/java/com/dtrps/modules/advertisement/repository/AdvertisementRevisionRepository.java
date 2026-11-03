package com.dtrps.modules.advertisement.repository;

import com.dtrps.modules.advertisement.entity.AdvertisementRevision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvertisementRevisionRepository
    extends JpaRepository<AdvertisementRevision, Long> {}
