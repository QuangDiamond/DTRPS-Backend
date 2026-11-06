
package com.dtrps.modules.discovery.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.discovery.config.OpenAiProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OpenAiEmbeddingGateway implements EmbeddingGateway {

    private static final Logger log =
        LoggerFactory.getLogger(OpenAiEmbeddingGateway.class);

    private static final int DIMENSIONS = 1536;
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 3;

    private static final String UNAVAILABLE =
        "Không gọi được dịch vụ AI Embedding";

    private final OpenAiProperties properties;
    private final RestClient restClient;

    public OpenAiEmbeddingGateway(OpenAiProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory factory =
            new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));

        this.restClient = RestClient.builder()
            .baseUrl(properties.resolvedBaseUrl())
            .requestFactory(factory)
            .build();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {

        if (texts == null || texts.isEmpty()
                || texts.size() > MAX_BATCH_SIZE
                || texts.stream().anyMatch(
                    text -> text == null || text.isBlank())) {

            throw new IllegalArgumentException(
                "Danh sách embedding phải có 1–100 văn bản hợp lệ"
            );
        }

        if (!properties.configured()) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Thiếu cấu hình OPENAI_API_KEY"
            );
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                EmbeddingRequest request = new EmbeddingRequest(
                    properties.resolvedModel(),
                    texts,
                    "float"
                );

                EmbeddingResponse response = restClient.post()
                    .uri("/embeddings")
                    .headers(headers ->
                        headers.setBearerAuth(properties.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EmbeddingResponse.class);

                return extractVectors(response, texts.size());

            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();

                log.warn(
                    "OpenAI Embedding HTTP status={}, attempt={}",
                    status, attempt
                );

                boolean retryable =
                    status == 429 || status >= 500;

                if (!retryable || attempt == MAX_ATTEMPTS) {
                    throw new ApiException(
                        HttpStatus.BAD_GATEWAY, UNAVAILABLE
                    );
                }

                backoff(attempt);

            } catch (RestClientException e) {
                log.warn(
                    "OpenAI Embedding connection failed, attempt={}",
                    attempt
                );

                if (attempt == MAX_ATTEMPTS) {
                    throw new ApiException(
                        HttpStatus.BAD_GATEWAY, UNAVAILABLE
                    );
                }

                backoff(attempt);
            }
        }

        throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
    }

    private List<float[]> extractVectors(
        EmbeddingResponse response,
        int expectedCount
    ) {
        if (response == null || response.data() == null
                || response.data().size() != expectedCount) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "OpenAI trả về số lượng vector không hợp lệ"
            );
        }

        float[][] vectors = new float[expectedCount][];

        for (EmbeddingData item : response.data()) {
            if (item == null
                    || item.index() < 0
                    || item.index() >= expectedCount
                    || item.embedding() == null
                    || item.embedding().length != DIMENSIONS
                    || vectors[item.index()] != null) {

                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "OpenAI trả về vector không hợp lệ"
                );
            }

            vectors[item.index()] = item.embedding();
        }

        for (float[] vector : vectors) {
            if (vector == null) {
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Thiếu vector trong kết quả OpenAI"
                );
            }
        }

        return Arrays.asList(vectors);
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(500L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "Yêu cầu Embedding bị gián đoạn"
            );
        }
    }

    private record EmbeddingRequest(
        String model,
        List<String> input,
        @JsonProperty("encoding_format") String encodingFormat
    ) {}

    private record EmbeddingResponse(
        List<EmbeddingData> data
    ) {}

    private record EmbeddingData(
        int index,
        float[] embedding
    ) {}
}
