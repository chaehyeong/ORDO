# 진행 기록 (PROGRESS)

> 작업 단계가 끝날 때마다 갱신한다: 한 일 · 검증 방법 · 남은 일 · 결정 사항. 계획은 `docs/plans/`, 기준은 `docs/BACKEND_SPEC.md`.

## 현재 상황 (2026-10-21)

- 명세 T0~T10 + **T12 구현 완료**(xlsx·csv·txt, PDF 는 거절, 테스트 133개). `feat/import` 푸시됨. PR #3 ← #5 ← #6 ← #7 ← #8 ← feat/import 리뷰 대기.
- T11 은 카카오 '나에게 보내기'가 휴대폰 알림이 안 울려서 **웹푸시로 계획을 다시 씀**(`docs/plans/T11-web-push.md`) → 사용자 승인 대기(계획서 13절).
- 2026-10-16: 팀 회의 메모에서 백엔드 추가분 2개를 계획서로 씀 — **T13 평점 평균 API**, **T14 홈 오늘 일정에 e캠퍼스 과제**(승인 대기). 온강(시간 없는 수업)·졸업 길 데이터·복수전공 일정은 **보류**.
- 2026-10-16: T13·T14 계획 승인(추천안 그대로). **T13 완료**(`feat/gpa`, cf4260d), **T14 완료**(`feat/home-assignments`, feat/gpa 위, bdbac7c, 테스트 146개). 두 브랜치 푸시됨, PR 은 아직 없음(base: feat/gpa → feat/import, feat/home-assignments → feat/gpa).
- 2026-10-21: T11 은 **웹푸시로 구현하기로 확정**(세부 선택은 계획 13절). FRONTEND_GUIDE 에 마이페이지 mock 필드 ↔ API 대응표 추가.
- 다음: PR 2개 생성(사용자), 실제 e캠퍼스 피드로 홈 확인(사용자), B 확인(PR #3~#9 리뷰·명세 9장 16), T11 구현.

## 결정 사항 (2026-10-08 승인)

- 번호: 카카오 알림 = **T11**, 파일 가져오기 = **T12** (명세 T10 = e캠퍼스와 겹치지 않게).
- T11: Flyway **V11**(`kakao_connections`, `notification_logs`), 새 의존성 없음, 할 일 알림 기준 09:00 은 9장 12 로 확인 대기.
- T12: Apache POI 추가, xlsx 는 **병합 영역 1개 = 칸 1개**로 읽음, 재업로드는 **학기 단위 교체(REPLACE)**, 이수구분은 **성적표 우선**(`keepClassification`), PDF 는 7단계 확인 후 **거절**, 테스트 사본은 이름·학번·**교수명**까지 익명화.

## T12 성적·시간표 파일 가져오기

| 단계 | 상태 | 한 일 | 검증 | 남은 일 |
|---|---|---|---|---|
| 0. 계획·명세 반영 | ✅ | `docs/plans/T12-import.md`, 명세 2.1·2.5·4.5·4.6·4.9·7·8·9장 | 문서 검토 | |
| 1. bulk 저장 API | ✅ | `POST /api/timetables/entries/bulk`(REPLACE/MERGE, 겹치면 409·아무것도 안 바뀜), 이수내역 bulk 에 `replaceTerms`·`keepClassification` | 단위 테스트 5개(교체·병합·겹침·학기 교체·성적표 우선) | |
| 2. 익명 테스트 사본 | ✅ | `src/test/resources/import/` 에 enrollment·transcript × xlsx·csv·txt·pdf 8개. 이름→홍길동, 학번→2026000000, 교수명→가명, xlsx·pdf 이미지 제거. 스크립트는 저장소 밖(스크래치) | 원래 값 검색: 파일 바이트·xlsx 내부 XML·PDF 스트림·PDF 글자 이어 붙이기 모두 0건 | |
| 3. Reader·형식 판별 | ✅ | `FileFormatDetector`(앞부분 바이트, UTF-8→MS949), `DelimitedTextReader`(탭/쉼표, 따옴표·칸 안 줄바꿈), `RawDocument`(빈 칸만 있는 행 = 빈 행) | 이미지·OLE2·다른 zip·이진·5MB 초과 거절, UTF-8·MS949 둘 다 읽힘 | |
| 4. 해석기 | ✅ | `EnrollmentInterpreter`, `TranscriptInterpreter`, `ClassificationMapper`, `TermText` | 기대값: 6과목 18학점 7칸 + NO_TIME 1 / 8과목 20학점 2026-1. 학기 이어 쓰기·폐기 제외·반복 머리글·모르는 이수구분 합성 테스트 | |
| 5. xlsx(POI) | ✅ | `XlsxReader`: 병합 영역 1개 = 칸 1개, 압축 폭탄 한도 | **xlsx·csv·txt 해석 결과 완전히 같음** 테스트 | |
| 6. /api/import | ✅ | `ImportController`·`ImportService`: 학번 대조(다르거나 없으면 403), multipart 5MB·메모리, 파싱 예외는 원인 없이 바꿔 로그에 내용이 안 남게 | 단위 테스트(로그에 홍길동·학번·과목명 없음) + 서버 실제 업로드 19개 확인(재업로드 중복 없음, 이수현황 20학점 반영), 서버 로그 개인정보 0건 | |
| 7. PDF 시도 | ✅ 거절로 결정 | PDFBox 3.0.7 로 익명 사본 확인: 한글 추출 정상(깨진 글자 0), 글자 위치도 정확. 하지만 2줄 칸이 위아래 줄로 갈라지고, 세로 병합된 '2026 1학기'가 과목 줄 사이에 따로 나오고, 빈 칸은 그려지지 않아 표를 그대로 읽을 수 없음. PDF 는 형식 판별 단계에서 거절(IMPORT_UNSUPPORTED_FORMAT), PDFBox 의존성은 넣지 않음 | 거절 테스트 | 원본 PDF 의 칸 테두리로 표를 재구성하는 방법은 여러 학기 샘플이 생기면 재검토(명세 9장 19) |

결정 메모: 미리보기 응답은 계획서 예시(평평한 구조)와 달리 `{ type, format, timetable | grades, warnings }` 로 묶었다(종류별로 필요한 칸만 채우려고). 명세 4.9 에 반영.

## T11 일정 알림 (카카오 → 웹푸시)

결정(2026-10-09, 사용자): 카카오 '나에게 보내기'는 메시지가 와도 휴대폰 알림이 울리지 않는다(카카오 데브톡 답변). 알림톡은 사업자등록증·딜러 계약·건당 비용, 톡캘린더는 권한 심사(개인 개발자 승인 불확실)라서 **웹푸시로 변경**. 카카오 계획 파일은 `T11-web-push.md` 로 이름을 바꿔 다시 썼다(옛 내용은 git 기록에 있음).

| 단계 | 상태 | 한 일 | 검증 | 남은 일 |
|---|---|---|---|---|
| 0. 카카오 계획·명세 반영 | ✅ (폐기) | `T11-kakao-alarm.md`, 명세 2.1·2.5·4.10·7·8·9장 | 문서 검토 | |
| 0b. 웹푸시 계획 | ✅ | `docs/plans/T11-web-push.md`: API 4개, V11(`push_subscriptions` + `schedules.alarm_sent_for`), 새 의존성 없이 JDK 암호화 + 기존 jjwt 로 VAPID, endpoint 허용 목록(SSRF 방지), 프론트 할 일, 사용자 체크리스트. 명세 4.10 과 FRONTEND_GUIDE 진행표에는 "변경 승인 대기" 표시만 | VAPID 키 생성 PowerShell 명령을 실제로 실행해 길이 확인(공개키 87자·개인키 43자) | 사용자 승인(계획 13절) → 명세 교체(계획 12절) |
| 1~7 | ⏳ | | | |

## T13 학사관리 평점 평균 (4.3 만점)

| 단계 | 상태 | 한 일 | 검증 | 남은 일 |
|---|---|---|---|---|
| 0. 계획 | ✅ | `docs/plans/T13-gpa.md`: `GET /api/academic/gpa`, 4.3 척도(성적표 평점 칸으로 확인), 셋째 자리 반올림(성적표 3.417 과 일치), P·NP 제외, 재수강 최근 1건, DB 변경 없음 | 성적표 익명 사본의 요약 값(3.417·전공 3.3)으로 척도·반올림 확인 | |
| 1. 브랜치 | ✅ | `feat/import` 위에 `feat/gpa` | | |
| 2. 계산 + 단위 테스트 | ✅ | `AcademicGpaService.calculate`(순수 정적), `CompletedCourse.retakeKey()` 로 재수강 키를 이수현황과 공용 | `AcademicGpaServiceTest` 12개 | |
| 3. DTO·서비스·컨트롤러 | ✅ | `GpaSummary`, `AcademicGpaService.getGpa`, `GET /api/academic/gpa` | 전체 테스트 145개 통과 | |
| 4. 로컬 서버 확인 | ✅ | 익명 성적표 샘플 8과목 bulk 저장 후 조회 | 3.417·전공 3.3·18·20(성적표와 같음), 재수강 F 반영, 학기별, 남의 데이터 분리, 401, 이수현황 영향 없음 등 10개 | |
| 5. 문서 | ✅ | 명세 4.6·5장·8장·9장 20·21, FRONTEND_GUIDE, 계획서 | | 커밋 승인 |

## T14 홈 오늘 일정에 e캠퍼스 과제

| 단계 | 상태 | 한 일 | 검증 | 남은 일 |
|---|---|---|---|---|
| 0. 계획 | ✅ | `docs/plans/T14-home-assignments.md`: timeline 에 `source: ECAMPUS`(마감 시각, 없으면 23:59) + `courseName`·`url`, e캠퍼스 호출 1번, DB 변경 없음 | 현재 HomeService 코드 확인 | |
| 1. 브랜치 | ✅ | `feat/gpa` 위에 `feat/home-assignments`(계획은 feat/import 위였으나, 명세·가이드·PROGRESS 의 같은 줄 근처를 두 브랜치가 고쳐 충돌하므로 이어 쌓음) | | |
| 2. DTO·서비스 + 테스트 | ✅ | `TimelineItem` 에 `courseName`·`url`, `HomeService.ecampusEvents`(요청 1번 → 점·timeline 공용), ECAMPUS 항목(마감 시각, 없으면 23:59), 정렬 수업→일정→e캠퍼스 | `HomeServiceTest` 1개 추가·3개 보강, 전체 146개 통과 | |
| 3. 로컬 서버 확인 | 🔶 | 미연동·가짜 토큰(실패) 경로 | 홈 정상, 기존 항목 `courseName`·`url` null, 토큰이 응답·로그에 없음 4개 | **실제 피드로 마감일 홈 확인**(사용자 피드 주소 필요) |
| 4. 문서 | ✅ | 명세 4.7·4.8·8장, FRONTEND_GUIDE 홈 절, 계획서 | | 커밋 승인 |
