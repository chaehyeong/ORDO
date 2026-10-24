package com.ordo.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.notification.domain.PushSubscription;
import com.ordo.notification.dto.PushSendResponse;
import com.ordo.notification.dto.PushSubscribeRequest;
import com.ordo.notification.repository.PushSubscriptionRepository;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.security.GeneralSecurityException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 기기 구독 등록·삭제와 사용자 기기 전체로 보내기 (명세 4.10). 로그에는 구독 id·상태 코드만 남긴다 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushSubscriptionService {

    static final int MAX_DEVICES = 10;

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;
    private final WebPushClient webPushClient;
    private final ObjectMapper objectMapper;

    public String publicKey() {
        return webPushClient.publicKey();
    }

    /** 같은 endpoint 면 갱신(다른 사용자 것이었으면 지금 사용자로 옮김). 기기가 10개를 넘으면 먼저 등록한 것부터 지운다 */
    @Transactional
    public void subscribe(Long userId, PushSubscribeRequest request) {
        requireConfigured();
        if (!WebPushClient.isAllowedEndpoint(request.endpoint())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String p256dh = normalizeKey(request.keys().p256dh(), 65);
        String auth = normalizeKey(request.keys().auth(), 16);
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        pushSubscriptionRepository.findByEndpoint(request.endpoint()).ifPresentOrElse(
                existing -> existing.change(user, p256dh, auth),
                () -> pushSubscriptionRepository.save(PushSubscription.builder()
                        .user(user).endpoint(request.endpoint()).p256dh(p256dh).auth(auth).build()));

        List<PushSubscription> devices = pushSubscriptionRepository.findByUserIdOrderByIdAsc(userId);
        if (devices.size() > MAX_DEVICES) {
            pushSubscriptionRepository.deleteAll(devices.subList(0, devices.size() - MAX_DEVICES));
        }
    }

    /** 내 구독만 지운다. 없어도 성공 */
    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        requireConfigured();
        pushSubscriptionRepository.deleteByEndpointAndUserId(endpoint, userId);
    }

    /** 사용자가 직접 누르는 테스트 알림: 마이페이지 알림 끄기 상태여도 보낸다 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PushSendResponse sendTest(Long userId) {
        requireConfigured();
        return sendToUser(userId, NotificationMessage.test());
    }

    /**
     * 그 사용자의 모든 기기로 보낸다. 푸시 서비스에 HTTP 요청하는 동안 DB 연결을 잡지 않도록 트랜잭션 밖에서 실행.
     * 404·410 = 만료된 구독이라 지우고, 그 밖의 실패는 경고 로그만 남긴다(재시도 없음)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PushSendResponse sendToUser(Long userId, NotificationMessage message) {
        byte[] payload = toJson(message);
        int sent = 0;
        int failed = 0;
        for (PushSubscription device : pushSubscriptionRepository.findByUserIdOrderByIdAsc(userId)) {
            int status = webPushClient.send(device.getEndpoint(), WebPushCrypto.decodeBase64Url(device.getP256dh()),
                    WebPushCrypto.decodeBase64Url(device.getAuth()), payload);
            if (status >= 200 && status < 300) {
                sent++;
                continue;
            }
            failed++;
            if (status == 404 || status == 410) {
                pushSubscriptionRepository.deleteById(device.getId());
                log.info("만료된 푸시 구독 삭제 subscriptionId={} status={}", device.getId(), status);
            } else {
                log.warn("푸시 발송 실패 subscriptionId={} status={}", device.getId(), status);
            }
        }
        return new PushSendResponse(sent, failed);
    }

    private void requireConfigured() {
        if (!webPushClient.isConfigured()) {
            throw new BusinessException(ErrorCode.PUSH_NOT_CONFIGURED);
        }
    }

    // Base64url(패딩 있어도 됨) → 길이 확인 → 패딩 없는 형태로 저장. p256dh 는 P-256 공개키인지도 확인
    private static String normalizeKey(String value, int length) {
        try {
            byte[] bytes = WebPushCrypto.decodeBase64Url(value);
            if (bytes.length != length) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            if (length == 65) {
                WebPushCrypto.toPublicKey(bytes);
            }
            return WebPushCrypto.encodeBase64Url(bytes);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private byte[] toJson(NotificationMessage message) {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
