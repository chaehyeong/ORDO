# T22 성적표·수강신청확인서 가져오기 화면

작성: 2026-11-01. 상태: **구현과 확인 완료.** 프론트 수정은 사용자가 이번 작업에 한해 승인했다.

## 목표

백엔드에 이미 있는 파일 가져오기(T12)를 화면에서 쓸 수 있게 한다. 페이지에 버튼을 하나씩 넣고, 파일을 고르면 내용을 확인한 뒤 저장한다.

- 시간표: `수업 추가` 옆 `수강신청확인서 불러오기`
- 졸업 로드맵: `+이수한 과목 추가하기` 옆 `성적표 불러오기`

처음에는 추가 창 안에 넣었으나, 김채형 의견에 따라 페이지의 추가 버튼 옆으로 옮겼다. 창을 열지 않아도 버튼이 보인다.

## 범위

- 프론트만 수정한다. 백엔드 코드, API, DB는 바꾸지 않는다.
- 새 라이브러리를 넣지 않는다.

**범위 밖**

- 토요일 수업 표시: 사용자 확인 결과 해당 수업이 없다. 시간표는 지금처럼 월~금만 그린다.
- 시간표 드롭다운의 계절학기 추가.
- 파일 형식 추가. PDF는 지금처럼 받지 않는다.

## 화면 동작

1. 버튼을 누르면 파일 선택 창이 열린다. `.xlsx`, `.csv`, `.txt`만 고를 수 있다.
2. `POST /api/import`로 올린다. 이 호출은 미리보기만 주고 저장하지 않는다. 올리는 동안 버튼을 막고 `읽는 중…`으로 표시한다.
3. 응답의 `type`이 버튼 종류와 다르면 안내하고 멈춘다.
4. 확인 창을 띄운다.
   - 요약: `2026년 2학기 · 6과목 · 18학점`처럼 표시한다.
   - 과목 목록: 과목이 많으면 목록 안에서 스크롤한다.
   - 경고: `warnings`를 표시한다. 고르는 칸으로 해결되는 경고(`DISTRIBUTION_AREA_REQUIRED`, `UNKNOWN_CLASSIFICATION`, `UNKNOWN_GRADE`)는 빼고 해당 칸을 표시한다.
   - 덮어쓰기 안내: 그 학기에 직접 넣은 수업이나 과목도 파일 내용으로 바뀐다고 알린다.
5. 성적표는 `needs`가 있는 과목을 목록 맨 위에 두고 고르는 칸을 보여 준다. 저장 버튼이 막혀 있으면 이유를 문구로 알린다.

   | `needs` | 고르는 값 | 안 고르면 |
   |---|---|---|
   | `CLASSIFICATION` | 이수구분 | 저장 버튼 비활성 |
   | `DISTRIBUTION_AREA` (또는 이수구분을 배분이수로 고른 경우) | 영역 1~5 | 저장 버튼 비활성 |
   | `GRADE` | 성적 | 저장 가능, 성적 없이 저장 |

6. 저장한다.
   - 시간표: `POST /api/timetables/entries/bulk`에 `{ year, term, mode: "REPLACE", entries }`
   - 성적: `POST /api/academic/completed-courses/bulk`에 `{ courses, replaceTerms: terms, keepClassification: true }`
7. 저장에 성공하면 화면을 다시 불러온다. 시간표는 파일의 학기로 이동한다. 그 학기가 드롭다운에 없으면 저장됐지만 목록에서 볼 수 없다고 안내한다.

과목이 0개인 파일은 서버가 저장을 거절하므로(`@NotEmpty`) 저장 버튼을 막는다.

## 버튼 디자인

새 모양을 만들지 않고 옆 버튼의 클래스를 그대로 쓴다.

| 페이지 | 쓰는 클래스 | 배치 |
|---|---|---|
| 시간표 | `.tt-add-btn` | `수업 추가` 왼쪽. 두 버튼 묶음이 오른쪽 끝에 붙는다 |
| 졸업 로드맵 | `.completed-add-btn` | `+이수한 과목 추가하기` 왼쪽, 간격 12px |

창이 좁으면 두 곳 모두 버튼이 다음 줄로 내려간다(`flex-wrap`). 확인 창은 `수업 추가` 창의 클래스(`course-backdrop`, `course-modal`, `course-title`, `course-input`, `course-actions`, `course-cancel`, `course-confirm`)를 재사용한다.

## 수정 파일

| 파일 | 할 일 | 규모 |
|---|---|---|
| `frontend/src/components/ImportButton.js` (새 파일) | 파일 선택, 업로드, 확인 창, 저장 | 193줄 |
| `frontend/src/components/ImportButton.css` (새 파일) | 비활성 표시, 과목 목록, 경고, 안내 문구 | 64줄 |
| `frontend/src/components/AddCompletedCourseModal.js` | `GRADES` 내보내기 | 1줄 |
| `frontend/src/components/CompletedCourses.js`, `.css` | 머리글에 `importButton` 자리 추가, 줄바꿈 | 약 15줄 |
| `frontend/src/pages/TimeTable.js`, `.css` | 버튼 추가, `reloadKey`, 저장 후 학기 이동, 줄바꿈 | 약 20줄 |
| `frontend/src/pages/RoadMap.js` | 버튼을 `CompletedCourses`에 넘기기, 저장 후 다시 불러오기 | 9줄 |
| `backend/docs/PROGRESS.md`, `backend/docs/FRONTEND_GUIDE.md` | 진행 기록, 현황 표 | 문서만 |

합계 프론트 8개 파일, 303줄 추가, 7줄 삭제. 계획의 약 190줄보다 늘었다.

## 설계

```jsx
<ImportButton
  kind="TIMETABLE"            // 또는 "GRADES"
  label="수강신청확인서 불러오기"
  className="tt-add-btn"      // 옆 버튼의 클래스
  onSaved={(draft) => { /* 다시 불러오기 */ }}  // 시간표면 timetable, 성적표면 grades
/>
```

재사용하는 코드:

- `api()`: `FormData`를 이미 지원한다(`client.js` 41~44줄).
- `DISTRIBUTION_AREAS`, `GRADES`(`AddCompletedCourseModal.js`), `CLASSIFICATION_LABELS`, `TERM_LABELS`(`CompletedCourses.js`).
- `reloadKey` 방식(`RoadMap.js`, `Calendar.js`, `Degree.js`).

구현할 때 지킨 점:

- `CompletedCourses`는 `ImportButton`을 직접 부르지 않고 `importButton` 값으로 받는다. `ImportButton`이 `CompletedCourses`의 상수를 쓰므로 직접 부르면 순환 import가 된다.
- 머리글 묶음 클래스는 `completed-header-btns`다. `completed-actions`는 표의 수정·삭제 버튼 묶음이고 평소 숨겨져 있어서 쓰면 안 된다.
- 파일을 고른 뒤 `input.value`를 비운다. 그래야 같은 파일을 다시 고를 수 있다.
- `courses`는 가이드대로 필요한 필드만 골라 보낸다.
- 업로드와 저장은 `try/finally`로 감싸, 서버가 꺼져 있어도 버튼이 막힌 채로 남지 않게 한다.
- 파일 내용과 미리보기는 화면 상태에만 두고, `console.log`나 `localStorage`에 남기지 않는다.
- 확인 창은 `createPortal`로 `body`에 띄운다.

## 오류 처리

서버 메시지를 다른 화면처럼 `alert(res.error.message)`로 보여 준다. 저장 오류가 나면 확인 창은 닫지 않는다.

| 상황 | 응답 | 결과 |
|---|---|---|
| PDF 등 지원하지 않는 파일 | 400 `IMPORT_UNSUPPORTED_FORMAT` | 엑셀로 저장해 올리라는 안내 |
| 5MB 초과 | 400 `IMPORT_TOO_LARGE` | 안내 |
| 수강신청확인서·성적표가 아닌 파일 | 400 `IMPORT_UNRECOGNIZED` | 안내 |
| 계정과 파일의 학번이 다름 | 403 `IMPORT_STUDENT_MISMATCH` | 안내 |
| 버튼과 다른 종류의 파일 | 200, `type` 불일치 | 화면에서 안내하고 멈춤 |
| 파일 안에서 수업 시간이 겹침 | 409 `TIMETABLE_OVERLAP` | 안내, 저장되지 않음 |
| 서버 꺼짐 등 응답 없음 | 요청 실패 | 일반 안내 |

## 확인 결과

프론트 테스트 파일은 `App.test.js` 하나뿐이고 `react-router/dom`을 찾지 못해 실행되지 않는다. 그래서 브라우저에서 시나리오로 확인했다. 익명 샘플(`backend/src/test/resources/import/`)과 학번 `2026000000` 테스트 계정을 썼고, 백엔드는 8081번, 프론트는 3001번으로 띄웠다.

**시간표**

- [x] xlsx, csv, txt 모두 6과목 18학점, 저장 후 시간표 8칸이 보인다.
- [x] 시간이 없는 과목이 온라인 강의 칸에 보인다.
- [x] 다른 학기를 보다가 저장하면 파일의 학기로 이동한다.
- [x] 같은 파일을 다시 저장해도 칸이 늘지 않고, 직접 넣은 수업은 파일 내용으로 바뀐다.

**성적표**

- [x] xlsx, csv, txt 모두 8과목 20학점이다.
- [x] 배분이수 영역을 고르기 전에는 저장 버튼이 막혀 있다.
- [x] 이수구분·영역·성적을 고르는 칸이 필요한 과목에만 나오고, 고른 값이 저장된다.
- [x] 저장 후 이수 과목 표, 평점 3.417, 곡선이 바뀐다.
- [x] 같은 파일을 다시 저장해도 8과목 그대로이고, 직접 넣은 과목은 파일 내용으로 바뀐다.

**공통**

- [x] 버튼이 옆 버튼과 같은 모양이고, 1024px 창에서 한 줄에 들어간다.
- [x] 표의 수정·삭제 버튼은 줄에 마우스를 올릴 때만 보인다.
- [x] PDF, 다른 학번 파일, 종류가 다른 파일, 시간 겹침, 서버 꺼짐에서 안내가 뜨고 저장되지 않는다.
- [x] 확인 창에서 취소하면 아무것도 바뀌지 않는다.
- [x] 같은 파일을 연달아 골라도 동작한다.
- [x] `CI=true` 프로덕션 빌드가 통과한다.

다른 학번, 시간 겹침, 서버 꺼짐, 이수구분·성적 고르기는 창 안 버튼 버전에서 확인했다. 페이지로 옮길 때 이 코드는 바뀌지 않았다.

**사용자 확인**: 실제 수강신청확인서와 성적표는 사용자가 직접 한 번 올려 본다. 실제 파일은 커밋하거나 출력하지 않는다.

## 남는 한계

- 계절학기 파일과 최근 4개 학기 밖의 파일은 저장돼도 시간표 드롭다운에서 볼 수 없다.
- 계정 학번과 파일 학번이 정확히 같아야 한다. 발표 계정의 학번을 미리 맞춰 둔다.
- 저장하면 그 학기의 기존 데이터가 파일 내용으로 바뀌고, 되돌리기 기능은 없다.
- 시간표 가져오기는 학기 전체를 바꾼다. 수업 몇 개만 골라 넣는 기능은 만들지 않는다.
