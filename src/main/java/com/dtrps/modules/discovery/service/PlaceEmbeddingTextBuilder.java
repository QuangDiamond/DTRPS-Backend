
package com.dtrps.modules.discovery.service;

import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import java.text.Normalizer;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class PlaceEmbeddingTextBuilder {

    public String build(Place place) {
        if (place == null
                || place.getName() == null
                || place.getName().isBlank()) {
            throw new IllegalArgumentException(
                "Place phải có tên hợp lệ"
            );
        }

        Set<String> types =
            new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        if (place.getCategory() != null) {
            types.add(
                normalize(place.getCategory().getName())
            );
        }

        if (place.getCategories() != null) {
            for (PlaceCategory category :
                    place.getCategories()) {
                if (category.getName() != null) {
                    types.add(normalize(category.getName()));
                }
            }
        }

        StringBuilder text = new StringBuilder();

        text.append("Địa điểm: ")
            .append(normalize(place.getName()));

        if (!types.isEmpty()) {
            text.append(". Loại hình: ")
                .append(String.join(", ", types));
        }

        if (place.getDescription() != null
                && !place.getDescription().isBlank()) {
            text.append(". Mô tả: ")
                .append(normalize(place.getDescription()));
        }

        return normalize(text.toString());
    }

    private String normalize(String value) {
        return Normalizer.normalize(
            value.trim().replaceAll("[\\s\\p{Z}]+", " "),
            Normalizer.Form.NFC
        );
    }
}
