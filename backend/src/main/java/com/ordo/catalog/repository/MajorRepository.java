package com.ordo.catalog.repository;

import com.ordo.catalog.domain.Major;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MajorRepository extends JpaRepository<Major, Long> {

    // ponytail: GraduationRequirement 엔티티(B, T3)가 생기면 그 리포지토리의 existsBy... 로 교체
    @Query(value = "SELECT COUNT(*) FROM graduation_requirements WHERE major_id = :majorId AND admission_year = :admissionYear",
            nativeQuery = true)
    long countGraduationRequirements(Long majorId, int admissionYear);

    /** 회원가입·프로필에서 고를 수 있는 전공: 융합전공 제외 + 해당 학번 졸업요건 행 존재 (명세 3.3) */
    default Optional<Major> findSelectable(Long majorId, int admissionYear) {
        return findById(majorId)
                .filter(major -> !major.isConvergence())
                .filter(major -> countGraduationRequirements(majorId, admissionYear) > 0);
    }
}
