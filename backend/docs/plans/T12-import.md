# T12 성적·시간표 파일 가져오기 — 구현 계획

> 상태: **승인됨(2026-10-08), 추천안대로 진행.** 진행 기록은 `docs/PROGRESS.md`.
> 결정: POI 추가 · PDF 는 마지막에 PDFBox 로 시도(A) · xlsx 병합 영역 방식 · 재업로드 REPLACE · 이수구분은 성적표 우선 · 테스트 사본 교수명 익명화.
> 작성: 2026-10-08 (백엔드 A). 처음엔 T11 로 썼으나 명세 T10(e캠퍼스)과 겹쳐 T12 로 바꿈.

---

## 0. 현재 코드 상태와 샘플 분석 결과 (2026-10-08)

**코드**
- 시간표(T5): 칸 1개씩 추가만 있다(`POST /api/timetables/entries`). **여러 칸 저장(bulk) API 없음** → 이번에 만든다.
- 이수내역(T6): `POST /api/academic/completed-courses/bulk` 있음(PR #7, 아직 머지 전). 학수번호 + 내 전공으로 이수구분 자동 판별, 하나라도 틀리면 전체 롤백. **같은 파일을 다시 올렸을 때 중복을 막는 규칙은 없음** → 이번에 추가한다.
- `users.student_number` 는 가입 때 필수라 학번 대조가 가능하다.

**샘플 분석** (`C:\Users\lmch0\OneDrive\Desktop\수강 신청 내역, 전체 성적 보기`, 실제 개인정보는 출력하지 않고 구조만 봄)

| 파일 | 형식 | 확인 결과 |
|---|---|---|
| `noname.*` | 수강신청확인서 | 제목 → `2026학년도`, `2학기` → 학번·성명 줄 → 머리글 7칸(순번, 학수번호-분반, 과목명, 학점, 이수구분, 재수강 여부, "교강사, 강의시간, 강의실") → 6행 → `1/1`. **6과목 18학점, 수업 7개 + 온라인 1과목** ✓ 기대값과 일치 |
| `noname (1).*` | 전체 성적 보기 | 제목 → 성명·학번 줄 → 머리글 11칸 → 과목 8행(첫 행만 앞에 `2026`,`1학기` 2칸 더 있음) → `1/4` 뒤로 '원 성적', '전공/교양별 평점', '편입/인정' 요약 표가 이어짐. **8과목 20학점, 2026학년도 1학기** ✓ |
| txt | 둘 다 **탭 구분**, MS949, CRLF | |
| csv | 쉼표 구분, MS949, 머리글 칸 안에 줄바꿈(`"재수강\n여부"`)이 따옴표로 들어 있음 | 따옴표·칸 안 줄바꿈을 처리하는 파서 필요 |
| xlsx | 시트 1개, 병합 셀 54개(신청)·262개(성적), 이미지 1개·4개 포함 | **병합 영역 1개 = 칸 1개로 읽으면 csv·txt 와 행 모양이 정확히 같아진다**(아래 4.2) |
| pdf | 만든 프로그램 `oz`(OZ 리포트), 글꼴 임베드 안 됨, `UniKS-UCS2-H` 인코딩 | **한글 추출 정상**(깨진 글자 0개, Python pdfplumber 로 확인). 단 **줄 순서·칸 배치가 뒤섞임**(아래 6절) |

**성적표 이수구분 칸에 숫자 코드가 같이 있다**: 04 전공필수, 05 전공선택, 08 일반선택, 11 전공기초, **14 중핵교과**, 15 배분이수교과, 17 자유이수교과. 이 번호는 명세 3.1 의 학교 코드와 같다(명세: "GEN_REQUIRED = 교양 필수교과 **14, 16**"). 그래서 "중핵교과 → GEN_REQUIRED" 추정은 근거가 있다. 기초교과는 16일 가능성이 높지만 성적표 샘플엔 없어서 확인 필요.

**학수번호 분반 분리를 시드와 대조**: `CSE20101→CSE201`, `SWCON10400→SWCON104`, `AMTH100931→AMTH1009`, `AMTH100409→AMTH1004` 모두 `courses` 시드에 있고, 이수구분도 파일과 같다. `CSE000100→CSE0001`(일반선택)은 시드에 없음 → 파일 값 사용.

## 1. 목표

- 학생이 학교 시스템에서 내려받은 **수강신청확인서**나 **전체 성적 보기** 파일을 올리면, 시간표 칸 또는 이수내역 **미리보기 초안**을 돌려준다(저장 안 함).
- 사용자가 미리보기를 확인·수정한 뒤 기존 bulk 저장 API 로 저장한다. 같은 파일을 다시 올려도 중복되지 않는다.
- 지원 형식: **xlsx(추천), csv, txt, pdf**. 그 외(이미지, ozd, hwp, xls 등)는 "엑셀(xlsx)로 저장해서 올려주세요"로 거절한다.
- 파일은 메모리에서만 처리하고 버린다. **학번·이름·성적은 로그에 남기지 않는다.** 파일의 학번이 내 학번과 다르면 거절한다.

## 2. API

### 2.1 미리보기 `POST /api/import` 🔒 (multipart, 필드 이름 `file`)

```json
// 수강신청확인서 → 응답 data
{ "type": "TIMETABLE", "format": "XLSX",
  "year": 2026, "term": "SECOND", "courseCount": 6, "totalCredits": 18,
  "courses": [ { "courseCode": "CSE103", "section": "01", "courseName": "객체지향프로그래밍", "credits": 3,
                 "classificationText": "전공필수", "retake": false, "sessionCount": 2 } ],
  "entries": [ { "courseName": "객체지향프로그래밍", "courseCode": "CSE103", "dayOfWeek": 2,
                 "startTime": "15:00", "endTime": "16:50", "location": "전205", "professor": "김교수", "color": null } ],
  "warnings": [ { "code": "NO_TIME", "message": "시간 없는 과목: 기초 미분적분학 (온라인)" } ] }

// 전체 성적 보기 → 응답 data
{ "type": "GRADES", "format": "CSV",
  "terms": [ { "year": 2026, "term": "FIRST" } ], "courseCount": 8, "totalCredits": 20,
  "courses": [ { "year": 2026, "term": "FIRST", "courseCode": "CSE201", "section": "01", "courseName": "이산구조",
                 "credits": 3, "grade": "B+", "classificationText": "전공선택", "classification": "MAJOR_ELECTIVE",
                 "distributionArea": null, "needs": [] },
               { …, "courseName": "코딩하는아티스트", "classification": "GEN_DISTRIBUTION", "needs": ["DISTRIBUTION_AREA"] } ],
  "warnings": [ { "code": "DISTRIBUTION_AREA_REQUIRED", "message": "배분이수 영역(1~5)을 골라 주세요: 코딩하는아티스트" } ] }
```

- `entries`·`courses` 는 저장 API 요청 모양과 같아서 프론트가 고친 뒤 그대로 보낼 수 있다.
- `needs`: 사용자가 골라야 하는 값(`CLASSIFICATION`, `DISTRIBUTION_AREA`, `GRADE`).
- 응답에 학번·이름은 넣지 않는다.

### 2.2 저장 (bulk)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | /api/timetables/entries/bulk | **새로 만듦.** `{ "year": 2026, "term": "SECOND", "mode": "REPLACE", "entries": [ … ] }` → 201 |
| POST | /api/academic/completed-courses/bulk | **기존 API 에 필드 1개 추가.** `{ "courses": [ … ], "replaceTerms": [ { "year": 2026, "term": "FIRST" } ] }` → 201 |

**중복 방지 규칙 (추천: 학기 단위 교체 REPLACE)**
- 시간표 `REPLACE`: 그 학기의 내 칸을 모두 지우고 새로 넣는다(같은 트랜잭션). 같은 파일을 여러 번 올려도 결과가 같다. 넣을 칸끼리 겹치면 409 `TIMETABLE_OVERLAP`, 아무것도 안 바뀜.
- 시간표 `MERGE`(선택): 기존 칸은 두고, 과목명·요일·시작·종료가 같은 칸은 건너뛰고, 나머지만 넣는다. 기존 칸과 겹치면 409.
- 이수내역 `replaceTerms`: 그 학기들의 내 이수내역을 지우고 새로 넣는다. 성적표가 공식 기록이라 그 학기는 파일이 정답이다. 생략하면 지금처럼 추가만 한다.
- 미리보기 응답에 "이 학기의 기존 데이터 n건이 교체됩니다"를 알려 주려면 프론트가 기존 목록과 비교한다(서버는 교체 대상 학기만 알려줌).

**새 에러 코드**(명세 2.5 표에 먼저 추가)

| 코드 | HTTP | 언제 |
|---|---|---|
| IMPORT_UNSUPPORTED_FORMAT | 400 | xlsx·csv·txt·pdf 가 아님 → "엑셀(xlsx)로 저장해서 올려주세요" |
| IMPORT_TOO_LARGE | 400 | 5MB 초과 |
| IMPORT_UNRECOGNIZED | 400 | 제목이 수강신청확인서·전체 성적 보기가 아님, 표를 못 찾음 |
| IMPORT_STUDENT_MISMATCH | 403 | 파일 학번 ≠ 내 학번, 또는 파일에 학번이 없음 |

## 3. DB 변경

**없음.** `timetable_entries`, `completed_courses` 에 그대로 넣는다. 수강신청확인서의 학점·이수구분·재수강 여부는 시간표 테이블에 칸이 없어서 **미리보기에만** 보여준다(테이블은 바꾸지 않는다).

## 4. 클래스 구조 (새 패키지 `com.ordo.importer`)

```
importer/
├── controller/ImportController               POST /api/import
├── service/ImportService                     크기 확인 → 형식 판별 → Reader → 해석기 선택 → 학번 대조 → 초안
├── service/FileFormatDetector                앞부분 바이트로 XLSX / PDF / TEXT / 거절
├── reader/RawDocument                        record(List<List<String>> rows) — 행과 칸의 목록, 형식과 무관
├── reader/DelimitedTextReader                csv·txt: UTF-8 → 실패하면 MS949, 탭 있으면 TSV 아니면 CSV(따옴표·칸 안 줄바꿈 처리)
├── reader/XlsxReader                         Apache POI: 병합 영역 1개 = 칸 1개
├── reader/PdfReader                          PDFBox: 글자 위치로 줄·칸 재구성 (6절, 승인 시)
├── interpret/EnrollmentInterpreter           "수강신청확인서" → 시간표 초안
├── interpret/TranscriptInterpreter           "전체 성적 보기" → 이수내역 초안
├── interpret/ClassificationMapper            이수구분 글자·코드 → enum (모르면 null + 경고)
├── dto/ImportPreviewResponse, TimetableDraft, GradesDraft, ImportWarning
timetable/ … TimetableEntryBulkRequest + TimetableService.replaceOrMerge (bulk 저장)
academic/  … CompletedCourseBulkRequest.replaceTerms + 서비스 수정
```

### 4.1 형식 판별 (확장자는 보지 않음)
| 앞부분 바이트 | 처리 |
|---|---|
| `PK\x03\x04` | ZIP → `xl/workbook.xml` 이 있어야 xlsx. 없으면(docx, hwpx, ozd 등) 거절 |
| `%PDF-` | PDF |
| `D0 CF 11 E0` (OLE2) | 거절 — 옛 xls, hwp, doc 가 여기 해당 |
| PNG·JPEG·GIF·BMP·WEBP 시그니처 | 거절 |
| 그 외 | 텍스트로 시도: UTF-8(BOM 허용) 엄격 디코딩 → 실패하면 MS949 엄격 디코딩 → 둘 다 실패하거나 NUL·제어문자가 있으면 거절 |

### 4.2 xlsx 를 "병합 영역 = 칸"으로 읽는 이유 (요청과 다른 점 — 확인 필요)
요청은 "머리글 글자를 찾아서 열을 정하라"였는데, 성적표 xlsx 는 **머리글과 데이터 칸이 1:1 로 맞지 않는다**. '교과목' 머리글 하나 아래에 학수번호·과목명 두 칸, '이수구분' 아래에 코드·이름 두 칸, '평점/등급' 아래에 평점·등급 두 칸이 있다. 대신 이 파일은 **모든 논리적 칸이 병합 영역**이다(빈 칸도). 그래서 "그 행에서 시작하는 병합 영역 1개 + 병합 안 된 값 있는 셀 = 칸 1개"로 열 순서대로 읽으면 **csv·txt 와 행이 칸 수까지 똑같이 나온다**(성적 첫 행 15칸·이후 13칸, 신청 7칸). 학년도·학기는 여러 행에 걸친 세로 병합이라 첫 행에만 나오는 것도 csv 와 같다.
→ 열 위치를 하드코딩하지 않으면서 해석기는 형식과 완전히 무관해진다. 해석기 안에서는 머리글 글자로 표를 찾는다.

### 4.3 수강신청확인서 해석
- 제목(첫 칸, 공백 무시)이 `수강신청확인서`.
- `(\d{4})학년도` + `1학기/2학기/여름…/겨울…` → `academic_year`, `term` (못 찾으면 `IMPORT_UNRECOGNIZED`).
- `학번:` 다음 칸 → 학번 대조.
- 머리글 행: 공백을 지운 글자로 `학수번호-분반`, `과목명`, `학점`, `이수구분`, `재수강여부`, `교강사,강의시간,강의실` 열을 찾는다.
- 데이터 행: 순번이 숫자인 행. `1/1` 같은 쪽 번호에서 끝.
- `학수번호-분반` → 하이픈으로 나눔(`GEC0103-G09` → `GEC0103`, `G09`).
- 수업 칸: 쉼표로 나눈 조각마다 `교수명 요일HH:MM-HH:MM 강의실` 을 찾는다(정규식, 요일 월=1…일=7). 한 조각에 교수명이 빠지면 앞 조각의 교수명을 쓴다. 하나도 없으면(예: `박교수  온라인`) 행을 만들지 않고 `NO_TIME` 경고.
- 파일 안에서 시간이 겹치는 칸 → `OVERLAP` 경고(저장하면 409).

### 4.4 전체 성적 보기 해석
- 제목이 `전체 성적 보기`. `성명 … 학번 …` 행의 `학번` 다음 칸으로 대조.
- 과목 표: 머리글에 `교과목`·`평점/등급` 이 있는 행부터 시작. 다른 요약 표 제목(`원 성적 및 4.5 변환 성적`, `전공/교양별 평점`, `편입/인정/선수학점`)이 나오면 끝. 쪽 번호(`1/4`)·반복 머리글(성명·학번·학과 줄, 표 머리글)은 건너뛴다.
- 과목 행은 **뒤에서부터 센 위치**로 읽는다: `-13 학수번호, -12 과목명, -11 이수구분코드, -10 이수구분, -9 평가방법, -8 학점, -7 점수, -6 평점, -5 등급, -4 교강사, -3 폐기사유, -2 취득구분, -1 취득대학`.
- 학기: 행 길이가 15 이상이고 앞 두 칸이 `2026`·`1학기` 모양일 때만 새로 잡고, 아래 행에 이어서 쓴다.
- 폐기사유가 있는 행(재수강으로 지워진 과목)은 건너뛴다(경고에 "제외 n건"만).
- 학수번호 분반: 끝이 `G두자리`면 그게 분반, 아니면 마지막 두 자리.
- 등급: `A+ A0 B+ B0 C+ C0 D+ D0 F P NP` 만 인정. 그 외 → `grade=null`, `needs: GRADE` 경고.
- 형식이 안 맞는 줄은 멈추지 않고 건너뛰고 `UNPARSED_ROW` 경고(행 번호만, 내용은 안 넣음).

### 4.5 이수구분 → enum
| 파일 글자 (코드) | enum |
|---|---|
| 전공필수(04) | MAJOR_REQUIRED |
| 전공선택(05) | MAJOR_ELECTIVE |
| 전공기초(11) | MAJOR_BASIC |
| 일반선택(08) | GENERAL_ELECTIVE |
| 자유이수교과(17) | GEN_FREE |
| 배분이수교과(15) | GEN_DISTRIBUTION → 영역은 파일에 없음 → `needs: DISTRIBUTION_AREA` |
| 교직(06) | TEACHING |
| 교직전선(20) | TEACHING_MAJOR |
| **중핵교과(14), 기초교과(16?)** | **GEN_REQUIRED — 추정, 9장 미결사항** |
| 그 외 | `classification=null`, `needs: CLASSIFICATION`, 경고 (추측하지 않음) |

- 전공 과목은 `courses` 를 `(unit_name = 내 전공 course_unit, course_code)` 로 찾아 **교육과정과 다르면 경고**한다(`CLASSIFICATION_MISMATCH`).
- 이때 어느 쪽을 쓸지가 문제다. T6 규칙은 "교육과정 마스터가 있으면 마스터 우선(요청값 무시)"인데, 성적표는 **학교 공식 기록**이라 옛 학번은 성적표가 더 정확하다 → 9장 미결사항. 추천: **성적표 우선**(가져오기로 저장할 때만 bulk 요청에 `keepClassification: true` 를 붙여 자동 판별을 건너뜀).

### 4.6 개인정보·보안
- 업로드는 메모리에서만: `spring.servlet.multipart.file-size-threshold` 를 6MB 로 올려 디스크 임시파일을 만들지 않게 하고, `max-file-size: 5MB`, `max-request-size: 6MB`(공용 `application.yml` 변경).
- 파싱 중 예외는 `ImportService` 에서 잡아 원인(cause) 없이 `IMPORT_UNRECOGNIZED` 로 바꾼다 → `GlobalExceptionHandler` 가 내용이 담긴 스택트레이스를 로그에 남기지 않게.
- 로그에는 형식·행 수·경고 수만. POI·PDFBox 로그 수준은 WARN 이상.
- xlsx 압축 폭탄 방지: POI `ZipSecureFile` 의 압축률·크기 제한 사용. XML 외부 엔티티는 POI 기본 설정이 막는다.
- 학번 대조: 파일에 학번이 없거나 내 `studentNumber` 와 다르면 403.

## 5. 새 의존성 (★ 승인 필요)

| 라이브러리 | 용도 | 이유·대안 |
|---|---|---|
| `org.apache.poi:poi-ooxml` 5.x (Apache-2.0) | xlsx 읽기 | 병합 영역·공유 문자열·숫자 형식을 정확히 읽고, 압축 폭탄·XXE 방어가 들어 있다. 무겁다(전이 의존성 포함 약 15MB). 대안: JDK zip+XML 로 직접 읽기(150줄+보안 처리 직접) — 업로드 파일이 신뢰할 수 없는 입력이라 비추천 |
| `org.apache.pdfbox:pdfbox` 3.0.x (Apache-2.0) | pdf 읽기 | 6절 결정이 "지원"일 때만 추가. 약 3MB |
| (없음) csv | 직접 작성 | 따옴표·칸 안 줄바꿈만 처리하면 돼서 40줄 정도. commons-csv 불필요 |

버전은 승인 시 Maven Central 의 최신 안정판으로 확정한다.

## 6. PDF — 확인 결과와 제안 (결정 필요)

**한글 추출은 된다.** 샘플 PDF 2개 모두 Python pdfplumber 로 한글 640자·174자가 깨짐 없이 나왔다. 글꼴이 임베드되지 않았지만 `UniKS-UCS2-H`(유니코드로 바로 대응되는 한국어 표준 CMap)를 써서다. PDFBox 도 이 CMap 을 지원하므로 같은 결과가 나올 것으로 예상하지만, **구현 첫 단계에서 PDFBox 로 직접 다시 확인한다.**

**하지만 표 재구성이 어렵다.**
- 칸 안에서 줄바꿈된 글자가 다른 줄로 흩어진다. 예: `김교수 화15:00-16:50 전205, 김교수 목15:00-16:50` 이 과목 줄 **위에**, `전205` 가 **아래에** 나온다.
- 세로 병합된 `2026 1학기` 가 표 **가운데** 줄에 따로 나온다. 학기가 여러 개인 성적표에서는 어느 과목이 어느 학기인지 줄 순서만으로는 정할 수 없다.
- 해결하려면 글자 좌표(x, y)로 칸을 다시 맞추는 휴리스틱이 필요하고, 그래도 정확도가 낮다.

**선택지**
- **A (추천)**: PDF 는 맨 마지막 단계에서 PDFBox 좌표 기반 Reader 를 시도한다. 샘플 두 개로 기대값(6과목·7칸, 8과목·20학점)이 나오면 지원하되 항상 "정확하지 않을 수 있으니 미리보기를 확인하세요" 경고를 붙인다. 안 나오면 PDF 도 "엑셀로 저장" 안내로 거절한다(거짓으로 구현하지 않음).
- B: 처음부터 PDF 는 거절한다. 학교 시스템에서 xlsx 를 바로 받을 수 있어서 사용자 손해가 적다.

## 7. 샘플과 테스트

**테스트용 사본** (`src/test/resources/import/`, 원본은 절대 커밋 안 함)
- 이름 → `홍길동`, 학번 → `2026000000` 으로 바꾼 사본 10개(2종 × 5형식)를 만든다. 바꾸는 스크립트는 저장소 밖(스크래치)에 두고 결과물만 커밋한다.
- txt·csv: MS949 로 다시 저장. xlsx: 압축 안의 XML 에서 해당 문자열만 교체해 병합 구조를 그대로 유지하고, **포함된 이미지(사진·직인일 수 있음)는 지운다.** pdf: 내용 스트림의 UCS2 문자열을 같은 길이로 교체(안 되면 PDF 사본은 만들지 않고 보고).
- 커밋 전에 사본 전체에서 원래 이름·학번이 없는지 검색으로 확인한다.
- 교강사 이름(제3자)과 실제 성적은 그대로 남는다 → 교강사명도 `교수A…` 로 바꿀지 결정 필요(추천: 바꿈).

**테스트**
| 대상 | 확인할 것 |
|---|---|
| FileFormatDetector | xlsx·pdf·UTF-8 csv·MS949 txt 통과 / PNG·JPEG·OLE2(xls·hwp)·zip(xlsx 아님)·이진 거절 / 5MB 초과 |
| DelimitedTextReader | 탭·쉼표 판별, 따옴표 안 쉼표·줄바꿈, UTF-8 BOM, MS949 |
| XlsxReader | 병합 영역 = 칸, 세로 병합은 첫 행에만, 이미지 무시 |
| EnrollmentInterpreter | **6과목 18학점, 시간표 7행, NO_TIME 경고 1건**, 2026 SECOND, 요일·시간·강의실·교수 |
| TranscriptInterpreter | **8과목 20학점, 2026 FIRST**, 분반 분리(G19·01), P/N 과목, 배분이수 `needs`, 폐기 행 제외, 학기 이어 쓰기, 요약 표 무시, 모르는 이수구분 경고 |
| **형식 간 동일성** | 같은 내용의 xlsx·csv·txt → 해석 결과(초안)가 **완전히 같음** (PDF 는 별도 허용 오차) |
| ImportService | 학번 다르면 403, 학번 없으면 403, 제목 모르면 400 |
| 로그 | 가져오기 중 로그에 `홍길동`·`2026000000`·성적 문자열이 없음(OutputCapture) |
| bulk 저장 | 시간표 REPLACE 두 번 → 결과 같음, MERGE 중복 건너뜀, 겹침 409 / 이수내역 `replaceTerms` 두 번 → 중복 없음 |

## 8. 위험 요소

1. 학교 시스템이 내보내기 형식(칸 순서·제목·머리글 글자)을 바꾸면 해석이 깨진다 → 머리글 글자로 찾고, 실패 시 경고로 알려줌.
2. 학기가 여러 개인 긴 성적표 샘플이 없다(1학기분만 있음) → 쪽 넘김·학기 바뀜은 합성 테스트로만 확인됨. 실제 2학년 이상 성적표로 한 번 더 확인 필요.
3. 계절학기·편입 인정 학점 표기 형식 미확인 → 모르면 경고.
4. PDF 정확도(6절).
5. 이수구분 우선순위(4.5) 결정 전까지 bulk 저장 동작이 갈림.
6. POI 용량 증가(약 15MB) → 배포 이미지 크기.
7. 개인정보: 테스트 사본에 실제 성적·교강사 이름이 남을 수 있음(7절).

## 9. 내가(사용자) 직접 할 일

1. [ ] 이 계획 승인. 특히 ★ 표시 결정: **POI 의존성**, **PDF A/B**, **xlsx 병합 영역 방식(4.2)**, **REPLACE 규칙**, **이수구분 우선순위**, **교강사명 익명화**.
2. [ ] (가능하면) 2학년 이상 선배·팀원의 **여러 학기 성적표 xlsx** 를 익명화해서 받아 두기 — 학기 바뀜·쪽 넘김 확인용.
3. [ ] 프론트 담당에게: 업로드 화면(파일 선택 → 미리보기 표 → 이수구분·배분영역 고르기 → 저장).

## 10. 작업 순서 (승인 후, 단계마다 `docs/PROGRESS.md` 갱신)

1. **bulk 저장 먼저**: `POST /api/timetables/entries/bulk`(REPLACE/MERGE), 이수내역 bulk 에 `replaceTerms`(+ 결정 시 `keepClassification`) (+테스트)
2. 테스트용 익명 사본 10개 만들기 → 원본 정보 없는지 검색 확인
3. `RawDocument` + `DelimitedTextReader` + `FileFormatDetector` (+테스트)
4. `EnrollmentInterpreter` + `TranscriptInterpreter` + `ClassificationMapper` (csv·txt 로 기대값 확인)
5. POI 추가 → `XlsxReader` + **형식 간 동일성 테스트**
6. `ImportController`·`ImportService`: 학번 대조, multipart 설정, 에러 코드, 로그 개인정보 테스트 → 로컬 서버로 실제 파일 업로드 확인
7. PDF: PDFBox 로 샘플 추출 확인 → 결정 A 면 `PdfReader`, 안 되면 거절로 마무리
8. 명세 반영(2.5, 4장, 8장, 9장), FRONTEND_GUIDE 업로드 절, 커밋 제안

**T11(카카오)과의 전체 순서**: 이 문서 1단계(bulk) → 2~6단계(가져오기) → 카카오 알림(그동안 사용자는 카카오 체크리스트 진행) → PDF(7단계). 가져오기는 외부 설정 없이 끝까지 검증할 수 있고, 카카오는 사용자의 개발자 콘솔 설정과 실기기 확인이 필요해서 기다리는 시간이 생기기 때문.

## 11. 명세 9장에 올릴 미결사항 (승인 시 추가)

| # | 내용 | 임시 결정 |
|---|---|---|
| 16 | 중핵교과·기초교과 → GEN_REQUIRED | 추정. 근거: 성적표 이수구분 코드 14 = 명세 3.1 "교양 필수교과 14, 16". 기초교과 코드 확인 필요 |
| 17 | 가져오기 이수구분: 성적표 vs 교육과정 마스터가 다를 때 | 추천: 성적표(공식 기록) 우선 |
| 18 | 같은 파일 재업로드 | 학기 단위 교체(REPLACE) |
| 19 | PDF 지원 | 6절 A/B |

공용 파일 변경 예정: `ErrorCode`(4개), `application.yml`(multipart), `build.gradle`(POI, PDFBox), `AGENTS.md`(패키지 목록에 importer).
