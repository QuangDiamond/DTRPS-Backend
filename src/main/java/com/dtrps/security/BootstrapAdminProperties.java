package com.dtrps.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.bootstrap")
public record BootstrapAdminProperties(String adminEmail, String adminPassword, String adminName) {}
