package com.ordo.catalog.repository;

import com.ordo.catalog.domain.MicroDegreeCourse;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MicroDegreeCourseRepository extends JpaRepository<MicroDegreeCourse, Long> {

    List<MicroDegreeCourse> findAllByOrderByIdAsc();

    List<MicroDegreeCourse> findByMicroDegreeIdInOrderByIdAsc(Collection<Long> microDegreeIds);
}
