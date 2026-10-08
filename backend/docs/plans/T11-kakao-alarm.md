# T11 카카오톡 일정 알림 ("나에게 보내기") — 구현 계획

> 상태: **승인됨(2026-10-08), 추천안대로 진행.** 진행 기록은 `docs/PROGRESS.md`.
> 작성: 2026-10-08 (백엔드 A). 처음엔 T10 으로 썼으나 명세 T10(e캠퍼스)과 겹쳐 T11 로 바꿈.

---

## 0. 현재 코드 상태 (2026-10-08 기준)

- 명세 T0~T9 + T10 e캠퍼스까지 구현 완료, 테스트 113개 통과.
- `master` 에는 T0~T3 만 머지됨. 나머지는 PR #3 ← #5 ← #6 ← #7 ← #8 로 쌓여 리뷰 대기.
- 알림에 필요한 값은 이미 있다:
  - `schedules.alarm_minutes_before` (null / 10 / 30 / 60 / 1440, 저장만 하는 중)
  - `schedules.done`, `schedules.start_time`(null 이면 할 일)
  - `users.notification_enabled` (마이페이지 알림 on/off, `PATCH /api/users/me/settings`)
- Flyway: V1, V2(공동), V10(A, e캠퍼스) 사용 중 → 이번 작업은 **V11**.
- 스케줄러(`@EnableScheduling`)는 아직 없다.

## 1. 목표

- 사용자가 카카오 계정을 한 번 연결하면, 알람을 설정한 일정·할 일을 정해진 시각에 **카카오톡 "나와의 채팅"으로 보낸다**.
- 발송 시각 = `일정 날짜 + 시작시간 - alarm_minutes_before`.
  시간이 없는 할 일은 **그날 09:00** 을 기준으로 한다 → **명세 9장 미결사항으로 올려 확인받는다.**
- 같은 알림은 한 번만 보낸다(서버를 재시작하거나 여러 대로 늘려도).
- 토큰은 암호화 저장, 만료되면 자동 갱신, 갱신이 안 되면 `EXPIRED` 로 바꿔 앱이 재연결을 안내한다.
- 발송부는 `NotificationSender` 인터페이스로 분리해 나중에 웹푸시 등으로 바꿀 수 있게 한다.
- 메시지에는 **제목·시간·장소만** 넣는다(메모 제외).

이번 범위가 아닌 것: 카카오 로그인(회원가입 대체), 친구에게 보내기·알림톡, e캠퍼스 과제 알림(제출 여부를 몰라서), 발송 실패 재시도.

## 2. API (전부 🔒)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | /api/notifications/kakao/connect | `{ "code": "...", "redirectUri": "http://localhost:3000/oauth/kakao/callback" }` → 카카오와 토큰 교환 후 저장 → 200, data = 연결 상태 |
| GET | /api/notifications/kakao | 연결 상태 |
| DELETE | /api/notifications/kakao | 연결 해제(카카오 `unlink` 호출 후 토큰 삭제) → 204 |

```json
// GET /api/notifications/kakao, connect 응답 data
{ "status": "CONNECTED", "connectedAt": "2026-10-08T21:00:00" }   // status: NONE / CONNECTED / EXPIRED
```

- **흐름**: 프론트가 사용자를 `https://kauth.kakao.com/oauth/authorize?client_id={REST_API_KEY}&redirect_uri={URI}&response_type=code&scope=talk_message&state={랜덤}` 로 보낸다. 카카오가 `redirectUri?code=...&state=...` 로 돌려보내면, 프론트가 `state` 를 확인(CSRF 방지)하고 `code` 와 `redirectUri` 를 위 connect API 로 보낸다.
- `redirectUri` 는 서버 설정(`KAKAO_REDIRECT_URIS`)에 있는 값만 허용한다. 아니면 `INVALID_INPUT`.
- 토큰 응답의 `scope` 에 `talk_message` 가 없으면(사용자가 동의를 해제) 저장하지 않고 `KAKAO_SCOPE_REQUIRED`.
- 이미 연결돼 있으면 새 토큰으로 덮어쓴다(재연결 = 같은 API).
- 키가 설정되지 않은 서버에서는 `KAKAO_NOT_CONFIGURED`.
- 마이페이지 알림 끄기(`notificationEnabled=false`)면 연결은 유지하고 발송만 하지 않는다.

**새 에러 코드**(명세 2.5 표에 먼저 추가)

| 코드 | HTTP | 언제 |
|---|---|---|
| KAKAO_NOT_CONFIGURED | 503 | 서버에 카카오 키가 없음 |
| KAKAO_AUTH_FAILED | 400 | 인가코드가 틀렸거나 만료, redirectUri 불일치 |
| KAKAO_SCOPE_REQUIRED | 400 | '카카오톡 메시지 전송' 동의 안 함 |
| KAKAO_UNAVAILABLE | 502 | 카카오 서버 오류·시간 초과 |

## 3. DB 변경 — `V11__add_kakao_notification.sql` (★ 승인 필요, 새 테이블 2개)

```sql
CREATE TABLE kakao_connections (            -- 사용자당 1행
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                  BIGINT       NOT NULL,
    access_token_enc         VARCHAR(512) NOT NULL,   -- AES-256-GCM 암호문(Base64)
    refresh_token_enc        VARCHAR(512) NOT NULL,
    access_token_expires_at  DATETIME(6)  NOT NULL,
    refresh_token_expires_at DATETIME(6)  NULL,
    status                   VARCHAR(20)  NOT NULL COMMENT 'CONNECTED / EXPIRED',
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_kakao_connections_user (user_id),
    CONSTRAINT fk_kakao_connections_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE notification_logs (             -- 중복 발송 방지 + 기록
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    schedule_id BIGINT      NOT NULL,
    channel     VARCHAR(20) NOT NULL COMMENT 'KAKAO',
    notify_at   DATETIME    NOT NULL COMMENT '계산된 발송 예정 시각(분 단위)',
    status      VARCHAR(20) NOT NULL COMMENT 'SENDING / SENT / FAILED',
    sent_at     DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_logs (schedule_id, channel, notify_at),
    CONSTRAINT fk_notification_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_logs_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id) ON DELETE CASCADE
);
```

- **users 에 칸을 더하지 않고 별도 테이블**로 둔 이유: 토큰 2개 + 만료 2개 + 상태로 5칸이고, 연결하지 않은 사용자가 대부분이라.
- **중복 방지**: `(schedule_id, channel, notify_at)` 유일 키. 보내기 전에 이 행을 먼저 넣고(선점), 넣기에 실패하면(이미 있음) 보내지 않는다 → 서버가 여러 대여도 한 번만 발송.
- 일정 시간이나 알람을 바꾸면 `notify_at` 이 달라지므로 새 알림이 다시 나간다(의도한 동작).

## 4. 클래스 구조 (새 패키지 `com.ordo.notification`)

```
notification/
├── controller/KakaoNotificationController      connect / status / disconnect
├── domain/KakaoConnection, KakaoConnectionStatus(CONNECTED, EXPIRED)
├── domain/NotificationLog, NotificationStatus(SENDING, SENT, FAILED)
├── repository/KakaoConnectionRepository, NotificationLogRepository
├── dto/KakaoConnectRequest(code, redirectUri), KakaoConnectionResponse(status, connectedAt)
├── service/KakaoConnectionService     토큰 교환·저장·갱신·만료 처리·해제
├── service/NotificationService        발송 대상 찾기 → 선점 → 발송 → 기록
├── service/NotificationScheduler      @Scheduled(fixedDelay = 60초) → NotificationService.sendDue()
├── service/NotificationMessage        record(title, date, startTime, location) + 문구 만들기 (메모 없음)
├── service/NotificationSender         interface: send(User, NotificationMessage)
├── service/KakaoMemoSender            implements NotificationSender (KakaoConnectionService + KakaoClient)
├── service/KakaoClient                JDK HttpClient: exchangeCode / refresh / sendMemo / unlink (5초 제한)
├── service/TokenCipher                AES-256-GCM 암·복호화 (키: KAKAO_TOKEN_ENC_KEY)
└── config/SchedulingConfig            @EnableScheduling, app.notification.scheduler-enabled=false 면 끔(테스트)
```

### 발송 흐름 (`NotificationService.sendDue`, 1분마다)
1. `now` = 한국 시간, 분 단위로 자름.
2. 후보 조회(쿼리 1번): `alarm_minutes_before IS NOT NULL`, `done = false`, `schedule_date` 가 어제~내일, 사용자 `notification_enabled = true`, 카카오 연결 `CONNECTED`.
3. 각 일정의 `notifyAt = date + (startTime ?? 09:00) - alarm분` 계산 → **`now - 10분 < notifyAt <= now`** 인 것만.
   - 10분보다 오래된 것은 보내지 않는다(서버가 꺼져 있다 켜졌을 때 늦은 알림이 한꺼번에 가는 것 방지).
4. `notification_logs` 에 `SENDING` 행 삽입(유일 키로 선점). 실패하면 건너뜀.
5. 트랜잭션 밖에서 `NotificationSender.send` → 성공 `SENT` / 실패 `FAILED`(재시도 없음).

### 토큰 처리 (`KakaoConnectionService.validAccessToken`)
- 액세스 토큰 만료 1분 전부터는 먼저 갱신. 발송 응답이 401이면 한 번 갱신 후 재시도.
- 갱신 응답에 새 리프레시 토큰이 오면(카카오는 만료 1개월 미만일 때만 줌) 같이 저장.
- 갱신 실패(리프레시 만료·연결 끊김) → `status = EXPIRED`, 이후 발송 대상에서 빠짐. 앱은 GET 응답의 `EXPIRED` 를 보고 재연결을 안내.

### 메시지 (텍스트 템플릿)
```
[오르도] 일정 알림
디자인씽킹 과제 제출
10월 18일 (일) 09:00 · 예405        ← 할 일이면 "10월 18일 (일) 할 일", 장소 없으면 생략
```
- 링크 버튼은 `ORDO_WEB_URL`(예: http://localhost:3000)로 연결. 이 도메인은 카카오 앱 설정에 등록해야 한다.
- 제목이 길면 잘라낸다(텍스트 템플릿 길이 제한).

### 설정 (`application.yml` — 값 없이 환경변수 이름만)
```yaml
app:
  kakao:
    rest-api-key: ${KAKAO_REST_API_KEY:}
    client-secret: ${KAKAO_CLIENT_SECRET:}
    token-enc-key: ${KAKAO_TOKEN_ENC_KEY:}          # Base64 32바이트
    redirect-uris: ${KAKAO_REDIRECT_URIS:http://localhost:3000/oauth/kakao/callback}
    web-url: ${ORDO_WEB_URL:http://localhost:3000}
  notification:
    scheduler-enabled: ${NOTIFICATION_SCHEDULER_ENABLED:true}
```
키가 비어 있어도 서버는 정상으로 뜨고, 카카오 API 만 `KAKAO_NOT_CONFIGURED` 를 준다. 키 값은 어디에도 커밋하지 않는다.

## 5. 테스트 계획 (실제 카카오 API 는 호출하지 않음 — `KakaoClient` 는 Mock)

| 대상 | 확인할 것 |
|---|---|
| TokenCipher | 암호화 → 복호화 원래 값, 같은 값도 매번 다른 암호문, 암호문 1바이트 변조 시 실패, 키 없으면 `KAKAO_NOT_CONFIGURED` |
| KakaoConnectionService | connect: 허용 안 된 redirectUri 거부, scope 에 talk_message 없으면 거부, DB 에는 평문 토큰이 없음 / 만료 직전이면 갱신 / 갱신 실패 → EXPIRED / disconnect: unlink 실패해도 로컬 삭제 |
| 발송 시각 계산 | 09:00 일정 - 30분 = 08:30, 할 일 - 1440분 = 전날 09:00, 자정 넘김 |
| NotificationService | now 기준 대상 선별(10분 창), done·알림 꺼짐·미연결·EXPIRED 제외, 이미 로그 있으면 안 보냄(H2 유일 키로 실제 확인), 401 → 갱신 후 1회 재시도, 실패 시 FAILED |
| NotificationMessage | 제목·시간·장소만 있고 **메모가 없음**, 장소 없으면 줄 생략 |
| 컨트롤러 | 토큰 없으면 401, 남의 연결은 볼 수 없음(본인 것만) |
| 로그 | 토큰 문자열이 로그에 안 찍힘(OutputCapture) |

로컬 실서버 확인: 키 없이 띄워서 503 확인 → (사용자가 키 설정 후) 실제 연결 → 2분 뒤 알림 일정 만들어 수신 확인.

## 6. 위험 요소

1. **"나에게 보내기"는 휴대폰 푸시 알림이 안 올 수 있다.** 나와의 채팅 메시지의 알림 여부는 카카오 문서에 없다. 푸시가 안 오면 '알림' 기능으로서 가치가 크게 떨어진다 → 실제 폰으로 먼저 확인(7절 9번). 안 되면 대안: 카카오 알림톡(비즈 채널·유료·심사), 웹푸시(이 구조로 Sender 만 교체).
2. 카카오 일일·월간 쿼터: '나에게 보내기'에도 쿼터가 있다(쿼터 문서 확인 필요). 학생 수가 적은 MVP 에선 문제없을 것으로 예상.
3. 암호화 키를 잃으면 저장된 토큰을 못 읽는다 → 모두 `EXPIRED` 처리하고 재연결 안내. 키는 비밀번호 관리자 등에 따로 보관.
4. 서버 시간대: JVM 시간대와 상관없이 Asia/Seoul 로 계산한다.
5. 다운타임에 놓친 알림은 10분까지만 보낸다(그 이상은 버림) — 미결사항.
6. 카카오 동의 화면에서 사용자가 '메시지 전송'을 해제할 수 있다 → scope 확인으로 막음.
7. 링크 도메인 미등록이면 메시지 발송이 실패한다 → 체크리스트 6번.

## 7. 내가(사용자) 직접 할 일 — 체크리스트

> 키·시크릿 값은 **채팅·코드·커밋에 붙여넣지 말고** 환경변수로만 넣는다.

1. [ ] https://developers.kakao.com 로그인 → **내 애플리케이션 → 애플리케이션 추가하기** (앱 이름 "오르도", 회사명은 팀명).
2. [ ] **앱 키** 메뉴에서 **REST API 키** 확인 → 환경변수 `KAKAO_REST_API_KEY` 로 설정(아래 10번).
3. [ ] **카카오 로그인 → 활성화 설정 ON**.
4. [ ] **카카오 로그인 → Redirect URI 등록**: `http://localhost:3000/oauth/kakao/callback` (프론트 콜백 주소. 배포하면 배포 주소도 추가). 서버 환경변수 `KAKAO_REDIRECT_URIS` 와 글자까지 같아야 한다.
5. [ ] **보안 → Client Secret 코드 생성 → 활성화 "사용함"** → 환경변수 `KAKAO_CLIENT_SECRET`.
6. [ ] **플랫폼 → Web → 사이트 도메인 등록**: `http://localhost:3000` (메시지 링크 버튼용).
7. [ ] **동의항목 → '카카오톡 메시지 전송(talk_message)'** 을 **선택 동의**로 설정하고 동의 목적("일정 알림 발송")을 적는다.
8. [ ] **팀원 관리 → 팀원 추가**: 테스트할 팀원 카카오 계정을 초대(초대 메일 수락 필요).
9. [ ] **실제 폰 푸시 테스트(가장 중요)**: 연결 후 2~3분 뒤로 알람 10분 일정(즉 곧 발송될 일정)을 만들고, 폰 카카오톡 **나와의 채팅**에 메시지가 오는지 + **잠금화면 알림이 뜨는지** 확인해 결과를 알려준다. 나와의 채팅 알림이 꺼져 있으면 채팅방 설정에서 켜고 다시 확인.
10. [ ] **암호화 키 만들기**(PowerShell):
    ```powershell
    $b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
    ```
    나온 값을 환경변수 `KAKAO_TOKEN_ENC_KEY` 로 설정하고 따로 보관(잃으면 모두 재연결).
11. [ ] 환경변수 설정: Windows **시스템 속성 → 환경 변수 → 사용자 변수 → 새로 만들기**로 1~10의 값을 넣고, 터미널·IntelliJ 를 다시 연다. (또는 git 에 안 올라가는 `application-local.yml` 에 넣기)
12. [ ] 프론트 담당에게: 카카오 콜백 페이지(`/oauth/kakao/callback`)와 `state` 확인, connect API 호출을 부탁.

## 8. 작업 순서 (승인 후, 단계마다 `docs/PROGRESS.md` 갱신)

1. V11 마이그레이션 + 엔티티·리포지토리 + `TokenCipher` (+테스트)
2. `KakaoClient` + `KakaoConnectionService` + 컨트롤러 3개 + 에러 코드 (+테스트, Mock)
3. `NotificationSender` / `KakaoMemoSender` / `NotificationMessage` (+테스트)
4. `NotificationService` + 스케줄러 + 설정 (+테스트, H2 로 중복 방지 확인)
5. 로컬 서버 확인(키 없이 503) → 사용자 체크리스트 완료 후 실제 연결·수신 확인
6. 명세 반영(2.5 에러 코드, 4장 API, 7장 V11, 8장 행, 9장 미결사항), FRONTEND_GUIDE 연동 절, 커밋 제안

## 9. 명세 9장에 올릴 미결사항 (승인 시 추가)

| # | 내용 | 임시 결정 |
|---|---|---|
| 12 | 시간 없는 할 일의 알림 기준 시각 | 그날 09:00 (예: 하루 전 알림 = 전날 09:00) — **확인 필요** |
| 13 | '나에게 보내기' 푸시 알림 여부 | 실기기 확인 전 미정. 안 오면 알림톡·웹푸시 검토 |
| 14 | 서버가 꺼져 있던 동안 놓친 알림 | 10분 이내 것만 발송, 그보다 오래된 것은 버림 |
| 15 | 발송 실패 재시도 | 안 함(FAILED 기록만) |

공용 파일 변경 예정: `ErrorCode`(4개), `application.yml`(app.kakao·notification), `AGENTS.md`(패키지 목록에 notification). 새 의존성: **없음**(JDK HttpClient·javax.crypto 사용).
