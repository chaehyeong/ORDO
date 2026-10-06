package com.ordo.catalog.domain;

/** 명세서 3.1의 이수구분. 교과목 검색과 이수내역에서 함께 사용한다. */
public enum Classification {
    MAJOR_BASIC,
    MAJOR_REQUIRED,
    MAJOR_ELECTIVE,
    GEN_REQUIRED,
    GEN_DISTRIBUTION,
    GEN_FREE,
    GENERAL_ELECTIVE,
    TEACHING,
    TEACHING_MAJOR
}
