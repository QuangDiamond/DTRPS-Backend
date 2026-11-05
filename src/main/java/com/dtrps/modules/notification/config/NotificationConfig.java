package com.dtrps.modules.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on the scheduled heartbeat that keeps live event streams open. */
@Configuration
@EnableScheduling
public class NotificationConfig {}
