package com.ordo.importer.service;

import com.ordo.global.common.AcademicTerm;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.importer.dto.ImportPreviewResponse.EnrolledCourse;
import com.ordo.importer.dto.ImportPreviewResponse.ImportWarning;
import com.ordo.importer.dto.ImportPreviewResponse.TimetableDraft;
import com.ordo.timetable.dto.TimetableEntryBulkRequest.Entry;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "수강신청확인서" → 시간표 초안. 형식(xlsx·csv·txt)과 무관하게 RawDocument 만 본다 */
final class EnrollmentInterpreter {

    static final String TITLE = "수강신청확인서";

    record Result(String studentNumber, TimetableDraft draft, List<ImportWarning> warnings) {
    }

    // "교수명 화15:00-16:50 전205" (교수명·강의실은 없을 수 있음)
    private static final Pattern SESSION = Pattern.compile(
            "(?<prof>[^,]*?)\\s*(?<day>[월화수목금토일])\\s*(?<start>\\d{1,2}:\\d{2})\\s*[-~]\\s*(?<end>\\d{1,2}:\\d{2})\\s*(?<room>[^,]*)");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final String DAYS = "월화수목금토일";

    private EnrollmentInterpreter() {
    }

    static Result interpret(RawDocument doc) {
        AcademicTerm term = TermText.find(doc);
        List<String> header = null;
        int code = -1, name = -1, credits = -1, classification = -1, retake = -1, sessions = -1;
        List<EnrolledCourse> courses = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        List<ImportWarning> warnings = new ArrayList<>();

        for (int r = 0; r < doc.rows().size(); r++) {
            List<String> row = doc.rows().get(r);
            if (header == null) {
                if (row.stream().map(RawDocument::compact).anyMatch("학수번호-분반"::equals)) {
                    header = row.stream().map(RawDocument::compact).toList();
                    code = header.indexOf("학수번호-분반");
                    name = header.indexOf("과목명");
                    credits = header.indexOf("학점");
                    classification = header.indexOf("이수구분");
                    retake = header.indexOf("재수강여부");
                    sessions = header.indexOf("교강사,강의시간,강의실");
                    if (name < 0 || sessions < 0) {
                        throw new BusinessException(ErrorCode.IMPORT_UNRECOGNIZED);
                    }
                }
                continue;
            }
            if (row.isEmpty() || row.size() == 1 && TermText.isPageNumber(row.get(0))) {
                continue;
            }
            if (row.size() <= Math.max(name, sessions) || !row.get(0).matches("\\d+")) {
                warnings.add(new ImportWarning("UNPARSED_ROW", (r + 1) + "번째 줄을 읽지 못해 건너뛰었어요."));
                continue;
            }
            String[] codeAndSection = splitCode(cell(row, code));
            String courseName = row.get(name);
            List<Entry> courseEntries = sessions(row.get(sessions), courseName, codeAndSection[0]);
            if (courseEntries.isEmpty()) {
                warnings.add(new ImportWarning("NO_TIME", "시간 없는 과목: " + courseName));
            }
            entries.addAll(courseEntries);
            String classificationText = cell(row, classification);
            courses.add(new EnrolledCourse(codeAndSection[0], codeAndSection[1], courseName, TermText.intOrZero(cell(row, credits)),
                    classificationText, ClassificationMapper.map(classificationText, null),
                    !cell(row, retake).isBlank(), courseEntries.size()));
        }
        if (header == null) {
            throw new BusinessException(ErrorCode.IMPORT_UNRECOGNIZED);
        }
        addOverlapWarnings(entries, warnings);
        int totalCredits = courses.stream().mapToInt(EnrolledCourse::credits).sum();
        return new Result(doc.valueAfter("학번"),
                new TimetableDraft(term.year(), term.term(), courses.size(), totalCredits, courses, entries), warnings);
    }

    // 쉼표로 나눈 조각마다 수업 1개. 교수명이 빠진 조각은 앞 조각의 교수명을 쓴다
    private static List<Entry> sessions(String text, String courseName, String courseCode) {
        List<Entry> entries = new ArrayList<>();
        String professor = null;
        for (String part : text.split(",")) {
            Matcher m = SESSION.matcher(part.strip());
            if (!m.find()) {
                continue;
            }
            if (!m.group("prof").isBlank()) {
                professor = m.group("prof").strip();
            }
            LocalTime start = LocalTime.parse(m.group("start"), TIME);
            LocalTime end = LocalTime.parse(m.group("end"), TIME);
            if (!end.isAfter(start)) {
                continue;
            }
            String room = m.group("room").strip();
            entries.add(new Entry(courseName, courseCode, DAYS.indexOf(m.group("day")) + 1, start, end,
                    room.isEmpty() ? null : room, professor, null));
        }
        return entries;
    }

    private static void addOverlapWarnings(List<Entry> entries, List<ImportWarning> warnings) {
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                Entry a = entries.get(i), b = entries.get(j);
                if (a.dayOfWeek().equals(b.dayOfWeek()) && a.startTime().isBefore(b.endTime()) && b.startTime().isBefore(a.endTime())) {
                    warnings.add(new ImportWarning("OVERLAP", "시간이 겹쳐요: " + a.courseName() + ", " + b.courseName()));
                }
            }
        }
    }

    /** "GEC0103-G09" → {"GEC0103", "G09"} */
    private static String[] splitCode(String text) {
        int dash = text.lastIndexOf('-');
        return dash < 0 ? new String[]{text, null} : new String[]{text.substring(0, dash), text.substring(dash + 1)};
    }

    private static String cell(List<String> row, int index) {
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }
}
