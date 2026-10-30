# ORDO 백엔드 개발 명세서 (AI 작업용)

> **이 문서가 백엔드 구현의 유일한 기준이다.** AI에게 작업을 시킬 때 이 문서와 `AGENTS.md` 를 먼저 읽히고 시작한다.
> `docs/ORDO_ARCHITECTURE.md` 는 초기 개요(구버전)라서, 내용이 다르면 **이 문서가 우선**이다.
> 근거 자료: 회의록.txt(1·2차), 디자인 시안 7장, `경희대_국제캠_2026_졸업관리데이터.xlsx`, `경희대_교육과정기본구조/2020~2026학번`.
> 최종 갱신: 2026-10-30 (T15 시간표 온라인 강의 4.5·V12), 2026-10-21 (T11 웹푸시 알림 4.10·V11, T13 평점·T14 홈 e캠퍼스 과제, 2026-10-08: T12 파일 가져오기 계획 반영, T3 catalog 조회 계약·검증 결과, T4 schedule·T5 timetable·T6·T7 academic·T8 home·T9 summary 세부 규칙, e캠퍼스 연동 추가, AI 추천 보류)

---

## 0. 한눈에 보기

| 항목 | 내용 |
|---|---|
| 서비스 | **오르도(ORDO, 라틴어 '질서')** — 경희대 국제캠퍼스 학생용 일정 + 학사(졸업요건) 통합 웹서비스 |
| 대상 | 국제캠퍼스 전체 학과 (학번 2020~2026) |
| 백엔드 | Java 17 · Spring Boot 3.3.5 · Spring Data JPA · Spring Security(JWT) · MySQL 8 · Flyway · Gradle |
| 프론트 | 별도 웹 프론트엔드 → 백엔드는 **REST API(JSON)만** 제공 |
| 팀 | 백엔드 2명 (각자 AI로 코딩) — 분업은 8장 |
| MVP 범위 | ① 회원/로그인 ② 홈 ③ 캘린더·일정·할 일(+ e캠퍼스 과제 마감 읽기) ④ 시간표 ⑤ 학사관리(이수현황) ⑥ 마이페이지 |
| 나중에 | 카카오 로그인, AI 추천, 졸업 시뮬레이션, 마이크로디그리, STT |

**이미 준비된 것 (새로 만들지 말 것)**

- `src/main/resources/db/migration/V1__init_schema.sql` — MVP 전체 테이블 (MySQL 8에서 검증 완료)
- `src/main/resources/db/migration/V2__seed_catalog.sql` — 학사 기준정보 시드 (단과대 10, 전공 76, 졸업요건 393행(학번×전공), 교양 필수과목 6, 교과목 3,036)
- `tools/seed/generate_seed.py` — 엑셀 → V2 SQL 생성기 (엑셀 수정 시 재실행)

→ **테이블은 이미 있다. AI는 테이블을 만들지 말고, V1 컬럼에 정확히 맞춰 JPA 엔티티만 작성한다.**

---

## 1. 화면별 요구사항 (디자인 + 회의록 2차 확정본)

회의록 2차에서 바뀐 것: **과제 페이지 삭제, 설정 페이지 삭제**(알림 on/off·로그아웃은 마이페이지로), **오늘일정 페이지 삭제 → 홈에 통합**, 우측 상단 아이콘 3개 삭제, 마이페이지 **시험·출석률 제거**, 학사관리 **전공필수 추가 + 마이크로디그리 화면**, 시간표는 **수동 등록**, 일정 추가에 **알람 선택 + 카테고리(수업/과제/개인일정)**.

| 화면 | 보여줄 것 | 사용 API |
|---|---|---|
| 랜딩 | 서비스 소개, 로그인/회원가입 버튼 | 없음 |
| 로그인·회원가입 | 가입: 이름·전화번호·이메일·비밀번호(+확인)·학과·학번 / 로그인: 이메일·비밀번호·'로그인 상태 유지' 체크 (프론트 `SignUp.js`, `SignIn.js`) | `POST /api/auth/*`, `GET /api/catalog/majors?admissionYear=` (학번 앞 4자리로 조회) |
| **홈** | "안녕하세요 (닉네임)님", 주간일정(요일·날짜·카테고리 점), 오늘일정 타임라인(시간·제목·장소·카테고리), ~~AI 오르도 추천 카드~~(보류, 최종 디자인에도 없음), 졸업 진행률(78%, 102/130, 남은 학점), 오늘 할 일 | `GET /api/home` |
| 캘린더 | 연/월 선택, 오늘 버튼, 월 달력, +일정추가, 날짜 클릭 → 그날 일정 상세·추가·삭제, e캠퍼스 과제 마감 표시 | `GET/POST/PATCH/DELETE /api/schedules`, `GET /api/ecampus/events` |
| 일정(할 일) 추가 폼 | 제목, 카테고리, 날짜, 시작~종료 시간, 알람 | `POST /api/schedules` |
| 시간표 | 학기 선택(2026년 2학기), 시간표 편집, 월~금 09:00~19:00 격자, 격자 아래 온라인 강의 줄 | `/api/timetables` |
| 학사관리 | 학점 취득 현황(총 18/130, 13.8%, 영역 그룹별 도넛), 졸업까지 필요한 학점, 영역별 이수현황(필수교양·전공기초·전공필수·전공선택·기타·전체), 마이크로디그리(데이터 없음·보류) | `GET /api/academic/progress`, `/api/academic/completed-courses` |
| 마이페이지 | 프로필(이름·학과·입학년도/학년·학번·이메일·연락처·사진), 정보 수정, 이번 학기 수강과목 수·과제 수, 학점 현황, 알림 on/off, e캠퍼스 연결, 로그아웃 | `/api/users/me`, `/api/users/me/summary`, `/api/auth/logout` |

---

## 2. 기술 규칙 (모든 코드 공통)

### 2.1 패키지 구조 — 도메인별로 나눈다

```
com.ordo
├── OrdoApplication.java
├── global
│   ├── config      SecurityConfig, JpaConfig(@EnableJpaAuditing), WebConfig(CORS, ArgumentResolver), OpenApiConfig
│   ├── common      ApiResponse, BaseTimeEntity, HealthController, Term(학기 enum)·AcademicTerm(현재 학기 판별)
│   ├── error       ErrorCode, BusinessException, GlobalExceptionHandler
│   └── security    JwtProvider, JwtAuthenticationFilter, LoginUser(애노테이션), LoginUserArgumentResolver
├── auth            회원가입·로그인·토큰
├── user            내 정보·설정·마이페이지 요약
├── catalog         단과대·전공·졸업요건·교과목 (읽기 전용)
├── academic        이수내역 CRUD, 이수현황 계산
├── schedule        일정·할 일
├── timetable       시간표
├── home            홈 화면 집계
├── ecampus         e캠퍼스(Canvas) 캘린더 피드 연동
├── importer        성적·시간표 파일 가져오기 (T12)
└── notification    일정 알림 웹푸시 (T11)
```

각 도메인 안: `controller / service / repository / domain(엔티티·enum) / dto`.
`global` 패키지는 T0에서 구현 완료. 공용 파일이므로 수정하면 상대에게 알린다.

### 2.2 build.gradle 에 추가할 의존성

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'   // Swagger UI (프론트와 API 공유)
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly    'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly    'io.jsonwebtoken:jjwt-jackson:0.12.6'
```

### 2.3 application.yml (목표 상태)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ordo?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:1234}
  jpa:
    hibernate:
      ddl-auto: none          # 스키마는 Flyway가 관리. 엔티티로 테이블을 만들거나 고치지 않는다.
    open-in-view: false
    properties.hibernate.format_sql: true
  flyway:
    enabled: true
    out-of-order: true        # 두 사람이 번호대를 나눠 쓰므로 필요
  jackson:
    time-zone: Asia/Seoul
    serialization.write-dates-as-timestamps: false

app:
  jwt:
    secret: ${JWT_SECRET:local-dev-secret-key-must-be-at-least-32-bytes!!}
    access-token-validity-seconds: 3600       # 1시간
    refresh-token-validity-seconds: 1209600   # 14일
  cors:
    allowed-origins: ${CORS_ORIGINS:http://localhost:3000,http://localhost:5173}
```

> `ddl-auto: validate` 를 쓰지 않는 이유: Hibernate 6 + MySQL 조합에서 boolean·enum 컬럼 타입 검증이 VARCHAR/TINYINT 스키마와 자주 충돌해 초보 팀이 시간을 많이 잃는다. 대신 **엔티티 컬럼명은 V1과 정확히 일치**시키고, 부팅 후 Swagger로 CRUD를 한 번씩 실행해 확인한다.

### 2.4 코딩 규칙

- **엔티티**: `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, setter 금지 → 의미 있는 메서드(`update(...)`, `toggleDone()`)로 변경. PK `Long id` + `IDENTITY`. 사용자 데이터 엔티티는 `BaseTimeEntity`(created_at/updated_at, `@CreatedDate/@LastModifiedDate`) 상속. 연관관계는 `@ManyToOne(fetch = LAZY)` 만, 양방향 매핑 만들지 않기.
- **컬럼 이름**: 자바 camelCase → DB snake_case 자동 변환(`scheduleDate` → `schedule_date`). V1에 없는 필드 만들지 않기.
- **enum**: `@Enumerated(EnumType.STRING)` 로 VARCHAR에 이름 저장.
- **DTO**: Java `record` 사용, 이름은 `XxxRequest` / `XxxResponse`. 엔티티를 컨트롤러에서 바로 반환하지 않는다. 요청 DTO에 `@Valid` + Bean Validation.
- **날짜/시간 JSON 형식**: 날짜 `"2026-10-16"`, 시간 `"09:00"`, 일시 `"2026-10-16T09:00:00"`. 서버 기준 시간대 `Asia/Seoul`.
- **응답 포맷(전 API 공통)**:

```json
// 성공
{ "success": true, "data": { ... }, "error": null }
// 실패
{ "success": false, "data": null, "error": { "code": "SCHEDULE_NOT_FOUND", "message": "일정을 찾을 수 없습니다." } }
```

- **HTTP 상태코드**: 조회/수정 200, 생성 201, 삭제 204(본문 없음) 또는 200. 에러는 ErrorCode에 정의된 상태.
- **권한**: 로그인 사용자는 컨트롤러 파라미터 `@LoginUser Long userId` 로 받는다. **남의 데이터(일정·시간표·이수내역) 조회/수정 시 404** 로 응답(존재 여부 노출 안 함).
- **트랜잭션**: 서비스 클래스 `@Transactional(readOnly = true)`, 변경 메서드에만 `@Transactional`.
- **테스트**: 이수현황 계산(`academic`)은 반드시 단위 테스트 작성. 나머지는 핵심 서비스 위주.

### 2.5 에러 코드 (global/error/ErrorCode)

| 코드 | HTTP | 설명 |
|---|---|---|
| INVALID_INPUT | 400 | 요청 값 검증 실패 (message에 필드별 사유) |
| UNAUTHORIZED | 401 | 토큰 없음/잘못됨 |
| EXPIRED_TOKEN | 401 | 액세스 토큰 만료 → 프론트가 refresh 호출 |
| FORBIDDEN | 403 | 권한 없음 |
| NOT_FOUND | 404 | 일반 리소스 없음 |
| INTERNAL_ERROR | 500 | 서버 오류 |
| EMAIL_DUPLICATED | 409 | 이미 가입된 이메일 |
| LOGIN_FAILED | 401 | 이메일 또는 비밀번호 불일치 |
| INVALID_REFRESH_TOKEN | 401 | refresh 토큰 없음/만료 |
| PROFILE_INCOMPLETE | 400 | 입학년도·전공 미설정인데 학사 기능 호출 |
| MAJOR_NOT_FOUND | 404 | 전공 없음 |
| REQUIREMENT_NOT_FOUND | 404 | 해당 학번·전공의 졸업요건 데이터 없음 |
| SCHEDULE_NOT_FOUND | 404 | |
| INVALID_TIME_RANGE | 400 | 종료 시간이 시작보다 빠름, 조회 기간 역전/과대 |
| TIMETABLE_ENTRY_NOT_FOUND | 404 | |
| TIMETABLE_OVERLAP | 409 | 같은 학기·요일에 시간 겹침 |
| COMPLETED_COURSE_NOT_FOUND | 404 | |
| CLASSIFICATION_REQUIRED | 400 | 교과목 자동판별 불가인데 이수구분 미입력 |
| DISTRIBUTION_AREA_REQUIRED | 400 | 배분이수인데 영역(1~5) 미입력 |
| ECAMPUS_FEED_UNAVAILABLE | 502 | e캠퍼스 피드를 받지 못했고 예전에 받은 것도 없음 (주소 재등록 안내) |
| PUSH_NOT_CONFIGURED | 503 | 서버에 웹푸시(VAPID) 키가 설정되지 않음 (T11) |
| IMPORT_UNSUPPORTED_FORMAT | 400 | xlsx·csv·txt 가 아님(pdf 포함) → "엑셀(xlsx)로 저장해서 올려주세요" (T12) |
| IMPORT_TOO_LARGE | 400 | 업로드 5MB 초과 (T12) |
| IMPORT_UNRECOGNIZED | 400 | 수강신청확인서·전체 성적 보기가 아니거나 표를 못 찾음 (T12) |
| IMPORT_STUDENT_MISMATCH | 403 | 파일 학번이 내 학번과 다르거나 없음 (T12) |

---

## 3. 데이터 모델

테이블 정의는 `V1__init_schema.sql` 이 정답이다. 여기서는 의미와 규칙만 정리한다.

### 3.1 enum 정의 (자바)

```java
enum ScheduleCategory { LECTURE /*수업*/, ASSIGNMENT /*과제*/, PERSONAL /*개인일정*/ }
enum Term { FIRST /*1학기*/, SECOND /*2학기*/, SUMMER /*여름계절*/, WINTER /*겨울계절*/ }
enum Provider { LOCAL, KAKAO }
enum Role { USER, ADMIN }
enum Classification {             // 이수구분 (학교 코드)
  MAJOR_BASIC,        // 전공기초 11
  MAJOR_REQUIRED,     // 전공필수 04
  MAJOR_ELECTIVE,     // 전공선택 05
  GEN_REQUIRED,       // 교양 필수교과 14,16
  GEN_DISTRIBUTION,   // 교양 배분이수 15 (영역 1~5)
  GEN_FREE,           // 교양 자유이수 17
  GENERAL_ELECTIVE,   // 일반선택 08 (타전공·봉사 등, 총학점에만 산입)
  TEACHING,           // 교직 06
  TEACHING_MAJOR      // 교직전선 20
}
```

배분이수 영역: 1 생명·우주·인간 / 2 분석·추론·논리 / 3 상징·문화·소통 / 4 사회·공동체·평화 / 5 지능·정보·미래.

### 3.2 테이블 요약

| 테이블 | 성격 | 핵심 컬럼 / 규칙 |
|---|---|---|
| colleges | 기준정보(읽기) | name |
| majors | 기준정보(읽기) | display_name(화면 표시), course_unit(교과목 매칭 키, NULL 가능), convergence(융합전공=다전공 전용) |
| graduation_requirements | 기준정보(읽기) | (major_id, admission_year) 유일. 단일/다전공/부전공 학점, 2026만 SW·영어강의·논문·TOPIK·인증 텍스트. **NULL = 요건 없음(0)** |
| general_education_requirements | 기준정보(읽기) | 2026만 존재: 필수 17 / 배분 9(3영역 이상) / 자유 3 / 합계 29 |
| general_education_required_courses | 기준정보(읽기) | 교양 필수과목 6개(인간의가치탐색, 세계와시민, 빅뱅에서문명까지, 성찰과표현, 주제연구, 대학영어) |
| courses | 기준정보(읽기) | 2026 전공 교과목. **학수번호가 학과마다 중복**(예: 미분적분학 AMTH1009가 32개 학과) → 반드시 `(unit_name, course_code)` 로 조회 |
| users | 사용자 | email 유일, BCrypt password, name, nickname, student_number, phone, admission_year, major_id, current_semester, notification_enabled, todo_alarm_time(V11, 기본 09:00), ecampus_feed_token(V10, 4.8) |
| refresh_tokens | 인증 | token 유일, 만료일. 로그아웃·재발급 시 삭제 |
| schedules | 사용자 | schedule_date, start_time/end_time(NULL 가능), category, done, alarm_minutes_before, alarm_sent_for(V11, 알림 중복 방지·내부용) |
| push_subscriptions | 사용자 | 기기(브라우저)별 웹푸시 구독: endpoint(유일), p256dh, auth (V11, 4.10) |
| timetable_entries | 사용자 | academic_year + term, day_of_week(1=월…7=일), start_time~end_time, online(T15, true 면 요일·시간 NULL) |
| completed_courses | 사용자 | 내가 들은 과목: course_code(선택), course_name, credits, classification, distribution_area, grade, academic_year, term |

### 3.3 도메인 규칙

- **일정 vs 할 일**: 같은 `schedules` 테이블. `start_time` 이 있으면 "일정"(홈 타임라인·캘린더), 없으면 "할 일"(홈 '오늘 할 일' 체크박스). `end_time` 이 있으면 `start_time` 필수, `end_time > start_time`.
- **알람**: `alarm_minutes_before` 값은 `null`(없음), 10, 30, 60, 1440(하루 전) 중 하나. 요청에서 **`0` 은 알람 끄기**(null 로 저장, PATCH 의 null 은 '그대로'라서 따로 둠, 2026-10-23). **T11 웹푸시로 발송**(4.10). 시간 없는 할 일은 사용자의 `todo_alarm_time`(기본 09:00) 기준.
- **현재 학기 판별**(`AcademicTerm.of(LocalDate)`): 3~8월 → 그해 FIRST, 9~12월 → 그해 SECOND, 1~2월 → **전년도** SECOND. 계절학기는 사용자가 직접 선택할 때만.
- **학년**: `grade = (current_semester + 1) / 2` (current_semester null이면 null).
- **전공 목록(회원가입)**: 선택한 입학년도에 `graduation_requirements` 행이 있는 전공만, `convergence = false` 만 (융합전공은 주전공 불가).
- **교양 기준 근사**: `general_education_requirements` 는 2026만 있으므로, 다른 학번은 2026 값을 쓰고 응답에 `"approximate": true` 표시.

---

## 4. API 명세

공통: prefix `/api`, JSON, 인증 필요한 API는 `Authorization: Bearer {accessToken}`.
**인증 없이 허용**: `/api/auth/signup`, `/api/auth/login`, `/api/auth/refresh`, `/api/health`, `GET /api/catalog/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/error`(서버 오류가 401로 가려지지 않게).
토큰 없음·잘못됨 → 401 `UNAUTHORIZED`, 만료 → 401 `EXPIRED_TOKEN`. `GET /api/health` → `{ "success": true, "data": { "status": "ok" }, "error": null }`.

### 4.1 인증 `auth`

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | /api/auth/signup | 회원가입 → 201, 바로 토큰 발급 |
| POST | /api/auth/login | 로그인 → 토큰 발급 |
| POST | /api/auth/refresh | refresh로 토큰 재발급 (refresh도 새로 발급, 기존 삭제) |
| POST | /api/auth/logout | 🔒 해당 refresh 토큰 삭제 |

```json
// POST /api/auth/signup  요청
{                                   // 프론트 SignUp.js 입력칸 기준 (이름·전화번호·이메일·비밀번호·학과·학번)
  "name": "이하은",                  // 필수, 30자 이하
  "phone": "010-1234-5678",         // 선택, 20자 이하 (형식 검사 없음)
  "email": "haeun@khu.ac.kr",       // 필수
  "password": "ordo1234!",          // 필수, 8~64자, 영문+숫자 포함. '비밀번호 확인'은 프론트에서만 비교
  "majorId": 35,                    // 필수
  "studentNumber": "2026105632",    // 필수, 10자리 숫자 (문자열)
  "admissionYear": null,            // 선택. 비우면 학번 앞 4자리(2026)로 서버가 채움
  "nickname": null,                 // 선택
  "currentSemester": null           // 선택
}
// 응답 (signup / login / refresh 공통)
{ "success": true, "data": {
    "accessToken": "eyJ...", "refreshToken": "eyJ...", "accessTokenExpiresIn": 3600,
    "user": { "id": 1, "name": "이하은", "nickname": "하은" } }, "error": null }

// 가입 규칙: 학번 앞 4자리가 입학년도. 입학년도에 해당 전공의 졸업요건 행이 없으면 MAJOR_NOT_FOUND.
//            융합전공(convergence=true)은 주전공으로 가입 불가 → MAJOR_NOT_FOUND. 이메일 중복 → 409 EMAIL_DUPLICATED.
//            프론트 학과 목록은 지금 하드코딩(3개) → GET /api/catalog/majors 로 교체 필요 (프론트 담당과 협의)
// '로그인 상태 유지'를 체크하지 않으면 프론트가 refreshToken 을 저장하지 않으면 됨 (서버 동작은 동일)
// '경희대학교 통합 로그인' 버튼은 info21 링크일 뿐, 백엔드 연동 대상 아님
// POST /api/auth/login  요청
{ "email": "haeun@khu.ac.kr", "password": "ordo1234!" }
// POST /api/auth/refresh, /api/auth/logout  요청
{ "refreshToken": "eyJ..." }
// refresh: 쓴 refresh 토큰은 삭제되므로 다음에는 새로 받은 refreshToken 을 써야 한다 (재사용 → 401 INVALID_REFRESH_TOKEN)
// logout 응답: 200, data = null
```

### 4.2 내 정보 `user`

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/users/me | 🔒 프로필 |
| PATCH | /api/users/me | 🔒 프로필 수정(보낸 필드만 변경): name, nickname, studentNumber, phone, admissionYear, majorId, currentSemester, profileImageUrl |
| PATCH | /api/users/me/settings | 🔒 `{ "notificationEnabled": false, "todoAlarmTime": "21:00" }` 둘 다 선택 (T11) |
| GET | /api/users/me/summary | 🔒 마이페이지 요약 |

- PATCH 두 API의 응답 data 는 `GET /api/users/me` 와 같은 형태(수정 후 값).
- 보낸 필드만 변경: 생략하거나 null 이면 그대로 둔다(값을 비우는 기능은 없음).
- `majorId`·`admissionYear` 를 바꾸면 가입과 같은 규칙으로 확인 → 안 맞으면 `MAJOR_NOT_FOUND`.
- (T11) `todoAlarmTime`: 시간 없는 할 일의 알림 기준 시각 `HH:mm`(기본 09:00, 형식이 틀리면 `INVALID_INPUT`). 예: 21:00 이고 알람이 하루 전이면 전날 21:00 에 온다. `notificationEnabled=false` 면 웹푸시를 보내지 않는다(4.10).

```json
// GET /api/users/me
{ "id": 1, "email": "haeun@khu.ac.kr", "name": "이하은", "nickname": "하은",
  "studentNumber": "2026105632", "phone": "010-1234-5678", "profileImageUrl": null,
  "admissionYear": 2026, "currentSemester": 1, "grade": 1,
  "major": { "id": 35, "displayName": "시각디자인학과", "collegeName": "예술·디자인대학" },
  "notificationEnabled": true, "todoAlarmTime": "09:00",    // todoAlarmTime: 시간 없는 할 일 알림 기준(T11)
  "ecampusConnected": false }                               // ecampusConnected: 4.8 피드 등록 여부

// GET /api/users/me/summary
{ "term": { "year": 2026, "term": "SECOND", "label": "2026년 2학기" },
  "courseCount": 6,          // 이번 학기 시간표의 서로 다른 과목명 수
  "pendingAssignmentCount": 3, // 오늘 이후 done=false 인 ASSIGNMENT 일정 수
  "credits": { "earned": 18, "required": 130, "percent": 13.8 } }   // 학사 프로필 미완성이면 credits = null
// term 은 현재 학기(3.3). pendingAssignmentCount 는 오늘 포함, e캠퍼스 마감은 제출 여부를 몰라 세지 않는다
```

### 4.3 학사 기준정보 `catalog` (읽기 전용, 인증 불필요)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/catalog/colleges | 단과대 목록 |
| GET | /api/catalog/majors?admissionYear=2026&collegeId= | 해당 학번에 존재하는 전공 목록(융합전공 제외) |
| GET | /api/catalog/requirements?majorId=&admissionYear= | 졸업요건 원본 |
| GET | /api/catalog/courses?majorId=&q=&classification=&page=0&size=20 | 교과목 검색 (majorId → course_unit 으로 필터, q는 과목명/학수번호 부분일치) |
| GET | /api/catalog/general-education?admissionYear= | 교양 기본구조 + 필수과목 목록 (없으면 2026, approximate=true) |

**T3 조회 계약**

- 모두 로그인 없이 호출 가능하며 `ApiResponse<T>` 로 감싼다. 아래는 `data` 의 형태이다.
- `admissionYear`·`majorId` 는 해당 API에서 필수, 양수. `collegeId` 는 선택, 지정하면 양수.
- 단과대는 이름→id, 전공은 표시명→id 오름차순. 전공은 **해당 입학년도의 졸업요건 행이 있고 융합전공이 아닌 경우만** 반환한다. 일치하는 전공이 없으면 성공 + 빈 배열.
- 검색의 `majorId` 는 전공 id이며 과목 id나 단과대 id가 아니다. 해당 전공의 `course_unit` 과 정확히 일치하는 과목만 검색한다. 연결된 교육과정이 없는 전공은 성공 + 빈 페이지.
- `q` 는 최대 100자. 생략·빈 문자열·공백이면 해당 전공의 전체 과목; 그 외에는 앞뒤 공백 제거 후 과목명/학수번호 부분일치, 영문 대소문자 구분 없음. `%`, `_`, `!` 도 일반 문자로 검색한다.
- `classification` 은 3.1의 enum 이름(대문자), 생략·빈 문자열이면 전체 이수구분. 검색어와 함께 보내면 두 조건을 모두 만족해야 한다.
- `page` 는 0부터, 기본 0. `size` 는 1~100, 기본 20. 과목명→id 오름차순으로 정렬하며 마지막 페이지 이후는 빈 `content` 를 반환한다.
- 잘못된 형식·누락된 필수값·범위 초과는 `400 INVALID_INPUT`. 없는 전공 id는 `404 MAJOR_NOT_FOUND`, 없는 전공×입학년도 졸업요건 또는 대체 자료마저 없는 교양 기준은 `404 REQUIREMENT_NOT_FOUND`.
- 교과목은 **2026 교육과정 편성 자료**다. `openSemester` 는 편성상의 개설학기 표기이며, 특정 학기의 실제 개설·수강신청 가능 여부를 보장하지 않는다.

| API | 응답 data |
|---|---|
| colleges | `[{ id, name }]` |
| majors | `[{ id, displayName, collegeId, collegeName }]` |
| courses | `{ content: [과목], page, size, totalElements, totalPages }` |
| requirements | 아래 졸업요건 원본 필드의 객체 |
| general-education | 아래 교양 기준 필드의 객체 |

과목 필드: `id, collegeName, unitName, courseCode, name, classification, credits, variableCredits, targetGrade, openSemester, track`.
`courseCode, targetGrade, openSemester, track` 는 null일 수 있다. `variableCredits=true` 인 과목은 학점이 가변적이므로 이수내역 등록 때 사용자가 실제 학점을 확인해야 한다.

졸업요건 원본 필드:

- 식별: `majorId, admissionYear`
- 단일전공: `totalCredits, basicCredits, requiredCredits, electiveCredits, majorTotalCredits, otherMajorCredits`
- 다전공: `doubleBasicCredits, doubleRequiredCredits, doubleElectiveCredits, doubleTotalCredits, doubleOtherMajorCredits`
- 부전공: `minorRequiredCredits, minorElectiveCredits, minorTotalCredits`
- 부가요건: `swRequirement, englishLectureRequirement, thesisRequirement, topikRequirement, competencyCertification`

원본의 null은 그대로 반환한다. **T7 계산에서의 NULL→0 처리와 다르며**, 이 API는 졸업 가능 판정·이수학점 계산을 하지 않는다. 다전공·부전공은 기존 원본 값 조회만 제공하고 별도 계산은 하지 않는다.

교양 기준 필드:

- `admissionYear`: 요청한 입학년도, `basisYear`: 실제 사용한 기준년도.
- `approximate`: 요청 연도가 없어 2026으로 대체했으면 true.
- `requiredCredits, distributionCredits, distributionMinAreas, freeCredits, totalCredits`.
- `requiredCourses`: `[{ id, groupName, courseName, credits, recommendedGrade, note }]`, 실제 사용한 기준년도에 속하는 목록만 id 순으로 반환. `groupName, recommendedGrade, note` 는 null일 수 있다.

프론트 호출 예시·독립 테스트 실행법은 `docs/FRONTEND_GUIDE.md` 4장 catalog 및 7장 참고.

### 4.4 일정·할 일 `schedule`

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/schedules?from=2026-10-01&to=2026-10-31&category= | 🔒 기간 조회(캘린더 월, 날짜 상세). 최대 62일. 날짜→시작시간 순 정렬, 시간 없는 항목은 그날 맨 뒤 |
| POST | /api/schedules | 🔒 생성 → 201 |
| GET | /api/schedules/{id} | 🔒 |
| PATCH | /api/schedules/{id} | 🔒 보낸 필드만 수정 |
| DELETE | /api/schedules/{id} | 🔒 → 204 |
| PATCH | /api/schedules/{id}/done | 🔒 완료 토글 `{ "done": true }` |

```json
// POST /api/schedules
{ "title": "디자인씽킹 과제 제출", "category": "ASSIGNMENT", "date": "2026-10-18",
  "startTime": null, "endTime": null, "location": null, "memo": "PDF로 제출",
  "alarmMinutesBefore": 1440 }
// 응답 data
{ "id": 42, "title": "디자인씽킹 과제 제출", "category": "ASSIGNMENT", "date": "2026-10-18",
  "startTime": null, "endTime": null, "location": null, "memo": "PDF로 제출",
  "done": false, "alarmMinutesBefore": 1440, "type": "TODO" }   // type: startTime 있으면 EVENT, 없으면 TODO
```

- 기간 조회: `from`·`to` 필수(없으면 `INVALID_INPUT`), 양 끝 포함 62일까지(예: 10-01~12-01). 넘거나 `from > to` 면 `INVALID_TIME_RANGE`. `category` 생략·빈 값이면 전체.
- PATCH 는 4.2 와 같은 규칙: 생략·null 이면 그대로, **값을 비우는 기능은 없음**(시간 지우기는 삭제 후 재생성). 예외로 **알람 끄기는 `"alarmMinutesBefore": 0`**(응답은 `null`). 바꾼 뒤의 값으로 3.3 시간 규칙을 다시 검사.
- `alarmMinutesBefore` 가 3.3 목록(+ 끄기 `0`) 밖의 값이면 `INVALID_INPUT`. 생성 때 `0` 은 알람 없음과 같다.
- `PATCH /{id}/done` 은 값을 뒤집지 않고 보낸 `done` 값으로 설정한다(여러 번 보내도 결과 같음).
- 남의 일정 → `SCHEDULE_NOT_FOUND`(404). 시간은 JSON `"HH:mm"`(응답 DTO 의 `@JsonFormat`).

### 4.5 시간표 `timetable`

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/timetables?year=2026&term=SECOND | 🔒 해당 학기 칸 목록 (year/term 생략 시 현재 학기) |
| POST | /api/timetables/entries | 🔒 수업 칸 추가 → 201, 겹치면 409 |
| POST | /api/timetables/entries/bulk | 🔒 여러 칸 저장 `{ year, term, mode: REPLACE\|MERGE, entries: [...] }` → 201 (T12) |
| PATCH | /api/timetables/entries/{id} | 🔒 |
| DELETE | /api/timetables/entries/{id} | 🔒 → 204 |

```json
// POST /api/timetables/entries
{ "year": 2026, "term": "SECOND", "courseName": "타이포그래피 I", "courseCode": null,
  "dayOfWeek": 2, "startTime": "09:00", "endTime": "10:30",
  "location": "예405", "professor": "김교수", "color": "#3BB39A" }
// POST /api/timetables/entries (온라인 강의, T15)
{ "year": 2026, "term": "SECOND", "courseName": "빅뱅에서 문명까지", "online": true }
// GET 응답 data
{ "year": 2026, "term": "SECOND", "label": "2026년 2학기",
  "entries": [ { "id": 3, "courseName": "타이포그래피 I", "courseCode": null, "dayOfWeek": 2, "startTime": "09:00",
                 "endTime": "10:30", "location": "예405", "professor": "김교수", "color": "#3BB39A", "online": false },
               { "id": 4, "courseName": "빅뱅에서 문명까지", "courseCode": null, "dayOfWeek": null, "startTime": null,
                 "endTime": null, "location": null, "professor": null, "color": null, "online": true } ] }
```
- GET: `year`·`term` 중 생략한 값만 현재 학기(3.3) 값으로 채운다. `label` 은 "2026년 1학기 / 2학기 / 여름계절학기 / 겨울계절학기".
- 칸 객체(`entries` 원소, POST·PATCH 응답 data): `id, courseName, courseCode, dayOfWeek, startTime, endTime, location, professor, color, online`. 요일 → 시작시간 순, 온라인 강의는 맨 뒤에 이름 순.
- 입력: `year` 2000~2100, `dayOfWeek` 1(월)~7(일), `courseName` 100자, `courseCode` 20자, `location` 100자, `professor` 50자, `color` `#RRGGBB`. 종료 ≤ 시작이면 `INVALID_TIME_RANGE`.
- **온라인 강의(T15)**: 시간 없는 수업. `online=true` 면 요일·시간을 보내도 저장하지 않는다(null). `online` 이 false·생략이면 `dayOfWeek`·`startTime`·`endTime` 이 모두 필수이고 없으면 `INVALID_TIME_RANGE`. 온라인 강의는 겹침 검사를 하지 않고, 홈 주간 점·오늘 타임라인에 나오지 않는다. `POST .../bulk` 의 칸에도 같은 `online` 이 있다.
- 겹침: 같은 학기·요일에서 `start < 다른 칸 end` 이고 `다른 칸 start < end` 이면 `TIMETABLE_OVERLAP`(409). 끝과 시작이 같은 칸은 허용. 수정은 바꾼 뒤의 값으로 검사하고 자기 자신은 제외.
- PATCH 는 4.2·4.4 와 같은 규칙(생략·null 이면 그대로, 값 비우기 없음). 예외로 `online: true` 는 요일·시간을 지우고, `online: false` 는 요일·시간을 같이 보내야 한다(없으면 `INVALID_TIME_RANGE`). 남의 칸 → `TIMETABLE_ENTRY_NOT_FOUND`(404).

### 4.6 학사관리 `academic`

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/academic/completed-courses | 🔒 내 이수내역 (학년도·학기 순) |
| POST | /api/academic/completed-courses | 🔒 1건 등록 |
| POST | /api/academic/completed-courses/bulk | 🔒 여러 건 등록 `{ "courses": [ ...위와 같은 객체 ], "replaceTerms": [...], "keepClassification": false }` |
| PATCH | /api/academic/completed-courses/{id} | 🔒 |
| DELETE | /api/academic/completed-courses/{id} | 🔒 → 204 |
| GET | /api/academic/progress | 🔒 **이수현황 계산 결과** (학사관리 화면·홈·마이페이지의 원천) |
| GET | /api/academic/gpa | 🔒 평점 평균 요약 (학사관리 학점 박스, T13) |

```json
// POST /api/academic/completed-courses
{ "courseCode": "CSE204",          // 선택. 있으면 내 전공 교과목에서 자동 판별
  "courseName": "자료구조",
  "credits": 3,
  "classification": null,          // 자동 판별 안 될 때 필수 (교양·일반선택 등)
  "distributionArea": null,        // GEN_DISTRIBUTION 일 때 1~5 필수
  "grade": "A+",                   // A+ A0 B+ B0 C+ C0 D+ D0 F P NP, 미정이면 null
  "year": 2026, "term": "FIRST" }
```

**이수구분 자동 판별 규칙** (등록·수정 시 서버에서)

1. `courseCode` 가 있고, 사용자 전공의 `majors.course_unit` 이 있고, `courses` 에 `(unit_name = course_unit, course_code = courseCode)` 가 있으면 → 그 행의 `classification` 을 사용 (요청값 무시). `courseName` 이 비었으면 마스터 과목명으로 채움.
2. 아니면 요청의 `classification` 필수 (없으면 `CLASSIFICATION_REQUIRED`).
3. `classification = GEN_DISTRIBUTION` 이면 `distributionArea` 1~5 필수.

**T6 세부 규칙** (구현하며 정한 것)

- 자동 판별이 안 되는데 `courseName` 도 비었으면 `INVALID_INPUT`. `courseCode`·`courseName` 은 앞뒤 공백 제거, 빈 `courseCode` 는 null.
- 배분이수가 아닌 과목에 보낸 `distributionArea` 는 버린다(null 저장).
- 입력 범위: `credits` 0~30, `year` 2000~2100, `term` 필수, `grade` 는 위 목록 또는 null.
- 과목 객체(목록 원소, POST·PATCH 응답 data): `id, courseCode, courseName, credits, classification, distributionArea, grade, year, term`. 목록은 학년도 → 학기(1학기·여름·2학기·겨울) → 등록 순.
- `bulk` 는 `courses` 1~100건, 하나라도 실패하면 전부 저장하지 않는다 → 201, data = 과목 객체 배열.
- (T12) `replaceTerms: [{ year, term }]` 를 주면 그 학기들의 내 이수내역을 지우고 새로 넣는다(같은 트랜잭션, 재업로드해도 중복 없음). `keepClassification: true` 면 요청에 이수구분이 있는 과목은 자동 판별을 건너뛰고 요청값을 쓴다(성적표가 공식 기록이라서, 9장 17).
- PATCH 는 4.2·4.4 와 같은 규칙(생략·null 이면 그대로, 값 비우기 없음)이고, 합친 값으로 자동 판별을 다시 한다. 남의 과목 → `COMPLETED_COURSE_NOT_FOUND`(404).

```json
// GET /api/academic/progress 응답 data
{
  "admissionYear": 2026,
  "major": { "id": 54, "displayName": "컴퓨터공학부 컴퓨터공학과" },
  "total": { "earned": 18, "required": 130, "remaining": 112, "percent": 13.8 },
  "areas": [
    { "key": "GEN_REQUIRED",     "label": "필수교양", "earned": 6,  "required": 17 },
    { "key": "GEN_DISTRIBUTION", "label": "배분이수", "earned": 0,  "required": 9, "areaCount": 0, "requiredAreaCount": 3 },
    { "key": "GEN_FREE",         "label": "자유이수", "earned": 0,  "required": 3 },
    { "key": "MAJOR_BASIC",      "label": "전공기초", "earned": 6,  "required": 12 },
    { "key": "MAJOR_REQUIRED",   "label": "전공필수", "earned": 3,  "required": 42 },
    { "key": "MAJOR_ELECTIVE",   "label": "전공선택", "earned": 0,  "required": 27 },
    { "key": "OTHER",            "label": "기타",     "earned": 3,  "required": null }
  ],
  "groups": [                                   // 도넛 차트용
    { "key": "MAJOR",   "label": "전공", "earned": 9,  "required": 81 },
    { "key": "GENERAL", "label": "교양", "earned": 6,  "required": 29 },
    { "key": "OTHER",   "label": "기타", "earned": 3,  "required": 20 }
  ],
  "requiredGeneralCourses": [                   // 교양 필수과목 체크리스트
    { "name": "인간의가치탐색", "credits": 3, "done": true },
    { "name": "대학영어", "credits": 2, "done": false }
  ],
  "checks": [                                   // 자동 검증 불가 → 안내용 (2026만 자료 있음)
    { "name": "SW기초교육",   "requirement": "6학점", "status": "MANUAL" },
    { "name": "졸업논문",     "requirement": "필요",  "status": "MANUAL" },
    { "name": "TOPIK(외국인)", "requirement": "4급 이상(한국어트랙 외국인)", "status": "MANUAL" },
    { "name": "졸업능력인증", "requirement": "있음",  "status": "MANUAL" }
  ],
  "graduatable": false,
  "approximate": false                          // 교양 기준을 2026으로 대체했으면 true
}
```

```json
// GET /api/academic/gpa 응답 data (T13)
{
  "gpa": 3.417,              // 전체 평점 평균(4.3 만점, 소수 셋째 자리 반올림). 평점 대상 과목이 없으면 null
  "majorGpa": 3.3,           // 전공(전공기초·전공필수·전공선택) 평점, 없으면 null
  "gradedCredits": 18,       // 평점 계산에 들어간 학점 (P·NP·성적 미정 제외)
  "earnedCredits": 20,       // 취득 학점 (F·NP·성적 미정 제외, P 포함)
  "terms": [                 // 학년도 → 학기(1학기·여름·2학기·겨울) 순
    { "year": 2026, "term": "FIRST", "gpa": 3.417, "gradedCredits": 18, "earnedCredits": 20 }
  ]
}
```

- 이수내역이 없으면 `{ "gpa": null, "majorGpa": null, "gradedCredits": 0, "earnedCredits": 0, "terms": [] }`(200). 입학년도·전공이 없어도 동작한다(졸업요건을 쓰지 않음). 계산 규칙은 5장 "평점 평균".

### 4.7 홈 `home`

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/home?date=2026-10-16 | 🔒 홈 화면 한 번에 (date 생략 시 오늘, Asia/Seoul) |

```json
{
  "greetingName": "하은",                       // nickname 없으면 name
  "week": [                                      // date가 속한 주 월~일 7칸 (1.에서 미결정 항목 참고)
    { "date": "2026-10-15", "dayOfWeek": 1, "today": false, "categories": [] },
    { "date": "2026-10-16", "dayOfWeek": 2, "today": true,  "categories": ["LECTURE", "PERSONAL"] }
  ],
  "timeline": [                                  // 오늘 일정: 시간표 수업 + 시간 있는 일정 + e캠퍼스 과제 마감, 시작시간 순
    { "source": "TIMETABLE", "id": 3,  "title": "타이포그래피 I", "category": "LECTURE",
      "startTime": "09:00", "endTime": "10:30", "location": "예405", "courseName": null, "url": null },
    { "source": "SCHEDULE",  "id": 51, "title": "세시 팀플 모임", "category": "PERSONAL",
      "startTime": "12:00", "endTime": null, "location": null, "courseName": null, "url": null },
    { "source": "ECAMPUS",   "id": null, "title": "3주차 과제 제출", "category": "ASSIGNMENT",   // T14
      "startTime": "23:59", "endTime": null, "location": null,
      "courseName": "디자인씽킹", "url": "https://khcanvas.khu.ac.kr/..." }
  ],
  "todos": [ { "id": 60, "title": "자료 조사", "category": "ASSIGNMENT", "done": false } ],  // 오늘 날짜의 시간 없는 일정
  "graduation": { "earned": 102, "required": 130, "percent": 78.4, "remaining": 28 }           // 프로필 미완성이면 null
}
```

- `week`: `date` 가 속한 주 **월~일**(최종 디자인). `today` 는 `date`(생략 시 오늘)와 같은 날. 학기는 날짜마다 `AcademicTerm.of` 로 본다.
- `week.categories`: 그날의 일정 카테고리(중복 제거) + 그 요일에 시간표 수업이 있으면 `LECTURE` + **e캠퍼스 과제 마감(4.8)이 있으면 `ASSIGNMENT`**. 순서는 LECTURE·ASSIGNMENT·PERSONAL. e캠퍼스를 못 받아 오면 그 점만 빠지고 홈은 정상 응답.
- `timeline`: 시작시간 순, 같은 시각이면 **시간표 수업 → 직접 등록한 일정 → e캠퍼스** 순. `todos`: 오늘 날짜의 시간 없는 일정(완료한 것도 `done: true` 로 포함). e캠퍼스 마감은 제출 여부를 몰라 할 일에는 넣지 않는다.
- (T14) `source: "ECAMPUS"`: `date` 날짜에 마감인 e캠퍼스 과제(4.8). `startTime` = 마감 시각(시각 없는 하루 종일 마감은 **23:59**), `id`·`endTime`·`location` 은 null, `category` 는 항상 `ASSIGNMENT`. `courseName`·`url` 은 ECAMPUS 에만 값이 있고 나머지 항목은 null. e캠퍼스 요청은 주간 점과 합쳐 **홈 1번당 1번**. 미연동이거나 못 받아 오면 ECAMPUS 항목과 점만 빠지고 홈은 정상 응답. 제출한 과제도 뜨고(피드에 제출 여부 없음), 사용자가 같은 과제를 직접 등록했으면 두 번 보일 수 있다(제목 비교로 거르지 않음).
- ~~`recommendation`~~ **보류(2026-10-07)**: 최종 디자인에 카드가 없어 응답에서 뺐다. 3차 LLM 추천 때 규칙(오늘~7일 이내 미완료 과제 중 가장 가까운 것)과 함께 다시 넣는다.


### 4.8 e캠퍼스 과제 마감 `ecampus`

e캠퍼스(Canvas, `khcanvas.khu.ac.kr`)의 **개인 캘린더 피드(.ics)** 를 사용자가 등록하면 과제 마감을 읽기 전용으로 보여준다. 학교 비밀번호는 받지 않는다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| PUT | /api/ecampus/feed | 🔒 피드 주소 등록·변경 `{ "feedUrl": "https://khcanvas.khu.ac.kr/feeds/calendars/user_….ics" }` → 204 |
| DELETE | /api/ecampus/feed | 🔒 연결 해제 → 204 |
| GET | /api/ecampus/events?from=2026-10-01&to=2026-10-31 | 🔒 기간 안의 과제 마감 |

```json
// GET /api/ecampus/events 응답 data
{ "connected": true, "syncedAt": "2026-10-06T19:30:00",
  "events": [ { "id": "event-assignment-123", "title": "중간 레포트", "courseName": "디자인씽킹 01분반",
                "date": "2026-10-18", "time": null, "url": "https://khcanvas.khu.ac.kr/calendar?…" } ] }
// 연결 안 했으면 { "connected": false, "syncedAt": null, "events": [] } (오류 아님)
```

- 주소는 정확히 `https://khcanvas.khu.ac.kr/feeds/calendars/user_{영숫자}.ics` 만 허용(그 외 `INVALID_INPUT`). DB(`users.ecampus_feed_token`, V10)에는 `user_…` 토큰만 저장하고, 서버가 고정 주소에 붙여 요청한다(다른 서버로 요청 불가). 토큰은 비밀값이라 응답·로그에 내보내지 않는다. `GET /api/users/me` 의 `ecampusConnected` 로 연결 여부만 알려준다.
- 동기화: 조회할 때 마지막으로 받은 지 **10분**이 지났으면 다시 받고 아니면 서버 메모리에 둔 것을 쓴다. 주기적 백그라운드 동기화는 하지 않는다. 등록·해제 시 버린다. 요청 제한 5초·2MB.
- 다시 받다가 실패하면 예전에 받은 것을 돌려준다(`syncedAt` 이 예전 시각). 받은 적이 없으면 `ECAMPUS_FEED_UNAVAILABLE`(502).
- `title`·`courseName`: 피드 제목 `과제명 [과목명 분반]` 을 나눈 것(괄호가 없으면 `courseName` null). `time` null = 하루 종일 표시(Canvas 는 23:59 마감을 이렇게 내보냄), 시간이 있으면 한국 시간 `"HH:mm"`.
- 정렬: 날짜 → 시간 순, 시간 없는 것은 그날 맨 뒤. `from > to` 면 `INVALID_TIME_RANGE`(기간 길이 제한 없음).
- 피드에 제출 여부가 없어 완료 체크는 없다. 일정(`schedules`)과 섞지 않으며 캘린더 화면이 두 API를 함께 불러 그린다. 홈 반영: 주간 점(T8), 오늘 `timeline`(T14) → 4.7.


### 4.9 파일 가져오기 `importer` (T12)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | /api/import | 🔒 multipart `file` (≤5MB) → 저장하지 않고 **미리보기 초안**과 `warnings` 를 돌려줌 |

- 지원: **xlsx(추천)·csv·txt**. 형식은 확장자가 아니라 앞부분 바이트로 판별(ZIP+`xl/workbook.xml`=xlsx, 나머지는 텍스트: UTF-8 → 실패 시 MS949). pdf·이미지·hwp·xls 등은 `IMPORT_UNSUPPORTED_FORMAT`("엑셀로 저장해서 올려주세요").
- 첫 줄 제목으로 해석기 선택: `수강신청확인서` → 시간표 초안(`type: TIMETABLE`), `전체 성적 보기` → 이수내역 초안(`type: GRADES`). 아니면 `IMPORT_UNRECOGNIZED`.
- 파일의 학번이 로그인 사용자의 `studentNumber` 와 다르거나 없으면 `IMPORT_STUDENT_MISMATCH`(403). 응답에 학번·이름은 넣지 않는다.
- 저장은 초안을 고친 뒤 `POST /api/timetables/entries/bulk`(mode=REPLACE) 또는 `POST /api/academic/completed-courses/bulk`(replaceTerms = 초안의 학기, keepClassification=true) 로 한다.
- 파일은 메모리에서만 처리하고 버린다. 학번·이름·성적은 로그에 남기지 않는다.
- 응답 data: `{ type, format, timetable, grades, warnings }` — `type` 이 TIMETABLE 이면 `timetable`, GRADES 면 `grades` 만 채운다.
  - `timetable`: `{ year, term, courseCount, totalCredits, courses: [{ courseCode, section, courseName, credits, classificationText, classification, retake, sessionCount }], entries: [bulk 저장용 칸] }`
  - `grades`: `{ terms: [{ year, term }], courseCount, totalCredits, courses: [{ year, term, courseCode, section, courseName, credits, grade, classificationText, classification, distributionArea, needs }] }`
  - `warnings`: `[{ code, message }]`, code = NO_TIME / OVERLAP / UNKNOWN_CLASSIFICATION / DISTRIBUTION_AREA_REQUIRED / UNKNOWN_GRADE / DISCARDED / UNPARSED_ROW
- 읽는 법: 모든 형식을 "행과 칸의 목록"으로 바꾼 뒤 같은 해석기로 읽는다. xlsx 는 **병합 영역 1개 = 칸 1개**(csv·txt 와 같은 행이 됨), txt 는 탭 구분, csv 는 따옴표·칸 안 줄바꿈 지원. 칸이 모두 빈 행은 빈 행.
- 수강신청확인서: 머리글 글자로 열을 찾고, 수업 칸을 쉼표로 나눠 `교수 요일HH:MM-HH:MM 강의실` 마다 시간표 1칸(월=1…일=7). 시간이 없으면 `online: true` 칸(요일·시간 null, 수업 칸의 첫 낱말을 교수명으로, 2026-10-30 T15)을 만들고 NO_TIME 경고로 알린다. `학수번호-분반` 은 하이픈으로 나눔.
- 전체 성적 보기: `평점/등급` 머리글 다음부터 다른 요약 표 제목 전까지가 과목 표. 과목 행은 **뒤에서부터 센 위치**(-13 학수번호 … -5 등급, -3 폐기사유)로 읽고, 앞 두 칸이 `2026`·`1학기` 모양일 때만 학기를 새로 잡아 아래로 이어 쓴다. 폐기사유가 있으면 제외, 쪽 번호·반복 머리글은 건너뜀. 분반은 끝이 G두자리면 그것, 아니면 마지막 두 자리. 이수구분은 글자(없으면 학교 코드 04·05·08·11·14·15·16·17·06·20)로 매핑, 모르면 null + `needs: CLASSIFICATION`. 배분이수는 `needs: DISTRIBUTION_AREA`.
- 5MB 초과는 Spring 업로드 한도에서도 `IMPORT_TOO_LARGE`. 업로드는 디스크 임시파일 없이 메모리에서만(`multipart.file-size-threshold`).
- **PDF 는 받지 않는다**(9장 19): PDFBox 로 확인한 결과 한글은 읽히지만 표 구조(2줄 칸·세로 병합된 학기·빈 칸)가 사라져 신뢰할 수 없음.

### 4.10 일정 알림 웹푸시 `notification` (T11)

> 2026-10-09: 카카오 '나에게 보내기'는 휴대폰 알림이 울리지 않아 **브라우저 웹푸시(Push API)** 로 바꿨다(이유·설계는 `docs/plans/T11-web-push.md`). 무료, 심사·사업자 불필요. 안드로이드·PC 는 바로, 아이폰은 iOS 16.4+ 에서 홈 화면에 추가한 경우만.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/notifications/push/public-key | 🔒 `{ "publicKey": "BMx…(87자)" }`: 구독할 때 `applicationServerKey` 로 쓴다 |
| POST | /api/notifications/push/subscriptions | 🔒 브라우저의 `subscription.toJSON()` 그대로 → 204. 같은 endpoint 면 갱신(다른 사용자 것이었으면 지금 사용자로 옮김) |
| DELETE | /api/notifications/push/subscriptions | 🔒 `{ "endpoint": "..." }` → 204. 내 것만 지우고, 없어도 204 |
| POST | /api/notifications/push/test | 🔒 내 모든 기기로 테스트 알림을 바로 보냄 → `{ "sent": 1, "failed": 0 }` (알림 끄기 상태여도 보냄) |

```json
// POST /api/notifications/push/subscriptions (expirationTime 은 무시)
{ "endpoint": "https://fcm.googleapis.com/fcm/send/dXk…",
  "keys": { "p256dh": "BNc…(87자)", "auth": "tBH…(22자)" } }
```

- 검증(아니면 `INVALID_INPUT`): endpoint 는 `https`·포트 지정 없음·사용자정보 없음·700자 이하·호스트가 **허용 목록**(`fcm.googleapis.com`, `updates.push.services.mozilla.com`, `*.push.apple.com`, `*.notify.windows.com`). 서버가 이 주소로 직접 요청하므로 다른 주소는 받지 않는다(SSRF 방지, 보낼 때도 다시 확인). `p256dh` = Base64url 65바이트 P-256 공개키, `auth` = 16바이트.
- 사용자당 기기 최대 10개, 넘으면 먼저 등록한 것부터 지운다. 서버에 VAPID 키가 없으면 4개 모두 `PUSH_NOT_CONFIGURED`(503), 서버는 정상 시작.
- **발송**: 1분마다 `날짜 + (시작시간 ?? 사용자의 todoAlarmTime(4.2, 기본 09:00)) − alarm_minutes_before` 가 **지난 10분 안**(`now − 10분 < 발송 시각 ≤ now`, Asia/Seoul)인 일정을 그 사용자의 모든 기기로 보낸다. `done` 이거나 `notificationEnabled=false` 면 안 보냄(구독은 유지).
- **한 번만**: 보내기 전에 `schedules.alarm_sent_for` 를 조건부 UPDATE 로 선점(바뀐 행 1 = 내 차례). 서버를 재시작하거나 여러 대여도 같은 알림은 한 번. 일정 시간·알람을 바꾸면 발송 시각이 달라져 다시 보낸다.
- 결과: 201 성공, 404·410 → 만료된 구독이라 삭제, 그 밖(403·429·5xx·시간 초과 5초)은 경고 로그(구독 id·상태 코드만)만, **재시도 없음**.
- 알림 내용(기기 키로 암호화, RFC 8291 aes128gcm + VAPID RFC 8292): `{ "title": "디자인씽킹 과제 제출", "body": "10월 18일 (일) 09:00 · 예405", "tag": "schedule-42", "url": "/" }`. 할 일이면 `"10월 18일 (일) 할 일"`, 장소 없으면 생략. **메모는 넣지 않는다.** 테스트 알림은 `"오르도 알림 테스트"`.
- 키는 환경변수(`WEBPUSH_PUBLIC_KEY`, `WEBPUSH_PRIVATE_KEY`, `WEBPUSH_SUBJECT` = `mailto:…` 또는 `https://…`, localhost 불가)로만. 짝이 안 맞거나 형식이 틀리면 서버 시작 실패(키 값은 로그에 안 찍음). 스케줄러는 `NOTIFICATION_SCHEDULER_ENABLED=false` 로 끈다.
- endpoint·p256dh·auth·개인키는 로그와 응답에 남기지 않는다. `alarm_sent_for` 는 응답에 없다.

---

## 5. 이수현황 계산 알고리즘 (`AcademicProgressService`)

1. 사용자 `admissionYear`, `majorId` 가 없으면 `PROFILE_INCOMPLETE`.
2. `graduation_requirements(majorId, admissionYear)` 조회, 없으면 `REQUIREMENT_NOT_FOUND`.
3. 교양 기준: `general_education_requirements(admissionYear)`, 없으면 2026 행 사용 + `approximate = true`.
4. 인정 과목 필터: `grade` 가 `F` 또는 `NP` 인 과목 제외. **재수강**: 같은 `course_code`(없으면 `course_name`)가 여러 번이면 가장 최근(학년도·학기 최신) 1건만 인정.
5. 영역별 합계 (`classification` 기준):
   - `GEN_REQUIRED` → 필수교양, `GEN_DISTRIBUTION` → 배분이수(+서로 다른 `distribution_area` 개수), `GEN_FREE` → 자유이수
   - `MAJOR_BASIC` / `MAJOR_REQUIRED` / `MAJOR_ELECTIVE` → 각 전공 영역
   - `GENERAL_ELECTIVE`, `TEACHING`, `TEACHING_MAJOR` → 기타
6. 요구 학점: 전공 영역은 단일전공 컬럼(`basic_credits`, `required_credits`, `elective_credits`), **NULL은 0으로 취급하고 응답 `required`는 0**. 교양은 3번 기준.
7. `total.earned` = 인정 과목 학점 합, `total.required` = `total_credits`, `remaining = max(0, required - earned)`, `percent = 소수 첫째 자리 버림(earned / required * 100)`.
8. `groups`: 전공 = 세 전공영역 합 vs `major_total_credits`, 교양 = 세 교양영역 합 vs 교양 total, 기타 = 기타 합 vs `max(0, total_credits - major_total_credits - 교양 total)`.
9. `requiredGeneralCourses`: 교양 필수과목 이름과 `GEN_REQUIRED` 이수 과목명을 공백 제거 후 비교해 `done`.
10. `graduatable` = 총학점 충족 AND 모든 영역 `earned >= required` AND 배분 영역 수 충족 AND 필수교양 과목 전부 done.
11. `checks`: 2026 부가요건 텍스트가 있는 항목만 `status: "MANUAL"` 로 안내(자동 판정하지 않음).

**T7 세부 규칙** (구현하며 정한 것)

- 성적 미정(`grade` null)은 인정한다(F·NP 만 제외). 4번은 적힌 순서대로 F·NP 를 먼저 빼고 재수강을 고른다 → 예전에 통과하고 재수강에서 F 면 예전 학점이 남는다(학과 시행세칙 확인 후 조정, 코드에 `ponytail:` 주석).
- 재수강 판별 키: 학수번호, 없으면 공백을 뺀 과목명. 같은 학기면 나중에 등록한 것.
- `areas` 의 `OTHER` 는 `required: null`, `areaCount`·`requiredAreaCount` 는 배분이수에만 있다. 요구 학점이 0 이면 `percent` 는 0.0.
- `checks` 이름: SW기초교육 / 영어강의 / 졸업논문 / TOPIK(외국인) / 졸업능력인증 (값이 있는 것만).
- 계산은 DB 조회 없는 정적 함수 `AcademicProgressService.calculate(...)` 로 분리 → 졸업 시뮬레이션은 계획 과목을 더해 그대로 부르면 된다.

**반드시 단위 테스트할 케이스**: 빈 이수내역 / F 제외 / 재수강 중복 제거 / NULL 요구학점 / 배분 영역 수 부족 / 2025학번 교양 근사(approximate) / 졸업 가능 케이스.

> 다전공·부전공 계산, 전공 초과학점의 전공선택 인정, 타전공 인정 한도는 **MVP 제외**(9장 미결정).
### 평점 평균 (`AcademicGpaService`, T13)

**등급 → 평점** (경희대 **4.3 만점**. A+·B+·B0·C+ 는 성적표 평점 칸으로 확인, 나머지는 4.3 척도 관례값 → 9장 20)

| A+ | A0 | B+ | B0 | C+ | C0 | D+ | D0 | F |
|---|---|---|---|---|---|---|---|---|
| 4.3 | 4.0 | 3.3 | 3.0 | 2.3 | 2.0 | 1.3 | 1.0 | 0 |

1. 평점 평균 = Σ(평점 × 학점) ÷ Σ학점. **P·NP·성적 미정(null)은 분모·분자 모두 제외**, F 는 0점으로 포함. 대상 학점이 0 이면 null.
2. 취득 학점(`earnedCredits`) = F·NP·성적 미정이 아닌 과목의 학점(P 포함). 이수현황(성적 미정도 셈)과 다르다.
3. **재수강(전체 값)**: 재수강 판별 키(학수번호, 없으면 공백 뺀 과목명)가 같으면 가장 최근(학년도·학기, 같은 학기면 나중 등록) 1건만. **최근 것이 F 면 F(0점)가 반영**된다 → 이수현황(4번, F 를 먼저 뺌)과 다를 수 있다(9장 21).
4. 학기별 값(`terms`)은 그 학기에 기록된 과목 전부로 계산(재수강으로 대체된 예전 과목도 그 학기 성적표에는 남으므로). 학기별 값을 합쳐도 전체와 다를 수 있다.
5. 전공 평점(`majorGpa`): 이수구분 `MAJOR_BASIC`·`MAJOR_REQUIRED`·`MAJOR_ELECTIVE` 만으로 1·3번 계산(성적표 산정기준 "전공 = 전공필수·전공선택·전공기초"와 같음).
6. 반올림: `BigDecimal` 로 계산해 **소수 셋째 자리 HALF_UP**(성적표 3.417 = 61.5 ÷ 18 과 같게).
7. 4.5 변환 평점은 넣지 않는다.

**반드시 단위 테스트할 케이스**: 등급표 / 학점 가중 / 성적표 샘플 대조(3.417·전공 3.3·18·20) / P·NP·미정 제외 / 전부 P 면 null / F 0점 / 재수강(최근 F 포함, 이름 공백 무시) / 학기별·학기 순 / 전공 평점 / 반올림 / 빈 이수내역.

> `AcademicProgressService.getSummary(Long userId)` 는 홈·마이페이지에서도 쓰므로 **시그니처를 먼저 합의**하고 만든다: 반환 `ProgressSummary(total, areas, groups, ...)`, 프로필 미완성이면 `Optional.empty()` 를 주는 `findSummary(userId)` 도 함께 제공.

---

## 6. 시드 데이터 설명 (`V2__seed_catalog.sql`)

| 데이터 | 출처 | 비고 |
|---|---|---|
| 단과대 10 | 기본구조 국제 시트 | '예술디자인대학' → '예술·디자인대학' 통일 |
| 전공 76 | 기본구조 2020~2026 국제 시트 | 학번마다 이름이 달라 학번별 전공을 모두 등록 (예: 2020~21 '컴퓨터공학과', 2022~ '컴퓨터공학부 컴퓨터공학과') |
| 졸업요건 393 | 같은 파일 | 학번×전공. 2026행만 SW·영어강의·논문·TOPIK·인증 텍스트 |
| 교양 기준 1 + 필수과목 6 | 졸업관리데이터 ② | 2026만 |
| 교과목 3,036 | 졸업관리데이터 ⑤ | 2026 전공 교과목만. 교양 과목 목록 없음 → 교양은 사용자가 이수구분 직접 선택 |

알려진 한계: 교과목은 2026 편성 기준이라 옛 학번 학생은 자동 판별이 틀릴 수 있음(수정 가능하게 함). `course_unit` 이 NULL인 전공 2개('글로벌한국학과', '스마트팜공학 융합전공')는 자동 판별 불가. 과목명 판독불가 4건, 개설학기 미표기 18건.

---

## 7. Flyway 규칙

- V1(스키마), V2(시드)는 공동 소유. **이미 적용된 파일은 절대 수정 금지** — 바꿀 게 있으면 새 파일.
- 새 마이그레이션 번호: **A 담당 V10~V49, B 담당 V50~V89**. 파일명 `V{번호}__{영문_설명}.sql`.
- 사용 중: `V10__add_ecampus_feed_token.sql`(A, users 에 e캠퍼스 피드 토큰 칸 추가).
- 사용 중: `V11__add_web_push.sql`(A, T11: `push_subscriptions` 새 테이블, `schedules.alarm_sent_for`·`idx_schedules_date`, `users.todo_alarm_time`, 2026-10-21 승인).
- 사용 중: `V12__add_timetable_online.sql`(A, T15: `timetable_entries` 요일·시간 NULL 허용, `online` 추가, 2026-10-30 승인).
- 로컬 DB가 꼬이면: `DROP DATABASE ordo;` 후 서버 재시작 → Flyway가 V1부터 다시 적용.

---

## 8. 분업과 작업 순서

인터페이스(5장의 `AcademicProgressService` 시그니처, 2장의 공통 규칙)만 지키면 서로 기다리지 않고 병행할 수 있다.

| 순서 | 작업 | 담당 | 완료 조건 |
|---|---|---|---|
| T0 ✅ | 공통 기반 (의존성·yml·global 패키지·JWT·CORS·Swagger·Flyway 적용) | 한 명이 먼저, 하루 안에 main에 머지 | 서버 부팅 시 V1·V2 적용, `/api/health` 200, `/swagger-ui/index.html` 열림, 토큰 없이 🔒 API 호출 시 401 JSON |
| T1 ✅ | auth (가입·로그인·재발급·로그아웃) | A | Swagger에서 가입→로그인→🔒API→refresh→logout 흐름 동작 |
| T2 ✅ | user (me 조회·수정·settings) | A (T1과 함께) | |
| T3 ✅ | catalog 엔티티 + 조회 API | B | `feat/catalog`에서 5개 조회 API 구현. T3 33개 + 기존 인증·회원 19개 + 실제 MySQL 부팅·시드 조회 2개로 전체 54개 테스트 통과 |
| T4 ✅ | schedule CRUD | A | 기간 조회 정렬·권한(남의 일정 404)·시간 검증 |
| T5 ✅ | timetable CRUD | A | 겹침 409, 현재 학기 기본값 |
| T6 ✅ | completed-courses CRUD + 이수구분 자동판별 | A (B 대신) | CSE204 입력 시 컴공 학생은 MAJOR_REQUIRED 자동 |
| T7 ✅ | progress 계산 + 단위 테스트 | A (B 대신) | 5장 테스트 케이스 전부 통과 |
| T8 ✅ | home 집계 | A (T7의 `findSummary` 사용) | 4.7 응답 형태 그대로 |
| T9 ✅ | users/me/summary | A | |
| T11 | 일정 알림 웹푸시 (4.10) | A | 구독 API 4개, 1분 스케줄러, 중복 발송 없음(선점 UPDATE), RFC 8291 테스트 벡터, 허용 주소 검증, 할 일 알림 시각 설정, 실기기(PC·안드로이드·아이폰) 수신 확인 |
| T12 ✅ | 성적·시간표 파일 가져오기 (4.9) | A | 샘플 기대값(신청 6과목 18학점·7칸(T15 후 온라인 1칸 추가)·경고 1, 성적 8과목 20학점), xlsx·csv·txt 결과 동일, 학번 대조, 재업로드 중복 없음 |
| T10 ✅ | e캠퍼스 피드 연동 (4.8) | A | 피드 주소 검증, 10분 캐시, 실패 시 예전 데이터, 토큰 비노출 |
| T14 ✅ | 홈 오늘 일정에 e캠퍼스 과제 (4.7) | A | ECAMPUS 항목, 23:59 규칙, 같은 시각 정렬(수업→일정→e캠퍼스), 미연동·실패 시 정상, e캠퍼스 호출 1번 |
| T13 ✅ | 학사관리 평점 평균 (4.6, 5장 평점 평균) | A | 등급표(4.3), P·NP 제외, 재수강 최근 1건(F 포함), 학기별, 전공 평점, 셋째 자리 반올림, 성적표 샘플 대조(3.417·3.3), 빈 이수내역 |

| T15 ✅ | 시간표 온라인 강의 (4.5, `docs/plans/T15-figma-final.md`) | A | 요일·시간 없는 칸 저장, 겹침 검사 제외, 온라인 맨 뒤 정렬, 홈에서 제외, 파일 가져오기의 시간 없는 과목이 온라인 칸으로 저장 |
| T16 | 홈 D-Day 알림 (계획서 2장, 승인 2026-10-30) | A | 7일 이내 미완료 과제·개인일정 + e캠퍼스 과제, 최대 5개 |
| T17 | 남은 수강 과목 (계획서 2장, 승인 2026-10-30) | A | 필수교양·전공 중 안 들은 과목, 학수번호 → 과목명 판정 |
| T18 | 학기별 배치하기 (계획서 2장, 승인 2026-10-30) | A | GET·PUT(통째로 교체), V13 |

### AI에게 줄 프롬프트 예시 (그대로 복사해서 사용)

**T0 공통 기반**
```
AGENTS.md와 docs/BACKEND_SPEC.md 를 먼저 읽어줘.
명세서 2장(기술 규칙)과 T0 완료 조건에 맞춰 공통 기반을 만들어줘:
build.gradle 의존성 추가, application.yml 을 2.3처럼 변경, global 패키지
(ApiResponse, BaseTimeEntity + JpaAuditing, ErrorCode 전체 목록, BusinessException, GlobalExceptionHandler,
JwtProvider, JwtAuthenticationFilter, @LoginUser + ArgumentResolver, SecurityConfig(허용 경로는 4장 참고), CORS, OpenAPI 설정).
기존 SecurityConfig, HealthController 는 global 로 옮겨줘. 테이블은 만들지 말고 Flyway V1/V2 를 그대로 써.
한 번에 다 하지 말고 파일 목록과 계획을 먼저 보여준 뒤 단계별로 진행해줘.
```

**T6 + T7 학사 계산**
```
AGENTS.md, docs/BACKEND_SPEC.md 의 3장·4.6·5장을 읽어줘.
academic 패키지에 CompletedCourse 엔티티(V1 completed_courses 컬럼과 정확히 일치), CRUD API,
이수구분 자동 판별 규칙, AcademicProgressService(getSummary / findSummary)와 GET /api/academic/progress 를 만들어줘.
5장의 계산 규칙을 그대로 구현하고, 5장에 적힌 테스트 케이스를 JUnit5 단위 테스트로 작성해줘(리포지토리는 Mock).
```

**T4 일정**
```
AGENTS.md, docs/BACKEND_SPEC.md 3.3·4.4 를 읽고 schedule 도메인을 만들어줘.
Schedule 엔티티는 V1 schedules 컬럼과 정확히 일치, 응답에 type(EVENT/TODO) 포함,
남의 일정 접근은 404, 종료시간 검증, 기간 조회 최대 62일. 서비스 단위 테스트도 작성해줘.
```

### Git 규칙
- 브랜치: `feat/{도메인}` (예: `feat/schedule`), 작업 단위로 **작게 자주** PR, 상대방이 리뷰 후 머지.
- 매일 작업 시작 전 main 최신 받아서 내 브랜치에 반영.
- `global` 패키지·`ErrorCode`·`build.gradle`·`application.yml` 은 충돌이 잦은 공용 파일 → 수정하면 바로 상대에게 알리고 빨리 머지.

---

## 9. 미결정 사항 (팀·프론트와 정해야 함)

| # | 내용 | 임시 결정 |
|---|---|---|
| 1 | 프론트 코드의 API 경로·필드명과 이 명세가 맞는지 | 2026-09-24 기준 프론트(React, CRA, 포트 3000)는 화면만 있고 API 호출 없음. 회원가입 필드는 SignUp.js에 맞춰 4.1 수정 완료. 나머지 화면은 연동할 때 4장 기준으로 맞춤 |
| 2 | 홈 주간일정: 회의록 1차 "오늘이 맨 왼쪽" vs 디자인 "월요일 시작" | 월요일 시작(디자인). 바뀌면 `week` 시작일만 변경 |
| 3 | 이수내역 입력 화면이 디자인에 없음 | 학사관리에 "과목 추가" 모달 필요 → 디자인팀 요청 |
| 4 | 가입 이메일을 학교메일(@khu.ac.kr)로 제한할지 | 제한 안 함 |
| 5 | 마이크로디그리 화면 | 데이터 없음 → 보류 |
| 6 | 전공 초과학점 전공선택 인정, 타전공 인정 한도, 재수강 세부 규칙 | 학과 시행세칙 확인 전까지 5장 단순 규칙 |
| 7 | 2025학번 이하 교양 기준 | 2026 값으로 근사 + approximate 표시 |
| 8 | 다전공·부전공 | MVP 제외 (데이터는 이미 시드에 있음) |
| 9 | 알림 실제 발송 | **T11 로 앞당김(2026-10-08)**, 2026-10-09 카카오 → **웹푸시**로 변경 → 4.10 |
| 10 | 디자인에 남아있는 '과제', '설정' 메뉴 | 회의록 2차대로 삭제 |
| 11 | e캠퍼스 연동 | **결정(2026-10-06): MVP 포함.** Canvas 개인 캘린더 피드(.ics) 주소를 사용자가 등록, 캘린더를 열 때 가져오고 10분 캐시, cron 없음 → 4.8. **학교 비밀번호를 서버에 저장하는 방식은 금지** |
| 12 | 시간 없는 할 일의 알림 기준 시각 (T11) | **결정(2026-10-21): 사용자가 설정**(`todoAlarmTime`, 기본 09:00). 하루 전 알림 = 전날 그 시각 |
| 13 | 휴대폰 알림 (T11) | '나에게 보내기'는 푸시가 안 와서 웹푸시로 변경(2026-10-09). 아이폰은 iOS 16.4+·홈 화면 추가 필요. 실기기 확인은 HTTPS 배포 후 |
| 14 | 서버가 꺼져 있던 동안 놓친 알림 (T11) | 10분 이내 것만 발송, 더 오래된 것은 버림 |
| 15 | 알림 발송 실패 재시도 (T11) | 안 함(경고 로그만, 404·410 은 구독 삭제) |
| 16 | 중핵교과·기초교과 → GEN_REQUIRED (T12) | 추정. 근거: 성적표 이수구분 코드 14(중핵교과) = 3.1 "교양 필수교과 14, 16". 기초교과 코드는 확인 필요 |
| 17 | 가져오기 이수구분: 성적표 vs 교육과정 마스터가 다를 때 (T12) | **결정(2026-10-08): 성적표 우선**(keepClassification), 다르면 경고 |
| 18 | 같은 파일 재업로드 (T12) | **결정(2026-10-08): 학기 단위 교체(REPLACE)** |
| 19 | PDF 가져오기 (T12) | **결정(2026-10-08): 거절.** PDFBox 로 확인한 결과 한글은 읽히지만 표 구조(2줄 칸·세로 병합된 학기·빈 칸)가 사라져 신뢰할 수 없음. 원본 PDF 의 칸 테두리로 표를 재구성하는 방법은 여러 학기 성적표 샘플이 생기면 재검토 |
| 20 | 평점 척도·반올림 (T13) | **결정(2026-10-16): 4.3 만점, 소수 셋째 자리 반올림**(성적표로 확인). A0·C0·D+·D0 점수(4.0·2.0·1.3·1.0)는 샘플에 없어 관례값 → 실제 성적표에 나오면 대조 |
| 21 | 재수강 처리: 평점 vs 이수현황 (T13) | 평점은 최근 1건(최근이 F 면 0점), 이수현황은 F 를 먼저 빼고 최근 1건(5장 4번) → 결과가 다를 수 있음. 학과 시행세칙(재수강 성적 상한 등) 확인 후 맞춤 |
| 22 | 시간표의 시간 없는 수업 (T15) | **결정(2026-10-30): 온라인 강의 = 요일·시간 없음.** 디자인의 수업 추가 창에는 시간 칸과 온라인 체크가 함께 있지만, 온라인 수업은 표 아래 줄에 시간 없이 나오므로 시간은 저장하지 않는다. 파일 가져오기의 시간 없는 과목도 온라인 칸으로 넣는다 |
