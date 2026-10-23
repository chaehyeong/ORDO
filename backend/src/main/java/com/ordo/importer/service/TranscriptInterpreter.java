package com.ordo.importer.service;

import com.ordo.academic.dto.CompletedCourseBulkRequest.TermRef;
import com.ordo.catalog.domain.Classification;
import com.ordo.global.common.AcademicTerm;
import com.ordo.importer.dto.ImportPreviewResponse.GradeCourse;
import com.ordo.importer.dto.ImportPreviewResponse.GradesDraft;
import com.ordo.importer.dto.ImportPreviewResponse.ImportWarning;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "전체 성적 보기" → 이수내역 초안. 과목 행은 뒤에서부터 센 칸 위치로 읽는다
 * (첫 행에만 학년도·학기 2칸이 앞에 붙고, 머리글과 데이터 칸 수가 달라서).
 */
final class TranscriptInterpreter {

    static final String TITLE = "전체성적보기";

    record Result(String studentNumber, GradesDraft draft, List<ImportWarning> warnings) {
    }

    // 뒤에서부터 센 위치: 학수번호, 과목명, 이수구분코드, 이수구분, 평가방법, 학점, 점수, 평점, 등급, 교강사, 폐기사유, 취득구분, 취득대학
    private static final int CODE = 13, NAME = 12, CLASS_CODE = 11, CLASS_TEXT = 10, CREDITS = 8, GRADE = 5, DISCARD = 3;
    private static final Pattern COURSE_CODE = Pattern.compile("[A-Z]{2,}[0-9][0-9A-Z]*");
    private static final Pattern GROUP_SECTION = Pattern.compile("(.+)(G\\d{2})");
    private static final Set<String> GRADES = Set.of("A+", "A0", "B+", "B0", "C+", "C0", "D+", "D0", "F", "P", "NP");
    // 과목 표 뒤에 이어지는 요약 표 제목 → 과목 표 끝
    private static final Set<String> OTHER_SECTIONS = Set.of("원성적및4.5변환성적", "전공/교양별평점", "편입/인정/선수학점");
    private static final Set<String> REPEATED_HEADERS = Set.of("성명", "학과", TITLE);

    private TranscriptInterpreter() {
    }

    static Result interpret(RawDocument doc) {
        List<GradeCourse> courses = new ArrayList<>();
        List<ImportWarning> warnings = new ArrayList<>();
        Set<TermRef> terms = new LinkedHashSet<>();
        boolean inTable = false;
        AcademicTerm current = null;
        int discarded = 0;

        for (int r = 0; r < doc.rows().size(); r++) {
            List<String> row = doc.rows().get(r);
            if (row.isEmpty()) {
                continue;
            }
            String first = RawDocument.compact(row.get(0));
            if (OTHER_SECTIONS.contains(first)) {
                inTable = false;
                continue;
            }
            if (row.stream().map(RawDocument::compact).anyMatch("평점/등급"::equals)) {
                inTable = true;  // 과목 표 머리글 (쪽이 넘어가면 다시 나옴)
                continue;
            }
            if (!inTable || REPEATED_HEADERS.contains(first) || row.size() == 1 && TermText.isPageNumber(row.get(0))) {
                continue;
            }
            if (row.size() < CODE || !COURSE_CODE.matcher(from(row, CODE)).matches()) {
                warnings.add(new ImportWarning("UNPARSED_ROW", (r + 1) + "번째 줄을 읽지 못해 건너뛰었어요."));
                continue;
            }
            if (row.size() >= CODE + 2) {  // 학기가 바뀌는 첫 행만 앞에 학년도·학기 2칸이 더 있다
                AcademicTerm term = TermText.of(from(row, CODE + 2), from(row, CODE + 1));
                if (term != null) {
                    current = term;
                }
            }
            if (current == null) {
                warnings.add(new ImportWarning("UNPARSED_ROW", (r + 1) + "번째 줄의 학기를 알 수 없어 건너뛰었어요."));
                continue;
            }
            if (!from(row, DISCARD).isBlank()) {
                discarded++;  // 재수강 등으로 폐기된 과목
                continue;
            }
            courses.add(course(row, current, warnings));
            terms.add(new TermRef(current.year(), current.term()));
        }
        if (discarded > 0) {
            warnings.add(new ImportWarning("DISCARDED", "폐기된 과목(재수강 등) " + discarded + "건은 제외했어요."));
        }
        int totalCredits = courses.stream().mapToInt(GradeCourse::credits).sum();
        return new Result(doc.valueAfter("학번"),
                new GradesDraft(List.copyOf(terms), courses.size(), totalCredits, courses), warnings);
    }

    private static GradeCourse course(List<String> row, AcademicTerm term, List<ImportWarning> warnings) {
        String[] codeAndSection = splitCode(from(row, CODE));
        String name = from(row, NAME);
        String classificationText = from(row, CLASS_TEXT);
        Classification classification = ClassificationMapper.map(classificationText, from(row, CLASS_CODE));
        String grade = RawDocument.compact(from(row, GRADE));
        List<String> needs = new ArrayList<>();
        if (classification == null) {
            needs.add("CLASSIFICATION");
            warnings.add(new ImportWarning("UNKNOWN_CLASSIFICATION",
                    "이수구분을 골라 주세요: " + name + " (" + classificationText + ")"));
        } else if (classification == Classification.GEN_DISTRIBUTION) {
            needs.add("DISTRIBUTION_AREA");
            warnings.add(new ImportWarning("DISTRIBUTION_AREA_REQUIRED", "배분이수 영역(1~5)을 골라 주세요: " + name));
        }
        if (!GRADES.contains(grade)) {
            needs.add("GRADE");
            warnings.add(new ImportWarning("UNKNOWN_GRADE", "성적을 확인해 주세요: " + name));
            grade = null;
        }
        return new GradeCourse(term.year(), term.term(), codeAndSection[0], codeAndSection[1], name,
                TermText.intOrZero(from(row, CREDITS)), grade, classificationText, classification, null, List.copyOf(needs));
    }

    /** 분반: 끝이 G두자리면 그것, 아니면 마지막 두 자리 ("GEC1102G19" → GEC1102/G19, "CSE20101" → CSE201/01) */
    private static String[] splitCode(String code) {
        Matcher m = GROUP_SECTION.matcher(code);
        if (m.matches()) {
            return new String[]{m.group(1), m.group(2)};
        }
        return code.length() > 2 ? new String[]{code.substring(0, code.length() - 2), code.substring(code.length() - 2)}
                : new String[]{code, null};
    }

    /** 뒤에서 n번째 칸 (1 = 마지막) */
    private static String from(List<String> row, int n) {
        return row.get(row.size() - n);
    }
}
