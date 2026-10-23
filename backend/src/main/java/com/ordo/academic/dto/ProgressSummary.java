package com.ordo.academic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 이수현황 계산 결과 (명세 4.6 GET /api/academic/progress, 5장). 홈·마이페이지도 이 값을 쓴다 */
public record ProgressSummary(int admissionYear, MajorInfo major, Total total, List<Area> areas, List<Group> groups,
                              List<RequiredCourse> requiredGeneralCourses, List<Check> checks,
                              boolean graduatable, boolean approximate) {

    public record MajorInfo(Long id, String displayName) {
    }

    /** percent: 소수 첫째 자리 버림 */
    public record Total(int earned, int required, int remaining, double percent) {
    }

    /** required null = 요건 없음(기타). areaCount·requiredAreaCount 는 배분이수에만 있다 */
    public record Area(String key, String label, int earned, Integer required,
                       @JsonInclude(JsonInclude.Include.NON_NULL) Integer areaCount,
                       @JsonInclude(JsonInclude.Include.NON_NULL) Integer requiredAreaCount) {
    }

    public record Group(String key, String label, int earned, int required) {
    }

    public record RequiredCourse(String name, int credits, boolean done) {
    }

    /** 자동 판정하지 않는 부가요건 안내 (status 는 항상 MANUAL) */
    public record Check(String name, String requirement, String status) {
    }
}
