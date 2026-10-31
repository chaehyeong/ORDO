package com.ordo.home.service;

import com.ordo.academic.dto.ProgressSummary;
import com.ordo.academic.service.AcademicProgressService;
import com.ordo.ecampus.dto.EcampusEventResponse;
import com.ordo.ecampus.service.EcampusService;
import com.ordo.global.common.AcademicTerm;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.home.dto.HomeResponse;
import com.ordo.home.dto.HomeResponse.Day;
import com.ordo.home.dto.HomeResponse.Deadline;
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
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 홈 화면 집계 (명세 4.7). 기존 일정·시간표·이수현황·e캠퍼스 조회를 모아서 만든다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private static final List<String> SOURCE_ORDER = List.of("TIMETABLE", "SCHEDULE", "ECAMPUS");
    private static final Comparator<TimelineItem> TIMELINE_ORDER = Comparator.comparing(TimelineItem::startTime)
            .thenComparing(item -> SOURCE_ORDER.indexOf(item.source()));  // 같은 시각이면 수업 → 일정 → e캠퍼스
    private static final LocalTime ALL_DAY_DUE = LocalTime.of(23, 59);  // 시각 없는 e캠퍼스 마감 (명세 4.8)
    private static final int DEADLINE_DAYS = 7;   // D-Day 알림 범위: 기준일~7일 뒤 (T16)
    private static final int DEADLINE_LIMIT = 5;
    private static final Comparator<Deadline> DEADLINE_ORDER = Comparator.comparing(Deadline::date)
            .thenComparing(d -> d.time() != null ? d.time() : ALL_DAY_DUE)  // 시간 없는 일정은 그날 맨 뒤
            .thenComparing(d -> SOURCE_ORDER.indexOf(d.source()));

    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final AcademicProgressService academicProgressService;
    private final EcampusService ecampusService;
    Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));  // 테스트에서 시간을 옮기려고 패키지 범위

    // e캠퍼스에 HTTP 요청하는 동안 DB 연결을 붙잡지 않도록 트랜잭션 밖에서 실행 (조회마다 짧은 트랜잭션)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public HomeResponse getHome(Long userId, LocalDate date) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        LocalDate deadlineEnd = date.plusDays(DEADLINE_DAYS);  // 항상 그 주 일요일보다 뒤라 주간 점·오늘 일정도 이 범위 안에 있다
        List<Schedule> schedules = scheduleRepository.findInPeriod(userId, monday, deadlineEnd, null);
        List<EcampusEventResponse> ecampusEvents = ecampusEvents(userId, monday, deadlineEnd);  // e캠퍼스 요청은 1번만
        Set<LocalDate> ecampusDueDays = ecampusEvents.stream().map(EcampusEventResponse::date).collect(Collectors.toSet());
        Map<AcademicTerm, List<TimetableEntry>> timetables = new HashMap<>();  // 학기가 바뀌는 주도 있어 날짜마다 학기를 본다

        List<Day> week = monday.datesUntil(sunday.plusDays(1))
                .map(day -> new Day(day, day.getDayOfWeek().getValue(), day.equals(date),
                        categories(day, schedules, classesOn(userId, day, timetables), ecampusDueDays)))
                .toList();

        List<Schedule> todaySchedules = schedules.stream().filter(s -> s.getScheduleDate().equals(date)).toList();
        List<TimelineItem> timeline = Stream.of(
                        classesOn(userId, date, timetables).stream()
                                .map(e -> new TimelineItem("TIMETABLE", e.getId(), e.getCourseName(), ScheduleCategory.LECTURE,
                                        e.getStartTime(), e.getEndTime(), e.getLocation(), null, null)),
                        todaySchedules.stream().filter(s -> s.getStartTime() != null)
                                .map(s -> new TimelineItem("SCHEDULE", s.getId(), s.getTitle(), s.getCategory(),
                                        s.getStartTime(), s.getEndTime(), s.getLocation(), null, null)),
                        ecampusEvents.stream().filter(e -> e.date().equals(date))
                                .map(e -> new TimelineItem("ECAMPUS", null, e.title(), ScheduleCategory.ASSIGNMENT,
                                        e.time() != null ? e.time() : ALL_DAY_DUE, null, null, e.courseName(), e.url())))
                .flatMap(Function.identity())
                .sorted(TIMELINE_ORDER)
                .toList();
        List<Todo> todos = todaySchedules.stream().filter(s -> s.getStartTime() == null)
                .map(s -> new Todo(s.getId(), s.getTitle(), s.getCategory(), s.isDone()))
                .toList();

        Graduation graduation = academicProgressService.findSummary(userId).map(ProgressSummary::total)
                .map(t -> new Graduation(t.earned(), t.required(), t.percent(), t.remaining()))
                .orElse(null);
        String greetingName = user.getNickname() != null ? user.getNickname() : user.getName();
        return new HomeResponse(greetingName, week, timeline, todos, deadlines(date, schedules, ecampusEvents), graduation);
    }

    // D-Day 알림 (T16): 기준일 이후의 완료 안 한 과제·개인일정 + e캠퍼스 마감, 가까운 순 5개. 오늘 이미 지난 시각은 뺀다
    private List<Deadline> deadlines(LocalDate date, List<Schedule> schedules, List<EcampusEventResponse> ecampusEvents) {
        LocalDateTime now = LocalDateTime.now(clock);
        return Stream.concat(
                        schedules.stream()
                                .filter(s -> !s.isDone() && s.getCategory() != ScheduleCategory.LECTURE)
                                .filter(s -> !s.getScheduleDate().isBefore(date))
                                .map(s -> new Deadline("SCHEDULE", s.getId(), s.getTitle(), s.getCategory(), s.getScheduleDate(),
                                        s.getStartTime(), daysBetween(date, s.getScheduleDate()), null, null)),
                        ecampusEvents.stream()
                                .filter(e -> !e.date().isBefore(date))
                                .map(e -> new Deadline("ECAMPUS", null, e.title(), ScheduleCategory.ASSIGNMENT, e.date(),
                                        e.time() != null ? e.time() : ALL_DAY_DUE, daysBetween(date, e.date()), e.courseName(), e.url())))
                .filter(d -> !(d.date().equals(now.toLocalDate()) && d.time() != null && d.time().isBefore(now.toLocalTime())))
                .sorted(DEADLINE_ORDER)
                .limit(DEADLINE_LIMIT)
                .toList();
    }

    private static int daysBetween(LocalDate from, LocalDate to) {
        return (int) ChronoUnit.DAYS.between(from, to);
    }

    // 그날 일정 카테고리(중복 제거) + 그 요일 시간표 수업이 있으면 LECTURE + e캠퍼스 마감이 있으면 ASSIGNMENT
    private static List<ScheduleCategory> categories(LocalDate day, List<Schedule> weekSchedules,
                                                     List<TimetableEntry> classes, Set<LocalDate> ecampusDueDays) {
        Set<ScheduleCategory> categories = EnumSet.noneOf(ScheduleCategory.class);
        if (!classes.isEmpty()) {
            categories.add(ScheduleCategory.LECTURE);
        }
        weekSchedules.stream().filter(s -> s.getScheduleDate().equals(day)).forEach(s -> categories.add(s.getCategory()));
        if (ecampusDueDays.contains(day)) {
            categories.add(ScheduleCategory.ASSIGNMENT);
        }
        return List.copyOf(categories);
    }

    private List<TimetableEntry> classesOn(Long userId, LocalDate day, Map<AcademicTerm, List<TimetableEntry>> timetables) {
        AcademicTerm term = AcademicTerm.of(day);
        return timetables.computeIfAbsent(term, t -> timetableEntryRepository
                        .findByUserIdAndAcademicYearAndTermOrderByDayOfWeekAscStartTimeAsc(userId, t.year(), t.term()))
                .stream()
                .filter(e -> !e.isOnline() && e.getDayOfWeek() == day.getDayOfWeek().getValue())
                .toList();
    }

    // e캠퍼스 과제 마감은 주간 점·오늘 timeline·D-Day 알림에만 쓴다(제출 여부를 몰라 할 일·추천에는 넣지 않음).
    // 미연동이면 빈 목록, 못 받아 오면 e캠퍼스 것만 빼고 보여준다
    private List<EcampusEventResponse> ecampusEvents(Long userId, LocalDate from, LocalDate to) {
        try {
            return ecampusService.getEvents(userId, from, to).events();
        } catch (BusinessException e) {
            return List.of();
        }
    }
}
