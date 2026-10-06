# ORDO — 백엔드 (Spring Boot + MySQL)

경희대 국제캠 대학생 학사·일정 통합 서비스 오르도의 서버입니다.
Java 17 · Spring Boot 3.3.5 · Gradle · MySQL 8.

---

## 1. 내 PC에 설치할 것 (Windows)

아래 3개를 순서대로 설치하세요.

### ① JDK 17 (자바)
- 다운로드: **Eclipse Temurin 17** — https://adoptium.net/temurin/releases/?version=17 (Windows x64 `.msi`)
- 설치 중 "Set JAVA_HOME variable" 옵션 체크
- 확인: 명령 프롬프트에서
  ```
  java -version
  ```
  → `17.x.x` 가 보이면 OK

### ② MySQL 8
- 다운로드: **MySQL Community Server 8.x** — https://dev.mysql.com/downloads/installer/ (MySQL Installer for Windows)
- 설치 중 **root 비밀번호**를 정합니다 → 꼭 기억하세요 (아래 3번에서 씀)
- 확인: 명령 프롬프트에서
  ```
  mysql -u root -p
  ```
  → 비밀번호 입력 후 `mysql>` 프롬프트가 뜨면 OK (`exit` 로 나옴)

### ③ IntelliJ IDEA (에디터)
- 다운로드: **Community Edition(무료)** — https://www.jetbrains.com/idea/download/
- 이 프로젝트는 IntelliJ로 여는 걸 권장합니다. 열면 Gradle 설정을 자동으로 잡아줍니다.

---

## 2. 프로젝트 열기

1. IntelliJ 실행 → **Open** → 이 `ordo` 폴더 선택
2. 오른쪽 아래에 "Gradle 로딩중…" 이 끝날 때까지 대기 (처음엔 라이브러리 내려받느라 몇 분 걸립니다 — 인터넷 필요)

> 💡 터미널에서는 `gradlew.bat bootRun`(Windows) / `./gradlew bootRun`(Mac) 으로 실행할 수 있습니다.
> 처음 한 번은 Gradle 8.10.2 를 내려받느라 시간이 걸립니다.

---

## 3. 비밀값 넣기 (application-local.yml)

⚠️ **`application.yml` 에는 비밀번호를 쓰지 마세요.** 그 파일은 git 에 올라갑니다.

`src/main/resources/application-local.yml` 파일을 만들고 아래처럼 적습니다.
이 파일은 `.gitignore` 에 들어 있어 git 에 올라가지 않습니다.

```yaml
spring:
  datasource:
    password: '설치 때 정한 MySQL root 비밀번호'

app:
  jwt:
    secret: '32자 이상 아무 랜덤 문자열'
```

- DB(`ordo`)와 테이블은 첫 실행 때 자동 생성(Flyway)되니 직접 만들 필요 없습니다.
- 파일 대신 환경변수 `DB_PASSWORD`, `JWT_SECRET` 으로 줘도 됩니다.

---

## 4. 실행 & 확인

1. `src/main/java/com/ordo/OrdoApplication.java` 열기 → 왼쪽 ▶ (Run) 클릭
2. 콘솔에 `Started OrdoApplication ...` 이 뜨면 성공
3. 브라우저에서 **http://localhost:8080/api/health** 접속
   → `{"success":true,"data":{"status":"ok"},"error":null}` 가 보이면 서버·DB 연결 성공 🎉
4. API 문서(Swagger): **http://localhost:8080/swagger-ui/index.html**

> ⚠️ 테스트·실행이 `ClassNotFoundException` 으로 실패하면: 프로젝트 경로에 한글이 있고 `JAVA_HOME` 이 JDK 21 이상일 때 생기는 문제입니다.
> 환경 변수 `JAVA_HOME` 을 JDK 17 로 바꾸고 터미널·IntelliJ 를 다시 켜세요.

---

## 폴더 구조

```
ordo/
├── build.gradle                 # 라이브러리·빌드 설정
├── src/main/java/com/ordo/
│   ├── OrdoApplication.java      # 시작점
│   └── global/                   # 공통: 응답 형식·에러·JWT 보안·CORS·Swagger (자세한 구조는 docs/BACKEND_SPEC.md 2.1)
├── src/main/resources/
│   ├── application.yml            # DB·서버 설정 (비밀값 없음)
│   ├── application-local.yml      # 내 PC 비밀값 (git 제외, 직접 만듦)
│   └── db/migration/              # Flyway: V1 스키마, V2 시드 (수정 금지)
└── src/test/...
```

## 다음 단계 (설치 확인 후)
- 엔티티(User, Course, Schedule …) + 교과목 CSV 시드 데이터 넣기
- 회원가입/로그인(JWT) API
- 학사관리 영역별 이수현황 계산 API
