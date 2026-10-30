package com.ordo.catalog.repository;

import com.ordo.catalog.domain.MicroDegree;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MicroDegreeRepository extends JpaRepository<MicroDegree, Long> {

    List<MicroDegree> findAllByOrderByNameAscIdAsc();
}
