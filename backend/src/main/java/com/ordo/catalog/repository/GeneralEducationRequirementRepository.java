package com.ordo.catalog.repository;

import com.ordo.catalog.domain.GeneralEducationRequirement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneralEducationRequirementRepository extends JpaRepository<GeneralEducationRequirement, Long> {
    Optional<GeneralEducationRequirement> findByAdmissionYear(int admissionYear);
}
