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

> 💡 `gradlew` 파일은 IntelliJ가 처음 열 때 자동 생성합니다.
> 터미널에서 직접 쓰고 싶으면 IntelliJ 하단 Terminal에서 `gradle wrapper` 를 한 번 실행하세요.

---

## 3. MySQL 비밀번호 맞추기

`src/main/resources/application.yml` 을 열어 **password** 를 설치 때 정한 root 비밀번호로 바꿉니다.

```yaml
    password: ${DB_PASSWORD:여기에_내_비밀번호}
```

- DB(`ordo`)는 첫 실행 때 자동 생성되니 직접 만들 필요 없습니다.
- 비밀번호를 코드에 안 넣고 싶으면 환경변수 `DB_PASSWORD` 로 줘도 됩니다.

---

## 4. 실행 & 확인

1. `src/main/java/com/ordo/OrdoApplication.java` 열기 → 왼쪽 ▶ (Run) 클릭
2. 콘솔에 `Started OrdoApplication ...` 이 뜨면 성공
3. 브라우저에서 **http://localhost:8080/api/health** 접속
   → `{"status":"ok","service":"ordo"}` 가 보이면 서버·DB 연결 성공 🎉

---

## 폴더 구조

```
ordo/
├── build.gradle                 # 라이브러리·빌드 설정
├── src/main/java/com/ordo/
│   ├── OrdoApplication.java      # 시작점
│   ├── config/SecurityConfig.java   # (지금은 전체 허용) 보안 설정
│   └── controller/HealthController.java  # /api/health 확인용 API
├── src/main/resources/
│   ├── application.yml            # DB·서버 설정
│   └── db/migration/              # (2단계) Flyway 마이그레이션 자리
└── src/test/...
```

## 다음 단계 (설치 확인 후)
- 엔티티(User, Course, Schedule …) + 교과목 CSV 시드 데이터 넣기
- 회원가입/로그인(JWT) API
- 학사관리 영역별 이수현황 계산 API
