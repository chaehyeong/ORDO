package com.ordo.importer.service;

import com.ordo.global.common.AcademicTerm;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 파일 속 학년도·학기 글자 읽기 ("2026학년도" "2학기", "2026" "1학기", "여름계절학기" …) */
final class TermText {

    private static final Pattern YEAR = Pattern.compile("(\\d{4})(?:학년도)?");
    private static final Pattern SCHOOL_YEAR = Pattern.compile("(\\d{4})학년도");

    private TermText() {
    }

    /** 문서에서 처음 나오는 "2026학년도" + 같은 줄의 학기. 없으면 IMPORT_UNRECOGNIZED */
    static AcademicTerm find(RawDocument doc) {
        for (List<String> row : doc.rows()) {
            for (int i = 0; i + 1 < row.size(); i++) {
                Matcher year = SCHOOL_YEAR.matcher(RawDocument.compact(row.get(i)));
                Term term = term(row.get(i + 1));
                if (year.matches() && term != null) {
                    return new AcademicTerm(Integer.parseInt(year.group(1)), term);
                }
            }
        }
        throw new BusinessException(ErrorCode.IMPORT_UNRECOGNIZED);
    }

    /** 성적표 첫 행의 "2026", "1학기" 처럼 두 칸이 학년도·학기 모양이면 그 학기, 아니면 null */
    static AcademicTerm of(String yearText, String termText) {
        Matcher m = YEAR.matcher(RawDocument.compact(yearText));
        Term term = term(termText);
        return m.matches() && term != null ? new AcademicTerm(Integer.parseInt(m.group(1)), term) : null;
    }

    static Term term(String text) {
        String t = RawDocument.compact(text);
        if (t.equals("1학기")) {
            return Term.FIRST;
        }
        if (t.equals("2학기")) {
            return Term.SECOND;
        }
        if (t.startsWith("여름") || t.startsWith("하계")) {
            return Term.SUMMER;
        }
        if (t.startsWith("겨울") || t.startsWith("동계")) {
            return Term.WINTER;
        }
        return null;
    }

    static boolean isPageNumber(String text) {
        return RawDocument.compact(text).matches("\\d+/\\d+");
    }

    static int intOrZero(String text) {
        String t = RawDocument.compact(text);
        return t.matches("\\d{1,2}") ? Integer.parseInt(t) : 0;
    }
}
