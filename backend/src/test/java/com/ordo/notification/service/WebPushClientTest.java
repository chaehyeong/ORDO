package com.ordo.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebPushClientTest {

    static final KeyPair KEYS = generate();
    static final String PUBLIC = WebPushCrypto.encodeBase64Url(WebPushCrypto.encodePublicKey((ECPublicKey) KEYS.getPublic()));
    static final String PRIVATE = privateKey(KEYS);

    @Test
    void vapidJwtIsSignedByServerKeyForEndpointOrigin() {
        WebPushClient client = new WebPushClient(PUBLIC, PRIVATE, "mailto:ordo-team@example.com");

        String header = client.authorization("https://fcm.googleapis.com");

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + PUBLIC);
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        Jws<Claims> parsed = Jwts.parser().verifyWith((ECPublicKey) KEYS.getPublic()).build().parseSignedClaims(jwt);
        assertThat(parsed.getHeader().getType()).isEqualTo("JWT");
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("ES256");
        assertThat(parsed.getPayload().getAudience()).containsExactly("https://fcm.googleapis.com");
        assertThat(parsed.getPayload().getSubject()).isEqualTo("mailto:ordo-team@example.com");
        assertThat(parsed.getPayload().getExpiration().toInstant())
                .isBetween(Instant.now().plusSeconds(11 * 3600), Instant.now().plusSeconds(24 * 3600));
        assertThat(new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1])))  // aud 는 배열이 아닌 문자열
                .contains("\"aud\":\"https://fcm.googleapis.com\"");
        assertThat(client.publicKey()).isEqualTo(PUBLIC);
    }

    @Test
    void withoutKeysPushIsNotConfigured() {
        WebPushClient client = new WebPushClient("", "", "");

        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(client::publicKey).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PUSH_NOT_CONFIGURED);
        assertThatThrownBy(() -> client.send("https://fcm.googleapis.com/fcm/send/x", new byte[65], new byte[16], new byte[1]))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void badConfigurationFailsStartupWithoutPrintingKeys() {
        String otherPublic = WebPushCrypto.encodeBase64Url(WebPushCrypto.encodePublicKey((ECPublicKey) generate().getPublic()));

        assertThatThrownBy(() -> new WebPushClient(otherPublic, PRIVATE, "mailto:a@example.com"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("짝이 아닙니다")
                .hasMessageNotContaining(PRIVATE);
        assertThatThrownBy(() -> new WebPushClient(PUBLIC, PRIVATE, "mailto:admin@localhost"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("localhost");
        assertThatThrownBy(() -> new WebPushClient(PUBLIC, PRIVATE, "https://localhost:3000"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebPushClient(PUBLIC, PRIVATE, "ordo-team@example.com"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebPushClient(PUBLIC, "", "mailto:a@example.com"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("모두 설정");
        assertThatThrownBy(() -> new WebPushClient(PUBLIC, "not-a-key", "mailto:a@example.com"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("not-a-key");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/dXk1",
            "https://updates.push.services.mozilla.com/wpush/v2/gAAA",
            "https://web.push.apple.com/QGuQyavXutnMH",
            "https://wns2-par02p.notify.windows.com/w/?token=BQYAAA",
    })
    void allowsKnownPushServices(String endpoint) {
        assertThat(WebPushClient.isAllowedEndpoint(endpoint)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/x",               // https 아님
            "https://localhost/push",
            "https://127.0.0.1/push",
            "https://169.254.169.254/latest/meta-data",           // 클라우드 메타데이터 주소
            "https://evilpush.apple.com/x",                       // 접미사 흉내
            "https://push.apple.com.evil.com/x",
            "https://fcm.googleapis.com.evil.com/x",
            "https://user@fcm.googleapis.com/x",                  // 사용자정보
            "https://fcm.googleapis.com@evil.com/x",
            "https://fcm.googleapis.com:8443/x",                  // 다른 포트
            "https://fcm.googleapis.com:443/x",                   // 포트를 적는 것도 거절
            "fcm.googleapis.com/x",
            "https://fcm.googleapis.com/x y",
            "",
    })
    void rejectsOtherEndpoints(String endpoint) {
        assertThat(WebPushClient.isAllowedEndpoint(endpoint)).isFalse();
    }

    @Test
    void rejectsTooLongEndpointAndNull() {
        String base = "https://fcm.googleapis.com/fcm/send/";
        assertThat(WebPushClient.isAllowedEndpoint(base + "a".repeat(700 - base.length()))).isTrue();
        assertThat(WebPushClient.isAllowedEndpoint(base + "a".repeat(701 - base.length()))).isFalse();
        assertThat(WebPushClient.isAllowedEndpoint(null)).isFalse();
    }

    @Test
    void sendToDisallowedEndpointMakesNoRequest() {
        WebPushClient client = new WebPushClient(PUBLIC, PRIVATE, "mailto:a@example.com");

        assertThat(client.send("https://127.0.0.1/push", new byte[65], new byte[16], new byte[1])).isEqualTo(-1);
    }

    static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String privateKey(KeyPair keys) {
        byte[] d = ((ECPrivateKey) keys.getPrivate()).getS().toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(d.length, 32);
        System.arraycopy(d, d.length - length, out, 32 - length, length);
        return WebPushCrypto.encodeBase64Url(Arrays.copyOf(out, 32));
    }
}
