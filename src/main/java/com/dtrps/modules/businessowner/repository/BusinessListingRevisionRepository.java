package com.dtrps.modules.businessowner.repository;

import com.dtrps.modules.businessowner.entity.BusinessListingRevision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessListingRevisionRepository
    extends JpaRepository<BusinessListingRevision, Long> {}
