package com.dtrps.modules.poi.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.Province;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.poi.repository.ProvinceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Turns category / province codes from a form into entities, rejecting unknown ones. */
@Service
public class PlaceReferenceResolver {
  private static final String UNKNOWN_CATEGORY = "Loại hình địa điểm không hợp lệ";
  private static final String UNKNOWN_PROVINCE = "Tỉnh/thành không hợp lệ";

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

  public Province province(String code) {
    return provinceRepository
        .findByCode(code.trim())
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, UNKNOWN_PROVINCE));
  }
}
