# T21 부전공 등록·요건·수강 후보

2026-10-31 사용자의 "개발할 수 있는 부분은 개발" 요청 범위. BACKEND_SPEC의 부전공 제외 결정을 이 범위로 갱신한다.

## 근거와 범위

- 교육과정 p115·116·118: 부전공을 하더라도 단일/다전공의 원래 요건을 충족해야 하며, 그 과정의 이수 교과목과 부전공 교과목은 중복 인정하지 않는다.
- 시드 2020~2026 졸업요건 393행 중 346행에 부전공 총학점이 있다. 2026학번은 64개 중 53개.
- 요구 학점은 저장된 `minor_required/elective/total_credits`를 사용한다. NULL은 미기재로 반환하며, 총학점이 없거나 0 이하이면 등록하지 않는다. 총학점이 명시된 융합전공도 추적할 수 있다. 목록은 학교의 신청 가능 여부 판정을 대신하지 않는다.
- 부전공 지정 과목은 학과 시행세칙의 별도 확인이 필요하다. 현재 전공 교과목의 전공필수·전공선택은 **후보**로만 제시하며, 전공기초·교직은 후보에 넣지 않는다.
- 등록은 학교의 승인/신청을 대신하지 않는다. 오르도에서 추적할 부전공은 1개다. 주전공 또는 복수전공과 같은 전공은 등록할 수 없다.

## 계약

- 공개 `GET /api/catalog/minors?admissionYear=2026&collegeId=`: 해당 학번에 부전공 총학점 > 0이 있는 전공을 이름/id 순으로 반환한다. 항목은 `id, displayName, collegeId, collegeName, requiredCredits, electiveCredits, totalCredits, majorCoursesAvailable`.
- 인증 `GET /api/academic/minor`: 미등록이면 `data:null`, 등록했으면 아래 응답.
- 인증 `PUT /api/academic/minor`: `{majorId:14}`로 등록/변경 후 GET과 같은 응답.
- 인증 `DELETE /api/academic/minor`: 등록이 없어도 204. 다른 사용자의 부전공에는 영향 없음.

응답 필드:

- `major:{id,displayName}`, `admissionYear`, `catalogYear:2026`.
- `requirements:{requiredCredits,electiveCredits,totalCredits}`: 입학년도별 요구 학점 원본. NULL 유지.
- `majorCoursesAvailable`: 부전공 전공필수·전공선택 자료가 실제로 있는지.
- `overlapCheckAvailable`: 주전공 및 복수전공의 교과목 목록을 모두 확인할 수 있는지.
- `candidateCredits`: `completedCourses` 중 상태 CANDIDATE인 내 이수 학점 합. 학교가 인정한 부전공 학점/최종 이수 판정이 아니다. 요구 학점 상한을 적용하지 않는다.
- `completedCourses:[{id,courseName,courseCode,classification,credits,grade,status}]`: 부전공 교과목 마스터와 연결된 인정 이수 기록. 이수구분은 후보 학과 마스터 기준.
- `remainingCourses:[{courseName,courseCode,classification,credits,targetGrade,openSemester,status}]`: 아직 안 들은 전공필수·전공선택 후보. 중복 불가/확인 필요 후보도 상태와 함께 반환한다.
- `status`: `CANDIDATE`, `PRIMARY_MAJOR_OVERLAP`, `DOUBLE_MAJOR_OVERLAP`, `OVERLAP_UNVERIFIED`. 앞의 두 전공에 겹치면 후보 학점에서 제외. 전공 자료가 없으면 중복 여부를 확정하지 않고 후보 학점에도 넣지 않는다.
- `reviewRequired:true`: 항상 확인 필요. `completed`, `earned` 등의 최종 인정 필드는 제공하지 않는다.

## 계산과 일관성

- 이수 기록은 기존 이수현황과 같은 F·NP 제외/재수강 최근 1건 정책을 사용한다. 성적 미정도 후보로 표시한다.
- 학수번호로 매칭하고, 학수번호 없는 기록만 공백을 뺀 과목명으로 매칭한다. 코드가 다른 동명 과목은 매칭하지 않는다.
- 중복 제외는 주전공/복수전공의 전공기초·필수·선택 마스터와 비교하는 보수적 후보 필터다. 실제 어느 과정에서 인정했는지에 대한 사용자 배분 자료가 없어 학과 확인이 필요하다.
- 부전공 등록/해제는 기존 progress·gpa·home·summary의 계산을 바꾸지 않는다. 2026학번의 추가 과정 의무 체크도 MANUAL을 유지한다.
- 주전공/입학년도 변경 후 부전공이 주전공과 같아지거나 해당 학번의 부전공 요건이 없으면 INVALID_INPUT. 복수전공을 부전공과 같게 등록해도 INVALID_INPUT.
- 부전공 등록·변경·해제, 전공/입학년도 변경, 복수전공 등록·해제는 사용자 쓰기 잠금으로 관련 검증을 직렬화한다.
- 없는 전공 MAJOR_NOT_FOUND, 부전공 총학점/해당 학번 요건 미확보 REQUIREMENT_NOT_FOUND, 프로필 미완성 PROFILE_INCOMPLETE, 같은 전공 INVALID_INPUT. 기존 에러 코드만 사용한다.

## 저장과 변경 파일

- B 번호대 `V50__add_user_minors.sql`: `user_minors(id,user_id UNIQUE,major_id,created_at,updated_at)`. 사용자 FK ON DELETE CASCADE. 기존 사용자 테이블/시드/이미 적용한 Flyway 파일은 바꾸지 않는다.
- `UserMinor`, 저장소, `MinorRequest`, `MinorResponse`, 순수 계산 `MinorCourseCalculation`, `MinorService`, `MinorOptionResponse`.
- Academic/Catalog 컨트롤러·CatalogService·GraduationRequirementRepository, 주전공/복수전공 변경 검증.
- 학사 계산/서비스/카탈로그/실제 MySQL 동시성 테스트, FRONTEND_GUIDE·PROGRESS 갱신.

## 후속 자료

- 학과별 부전공 지정 교과목과 구 교육과정 대체 인정표.
- 주전공/다전공/부전공 간 실제 과목 배분 결과. 확보 전에는 최종 이수 판정을 자동화하지 않는다.
- 미기재 47행의 부전공 운영 여부와 자료 누락 여부.
