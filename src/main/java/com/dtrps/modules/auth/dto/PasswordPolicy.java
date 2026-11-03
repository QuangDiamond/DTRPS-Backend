package com.dtrps.modules.auth.dto;

public final class PasswordPolicy {
  public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,72}$";
  public static final String MESSAGE = "Mật khẩu từ 8 đến 72 ký tự, gồm chữ hoa, chữ thường và số";

  private PasswordPolicy() {}
}
