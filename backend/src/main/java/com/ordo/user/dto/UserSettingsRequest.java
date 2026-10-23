package com.ordo.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

/** 마이페이지 설정. 다른 PATCH 와 같이 생략·null 이면 그대로. todoAlarmTime = 시간 없는 할 일의 알림 기준 시각(HH:mm) */
public record UserSettingsRequest(Boolean notificationEnabled, @JsonFormat(pattern = "HH:mm") LocalTime todoAlarmTime) {
}
