# T13 학사관리 학점(성적) 박스: 평점 평균 API 계획

> 상태: **승인(2026-10-16, 추천안 그대로) → 구현 완료.** 진행 기록은 `docs/PROGRESS.md`.
> 구현 중 바꾼 것: 전공 평점에 **전공기초(MAJOR_BASIC)도 포함**. 성적표 산정기준이 "전공 = 전공필수·전공선택·전공기초"이고, 7절의 샘플 대조값(전공 12학점 3.3)도 전공기초를 넣어야 나온다(빼면 6학점 3.8). 계산기·서비스는 한 클래스(`AcademicGpaService`, `AcademicProgressService` 와 같은 모양)로 합침.
> 출처: 팀 회의 메모 "학사관리에 학점(성적) 박스 필요". 디자인 시안은 아직 못 봤으므로 보여줄 숫자는 11절 질문으로 확인한다.

---

## 1. 현재 코드 상태 (2026-10-16)

- 내 이수내역(`completed_courses`)에 `grade`(A+ A0 B+ B0 C+ C0 D+ D0 F P NP, 미정이면 null)가 이미 저장된다. 파일 가져오기(T12)로도 들어온다.
- `GET /api/academic/progress` 는 **취득 학점**만 센다. 평점 평균은 어디에도 없다.
- 재수강은 `AcademicProgressService.recognize` 가 "F·NP 를 먼저 빼고 같은 학수번호는 가장 최근 1건만 인정"하는 방식으로 처리한다(명세 5장 4번).
- DB 변경, 새 에러 코드, 새 의존성은 필요 없다.

## 2. 목표

- 학사관리 화면의 학점(성적) 박스에 쓸 값을 한 번에 준다: **전체 평점 평균, 학기별 평점·취득 학점, 전공 평점**.
- 계산은 서버에서 한다(프론트가 성적표를 직접 계산하다 틀리지 않게).

이번 범위가 아닌 것: 졸업 시뮬레이션(목표 평점 계산), 성적 추이 그래프용 가공(학기별 값으로 프론트가 그린다), 성적 입력 방식 변경.

## 3. API (🔒)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | /api/academic/gpa | 평점 평균 요약. 이수내역이 없으면 평점은 null |

```json
{
  "gpa": 3.817,                       // 전체 평점 평균(4.3 만점), 평점 대상 과목이 없으면 null
  "majorGpa": 3.9,                    // 전공(전공필수·전공선택) 평점, 없으면 null
  "gradedCredits": 96,               // 평점 계산에 들어간 학점(P·NP·성적 미정 제외)
  "earnedCredits": 102,              // 취득 학점(F·NP·성적 미정 제외, 같은 과목은 최근 것만)
  "terms": [                         // 학년도 → 학기 순
    { "year": 2025, "term": "FIRST",  "gpa": 3.7,   "gradedCredits": 18, "earnedCredits": 18 },
    { "year": 2025, "term": "SECOND", "gpa": null, "gradedCredits": 0,  "earnedCredits": 3 }
  ]
}
```

- 이수내역이 하나도 없으면 `gpa: null`, `terms: []`, 학점은 0(오류가 아니라 정상 응답).
- 프로필(입학년도·전공)이 없어도 동작한다. 이수구분만 있으면 되고 졸업요건은 쓰지 않는다.

## 4. 계산 규칙 (★ 명세 5장에 새 절로 추가)

**등급 → 평점** (경희대 **4.3 만점**. 사용자 확인 + 성적표 과목 행의 평점 칸으로 검증: A+ 4.3, B+ 3.3, B0 3.0, C+ 2.3)

| A+ | A0 | B+ | B0 | C+ | C0 | D+ | D0 | F |
|---|---|---|---|---|---|---|---|---|
| 4.3 | 4.0 | 3.3 | 3.0 | 2.3 | 2.0 | 1.3 | 1.0 | 0 |

1. **평점 평균** = Σ(평점 × 학점) ÷ Σ학점, 대상은 위 표의 9개 등급. **P·NP 와 성적 미정(null)은 분모·분자 모두에서 뺀다.** F 는 0점으로 들어간다.
2. **취득 학점**: F·NP·성적 미정이 아닌 과목의 학점(P 는 취득). 같은 과목은 아래 3번대로 한 번만.
3. **재수강(전체 값)**: 같은 `course_code`(없으면 공백 뺀 `course_name`)가 여러 번이면 **가장 최근(학년도·학기) 1건만** 쓴다. 최근 것이 F 면 그 F(0점)가 반영된다. 이 점이 이수현황(5장 4번, F 를 먼저 뺌)과 다르다(9장 20 참고).
4. **학기별 값**: 그 학기에 기록된 과목 전부로 계산한다(재수강으로 대체된 예전 과목도 그 학기 성적표에는 남아 있으므로). 그래서 학기별 학점을 다 더해도 전체와 다를 수 있다.
5. **전공 평점**: 이수구분이 `MAJOR_REQUIRED`·`MAJOR_ELECTIVE` 인 과목만으로 1번과 같은 계산(재수강 규칙 3번 포함).
6. **반올림**: **소수 셋째 자리까지 반올림(HALF_UP)** — 성적표 요약이 3.417(= 61.5 ÷ 18 = 3.41666…)로 반올림해 표시하므로 같게 맞춘다(버림이면 3.416). `BigDecimal` 로 계산해 `double` 로 내려준다. 응답 키 `gpa`·`majorGpa` 는 숫자 또는 null.
7. **4.5 변환 평점**(성적표 요약의 "4.5기준 평점")은 이번에 넣지 않는다. 필요하면 `gpa45` 를 나중에 더한다.

## 5. 클래스 구조

```
academic/
├── controller/AcademicController      GET /gpa 추가 (기존 컨트롤러에 메서드 1개)
├── dto/GpaSummary                     record(gpa, majorGpa, gradedCredits, earnedCredits, List<TermGpa> terms)
│                                       + nested TermGpa(year, term, gpa, gradedCredits, earnedCredits)
├── service/GpaCalculator              static calculate(List<CompletedCourse>) → GpaSummary  (순수 계산, DB 모름)
└── service/AcademicGpaService         이수내역 조회 → GpaCalculator   (또는 CompletedCourseService 에 메서드 1개)
```

- `AcademicProgressService.calculate` 와 같은 방식(순수 static 계산 + 얇은 서비스)으로 두어 단위 테스트가 쉽다.
- 서비스는 `@Transactional(readOnly = true)`, 컨트롤러는 `@LoginUser Long userId`, 응답은 `ApiResponse<GpaSummary>`.
- 재수강 키 만들기는 `AcademicProgressService.recognize` 의 것과 같다. 중복을 피하려고 키 만드는 작은 메서드만 공용으로 뺄지, 계산기에 한 벌 더 둘지는 구현 때 코드를 보고 가벼운 쪽으로 한다.

## 6. 파일 목록

| 구분 | 파일 |
|---|---|
| 새로 | `academic/dto/GpaSummary.java`, `academic/service/GpaCalculator.java`, `academic/service/AcademicGpaService.java`, 테스트 `GpaCalculatorTest`, `AcademicGpaServiceTest` |
| 수정 | `academic/controller/AcademicController.java` (메서드 추가) |
| 문서 | `BACKEND_SPEC.md`(4.6 표 1줄, 5장 새 절, 8장 행, 9장 20·21), `FRONTEND_GUIDE.md`(진행표, 평점 박스 절), `PROGRESS.md` |

공용 파일(`global/**`, `ErrorCode`, `build.gradle`, `application.yml`) 변경 없음. Flyway 변경 없음. 새 의존성 없음.

## 7. 테스트 계획 (실제 DB 없이 단위 테스트)

| 대상 | 확인할 것 |
|---|---|
| 등급표 | A+ 4.3 … D0 1.0, F 0 |
| 평균 | 3학점 A+ + 3학점 B0 = 3.65, 학점 가중(3학점 A0 + 1학점 C0 = 3.5) |
| **성적표 샘플 대조(T12 익명 사본 8과목)** | 평점 대상 6과목 18학점 → 전체 3.417, 전공 12학점 3.3, 취득 20학점(P 2과목 포함). 성적표에 적힌 값과 같아야 함 |
| 제외 | P·NP·성적 null 은 분모에 안 들어감, 전부 P 면 `gpa: null` |
| 취득 학점 | F·NP·null 제외, P 포함 |
| 재수강 | 같은 학수번호 2번 → 최근 것만(최근이 F 면 0점 반영), 학수번호 없으면 과목명(공백 무시) |
| 학기별 | 재수강으로 대체된 예전 과목도 그 학기 값에는 들어감, 학기 정렬(1학기·여름·2학기·겨울) |
| 전공 평점 | 전공필수·선택만, 전공 과목이 없으면 null |
| 반올림 | 61.5 ÷ 18 → 3.417 (셋째 자리 HALF_UP), 3.4165 → 3.417 |
| 빈 이수내역 | gpa null, terms 빈 배열, 학점 0 |
| 컨트롤러 | 토큰 없으면 401, 남의 데이터는 보이지 않음(내 userId 로만 조회) |
| 실서버 | 로컬 서버에서 `POST /api/academic/completed-courses/bulk` 로 익명 샘플(T12 성적 8과목)을 넣고 `GET /api/academic/gpa` 값을 손계산과 대조 |

## 8. 위험·결정 필요

1. **척도·반올림은 성적표로 확인됨**(4.3 만점, 셋째 자리 반올림). 단 A0·C0·D+·D0 의 점수는 성적표 샘플에 없어 4.3 척도의 관례(4.0·2.0·1.3·1.0)로 정했다. 실제 성적표에 이 등급이 있으면 한 번 대조한다.
2. 재수강 시 학교는 상한 성적(예: B+)을 두기도 한다. 이 규칙은 모르므로 **적힌 성적 그대로** 계산한다.
3. 성적이 입력되지 않은 과목이 많은 학생은 평균이 일부 과목만의 값이다. 응답의 `gradedCredits` 로 프론트가 "N학점 기준"이라고 표시할 수 있다.

## 9. 작업 순서 (승인 후, 단계마다 `docs/PROGRESS.md` 갱신)

1. 브랜치: `feat/import` 위에서 `feat/gpa` 새로 생성(승인 후, 아직 만들지 않음)
2. `GpaCalculator` + 단위 테스트(순수 계산, 가장 먼저)
3. DTO·서비스·컨트롤러 + 테스트
4. 로컬 서버 확인 + 성적표 요약 숫자와 대조
5. 명세·FRONTEND_GUIDE 반영, 커밋 제안(승인 후에만 커밋)

## 10. 승인되면 바꿀 명세 목록

- 4.6 표에 `GET /api/academic/gpa` 한 줄과 응답 예시, 5장에 "평점 평균 계산" 절(4절 내용)
- 8장에 `T13 | 학사관리 평점 평균 (4.6, 5장) | A | 등급표(4.3), P·NP 제외, 재수강 최근 1건, 학기별, 셋째 자리 반올림, 성적표 샘플 대조(3.417), 빈 이수내역`
- 9장 20: 평점 척도 4.3·셋째 자리 반올림(성적표로 확인), A0·C0·D+·D0 점수는 관례값 / 21: 재수강 처리가 이수현황과 다름(GPA 는 F 도 최근 것이 우선)

## 11. 승인받을 것

1. 위 API 모양(전체·전공·학기별)과 계산 규칙. **디자인의 학점 박스에 다른 숫자(예: 학기별 추이 그래프, 취득/졸업 학점 막대)가 있으면 알려 주세요.** 값을 더하거나 뺄 수 있다.
2. 전공 평점(`majorGpa`) 포함 여부(추천: 포함, 계산이 거의 공짜).
3. 재수강 때 최근 성적이 F 면 0점으로 반영하는 것(추천)과, 이수현황과 결과가 다를 수 있다는 점.
