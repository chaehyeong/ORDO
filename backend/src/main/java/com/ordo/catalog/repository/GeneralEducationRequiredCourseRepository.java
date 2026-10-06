package com.ordo.catalog.repository;

import com.ordo.catalog.domain.GeneralEducationRequiredCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneralEducationRequiredCourseRepository extends JpaRepository<GeneralEducationRequiredCourse, Long> {
    List<GeneralEducationRequiredCourse> findAllByAdmissionYearOrderByIdAsc(int admissionYear);
}
