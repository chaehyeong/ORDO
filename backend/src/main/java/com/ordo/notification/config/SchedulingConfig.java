package com.ordo.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 1분마다 알림 발송(NotificationService.sendDue). 테스트 등에서 app.notification.scheduler-enabled=false 면 끈다 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.notification.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
