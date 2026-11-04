package com.dtrps.modules.poi.service;

import com.dtrps.modules.poi.dto.CategoryGroupResponse;
import com.dtrps.modules.poi.dto.ProvinceResponse;
import com.dtrps.modules.poi.dto.ReferenceItem;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.repository.InterestRepository;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.poi.repository.ProvinceRepository;
import com.dtrps.modules.poi.repository.SpecialNeedRepository;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reference data for provinces, place category groups, interests and special needs. */
@Service
public class ReferenceDataService {
  public static final String ACTIVE = "ACTIVE";

  /** The 4 service groups, in the order every client shows them */
  public static final List<String> GROUP_ORDER =
      List.of("lodging", "fnb", "attraction", "shopping");

  private final ProvinceRepository provinceRepository;
  private final PlaceCategoryRepository categoryRepository;
  private final InterestRepository interestRepository;
  private final SpecialNeedRepository specialNeedRepository;

  public ReferenceDataService(
      ProvinceRepository provinceRepository,
      PlaceCategoryRepository categoryRepository,
      InterestRepository interestRepository,
      SpecialNeedRepository specialNeedRepository) {
    this.provinceRepository = provinceRepository;
    this.categoryRepository = categoryRepository;
    this.interestRepository = interestRepository;
    this.specialNeedRepository = specialNeedRepository;
  }

  /** Returns provinces sorted by Vietnamese display name. */
  @Transactional(readOnly = true)
  public List<ProvinceResponse> provinces() {
    Collator vietnamese = Collator.getInstance(Locale.forLanguageTag("vi"));
    return provinceRepository.findAll().stream()
        .map(
            province ->
                new ProvinceResponse(
                    province.getProvinceId(), province.getCode(), province.getName()))
        .sorted(Comparator.comparing(ProvinceResponse::name, vietnamese))
        .toList();
  }

  /**
   * Active groups in {@link #GROUP_ORDER}, each with its active subtypes. A group without any
   * active subtype is left out: a place cannot be registered under it.
   */
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
        .filter(group -> subtypes.containsKey(group.getCategoryId()))
        .sorted(Comparator.comparingInt(group -> groupRank(group.getCode())))
        .map(
            group ->
                new CategoryGroupResponse(
                    group.getCode(),
                    group.getName(),
                    subtypes.getOrDefault(group.getCategoryId(), List.of())))
        .toList();
  }

  /** Travel interests for the traveller's preferences screen and search filters. */
  @Transactional(readOnly = true)
  public List<ReferenceItem> interests() {
    return interestRepository.findAllByOrderByInterestIdAsc().stream()
        .map(interest -> new ReferenceItem(interest.getCode(), interest.getName()))
        .toList();
  }

  /** Special needs (dietary, accessibility…) for the traveller's preferences screen. */
  @Transactional(readOnly = true)
  public List<ReferenceItem> specialNeeds() {
    return specialNeedRepository.findAllByOrderBySpecialNeedIdAsc().stream()
        .map(need -> new ReferenceItem(need.getCode(), need.getName()))
        .toList();
  }

  /** Known groups first in their fixed order, anything else after them */
  public static int groupRank(String code) {
    int rank = GROUP_ORDER.indexOf(code);
    return rank < 0 ? GROUP_ORDER.size() : rank;
  }
}
