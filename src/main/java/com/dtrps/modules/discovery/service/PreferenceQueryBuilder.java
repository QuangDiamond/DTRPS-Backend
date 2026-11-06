
package com.dtrps.modules.discovery.service;

import com.dtrps.modules.discovery.service
    .PlanProfileResolver.PlanProfile;

import org.springframework.stereotype.Component;

@Component
public class PreferenceQueryBuilder {

    public String build(PlanProfile profile) {

        StringBuilder text = new StringBuilder(
            "Tôi muốn khám phá những địa điểm du lịch " +
            "có trải nghiệm phù hợp với sở thích của mình."
        );

        if (profile.categoryNames() != null
                && !profile.categoryNames().isEmpty()) {

            text.append(" Các loại trải nghiệm yêu thích: ")
                .append(String.join(
                    ", ",
                    profile.categoryNames()
                ))
                .append(".");
        }

        String companion = profile.companionType();

        if (companion != null
                && !companion.isBlank()
                && !"UNKNOWN".equalsIgnoreCase(companion)) {

            text.append(" Tôi đi du lịch theo hình thức: ")
                .append(companion)
                .append(".");
        }

        return text.toString();
    }
}
