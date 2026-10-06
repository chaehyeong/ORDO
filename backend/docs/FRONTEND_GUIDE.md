# 프론트 연동 가이드 · 백엔드 진행 현황

> 프론트 개발자와 백엔드 B 검수용. 기준 문서는 `docs/BACKEND_SPEC.md` 이고, 이 문서는 **지금 바로 쓸 수 있는 API** 와 **프론트에 부탁할 수정**만 모았다.
> 작성: 2026-09-24 (백엔드 A)
> T3 연동 설명 갱신: 2026-10-06 (`feat/catalog`, `feat/auth` 기반)
> T4 일정 API 추가: 2026-09-28 (백엔드 A)
> T5 시간표 API 추가: 2026-10-06 (백엔드 A)

---

## 1. 진행 현황

| 작업 | 상태 | 브랜치 / PR |
|---|---|---|
| T0 공통 기반 (응답 형식·에러·JWT 보안·CORS·Swagger·Flyway) | ✅ 완료 | `feat/global` · PR #1 |
| T1 회원가입·로그인·토큰 재발급·로그아웃 | ✅ 완료 | `feat/auth` (feat/global 위에서 작업) |
| T2 내 정보 조회·수정·알림 설정 | ✅ 완료 | `feat/auth` |
| T3 전공 목록 등 catalog 조회 API | ✅ 구현·전체 테스트 완료 (B) | `feat/catalog` (실제 MySQL 부팅·시드 조회 포함) |
| T4 일정·할 일 | ✅ 완료 (A) | `feat/schedule` · PR #3 |
| T5 시간표 | ✅ 완료 (A) | `feat/timetable` (feat/schedule 위에서 작업) |
| T8 홈 · T9 마이페이지 요약 | ⏳ 예정 (A) | |
| T6·T7 이수내역 · 이수현황 계산 | ⏳ 예정 (B) | |

**기존 T1·T2 검증 기록 (2026-09-24)**: 테스트 21개 통과. 서버를 띄워 가입 → 로그인 → 내 정보 → 수정 → refresh → 로그아웃 흐름과 실패 케이스(중복 이메일, 잘못된 전공, 틀린 비밀번호, 폐기된 토큰 등) 19개를 직접 호출해 확인.

**T3 검증 (2026-10-06)**: 조회·실제 시드 데이터 테스트 16개, HTTP 응답·입력 검증 17개, 기존 인증·회원 테스트 19개, 실제 MySQL의 애플리케이션 부팅·초기 데이터 조회 2개로 전체 **54개 통과**. 실행 서버에서도 컴퓨터공학과 교과목 80개와 CSE204 자료구조 조회를 확인했다. 재현 명령과 범위는 7장 참고.

**T4 검증 (2026-09-28)**: 일정 단위 테스트 11개 통과. 서버를 띄워 일정 API(생성·정렬·기간 제한·시간 검증·남의 일정 404·삭제 등) 31개를 직접 호출해 확인. T3 가 들어간 master 를 합친 뒤 전체 테스트 **65개 통과**(T3 까지 54개 + 일정 11개).

**T5 검증 (2026-10-06)**: 학기 판별·시간표 단위 테스트 12개 추가, 전체 **77개 통과**. 서버를 띄워 시간표 API(겹침 409·딱 붙은 시간 허용·현재 학기 기본값·입력 검증·남의 칸 404·삭제 등) 27개를 직접 호출해 확인.

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
| MAJOR_NOT_FOUND | 404 | 없는 전공 id. 가입·프로필 변경에서는 해당 학번에 없는 전공이나 융합전공 선택도 포함 |
| REQUIREMENT_NOT_FOUND | 404 | 해당 전공·입학년도 졸업요건이 없거나, 교양 기준과 2026 대체 자료가 모두 없음 |
| SCHEDULE_NOT_FOUND | 404 | 없는 일정, 또는 남의 일정 |
| INVALID_TIME_RANGE | 400 | 종료 시간이 시작보다 빠름·같음(일정·시간표), 시작 없이 종료만 있음, 조회 기간 역전·62일 초과 |
| TIMETABLE_ENTRY_NOT_FOUND | 404 | 없는 시간표 칸, 또는 남의 칸 |
| TIMETABLE_OVERLAP | 409 | 같은 학기·요일에 시간이 겹치는 칸이 이미 있음 |
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

### 학사 기준정보 `catalog` (로그인 불필요)

아래 API는 토큰 없이 호출할 수 있다. 회원가입 화면에서는 Authorization 헤더를 생략한다.

| GET 경로 | 용도 |
|---|---|
| /api/catalog/colleges | 단과대 목록 |
| /api/catalog/majors?admissionYear=2026 | 입학년도별 전공 목록 |
| /api/catalog/majors?admissionYear=2026&collegeId=3 | 단과대까지 선택한 경우 목록 필터 (id는 colleges 응답 사용) |
| /api/catalog/courses?majorId=54&q=CSE204&page=0&size=20 | 전공별 과목명·학수번호 검색 |
| /api/catalog/courses?majorId=54&classification=MAJOR_REQUIRED | 전공필수 과목 조회 |
| /api/catalog/requirements?majorId=54&admissionYear=2026 | 졸업요건 원본 조회 (진행률·졸업 가능 판정 아님) |
| /api/catalog/general-education?admissionYear=2025 | 교양 기본구조·필수과목, 기준 대체 여부 확인 |

응답에서 사용할 구조:

```text
colleges.data: [{ id, name }]
majors.data: [{ id, displayName, collegeId, collegeName }]
courses.data: { content: [{ id, collegeName, unitName, courseCode, name,
                           classification, credits, variableCredits,
                           targetGrade, openSemester, track }],
                page, size, totalElements, totalPages }
general-education.data: { admissionYear, basisYear, approximate,
                          requiredCredits, distributionCredits, distributionMinAreas,
                          freeCredits, totalCredits,
                          requiredCourses: [{ id, groupName, courseName, credits,
                                              recommendedGrade, note }] }
```

위에서 `majors.data` 등은 각 요청 응답의 `data` 를 뜻한다. 모든 응답은 `{ success, data, error }` 공통 형식이다. 졸업요건 원본의 전체 필드는 명세서 4.3 참고.

**연동 순서**

1. 학번 앞 4자리가 정해지면 `majors?admissionYear=...` 호출 → `displayName` 을 보여주고 선택한 `id` 를 가입 요청의 `majorId` 로 전송한다.
2. 입학년도가 바뀌면 이전 전공 선택을 지우고 목록을 다시 조회한다. 이전 요청이 늦게 도착해 최신 목록을 덮지 않도록 요청 취소 또는 응답 순서를 확인한다.
3. 과목 검색은 선택 전공의 `majorId` 와 검색어를 보낸다. 입력 중 200~300ms 지연 후 요청하는 방식을 권장하며 검색어는 `URLSearchParams` 로 인코딩한다.
4. 전공·검색어·이수구분이 바뀌면 `page=0` 으로 초기화한다. `page` 는 0부터 시작하고 `size` 는 1~100, 기본 20이다. 화면 목록은 `data.content`, 전체 개수는 `data.totalElements` 를 사용한다.
5. 로딩·결과 없음·요청 실패를 구분한다. 자료에 없는 입학년도의 전공이나 교과목이 연결되지 않은 전공은 정상적으로 빈 목록이 올 수 있다. 오류 메시지는 `error.message` 를 표시한다.

**표시할 주의사항**

- 과목은 2026 교육과정 기준이다. ‘이번 학기 개설 확정 과목’이라고 표시하지 않는다. `openSemester` 도 실시간 개설 정보가 아니다.
- 과목명과 학수번호는 부분 검색이며 영문 대소문자를 구분하지 않는다. `q` 생략·공백은 전체 조회이고 최대 100자이다. `classification` 은 명세의 대문자 enum이며 빈 값은 전체 조회이다.
- `courseCode`·`targetGrade`·`openSemester`·`track` 이 null이면 ‘정보 없음’ 등으로 처리한다. `variableCredits=true` 인 과목은 실제 취득 학점을 입력할 때 확인이 필요하다.
- `approximate=true` 이면 ‘해당 입학년도 교양 기준이 없어 2026년 자료로 안내합니다’ 문구를 표시한다.
- 졸업요건 원본의 null은 ‘자료 없음/해당 없음’이다. 이 API만으로 이수현황·졸업 가능 여부를 계산하지 않는다(T7에서 제공 예정).
- 검색 결과를 사용자의 이수내역에 저장하는 기능은 T6 범위로, 이번 T3에는 포함되지 않는다.

### 일정·할 일 `schedule` (전부 🔒)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/schedules?from=2026-10-01&to=2026-10-31&category= | 기간 조회 (캘린더 월·날짜 상세). data = 아래 객체의 배열 |
| POST | /api/schedules | 생성 → **201** |
| GET | /api/schedules/{id} | 한 건 조회 |
| PATCH | /api/schedules/{id} | 수정 — **보낸 필드만** 바뀜 |
| DELETE | /api/schedules/{id} | 삭제 → **204** (본문 없음) |
| PATCH | /api/schedules/{id}/done | 완료 체크 `{ "done": true }` — 뒤집기가 아니라 보낸 값으로 설정 |

```json
// POST /api/schedules — 일정 추가 폼
{ "title": "팀플 모임",          // 필수, 100자 이하
  "category": "PERSONAL",       // 필수: LECTURE(수업) / ASSIGNMENT(과제) / PERSONAL(개인일정)
  "date": "2026-10-18",         // 필수
  "startTime": "14:00",         // 선택. 비우면 '할 일'
  "endTime": "15:30",           // 선택. 넣으면 startTime 필수, startTime 보다 늦어야 함
  "location": "예405",          // 선택, 100자 이하
  "memo": null,                 // 선택, 1000자 이하
  "alarmMinutesBefore": 30 }    // 선택: null(없음) / 10 / 30 / 60 / 1440(하루 전). 그 외 값은 INVALID_INPUT

// 응답 data (생성·조회·수정·완료 공통)
{ "id": 42, "title": "팀플 모임", "category": "PERSONAL", "date": "2026-10-18",
  "startTime": "14:00", "endTime": "15:30", "location": "예405", "memo": null,
  "done": false, "alarmMinutesBefore": 30, "type": "EVENT" }
```

- `type`: `startTime` 이 있으면 `EVENT`(일정 → 캘린더·홈 타임라인), 없으면 `TODO`(할 일 → 홈 '오늘 할 일' 체크박스).
- 목록 정렬: 날짜 → 시작시간 순, 시간 없는 할 일은 그날 맨 뒤. `category` 를 생략하거나 비우면 전체.
- 조회 기간: `from`·`to` 필수, 양 끝 포함 **최대 62일**(예: 10-01~12-01). 달력 앞뒤 주까지 한 번에 받아도 된다.
- **PATCH 로 값을 지울 수는 없다**(null = 그대로). 알람 끄기, 시간 지우기(일정 → 할 일), 장소·메모 지우기는 삭제 후 다시 만든다.
- 알람은 저장만 된다. 실제 알림 발송은 2차(카톡).

### 시간표 `timetable` (전부 🔒)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/timetables?year=2026&term=SECOND | 학기 시간표. 생략한 값은 현재 학기 값으로 채움 |
| POST | /api/timetables/entries | 수업 칸 추가 → **201**, 겹치면 **409** |
| PATCH | /api/timetables/entries/{id} | 수정 — **보낸 필드만** 바뀜, 겹치면 409 |
| DELETE | /api/timetables/entries/{id} | 삭제 → **204** (본문 없음) |

```json
// POST /api/timetables/entries
{ "year": 2026,                   // 필수, 2000~2100
  "term": "SECOND",               // 필수: FIRST / SECOND / SUMMER(여름계절) / WINTER(겨울계절)
  "courseName": "타이포그래피 I",  // 필수, 100자 이하
  "courseCode": null,             // 선택, 20자 이하
  "dayOfWeek": 2,                 // 필수: 1=월 … 7=일
  "startTime": "09:00",           // 필수
  "endTime": "10:30",             // 필수, startTime 보다 늦어야 함
  "location": "예405",            // 선택, 100자 이하
  "professor": "김교수",           // 선택, 50자 이하
  "color": "#3BB39A" }            // 선택, #RRGGBB

// GET 응답 data
{ "year": 2026, "term": "SECOND", "label": "2026년 2학기",
  "entries": [ { "id": 3, "courseName": "타이포그래피 I", "courseCode": null, "dayOfWeek": 2,
                 "startTime": "09:00", "endTime": "10:30", "location": "예405",
                 "professor": "김교수", "color": "#3BB39A" } ] }
// POST·PATCH 응답 data 는 entries 원소 하나와 같은 모양
```

- 현재 학기: 3~8월 → 그해 1학기, 9~12월 → 그해 2학기, 1~2월 → 전년도 2학기. 학기 선택의 기본값은 파라미터 없이 `GET /api/timetables` 한 응답의 `year`·`term` 을 쓰면 된다.
- `entries` 는 요일 → 시작시간 순. 격자(월~금 09:00~19:00) 밖의 칸(토·일, 이른 시간)도 올 수 있다.
- 겹침: 같은 학기·요일에서 시간이 겹치면 `TIMETABLE_OVERLAP`. 09:00~10:30 다음 10:30~12:00 처럼 딱 붙는 건 괜찮다.
- PATCH 로 값을 지울 수는 없다(null = 그대로). 장소·교수·색 지우기는 삭제 후 다시 만든다.

---

## 5. 프론트에 부탁할 수정 (SignUp.js / SignIn.js)

1. **학과 목록이 하드코딩(value 1·2·3)** 이라 실제 전공 id 와 다르다. 그대로 보내면 엉뚱한 전공으로 가입되거나 `MAJOR_NOT_FOUND`.
   - `GET /api/catalog/majors?admissionYear=2026` 으로 실제 목록을 받아 선택하도록 교체한다 (T3 구현).
   - 수동 호출 테스트용 id (2026학번 기준): `35` 시각디자인학과, `54` 컴퓨터공학부 컴퓨터공학과. 화면 코드에는 이 id를 하드코딩하지 않는다.
   - 기존 안내 정정: 시드에는 `55` '컴퓨터공학부 인공지능학과'가 있고 2022~2026학번의 주전공으로 선택 가능하다. 프론트의 줄임말·임의 id 대신 API의 표시명과 id를 사용한다. '인공지능반도체'·'우주인공지능' 융합전공은 주전공 가입 불가.
2. **학번을 학과보다 먼저** 입력받기. 전공 목록이 학번 앞 4자리(입학년도)에 따라 달라진다.
3. **학번 칸** `type="number"` → `type="text" inputMode="numeric" maxLength={10}` 권장. 서버는 숫자 10자리 문자열을 기대한다.
4. 비밀번호 규칙 안내 문구 추가: 8~64자, 영문과 숫자 포함.
5. (참고) '비밀번호'와 '비밀번호 확인' 칸이 같은 `controlId="formBasicPassword"` 라 라벨 연결이 겹친다.
6. SignIn.js: '로그인 상태 유지' 는 3장 4번처럼 구현. '비밀번호 찾기' API 는 없다. '경희대학교 통합 로그인' 은 링크일 뿐 백엔드 연동 대상 아님.

---

## 6. 백엔드 B 참고

- 로컬 실행 전 `src/main/resources/application-local.yml` 을 직접 만들어 DB 비밀번호·JWT 키를 넣는다 (README 3장, git 에 안 올라감).
- `catalog` 의 `College`·`Major` 엔티티와 `MajorRepository` 는 T1에서 먼저 만들었으며, T3는 이를 이어서 구현했다. 기존 가입 검증용 `countGraduationRequirements` 네이티브 쿼리는 그대로 유지했다.
- 기존 T0~T2 공용 파일 변경: `build.gradle`(springdoc·jjwt), `application.yml`, `global/**` 전체, `ErrorCode`(2.5 전체), `SecurityConfig`(PasswordEncoder, `/error` 허용). 이번 T3 변경은 아래 7장 참고.
- T4 일정은 공용 파일 변경 없음.
- T5: 공용 파일 `global/common/Term.java`(학기 enum)·`AcademicTerm.java`(현재 학기 판별, `AcademicTerm.now()`)를 새로 추가했다. T6 이수내역의 `term` 도 이 `Term` 을 써 주세요.
- 명세와 다르게/새로 정한 것은 `BACKEND_SPEC.md` 4장·4.1·4.2·8장에 반영해 두었다.

---

## 7. T3 검증과 변경 범위

- `feat/catalog` 는 인증 구현이 있는 `feat/auth` 를 기반으로 한다. 별도 일정 개발 브랜치의 코드는 포함하지 않는다.
- T3 공용 파일 변경: `build.gradle` 에 테스트용 H2 의존성만 추가. 운영 DB는 기존 MySQL 그대로이며 `application.yml`, `global/**`, V1·V2 마이그레이션, 프론트 코드는 변경하지 않았다.
- 기존 `MajorRepository.findSelectable` 의 가입 검증 경로는 유지하고, 전공 목록용 조회를 추가했다.
- 새 테스트는 운영 DB와 분리된 H2 메모리 DB에 테스트 스키마를 만들고, 정해진 소량 데이터 및 기존 V2 시드 전체를 읽어 검증한다. 테스트가 끝나면 메모리 DB는 사라진다.
- MySQL 없이 독립 테스트 52개만 실행하려면 Java 17에서, backend 폴더를 기준으로 실행:

```powershell
.\gradlew.bat test --tests "com.ordo.catalog.*" --tests "com.ordo.auth.service.*" --tests "com.ordo.user.service.*" --tests "com.ordo.global.security.*"
```

- 위 명령은 MySQL이 필요한 기존 `OrdoApplicationTests` 를 제외한다. 로컬 MySQL 비밀번호를 README대로 설정한 뒤 `.\gradlew.bat test` 를 실행하면 전체 54개 테스트를 검증한다.
- 2026-10-06 전체 테스트에서 실제 `jdbc:mysql://localhost:3306/ordo` 연결·Flyway 검증과 단과대 10개, 전공 76개, 교과목 3,036개의 초기 데이터 조회를 확인했다. 프론트 화면 연동 검증은 별도다.
- Swagger에서 4장의 GET 예시를 호출할 수 있다. 기본 주소는 `http://localhost:8080/swagger-ui/index.html` 이며 서버가 켜져 있어야 한다.
