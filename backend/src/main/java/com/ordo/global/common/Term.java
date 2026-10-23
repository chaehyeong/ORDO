package com.ordo.global.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 학기 (명세 3.1). 시간표·이수내역 공용 */
@Getter
@RequiredArgsConstructor
public enum Term {
    FIRST("1학기"),
    SECOND("2학기"),
    SUMMER("여름계절학기"),
    WINTER("겨울계절학기");

    private final String label;
}
