package com.ordo.catalog.repository;

import com.ordo.catalog.domain.GraduationRequirement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraduationRequirementRepository extends JpaRepository<GraduationRequirement, Long> {
    Optional<GraduationRequirement> findByMajorIdAndAdmissionYear(Long majorId, int admissionYear);
}
