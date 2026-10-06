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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final Comparator<TimelineItem> TIMELINE_ORDER = Comparator.comparing(TimelineItem::startTime)
            .thenComparing(item -> item.source().equals("SCHEDULE"));  // 같은 시각이면 수업 먼저

    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final AcademicProgressService academicProgressService;
    private final EcampusService ecampusService;

    // e캠퍼스에 HTTP 요청하는 동안 DB 연결을 붙잡지 않도록 트랜잭션 밖에서 실행 (조회마다 짧은 트랜잭션)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public HomeResponse getHome(Long userId, LocalDate date) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        List<Schedule> weekSchedules = scheduleRepository.findInPeriod(userId, monday, sunday, null);
        Set<LocalDate> ecampusDueDays = ecampusDueDays(userId, monday, sunday);
        Map<AcademicTerm, List<TimetableEntry>> timetables = new HashMap<>();  // 학기가 바뀌는 주도 있어 날짜마다 학기를 본다

        List<Day> week = monday.datesUntil(sunday.plusDays(1))
                .map(day -> new Day(day, day.getDayOfWeek().getValue(), day.equals(date),
                        categories(day, weekSchedules, classesOn(userId, day, timetables), ecampusDueDays)))
                .toList();

        List<Schedule> todaySchedules = weekSchedules.stream().filter(s -> s.getScheduleDate().equals(date)).toList();
        List<TimelineItem> timeline = Stream.concat(
                        classesOn(userId, date, timetables).stream()
                                .map(e -> new TimelineItem("TIMETABLE", e.getId(), e.getCourseName(), ScheduleCategory.LECTURE,
                                        e.getStartTime(), e.getEndTime(), e.getLocation())),
                        todaySchedules.stream().filter(s -> s.getStartTime() != null)
                                .map(s -> new TimelineItem("SCHEDULE", s.getId(), s.getTitle(), s.getCategory(),
                                        s.getStartTime(), s.getEndTime(), s.getLocation())))
                .sorted(TIMELINE_ORDER)
                .toList();
        List<Todo> todos = todaySchedules.stream().filter(s -> s.getStartTime() == null)
                .map(s -> new Todo(s.getId(), s.getTitle(), s.getCategory(), s.isDone()))
                .toList();

        Graduation graduation = academicProgressService.findSummary(userId).map(ProgressSummary::total)
                .map(t -> new Graduation(t.earned(), t.required(), t.percent(), t.remaining()))
                .orElse(null);
        String greetingName = user.getNickname() != null ? user.getNickname() : user.getName();
        return new HomeResponse(greetingName, week, timeline, todos, graduation);
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
                .filter(e -> e.getDayOfWeek() == day.getDayOfWeek().getValue())
                .toList();
    }

    // e캠퍼스 과제 마감은 주간 점에만 쓴다(제출 여부를 몰라 할 일·추천에는 넣지 않음). 못 받아 오면 점 없이 보여준다
    private Set<LocalDate> ecampusDueDays(Long userId, LocalDate from, LocalDate to) {
        try {
            return ecampusService.getEvents(userId, from, to).events().stream()
                    .map(EcampusEventResponse::date)
                    .collect(Collectors.toSet());
        } catch (BusinessException e) {
            return Set.of();
        }
    }
}
