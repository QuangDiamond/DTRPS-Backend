package com.dtrps.modules.user.dto;

import com.dtrps.modules.user.entity.Role;
import com.dtrps.modules.user.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** {@code statusNote} is the admin's lock reason while SUSPENDED (admin views only). */
public record UserResponse(
    Long id,
    String name,
    String email,
    String phoneNumber,
    LocalDate dateOfBirth,
    String avatarUrl,
    String status,
    List<String> roles,
    Instant createdAt,
    String statusNote) {
  public static UserResponse from(User user) {
    return from(user, null);
  }

  public static UserResponse from(User user, String statusNote) {
    List<String> roles = user.getRoles().stream().map(Role::getRoleName).sorted().toList();
    return new UserResponse(
        user.getUserId(),
        user.getFullName(),
        user.getEmail(),
        user.getPhoneNumber(),
        user.getDateOfBirth(),
        user.getAvatarUrl(),
        user.getStatus(),
        roles,
        user.getCreatedAt(),
        statusNote);
  }
}
