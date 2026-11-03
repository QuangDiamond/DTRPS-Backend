package com.dtrps.modules.user.controller;

import com.dtrps.common.PageResponse;
import com.dtrps.modules.user.dto.UpdateUserStatusRequest;
import com.dtrps.modules.user.dto.UserResponse;
import com.dtrps.modules.user.service.UserService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
  private static final int MAX_PAGE_SIZE = 100;

  private final UserService userService;

  public AdminUserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public PageResponse<UserResponse> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return userService.list(PageRequest.of(page, size, Sort.by("userId")));
  }

  @GetMapping("/{id}")
  public UserResponse get(@PathVariable Long id) {
    return userService.getProfile(id);
  }

  @PutMapping("/{id}/status")
  public UserResponse updateStatus(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long id,
      @Valid @RequestBody UpdateUserStatusRequest request) {
    return userService.updateStatus(CurrentUser.id(jwt), id, request.status());
  }
}
