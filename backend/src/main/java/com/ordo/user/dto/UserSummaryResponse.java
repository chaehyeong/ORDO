package com.ordo.user.dto;

import com.ordo.global.common.Term;

/** 마이페이지 요약 (명세 4.2). credits 는 학사 프로필 미완성이면 null */
public record UserSummaryResponse(TermInfo term, int courseCount, int pendingAssignmentCount, Credits credits) {

    public record TermInfo(int year, Term term, String label) {
    }

    public record Credits(int earned, int required, double percent) {
    }
}
