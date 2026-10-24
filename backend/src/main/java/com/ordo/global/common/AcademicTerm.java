package com.ordo.global.common;

import java.time.LocalDate;
import java.time.ZoneId;

/** 학년도 + 학기 (예: 2026년 2학기) */
public record AcademicTerm(int year, Term term) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 3~8월 → 그해 1학기, 9~12월 → 그해 2학기, 1~2월 → 전년도 2학기. 계절학기는 사용자가 직접 고를 때만 (명세 3.3)
    public static AcademicTerm of(LocalDate date) {
        int month = date.getMonthValue();
        if (month <= 2) {
            return new AcademicTerm(date.getYear() - 1, Term.SECOND);
        }
        return new AcademicTerm(date.getYear(), month <= 8 ? Term.FIRST : Term.SECOND);
    }

    /** 서버 시간대와 상관없이 한국 날짜 기준 현재 학기 */
    public static AcademicTerm now() {
        return of(LocalDate.now(SEOUL));
    }

    public String label() {
        return year + "년 " + term.getLabel();
    }
}
