package com.ordo.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.dto.ProgressSummary.Total;
import com.ordo.academic.service.AcademicProgressService;
import com.ordo.ecampus.dto.EcampusEventResponse;
import com.ordo.ecampus.dto.EcampusEventsResponse;
import com.ordo.ecampus.service.EcampusService;
import com.ordo.global.common.Term;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.home.dto.HomeResponse;
import com.ordo.home.dto.HomeResponse.Day;
import com.ordo.home.dto.HomeResponse.Graduation;
import com.ordo.home.dto.HomeResponse.TimelineItem;
import com.ordo.home.dto.HomeResponse.Todo;
import com.ordo.schedule.domain.Schedule;
import com.ordo.schedule.domain.ScheduleCategory;
import com.ordo.schedule.repository.ScheduleRepository;
import com.ordo.timetable.domain.TimetableEntry;
import com.ordo.timetable.repository.TimetableEntryRepository;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 7);
    static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    static final LocalDate SUNDAY = LocalDate.of(2026, 10, 11);

    @Mock
    UserRepository userRepository;
    @Mock
    ScheduleRepository scheduleRepository;
    @Mock
    TimetableEntryRepository timetableEntryRepository;
    @Mock
    AcademicProgressService academicProgressService;
    @Mock
    EcampusService ecampusService;
    @InjectMocks
    HomeService homeService;

    @Test
    void weekStartsOnMondayWithCategoriesOfEachDay() {
        givenHome(user("하은"),
                List.of(schedule("팀플", MONDAY, null, ScheduleCategory.PERSONAL),
                        schedule("영화", MONDAY, at(20), ScheduleCategory.PERSONAL),
                        schedule("보고서", WEDNESDAY, null, ScheduleCategory.ASSIGNMENT)),
                List.of(timetable("색채학", 1, at(13)), timetable("타이포그래피 I", 3, at(9))),
                List.of(LocalDate.of(2026, 10, 9)));

        List<Day> week = homeService.getHome(1L, WEDNESDAY).week();

        assertThat(week).extracting(Day::date).containsExactlyElementsOf(MONDAY.datesUntil(SUNDAY.plusDays(1)).toList());
        assertThat(week).extracting(Day::dayOfWeek).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(week).extracting(Day::today).containsExactly(false, false, true, false, false, false, false);
        assertThat(week.get(0).categories()).containsExactly(ScheduleCategory.LECTURE, ScheduleCategory.PERSONAL);
        assertThat(week.get(2).categories()).containsExactly(ScheduleCategory.LECTURE, ScheduleCategory.ASSIGNMENT);
        assertThat(week.get(4).categories()).containsExactly(ScheduleCategory.ASSIGNMENT);  // e캠퍼스 마감
        assertThat(week.get(6).categories()).isEmpty();
    }

    @Test
    void timelineMergesClassesAndTimedSchedulesAndTodosAreUntimed() {
        givenHome(user("하은"),
                List.of(schedule("팀플 모임", WEDNESDAY, at(12), ScheduleCategory.PERSONAL),
                        schedule("면담", WEDNESDAY, at(9), ScheduleCategory.PERSONAL),
                        schedule("자료 조사", WEDNESDAY, null, ScheduleCategory.ASSIGNMENT),
                        schedule("월요일 할 일", MONDAY, null, ScheduleCategory.PERSONAL)),
                List.of(timetable("타이포그래피 I", 3, at(9)), timetable("색채학", 1, at(13))),
                List.of());

        HomeResponse home = homeService.getHome(1L, WEDNESDAY);

        assertThat(home.timeline()).extracting(TimelineItem::title)
                .containsExactly("타이포그래피 I", "면담", "팀플 모임");  // 같은 9시면 수업 먼저, 월요일 수업은 빠짐
        assertThat(home.timeline().get(0).source()).isEqualTo("TIMETABLE");
        assertThat(home.timeline().get(0).category()).isEqualTo(ScheduleCategory.LECTURE);
        assertThat(home.todos()).extracting(Todo::title).containsExactly("자료 조사");
        assertThat(home.timeline()).extracting(TimelineItem::courseName, TimelineItem::url).containsOnly(tuple(null, null));
    }

    @Test
    void ecampusAssignmentsDueTodayJoinTimelineAtDueTime() {
        givenHomeWithEvents(user("하은"),
                List.of(schedule("팀플 모임", WEDNESDAY, at(15), ScheduleCategory.PERSONAL)),
                List.of(timetable("디자인씽킹", 3, at(15))),
                List.of(event("퀴즈", WEDNESDAY, null),
                        event("3주차 과제 제출", WEDNESDAY, at(15)),
                        event("금요일 과제", LocalDate.of(2026, 10, 9), at(10))));

        HomeResponse home = homeService.getHome(1L, WEDNESDAY);

        assertThat(home.timeline()).extracting(TimelineItem::source, TimelineItem::title, TimelineItem::startTime)
                .containsExactly(tuple("TIMETABLE", "디자인씽킹", at(15)),       // 같은 15시면 수업 → 일정 → e캠퍼스
                        tuple("SCHEDULE", "팀플 모임", at(15)),
                        tuple("ECAMPUS", "3주차 과제 제출", at(15)),
                        tuple("ECAMPUS", "퀴즈", LocalTime.of(23, 59)));       // 시각 없는 마감은 23:59
        assertThat(home.timeline().get(2)).isEqualTo(new TimelineItem("ECAMPUS", null, "3주차 과제 제출",
                ScheduleCategory.ASSIGNMENT, at(15), null, null, "디자인씽킹", "https://khcanvas.khu.ac.kr/courses/1"));
        assertThat(home.week().get(4).categories()).containsExactly(ScheduleCategory.ASSIGNMENT);  // 금요일은 점만
        assertThat(home.todos()).isEmpty();
        then(ecampusService).should(times(1)).getEvents(1L, MONDAY, SUNDAY);
    }

    @Test
    void greetingAndGraduation() {
        User withNickname = User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은").nickname("하은").build();
        givenHome(withNickname, List.of(), List.of(), List.of());
        given(academicProgressService.findSummary(1L)).willReturn(Optional.of(new ProgressSummary(2026, null,
                new Total(102, 130, 28, 78.4), List.of(), List.of(), List.of(), List.of(), false, false)));

        HomeResponse home = homeService.getHome(1L, WEDNESDAY);

        assertThat(home.greetingName()).isEqualTo("하은");
        assertThat(home.graduation()).isEqualTo(new Graduation(102, 130, 78.4, 28));
    }

    @Test
    void graduationIsNullWithoutProfileAndNameIsUsedWithoutNickname() {
        givenHome(user("이하은"), List.of(), List.of(), List.of());

        HomeResponse home = homeService.getHome(1L, WEDNESDAY);

        assertThat(home.greetingName()).isEqualTo("이하은");
        assertThat(home.graduation()).isNull();
    }

    @Test
    void ecampusFailureOnlyHidesItsDots() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user("하은")));
        given(scheduleRepository.findInPeriod(1L, MONDAY, SUNDAY, null))
                .willReturn(List.of(schedule("보고서", WEDNESDAY, null, ScheduleCategory.ASSIGNMENT)));
        given(timetableEntryRepository.findByUserIdAndAcademicYearAndTermOrderByDayOfWeekAscStartTimeAsc(1L, 2026, Term.SECOND))
                .willReturn(List.of());
        given(ecampusService.getEvents(1L, MONDAY, SUNDAY))
                .willThrow(new BusinessException(ErrorCode.ECAMPUS_FEED_UNAVAILABLE));

        HomeResponse home = homeService.getHome(1L, WEDNESDAY);

        assertThat(home.week().get(2).categories()).containsExactly(ScheduleCategory.ASSIGNMENT);
        assertThat(home.todos()).hasSize(1);
        assertThat(home.timeline()).isEmpty();
    }

    private void givenHome(User user, List<Schedule> schedules, List<TimetableEntry> entries, List<LocalDate> ecampusDays) {
        givenHomeWithEvents(user, schedules, entries, ecampusDays.stream().map(d -> event("LAB", d, null)).toList());
    }

    private void givenHomeWithEvents(User user, List<Schedule> schedules, List<TimetableEntry> entries,
                                     List<EcampusEventResponse> events) {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(scheduleRepository.findInPeriod(1L, MONDAY, SUNDAY, null)).willReturn(schedules);
        given(timetableEntryRepository.findByUserIdAndAcademicYearAndTermOrderByDayOfWeekAscStartTimeAsc(1L, 2026, Term.SECOND))
                .willReturn(entries);
        given(ecampusService.getEvents(1L, MONDAY, SUNDAY)).willReturn(new EcampusEventsResponse(!events.isEmpty(), null, events));
    }

    private static EcampusEventResponse event(String title, LocalDate date, LocalTime time) {
        return new EcampusEventResponse("event-assignment-1", title, "디자인씽킹", date, time,
                "https://khcanvas.khu.ac.kr/courses/1");
    }

    private static User user(String name) {
        return User.builder().email("haeun@khu.ac.kr").password("encoded").name(name).build();
    }

    private static Schedule schedule(String title, LocalDate date, LocalTime start, ScheduleCategory category) {
        return Schedule.builder().title(title).category(category).scheduleDate(date).startTime(start).build();
    }

    private static TimetableEntry timetable(String name, int dayOfWeek, LocalTime start) {
        return TimetableEntry.builder().academicYear(2026).term(Term.SECOND).courseName(name).dayOfWeek(dayOfWeek)
                .startTime(start).endTime(start.plusMinutes(90)).location("예405").build();
    }

    private static LocalTime at(int hour) {
        return LocalTime.of(hour, 0);
    }
}
