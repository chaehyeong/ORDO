package com.ordo.notification.repository;

import com.ordo.notification.domain.PushSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    /** 등록 순 (기기 10개를 넘으면 앞에서부터 지운다) */
    List<PushSubscription> findByUserIdOrderByIdAsc(Long userId);

    void deleteByEndpointAndUserId(String endpoint, Long userId);
}
