package com.dtrps.modules.auth.dto;

import com.dtrps.modules.user.dto.UserResponse;
import com.dtrps.modules.user.entity.RoleName;
import com.dtrps.modules.user.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code admin} is the shape Admin Web expects; it is present only for admin accounts. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserResponse user,
    AdminProfileResponse admin) {
  private static final String TOKEN_TYPE = "Bearer";

  public static AuthResponse of(User user, TokenPair tokens) {
    UserResponse profile = UserResponse.from(user);
    AdminProfileResponse admin =
        profile.roles().contains(RoleName.ADMIN.name())
            ? new AdminProfileResponse(profile.name(), profile.email(), RoleName.ADMIN.name())
            : null;
    return new AuthResponse(
        tokens.accessToken(),
        tokens.refreshToken(),
        TOKEN_TYPE,
        tokens.expiresIn(),
        profile,
        admin);
  }
}
