# ORDO 백엔드 — AI 작업 규칙

경희대 국제캠퍼스 학생용 일정 + 학사(졸업요건) 통합 웹서비스 **오르도(ORDO)** 의 Spring Boot 백엔드.
백엔드 개발자 2명이 각자 AI(Claude Code / Codex)로 코딩한다. 이 파일은 Codex가 자동으로 읽고, Claude Code는 CLAUDE.md를 통해 이 파일을 읽는다 — **규칙은 이 파일에만 적는다.** 두 사람의 코드가 같은 모양으로 나오도록 아래 규칙을 반드시 지킨다.

## 기준 문서
- **`docs/BACKEND_SPEC.md` 가 유일한 기준**이다. 작업 전에 해당 장을 읽고, 명세와 다르게 만들어야 할 것 같으면 코드를 쓰기 전에 사람에게 먼저 물어본다.
- `docs/ORDO_ARCHITECTURE.md` 는 초기 개요(구버전). 명세서와 다르면 무시한다.

## 스택
Java 17 · Spring Boot 3.3.5 · Spring Data JPA · Spring Security + JWT(jjwt 0.12.6) · MySQL 8 · Flyway · Lombok · springdoc-openapi · Gradle

## 실행
- 로컬 MySQL 필요 (DB `ordo` 는 자동 생성). 비밀번호는 환경변수 `DB_PASSWORD` 또는 git에 올리지 않는 `application-local.yml`.
- `./gradlew bootRun` (Windows: `gradlew.bat bootRun`) → http://localhost:8080/swagger-ui/index.html
- 테스트: `./gradlew test`

## 절대 규칙
1. **테이블을 만들거나 바꾸지 않는다.** 스키마는 `src/main/resources/db/migration/V1__init_schema.sql`, 시드는 `V2__seed_catalog.sql` 에 이미 있다. 엔티티는 V1 컬럼명·타입에 **정확히** 맞춘다(자바 camelCase ↔ DB snake_case). `ddl-auto` 는 `none`.
2. **이미 있는 Flyway 파일은 수정 금지.** 스키마 변경이 필요하면 사람에게 확인 후 새 파일(A 담당 V10~V49, B 담당 V50~V89).
3. **V2 시드 SQL은 손으로 고치지 않는다.** 데이터 수정은 `tools/seed/generate_seed.py` 로 재생성.
4. 비밀번호·JWT 시크릿 등 비밀값을 코드나 yml 기본값 외의 곳에 하드코딩하지 않는다.
5. 명세에 없는 기능·엔드포인트·필드를 임의로 추가하지 않는다.

## 코드 규칙 (자세한 건 명세서 2장)
- 패키지: `com.ordo.{global|auth|user|catalog|academic|schedule|timetable|home|ecampus|importer|notification}`, 각 도메인 안에 `controller / service / repository / domain / dto`.
- 응답은 항상 `ApiResponse<T>` (`{success, data, error}`), 예외는 `BusinessException(ErrorCode)` → `GlobalExceptionHandler`. 새 에러는 명세서 2.5 표에 있는 코드만 사용(추가 필요 시 표부터 갱신).
- 로그인 사용자: 컨트롤러에서 `@LoginUser Long userId`. 남의 데이터 접근은 **404**.
- 엔티티: setter 금지, `@NoArgsConstructor(access = PROTECTED)`, 연관관계는 `@ManyToOne(fetch = LAZY)` 단방향만, enum은 `@Enumerated(EnumType.STRING)`, 사용자 데이터는 `BaseTimeEntity` 상속.
- DTO: `record`, `XxxRequest`/`XxxResponse`, 요청은 `@Valid` 검증. 컨트롤러에서 엔티티 직접 반환 금지.
- JSON 날짜 `2026-10-16`, 시간 `09:00`, 시간대 Asia/Seoul.
- 서비스: 클래스에 `@Transactional(readOnly = true)`, 변경 메서드에 `@Transactional`.
- 주석·커밋 메시지는 한국어 OK. 메서드/변수 이름은 영어.

## 작업 방식
- 큰 작업은 **파일 목록과 계획을 먼저 보여주고** 승인 후 진행. 한 번에 한 도메인씩.
- 끝나면: 컴파일 확인 → 관련 테스트 실행 → Swagger로 검증할 요청 예시를 알려준다.
- 공용 파일(`global/**`, `ErrorCode`, `build.gradle`, `application.yml`)을 바꿨으면 요약에 따로 적는다(다른 개발자에게 알려야 함).
- 학사 계산(`academic`)은 명세서 5장 규칙 그대로 구현하고 단위 테스트 필수.
