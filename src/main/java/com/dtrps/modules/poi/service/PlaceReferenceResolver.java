package com.dtrps.modules.poi.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.Province;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.poi.repository.ProvinceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Turns category / province codes from a form into entities, rejecting unknown ones. */
@Service
public class PlaceReferenceResolver {
  private static final String UNKNOWN_CATEGORY = "Loại hình địa điểm không hợp lệ";
  private static final String UNKNOWN_PROVINCE = "Tỉnh/thành không hợp lệ";
  private static final String NO_TYPE = "Vui lòng chọn ít nhất một loại hình";

  private final PlaceCategoryRepository categoryRepository;
  private final ProvinceRepository provinceRepository;

  public PlaceReferenceResolver(
      PlaceCategoryRepository categoryRepository, ProvinceRepository provinceRepository) {
    this.categoryRepository = categoryRepository;
    this.provinceRepository = provinceRepository;
  }

  /** Places use an active subtype, never one of the 4 groups. */
  public PlaceCategory subtype(String code) {
    return categoryRepository
        .findByCode(code.trim())
        .filter(found -> found.getParent() != null)
        .filter(found -> ReferenceDataService.ACTIVE.equals(found.getStatus()))
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, UNKNOWN_CATEGORY));
  }

  /**
   * The types ticked on a form, in order and without repeats; the first one is the main type. They
   * may span several groups (a resort that also runs a restaurant). {@code fallback} is the single
   * type older clients send. At least one type is required.
   */
  public List<PlaceCategory> types(List<String> codes, String fallback) {
    List<String> wanted = new ArrayList<>();
    if (codes != null) {
      codes.stream().filter(code -> code != null && !code.isBlank()).forEach(wanted::add);
    }
    if (wanted.isEmpty() && fallback != null && !fallback.isBlank()) {
      wanted.add(fallback);
    }
    if (wanted.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, NO_TYPE);
    }
    Map<Long, PlaceCategory> types = new LinkedHashMap<>();
    for (String code : wanted) {
      PlaceCategory type = subtype(code);
      types.putIfAbsent(type.getCategoryId(), type);
    }
    return List.copyOf(types.values());
  }

  public Province province(String code) {
    return provinceRepository
        .findByCode(code.trim())
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, UNKNOWN_PROVINCE));
  }
}
