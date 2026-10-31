package com.ordo.catalog.repository;

import com.ordo.catalog.domain.GraduationRequirement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GraduationRequirementRepository extends JpaRepository<GraduationRequirement, Long> {
    Optional<GraduationRequirement> findByMajorIdAndAdmissionYear(Long majorId, int admissionYear);

    @Query("""
            select r from GraduationRequirement r join fetch r.major m join fetch m.college
            where r.admissionYear = :admissionYear
              and r.minorTotalCredits > 0
              and (:collegeId is null or m.college.id = :collegeId)
            order by m.displayName, m.id
            """)
    List<GraduationRequirement> findMinorOptions(@Param("admissionYear") int admissionYear,
                                                @Param("collegeId") Long collegeId);
}
