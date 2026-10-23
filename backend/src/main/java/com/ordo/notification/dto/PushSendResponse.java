package com.ordo.notification.dto;

/** 기기별 발송 결과 수. 404·410(만료된 구독)은 failed 로 세고 구독을 지운다 */
public record PushSendResponse(int sent, int failed) {
}
