package com.ordo.importer.service;

import java.util.List;

/**
 * 형식과 무관한 "행과 칸의 목록". 칸 글자는 앞뒤 공백을 지운 값.
 * xlsx·csv·txt 는 같은 내용이면 같은 행이 나오도록 Reader 가 맞춘다.
 */
public record RawDocument(List<List<String>> rows) {

    /** 칸이 모두 비어 있는 행은 빈 행(칸 0개)으로 맞춘다 — xlsx 의 빈 병합 영역, csv 의 ",,," 줄 */
    public RawDocument {
        rows = rows.stream().map(row -> row.stream().allMatch(String::isBlank) ? List.<String>of() : row).toList();
    }

    /** 공백을 모두 지운 글자 (머리글·제목 비교용) */
    static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    /** 첫 번째 비어 있지 않은 칸 (문서 제목) */
    String title() {
        return rows.stream().flatMap(List::stream).filter(c -> !c.isBlank()).findFirst().orElse("");
    }

    /** label(공백 무시, 끝의 ':' 무시) 칸 바로 뒤의 비어 있지 않은 칸 */
    String valueAfter(String label) {
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (compact(row.get(i)).replaceAll("[:：]$", "").equals(label)) {
                    for (int j = i + 1; j < row.size(); j++) {
                        if (!row.get(j).isBlank()) {
                            return row.get(j);
                        }
                    }
                }
            }
        }
        return null;
    }
}
