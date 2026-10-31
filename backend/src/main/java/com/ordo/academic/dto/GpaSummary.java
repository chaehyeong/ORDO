package com.ordo.academic.dto;

import com.ordo.global.common.Term;
import java.util.List;

/**
 * 평점 평균 (명세 4.6 GET /api/academic/gpa, 5장). 4.3 만점, 소수 셋째 자리 반올림.
 * gpa·majorGpa 는 평점 대상 과목(P·NP·성적 미정 제외)이 없으면 null.
 * currentSemesterLabel: 졸업 로드맵의 "지금 여기"(예: "3-2"). 기록에서 계산하므로 항상 값이 있다 (명세 5장)
 */
public record GpaSummary(Double gpa, Double majorGpa, int gradedCredits, int earnedCredits, String currentSemesterLabel,
                         List<TermGpa> terms) {

    /** 학기별 값은 그 학기 기록 전부로 계산 (재수강으로 대체된 과목도 포함). semesterLabel: "1-1" 같은 학기 번호, 계절학기는 null */
    public record TermGpa(int year, Term term, String semesterLabel, Double gpa, int gradedCredits, int earnedCredits) {
    }
}
