package com.ordo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import com.ordo.notification.domain.PushSubscription;
import com.ordo.notification.dto.PushSendResponse;
import com.ordo.notification.dto.PushSubscribeRequest;
import com.ordo.notification.dto.PushSubscribeRequest.Keys;
import com.ordo.notification.repository.PushSubscriptionRepository;
import com.ordo.user.domain.User;
import com.ordo.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PushSubscriptionServiceTest {

    static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/dXk1-secret-device";
    static final String P256DH = WebPushCryptoTest.UA_PUBLIC;
    static final String AUTH = WebPushCryptoTest.AUTH;

    @Mock
    PushSubscriptionRepository pushSubscriptionRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    WebPushClient webPushClient;

    PushSubscriptionService service;
    final User user = user();

    @BeforeEach
    void setUp() {
        service = new PushSubscriptionService(pushSubscriptionRepository, userRepository, webPushClient, new ObjectMapper());
    }

    @Test
    void subscribeSavesNewDeviceWithUnpaddedKeys() {
        givenConfigured();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.empty());

        service.subscribe(1L, new PushSubscribeRequest(ENDPOINT, new Keys(P256DH + "=", AUTH + "==")));

        ArgumentCaptor<PushSubscription> saved = ArgumentCaptor.forClass(PushSubscription.class);
        then(pushSubscriptionRepository).should().save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(saved.getValue().getP256dh()).isEqualTo(P256DH);
        assertThat(saved.getValue().getAuth()).isEqualTo(AUTH);
    }

    @Test
    void sameEndpointIsUpdatedAndMovedToCurrentUser() {
        givenConfigured();
        PushSubscription existing = subscription(5L, user(), ENDPOINT);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.of(existing));

        service.subscribe(1L, new PushSubscribeRequest(ENDPOINT, new Keys(P256DH, AUTH)));

        assertThat(existing.getUser()).isSameAs(user);
        then(pushSubscriptionRepository).should(never()).save(any());
    }

    @Test
    void eleventhDeviceRemovesOldest() {
        givenConfigured();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(pushSubscriptionRepository.findByEndpoint(ENDPOINT)).willReturn(Optional.empty());
        List<PushSubscription> devices = new ArrayList<>(IntStream.rangeClosed(1, 11)
                .mapToObj(i -> subscription((long) i, user, ENDPOINT + i)).toList());
        given(pushSubscriptionRepository.findByUserIdOrderByIdAsc(1L)).willReturn(devices);

        service.subscribe(1L, new PushSubscribeRequest(ENDPOINT, new Keys(P256DH, AUTH)));

        then(pushSubscriptionRepository).should().deleteAll(List.of(devices.get(0)));
    }

    @ParameterizedTest
    @CsvSource({
            "https://evil.example.com/push, " + "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4, BTBZMqHH6r4Tts7J_aSIgg",
            "https://fcm.googleapis.com/fcm/send/x, BCVxsr7N, BTBZMqHH6r4Tts7J_aSIgg",                        // 공개키 길이
            "https://fcm.googleapis.com/fcm/send/x, " + "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4, BTBZMqHH",  // auth 길이
            "https://fcm.googleapis.com/fcm/send/x, " + "ACVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4, BTBZMqHH6r4Tts7J_aSIgg",  // 0x04 아님
            "https://fcm.googleapis.com/fcm/send/x, not+base64/url!, BTBZMqHH6r4Tts7J_aSIgg",
    })
    void invalidSubscriptionIsRejected(String endpoint, String p256dh, String auth) {
        givenConfigured();

        assertThatThrownBy(() -> service.subscribe(1L, new PushSubscribeRequest(endpoint, new Keys(p256dh, auth))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        then(pushSubscriptionRepository).should(never()).save(any());
    }

    @Test
    void unsubscribeDeletesOnlyMine() {
        givenConfigured();

        service.unsubscribe(1L, ENDPOINT);

        then(pushSubscriptionRepository).should().deleteByEndpointAndUserId(ENDPOINT, 1L);
    }

    @Test
    void everyApiNeedsServerKeys() {
        given(webPushClient.isConfigured()).willReturn(false);
        PushSubscribeRequest request = new PushSubscribeRequest(ENDPOINT, new Keys(P256DH, AUTH));

        assertThatThrownBy(() -> service.subscribe(1L, request)).extracting("errorCode").isEqualTo(ErrorCode.PUSH_NOT_CONFIGURED);
        assertThatThrownBy(() -> service.unsubscribe(1L, ENDPOINT)).extracting("errorCode").isEqualTo(ErrorCode.PUSH_NOT_CONFIGURED);
        assertThatThrownBy(() -> service.sendTest(1L)).extracting("errorCode").isEqualTo(ErrorCode.PUSH_NOT_CONFIGURED);
    }

    @Test
    void sendCountsResultsAndRemovesExpiredDevices(CapturedOutput output) {
        List<PushSubscription> devices = List.of(subscription(1L, user, ENDPOINT + "a"), subscription(2L, user, ENDPOINT + "b"),
                subscription(3L, user, ENDPOINT + "c"), subscription(4L, user, ENDPOINT + "d"));
        given(pushSubscriptionRepository.findByUserIdOrderByIdAsc(1L)).willReturn(devices);
        given(webPushClient.send(any(), any(), any(), any())).willReturn(201, 410, 404, 500);

        PushSendResponse result = service.sendToUser(1L, NotificationMessage.test());

        assertThat(result).isEqualTo(new PushSendResponse(1, 3));
        then(pushSubscriptionRepository).should().deleteById(2L);
        then(pushSubscriptionRepository).should().deleteById(3L);
        then(pushSubscriptionRepository).should(never()).deleteById(4L);  // 500 은 로그만
        assertThat(output).contains("subscriptionId=4 status=500")
                .doesNotContain("secret-device").doesNotContain(P256DH).doesNotContain(AUTH);
    }

    @Test
    void sendTestPayloadIsJsonForServiceWorker() {
        givenConfigured();
        given(pushSubscriptionRepository.findByUserIdOrderByIdAsc(1L)).willReturn(List.of(subscription(1L, user, ENDPOINT)));
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        given(webPushClient.send(anyString(), any(), any(), payload.capture())).willReturn(201);

        assertThat(service.sendTest(1L)).isEqualTo(new PushSendResponse(1, 0));
        assertThat(new String(payload.getValue(), StandardCharsets.UTF_8))
                .isEqualTo("{\"title\":\"오르도 알림 테스트\",\"body\":\"이 기기에서 일정 알림을 받을 수 있어요.\",\"tag\":\"test\",\"url\":\"/\"}");
    }

    private void givenConfigured() {
        given(webPushClient.isConfigured()).willReturn(true);
    }

    private static User user() {
        return User.builder().email("haeun@khu.ac.kr").password("encoded").name("이하은").build();
    }

    private static PushSubscription subscription(Long id, User user, String endpoint) {
        PushSubscription subscription = PushSubscription.builder().user(user).endpoint(endpoint).p256dh(P256DH).auth(AUTH).build();
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }
}
