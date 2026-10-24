package com.ordo.ecampus.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordo.ecampus.dto.EcampusEventResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 실제 Canvas 피드 형식을 본뜬 가짜 데이터로 검사한다 */
class IcsParserTest {

    @Test
    void parsesCanvasAllDayAssignment() {
        List<EcampusEventResponse> events = IcsParser.parse(ics(
                "UID:event-assignment-1",
                "DTSTAMP:20260917T080100Z",
                "DTSTART;VALUE=DATE:20261008T000000",  // Canvas 가 내보내는 '시간 붙은 DATE'
                "DTEND;VALUE=DATE:20261008T000000",
                "DESCRIPTION:긴 설명\\, 무시한다",
                "SUMMARY:중간 레포트 [디자인씽킹 01분반]",
                "URL:https://khcanvas.khu.ac.kr/calendar?include_contexts=course_1&month",
                " =10&year=2026#assignment_1"));

        assertThat(events).containsExactly(new EcampusEventResponse("event-assignment-1", "중간 레포트",
                "디자인씽킹 01분반", LocalDate.of(2026, 10, 8), null,
                "https://khcanvas.khu.ac.kr/calendar?include_contexts=course_1&month=10&year=2026#assignment_1"));
    }

    @Test
    void convertsUtcTimeToSeoulAndUnescapesTitle() {
        EcampusEventResponse event = IcsParser.parse(ics(
                "UID:event-assignment-2",
                "DTSTART:20261009T150000Z",  // UTC 15:00 = 한국 다음 날 00:00
                "SUMMARY:퀴즈\\, 1차")).get(0);

        assertThat(event.date()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(event.time()).isEqualTo(LocalTime.of(0, 0));
        assertThat(event.title()).isEqualTo("퀴즈, 1차");
        assertThat(event.courseName()).isNull();
    }

    @Test
    void joinsLineFoldedInsideHangulCharacter() {
        byte[] summary = "SUMMARY:[과제1] 글쓰기 최종본 [성찰과표현 G28분반]".getBytes(StandardCharsets.UTF_8);
        int insideHangul = "SUMMARY:[과제1] 글쓰기 최종본 [성찰".getBytes(StandardCharsets.UTF_8).length + 1;
        ByteArrayOutputStream feed = new ByteArrayOutputStream();
        feed.writeBytes("BEGIN:VEVENT\r\nUID:event-assignment-3\r\nDTSTART;VALUE=DATE:20261218\r\n"
                .getBytes(StandardCharsets.UTF_8));
        feed.write(summary, 0, insideHangul);
        feed.writeBytes("\r\n ".getBytes(StandardCharsets.UTF_8));
        feed.write(summary, insideHangul, summary.length - insideHangul);
        feed.writeBytes("\r\nEND:VEVENT\r\n".getBytes(StandardCharsets.UTF_8));

        EcampusEventResponse event = IcsParser.parse(feed.toByteArray()).get(0);

        assertThat(event.title()).isEqualTo("[과제1] 글쓰기 최종본");
        assertThat(event.courseName()).isEqualTo("성찰과표현 G28분반");
        assertThat(event.date()).isEqualTo(LocalDate.of(2026, 12, 18));
    }

    @Test
    void skipsEventWithBrokenDateAndKeepsOthers() {
        byte[] feed = (new String(ics("UID:broken", "DTSTART:언젠가", "SUMMARY:깨진 일정"), StandardCharsets.UTF_8)
                + new String(ics("UID:ok", "DTSTART;VALUE=DATE:20261012", "SUMMARY:정상 일정"), StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);

        assertThat(IcsParser.parse(feed)).extracting(EcampusEventResponse::id).containsExactly("ok");
    }

    static byte[] ics(String... veventLines) {
        return ("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\n" + String.join("\r\n", veventLines)
                + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n").getBytes(StandardCharsets.UTF_8);
    }
}
