
package com.dtrps.modules.discovery.service;

import java.util.List;

public interface EmbeddingGateway {

    List<float[]> embedAll(List<String> texts);

    default float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }
}
