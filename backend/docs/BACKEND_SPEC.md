# ORDO 백엔드 개발 명세서 (AI 작업용)

> **이 문서가 백엔드 구현의 유일한 기준이다.** AI에게 작업을 시킬 때 이 문서와 `AGENTS.md` 를 먼저 읽히고 시작한다.
> `docs/ORDO_ARCHITECTURE.md` 는 초기 개요(구버전)라서, 내용이 다르면 **이 문서가 우선**이다.
> 근거 자료: 회의록.txt(1·2차), 디자인 시안 7장, `경희대_국제캠_2026_졸업관리데이터.xlsx`, `경희대_교육과정기본구조/2020~2026학번`, `오르도_T19복수전공_T20마이크로디그리_최종.xlsx`, `2026 교육과정(전체).pdf`(전공 이수 총칙 p115~118).
> 최종 갱신: 2026-10-31 (T19 복수전공 3.2·3.3·4.3·4.6·5·V14, T20 마이크로디그리 3.2·4.3·4.6·5·6·V17·V18), 2026-10-30 (T15 시간표 온라인 강의 4.5·V12, T16 홈 D-Day 알림 4.7, T17 남은 수강 과목 4.6, T18 학기별 배치 4.6·V13, 시드 과목명·전문실기 소속 교정 6·7·9장 24·V15·V16), 2026-10-21 (T11 웹푸시 알림 4.10·V11, T13 평점·T14 홈 e캠퍼스 과제, 2026-10-08: T12 파일 가져오기 계획 반영, T3 catalog 조회 계약·검증 결과, T4 schedule·T5 timetable·T6·T7 academic·T8 home·T9 summary 세부 규칙, e캠퍼스 연동 추가, AI 추천 보류)

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
| 나중에 | 카카오 로그인, AI 추천, 졸업 시뮬레이션, STT |

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
| **홈** | "안녕하세요 (닉네임)님", 주간일정(요일·날짜·카테고리 점), 오늘일정 타임라인(시간·제목·장소·카테고리), ~~AI 오르도 추천 카드~~(보류, 최종 디자인에도 없음), 졸업 진행률(78%, 102/130, 남은 학점), 오늘 할 일, D-Day 알림(T16) | `GET /api/home` |
| 캘린더 | 연/월 선택, 오늘 버튼, 월 달력, +일정추가, 날짜 클릭 → 그날 일정 상세·추가·삭제, e캠퍼스 과제 마감 표시 | `GET/POST/PATCH/DELETE /api/schedules`, `GET /api/ecampus/events` |
| 일정(할 일) 추가 폼 | 제목, 카테고리, 날짜, 시작~종료 시간, 알람 | `POST /api/schedules` |
| 시간표 | 학기 선택(2026년 2학기), 시간표 편집, 월~금 09:00~19:00 격자, 격자 아래 온라인 강의 줄 | `/api/timetables` |
| 학사관리 | 학점 취득 현황(총 18/130, 13.8%, 영역 그룹별 도넛), 졸업까지 필요한 학점, 영역별 이수현황(필수교양·전공기초·전공필수·전공선택·기타·전체), 복수전공(T19), 마이크로디그리(T20) | `GET /api/academic/progress`, `/api/academic/completed-courses`, `/api/academic/double-major`, `/api/academic/micro-degrees`, `/api/catalog/micro-degrees` |
| 졸업 로드맵 | 학기별 곡선(학기별 평점·학점), 이수한 과목, 남은 수강 과목(T17), 학기별 배치(T18) | `GET /api/academic/gpa`, `/api/academic/completed-courses`, `/api/academic/remaining-courses`, `/api/academic/plans` |
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
| users | 사용자 | email 유일, BCrypt password, name, nickname, student_number, phone, admission_year, major_id, current_semester, notification_enabled, todo_alarm_time(V11, 기본 09:00), ecampus_feed_token(V10, 4.8), double_major_id(복수전공·융합전공, NULL 이면 단일전공, T19) |
| refresh_tokens | 인증 | token 유일, 만료일. 로그아웃·재발급 시 삭제 |
| schedules | 사용자 | schedule_date, start_time/end_time(NULL 가능), category, done, alarm_minutes_before, alarm_sent_for(V11, 알림 중복 방지·내부용) |
| push_subscriptions | 사용자 | 기기(브라우저)별 웹푸시 구독: endpoint(유일), p256dh, auth (V11, 4.10) |
| timetable_entries | 사용자 | academic_year + term, day_of_week(1=월…7=일), start_time~end_time, online(T15, true 면 요일·시간 NULL) |
| planned_courses | 사용자 | 학기별 배치(T18): academic_year + term, course_name, course_code(선택), classification(선택), credits |
| completed_courses | 사용자 | 내가 들은 과목: course_code(선택), course_name, credits, classification, distribution_area, grade, academic_year, term |
| micro_degrees | 기준정보(읽기) | 마이크로디그리 56개(V18): name 유일, scope(국제/공통), host_department, required_course_count(판정 기준), required_credits(표시용), min_departments(2 면 참여학과 2개 조건), selective(선발제), requirement_text, guide_url |
| micro_degree_courses | 기준정보(읽기) | 편성 과목 249행(V18): course_code(NULL 이면 과목명으로 찾음, `courses` 와 FK 없음), alias_code(구 학수번호·오기), department_name, credits(NULL = 자료에서 확인 못함), mandatory, group_key·group_min·group_max |
| user_micro_degrees | 사용자 | 내가 등록한 MD (user_id, micro_degree_id) 유일. 최대 3개. 등록 순서가 과목 배정 순서 |
| user_minors | 사용자 | T21 부전공 추적. user_id 유일, major_id, 생성/수정 시각(V50). 최종 인정 학점이 아니라 수강 후보를 조회한다 |

### 3.3 도메인 규칙

- **일정 vs 할 일**: 같은 `schedules` 테이블. `start_time` 이 있으면 "일정"(홈 타임라인·캘린더), 없으면 "할 일"(홈 '오늘 할 일' 체크박스). `end_time` 이 있으면 `start_time` 필수, `end_time > start_time`.
- **알람**: `alarm_minutes_before` 값은 `null`(없음), 10, 30, 60, 1440(하루 전) 중 하나. 요청에서 **`0` 은 알람 끄기**(null 로 저장, PATCH 의 null 은 '그대로'라서 따로 둠, 2026-10-23). **T11 웹푸시로 발송**(4.10). 시간 없는 할 일은 사용자의 `todo_alarm_time`(기본 09:00) 기준.
- **현재 학기 판별**(`AcademicTerm.of(LocalDate)`): 3~8월 → 그해 FIRST, 9~12월 → 그해 SECOND, 1~2월 → **전년도** SECOND. 계절학기는 사용자가 직접 선택할 때만.
- **학년**: `grade = (current_semester + 1) / 2` (current_semester null이면 null).
- **전공 목록(회원가입)**: 선택한 입학년도에 `graduation_requirements` 행이 있는 전공만, `convergence = false` 만 (융합전공은 주전공 불가).
- **전공 목록(복수전공)**: `includeConvergence=true` 면 융합전공도 함께 준다. 융합전공은 주전공으로 고를 수 없고 복수전공으로만 등록한다(T19).
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
| GET | /api/catalog/majors?admissionYear=2026&collegeId=&includeConvergence=false | 해당 학번에 존재하는 전공 목록. 융합전공은 `includeConvergence=true` 일 때만(복수전공 고르기, T19) |
| GET | /api/catalog/requirements?majorId=&admissionYear= | 졸업요건 원본 |
| GET | /api/catalog/courses?majorId=&q=&classification=&page=0&size=20 | 교과목 검색 (majorId → course_unit 으로 필터, q는 과목명/학수번호 부분일치) |
| GET | /api/catalog/general-education?admissionYear= | 교양 기본구조 + 필수과목 목록 (없으면 2026, approximate=true) |
| GET | /api/catalog/micro-degrees | 마이크로디그리 전체 + 편성 과목, 이름 → id 순 (T20) |
| GET | /api/catalog/minors?admissionYear=2026&collegeId= | 부전공 총학점이 확인된 전공·요구 학점 목록 (T21, 융합전공도 총학점이 있으면 포함) |

**T3 조회 계약**

- 모두 로그인 없이 호출 가능하며 `ApiResponse<T>` 로 감싼다. 아래는 `data` 의 형태이다.
- `admissionYear`·`majorId` 는 해당 API에서 필수, 양수. `collegeId` 는 선택, 지정하면 양수.
- 단과대는 이름→id, 전공은 표시명→id 오름차순. 전공은 **해당 입학년도의 졸업요건 행이 있고 융합전공이 아닌 경우만** 반환한다(`includeConvergence=true` 면 융합전공도 포함). 일치하는 전공이 없으면 성공 + 빈 배열.
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

원본의 null은 그대로 반환한다. **T7 계산에서의 NULL→0 처리와 다르며**, 이 API는 졸업 가능 판정·이수학점 계산을 하지 않는다. 복수전공 진행 상황과 부전공 수강 후보는 4.6의 별도 API를 쓴다.

교양 기준 필드:

- `admissionYear`: 요청한 입학년도, `basisYear`: 실제 사용한 기준년도.
- `approximate`: 요청 연도가 없어 2026으로 대체했으면 true.
- `requiredCredits, distributionCredits, distributionMinAreas, freeCredits, totalCredits`.
- `requiredCourses`: `[{ id, groupName, courseName, credits, recommendedGrade, note }]`, 실제 사용한 기준년도에 속하는 목록만 id 순으로 반환. `groupName, recommendedGrade, note` 는 null일 수 있다.

프론트 호출 예시·독립 테스트 실행법은 `docs/FRONTEND_GUIDE.md` 4장 catalog 및 7장 참고.

**마이크로디그리 목록 (T20)**

```json
// GET /api/catalog/micro-degrees 응답 data 의 원소
{ "id": 22, "name": "로컬브랜드매니지먼트", "scope": "국제", "collegeName": "예술·디자인대학", "hostDepartment": "시각디자인학과",
  "requiredCourseCount": 3, "requiredCredits": 9, "minDepartments": 2, "selective": false,
  "requirementText": "3과목 이수(총9학점)", "guideUrl": "https://sdl.khu.ac.kr/bbs/board.php?bo_table=faq_global",
  "courses": [ { "courseCode": "VID3038", "courseName": "아이덴티티디자인", "departmentName": "시각디자인학과", "credits": 3,
                 "mandatory": false, "groupKey": null, "groupMin": null, "groupMax": null } ] }
```

- 페이지 나눔 없음(약 70KB). 입학년도 제한이 없어 파라미터도 없다(운영지침 제4조). `credits` null 은 자료에서 학점을 확인 못한 과목, `courseCode` null 은 학수번호 미표기 과목(타대학 등)이다.

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
| GET | /api/academic/remaining-courses | 🔒 남은 수강 과목 (졸업 로드맵, T17) |
| GET | /api/academic/plans | 🔒 학기별 배치 조회 (졸업 로드맵, T18) |
| PUT | /api/academic/plans | 🔒 학기별 배치 **통째로 교체**, 빈 목록 = 초기화 (T18) |
| GET | /api/academic/double-major | 🔒 복수전공 진행 상황, 없으면 `data: null` (학사관리, T19) |
| PUT | /api/academic/double-major | 🔒 복수전공 등록·변경 (T19) |
| DELETE | /api/academic/double-major | 🔒 복수전공 해제 → 204 (T19) |
| GET | /api/academic/micro-degrees | 🔒 내 마이크로디그리와 진행 상황, 등록순 (학사관리, T20) |
| POST | /api/academic/micro-degrees | 🔒 등록 `{ "microDegreeId": 22 }` → 201, 내 목록 전체 (T20) |
| DELETE | /api/academic/micro-degrees/{microDegreeId} | 🔒 해제 → 204 (T20) |
| GET | /api/academic/minor | 🔒 부전공 요구 학점·이수/미이수 후보, 미등록이면 data:null (T21) |
| PUT | /api/academic/minor | 🔒 `{majorId:14}` 부전공 등록/변경, GET과 같은 응답 (T21) |
| DELETE | /api/academic/minor | 🔒 부전공 추적 해제, 등록이 없어도 204 (T21) |

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

```json
// GET /api/academic/remaining-courses 응답 data (T17)
{
  "remainingCredits": 28,          // 졸업까지 남은 학점 = progress 의 total.remaining (courses 학점의 합이 아님)
  "majorCoursesAvailable": true,   // false 면 전공 교과목 자료(course_unit)가 없는 전공이 있어 그 전공 과목이 빠진다
  "courses": [
    { "courseName": "주제연구", "courseCode": null, "classification": "GEN_REQUIRED", "credits": 3,
      "targetGrade": "2", "openSemester": null, "unitName": null },
    { "courseName": "디지털디자인", "courseCode": "FD105", "classification": "MAJOR_BASIC", "credits": 3,
      "targetGrade": "1", "openSemester": "1,2", "unitName": "시각디자인학과" }
  ]
}
```

- 원천: **필수교양** = `progress` 의 `requiredGeneralCourses` 에서 `done: false` 인 과목(판정 규칙도 5장 9번 그대로). **전공** = `courses` 마스터에서 `unit_name = 내 전공 course_unit` 이고 전공기초·전공필수·전공선택인 과목 중 안 들은 과목. 교직·배분이수·자유이수는 정해진 과목이 없어 넣지 않는다.
- 전공 과목 "들었다" 판정(F·NP 기록은 안 들은 것, 성적 미정은 들은 것): ① 학수번호가 같다 ② 학수번호 없이 저장한 기록의 과목명이 공백을 빼고 같다. 학수번호가 다르면 이름이 같아도 다른 과목으로 본다.
- 정렬: 이수구분(필수교양 → 전공기초 → 전공필수 → 전공선택) → `targetGrade`(없으면 맨 뒤) → 과목명. 페이지 나눔 없음(학과당 최대 약 130과목), 이수구분 필터는 프론트에서 한다.
- `targetGrade`: "1", "3-4", 교양은 "전학년"도 있다. `openSemester`: "1", "2", "1,2". 없으면 null. `credits` 0 인 과목도 있다(졸업논문 등 0학점 전공필수).
- 교과목 마스터는 **2026 자료뿐**이라 다른 학번도 2026 교육과정으로 나온다. 필수교양은 `approximate` 면 2026 기준. 학사 프로필이 없으면 `PROFILE_INCOMPLETE`, 졸업요건이 없으면 `REQUIREMENT_NOT_FOUND`(`progress` 와 같음).
- 복수전공이 있으면(T19) 제2전공 과목도 넣는다. 같은 학수번호는 제1전공 쪽 1행만 남기고, `unitName` 으로 어느 전공 과목인지 구분한다(필수교양은 null).

```json
// PUT /api/academic/plans (T18)
{ "courses": [
    { "year": 2027, "term": "FIRST", "courseName": "성찰과표현", "courseCode": null, "classification": "GEN_REQUIRED", "credits": 3 },
    { "year": 2027, "term": "FIRST", "courseName": "타이포그래피1", "courseCode": "VID1004", "classification": "MAJOR_ELECTIVE", "credits": 3 }
] }
// GET·PUT 응답 data
{ "totalCredits": 6,
  "terms": [ { "year": 2027, "term": "FIRST", "label": "2027년 1학기", "credits": 6,
               "courses": [ { "id": 7, "courseName": "성찰과표현", "courseCode": null, "classification": "GEN_REQUIRED", "credits": 3 },
                            { "id": 8, "courseName": "타이포그래피1", "courseCode": "VID1004", "classification": "MAJOR_ELECTIVE", "credits": 3 } ] } ] }
```

- PUT 은 내 배치 전체를 보낸 목록으로 바꾼다(개별 이동 API 없음). 저장할 때마다 `id` 가 새로 나온다. `courses: []` = 초기화, `courses` 자체가 없으면 400.
- 입력: `courses` 최대 100개, `year` 2000~2100, `term` 필수, `courseName` 필수 100자, `courseCode` 20자(빈 값은 null), `classification` 선택(직접 추가한 과목), `credits` 필수 **0~30**(0 은 T17 의 0학점 졸업논문 때문). 앞뒤 공백 제거.
- 같은 학기에 같은 과목(학수번호, 없으면 공백 뺀 과목명)이 두 번이면 `INVALID_INPUT` 이고 아무것도 바꾸지 않는다. 다른 학기에는 다시 넣을 수 있다.
- 응답은 **과목이 있는 학기만** 시간 순(학년도 → 1학기·여름·2학기·겨울), 학기 안은 보낸 순서. 학기 칸은 제한하지 않고, 화면의 빈 학기 칸은 프론트가 만든다.
- 계획일 뿐이라 이수현황·평점·남은 수강 과목에 영향이 없다. 배치한 과목을 남은 과목 카드에서 뺄지는 프론트가 정한다. 졸업 시뮬레이션은 이번 범위가 아니다(`calculate` 로 나중에 붙일 수 있음).

```json
// PUT /api/academic/double-major (T19)
{ "majorId": 12 }   // GET /api/catalog/majors?admissionYear=&includeConvergence=true 의 id
// GET·PUT 응답 data (복수전공이 없으면 GET 은 data: null)
{ "major": { "id": 12, "displayName": "산업디자인학과" },
  "majorCoursesAvailable": true,           // false 면 교과목 자료가 없는 전공: courses 가 비고 earned 는 0
  "total": { "earned": 6, "required": 42 },
  "groups": [ { "key": "MAJOR_BASIC", "label": "전공기초", "earned": 6, "required": 9 },
              { "key": "MAJOR_REQUIRED", "label": "전공필수", "earned": 0, "required": 12 },
              { "key": "MAJOR_ELECTIVE", "label": "전공선택", "earned": 0, "required": 21 } ],
  "courses": [ { "id": 3, "courseName": "드로잉", "courseCode": "FD101", "classification": "MAJOR_BASIC",
                 "credits": 3, "grade": "A+" } ] }
```

- 계산 규칙은 5장 12번. `groups`·`courses` 의 이수구분은 성적표가 아니라 복수전공 학과 마스터 기준이다.
- 등록하면 `progress`·`home`·`summary` 의 숫자가 바뀐다(제1전공 요건이 다전공 기준으로). `progress` 응답 모양은 그대로다.
- 오류: 입학년도·주전공 없음 `PROFILE_INCOMPLETE`, 주전공과 같음 `INVALID_INPUT`, 없는 전공·내 입학년도에 요건이 없는 전공 `MAJOR_NOT_FOUND`. `majorId` 누락·0 이하는 400.
- `PATCH /api/users/me` 로 주전공을 복수전공과 같게 하거나 복수전공 요건이 없는 입학년도로 바꾸면 `INVALID_INPUT`(복수전공을 먼저 해제). 그래서 복수전공 요건 행은 항상 있다.
- 복수전공 1개만 저장한다(학교는 2개까지 승인). 오르도는 신청 창구가 아니라 승인받은 결과를 입력받으므로 다전공 금지 조합은 검사하지 않는다. `DELETE` 는 복수전공이 없어도 204.

```json
// GET /api/academic/micro-degrees 응답 data, POST 도 같은 모양(T20)
[ { "id": 22, "name": "로컬브랜드매니지먼트", "hostDepartment": "시각디자인학과", "selective": false,
    "guideUrl": "https://sdl.khu.ac.kr/bbs/board.php?bo_table=faq_global",
    "earned": 9, "required": 9, "earnedCourses": 3, "requiredCourses": 3,
    "completed": false, "unmet": ["DEPARTMENT_COUNT"],      // 비어 있으면 completed
    "period": "2026. 2학기 ~ 2027. 1학기",                  // 센 과목의 첫~마지막 학기, 없으면 null
    "courses": [ { "id": 7, "courseName": "아이덴티티디자인", "courseCode": "VID3038", "credits": 3, "grade": "A0", "counted": true } ] } ]
```

- 판정 규칙은 5장 끝의 "마이크로디그리 판정". `courses` 는 이 MD 과목 중 내가 들은 과목이고, `counted: false` 면 그룹 한도를 넘었거나 먼저 등록한 MD 에 배정된 과목이다.
- `unmet`: `REQUIRED_COURSE`(필수 과목 미이수), `COURSE_COUNT`(과목 수 부족), `GROUP_MINIMUM`(선택그룹 최소 미달), `DEPARTMENT_COUNT`(참여학과 2개 미달).
- 오류: 없는 MD 나 등록 안 한 MD 해제 `NOT_FOUND`, 이미 등록했거나 3개를 넘으면 `INVALID_INPUT`. `microDegreeId` 누락·0 이하는 400.
- 등록은 사용자 행을 쓰기 잠금으로 읽은 뒤 개수·중복 검사와 저장을 같은 트랜잭션에서 처리한다. 동시 등록 요청도 최대 3개·중복 금지를 지킨다.
- 오르도는 신청 창구가 아니다. 신청은 학교 포털에서 하고, 선발제(`selective`) 2개는 신청해도 선발돼야 이수할 수 있다. 신청 자격(2학기 이상)은 검사하지 않는다. 입학년도·전공이 없어도 쓸 수 있다.

#### 부전공 후보 조회 (T21)

계약과 계산 세부 기준은 `docs/plans/T21-minor.md`를 따른다. 교육과정 p115·116·118에 따라 부전공을 등록해도 주전공 요건은 줄어들지 않으며, 단일/다전공과 부전공 과목은 중복 인정하지 않는다.

- 공개 목록 응답은 `id,displayName,collegeId,collegeName,requiredCredits,electiveCredits,totalCredits,majorCoursesAvailable`. 입학년도별 `minor_total_credits > 0`인 전공만 이름/id 순으로 반환하며 NULL 영역 학점은 유지한다.
- 등록 응답은 `major:{id,displayName},admissionYear,catalogYear:2026,requirements:{requiredCredits,electiveCredits,totalCredits},majorCoursesAvailable,overlapCheckAvailable,candidateCredits,completedCourses,remainingCourses,reviewRequired:true`.
- `completedCourses`는 `id,courseName,courseCode,classification,credits,grade,status`, `remainingCourses`는 `courseName,courseCode,classification,credits,targetGrade,openSemester,status`다.
- 상태는 CANDIDATE / PRIMARY_MAJOR_OVERLAP / DOUBLE_MAJOR_OVERLAP / OVERLAP_UNVERIFIED. 주전공/다전공 목록과 겹치거나 그 목록이 없어 중복을 확인할 수 없으면 후보 학점에 넣지 않는다.
- 후보는 해당 전공 마스터의 전공필수·전공선택. 이수구분은 마스터 기준, 학점은 내 기록 기준이다. F·NP 제외/재수강은 기존 이수현황 정책을 쓰며, 학수번호 없는 수동 기록만 공백 제거한 이름으로 매칭한다. 같은 마스터 과목이 코드/이름 기록에 함께 있으면 최신 인정 기록 1건만 센다.
- 학과별 부전공 지정 과목이 별도 데이터로 없어 **최종 이수 판정은 하지 않는다**. `candidateCredits`를 공식 인정 학점으로 표시하면 안 된다. `reviewRequired`는 항상 true이고, 최종 `completed`/`earned` 필드는 없다. 옛 학번도 과목 목록은 2026 기준이다.
- 주전공/복수전공과 같은 전공은 INVALID_INPUT. 없는 전공은 MAJOR_NOT_FOUND, 해당 학번 부전공 총학점 미확보는 REQUIREMENT_NOT_FOUND. 현재 부전공과 충돌하는 주전공/학번/복수전공 변경도 INVALID_INPUT으로 거절한다.
- 관련 쓰기는 사용자 행 잠금으로 직렬화한다. 기존 progress/gpa/home/summary 및 2026학번 부가요건 MANUAL 체크는 유지한다.

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
  "deadlines": [                               // T16 D-Day 알림: 7일 안, 가까운 순 최대 5개
    { "source": "SCHEDULE", "id": 12, "title": "팀플 모임", "category": "PERSONAL", "date": "2026-10-16",
      "time": "10:00", "dday": 0, "courseName": null, "url": null },
    { "source": "ECAMPUS", "id": null, "title": "3주차 과제 제출", "category": "ASSIGNMENT", "date": "2026-10-18",
      "time": "23:59", "dday": 2, "courseName": "디자인씽킹", "url": "https://khcanvas.khu.ac.kr/..." }
  ],
  "graduation": { "earned": 102, "required": 130, "percent": 78.4, "remaining": 28 }           // 프로필 미완성이면 null
}
```

- `week`: `date` 가 속한 주 **월~일**(최종 디자인). `today` 는 `date`(생략 시 오늘)와 같은 날. 학기는 날짜마다 `AcademicTerm.of` 로 본다.
- `week.categories`: 그날의 일정 카테고리(중복 제거) + 그 요일에 시간표 수업이 있으면 `LECTURE` + **e캠퍼스 과제 마감(4.8)이 있으면 `ASSIGNMENT`**. 순서는 LECTURE·ASSIGNMENT·PERSONAL. e캠퍼스를 못 받아 오면 그 점만 빠지고 홈은 정상 응답.
- `timeline`: 시작시간 순, 같은 시각이면 **시간표 수업 → 직접 등록한 일정 → e캠퍼스** 순. `todos`: 오늘 날짜의 시간 없는 일정(완료한 것도 `done: true` 로 포함). e캠퍼스 마감은 제출 여부를 몰라 할 일에는 넣지 않는다.
- (T14) `source: "ECAMPUS"`: `date` 날짜에 마감인 e캠퍼스 과제(4.8). `startTime` = 마감 시각(시각 없는 하루 종일 마감은 **23:59**), `id`·`endTime`·`location` 은 null, `category` 는 항상 `ASSIGNMENT`. `courseName`·`url` 은 ECAMPUS 에만 값이 있고 나머지 항목은 null. e캠퍼스 요청은 주간 점과 합쳐 **홈 1번당 1번**. 미연동이거나 못 받아 오면 ECAMPUS 항목과 점만 빠지고 홈은 정상 응답. 제출한 과제도 뜨고(피드에 제출 여부 없음), 사용자가 같은 과제를 직접 등록했으면 두 번 보일 수 있다(제목 비교로 거르지 않음).
- (T16) `deadlines`: `date`(생략 시 오늘)부터 **7일 뒤까지**의 완료 안 한 직접 등록 일정 중 `ASSIGNMENT`·`PERSONAL`(수업 `LECTURE` 제외) + e캠퍼스 과제 마감(4.8). 날짜 → 시각(시간 없는 일정은 그날 맨 뒤) → 일정·e캠퍼스 순, **최대 5개**. `dday` = `date` 부터 며칠 남았는지(0 = 그날). `time` 은 시간 없는 일정이면 null, 시각 없는 e캠퍼스 마감은 23:59. 실제 오늘 날짜의 항목은 **이미 지난 시각이면 뺀다**(시간 없는 일정은 남김). e캠퍼스 요청은 주간 점·timeline 과 합쳐 1번(조회 범위 = 그 주 월요일~`date`+7일). 미연동이거나 못 받아 오면 e캠퍼스 항목만 빠진다. 제출한 과제도 뜨고, 같은 과제를 직접 등록했으면 두 번 보일 수 있다(T14 와 같음).
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
6. 요구 학점: 전공 영역은 단일전공 컬럼(`basic_credits`, `required_credits`, `elective_credits`), **NULL은 0으로 취급하고 응답 `required`는 0**. 교양은 3번 기준. 복수전공이 있으면 다전공 컬럼(12번).
7. `total.earned` = 인정 과목 학점 합, `total.required` = `total_credits`, `remaining = max(0, required - earned)`, `percent = 소수 첫째 자리 버림(earned / required * 100)`.
8. `groups`: 전공 = 세 전공영역 합 vs `major_total_credits`, 교양 = 세 교양영역 합 vs 교양 total, 기타 = 기타 합 vs `max(0, total_credits - major_total_credits - 교양 total)`.
9. `requiredGeneralCourses`: 교양 필수과목 이름과 `GEN_REQUIRED` 이수 과목명을 공백 제거 후 비교해 `done`.
10. `graduatable` = 총학점 충족 AND 모든 영역 `earned >= required` AND 배분 영역 수 충족 AND 필수교양 과목 전부 done. 복수전공이 있으면 제2전공 영역·합계 충족도 필요(12번).
11. `checks`: 2026 부가요건 텍스트가 있는 항목만 `status: "MANUAL"` 로 안내(자동 판정하지 않음). 2026학년도 입학자부터 `다전공·부전공·마이크로디그리` 1개 이상 항목을 더한다(교육과정 p115). 부전공을 판정할 수 없어 다른 항목처럼 `graduatable` 에는 넣지 않는다.
12. **복수전공**(T19, `users.double_major_id`, 교육과정 p115~116). 없으면 1~11 그대로이고, 있으면:
    - 요건: 제1전공은 `double_basic/required/elective/total_credits`(6·8번의 단일 컬럼 대신, NULL 은 0). 제2전공도 그 학과의 같은 컬럼.
    - 제2전공 소속: 학수번호가 제2전공 `courses`(전공기초·필수·선택)에 있음. 학수번호 없는 기록만 공백 뺀 과목명으로 비교한다. 이름만 같고 학수번호가 다르면 소속이 아니다(p115).
    - 제2전공 학점: 소속 과목의 내 학점을 **그 학과 마스터의 이수구분**으로 합산한다. 성적표가 일반선택이어도 넣는다.
    - 제1전공 학점: 성적표가 전공기초·필수·선택이면서 제2전공에만 있는 과목은 전공 영역에서 빼고 `기타`로 옮긴다. 양쪽에 있으면 양쪽 다 인정하고, 어느 쪽에도 없으면 지금처럼 성적표 이수구분으로 센다. 제1전공 `course_unit` 이 없으면 제1전공 과목을 알 수 없어 제2전공 소속 과목은 제2전공에만 있는 것으로 본다.
    - `total`: 과목당 1번(7번 그대로, p116).

**T7 세부 규칙** (구현하며 정한 것)

- 성적 미정(`grade` null)은 인정한다(F·NP 만 제외). 4번은 적힌 순서대로 F·NP 를 먼저 빼고 재수강을 고른다 → 예전에 통과하고 재수강에서 F 면 예전 학점이 남는다(학과 시행세칙 확인 후 조정, 코드에 `ponytail:` 주석).
- 재수강 판별 키: 학수번호, 없으면 공백을 뺀 과목명. 같은 학기면 나중에 등록한 것.
- `areas` 의 `OTHER` 는 `required: null`, `areaCount`·`requiredAreaCount` 는 배분이수에만 있다. 요구 학점이 0 이면 `percent` 는 0.0.
- `checks` 이름: SW기초교육 / 영어강의 / 졸업논문 / TOPIK(외국인) / 졸업능력인증 (값이 있는 것만) / 다전공·부전공·마이크로디그리 (2026학번부터).
- 계산은 DB 조회 없는 정적 함수 `AcademicProgressService.calculate(...)` 로 분리 → 졸업 시뮬레이션은 계획 과목을 더해 그대로 부르면 된다.

**반드시 단위 테스트할 케이스**: 빈 이수내역 / F 제외 / 재수강 중복 제거 / NULL 요구학점 / 배분 영역 수 부족 / 2025학번 교양 근사(approximate) / 졸업 가능 케이스. 복수전공: 제1전공 요건 교체 / 공통 과목 양쪽 합산·총학점 1번 / 제2전공만 소속 과목 기타 이동 / 마스터 이수구분 / 제2전공 미충족이면 졸업 불가.

> 부전공은 T21의 등록·요건·수강 후보 조회를 지원한다. 학과별 부전공 지정 과목 자료가 없어 최종 이수 판정은 하지 않는다. 전공 초과학점의 전공선택 인정, 타전공 인정 한도는 미반영이다.

### 마이크로디그리 판정 (`MicroDegreeCalculation`, T20)

운영지침(2026.5.8 시행)과 교육과정 p25·26 기준. 이수현황 계산(요건·학점)은 바꾸지 않는다(제5조④: MD 과목은 전공·교양 학점으로 그대로 인정).

1. 인정 과목은 이수현황과 같다(F·NP 제외, 재수강 최근 1건). 신청 전 이수도 인정(제5조③). 이수구분은 보지 않는다.
2. 과목 찾기: `course_code` 또는 `alias_code` 가 같은 기록. 학수번호 없는 기록은 공백 뺀 과목명으로 비교한다. `course_code` 가 없는 MD 과목(타대학 등 4행)은 기록의 학수번호와 상관없이 과목명으로 비교한다. 이름만 같고 학수번호가 다른 기록은 아니다.
3. MD 간 중복(제5조⑤): 같은 과목은 **먼저 등록한 MD 에서 센 것**으로 끝난다. 뒤 MD 에 더 필요해도 넘겨주지 않는다. 앞 MD 에서 세지 않은 과목은 뒤 MD 에서 셀 수 있다.
4. 세기: 선택그룹은 `group_max` 까지만 센다. 넘는 과목은 `counted: false` 일 뿐 **미충족 사유가 아니다.**
5. `completed` = 필수 과목 전부 센다 AND 센 과목 수 ≥ `required_course_count` AND 그룹마다 센 과목 수 ≥ `group_min` AND 센 과목의 `department_name` 종류 ≥ `min_departments`.
6. `earned` = 센 과목의 **내 이수 기록 학점** 합, `required_credits` 상한. `earnedCourses` 도 `required_course_count` 상한. 판정은 과목 수 기준이다(예술체험심화 11~12학점 때문).
7. `period`: 센 과목의 첫 학기 ~ 마지막 학기(학교에 고정 기간이 없음, 제4조⑥). 같으면 학기 1개, 없으면 null.

**반드시 단위 테스트할 케이스**: 필수 미이수 / 과목 수 / 그룹 상한(초과가 미충족이 아님) / 그룹 하한 / 2개 학과 / 별칭·과목명 찾기 / MD 간 중복 / 학점 상한 / 기간 / F·재수강.

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
| 교과목 3,036 (V16 후 3,060) | 졸업관리데이터 ⑤ | 2026 전공 교과목만. 교양 과목 목록 없음 → 교양은 사용자가 이수구분 직접 선택 |
| 마이크로디그리 56 + 편성 과목 249 (V18) | `오르도_T19복수전공_T20마이크로디그리_최종.xlsx` MD목록·MD과목 | `tools/seed/generate_micro_degree_seed.py` 로 생성. 57행 중 타대학 융합 묶음 제외, 학점 추정 27행은 NULL, 구 학수번호·오기 12행은 `alias_code` |

알려진 한계: 교과목은 2026 편성 기준이라 옛 학번 학생은 자동 판별이 틀릴 수 있음(수정 가능하게 함). `course_unit` 이 NULL인 전공 2개('글로벌한국학과', '스마트팜공학 융합전공')는 자동 판별 불가. 깨진 과목명은 V15, 전문실기 소속은 V16 에서 교정(9장 24). 전문실기는 종목을 나누지 않고 학수번호당 1행이다. 개설학기 미표기 18건.

---

## 7. Flyway 규칙

- V1(스키마), V2(시드)는 공동 소유. **이미 적용된 파일은 절대 수정 금지** — 바꿀 게 있으면 새 파일.
- 새 마이그레이션 번호: **A 담당 V10~V49, B 담당 V50~V89**. 파일명 `V{번호}__{영문_설명}.sql`.
- 사용 중: `V50__add_user_minors.sql`(B, T21: 부전공 추적 `user_minors`, 2026-10-31 추가 개발 요청). 기존 Flyway/기준정보 파일은 수정하지 않는다.
- 사용 중: `V10__add_ecampus_feed_token.sql`(A, users 에 e캠퍼스 피드 토큰 칸 추가).
- 사용 중: `V11__add_web_push.sql`(A, T11: `push_subscriptions` 새 테이블, `schedules.alarm_sent_for`·`idx_schedules_date`, `users.todo_alarm_time`, 2026-10-21 승인).
- 사용 중: `V12__add_timetable_online.sql`(A, T15: `timetable_entries` 요일·시간 NULL 허용, `online` 추가, 2026-10-30 승인).
- 사용 중: `V13__add_planned_courses.sql`(A, T18: `planned_courses` 새 테이블, 2026-10-30 승인).
- 사용 중: `V14__add_user_double_major.sql`(A, T19: `users.double_major_id`, 2026-10-31 승인). `out-of-order: true` 라 V15·V16 뒤에 들어와도 적용된다.
- 사용 중: `V17__add_micro_degrees.sql`(A, T20: `micro_degrees`·`micro_degree_courses`·`user_micro_degrees` 새 테이블, 2026-10-31 승인), `V18__seed_micro_degrees.sql`(A, T20 시드, 생성기 `tools/seed/generate_micro_degree_seed.py`).
- 사용 중: `V15__fix_course_names.sql`(A, 시드 `courses` 과목명 174행 교정, 스키마 변경 없음, 9장 24, 2026-10-30 승인).
- 사용 중: `V16__fix_sports_practice_course_units.sql`(A, 전문실기 6행을 체육대학 5개 학과로 이동, `courses` 3,036 → 3,060행, 스키마 변경 없음, 9장 24, 2026-10-30 승인).
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
| T16 ✅ | 홈 D-Day 알림 (4.7, `docs/plans/T15-figma-final.md`) | A | 7일 이내 미완료 과제·개인일정 + e캠퍼스 과제, 최대 5개, 지난 시각 제외, e캠퍼스 호출 1번 |
| T17 ✅ | 남은 수강 과목 (4.6, `docs/plans/T15-figma-final.md`) | A | 필수교양·전공 중 안 들은 과목, 학수번호 → 과목명 판정, F 는 남음, course_unit 없는 전공 처리 |
| T18 ✅ | 학기별 배치하기 (4.6, `docs/plans/T15-figma-final.md`) | A | GET·PUT(통째로 교체), V13, 빈 목록 초기화, 같은 학기 중복 400, 남의 배치 분리, 이수현황·평점에 영향 없음 |
| T19 ✅ | 복수전공 (4.6·5장 12번, `docs/plans/T19-T20-double-major-micro-degree.md`) | A | V14, GET·PUT·DELETE, 제1전공 요건 교체, 공통 과목 양쪽 인정, 제2전공만 소속 과목 기타 이동, 융합전공 등록·`includeConvergence`, 남은 과목 합치기, 프로필 변경 충돌 400 |
| T20 ✅ | 마이크로디그리 (4.3·4.6·5장, `docs/plans/T19-T20-double-major-micro-degree.md`) | A | V17·V18(56개·249행), 목록 API, 등록·해제·최대 3개, 필수·과목 수·그룹·참여학과 판정, 별칭·과목명 찾기, MD 간 중복, 기간 계산 |

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
| 5 | 마이크로디그리 화면 | **결정(2026-10-31): 목록을 시드로 넣고 자동 판정**(T20, 5장 끝, V17·V18). 사용자 직접 입력(B안)은 만들지 않음 |
| 6 | 전공 초과학점 전공선택 인정, 타전공 인정 한도, 재수강 세부 규칙 | 학과 시행세칙 확인 전까지 5장 단순 규칙 |
| 7 | 2025학번 이하 교양 기준 | 2026 값으로 근사 + approximate 표시 |
| 8 | 다전공·부전공 | **결정(2026-10-31): 다전공 1개(복수전공·융합전공) 지원**(T19, 5장 12번). 추가 개발 요청에 따라 부전공 1개 등록·요건·수강 후보 조회는 T21로 지원. 최종 부전공 이수 판정은 지정 과목 확인 후 구현 |
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
| 23 | 남은 수강 과목의 전공선택 (T17) | **결정(2026-10-30): 전공선택도 전부 넣는다**(디자인 카드에 있음). 졸업에 다 들어야 하는 과목은 아니다. 필요한 학점은 `remainingCredits`·`progress` 로 본다 |
| 24 | 교과목 시드의 깨진 과목명 (T3, T17 에서 발견) | **V15 로 교정(2026-10-30, `fix/course-names`).** 원인은 `generate_seed.py` 가 아니라 엑셀 ⑤교과목마스터를 PDF 에서 뽑을 때의 오류다(생성기 재실행 결과가 V2 와 같음). 3036행을 PDF 와 전수 대조해 깨진 163행(숫자 시작 54, 소문자 시작 48, 첫 글자로 안 잡히는 61, 예: `Foreign Policy`)과 영어 공백만 사라진 11행을 고쳤다. 숫자·소문자로 시작해도 정상인 21행(`3D모델링`, `h-STEM융합개론` 등)은 그대로 둔다. 원문이 서로 다른 5행(`IS2102`, `IS3102`, `IS3103`, `PAM320`, `EDU3167`)은 **편성표 기준으로 결정.** 전문실기 `CPE201`~`CPE402` 6행은 소속이 `포스트모던음악학과`로 잘못 들어가 있어 **V16 에서 체육대학 5개 학과로 옮겼다**(PDF 체육대학 공통 편성표, 5개 학과 시행세칙). 종목은 수강할 때 고르므로 이름은 `전문실기1`~`6`, 학수번호·학과당 1행이다. 학과별 전문실기 필수 학점(체육학과·스포츠지도학과 8학점 등)은 졸업요건 계산에 넣지 않았다. B 에게 공유 필요 |
| 25 | 졸업 로드맵 "수업 추가" 창 (T18) | 디자인이 시간표 창(강의실·요일·시간·온라인)과 똑같다. 학기별 배치에 저장하는 값은 **과목명·학수번호·이수구분·학점**이라 강의실·시간은 받지 않는다. 디자인 수정 여부 확인 필요 |
| 26 | 단과대 시행세칙의 다전공 중복 인정 한도 (T19) | 넣지 않음. 공통 과목은 전부 양쪽 인정한다. 알려진 예: 영미어문↔영미문화 전공필수 15학점, 체육대학 전문실기. "전공기초가 같은 두 전공"의 기준도 학과 확인 필요(시각·산업은 전공기초 9과목 중 8과목이 같고, 학수번호별로 양쪽 인정하므로 별도 판정을 두지 않음) |
| 27 | 복수전공 학생의 실제 성적표 (T19) | 샘플 없음. 책자 코드표에 전용 코드가 없어 학수번호로 판정한다. 전용 코드가 있으면 소속 판정보다 우선하도록 고친다 |
| 28 | 시각디자인 전공기초 학점 (T19) | 문서마다 다름: 6(교육과정 p122, 시드) vs 12(p1042·시행세칙). 합계 67/42 는 같아 시드 값을 유지. 학과 확인 필요 |
| 29 | 졸업 판정에 아직 없는 규칙 (T19 조사) | 전공탐색 세미나 필수·면제(p22), 전공필수 초과 이월(p119), 타전공 인정 한도, 대학영어 면제 학점, 적용 교육과정 연도 선택(p119), 빅뱅·배분 대체, 외국인 대학영어 대체. 이 때문에 `graduatable` 이 실제보다 true 로 나올 수 있다 |
| 30 | 마이크로디그리 자료에서 확인할 것 (T20) | 참여학과 2개 조건: 적용 3개는 책자 본문이 근거, **AI로봇은 2026 책자에 없어 2026 이후 신설로 추정해 적용**, 실감미디어기술·테크놀로지&디자인융합콘텐츠제작은 책자에 있어 2025 이전 신설로 추정해 미적용. 의료인공지능의 국제캠 신청 가능 여부, 디자인 소프트웨어의 `SWCON492`, 그린바이오소재의 두 과목표(사이트 기준으로 넣음), 학점 미확인 27행(NULL). 실감미디어융합전공 이수자의 추가 등록 허용은 넣지 않음(최대 3개 고정). 2026학번 의무(`checks`)는 MD 를 완료해도 자동으로 표시되지 않는다 |
