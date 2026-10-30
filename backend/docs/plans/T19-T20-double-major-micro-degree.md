# T19 복수전공 · T20 마이크로디그리 계획

> 작성 2026-10-31 · 상태: **승인(2026-10-31)**, T19 구현 완료, T20 구현 전 · `T15-figma-final.md` 2장의 T19·T20 을 이 문서로 대체한다.
>
> 근거 자료: `예소/오르도_T19복수전공_T20마이크로디그리_최종.xlsx`(2026-10-31 검증본. 같은 폴더의 무접미사·`_수정전` 파일은 쓰지 않는다), `2026 교육과정(전체).pdf`.
>
> 구현 기록은 `docs/PROGRESS.md`, 기준은 `docs/BACKEND_SPEC.md`.

## 1. 자료로 확정된 규칙

PDF 원문으로 직접 확인한 항목: p25·26(후마니타스 MD), p115~118(전공 이수 총칙), p370(첨단패키징 선발), p390(의료인공지능 선발). 운영지침·SDL 사이트 내용은 엑셀 기재값을 따랐고 다시 대조하지 않았다.

| # | 질문 | 답 | 근거 | 영향 |
|---|---|---|---|---|
| 1 | 복수전공을 하면 주전공 요건도 줄어드나 | **줄어든다.** 제1전공·제2전공 모두 각자의 다전공 이수학점을 채운다 | p116 | `progress`·`home`·`summary` 숫자가 바뀐다 |
| 2 | 복수전공 학과의 요건 | 그 학과의 `double_*` 값. 산업디자인 2026 = 9/12/21/42. 디자인의 36 은 예시 값 | p122, p1025 | 시드 그대로 사용. 393행 모두 `double_total` 있음, 세부 합과 일치 |
| 3 | 두 학과에 같은 과목 | **양쪽 다 인정**, 졸업 총학점에는 1번만. 각 전공의 이수구분으로 센다. 학수번호가 다르고 이름만 같으면 불인정 | p115, p116 | 이전 추천안(주전공만 인정)을 **철회** |
| 4 | 복수전공 전용 이수구분 코드 | 책자 코드표에 없음. 실제 복수전공 성적표 샘플은 없음 | 코드표 | 학수번호 대조로 시작(6장 7번) |
| 5 | 지원 범위 | 권장: 다전공 1개(복수전공·융합전공) + MD 최대 3개. 부전공 제외 | p115(최대 2개 전공), 운영지침 제4조②(MD 3개) | 프론트 `multiMajor` 가 단일 객체라 1개로 제한 |
| 6 | 시연 | 권장: 시각디자인 + 산업디자인 + 로컬브랜드매니지먼트 | p115(2026학번 의무) | 3.4 예시와 같은 조합 |

새로 드러난 규칙: **2026학년도 입학자는 다전공·부전공·마이크로디그리 중 1개 이상 필수**(p115). 3.3 에서 처리한다.

## 2. 자료의 제안과 다르게 하는 것

| 자료의 제안 | 결정 | 이유 |
|---|---|---|
| 프로필에 전공 유형(단일/다전공/부전공) 필드 | 만들지 않음 | `double_major_id` 가 NULL 이면 단일전공이다 |
| 학수번호 접두어로 학과 판정 | `courses` 마스터 소속으로 판정 | `FD101` 은 5개 학과·전공에 있고, `SWCON366` 은 컴퓨터공학과 개설이라 접두어로 학과를 알 수 없다 |
| 선택그룹 이수 수가 최대를 넘으면 미충족 | **초과 과목은 세지 않을 뿐 미충족이 아님** | "1과목만 인정"은 금지가 아니다. 그대로 구현하면 한 과목 더 들은 학생이 미충족이 된다 |
| 학점 추정값 27행 저장 | `credits` 를 **NULL 로 저장** | 확인 안 된 숫자를 DB에 넣지 않는다. `earned` 는 내 이수 과목의 실제 학점으로 계산해 영향이 없다 |
| 단독 MD 를 소속 학과에 선택 불가로 표시 | 만들지 않음 | 단독 MD 7개는 전부 후마니타스칼리지 개설이라 대상 학생이 없다 |
| 다전공 금지 조합 필터 | 만들지 않음 | 오르도는 신청 창구가 아니라 승인받은 결과를 입력받는다. 국제캠 금지 전공 2개(동서의과학과, 자유전공학부)는 `majors` 에도 없다 |
| 못 찾은 MD 는 B안(직접 입력) | 만들지 않음 | 56개를 시드로 넣으면 남는 것은 "타대학 융합" 묶음뿐이다 |
| 타대학 융합 MD 행 | 시드에서 제외 | 과목·요건이 없어 판정할 수 없다. 57행 → 56개 |
| MD 이수 기간 입력 | 입력받지 않고 이수 과목으로 계산 | 학교에 고정 기간이 없다(운영지침 제4조⑥). 디자인의 미래 종료 학기는 표시되지 않는다. 필요해지면 칸 4개 추가 |

## 3. T19 복수전공

### 3.1 DB

`V14__add_user_double_major.sql`: `users.double_major_id BIGINT NULL`, FK `majors`.

### 3.2 API

- `GET /api/academic/double-major` 🔒 → 없으면 `data: null`.
- `PUT /api/academic/double-major` 🔒 `{ "majorId": 12 }` → 등록·변경 후 GET 과 같은 응답.
- `DELETE /api/academic/double-major` 🔒

```json
{
  "major": { "id": 12, "displayName": "산업디자인학과" },
  "majorCoursesAvailable": true,
  "total": { "earned": 6, "required": 42 },
  "groups": [
    { "key": "MAJOR_BASIC", "label": "전공기초", "earned": 6, "required": 9 },
    { "key": "MAJOR_REQUIRED", "label": "전공필수", "earned": 0, "required": 12 },
    { "key": "MAJOR_ELECTIVE", "label": "전공선택", "earned": 0, "required": 21 }
  ],
  "courses": [
    { "id": 3, "courseName": "드로잉", "courseCode": "FD101", "classification": "MAJOR_BASIC", "credits": 3, "grade": "A+" }
  ]
}
```

검증:

| 경우 | 에러 |
|---|---|
| 입학년도·주전공 없음 | `PROFILE_INCOMPLETE` |
| 주전공과 같은 전공 | `INVALID_INPUT` |
| 없는 전공, 내 입학년도에 요건이 없는 전공 | `MAJOR_NOT_FOUND` (`findSelectable` 은 융합전공을 빼므로 쓰지 않고 요건 행을 직접 확인) |
| `PATCH /api/users/me` 로 주전공을 복수전공과 같게 하거나, 복수전공 요건이 없는 입학년도로 변경 | `INVALID_INPUT` (복수전공을 먼저 해제) |

마지막 검사가 있어 복수전공 요건 행은 항상 존재한다. 그래서 `required` 가 null 인 경우는 없다.

`course_unit` 이 없는 2개 전공(글로벌한국학과, 스마트팜공학 융합전공)은 요건만 보여 주고 `majorCoursesAvailable=false`, 과목은 0개다. T17 과 같은 방식.

전공 고르기: 지금 `GET /api/catalog/majors` 는 융합전공 13개를 뺀다(주전공 불가). `includeConvergence=true` 파라미터를 추가하고, 기본값은 지금과 같게 false 로 둔다. `findAllSelectable` 은 B 가 만든 T3 쿼리라 B 에게 알린다.

### 3.3 계산 (명세 5장에 추가)

복수전공이 없으면 **지금과 완전히 같다.** 있으면:

1. 인정 과목은 기존 `recognize`(F·NP 제외, 재수강 최근 1건)를 그대로 쓴다.
2. **제1전공 요건**: `basic/required/elective/major_total` 대신 `double_basic/required/elective/total`. NULL 은 지금처럼 0.
3. **제2전공 소속 판정**: 학수번호가 제2전공의 `courses`(`unit_name = course_unit`, 전공기초·필수·선택)에 있으면 소속. 학수번호 없는 수동 입력은 공백 뺀 과목명으로 비교(T17 과 같음).
4. **제2전공 학점**: 소속 과목을 **마스터의 이수구분**으로 합산한다. 성적표 이수구분이 일반선택이어도 소속이면 넣는다.
5. **제1전공 학점**: 제2전공 소속이면서 제1전공 `courses` 에는 없는 과목은 전공 영역에서 빼고 `기타`로 옮긴다. 이것이 없으면 제2전공 전공선택이 제1전공 전공선택으로 잘못 합산된다. 양쪽 소속이거나 양쪽 다 아닌 과목은 지금처럼 성적표 이수구분으로 센다.
6. **졸업 총학점**: 과목당 1번. 지금 계산 그대로.
7. **`graduatable`**: 기존 조건에 제2전공 영역·합계 충족을 더한다.
8. **2026학번 의무**: `checks` 에 "다전공·부전공·마이크로디그리 1개 이상"을 `MANUAL` 로 추가한다(입학년도 2026 이상). 다른 `checks` 처럼 `graduatable` 에는 넣지 않는다. 부전공을 판정할 수 없어서다.

`progress` 응답 형태는 그대로이고 숫자만 바뀐다. 홈·마이페이지는 같은 계산을 쓰므로 함께 바뀐다.

한도 미적용: 단과대 시행세칙의 중복 인정 한도(영미어문↔영미문화 전공필수 15학점, 체육대학 전문실기)는 넣지 않고 코드에 `ponytail:` 주석과 명세 9장으로 남긴다.

### 3.4 예시 (2026학번, 시각디자인 + 산업디자인)

요건: 시각디자인 6/-/36 합 42, 산업디자인 9/12/21 합 42.

| 이수 과목 | 성적표 | 제1전공(시각) | 제2전공(산업) | 졸업 총학점 |
|---|---|---|---|---|
| `FD101` 드로잉 (양쪽 전공기초) | 전공기초 | 전공기초 +3 | 전공기초 +3 | +3 |
| `FD112` 산업디자인리서치 (산업만) | 전공기초 | 빼고 기타 +3 | 전공기초 +3 | +3 |
| `FD113` 시각디자인연구 (시각만) | 전공기초 | 전공기초 +3 | 없음 | +3 |
| 어느 쪽 마스터에도 없는 전공 과목 | 전공선택 | 전공선택 +3 (지금과 같음) | 없음 | +3 |

### 3.5 남은 수강 과목 (T17)

제2전공 과목도 넣는다. 같은 학수번호는 제1전공 쪽 1행만 남긴다. 두 학과가 섞여 구분이 필요하므로 각 행에 `unitName`(필수교양은 null)을 추가한다. `majorCoursesAvailable` 은 두 전공 모두 `course_unit` 이 있을 때 true.

### 3.6 파일

- 새 파일: `V14__add_user_double_major.sql`, `academic/service/DoubleMajorService`, `academic/dto/DoubleMajorRequest`·`DoubleMajorResponse`, `DoubleMajorServiceTest`.
- 수정: `user/domain/User`, `user/service/UserService`, `academic/service/AcademicProgressService`·`RemainingCourseService`, `academic/dto/RemainingCoursesResponse`, `academic/controller/AcademicController`, `catalog/repository/MajorRepository`, `catalog/service/CatalogService`, `catalog/controller/CatalogController`, 관련 테스트.

### 3.7 테스트 (명세 5장 필수)

복수전공 없으면 기존과 동일 / 제1전공 요건 교체 / 양쪽 소속 과목 양쪽 합산·총학점 1번 / 제2전공만 소속 과목이 제1전공에서 빠지고 기타로 이동 / 마스터 이수구분 사용 / 이름만 같고 학수번호 다르면 불인정 / F·재수강 / 제2전공 미충족이면 `graduatable=false` / 2026학번 check / `course_unit` 없음 / PUT 검증(융합전공 등록 가능) / PATCH 충돌 / 전공 목록 `includeConvergence` / 남은 과목 합치기·중복 제거.

## 4. T20 마이크로디그리 (C안: 목록을 시드로)

### 4.1 DB

`V17__add_micro_degrees.sql`:

- `micro_degrees`: `id`, `name`(UNIQUE), `scope`(국제/공통), `college_name`, `host_department`, `required_course_count`, `required_credits`, `min_departments`(0 또는 2), `selective`, `requirement_text`, `guide_url`
- `micro_degree_courses`: `id`, `micro_degree_id`, `course_code`(NULL), `alias_code`(NULL), `course_name`, `department_name`, `credits`(NULL), `mandatory`, `group_key`(NULL), `group_min`(NULL), `group_max`(NULL)
- `user_micro_degrees`: `id`, `user_id`(FK, CASCADE), `micro_degree_id`(FK), 시간 컬럼, UNIQUE(`user_id`, `micro_degree_id`)

`course_code` 는 `courses` 와 FK 를 걸지 않는다. MD 과목 70개 학수번호(후마니타스 등)가 `courses` 에 없다.

`V18__seed_micro_degrees.sql`: 56개, 과목 249행. `tools/seed/generate_micro_degree_seed.py --xlsx <경로> --out <파일>` 로 생성하고 엑셀은 커밋하지 않는다. 이름·학번 같은 개인정보는 없다. 학과 연락처·내부 비고는 넣지 않는다.

`min_departments`:

- 2: 로컬브랜드매니지먼트, 로컬콘텐츠마케팅, 지역공간디자인매니지먼트(책자 본문 명시), AI로봇(2026 책자에 없어 2026 이후 신설로 판단, 추정).
- 0: 실감미디어기술, 테크놀로지&디자인융합콘텐츠제작(책자에 있어 2025 이전 신설로 판단, 추정). 나머지는 조건이 없거나 구조상 항상 충족.

### 4.2 API

- `GET /api/catalog/micro-degrees` (로그인 불필요) → 56개 + 과목 목록. 다른 카탈로그 API 와 같은 위치.
- `GET /api/academic/micro-degrees` 🔒 → 내 MD 와 진행 상황, 등록순.
- `POST /api/academic/micro-degrees` 🔒 `{ "microDegreeId": 23 }` → 등록. 없는 MD 는 `NOT_FOUND`. 이미 등록했거나 3개를 넘으면 `INVALID_INPUT`.
- `DELETE /api/academic/micro-degrees/{microDegreeId}` 🔒 → 해제. 등록 안 한 MD 는 `NOT_FOUND`.

```json
[{
  "id": 23,
  "name": "로컬브랜드매니지먼트",
  "hostDepartment": "시각디자인학과",
  "selective": false,
  "guideUrl": "https://sdl.khu.ac.kr/...",
  "earned": 9,
  "required": 9,
  "earnedCourses": 3,
  "requiredCourses": 3,
  "completed": false,
  "unmet": ["DEPARTMENT_COUNT"],
  "period": "2026. 1학기 ~ 2027. 1학기",
  "courses": [
    { "id": 7, "courseName": "아이덴티티디자인", "courseCode": "VID3038", "credits": 3, "grade": "A0", "counted": true }
  ]
}]
```

`period` 는 프론트 mock 과 같은 문자열이고 이수 과목이 없으면 null 이라 화면에서 숨겨진다. 신청 자체는 오르도에서 하지 않는다. "알아보기" 버튼은 `guideUrl` 로 보낸다. 선발제 2개(첨단패키징, 의료인공지능)는 `selective=true` 로 내려서 프론트가 안내한다.

### 4.3 판정

순수 함수로 분리하고 단위 테스트한다.

1. 인정 과목은 `recognize` 재사용. 이수구분은 보지 않는다. 신청 전 이수도 인정(운영지침 제5조③). MD 인정이 전공·교양 학점 계산을 바꾸지 않는다(제5조④).
2. 매칭: 학수번호 = `course_code` 또는 `alias_code`. `course_code` 가 없는 4행은 공백 뺀 과목명으로 비교.
3. MD 간 중복(제5조⑤): 같은 학수번호는 **먼저 등록한 MD 에만** 넣는다. 공유 학수번호는 20개. `ponytail:` 먼저 등록순 배정이라 뒤 MD 가 더 필요해도 넘겨주지 않는다. 사용자 지정이 필요해지면 그때 추가.
4. 셈: 선택그룹은 `min(이수 수, group_max)` 만 센다. 넘는 과목은 `counted=false`.
5. `completed` = 아래를 모두 만족. 못 만족한 것은 `unmet` 에 담는다.
   - `REQUIRED_COURSE`: `mandatory` 과목 전부 이수
   - `COURSE_COUNT`: 센 과목 수 ≥ `required_course_count`
   - `GROUP_MINIMUM`: 그룹마다 이수 수 ≥ `group_min`
   - `DEPARTMENT_COUNT`: `min_departments=2` 면 센 과목의 `department_name` 2종 이상
6. `earned` = 센 과목의 실제 학점 합, `required_credits` 로 상한. 판정은 과목 수 기준(예술체험심화 11~12학점 때문).
7. 최대 3개만 막는다. 실감미디어융합전공 이수자의 추가 허용은 넣지 않는다.

### 4.4 파일

- 새 파일: `V17`, `V18`, `tools/seed/generate_micro_degree_seed.py`, `catalog/domain/MicroDegree`·`MicroDegreeCourse`, `catalog/repository/MicroDegreeRepository`, `catalog/dto/MicroDegreeResponse`, `academic/domain/UserMicroDegree`, `academic/repository/UserMicroDegreeRepository`, `academic/service/MicroDegreeService`, `academic/dto/MicroDegreeAddRequest`·`MicroDegreeProgressResponse`, `MicroDegreeServiceTest`.
- 수정: `catalog/service/CatalogService`, `catalog/controller/CatalogController`, `academic/controller/AcademicController`, `CatalogSeedTest`, `OrdoApplicationTests`.

### 4.5 테스트

필수 미이수 / 과목 수 / 그룹 상한(초과가 미충족이 아님) / 그룹 하한 / 2개 학과 / 별칭·학수번호 없는 과목 / MD 간 중복 / F·재수강 / 학점 상한 / 기간 계산 / 등록 3개 제한·중복 / 남의 데이터 분리 / 시드 행 수·별칭.

## 5. 추가규칙 26개 처리

| 처리 | 규칙 |
|---|---|
| 이번에 반영 | 2026학번 의무(3.3), 다전공 1개, MD 참여학과·선발제·후마니타스 학수번호 매칭(4장) |
| 이미 동작 | 이수 당시 이수구분 인정(성적표 우선, 9장 17), 타 학과 과목(성적표를 따름), 배분이수 영역 수, 교양 학점 구조 |
| 자동 처리 | 다전공 포기: DELETE 하면 계산이 원래대로 돌아간다. 일반선택 전환은 성적표를 다시 올리면 반영된다 |
| 보류, 명세 9장에 기록 | 적용 교육과정 연도 선택, 전공필수 초과 이월, 타전공 인정 한도, 대학영어 면제 학점, 전공탐색 세미나 필수·면제, 빅뱅·배분 대체, 외국인 대학영어 대체 |
| 범위 밖 | 부전공 2개 규칙, 교직, 다전공 금지 조합, 편입생 학점, 트랙, 이수 순서 추천, 선수과목 경고, 학생설계전공, 융합전공 제1전공 진입 |

보류 항목 때문에 `graduatable` 이 실제보다 true 로 나올 수 있다. 지금의 `checks` 와 같은 한계이고, 전공탐색 세미나는 특히 필수이므로 명세 9장에 명시한다.

## 6. 학과 확인이 필요한 것 (구현을 막지 않음)

1. 시각디자인 전공기초가 문서마다 다름: 6(p122) vs 12(p1042·시행세칙). 합계 67/42 는 같다. 시드의 p122 값을 유지한다.
2. 실감미디어기술·테크놀로지&디자인융합콘텐츠제작의 신설 연도(참여학과 2개 조건).
3. AI로봇이 2026 이후 신설인지(4.1 의 추정).
4. 의료인공지능을 국제캠 학생이 신청할 수 있는지.
5. 디자인 소프트웨어의 `SWCON492`, 그린바이오소재의 두 과목표(사이트 기준으로 넣음).
6. 학점 추정 27행(NULL 로 넣음).
7. 복수전공 학생의 실제 성적표: 전용 코드가 있으면 3.3 의 소속 판정보다 우선하도록 고친다. 샘플은 커밋·출력하지 않는다.
8. "전공기초가 같은 두 전공"의 기준: 시각·산업은 9과목 중 8과목이 같다. 지금 계획은 학수번호별 양쪽 인정이라 별도 판정을 두지 않는다.

## 7. 순서·브랜치

| 순서 | 작업 | 브랜치 | DB |
|---|---|---|---|
| 1 | T19 | `feat/double-major` | V14 (예약해 둔 번호, `out-of-order` 로 적용) |
| 2 | T20 | `feat/micro-degree` | V17, V18 (V15·V16 은 과목명 교정에서 사용) |

master 에 T15~T18 이 아직 없고 문서 충돌을 피해야 하므로 `fix/course-names` 위에 쌓는다. 공용 파일(`global/**`, `ErrorCode`, `build.gradle`, `application.yml`)은 바꾸지 않는다. 새 의존성 없음. 시드 생성 스크립트는 `generate_seed.py` 처럼 `openpyxl` 을 쓴다(런타임 의존성 아님).

## 8. 바꿀 문서

- BACKEND_SPEC: 3장(V14·V17·V18), 4.3 카탈로그, 4.6 학사, 5장(3.3·3.7), 7장(번호), 8장(T19·T20), 9장(5·6·8 갱신, 5장·6장 항목 추가).
- FRONTEND_GUIDE: 학사관리·졸업 로드맵. `multiMajor`·`microDegree` 대응표(`groups` ↔ `breakdown`, 소문자 키 차이).
- PROGRESS: T18 줄의 커밋 번호 포함.
- `T15-figma-final.md`: 상태 줄과 2장 T19·T20 에 이 문서 안내.

## 9. 승인받을 것

1. **범위**: 다전공 1개 + MD 최대 3개, 부전공 제외.
2. **T19 계산**: 복수전공 등록 시 제1전공 요건 교체, 공통 과목 양쪽 인정, 제2전공만 소속 과목은 제1전공에서 빼고 기타로.
3. **T20 C안**: 56개 시드, 2장의 결정들(그룹 초과 처리, 추정 학점 NULL, 기간 계산, 먼저 등록한 MD 배정).
4. **참여학과 추정 3건**(4.1).
