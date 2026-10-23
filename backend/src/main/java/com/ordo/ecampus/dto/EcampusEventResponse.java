package com.ordo.ecampus.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;

/** e캠퍼스 과제 마감 1건 (읽기 전용). time 이 null 이면 그날 23:59 마감(Canvas 의 하루 종일 표시) */
public record EcampusEventResponse(String id, String title, String courseName, LocalDate date,
                                   @JsonFormat(pattern = "HH:mm") LocalTime time, String url) {
}
