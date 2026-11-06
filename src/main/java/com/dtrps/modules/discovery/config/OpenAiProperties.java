
package com.dtrps.modules.discovery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openai")
public record OpenAiProperties(
    String apiKey,
    String baseUrl,
    String embeddingModel
) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String resolvedBaseUrl() {
        return baseUrl == null || baseUrl.isBlank()
            ? "https://api.openai.com/v1"
            : baseUrl;
    }

    public String resolvedModel() {
        return embeddingModel == null || embeddingModel.isBlank()
            ? "text-embedding-3-small"
            : embeddingModel;
    }
}
