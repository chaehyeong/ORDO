package com.ordo.importer.service;

import com.ordo.catalog.domain.Classification;
import java.util.Map;

/** 파일의 이수구분 글자(또는 성적표의 학교 코드) → enum. 모르면 null (추측하지 않고 사용자가 고르게 함) */
final class ClassificationMapper {

    private static final Map<String, Classification> BY_NAME = Map.of(
            "전공필수", Classification.MAJOR_REQUIRED,
            "전공선택", Classification.MAJOR_ELECTIVE,
            "전공기초", Classification.MAJOR_BASIC,
            "일반선택", Classification.GENERAL_ELECTIVE,
            "자유이수교과", Classification.GEN_FREE,
            "배분이수교과", Classification.GEN_DISTRIBUTION,
            "교직", Classification.TEACHING,
            "교직전선", Classification.TEACHING_MAJOR,
            // ponytail: 추정(명세 9장 16). 근거는 성적표 코드 14 = 명세 3.1 "교양 필수교과 14, 16". 확인되면 주석만 지운다
            "중핵교과", Classification.GEN_REQUIRED,
            "기초교과", Classification.GEN_REQUIRED);

    // 성적표에만 있는 학교 이수구분 코드 (명세 3.1)
    private static final Map<String, Classification> BY_CODE = Map.of(
            "04", Classification.MAJOR_REQUIRED,
            "05", Classification.MAJOR_ELECTIVE,
            "11", Classification.MAJOR_BASIC,
            "08", Classification.GENERAL_ELECTIVE,
            "17", Classification.GEN_FREE,
            "15", Classification.GEN_DISTRIBUTION,
            "06", Classification.TEACHING,
            "20", Classification.TEACHING_MAJOR,
            "14", Classification.GEN_REQUIRED,
            "16", Classification.GEN_REQUIRED);

    private ClassificationMapper() {
    }

    static Classification map(String text, String code) {
        Classification byName = BY_NAME.get(RawDocument.compact(text));
        return byName != null ? byName : BY_CODE.get(code == null ? "" : code.strip());
    }
}
