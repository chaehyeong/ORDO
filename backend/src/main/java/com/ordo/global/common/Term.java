package com.ordo.global.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 학기 (명세 3.1). 시간표·이수내역 공용. 선언 순서 = 한 학년도 안의 시간 순서라 정렬에 그대로 쓴다 */
@Getter
@RequiredArgsConstructor
public enum Term {
    FIRST("1학기"),
    SUMMER("여름계절학기"),
    SECOND("2학기"),
    WINTER("겨울계절학기");

    private final String label;
}
