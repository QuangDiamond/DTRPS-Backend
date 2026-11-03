package com.dtrps.modules.poi.controller;

import com.dtrps.modules.poi.dto.AdminCategoryGroup;
import com.dtrps.modules.poi.dto.AdminCategoryGroup.AdminCategory;
import com.dtrps.modules.poi.dto.AdminCategoryRequest;
import com.dtrps.modules.poi.dto.CategoryStatusRequest;
import com.dtrps.modules.poi.service.AdminCategoryService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Place types under the 4 service groups. Codes and names are unique; codes never change. */
@RestController
@RequestMapping("/api/admin/place-categories")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {
  private final AdminCategoryService categoryService;

  public AdminCategoryController(AdminCategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @GetMapping
  public List<AdminCategoryGroup> list() {
    return categoryService.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AdminCategory create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AdminCategoryRequest request) {
    return categoryService.create(CurrentUser.id(jwt), request);
  }

  @PutMapping("/{id}")
  public AdminCategory update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody AdminCategoryRequest request) {
    return categoryService.update(CurrentUser.id(jwt), id, request);
  }

  @PutMapping("/{id}/status")
  public AdminCategory changeStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody CategoryStatusRequest request) {
    return categoryService.changeStatus(CurrentUser.id(jwt), id, request.status());
  }
}
