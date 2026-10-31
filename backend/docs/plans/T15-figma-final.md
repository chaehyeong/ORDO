# T15~T20 최종 디자인 대비 빠진 API 계획

> 작성 2026-10-30 · 상태: **T15~T18 승인(추천안 그대로, 2026-10-30)**, T19·T20 은 `T19-T20-double-major-micro-degree.md` 로 대체(2026-10-31 승인), 1.3 보류 동의·1.4 질문은 대기 · 기준: Figma "웹디자인 최종"(node 381:751) 12개 화면, master `cad1390`(PR #1~#12 병합, 테스트 198개)
>
> 승인 전에는 코드를 쓰지 않는다. 승인되면 기능마다 브랜치를 따로 만들고, 단계마다 `docs/PROGRESS.md` 를 갱신한다. 승인된 내용은 BACKEND_SPEC·FRONTEND_GUIDE 에 반영한다.

## 1. 화면별 비교 결과

### 1.1 지금 API 로 되는 것 (백엔드 할 일 없음)

| 화면 | 디자인 요소 | 쓰는 API |
|---|---|---|
| 로그인 | 이메일·비밀번호 | `POST /api/auth/login` |
| 회원가입 | 이름·연락처·이메일·비밀번호 (프론트 `SignUp.js` 에는 학과·학번도 있음) | `POST /api/auth/signup` |
| 로그인 상태 유지 | 체크박스 | 백엔드 변경 없음. 프론트가 refresh 토큰을 어디(localStorage/sessionStorage)에 둘지로 처리 |
| 홈 | 인사·주간일정 점·오늘일정 타임라인·졸업 진행률(78%, 102/130, 남은 28학점) | `GET /api/home` |
| 캘린더 | 월 보기, 수업·과제·개인일정 색, 할 일 추가 창(제목·카테고리·날짜·시작·종료) | `GET/POST /api/schedules`, `GET /api/ecampus/events` |
| 캘린더 | "캘린더 연동" 스위치 + "캘린더 URL 추가하기" 창 | `PUT/DELETE /api/ecampus/feed`, 상태는 `GET /api/users/me` 의 `ecampusConnected` |
| 캘린더·마이페이지 | "알림 받기"/"알림" 스위치 | `PATCH /api/users/me/settings` (`notificationEnabled`) + T11 웹푸시 |
| 시간표 | 학기 선택, 시간 있는 수업 추가 | `GET /api/timetables`, `POST /api/timetables/entries` |
| 학사관리 | 학점 취득 현황(총 18/130, 영역 6개) | `GET /api/academic/progress` |
| 학사관리 | 평균 학점 3.68/4.3 | `GET /api/academic/gpa` |
| 학사관리 | 이수한 과목 관리(연도·학기·이수구분 필터) + 추가 창(학년도·학기·과목명·과목코드·이수구분·학점·성적) | `GET/POST/PATCH/DELETE /api/academic/completed-courses` |
| 졸업 로드맵 | 학기별 곡선(1-1 3.53 18학점 …), "지금 여기" | `GET /api/academic/gpa` 의 `terms`(학기별 평점·학점). 학기 번호·"지금 여기"는 당시 `currentSemester` 로 된다고 봤지만 입력받는 곳이 없어 틀렸고, 2026-10-31 `semesterLabel`·`currentSemesterLabel` 을 추가함 |
| 졸업 로드맵 | 이수한 과목 표 | `GET /api/academic/completed-courses` |
| 마이페이지 | 이름·학번·학과·연락처·이메일, 로그아웃 | `GET/PATCH /api/users/me`, `POST /api/auth/logout` |

### 1.2 API 가 없는 것 → 이 계획서에서 만들 것

| 번호 | 화면 | 디자인 요소 | 지금 상태 |
|---|---|---|---|
| **T15** | 시간표·수업 추가 창 | "온라인 강의" 체크, 표 아래 시간 없는 수업 줄(빅뱅에서 문명까지 등) | `day_of_week`·`start_time`·`end_time` 이 NOT NULL 이라 저장 불가. 파일 가져오기도 `NO_TIME` 경고 후 버림 |
| **T16** | 홈 | "D-Day 알림" 카드(과제 마감 D-2, 팀플 모임 오늘 10:00) | `GET /api/home` 에 없음 (AI 추천 자리가 이 카드로 바뀜) |
| **T17** | 졸업 로드맵 | "남은 수강 과목" 카드(이수구분 태그·학점, 이수구분 필터) | 없음. `progress` 의 `requiredGeneralCourses` 는 필수교양만 |
| **T18** | 졸업 로드맵 | "학기별 배치하기"(과목을 학기로 끌어 놓기, 학기별 학점 합, 수업 추가, 초기화) | 없음. 저장할 테이블 없음 |
| **T19** | 학사관리 | 복수전공 카드(산업디자인학과 00/36학점, 전공기초·필수·선택 막대, 이수 과목 보기, 신청하기 / 빈 상태) | 없음. 요건 숫자는 `graduation_requirements.double_*` 에 이미 있음(명세 9장 8 "MVP 제외") |
| **T20** | 학사관리 | 마이크로디그리 카드(UX 디자인 마이크로디그리 6/12학점, 이수 기간 2026.2학기~2027.2학기 / 빈 상태) | 없음. **마이크로디그리 데이터 자체가 없음**(명세 9장 5 보류) |

### 1.3 만들지 않기를 제안하는 것

| 디자인 요소 | 이유 | 제안 |
|---|---|---|
| 로그인 "경희대학교 통합로그인" | 학교 통합인증은 학교의 공식 승인·등록이 있어야 연동 가능 | 버튼 삭제를 디자인·프론트에 요청 |
| 로그인 "비밀번호 찾기" | 메일 발송이 필요 → 새 의존성(`spring-boot-starter-mail`) + 메일 계정 비밀값 | 보류. 시연에서 버튼 숨김 |
| 마이페이지 "프로필 사진 변경" | 파일 업로드·저장소가 필요(지금은 `profileImageUrl` 문자열만) | 보류. 기본 아이콘 표시 |
| 시작 화면 "AI 추천" 카드 | 이미 보류(명세 4.7) | 보류 유지 |

### 1.4 디자인 쪽에 물어볼 것 (백엔드 결정에 영향)

1. 졸업 로드맵 옆 "수업 추가" 창이 시간표 창(강의실·요일·시간·온라인)과 똑같다. 학기별 배치용이라면 **과목명·학점·이수구분**만 받는 게 맞는지.
2. 남은 수강 과목·이수한 과목의 "총 50학점" 배지가 무엇의 합인지(졸업까지 남은 학점인지, 카드에 보이는 과목 학점의 합인지).
3. 복수전공 버튼 문구가 화면마다 "+복수전공 신청하기"/"다전공 추가하기"로 다르다. 마이크로디그리 카드 제목 아래 "복수전공" 글자는 오타로 보임.

## 2. 기능별 계획

### T15 시간표 온라인 강의 (작음)

- **Flyway V12** `timetable_entries`: `day_of_week`·`start_time`·`end_time` 을 NULL 허용, `online BOOLEAN NOT NULL DEFAULT FALSE` 추가.
- 규칙: `online=true` 면 요일·시간을 저장하지 않는다(null). `online=false` 면 지금처럼 셋 다 필수. 겹침(409) 검사는 온라인 수업을 건너뛴다.
- API: 요청·응답에 `online` 추가. `PATCH` 로 온라인으로 바꾸면 요일·시간을 지우고, 온라인을 끄면 요일·시간을 같이 보내야 한다(아니면 400).
- 정렬: 시간 있는 수업(요일·시작 순) 다음에 온라인 수업(이름 순).
- 홈: 주간 점·오늘 타임라인에서 온라인 수업은 뺀다.
- 파일 가져오기(T12): 시간 없는 과목을 버리지 않고 `online=true` 칸으로 만든다. `NO_TIME` 경고는 "온라인(시간 없음)으로 넣었어요"로 바꿔 남긴다.
- 엔티티 `dayOfWeek` 는 `int` → `Integer`. `HomeService`·`TimetableService` 의 요일 비교를 null 안전하게 고친다.
- 테스트: 온라인 저장(시간 null), 온라인+시간 보냄 → 시간 무시, 온라인 아님+시간 없음 → 400, 온라인끼리 겹침 검사 안 함, 홈에서 제외, 가져오기 결과에 온라인 칸.

### T16 홈 D-Day 알림 (작음, DB 변경 없음)

- `GET /api/home` 응답에 `deadlines` 배열 추가(하위 호환).

```json
"deadlines": [
  { "source": "ECAMPUS",  "id": null, "title": "타이포그래피 과제", "category": "ASSIGNMENT",
    "date": "2026-10-18", "time": "23:59", "dday": 2, "courseName": "타이포그래피 I", "url": "https://..." },
  { "source": "SCHEDULE", "id": 12,   "title": "팀플 모임", "category": "PERSONAL",
    "date": "2026-10-16", "time": "10:00", "dday": 0, "courseName": null, "url": null }
]
```

- 대상(추천): 오늘~7일 뒤의 **완료 안 된 과제·개인일정**(`ASSIGNMENT`·`PERSONAL`, 수업 `LECTURE` 제외) + e캠퍼스 과제 마감. 오늘 이미 지난 시각은 뺀다.
- 정렬: 날짜 → 시각(없으면 23:59) 순. **최대 5개**.
- `dday` = 오늘부터 며칠 남았는지(0 = 오늘). 화면의 "D-2"·"오늘 10:00" 표기는 프론트가 만든다.
- e캠퍼스 호출은 지금처럼 요청당 1번(조회 범위를 주간과 오늘+7일을 합친 기간으로 넓힘). 미연동·실패면 e캠퍼스 항목만 빠진다.
- 테스트: 7일 경계, 완료된 일정 제외, 수업 제외, 5개 제한, 정렬, e캠퍼스 실패 시 정상, e캠퍼스 호출 1번.

### T17 남은 수강 과목 (중간, DB 변경 없음)

- `GET /api/academic/remaining-courses` 🔒

```json
{
  "remainingCredits": 28,
  "majorCoursesAvailable": true,
  "courses": [
    { "courseName": "성찰과 표현", "courseCode": null, "classification": "GEN_REQUIRED", "credits": 3,
      "targetGrade": "1", "openSemester": null },
    { "courseName": "디지털디자인", "courseCode": "D101234", "classification": "MAJOR_BASIC", "credits": 3,
      "targetGrade": "1", "openSemester": "1,2" }
  ]
}
```

- 원천:
  1. 필수교양: 이수현황 계산이 이미 쓰는 `general_education_required_courses`(입학년도 기준) 중 안 들은 과목.
  2. 전공: `courses` 마스터에서 `unit_name = 내 전공 course_unit` 이고 이수구분이 전공기초·전공필수·전공선택인 과목 중 안 들은 과목.
- "들었다" 판정: 학수번호가 같으면 들은 것. 학수번호가 없으면 과목명이 같으면 들은 것. F·NP 는 안 들은 것으로 본다.
- 정렬: 이수구분(필수교양 → 전공기초 → 전공필수 → 전공선택) → 권장 학년 → 이름.
- 내 전공의 `course_unit` 이 없으면(자동 판별 불가 전공) 전공 과목은 빼고 `majorCoursesAvailable=false`.
- `remainingCredits` 는 이수현황의 `total.remaining`(졸업까지 남은 학점)을 그대로 쓴다. 1.4-2 답에 따라 바꿀 수 있다.
- 학사 프로필(전공·입학년도)이 없으면 기존 이수현황 API 와 같은 `PROFILE_INCOMPLETE`.
- 테스트(명세 5장처럼 단위 테스트 필수): 들은 과목 제외(학수번호·이름), F 는 남은 과목, 다른 학과 과목 제외, course_unit 없음, 정렬.

### T18 학기별 배치하기 (중간)

- **Flyway V13** `planned_courses`: `id`, `user_id`, `academic_year`, `term`, `course_name`, `course_code`(NULL), `classification`(NULL), `credits`, `created_at`, `updated_at`.
- API 2개(끌어 놓기 결과를 통째로 저장하는 방식이라 개별 이동 API는 만들지 않음):
  - `GET /api/academic/plans` 🔒 → 학기별로 묶은 목록 + 학기별 학점 합 + 전체 합.
  - `PUT /api/academic/plans` 🔒 → 보낸 목록으로 **전부 교체**. "초기화" = 빈 목록 PUT.
- 검증: 학점 0~30(구현 때 1~30 에서 바꿈: T17 의 0학점 졸업논문도 배치할 수 있어야 함), 한 번에 최대 100과목, 같은 학기에 같은 과목(학수번호, 없으면 이름) 중복 → 400.
- 화면의 학기 칸(2027년 1학기 ~ 2028년 2학기)은 프론트가 `admissionYear`·`currentSemester` 로 만든다. 백엔드는 학기를 제한하지 않는다.
- "수업 추가"는 남은 과목에 없는 과목을 목록에 넣는 것 → 같은 PUT 으로 저장(1.4-1 답에 따라 필드 확정).
- 졸업 시뮬레이션(배치한 과목까지 들으면 졸업 가능한지)은 이번 범위가 아니다. 이미 있는 `AcademicProgressService.calculate(...)` 로 나중에 붙일 수 있다.
- 테스트: 교체 저장, 빈 목록 초기화, 남의 계획 분리, 검증 400, 학기별 합.

### T19 복수전공 (큼, 규칙 확인 필요)

> **대체됨:** 아래는 자료를 받기 전 초안이다. 규칙이 달라졌으니 `T19-T20-double-major-micro-degree.md` 를 본다.

- **Flyway V14** `users`: `double_major_id BIGINT NULL`(FK majors) 추가. 복수전공은 1개만.
- API:
  - `PUT /api/academic/double-major` 🔒 `{ "majorId": 12 }` → 등록·변경. 주전공과 같으면 400.
  - `DELETE /api/academic/double-major` 🔒
  - `GET /api/academic/double-major` 🔒 → 없으면 `data: null`.

```json
{
  "major": { "id": 12, "displayName": "산업디자인학과" },
  "total": { "earned": 0, "required": 36 },
  "groups": [
    { "key": "MAJOR_BASIC", "earned": 0, "required": 9 },
    { "key": "MAJOR_REQUIRED", "earned": 0, "required": 12 },
    { "key": "MAJOR_ELECTIVE", "earned": 0, "required": 15 }
  ],
  "courses": [ { "id": 3, "courseName": "...", "classification": "MAJOR_BASIC", "credits": 3, "grade": "A+" } ]
}
```

- 요건: `graduation_requirements`(복수전공 학과, 내 입학년도) 행의 `double_basic/required/elective/total_credits`. 행이 없으면 `required` 를 null 로 둔다.
- 과목 인정: 이수 과목의 학수번호가 복수전공 학과의 `courses` 마스터(`unit_name = course_unit`)에 있으면 그 과목의 이수구분으로 복수전공 학점에 넣는다. `courses`(이수 과목 보기)는 그 목록.
- **확인 필요 규칙**(명세 9장 6·8):
  1. 복수전공을 하면 **주전공 요건도 다전공 기준**(`double_*`)으로 바뀌는지(경희대 졸업요건 표의 "다전공" 열 해석).
  2. 한 과목이 두 학과 교육과정에 모두 있을 때 **양쪽에 인정**하는지, 주전공에만 넣는지(추천: 주전공 우선, 중복 인정 안 함).
  3. 성적표 이수구분에 복수전공 코드가 따로 있는지. 있으면 학수번호 대조보다 그 코드를 우선한다.
- 위 1이 "바뀐다"면 `progress`·`home`·`summary` 의 주전공 요건 계산도 바뀌므로 5장 테스트를 추가한다.

### T20 마이크로디그리 (데이터 없음 → 방식 결정 필요)

> **대체됨:** 자료를 받아 C안으로 결정했다. `T19-T20-double-major-micro-degree.md` 를 본다.

| 안 | 내용 | 장단점 |
|---|---|---|
| A. 보류 | 화면은 빈 상태("이수 중인 마이크로디그리가 없습니다")로 고정 | 작업 없음. 화면 일부가 동작 안 함 |
| **B. 사용자가 직접 입력 (추천)** | 이름·필요 학점·이수 기간(시작·끝 학기)을 입력하고, 이수한 과목 중 인정 과목을 고른다 | 데이터 없이 가능. 학교 기준과 맞는지는 사용자 책임 |
| C. 목록 데이터 확보 | 팀이 마이크로디그리 목록·과목표를 구해 시드로 넣고 자동 계산 | 가장 정확. 데이터 수집이 먼저 필요 |

- B안이면 **Flyway V15**: `micro_degrees`(`user_id`, `name`, `required_credits`, `start_year`, `start_term`, `end_year`, `end_term`), `micro_degree_courses`(`micro_degree_id`, `completed_course_id`).
- B안 API: `GET/POST /api/academic/micro-degrees`, `PATCH/DELETE /api/academic/micro-degrees/{id}`, `PUT /api/academic/micro-degrees/{id}/courses` `{ "completedCourseIds": [1, 2] }`. 응답 `earned` = 고른 과목 학점 합(F·NP 제외).

## 3. 순서 제안

| 순서 | 작업 | 크기 | DB | 이유 |
|---|---|---|---|---|
| 1 | T15 온라인 강의 | 작음 | V12 | 시간표 화면이 바로 완성됨, 가져오기 경고도 해결 |
| 2 | T16 홈 D-Day | 작음 | 없음 | 홈 카드 1개 |
| 3 | T17 남은 수강 과목 | 중간 | 없음 | 로드맵 화면 절반 |
| 4 | T18 학기별 배치 | 중간 | V13 | 로드맵 화면 나머지 |
| 5 | T19 복수전공 | 큼 | V14 | 규칙 확인(2장 T19) 후 시작 |
| 6 | T20 마이크로디그리 | 중간(B안) | V15 | 방식 결정 후 |

브랜치는 기능마다 master 에서 따로 만든다(T15 는 이 계획서 커밋을 포함하려고 `feat/figma-gap` 위에 만듦. `feat/timetable-online`, `feat/home-deadlines`, `feat/remaining-courses`, `feat/plans`, `feat/double-major`, `feat/micro-degree`). 지난번처럼 깊게 쌓으면 문서 충돌이 나므로, 하나 머지되면 다음 것을 master 에서 시작한다.

## 4. 공용 파일 변경 (B 에게 알릴 것)

- `ErrorCode`: 새 코드 없이 기존 코드로 처리할 예정 — 검증 실패 `INVALID_INPUT`, 시간 누락 `INVALID_TIME_RANGE`, 학사 프로필 없음 `PROFILE_INCOMPLETE`, 없는 학과 `MAJOR_NOT_FOUND`, 남의 데이터 `NOT_FOUND`. 필요해지면 명세 2.5 표부터 고친다.
- `build.gradle`·`application.yml`·`global/**`: 변경 없음. 새 의존성 없음.

## 5. 승인되면 바꿀 명세 목록

- BACKEND_SPEC: 3장(V12~V15 테이블), 4.5 시간표(`online`), 4.6 학사(remaining-courses, plans, double-major, micro-degrees), 4.7 홈(`deadlines`), 4.9 가져오기(NO_TIME), 5장(남은 과목·복수전공 규칙), 7장(Flyway 번호), 8장(T15~T20 행), 9장(5·8 갱신, 1.3 보류 항목, 1.4 질문).
- FRONTEND_GUIDE: 시간표·홈·학사관리·졸업 로드맵 절, 1.1 표(이미 되는 API 대응).

## 6. 승인받을 것

1. 범위·순서: T15~T20 전부인지, 일부(예: T15~T18 먼저)인지.
2. T15: 온라인 강의 = 시간 없음(요일·시간 저장 안 함).
3. T16: 대상 = 7일 이내 미완료 과제·개인일정 + e캠퍼스 과제, 최대 5개.
4. T17: 전공선택까지 포함, 들었다 판정 = 학수번호 → 과목명.
5. T18: 통째로 교체(PUT) 방식.
6. T19: 2장의 확인 필요 규칙 1~3 (팀·B 와 확인 후 시작해도 됨).
7. T20: A / B / C 중 선택.
8. 1.3 의 보류 4개 동의, 1.4 질문을 디자인팀에 전달.
