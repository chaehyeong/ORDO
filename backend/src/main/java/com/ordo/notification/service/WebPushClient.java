package com.ordo.notification.service;

import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 푸시 서비스로 1건 보낸다: RFC 8291 암호화 + VAPID 서명(RFC 8292, 기존 jjwt 의 ES256).
 * 키는 환경변수(WEBPUSH_*)로만 받고, 키·endpoint 는 로그와 예외 메시지에 남기지 않는다.
 */
@Slf4j
@Component
public class WebPushClient {

    // 서버가 이 주소로 직접 요청하므로 허용한 푸시 서비스만 받는다(SSRF 방지). 저장할 때와 보낼 때 둘 다 확인
    private static final Set<String> ALLOWED_HOSTS = Set.of("fcm.googleapis.com", "updates.push.services.mozilla.com");
    private static final List<String> ALLOWED_HOST_SUFFIXES = List.of(".push.apple.com", ".notify.windows.com");
    static final int MAX_ENDPOINT_LENGTH = 700;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration JWT_VALIDITY = Duration.ofHours(12);  // RFC 8292: 24시간 이하
    private static final String TTL_SECONDS = "3600";  // 기기가 꺼져 있으면 1시간까지 보관 후 버림

    private final String publicKey;       // Base64url 87자, 설정 안 했으면 null
    private final ECPrivateKey privateKey;
    private final ECPublicKey verifyKey;
    private final String subject;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    Clock clock = Clock.systemUTC();

    public WebPushClient(@Value("${app.webpush.public-key:}") String publicKey,
                         @Value("${app.webpush.private-key:}") String privateKey,
                         @Value("${app.webpush.subject:}") String subject) {
        if (publicKey.isBlank() && privateKey.isBlank() && subject.isBlank()) {
            log.warn("웹푸시 키(WEBPUSH_PUBLIC_KEY·WEBPUSH_PRIVATE_KEY·WEBPUSH_SUBJECT)가 없어 알림을 보내지 않습니다");
            this.publicKey = null;
            this.privateKey = null;
            this.verifyKey = null;
            this.subject = null;
            return;
        }
        if (publicKey.isBlank() || privateKey.isBlank() || subject.isBlank()) {
            throw new IllegalStateException("WEBPUSH_PUBLIC_KEY·WEBPUSH_PRIVATE_KEY·WEBPUSH_SUBJECT 를 모두 설정하세요");
        }
        try {
            this.verifyKey = WebPushCrypto.toPublicKey(WebPushCrypto.decodeBase64Url(publicKey.trim()));
            this.privateKey = WebPushCrypto.toPrivateKey(WebPushCrypto.decodeBase64Url(privateKey.trim()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("웹푸시 키 형식이 올바르지 않습니다 (공개키 87자·개인키 43자 Base64url)");
        }
        this.publicKey = publicKey.trim();
        this.subject = subject.trim();
        // 애플은 localhost subject 를 거절한다. mailto: 또는 https: 만
        String lower = this.subject.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("mailto:") || lower.startsWith("https://")) || lower.contains("localhost")) {
            throw new IllegalStateException("WEBPUSH_SUBJECT 는 mailto:메일주소 또는 https://배포주소 여야 합니다 (localhost 불가)");
        }
        try {
            Jwts.parser().verifyWith(verifyKey).build().parseSignedClaims(vapidJwt("https://fcm.googleapis.com"));
        } catch (RuntimeException e) {
            throw new IllegalStateException("WEBPUSH_PUBLIC_KEY 와 WEBPUSH_PRIVATE_KEY 가 짝이 아닙니다");
        }
    }

    public boolean isConfigured() {
        return publicKey != null;
    }

    /** 프론트가 구독할 때 applicationServerKey 로 쓰는 값 (공개해도 됨) */
    public String publicKey() {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.PUSH_NOT_CONFIGURED);
        }
        return publicKey;
    }

    /** https·기본 포트·사용자정보 없음·700자 이하·허용 호스트 */
    public static boolean isAllowedEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() > MAX_ENDPOINT_LENGTH) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            return false;
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() != -1 || uri.getRawUserInfo() != null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return ALLOWED_HOSTS.contains(host) || ALLOWED_HOST_SUFFIXES.stream().anyMatch(host::endsWith);
    }

    /**
     * 1건 발송. 푸시 서비스의 HTTP 상태 코드(201 성공, 404·410 구독 만료)를, 연결 실패·시간 초과면 -1 을 돌려준다.
     * 재시도하지 않는다(명세 9장 15).
     */
    public int send(String endpoint, byte[] p256dh, byte[] auth, byte[] payload) {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.PUSH_NOT_CONFIGURED);
        }
        if (!isAllowedEndpoint(endpoint)) {
            return -1;
        }
        try {
            URI uri = URI.create(endpoint);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("Authorization", authorization("https://" + uri.getHost()))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", TTL_SECONDS)
                    .header("Urgency", "high")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(WebPushCrypto.encrypt(payload, p256dh, auth)))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (GeneralSecurityException | IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** VAPID 헤더 값: vapid t=<JWT>, k=<공개키> */
    String authorization(String audience) {
        return "vapid t=" + vapidJwt(audience) + ", k=" + publicKey;
    }

    String vapidJwt(String audience) {
        return Jwts.builder()
                .header().type("JWT").and()
                .audience().single(audience)
                .expiration(Date.from(clock.instant().plus(JWT_VALIDITY)))
                .subject(subject)
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }
}
