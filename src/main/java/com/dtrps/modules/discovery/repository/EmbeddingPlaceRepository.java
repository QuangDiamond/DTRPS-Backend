
package com.dtrps.modules.discovery.repository;

import com.dtrps.modules.poi.entity.Place;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface EmbeddingPlaceRepository
        extends Repository<Place, Long> {

    @Query("""
        SELECT DISTINCT p
        FROM Place p
        LEFT JOIN FETCH p.category
        LEFT JOIN FETCH p.categories
        WHERE p.status = 'ACTIVE'
        ORDER BY p.placeId
        """)
    List<Place> findActiveWithCategories();
}
