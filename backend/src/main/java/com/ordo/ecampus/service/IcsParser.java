package com.ordo.ecampus.service;

import com.ordo.ecampus.dto.EcampusEventResponse;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canvas 캘린더 피드(.ics)에서 VEVENT 의 제목·날짜·링크만 꺼낸다.
 * Canvas 는 "DTSTART;VALUE=DATE:20261008T000000" 처럼 표준을 어긴 날짜를 내보내서 너그럽게 읽는다.
 */
final class IcsParser {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final Pattern ESCAPED = Pattern.compile("\\\\([\\\\,;nN])");
    private static final Pattern TITLE_AND_COURSE = Pattern.compile("^(.*\\S)\\s*\\[([^\\[\\]]+)]$");  // "과제명 [과목명 분반]"

    private IcsParser() {
    }

    static List<EcampusEventResponse> parse(byte[] ics) {
        List<EcampusEventResponse> events = new ArrayList<>();
        Map<String, String> properties = null;  // VEVENT 안의 "이름 → 한 줄 전체"
        for (String line : unfold(ics).split("\r?\n")) {
            if (line.equals("BEGIN:VEVENT")) {
                properties = new HashMap<>();
            } else if (line.equals("END:VEVENT") && properties != null) {
                addEvent(events, properties);
                properties = null;
            } else if (properties != null && line.indexOf(':') > 0) {
                properties.putIfAbsent(line.split("[;:]", 2)[0], line);
            }
        }
        return events;
    }

    // 깨진 일정 하나 때문에 피드 전체를 버리지 않는다
    private static void addEvent(List<EcampusEventResponse> events, Map<String, String> properties) {
        String uid = value(properties.get("UID"));
        String summary = value(properties.get("SUMMARY"));
        String dtstart = properties.get("DTSTART");
        if (uid == null || summary == null || dtstart == null) {
            return;
        }
        try {
            LocalDateTime start = startOf(dtstart);
            String title = unescape(summary);
            Matcher matcher = TITLE_AND_COURSE.matcher(title);
            boolean hasCourse = matcher.matches();
            events.add(new EcampusEventResponse(uid,
                    hasCourse ? matcher.group(1) : title,
                    hasCourse ? matcher.group(2) : null,
                    start.toLocalDate(),
                    isAllDay(dtstart) ? null : start.toLocalTime(),
                    value(properties.get("URL"))));
        } catch (DateTimeException | IndexOutOfBoundsException e) {
            // 날짜를 못 읽는 일정은 건너뛴다
        }
    }

    // VALUE=DATE(하루 종일)면 앞 8자리만 날짜로, ...Z 는 UTC → 한국 시간, 그 외(시간대 표시 없음)는 한국 시간으로 본다
    private static LocalDateTime startOf(String dtstart) {
        String value = value(dtstart);
        if (isAllDay(dtstart)) {
            return LocalDate.parse(value.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay();
        }
        if (value.endsWith("Z")) {
            return LocalDateTime.parse(value.substring(0, value.length() - 1), DATE_TIME)
                    .atOffset(ZoneOffset.UTC).atZoneSameInstant(SEOUL).toLocalDateTime();
        }
        return LocalDateTime.parse(value, DATE_TIME);
    }

    private static boolean isAllDay(String dtstart) {
        String params = dtstart.substring(0, dtstart.indexOf(':')) + ";";
        return params.contains(";VALUE=DATE;") || value(dtstart).length() == 8;
    }

    private static String value(String line) {
        return line == null ? null : line.substring(line.indexOf(':') + 1);
    }

    private static String unescape(String text) {
        return ESCAPED.matcher(text).replaceAll(m -> {
            String c = m.group(1);
            return c.equalsIgnoreCase("n") ? " " : Matcher.quoteReplacement(c);
        });
    }

    // 75바이트마다 접힌 줄(줄바꿈 + 공백/탭)을 잇는다. 한글 글자 중간에서 접혀도 되도록 바이트 단위로 이은 뒤 UTF-8 로 읽는다
    private static String unfold(byte[] ics) {
        String bytes = new String(ics, StandardCharsets.ISO_8859_1).replaceAll("\r?\n[ \t]", "");
        return new String(bytes.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }
}
