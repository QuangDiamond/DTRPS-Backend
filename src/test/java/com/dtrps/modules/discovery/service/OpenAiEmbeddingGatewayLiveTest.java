
package com.dtrps.modules.discovery.service;

import com.dtrps.modules.discovery.config.OpenAiProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiEmbeddingGatewayLiveTest {

    @Test
    @EnabledIfEnvironmentVariable(
        named = "RUN_OPENAI_LIVE_TEST",
        matches = "true"
    )
    void shouldGenerate1536DimensionVector() throws Exception {

        Properties env = new Properties();

        try (var reader = Files.newBufferedReader(
                Path.of(".env.local"),
                StandardCharsets.UTF_8)) {
            env.load(reader);
        }

        String key = env.getProperty("OPENAI_API_KEY");

        assertNotNull(key, "Thiếu OPENAI_API_KEY");

        key = key.trim().replaceAll("^['\"]|['\"]$", "");

        assertFalse(key.isBlank());

        OpenAiProperties properties = new OpenAiProperties(
            key,
            "https://api.openai.com/v1",
            "text-embedding-3-small"
        );

        EmbeddingGateway gateway =
            new OpenAiEmbeddingGateway(properties);

        float[] vector = gateway.embed(
            "Tôi thích du lịch biển và khám phá ẩm thực."
        );

        assertNotNull(vector);
        assertEquals(1536, vector.length);
        assertTrue(Float.isFinite(vector[0]));

        System.out.println(
            "JAVA EMBEDDING SUCCESS - Dimensions: "
                + vector.length
        );
    }
}
