package com.ordo.academic.repository;

import com.ordo.academic.domain.UserMinor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMinorRepository extends JpaRepository<UserMinor, Long> {
    Optional<UserMinor> findByUserId(Long userId);
}
