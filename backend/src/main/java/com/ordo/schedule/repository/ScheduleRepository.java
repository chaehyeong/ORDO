package com.ordo.schedule.repository;

import com.ordo.schedule.domain.Schedule;
import com.ordo.schedule.domain.ScheduleCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findByIdAndUserId(Long id, Long userId);

    /** category 가 null 이면 전체. 화면 정렬은 서비스에서 한다 */
    @Query("""
            SELECT s FROM Schedule s
            WHERE s.user.id = :userId AND s.scheduleDate BETWEEN :from AND :to
              AND (:category IS NULL OR s.category = :category)
            ORDER BY s.id
            """)
    List<Schedule> findInPeriod(Long userId, LocalDate from, LocalDate to, ScheduleCategory category);
}
