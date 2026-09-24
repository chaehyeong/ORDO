# ORDO 백엔드 개발 명세서 (AI 작업용)

> **이 문서가 백엔드 구현의 유일한 기준이다.** AI에게 작업을 시킬 때 이 문서와 `AGENTS.md` 를 먼저 읽히고 시작한다.
> `docs/ORDO_ARCHITECTURE.md` 는 초기 개요(구버전)라서, 내용이 다르면 **이 문서가 우선**이다.
> 근거 자료: 회의록.txt(1·2차), 디자인 시안 7장, `경희대_국제캠_2026_졸업관리데이터.xlsx`, `경희대_교육과정기본구조/2020~2026학번`.
> 최종 갱신: 2026-09-24

---

## 0. 한눈에 보기

| 항목 | 내용 |
|---|---|
| 서비스 | **오르도(ORDO, 라틴어 '질서')** — 경희대 국제캠퍼스 학생용 일정 + 학사(졸업요건) 통합 웹서비스 |
| 대상 | 국제캠퍼스 전체 학과 (학번 2020~2026) |
| 백엔드 | Java 17 · Spring Boot 3.3.5 · Spring Data JPA · Spring Security(JWT) · MySQL 8 · Flyway · Gradle |
| 프론트 | 별도 웹 프론트엔드 → 백엔드는 **REST API(JSON)만** 제공 |
| 팀 | 백엔드 2명 (각자 AI로 코딩) — 분업은 8장 |
| MVP 범위 | ① 회원/로그인 ② 홈 ③ 캘린더·일정·할 일 ④ 시간표 ⑤ 학사관리(이수현황) ⑥ 마이페이지 |
| 나중에 | 카카오 로그인·카톡 알림, AI 추천, 졸업 시뮬레이션, 마이크로디그리, e캠퍼스 연동, STT |

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
| **홈** | "안녕하세요 (닉네임)님", 주간일정(요일·날짜·카테고리 점), 오늘일정 타임라인(시간·제목·장소·카테고리), AI 오르도 추천 카드, 졸업 진행률(78%, 102/130, 남은 학점), 오늘 할 일 | `GET /api/home` |
| 캘린더 | 연/월 선택, 오늘 버튼, 월 달력, +일정추가, 날짜 클릭 → 그날 일정 상세·추가·삭제 | `GET/POST/PATCH/DELETE /api/schedules` |
| 일정(할 일) 추가 폼 | 제목, 카테고리, 날짜, 시작~종료 시간, 알람 | `POST /api/schedules` |
| 시간표 | 학기 선택(2026년 2학기), 시간표 편집, 월~금 09:00~19:00 격자 | `/api/timetables` |
| 학사관리 | 학점 취득 현황(총 18/130, 13.8%, 영역 그룹별 도넛), 졸업까지 필요한 학점, 영역별 이수현황(필수교양·전공기초·전공필수·전공선택·기타·전체), 마이크로디그리(데이터 없음·보류) | `GET /api/academic/progress`, `/api/academic/completed-courses` |
| 마이페이지 | 프로필(이름·학과·입학년도/학년·학번·이메일·연락처·사진), 정보 수정, 이번 학기 수강과목 수·과제 수, 학점 현황, 알림 on/off, 로그아웃 | `/api/users/me`, `/api/users/me/summary`, `/api/auth/logout` |

---

## 2. 기술 규칙 (모든 코드 공통)

### 2.1 패키지 구조 — 도메인별로 나눈다

```
com.ordo
├── OrdoApplication.java
├── global
│   ├── config      SecurityConfig, JpaConfig(@EnableJpaAuditing), WebConfig(CORS, ArgumentResolver), OpenApiConfig
│   ├── common      ApiResponse, BaseTimeEntity, HealthController, AcademicTerm(학기 유틸)
│   ├── error       ErrorCode, BusinessException, GlobalExceptionHandler
│   └── security    JwtProvider, JwtAuthenticationFilter, LoginUser(애노테이션), LoginUserArgumentResolver
├── auth            회원가입·로그인·토큰
├── user            내 정보·설정·마이페이지 요약
├── catalog         단과대·전공·졸업요건·교과목 (읽기 전용)
├── academic        이수내역 CRUD, 이수현황 계산
├── schedule        일정·할 일
├── timetable       시간표
└── home            홈 화면 집계
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
| users | 사용자 | email 유일, BCrypt password, name, nickname, student_number, phone, admission_year, major_id, current_semester, notification_enabled |
| refresh_tokens | 인증 | token 유일, 만료일. 로그아웃·재발급 시 삭제 |
| schedules | 사용자 | schedule_date, start_time/end_time(NULL 가능), category, done, alarm_minutes_before |
| timetable_entries | 사용자 | academic_year + term, day_of_week(1=월…7=일), start_time~end_time |
| completed_courses | 사용자 | 내가 들은 과목: course_code(선택), course_name, credits, classification, distribution_area, grade, academic_year, term |

### 3.3 도메인 규칙

- **일정 vs 할 일**: 같은 `schedules` 테이블. `start_time` 이 있으면 "일정"(홈 타임라인·캘린더), 없으면 "할 일"(홈 '오늘 할 일' 체크박스). `end_time` 이 있으면 `start_time` 필수, `end_time > start_time`.
- **알람**: `alarm_minutes_before` 값은 `null`(없음), 10, 30, 60, 1440(하루 전) 중 하나. MVP는 **저장만** 하고 실제 발송은 2차(카톡).
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
| PATCH | /api/users/me/settings | 🔒 `{ "notificationEnabled": false }` |
| GET | /api/users/me/summary | 🔒 마이페이지 요약 |

- PATCH 두 API의 응답 data 는 `GET /api/users/me` 와 같은 형태(수정 후 값).
- 보낸 필드만 변경: 생략하거나 null 이면 그대로 둔다(값을 비우는 기능은 없음).
- `majorId`·`admissionYear` 를 바꾸면 가입과 같은 규칙으로 확인 → 안 맞으면 `MAJOR_NOT_FOUND`.

```json
// GET /api/users/me
{ "id": 1, "email": "haeun@khu.ac.kr", "name": "이하은", "nickname": "하은",
  "studentNumber": "2026105632", "phone": "010-1234-5678", "profileImageUrl": null,
  "admissionYear": 2026, "currentSemester": 1, "grade": 1,
  "major": { "id": 35, "displayName": "시각디자인학과", "collegeName": "예술·디자인대학" },
  "notificationEnabled": true }

// GET /api/users/me/summary
{ "term": { "year": 2026, "term": "SECOND", "label": "2026년 2학기" },
  "courseCount": 6,          // 이번 학기 시간표의 서로 다른 과목명 수
  "pendingAssignmentCount": 3, // 오늘 이후 done=false 인 ASSIGNMENT 일정 수
  "credits": { "earned": 18, "required": 130, "percent": 13.8 } }   // 학사 프로필 미완성이면 credits = null
```

### 4.3 학사 기준정보 `catalog` (읽기 전용, 인증 불필요)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/catalog/colleges | 단과대 목록 |
| GET | /api/catalog/majors?admissionYear=2026&collegeId= | 해당 학번에 존재하는 전공 목록(융합전공 제외) |
| GET | /api/catalog/requirements?majorId=&admissionYear= | 졸업요건 원본 |
| GET | /api/catalog/courses?majorId=&q=&classification=&page=0&size=20 | 교과목 검색 (majorId → course_unit 으로 필터, q는 과목명/학수번호 부분일치) |
| GET | /api/catalog/general-education?admissionYear= | 교양 기본구조 + 필수과목 목록 (없으면 2026, approximate=true) |

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

### 4.5 시간표 `timetable`

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/timetables?year=2026&term=SECOND | 🔒 해당 학기 칸 목록 (year/term 생략 시 현재 학기) |
| POST | /api/timetables/entries | 🔒 수업 칸 추가 → 201, 겹치면 409 |
| PATCH | /api/timetables/entries/{id} | 🔒 |
| DELETE | /api/timetables/entries/{id} | 🔒 → 204 |

```json
// POST /api/timetables/entries
{ "year": 2026, "term": "SECOND", "courseName": "타이포그래피 I", "courseCode": null,
  "dayOfWeek": 2, "startTime": "09:00", "endTime": "10:30",
  "location": "예405", "professor": "김교수", "color": "#3BB39A" }
// GET 응답 data
{ "year": 2026, "term": "SECOND", "label": "2026년 2학기",
  "entries": [ { "id": 3, "courseName": "타이포그래피 I", "dayOfWeek": 2, "startTime": "09:00",
                 "endTime": "10:30", "location": "예405", "professor": "김교수", "color": "#3BB39A" } ] }
```

### 4.6 학사관리 `academic`

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/academic/completed-courses | 🔒 내 이수내역 (학년도·학기 순) |
| POST | /api/academic/completed-courses | 🔒 1건 등록 |
| POST | /api/academic/completed-courses/bulk | 🔒 여러 건 등록 `{ "courses": [ ...위와 같은 객체 ] }` |
| PATCH | /api/academic/completed-courses/{id} | 🔒 |
| DELETE | /api/academic/completed-courses/{id} | 🔒 → 204 |
| GET | /api/academic/progress | 🔒 **이수현황 계산 결과** (학사관리 화면·홈·마이페이지의 원천) |

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
  "timeline": [                                  // 오늘 일정: 시간표 수업 + 시간 있는 일정, 시작시간 순
    { "source": "TIMETABLE", "id": 3,  "title": "타이포그래피 I", "category": "LECTURE",
      "startTime": "09:00", "endTime": "10:30", "location": "예405" },
    { "source": "SCHEDULE",  "id": 51, "title": "세시 팀플 모임", "category": "PERSONAL",
      "startTime": "12:00", "endTime": null, "location": null }
  ],
  "todos": [ { "id": 60, "title": "자료 조사", "category": "ASSIGNMENT", "done": false } ],  // 오늘 날짜의 시간 없는 일정
  "graduation": { "earned": 102, "required": 130, "percent": 78.4, "remaining": 28 },          // 프로필 미완성이면 null
  "recommendation": { "message": "오늘은 디자인씽킹 수업 과제가 마감 2일 전이에요!", "scheduleId": 42 }  // 없으면 null
}
```

- `week.categories`: 그날의 일정 카테고리(중복 제거) + 그 요일에 시간표 수업이 있으면 `LECTURE`.
- `recommendation`(MVP는 규칙 기반): 오늘~7일 이내, `done=false` 인 `ASSIGNMENT` 중 가장 마감이 가까운 것 → `"오늘은 {제목} 과제가 마감 {D}일 전이에요!"` (D=0이면 "오늘 마감이에요!"). 없으면 null. **3차에 LLM으로 교체**.

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

**반드시 단위 테스트할 케이스**: 빈 이수내역 / F 제외 / 재수강 중복 제거 / NULL 요구학점 / 배분 영역 수 부족 / 2025학번 교양 근사(approximate) / 졸업 가능 케이스.

> 다전공·부전공 계산, 전공 초과학점의 전공선택 인정, 타전공 인정 한도는 **MVP 제외**(9장 미결정).
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
- 로컬 DB가 꼬이면: `DROP DATABASE ordo;` 후 서버 재시작 → Flyway가 V1부터 다시 적용.

---

## 8. 분업과 작업 순서

인터페이스(5장의 `AcademicProgressService` 시그니처, 2장의 공통 규칙)만 지키면 서로 기다리지 않고 병행할 수 있다.

| 순서 | 작업 | 담당 | 완료 조건 |
|---|---|---|---|
| T0 ✅ | 공통 기반 (의존성·yml·global 패키지·JWT·CORS·Swagger·Flyway 적용) | 한 명이 먼저, 하루 안에 main에 머지 | 서버 부팅 시 V1·V2 적용, `/api/health` 200, `/swagger-ui/index.html` 열림, 토큰 없이 🔒 API 호출 시 401 JSON |
| T1 ✅ | auth (가입·로그인·재발급·로그아웃) | A | Swagger에서 가입→로그인→🔒API→refresh→logout 흐름 동작 |
| T2 ✅ | user (me 조회·수정·settings) | A (T1과 함께) | |
| T3 | catalog 엔티티 + 조회 API | B | 회원가입에 쓸 전공 목록이 학번별로 나옴. `College`·`Major` 엔티티와 `MajorRepository`(가입 검증용 `findSelectable`)는 T1에서 먼저 생성됨 → 이어서 작업 |
| T4 | schedule CRUD | A | 기간 조회 정렬·권한(남의 일정 404)·시간 검증 |
| T5 | timetable CRUD | A | 겹침 409, 현재 학기 기본값 |
| T6 | completed-courses CRUD + 이수구분 자동판별 | B | CSE204 입력 시 컴공 학생은 MAJOR_REQUIRED 자동 |
| T7 | progress 계산 + 단위 테스트 | B | 5장 테스트 케이스 전부 통과 |
| T8 | home 집계 | A (T7의 `findSummary` 사용) | 4.7 응답 형태 그대로 |
| T9 | users/me/summary | A 또는 B | |

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
| 9 | 알림 실제 발송 | 2차: 카카오 로그인 + 카톡 알림 (알림 시각 계산은 `schedule_date + start_time - alarm_minutes_before`) |
| 10 | 디자인에 남아있는 '과제', '설정' 메뉴 | 회의록 2차대로 삭제 |
| 11 | e캠퍼스 연동 | 공식 API 없음(LearningX). MVP는 과제 수동 입력, 3차에 iCal 내보내기 가능 여부부터 확인. **학교 비밀번호를 서버에 저장하는 방식은 금지** |
