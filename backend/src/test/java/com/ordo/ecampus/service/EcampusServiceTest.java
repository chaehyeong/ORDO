package com.ordo.ecampus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ordo.ecampus.dto.EcampusEventResponse;
import com.ordo.ecampus.dto.EcampusEventsResponse;
import com.ordo.ecampus.dto.EcampusFeedRequest;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EcampusServiceTest {

    static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    static final LocalDate TO = LocalDate.of(2026, 10, 15);
    static final Clock NOW = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    UserRepository userRepository;
    @Mock
    EcampusFeedClient feedClient;
    @InjectMocks
    EcampusService ecampusService;

    User user = User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은").build();

    @BeforeEach
    void setUp() {
        ecampusService.clock = NOW;
    }

    @Test
    void notConnectedReturnsEmptyWithoutFetching() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        EcampusEventsResponse response = ecampusService.getEvents(1L, FROM, TO);

        assertThat(response.connected()).isFalse();
        assertThat(response.events()).isEmpty();
        verify(feedClient, never()).fetch(any());
    }

    @Test
    void returnsEventsInPeriodWithAllDayLast() {
        connectedUser();
        given(feedClient.fetch("user_abc")).willReturn(feed());

        EcampusEventsResponse response = ecampusService.getEvents(1L, FROM, TO);

        assertThat(response.connected()).isTrue();
        assertThat(response.syncedAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 19, 0));
        assertThat(response.events()).extracting(EcampusEventResponse::title)
                .containsExactly("3일 과제", "8일 14시 퀴즈", "8일 과제");  // 20일 과제는 기간 밖
    }

    @Test
    void reusesFeedForTenMinutesThenFetchesAgain() {
        connectedUser();
        given(feedClient.fetch("user_abc")).willReturn(feed());

        ecampusService.getEvents(1L, FROM, TO);
        ecampusService.getEvents(1L, FROM, TO);
        verify(feedClient, times(1)).fetch("user_abc");

        ecampusService.clock = Clock.offset(NOW, Duration.ofMinutes(11));
        ecampusService.getEvents(1L, FROM, TO);
        verify(feedClient, times(2)).fetch("user_abc");
    }

    @Test
    void fallsBackToLastFeedWhenFetchingAgainFails() {
        connectedUser();
        given(feedClient.fetch("user_abc")).willReturn(feed())
                .willThrow(new BusinessException(ErrorCode.ECAMPUS_FEED_UNAVAILABLE));
        ecampusService.getEvents(1L, FROM, TO);

        ecampusService.clock = Clock.offset(NOW, Duration.ofMinutes(11));
        EcampusEventsResponse response = ecampusService.getEvents(1L, FROM, TO);

        assertThat(response.events()).hasSize(3);
        assertThat(response.syncedAt()).isEqualTo(LocalDateTime.of(2026, 10, 6, 19, 0));  // 예전 시각 그대로
    }

    @Test
    void fetchFailureWithoutPreviousFeedIsError() {
        connectedUser();
        given(feedClient.fetch("user_abc")).willThrow(new BusinessException(ErrorCode.ECAMPUS_FEED_UNAVAILABLE));

        assertThatThrownBy(() -> ecampusService.getEvents(1L, FROM, TO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ECAMPUS_FEED_UNAVAILABLE);
    }

    @Test
    void connectingNewFeedDropsCachedOne() {
        connectedUser();
        given(feedClient.fetch(any())).willReturn(feed());
        ecampusService.getEvents(1L, FROM, TO);

        ecampusService.connect(1L, "user_new");
        ecampusService.getEvents(1L, FROM, TO);

        verify(feedClient).fetch("user_new");
    }

    @Test
    void periodMustBeInOrder() {
        assertThatThrownBy(() -> ecampusService.getEvents(1L, TO, FROM))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    void feedUrlMustBeCanvasCalendarFeed() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        EcampusFeedRequest valid = new EcampusFeedRequest("https://khcanvas.khu.ac.kr/feeds/calendars/user_abc123.ics");

        assertThat(validator.validate(valid)).isEmpty();
        assertThat(valid.feedToken()).isEqualTo("user_abc123");
        for (String url : new String[]{
                "http://khcanvas.khu.ac.kr/feeds/calendars/user_abc.ics",
                "https://evil.example.com/feeds/calendars/user_abc.ics",
                "https://khcanvas.khu.ac.kr.evil.example.com/feeds/calendars/user_abc.ics",
                "https://khcanvas.khu.ac.kr/feeds/calendars/user_abc/../x.ics",
                "https://khcanvas.khu.ac.kr/feeds/calendars/user_abc.ics?x=1",
                "https://khcanvas.khu.ac.kr/calendar"}) {
            assertThat(validator.validate(new EcampusFeedRequest(url))).as(url).isNotEmpty();
        }
    }

    private void connectedUser() {
        user.changeEcampusFeedToken("user_abc");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
    }

    private static byte[] feed() {
        return String.join("\r\n",
                "BEGIN:VCALENDAR",
                "BEGIN:VEVENT", "UID:a", "DTSTART;VALUE=DATE:20261008T000000", "SUMMARY:8일 과제", "END:VEVENT",
                "BEGIN:VEVENT", "UID:b", "DTSTART:20261008T050000Z", "SUMMARY:8일 14시 퀴즈", "END:VEVENT",
                "BEGIN:VEVENT", "UID:c", "DTSTART;VALUE=DATE:20261020T000000", "SUMMARY:20일 과제", "END:VEVENT",
                "BEGIN:VEVENT", "UID:d", "DTSTART;VALUE=DATE:20261003T000000", "SUMMARY:3일 과제", "END:VEVENT",
                "END:VCALENDAR", "").getBytes(StandardCharsets.UTF_8);
    }
}
