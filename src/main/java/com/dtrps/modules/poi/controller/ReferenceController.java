package com.dtrps.modules.poi.controller;

import com.dtrps.modules.poi.dto.CategoryGroupResponse;
import com.dtrps.modules.poi.dto.ReferenceItem;
import com.dtrps.modules.poi.service.ReferenceDataService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reference lists for forms and filters. Any signed-in user may read them. */
@RestController
@RequestMapping("/api/reference")
public class ReferenceController {
  private final ReferenceDataService referenceDataService;

  public ReferenceController(ReferenceDataService referenceDataService) {
    this.referenceDataService = referenceDataService;
  }

  @GetMapping("/provinces")
  public List<ReferenceItem> provinces() {
    return referenceDataService.provinces();
  }

  @GetMapping("/categories")
  public List<CategoryGroupResponse> categories() {
    return referenceDataService.categories();
  }
}
