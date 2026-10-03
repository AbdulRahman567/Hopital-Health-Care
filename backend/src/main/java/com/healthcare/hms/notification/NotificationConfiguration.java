package com.healthcare.hms.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the notification module's externalised settings (ENGINEERING_RULES section 4). */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfiguration {}
