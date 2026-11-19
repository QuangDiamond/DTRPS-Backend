package com.dtrps.modules.weather.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WeatherProperties.class)
public class WeatherConfig {}
