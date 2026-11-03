package com.dtrps.modules.businessowner.repository;

import com.dtrps.modules.businessowner.entity.BusinessListing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessListingRepository extends JpaRepository<BusinessListing, Long> {}
