package com.ordo.ecampus.service;

import com.ordo.ecampus.dto.EcampusEventResponse;
import com.ordo.ecampus.dto.EcampusEventsResponse;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** e캠퍼스 피드는 캘린더를 열 때 받아 오고 10분 동안 재사용한다 (주기적 백그라운드 동기화 없음) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EcampusService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    // 날짜 → 시간 순, 시간 없는(23:59 마감) 과제는 그날 맨 뒤
    private static final Comparator<EcampusEventResponse> DISPLAY_ORDER = Comparator.comparing(EcampusEventResponse::date)
            .thenComparing(EcampusEventResponse::time, Comparator.nullsLast(Comparator.naturalOrder()));

    private final UserRepository userRepository;
    private final EcampusFeedClient feedClient;

    // ponytail: 서버 메모리 캐시. 서버가 여러 대가 되면 Redis 같은 공유 캐시로 바꾼다
    private final Map<Long, CachedFeed> cache = new ConcurrentHashMap<>();
    Clock clock = Clock.system(ZoneId.of("Asia/Seoul"));  // 테스트에서 시간을 옮기려고 패키지 범위로 둠

    private record CachedFeed(LocalDateTime syncedAt, List<EcampusEventResponse> events) {
    }

    @Transactional
    public void connect(Long userId, String feedToken) {
        getUser(userId).changeEcampusFeedToken(feedToken);
        cache.remove(userId);
    }

    @Transactional
    public void disconnect(Long userId) {
        getUser(userId).changeEcampusFeedToken(null);
        cache.remove(userId);
    }

    // e캠퍼스에 HTTP 요청하는 동안 DB 연결을 붙잡지 않도록 트랜잭션 밖에서 실행
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EcampusEventsResponse getEvents(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
        String feedToken = getUser(userId).getEcampusFeedToken();
        if (feedToken == null) {
            return new EcampusEventsResponse(false, null, List.of());
        }
        CachedFeed feed = loadFeed(userId, feedToken);
        List<EcampusEventResponse> events = feed.events().stream()
                .filter(event -> !event.date().isBefore(from) && !event.date().isAfter(to))
                .sorted(DISPLAY_ORDER)
                .toList();
        return new EcampusEventsResponse(true, feed.syncedAt(), events);
    }

    // 10분 안에 받아 둔 게 있으면 그대로 쓴다. 다시 받다가 실패하면 예전 것을 돌려준다(syncedAt 으로 오래된 걸 알 수 있음)
    private CachedFeed loadFeed(Long userId, String feedToken) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        CachedFeed cached = cache.get(userId);
        if (cached != null && cached.syncedAt().plus(CACHE_TTL).isAfter(now)) {
            return cached;
        }
        try {
            CachedFeed fresh = new CachedFeed(now, IcsParser.parse(feedClient.fetch(feedToken)));
            cache.put(userId, fresh);
            return fresh;
        } catch (BusinessException e) {
            if (cached != null) {
                return cached;
            }
            throw e;
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
