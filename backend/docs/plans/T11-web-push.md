# T11 일정 알림 (웹푸시) — 구현 계획

> 상태: **계획, 승인 대기 (2026-10-09).** 진행 기록은 `docs/PROGRESS.md`.
> 2026-10-08 에 승인된 카카오 "나에게 보내기" 계획을 대체한다(이유는 0절). 명세(2.5·3.3·4.10·7·8·9장)는 아직 카카오 기준이고, 이 계획이 승인되면 12절 목록대로 바꾼다.

---

## 0. 카카오에서 웹푸시로 바꾼 이유

| 방법 | 결과 |
|---|---|
| 카카오 "나에게 보내기" | 메시지는 나와의 채팅에 쌓이지만 **휴대폰 알림이 울리지 않는다**(카카오 데브톡 답변). 알림 기능으로 쓸 수 없음 |
| 카카오 알림톡 | 알림은 울리지만 developers.kakao.com 이 아니라 공식 딜러사와 계약해야 하고, 비즈니스 채널에 **사업자등록증**이 필요. 템플릿 심사, 건당 약 7~13원 |
| 카카오 톡캘린더 API | 권한 심사 전에는 앱 멤버(팀원)만 사용 가능. 사업자 없는 개인 개발자의 승인 여부가 불확실하고, 일정 수정·삭제를 카카오 캘린더와 계속 맞춰야 함 |
| **웹푸시** | 브라우저 표준(Push API). 무료, 심사·사업자 불필요. 안드로이드·PC 는 바로, 아이폰은 iOS 16.4+ 에서 홈 화면에 추가한 경우만 |

→ **웹푸시로 결정(2026-10-09, 사용자).** 카카오 키·체크리스트·토큰 암호화는 모두 필요 없어진다.

## 1. 현재 코드 상태 (2026-10-09)

- 명세 T0~T10 + T12 구현, 테스트 133개. PR #3 ← #5 ← #6 ← #7 ← #8 ← `feat/import` 리뷰 대기.
- 알림에 쓸 값은 이미 있다:
  - `schedules.alarm_minutes_before` (null / 10 / 30 / 60 / 1440, 지금은 저장만)
  - `schedules.done`, `schedules.start_time`(null 이면 할 일)
  - `users.notification_enabled` (마이페이지 알림 on/off)
- Flyway: V1·V2(공동), V10(A) 사용 중 → 이번 작업은 **V11**. 스케줄러(`@EnableScheduling`)는 아직 없다.
- jjwt 0.12.6 이 이미 있다(VAPID 서명에 그대로 쓴다).
- 프론트는 CRA(react-scripts): `public/` 에 서비스워커 파일을 둘 수 있다. `manifest.json` 은 아직 없다.

## 2. 목표 / 범위

- 사용자가 브라우저(기기)에서 "알림 켜기"를 한 번 하면, 알람을 설정한 일정·할 일을 정해진 시각에 **그 기기의 알림(푸시)** 으로 보낸다. 기기는 여러 개 가능(폰·PC).
- 발송 시각 = `일정 날짜 + 시작시간 − alarm_minutes_before`. 시간이 없는 할 일은 **그날 09:00** 기준(명세 9장 12, 확인 대기).
- 같은 알림은 한 번만 보낸다(서버를 재시작하거나 여러 대로 늘려도).
- 알림 내용은 **제목·시간·장소만**(메모 제외).

이번 범위가 아닌 것: e캠퍼스 과제 알림(제출 여부를 몰라서), 발송 실패 재시도, 이메일·문자, 카카오.

## 3. 동작 원리

```
[처음 한 번: 기기 등록]
사용자 "알림 켜기" 클릭 → 브라우저 알림 권한 허용
프론트  → GET  /api/notifications/push/public-key        (서버 공개키 받기)
브라우저 → 푸시 서비스(크롬=구글 FCM, 사파리=애플, 파이어폭스=모질라)에 구독
        ← 구독 정보 { endpoint(이 기기로 가는 주소), keys.p256dh, keys.auth }
프론트  → POST /api/notifications/push/subscriptions     (구독 정보 저장)

[1분마다: 발송]
서버 스케줄러 → 보낼 일정 찾기 → 선점(중복 방지) → 내용을 기기 키로 암호화
서버 → POST {endpoint} (VAPID 서명 + 암호문) → 푸시 서비스 → 기기
기기의 서비스워커(sw.js)가 알림을 띄운다 (오르도 탭이 닫혀 있어도)
```

- 내용은 기기 키로 **종단 암호화**(RFC 8291)되어 구글·애플 푸시 서버도 읽을 수 없다.
- 서버는 VAPID 키 쌍(RFC 8292)으로 "이 서버가 보낸 것"임을 서명한다. 공개키는 프론트가 구독할 때 쓰고, 개인키는 환경변수로만 둔다.

## 4. API (전부 🔒)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/notifications/push/public-key | `{ "publicKey": "BMx…(87자)" }`: 구독할 때 `applicationServerKey` 로 쓴다 |
| POST | /api/notifications/push/subscriptions | 브라우저의 `subscription.toJSON()` 그대로 → 204. 같은 endpoint 면 갱신(다른 사용자 것이었으면 지금 사용자로 옮김) |
| DELETE | /api/notifications/push/subscriptions | `{ "endpoint": "..." }` → 204. 내 것만 지우고, 없어도 204 |
| POST | /api/notifications/push/test | 내 모든 기기로 테스트 알림을 바로 보냄 → `{ "sent": 1, "failed": 0 }` |

```json
// POST /api/notifications/push/subscriptions 요청 (브라우저가 만든 값 그대로, expirationTime 은 무시)
{ "endpoint": "https://fcm.googleapis.com/fcm/send/dXk…",
  "keys": { "p256dh": "BNc…(87자)", "auth": "tBH…(22자)" } }
```

- 검증(아니면 `INVALID_INPUT`):
  - endpoint: `https`, 포트 지정 없음(443), 사용자정보(`a@host`) 없음, 700자 이하, 호스트가 **허용 목록**에 있어야 함:
    `fcm.googleapis.com`, `updates.push.services.mozilla.com`, `*.push.apple.com`, `*.notify.windows.com`(Edge).
    서버가 이 주소로 직접 요청을 보내므로, 아무 주소나 받으면 내부망을 찌르는 통로(SSRF)가 된다. 저장할 때와 보낼 때 둘 다 확인.
  - `p256dh` = Base64url 로 65바이트인 P-256 공개키, `auth` = 16바이트.
- 사용자당 기기는 최대 10개. 넘으면 가장 오래된 것을 지운다.
- 서버에 VAPID 키가 없으면 4개 모두 `PUSH_NOT_CONFIGURED`(503). 키가 없어도 서버는 정상으로 뜬다.
- 마이페이지 알림 끄기(`notificationEnabled=false`)면 등록은 유지하고 발송만 안 한다(기존 API 그대로).
- 테스트 API 는 실기기 확인(특히 아이폰)을 일정 만들고 기다리지 않고 바로 하려고 둔다. 사용자가 직접 누른 것이라 알림 끄기 상태여도 보낸다.

**새 에러 코드**(명세 2.5 표에 먼저 추가, 표의 `KAKAO_*` 4개는 삭제)

| 코드 | HTTP | 언제 |
|---|---|---|
| PUSH_NOT_CONFIGURED | 503 | 서버에 웹푸시 키가 설정되지 않음 |

## 5. DB 변경 — `V11__add_web_push.sql` (★ 승인 필요)

```sql
CREATE TABLE push_subscriptions (            -- 기기(브라우저)당 1행
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    endpoint   VARCHAR(700) NOT NULL COMMENT '푸시 서비스 주소(허용 목록 호스트만)',
    p256dh     VARCHAR(100) NOT NULL COMMENT '브라우저 공개키 Base64url',
    auth       VARCHAR(30)  NOT NULL COMMENT '인증 비밀 Base64url',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_push_subscriptions_endpoint (endpoint),
    CONSTRAINT fk_push_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE schedules
    ADD COLUMN alarm_sent_for DATETIME NULL COMMENT '알림을 처리한 발송 예정 시각(중복 방지)',
    ADD KEY idx_schedules_date (schedule_date);
```

- 카카오 계획의 테이블 2개(`kakao_connections`, `notification_logs`) → **테이블 1개 + 칸 1개**로 줄였다.
- **중복 방지(선점)**: 보내기 전에
  `UPDATE schedules SET alarm_sent_for = :notifyAt WHERE id = :id AND (alarm_sent_for IS NULL OR alarm_sent_for <> :notifyAt)`
  → 바뀐 행이 1이면 내가 보낼 차례, 0이면 이미 처리됨. 서버가 여러 대여도 한 번만 나간다.
- 일정 시간이나 알람을 바꾸면 `notifyAt` 이 달라지므로 새 알림이 다시 나간다(의도한 동작).
- `idx_schedules_date`: 매분 "어제~내일" 일정을 날짜로 찾는다(기존 인덱스는 `user_id` 가 앞이라 쓸 수 없음).
- `alarm_sent_for` 는 응답 DTO 에 넣지 않는다(내부용).
- `p256dh`·`auth` 는 메시지를 "암호화"만 할 수 있고 "읽을" 수는 없는 값이라 따로 암호화하지 않는다. 그래도 로그에는 찍지 않는다.
- endpoint 유일 키: 700자 × 4바이트 = 2,800바이트로 MySQL 8 인덱스 한도(3,072)보다 작다.

## 6. 클래스 구조 (새 패키지 `com.ordo.notification`)

```
notification/
├── controller/PushNotificationController     public-key / 구독 등록·삭제 / 테스트 발송
├── domain/PushSubscription
├── repository/PushSubscriptionRepository
├── dto/PushSubscribeRequest(endpoint, keys{p256dh, auth}), PushUnsubscribeRequest(endpoint),
│       PushPublicKeyResponse(publicKey), PushTestResponse(sent, failed)
├── service/PushSubscriptionService    검증(허용 목록·키 길이)·등록·삭제·기기 10개 제한
├── service/NotificationService        1분마다: 대상 찾기 → 선점 → 발송
├── service/NotificationScheduler      @Scheduled(fixedDelay = 60초) → NotificationService.sendDue()
├── service/NotificationMessage        record(title, body, tag) + 일정 → 문구 만들기 (메모 없음)
├── service/WebPushSender              기기마다 암호화 + 전송, 404·410 이면 구독 삭제
├── service/WebPushClient              VAPID JWT(jjwt ES256) + JDK HttpClient POST (5초 제한)
├── service/WebPushCrypto              RFC 8291 aes128gcm 암호화 (javax.crypto, static 메서드)
└── config/SchedulingConfig            @EnableScheduling, app.notification.scheduler-enabled=false 면 끔(테스트)

+ schedule/domain/Schedule 에 alarmSentFor 필드, ScheduleRepository 에 후보 조회·선점 쿼리
```

카카오 계획에 있던 `NotificationSender` 인터페이스는 뺐다. 구현이 하나뿐이라 필요해질 때 추출한다.

### 발송 흐름 (`NotificationService.sendDue`, 1분마다)
1. `now` = 한국 시간(Asia/Seoul, JVM 시간대와 무관), 분 단위로 자름.
2. 후보 조회(쿼리 1번): `alarm_minutes_before IS NOT NULL`, `done = false`, `schedule_date` 가 어제~내일, 사용자 `notification_enabled = true`, 그 사용자의 구독이 1개 이상.
3. 각 일정의 `notifyAt = date + (startTime ?? 09:00) − alarm분` 계산 → **`now − 10분 < notifyAt ≤ now`** 인 것만.
   10분보다 오래된 것은 보내지 않는다(서버가 꺼져 있다 켜졌을 때 늦은 알림이 한꺼번에 가는 것 방지, 9장 14).
4. 선점 UPDATE(5절). 0행이면 건너뜀.
5. 트랜잭션 밖에서 그 사용자의 모든 기기로 발송. 기기별 결과:
   - 201 → 성공
   - 404·410 → 구독이 만료됨 → 그 행 삭제
   - 그 밖(403 키 불일치, 429, 5xx, 시간 초과) → 경고 로그(구독 id·상태 코드만), **재시도 없음**(9장 15)

### 암호화·서명
- **내용 암호화 (RFC 8291, `Content-Encoding: aes128gcm`)**: 메시지마다 임시 P-256 키쌍 생성 → 기기 공개키와 ECDH → HKDF-SHA256 으로 키(16바이트)·nonce(12바이트) 도출 → AES-128-GCM.
  모두 JDK 표준 기능(`KeyPairGenerator EC`, `KeyAgreement ECDH`, `Mac HmacSHA256`, `Cipher AES/GCM/NoPadding`), 80줄 정도.
- **VAPID (RFC 8292)**: JWT `{ aud: endpoint 의 origin, exp: 지금 + 12시간, sub: WEBPUSH_SUBJECT }` 를 **이미 있는 jjwt** 로 ES256 서명
  → 헤더 `Authorization: vapid t=<JWT>, k=<공개키>`.
- 그 밖의 헤더: `TTL: 3600`(기기가 꺼져 있으면 푸시 서비스가 1시간까지 보관 후 버림), `Urgency: high`, `Content-Type: application/octet-stream`.
- 리다이렉트는 따라가지 않는다(JDK HttpClient 기본값).
- 서버 시작 때 확인: 공개키·개인키가 짝인지(서명 → 검증), subject 가 `mailto:`/`https:` 로 시작하고 localhost 가 아닌지(애플이 거절함). 틀리면 시작 실패(키 값은 로그에 찍지 않음).

### 의존성 선택 (★ 승인 필요)

| 안 | 내용 | 장단점 |
|---|---|---|
| **A. 새 의존성 없음 (추천)** | 위처럼 JDK + 기존 jjwt 로 직접 구현 | 의존성 0개. 암호 코드를 직접 짜는 위험은 **RFC 8291 부록 A 의 공식 테스트 벡터**(고정 키·salt → 정해진 암호문)와 바이트까지 같은지 테스트로 막고, 실제 브라우저 수신으로 한 번 더 확인 |
| B. `nl.martijndwars:web-push:5.1.2` | 자바 웹푸시 라이브러리 | 우리 코드는 줄지만 BouncyCastle·Apache HttpAsyncClient 를 함께 끌고 온다. 2020년(5.1.1) 이후 2025년 5.1.2 하나뿐일 정도로 관리가 뜸함 |
| C. Firebase(FCM) Admin SDK | 구글 Firebase 를 거쳐 발송 | 의존성이 크고 Firebase 프로젝트·서비스 계정 키가 필요, 프론트도 Firebase SDK 를 써야 함 |

### 알림 내용 (서비스워커가 받는 JSON, 암호화되어 전달)
```json
{ "title": "디자인씽킹 과제 제출", "body": "10월 18일 (일) 09:00 · 예405", "tag": "schedule-42", "url": "/" }
```
- 할 일이면 `"10월 18일 (일) 할 일"`, 장소가 없으면 `· 장소` 를 생략. **메모는 넣지 않는다.**
- `tag` 가 같으면 같은 일정의 이전 알림을 덮어쓴다. 알림을 누르면 `url`(오르도 홈)이 열린다.
- 제목·장소가 최대 100자라 페이로드 한도(약 4KB) 안에 항상 들어간다.
- 테스트 API 내용: `{ "title": "오르도 알림 테스트", "body": "이 기기에서 일정 알림을 받을 수 있어요.", "tag": "test", "url": "/" }`

### 설정 (`application.yml`, 값 없이 환경변수 이름만)
```yaml
app:
  webpush:
    public-key: ${WEBPUSH_PUBLIC_KEY:}     # Base64url 87자 (공개해도 되는 값)
    private-key: ${WEBPUSH_PRIVATE_KEY:}   # Base64url 43자 (비밀)
    subject: ${WEBPUSH_SUBJECT:}           # mailto:팀메일 또는 https://배포주소
  notification:
    scheduler-enabled: ${NOTIFICATION_SCHEDULER_ENABLED:true}
```
키 값은 어디에도 커밋하지 않는다. 키를 바꾸면 기존 구독이 모두 무효가 되고, 프론트가 다시 구독해야 한다(7절 5번이 자동으로 처리).

## 7. 프론트 할 일 (승인 후 FRONTEND_GUIDE 에 예시 코드와 함께 정리)

1. `public/sw.js` 서비스워커: `push` 이벤트에서 `showNotification(title, { body, tag, data: { url } })`, `notificationclick` 에서 오르도 창 열기.
2. 앱 시작 때 `navigator.serviceWorker.register('/sw.js')`.
3. 마이페이지 "이 기기에서 알림 받기" 버튼(**반드시 사용자 클릭 안에서**): 권한 요청 → 공개키 받기 → 구독 → 서버에 저장.
4. 로그아웃 때 `DELETE subscriptions { endpoint }` + `subscription.unsubscribe()` (같은 PC 를 다른 사람이 쓰면 앞 사람 알림이 오지 않게).
5. 로그인 후 앱을 열 때마다, 권한이 이미 허용돼 있으면 현재 구독을 다시 `POST`(같은 값이면 갱신만 됨 → 키 교체·구독 만료 자동 복구).
6. 아이폰용: `public/manifest.json`(`"display": "standalone"`, 이름, 아이콘) + `index.html` 에 `<link rel="manifest" href="/manifest.json">`. 화면에 "공유 → 홈 화면에 추가 후 앱에서 알림 켜기" 안내.

```js
// 3번 예시
const toBytes = (s) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0));
const reg = await navigator.serviceWorker.ready;
if ((await Notification.requestPermission()) !== 'granted') return;
const { data } = await api.get('/api/notifications/push/public-key');
const sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: toBytes(data.publicKey) });
await api.post('/api/notifications/push/subscriptions', sub.toJSON());
```

## 8. 테스트 계획 (실제 푸시 서비스는 호출하지 않음, `WebPushClient` 는 Mock)

| 대상 | 확인할 것 |
|---|---|
| WebPushCrypto | **RFC 8291 부록 A 테스트 벡터**: 같은 키·salt·평문 → 문서의 암호문과 바이트까지 같음 |
| VAPID | JWT 가 공개키로 검증됨, `aud` = endpoint origin, `exp` ≤ 24시간, `sub`, 헤더 형식 `vapid t=…, k=…` / 짝이 안 맞는 키·localhost subject → 시작 실패 |
| 구독 검증 | 4개 서비스 주소 통과 / `http`, `localhost`, IP 주소, `evilpush.apple.com`, `user@host`, 다른 포트, 700자 초과, 키 길이 틀림 → `INVALID_INPUT` |
| PushSubscriptionService | 같은 endpoint 재등록 = 갱신(다른 사용자 것이면 옮김), 11번째 기기 → 가장 오래된 것 삭제, 남의 구독은 지우지 못함 |
| 발송 시각 계산 | 09:00 일정 − 30분 = 08:30, 할 일 − 1440분 = 전날 09:00, 00:10 일정 − 30분 = 전날 23:40 |
| NotificationService | 10분 창 선별, done·알림 꺼짐·기기 없음 제외, 두 번 돌려도 한 번만(선점 UPDATE 를 H2 로 실제 확인), 시간을 바꾸면 다시 발송, 404·410 → 구독 삭제, 그 밖의 실패 → 로그만 |
| NotificationMessage | 제목·시간·장소만 있고 **메모가 없음**, 장소 없으면 생략 |
| 컨트롤러 | 토큰 없으면 401, 키 없는 서버 → 503 |
| 로그 | endpoint·p256dh·auth·개인키가 로그에 찍히지 않음(OutputCapture) |

로컬 실서버 확인: 키 없이 띄워 503 확인 → 키 설정 → 프론트가 준비되기 전에는 저장소 밖(스크래치)의 임시 HTML + `sw.js` 를 `http://localhost` 에 띄워(localhost 는 HTTPS 없이도 허용) 사용자 크롬으로 구독 → 테스트 API → Windows 알림 수신 → 2분 뒤 알람 일정으로 자동 발송 확인.

## 9. 위험 요소

1. **아이폰**: iOS 16.4 이상 + 홈 화면에 추가한 경우만 된다. 사파리 탭에서는 안 됨 → 앱 안에 안내가 필요. 아이폰 확인은 HTTPS 배포 주소가 있어야 가능(10절 5번).
2. 사용자가 권한을 "차단"하면 다시 물어볼 수 없다(브라우저 설정에서 직접 풀어야 함) → 버튼을 누를 때만 묻는다.
3. PC 는 브라우저가 실행 중이어야 받는다(창은 닫혀 있어도 됨, 크롬 백그라운드 실행 설정). 안드로이드·아이폰은 앱이 꺼져 있어도 받는다.
4. 기기가 1시간 넘게 꺼져 있으면 그 알림은 버려진다(TTL 3600).
5. VAPID 키를 잃거나 바꾸면 모든 구독이 무효 → 7절 5번으로 다음 접속 때 자동 재구독(접속 전까지는 알림 없음). 키는 따로 보관.
6. 암호 코드를 직접 구현 → RFC 테스트 벡터 + 실제 크롬·사파리 수신으로 확인.
7. SSRF → endpoint 허용 목록(4절). Edge 호스트(`*.notify.windows.com`)는 구현 때 실제 Edge 구독으로 확인.
8. 서버가 꺼져 있던 동안 놓친 알림은 10분까지만 보낸다(9장 14). 계산은 항상 Asia/Seoul.

## 10. 내가(사용자) 직접 할 일 — 체크리스트

> 개인키는 **채팅·코드·커밋에 붙여넣지 말고** 환경변수로만 넣는다.

1. [ ] **VAPID 키 만들기**(PowerShell, 한 번만):
    ```powershell
    $k = [System.Security.Cryptography.ECDsa]::Create([System.Security.Cryptography.ECCurve+NamedCurves]::nistP256); $p = $k.ExportParameters($true); $u = { param($b) [Convert]::ToBase64String($b).TrimEnd('=').Replace('+','-').Replace('/','_') }; "공개키: " + (& $u ([byte[]](,4 + $p.Q.X + $p.Q.Y))); "개인키: " + (& $u $p.D)
    ```
    공개키 87자, 개인키 43자가 나온다. 개인키는 비밀번호 관리자 등에 따로 보관(잃으면 모두 재구독).
2. [ ] 환경변수 3개 설정: `WEBPUSH_PUBLIC_KEY`, `WEBPUSH_PRIVATE_KEY`, `WEBPUSH_SUBJECT`(예: `mailto:` + 팀 대표 메일, localhost 주소는 안 됨).
    Windows **시스템 속성 → 환경 변수 → 사용자 변수 → 새로 만들기** 후 터미널·IntelliJ 를 다시 연다. (또는 git 에 안 올라가는 `application-local.yml` 에 넣기)
3. [ ] 팀 공유: 배포 서버도 **같은 키**를 써야 한다(개인키는 안전한 경로로만 전달). 로컬에서 각자 다른 키를 쓰면 자기 서버로 구독한 기기만 받는다.
4. [ ] PC 크롬 확인 준비: Windows **설정 → 시스템 → 알림** 에서 Chrome 알림 켜기, 방해 금지 끄기.
5. [ ] 휴대폰 확인은 HTTPS 주소가 필요: 배포 후(또는 cloudflared 같은 터널로) 확인.
    - 안드로이드: 크롬으로 열기 → 알림 허용 → 테스트 API → 알림 확인.
    - 아이폰: 사파리로 열기 → 공유 → **홈 화면에 추가** → 홈 화면 아이콘으로 열어 알림 켜기 → 테스트 API → **잠금화면 알림** 확인.
6. [ ] 프론트 담당에게 7절 전달(서비스워커·버튼·로그아웃 때 해제·manifest).

## 11. 작업 순서 (승인 후, 단계마다 `docs/PROGRESS.md` 갱신)

1. `WebPushCrypto` + VAPID 서명 (+RFC 테스트 벡터): 가장 위험한 부분을 먼저
2. V11 + `PushSubscription` + 구독 API 4개 + 에러 코드 (+테스트)
3. `NotificationMessage` + `WebPushSender` / `WebPushClient` (+테스트, Mock)
4. `NotificationService` + 스케줄러 + 설정 (+테스트, H2 로 중복 방지 확인)
5. 로컬 확인: 키 없이 503 → 키 설정 후 PC 크롬 임시 페이지로 수신 확인
6. 명세 반영(12절), FRONTEND_GUIDE 웹푸시 절, 커밋 제안
7. (배포 후) 안드로이드·아이폰 실기기 확인 → 명세 9장 13 갱신

## 12. 승인되면 바꿀 명세 목록

- 2.1 패키지 트리: `notification    일정 알림(웹푸시) (T11)`
- 2.5: `KAKAO_*` 4개 삭제, `PUSH_NOT_CONFIGURED` 503 추가
- 3.3: 알람 "저장만, 실제 발송은 2차(카톡)" → "T11 웹푸시로 발송(4.10)"
- 4.10: 카카오 API → 이 계획 4절 API 로 교체, 계획 경로 `docs/plans/T11-web-push.md`
- 7장: V11 = `push_subscriptions` + `schedules.alarm_sent_for`·`idx_schedules_date`
- 8장 T11 행: 일정 알림(웹푸시) (4.10) — 구독 API, 1분 스케줄러, 중복 발송 없음, RFC 8291 테스트 벡터, 실기기(안드로이드·아이폰) 수신
- 9장: 9번 "T11 웹푸시로 앞당김", 12번(할 일 09:00) 그대로 확인 대기, 13번 → "'나에게 보내기'는 푸시가 안 와서 웹푸시로 변경(2026-10-09). 아이폰은 홈 화면 추가 필요", 14·15번 그대로
- FRONTEND_GUIDE: 진행표 T11 행, 웹푸시 연동 절(7절 내용), 에러 표 `PUSH_NOT_CONFIGURED`
- 공용 파일 변경 예정: `ErrorCode`(1개), `application.yml`(app.webpush·notification). 새 의존성: 안 A 면 **없음**.

## 13. 승인받을 것

1. 카카오 → 웹푸시 전환과 이 API·DB 설계(V11: 테이블 1개 + `schedules` 칸 1개·인덱스 1개)
2. 의존성: **안 A(새 의존성 없음, 추천)** / B / C
3. 테스트 발송 API(`POST /api/notifications/push/test`) 포함 여부: 추천은 포함
4. (기존 미결) 시간 없는 할 일의 알림 기준 09:00: 명세 9장 12
