package com.ordo.timetable.repository;

import com.ordo.global.common.Term;
import com.ordo.timetable.domain.TimetableEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {

    Optional<TimetableEntry> findByIdAndUserId(Long id, Long userId);

    /** 학기 시간표: 요일 → 시작시간 순 (온라인 강의는 요일이 null 이라 DB 마다 위치가 다르다. 화면 순서는 서비스에서 정한다) */
    List<TimetableEntry> findByUserIdAndAcademicYearAndTermOrderByDayOfWeekAscStartTimeAsc(
            Long userId, int academicYear, Term term);

    /** 같은 학기·요일의 칸 (겹침 검사, 홈 오늘 수업) */
    List<TimetableEntry> findByUserIdAndAcademicYearAndTermAndDayOfWeek(
            Long userId, int academicYear, Term term, int dayOfWeek);
}
