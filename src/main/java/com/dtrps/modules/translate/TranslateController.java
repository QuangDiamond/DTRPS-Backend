package com.dtrps.modules.translate;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API dịch theo lô Tiếng Việt → Tiếng Anh dùng riêng cho giao diện Du khách. */
@RestController
@RequestMapping("/api/translate")
public class TranslateController {
  private final TranslateService translateService;

  public TranslateController(TranslateService translateService) {
    this.translateService = translateService;
  }

  public record BatchRequest(List<String> texts) {}

  public record BatchResponse(Map<String, String> translations) {}

  @PostMapping("/vi-en")
  public BatchResponse viToEn(@RequestBody BatchRequest request) {
    List<String> texts = request == null ? List.of() : request.texts();
    return new BatchResponse(translateService.translateBatchViToEn(texts));
  }
}
