package com.dtrps.modules.auth.dto;

import com.dtrps.modules.user.entity.User;

public record RotatedSession(User user, TokenPair tokens) {}
