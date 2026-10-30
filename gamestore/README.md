# 🎮 PLAYPORT

**Java Servlet과 JSP로 개발한 게임 스토어 웹 애플리케이션**

PLAYPORT는 게임을 탐색하고, 찜하거나 장바구니에 담고, 주문 내역과 게임 라이브러리를 관리할 수 있는 개인 개발 프로젝트입니다. 관리자는 게임과 주문을 관리할 수 있습니다.

> 🚧 **개발 진행 중** — 현재 구현된 기능을 기반으로 문서를 작성했으며, 실제 결제 서비스는 연동되어 있지 않습니다. 결제 흐름은 테스트용입니다.

## 📸 미리보기

| 홈 | 게임 목록 |
| --- | --- |
| ![홈 화면](docs/previews/home-desktop.jpg) | ![게임 목록 화면](docs/previews/browse-desktop.jpg) |

| 게임 상세 및 리뷰 | 관리자 대시보드 |
| --- | --- |
| ![게임 리뷰](docs/previews/game-reviews-desktop.jpg) | ![관리자 대시보드](docs/previews/admin-dashboard-desktop.jpg) |

## ✨ 주요 기능

**사용자**
- 회원가입, 로그인 및 로그아웃
- 게임 검색·장르 필터·정렬·페이지네이션
- 게임 상세 정보, 갤러리, 리뷰 및 별점 조회
- 찜 목록 및 장바구니 관리
- 테스트 주문, 주문 이력 조회 및 주문 취소
- 보유 게임 라이브러리와 계정 정보 관리
- 보유 게임 기반 리뷰 작성·수정·삭제

**관리자**
- 게임 등록 및 정보 수정
- 게임 판매 중지·재개
- 주문 조회 및 상태 관리
- 판매 현황 대시보드

## 🛠 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Java 21, JavaScript, HTML, CSS |
| Backend | Jakarta Servlet 6.0, JSP, JSTL, JDBC |
| Database | PostgreSQL |
| Build | Maven (WAR) |
| Server | Apache Tomcat 10.1 |
| Test | JUnit 4, JavaScript 테스트 |

별도의 Spring 프레임워크나 ORM 없이 Servlet과 JDBC를 중심으로 요청 처리부터 SQL 실행까지 직접 구현했습니다.

## 🏗 아키텍처

```text
Browser
  │ HTTP Request
  ▼
Servlet / Filter  ────── 인증·인가 및 요청 검증
  │
  ▼
Repository (JDBC) ───── SQL 실행 / 데이터 매핑
  │
  ▼
PostgreSQL
  │
  └── 데이터 조회 및 저장

JSP + JSTL ──────────── 서버 측 화면 렌더링
```

프로젝트는 주요 기능별 Servlet과 Repository로 역할을 분리했습니다. 데이터 조회에는 `PreparedStatement`를 사용하고, 화면은 JSP와 공통 JSP 조각으로 구성했습니다.

## 🔎 구현 과정에서 중점적으로 다룬 내용

- **데이터 영속성:** 메모리 저장 방식에서 PostgreSQL/JDBC 기반 저장 방식으로 전환
- **검색 성능과 페이지네이션:** 조건별 검색·정렬·집계 및 `LIMIT/OFFSET`을 데이터베이스에서 처리
- **계정 보안:** 비밀번호 해싱, 세션 기반 인증, CSRF 검증 및 관리자 접근 제어
- **주문 데이터 처리:** 주문 저장과 항목 저장, 중복 요청 방지 및 주문 상태 관리
- **반응형 UI:** 데스크톱과 모바일 화면에 대응하는 게임 스토어 인터페이스 구현

## 🚀 로컬 실행

### 요구 환경

- JDK 21
- Maven
- PostgreSQL
- Tomcat 10.1

### 1. 데이터베이스 설정

PostgreSQL에 프로젝트용 데이터베이스를 준비한 뒤 `sql/` 디렉터리의 스키마 및 마이그레이션 SQL을 **파일 번호와 의존 관계에 맞게** 적용합니다. 일부 SQL은 기존 스키마를 전제로 하므로 신규 데이터베이스에서의 초기 설정은 별도 확인이 필요합니다.

`db.properties.example`을 복사해 `db.properties`를 만들고 접속 정보를 입력합니다.

```properties
db.url=jdbc:postgresql://localhost:5432/gamestore
db.user=your_database_user
db.password=your_database_password
```

> ⚠️ `db.properties`에 들어 있는 실제 비밀번호는 Git에 커밋하지 마세요.

### 2. 설정 파일 경로 지정

PowerShell 예시:

```powershell
$env:GAMESTORE_DB_CONFIG = (Resolve-Path .\db.properties).Path
```

Tomcat을 실행하는 프로세스에서도 동일한 환경변수를 사용할 수 있어야 합니다.

### 3. 빌드 및 배포

```powershell
mvn clean package
```

생성된 `target/gamestore.war`를 Tomcat의 `webapps`에 배포하고 Tomcat을 실행합니다. Windows + Scoop 기반 로컬 환경에서는 아래 스크립트도 사용할 수 있습니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1
```

배포 후 접속 주소:

```text
http://localhost:8080/gamestore/home
```

*배포 스크립트는 Tomcat을 직접 시작하거나 재시작하지 않습니다.*

## 🧪 테스트

```bash
mvn test
```

인증 필터, 계정, 게임 검색, 주문, 찜, 리뷰, 관리자 기능 등을 대상으로 테스트 코드를 작성했습니다. 일부 통합 검증에는 PostgreSQL 및 로컬 실행 환경 준비가 필요할 수 있습니다.

## 📂 주요 디렉터리

```text
gamestore/
├── src/
│   ├── main/
│   │   ├── java/com/gamestore/
│   │   │   ├── filter/
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   └── servlet/
│   │   └── webapp/
│   └── test/
├── sql/                  # DB 스키마·변경·샘플 데이터
├── docs/                 # 개발 문서 및 화면 미리보기
├── db.properties.example # DB 설정 예시
├── pom.xml
└── README.md
```

## 🗺 향후 개선 방향

- 초기 DB 구성 및 마이그레이션 절차 단순화
- 실제 배포 환경에 맞춘 설정 및 보안 점검
- 자동화된 테스트와 예외 상황 검증 확장
- UI/UX 및 접근성 개선
- 기술적 설계 결정과 트러블슈팅 문서 보강

---

**개인 학습 및 포트폴리오 목적으로 개발 중인 프로젝트입니다.**
