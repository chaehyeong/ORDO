package com.ordo.academic.dto;

import com.ordo.global.common.Term;
import java.util.List;

/**
 * 평점 평균 (명세 4.6 GET /api/academic/gpa, 5장). 4.3 만점, 소수 셋째 자리 반올림.
 * gpa·majorGpa 는 평점 대상 과목(P·NP·성적 미정 제외)이 없으면 null
 */
public record GpaSummary(Double gpa, Double majorGpa, int gradedCredits, int earnedCredits, List<TermGpa> terms) {

    /** 학기별 값은 그 학기 기록 전부로 계산 (재수강으로 대체된 과목도 포함) */
    public record TermGpa(int year, Term term, Double gpa, int gradedCredits, int earnedCredits) {
    }
}
