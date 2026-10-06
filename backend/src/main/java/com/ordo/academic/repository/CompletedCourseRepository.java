package com.ordo.academic.repository;

import com.ordo.academic.domain.CompletedCourse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompletedCourseRepository extends JpaRepository<CompletedCourse, Long> {

    Optional<CompletedCourse> findByIdAndUserId(Long id, Long userId);

    /** 학기 순 정렬은 CompletedCourse.CHRONOLOGICAL 로 (DB 는 학기 이름을 글자 순으로 정렬해서 못 씀) */
    List<CompletedCourse> findByUserId(Long userId);
}
