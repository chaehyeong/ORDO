package com.ordo.ecampus.service;

import com.ordo.global.error.BusinessException;
import com.ordo.global.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** e캠퍼스(Canvas) 캘린더 피드를 내려받는다. 주소는 고정 경로 + 토큰이라 다른 서버로는 요청할 수 없다 */
@Component
public class EcampusFeedClient {

    private static final String FEED_BASE_URL = "https://khcanvas.khu.ac.kr/feeds/calendars/";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_BYTES = 2 * 1024 * 1024;  // 한 학기 피드는 20KB 정도

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** 토큰(user_...)이 들어간 주소는 비밀값이라 예외 메시지·로그에 남기지 않는다 */
    public byte[] fetch(String feedToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(FEED_BASE_URL + feedToken + ".ics"))
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_BYTES + 1);
                if (response.statusCode() != 200 || bytes.length > MAX_BYTES) {
                    throw new BusinessException(ErrorCode.ECAMPUS_FEED_UNAVAILABLE);
                }
                return bytes;
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.ECAMPUS_FEED_UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.ECAMPUS_FEED_UNAVAILABLE);
        }
    }
}
