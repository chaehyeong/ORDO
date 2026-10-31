package com.ordo.academic.repository;

import com.ordo.academic.domain.UserMicroDegree;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMicroDegreeRepository extends JpaRepository<UserMicroDegree, Long> {

    /** 등록한 순서 그대로 (과목 배정 순서) */
    List<UserMicroDegree> findByUserIdOrderByIdAsc(Long userId);

    Optional<UserMicroDegree> findByUserIdAndMicroDegreeId(Long userId, Long microDegreeId);
}
