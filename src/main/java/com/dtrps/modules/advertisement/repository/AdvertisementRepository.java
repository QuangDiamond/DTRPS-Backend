package com.dtrps.modules.advertisement.repository;

import com.dtrps.modules.advertisement.entity.Advertisement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {}
