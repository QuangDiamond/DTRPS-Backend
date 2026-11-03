package com.dtrps.modules.poi.service;

import com.dtrps.modules.poi.dto.CategoryGroupResponse;
import com.dtrps.modules.poi.dto.ReferenceItem;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.poi.repository.ProvinceRepository;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists the clients use for pickers: provinces and the two-level place category tree. */
@Service
public class ReferenceDataService {
  public static final String ACTIVE = "ACTIVE";

  private final ProvinceRepository provinceRepository;
  private final PlaceCategoryRepository categoryRepository;

  public ReferenceDataService(
      ProvinceRepository provinceRepository, PlaceCategoryRepository categoryRepository) {
    this.provinceRepository = provinceRepository;
    this.categoryRepository = categoryRepository;
  }

  /** Sorted by Vietnamese name. */
  @Transactional(readOnly = true)
  public List<ReferenceItem> provinces() {
    Collator vietnamese = Collator.getInstance(Locale.forLanguageTag("vi"));
    return provinceRepository.findAll().stream()
        .map(province -> new ReferenceItem(province.getCode(), province.getName()))
        .sorted(Comparator.comparing(ReferenceItem::name, vietnamese))
        .toList();
  }

  /** Active groups in seed order, each with its active subtypes. */
  @Transactional(readOnly = true)
  public List<CategoryGroupResponse> categories() {
    List<PlaceCategory> active = categoryRepository.findByStatusOrderByCategoryIdAsc(ACTIVE);
    Map<Long, List<ReferenceItem>> subtypes = new LinkedHashMap<>();
    List<PlaceCategory> groups = new ArrayList<>();
    for (PlaceCategory category : active) {
      if (category.getParent() == null) {
        groups.add(category);
      } else {
        subtypes
            .computeIfAbsent(category.getParent().getCategoryId(), id -> new ArrayList<>())
            .add(new ReferenceItem(category.getCode(), category.getName()));
      }
    }
    return groups.stream()
        .map(
            group ->
                new CategoryGroupResponse(
                    group.getCode(),
                    group.getName(),
                    subtypes.getOrDefault(group.getCategoryId(), List.of())))
        .toList();
  }
}
