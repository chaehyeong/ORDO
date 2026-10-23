package com.ordo.catalog.repository;

import com.ordo.catalog.domain.Classification;
import com.ordo.catalog.domain.Course;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /** 이수구분 자동 판별(명세 4.6): (편성 단위, 학수번호)는 유일하지 않아 첫 행을 쓴다 */
    Optional<Course> findFirstByUnitNameAndCourseCodeOrderByIdAsc(String unitName, String courseCode);

    // 학수번호만으로 조회하면 다른 학과의 동일 과목이 섞이므로 편성 단위를 반드시 제한한다.
    @Query("""
            select c from Course c
            where c.unitName = :unitName
              and (:classification is null or c.classification = :classification)
              and (:pattern is null or lower(c.name) like :pattern escape '!'
                   or lower(c.courseCode) like :pattern escape '!')
            """)
    Page<Course> search(@Param("unitName") String unitName,
                        @Param("pattern") String pattern,
                        @Param("classification") Classification classification,
                        Pageable pageable);
}
