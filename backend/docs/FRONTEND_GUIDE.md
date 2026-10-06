# 프론트 연동 가이드 · 백엔드 진행 현황

> 프론트 개발자와 백엔드 B 검수용. 기준 문서는 `docs/BACKEND_SPEC.md` 이고, 이 문서는 **지금 바로 쓸 수 있는 API** 와 **프론트에 부탁할 수정**만 모았다.
> 작성: 2026-09-24 (백엔드 A)

---

## 1. 진행 현황

| 작업 | 상태 | 브랜치 / PR |
|---|---|---|
| T0 공통 기반 (응답 형식·에러·JWT 보안·CORS·Swagger·Flyway) | ✅ 완료 | `feat/global` · PR #1 |
| T1 회원가입·로그인·토큰 재발급·로그아웃 | ✅ 완료 | `feat/auth` (feat/global 위에서 작업) |
| T2 내 정보 조회·수정·알림 설정 | ✅ 완료 | `feat/auth` |
| T3 전공 목록 등 catalog 조회 API | ⏳ 예정 (B) | |
| T4 일정 · T5 시간표 · T8 홈 · T9 마이페이지 요약 | ⏳ 예정 (A) | |
| T6·T7 이수내역 · 이수현황 계산 | ⏳ 예정 (B) | |

**확인한 것**: 단위 테스트 21개 통과. 서버를 띄워 가입 → 로그인 → 내 정보 → 수정 → refresh → 로그아웃 흐름과 실패 케이스(중복 이메일, 잘못된 전공, 틀린 비밀번호, 폐기된 토큰 등) 19개를 직접 호출해 확인.

---

## 2. 공통 규칙

- 주소: `http://localhost:8080` , 모든 API 는 `/api` 로 시작, JSON.
- Swagger(API 문서·직접 호출): `http://localhost:8080/swagger-ui/index.html` (백엔드 서버가 켜져 있어야 함)
- CORS 허용: `http://localhost:3000`, `http://localhost:5173`
- 🔒 표시 API 는 헤더 `Authorization: Bearer {accessToken}` 필요.
- **응답은 항상 같은 모양**

```json
// 성공
{ "success": true, "data": { ... }, "error": null }
// 실패
{ "success": false, "data": null, "error": { "code": "EMAIL_DUPLICATED", "message": "이미 가입된 이메일입니다." } }
```

- `error.message` 는 한국어라 화면에 그대로 띄워도 된다. 분기는 `error.code` 로 한다.
- 날짜 `"2026-10-16"`, 시간 `"09:00"`.

### 지금 나올 수 있는 에러 코드

| code | HTTP | 언제 |
|---|---|---|
| INVALID_INPUT | 400 | 입력 형식 오류. message 에 `필드: 사유` 가 쉼표로 이어져 온다 |
| UNAUTHORIZED | 401 | 토큰 없음·잘못됨 → 로그인 화면으로 |
| EXPIRED_TOKEN | 401 | access 토큰 만료 → **refresh 호출 후 원래 요청 재시도** |
| LOGIN_FAILED | 401 | 이메일 또는 비밀번호 틀림 |
| INVALID_REFRESH_TOKEN | 401 | refresh 토큰 없음·만료·이미 사용됨 → 로그인 화면으로 |
| EMAIL_DUPLICATED | 409 | 이미 가입된 이메일 |
| MAJOR_NOT_FOUND | 404 | 그 학번(입학년도)에 없는 전공, 또는 융합전공 선택 |
| NOT_FOUND | 404 | 없는 경로·리소스 |
| INTERNAL_ERROR | 500 | 서버 오류 (백엔드에 알려주세요) |

---

## 3. 로그인 · 토큰 흐름

1. 가입(`signup`) 또는 로그인(`login`) 하면 `accessToken`(1시간), `refreshToken`(14일) 을 받는다.
   - 가입도 토큰을 바로 주므로, 가입 후 로그인 화면을 거치지 않고 홈으로 보내도 된다(선택).
2. 🔒 API 호출 때 `Authorization: Bearer {accessToken}` 헤더를 붙인다.
3. 401 + `EXPIRED_TOKEN` 이 오면 `POST /api/auth/refresh` 로 새 토큰 한 쌍을 받고, 원래 요청을 다시 보낸다.
   - **refresh 할 때마다 refreshToken 도 새로 바뀐다.** 예전 refreshToken 은 바로 폐기되니 반드시 새 값으로 교체 저장.
4. **'로그인 상태 유지'**: 체크하면 refreshToken 을 `localStorage` 에, 안 하면 `sessionStorage`(탭 닫으면 사라짐)에 저장. 서버 동작은 같다.
5. 로그아웃: `POST /api/auth/logout` (🔒, 본문에 refreshToken) 후 저장한 토큰을 지운다.

```js
// fetch 예시 — 만료 시 한 번 refresh 후 재시도
async function api(path, options = {}) {
  const send = () => fetch(`http://localhost:8080${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json',
               Authorization: `Bearer ${getAccessToken()}`, ...options.headers },
  });
  let res = await send();
  let body = await res.json();
  if (res.status === 401 && body.error?.code === 'EXPIRED_TOKEN') {
    const r = await fetch('http://localhost:8080/api/auth/refresh', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: getRefreshToken() }),
    }).then((x) => x.json());
    if (!r.success) { goToLogin(); return r; }
    saveTokens(r.data.accessToken, r.data.refreshToken);   // refreshToken 도 교체!
    res = await send();
    body = await res.json();
  }
  return body;   // { success, data, error }
}
```

---

## 4. API

### 인증 `auth`

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | /api/auth/signup | 회원가입 → **201**, 토큰 발급 |
| POST | /api/auth/login | 로그인 → 토큰 발급 |
| POST | /api/auth/refresh | 토큰 재발급 (refreshToken 도 새로 바뀜) |
| POST | /api/auth/logout | 🔒 로그아웃 → data = null |

```json
// POST /api/auth/signup — SignUp.js 입력칸 기준
{
  "name": "이하은",               // 필수, 30자 이하
  "phone": "010-1234-5678",      // 선택, 20자 이하
  "email": "haeun@khu.ac.kr",    // 필수, 이메일 형식
  "password": "ordo1234!",       // 필수, 8~64자, 영문+숫자 포함 ('비밀번호 확인'은 프론트에서 비교)
  "majorId": 35,                 // 필수 (아래 5-1 참고)
  "studentNumber": "2026105632"  // 필수, 숫자 10자리 "문자열". 앞 4자리가 입학년도
}

// POST /api/auth/login
{ "email": "haeun@khu.ac.kr", "password": "ordo1234!" }

// POST /api/auth/refresh , /api/auth/logout
{ "refreshToken": "eyJ..." }

// signup / login / refresh 응답 data
{ "accessToken": "eyJ...", "refreshToken": "eyJ...", "accessTokenExpiresIn": 3600,
  "user": { "id": 1, "name": "이하은", "nickname": null } }
```

### 내 정보 `user` (전부 🔒)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/users/me | 프로필 |
| PATCH | /api/users/me | 프로필 수정 — **보낸 필드만** 바뀜 |
| PATCH | /api/users/me/settings | 알림 on/off `{ "notificationEnabled": false }` |

```json
// GET /api/users/me 응답 data (PATCH 두 개도 수정 후 이 모양 그대로 응답)
{ "id": 1, "email": "haeun@khu.ac.kr", "name": "이하은", "nickname": "하은",
  "studentNumber": "2026105632", "phone": "010-1234-5678", "profileImageUrl": null,
  "admissionYear": 2026, "currentSemester": 3, "grade": 2,
  "major": { "id": 35, "displayName": "시각디자인학과", "collegeName": "예술·디자인대학" },
  "notificationEnabled": true }

// PATCH /api/users/me — 바꿀 것만 보낸다
{ "nickname": "하은", "currentSemester": 3 }
// 보낼 수 있는 필드: name, nickname, studentNumber, phone, admissionYear, majorId, currentSemester, profileImageUrl
```

- `grade`(학년) = (currentSemester + 1) / 2, 학기를 모르면 null.
- 홈 인사말은 `nickname` 이 없으면 `name` 을 쓴다.

---

## 5. 프론트에 부탁할 수정 (SignUp.js / SignIn.js)

1. **학과 목록이 하드코딩(value 1·2·3)** 이라 실제 전공 id 와 다르다. 그대로 보내면 엉뚱한 전공으로 가입되거나 `MAJOR_NOT_FOUND`.
   - 원래는 `GET /api/catalog/majors?admissionYear=2026` 으로 받아야 하는데, 이 API 는 B 가 T3 에서 만들 예정.
   - 그 전까지 테스트용 id (2026학번 기준): `35` 시각디자인학과, `54` 컴퓨터공학부 컴퓨터공학과.
   - 참고: 목록의 '인공지능학과'는 국제캠 시드 데이터에 주전공으로 없다('인공지능반도체'·'우주인공지능'은 융합전공이라 주전공 가입 불가).
2. **학번을 학과보다 먼저** 입력받기. 전공 목록이 학번 앞 4자리(입학년도)에 따라 달라진다.
3. **학번 칸** `type="number"` → `type="text" inputMode="numeric" maxLength={10}` 권장. 서버는 숫자 10자리 문자열을 기대한다.
4. 비밀번호 규칙 안내 문구 추가: 8~64자, 영문과 숫자 포함.
5. (참고) '비밀번호'와 '비밀번호 확인' 칸이 같은 `controlId="formBasicPassword"` 라 라벨 연결이 겹친다.
6. SignIn.js: '로그인 상태 유지' 는 3장 4번처럼 구현. '비밀번호 찾기' API 는 없다. '경희대학교 통합 로그인' 은 링크일 뿐 백엔드 연동 대상 아님.

---

## 6. 백엔드 B 참고

- 로컬 실행 전 `src/main/resources/application-local.yml` 을 직접 만들어 DB 비밀번호·JWT 키를 넣는다 (README 3장, git 에 안 올라감).
- `catalog` 의 `College`·`Major` 엔티티와 `MajorRepository` 는 T1 에서 먼저 만들었다 → T3 에서 이어서 작업. `countGraduationRequirements` 네이티브 쿼리는 `GraduationRequirement` 엔티티를 만들면 그쪽으로 교체(코드에 `ponytail:` 주석).
- 공용 파일 변경: `build.gradle`(springdoc·jjwt), `application.yml`, `global/**` 전체, `ErrorCode`(2.5 전체), `SecurityConfig`(PasswordEncoder, `/error` 허용).
- 명세와 다르게/새로 정한 것은 `BACKEND_SPEC.md` 4장·4.1·4.2·8장에 반영해 두었다.
