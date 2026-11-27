package com.dtrps.modules.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dịch tự động chuỗi Tiếng Việt → Tiếng Anh theo lô cho khu Du khách (mô tả địa điểm, nhận xét,
 * địa chỉ, tiêu đề chuyến đi…) kèm cache RAM để mỗi đoạn chỉ dịch 1 lần duy nhất.
 */
@Service
public class TranslateService {
  private static final Logger log = LoggerFactory.getLogger(TranslateService.class);
  private static final int MAX_CACHE_SIZE = 15_000;
  private static final int MAX_BATCH_SIZE = 40;
  private static final int MAX_TEXT_LENGTH = 2_000;

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
  private final ExecutorService pool = Executors.newFixedThreadPool(6);
  private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

  public TranslateService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Map<String, String> translateBatchViToEn(List<String> rawTexts) {
    Map<String, String> result = new LinkedHashMap<>();
    if (rawTexts == null || rawTexts.isEmpty()) {
      return result;
    }

    List<String> pending = new ArrayList<>();
    for (String item : rawTexts) {
      if (item == null) continue;
      String key = item.trim();
      if (key.isEmpty() || key.length() > MAX_TEXT_LENGTH) continue;
      if (result.containsKey(key)) continue;

      String cached = cache.get(key);
      if (cached != null) {
        result.put(key, cached);
      } else if (pending.size() < MAX_BATCH_SIZE) {
        pending.add(key);
      }
    }

    if (pending.isEmpty()) {
      return result;
    }

    if (cache.size() > MAX_CACHE_SIZE) {
      cache.clear();
    }

    List<CompletableFuture<Map.Entry<String, String>>> futures =
        pending.stream()
            .map(
                text ->
                    CompletableFuture.supplyAsync(() -> Map.entry(text, translateSingle(text)), pool))
            .toList();

    for (CompletableFuture<Map.Entry<String, String>> future : futures) {
      try {
        Map.Entry<String, String> entry = future.join();
        if (entry.getValue() != null && !entry.getValue().isBlank()) {
          cache.put(entry.getKey(), entry.getValue());
          result.put(entry.getKey(), entry.getValue());
        } else {
          result.put(entry.getKey(), entry.getKey());
        }
      } catch (Exception ex) {
        log.debug("Translate batch item failed: {}", ex.getMessage());
      }
    }
    return result;
  }

  private String translateSingle(String text) {
    try {
      String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
      URI uri =
          URI.create(
              "https://translate.googleapis.com/translate_a/single?client=gtx&sl=vi&tl=en&dt=t&q="
                  + encoded);
      HttpRequest request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(5))
              .header("User-Agent", "Mozilla/5.0")
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200 || response.body() == null) {
        return text;
      }
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode sentences = root.get(0);
      if (sentences == null || !sentences.isArray()) {
        return text;
      }
      StringBuilder sb = new StringBuilder();
      for (JsonNode sentence : sentences) {
        if (sentence != null && sentence.isArray() && sentence.size() > 0) {
          String part = sentence.get(0).asText("");
          if (!part.isEmpty()) {
            sb.append(part);
          }
        }
      }
      String translated = sb.toString().trim();
      return translated.isEmpty() ? text : translated;
    } catch (Exception ex) {
      log.debug("Translate single failed: {}", ex.getMessage());
      return text;
    }
  }
}
