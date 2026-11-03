package com.dtrps.modules.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code url} is the value Cloudinary shows on its API Keys page: {@code
 * cloudinary://<api_key>:<api_secret>@<cloud_name>}. Blank means uploads are disabled.
 */
@ConfigurationProperties("app.cloudinary")
public record CloudinaryProperties(String url, String folder) {}
