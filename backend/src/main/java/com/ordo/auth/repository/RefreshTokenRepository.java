package com.ordo.auth.repository;

import com.ordo.auth.domain.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    /** 지운 행 수. 같은 토큰으로 동시에 요청해도 1개만 1을 받는다 */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.token = :token")
    int deleteByToken(String token);

    /** 없어도 그대로 끝난다 (로그아웃은 여러 번 불러도 결과가 같다) */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.token = :token AND r.user.id = :userId")
    void deleteByTokenAndUserId(String token, Long userId);
}
