package com.ordo.ecampus.dto;

import java.time.LocalDateTime;
import java.util.List;

/** connected=false 면 events 는 빈 배열, syncedAt 은 null. syncedAt 은 e캠퍼스에서 마지막으로 받아 온 시각 */
public record EcampusEventsResponse(boolean connected, LocalDateTime syncedAt, List<EcampusEventResponse> events) {
}
