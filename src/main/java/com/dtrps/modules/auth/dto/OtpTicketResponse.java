package com.dtrps.modules.auth.dto;

/** The code was right: send {@code ticket} with the last step within {@code expiresInSeconds}. */
public record OtpTicketResponse(String ticket, long expiresInSeconds) {}
