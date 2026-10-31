package com.ordo.academic.repository;

import com.ordo.academic.domain.PlannedCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlannedCourseRepository extends JpaRepository<PlannedCourse, Long> {

    /** 저장한 순서 그대로 (학기 안의 표시 순서) */
    List<PlannedCourse> findByUserIdOrderByIdAsc(Long userId);
}
