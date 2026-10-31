package com.ordo.catalog.repository;

import com.ordo.catalog.domain.MinorDesignatedCourse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinorDesignatedCourseRepository extends JpaRepository<MinorDesignatedCourse, Long> {
    List<MinorDesignatedCourse> findByMajorId(Long majorId);
}
