package com.dtrps.modules.user.dto;

import com.dtrps.modules.user.entity.Role;
import com.dtrps.modules.user.entity.User;
import java.time.LocalDate;
import java.util.List;

public record UserResponse(
    Long id,
    String name,
    String email,
    String phoneNumber,
    LocalDate dateOfBirth,
    String avatarUrl,
    String status,
    List<String> roles) {
  public static UserResponse from(User user) {
    List<String> roles = user.getRoles().stream().map(Role::getRoleName).sorted().toList();
    return new UserResponse(
        user.getUserId(),
        user.getFullName(),
        user.getEmail(),
        user.getPhoneNumber(),
        user.getDateOfBirth(),
        user.getAvatarUrl(),
        user.getStatus(),
        roles);
  }
}
