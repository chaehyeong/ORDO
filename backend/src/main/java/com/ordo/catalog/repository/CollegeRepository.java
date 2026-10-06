package com.ordo.catalog.repository;

import com.ordo.catalog.domain.College;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollegeRepository extends JpaRepository<College, Long> {
    List<College> findAllByOrderByNameAscIdAsc();
}
