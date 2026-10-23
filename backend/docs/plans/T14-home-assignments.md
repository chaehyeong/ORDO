# T14 홈 "오늘 일정"에 e캠퍼스 과제 표시 계획

> 상태: **계획, 승인 대기 (2026-10-16).** 진행 기록은 `docs/PROGRESS.md`.
> 출처: 팀 회의 메모 "오늘 일정에 과제도 뜨게 (과제는 마감이 시간인 걸로)".

---

## 1. 현재 코드 상태 (2026-10-16)

- `GET /api/home` 의 `timeline`(오늘 일정)은 **시간표 수업(TIMETABLE) + 시간 있는 일정(SCHEDULE)** 만 있다.
- e캠퍼스 과제 마감(T10, `EcampusService.getEvents`)은 지금 **주간 점(`week.categories` 의 ASSIGNMENT)** 에만 쓴다. 이유: 제출 여부를 몰라서 할 일·추천에는 넣지 않기로 했다(명세 4.7).
- 사용자가 직접 등록한 과제 일정(카테고리 `ASSIGNMENT`)은 시간이 있으면 이미 `timeline` 에, 없으면 `todos` 에 나온다.
- e캠퍼스 이벤트: `{ id, title, courseName, date, time(null 이면 그날 23:59), url }`.
- DB 변경, 새 에러 코드, 새 의존성은 필요 없다.

## 2. 목표

- 오늘 마감인 e캠퍼스 과제를 `timeline` 에 **마감 시각을 시작 시각으로** 넣는다. 예: 23:59 마감 과제는 하루의 맨 끝에 뜬다.
- 프론트가 과목명과 e캠퍼스 링크를 같이 보여줄 수 있게 한다.

이번 범위가 아닌 것: 제출 여부 표시(피드에 없음), e캠퍼스 과제를 `todos` 에 넣기, 알림, "오늘 할 일" 박스 삭제(프론트 디자인 작업이고, 백엔드는 `todos` 를 그대로 둔다. 안 쓰면 프론트가 무시하면 된다).

## 3. API 변경 (`GET /api/home`, 하위 호환)

`timeline` 원소에 `source: "ECAMPUS"` 가 생기고, 원소 모양에 선택 필드 2개가 더해진다. 기존 필드와 값은 그대로다.

```json
"timeline": [
  { "source": "TIMETABLE", "id": 3,  "title": "타이포그래피 I", "category": "LECTURE",
    "startTime": "09:00", "endTime": "10:30", "location": "예405", "courseName": null, "url": null },
  { "source": "SCHEDULE",  "id": 51, "title": "세시 팀플 모임", "category": "PERSONAL",
    "startTime": "12:00", "endTime": null, "location": null, "courseName": null, "url": null },
  { "source": "ECAMPUS",   "id": null, "title": "3주차 과제 제출", "category": "ASSIGNMENT",
    "startTime": "23:59", "endTime": null, "location": null,
    "courseName": "디자인씽킹", "url": "https://khcanvas.khu.ac.kr/..." }
]
```

- `ECAMPUS` 항목: `id` 는 null(e캠퍼스 id 는 문자열이라 Long `id` 에 못 넣음), `startTime` = 마감 시각(시각이 없는 하루 종일 마감은 **23:59**, 4.8 규칙과 같음), `endTime`·`location` 은 null, `category` 는 항상 `ASSIGNMENT`.
- `courseName`, `url`: ECAMPUS 항목에만 값이 있고 나머지는 null.
- 정렬: 시작 시각 순, 같은 시각이면 **수업 → 직접 등록한 일정 → e캠퍼스** 순(기존 "수업 먼저" 규칙의 연장).
- 오늘 날짜(`date` 파라미터 기준) 마감만 넣는다. e캠퍼스 연동을 안 했거나 못 받아 오면 e캠퍼스 항목만 빠지고 홈은 정상 응답(지금 주간 점과 같은 처리).
- 사용자가 같은 과제를 직접 등록해 둔 경우 중복해서 보일 수 있다(제목 비교로 걸러내지 않는다: 오탐 위험이 더 큼).

## 4. 코드 변경

```
home/
├── dto/HomeResponse.TimelineItem     courseName, url 필드 추가 (record 뒤쪽에 2개)
└── service/HomeService               e캠퍼스 이벤트를 한 번만 받아 (1) 주간 점 (2) 오늘 timeline 둘 다에 사용
```

- `ecampusDueDays(...)`(날짜 집합만 돌려줌)를 `ecampusEvents(...)`(이벤트 목록, 실패 시 빈 목록)로 바꾸고 날짜 집합은 그 목록에서 만든다. **e캠퍼스 요청은 지금처럼 1번만** 한다(주 단위 조회 한 번에서 오늘 것도 고름).
- 정렬 기준(`TIMELINE_ORDER`)의 동순위 규칙에 ECAMPUS 를 가장 뒤로 넣는다.
- 기존 `new TimelineItem(...)` 호출 두 곳에는 `null, null` 을 추가한다.

## 5. 파일 목록

| 구분 | 파일 |
|---|---|
| 수정 | `home/dto/HomeResponse.java`, `home/service/HomeService.java` |
| 테스트 수정·추가 | 기존 `HomeServiceTest`(있다면)에 케이스 추가, 없으면 새로 |
| 문서 | `BACKEND_SPEC.md`(4.7 응답 예시·설명, 8장 행), `FRONTEND_GUIDE.md`(홈 절), `PROGRESS.md` |

공용 파일 변경 없음. Flyway 변경 없음. 새 의존성 없음.

## 6. 테스트 계획 (`EcampusService` 는 Mock)

| 상황 | 기대 |
|---|---|
| 오늘 15:00 마감 과제 1건 | timeline 에 ECAMPUS 항목(startTime 15:00, courseName·url 있음) |
| 시각 없는 하루 종일 마감 | startTime 23:59 |
| 오늘이 아닌 날(주중 다른 날) 마감 | timeline 에는 없고 주간 점(ASSIGNMENT)에만 있음 |
| 같은 15:00 에 수업·일정·과제 | 수업 → 일정 → 과제 순 |
| e캠퍼스 미연동(`connected=false`) | ECAMPUS 항목 없음, 나머지 정상 |
| e캠퍼스 실패(`BusinessException`) | ECAMPUS 항목·점만 빠지고 정상 응답 |
| 기존 항목 | `courseName`·`url` 이 null, 기존 값은 그대로 |
| 호출 횟수 | `getEvents` 가 요청당 1번만 호출됨 |
| 실서버 | 사용자의 실제 e캠퍼스 피드로 `GET /api/home?date=마감일` 확인(토큰은 응답·로그에 나오지 않는지 포함) |

## 7. 위험

1. **제출을 이미 한 과제도 뜬다.** 피드에 제출 상태가 없다(명세 4.8). 안내 문구(예: "e캠퍼스 마감")는 프론트가 붙인다.
2. 프론트가 `source` 를 모르는 값으로 받으면 깨질 수 있다. FRONTEND_GUIDE 에 ECAMPUS 를 명시하고, 새 값이라 프론트가 반영해야 한다는 점을 알린다. (기존 응답에 필드를 더하는 방식이라 안 읽으면 무시된다.)
3. 마감 시각 해석(Canvas 의 `DTSTART` 시간대)은 T10 에서 실제 피드로 검증한 파서를 그대로 쓴다. 새 해석을 만들지 않는다.

## 8. 작업 순서 (승인 후, 단계마다 `docs/PROGRESS.md` 갱신)

1. 브랜치 `feat/home-assignments`(T13 과 같은 기준 브랜치 위, 승인 후 생성)
2. DTO·서비스 수정 + 테스트
3. 로컬 서버에서 실제 e캠퍼스 피드로 확인
4. 명세·FRONTEND_GUIDE 반영, 커밋 제안(승인 후에만 커밋)

## 9. 승인되면 바꿀 명세 목록

- 4.7: `timeline` 설명에 e캠퍼스 과제 추가(예시에 ECAMPUS 한 줄, `courseName`·`url` 설명), "e캠퍼스 마감은 할 일에는 넣지 않는다"는 문장은 그대로 유지
- 8장에 `T14 | 홈 오늘 일정에 e캠퍼스 과제 (4.7) | A | ECAMPUS 항목, 23:59 규칙, 정렬, 미연동·실패 시 정상, e캠퍼스 호출 1번`

## 10. 승인받을 것

1. `timeline` 에 `source: "ECAMPUS"` 항목과 `courseName`·`url` 필드 추가(하위 호환).
2. 시각 없는 마감은 23:59 로 표시.
3. 직접 등록한 과제와 중복돼도 그대로 둠.
4. 어느 브랜치 위에서 시작할지: 지금은 `feat/import` 가 PR #8 위에 쌓여 있다. 앞선 PR 이 머지되기 전이라 **`feat/import` 위에 `feat/gpa`, `feat/home-assignments` 를 쌓는 것**이 가장 간단하다(추천). 두 작업은 서로 독립이라 어느 쪽이 먼저 머지돼도 된다.
