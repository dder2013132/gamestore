# PLAYPORT 로그인 및 주문 저장

JSP / Jakarta Servlet / PostgreSQL을 사용하는 Maven WAR 프로젝트입니다.
Java 21과 Tomcat 10.1을 기준으로 합니다. 현재 Servlet API 및 web.xml 버전은 6.0입니다.

## 실행 준비

1. 기존 게임 DB에서 `sql/006_create_users.sql`을 한 번 실행합니다.
   예: `psql -U your_database_user -d gamestore -f sql/006_create_users.sql`
   이어서 `sql/007_add_order_user.sql`을 한 번 실행하여 orders에 회원 ID를 연결합니다.
   기존 주문이 있다면 소유자를 먼저 연결해야 하며, 이번 로컬 DB에는 빈 주문 테이블에 적용했습니다.
   관리자·게임 등록에는 `sql/008_add_user_role.sql`을 실행하고,
   표지가 포함된 샘플 게임 3개를 추가하려면 `sql/010_seed_games.sql`을 실행합니다.
   008·010은 이번 로컬 DB에 이미 적용했습니다. 실제 관리자 승격은 아래 009 SQL로 직접 선택하세요.
   게임 정보 수정·판매 중지에는 `sql/012_add_game_sale_status.sql`도 배포 전에 실행합니다.
   현재 로컬 DB에는 012까지 적용했으며 기존 게임은 모두 판매 중 상태입니다.
   주문 중복 방지와 찜 목록에는 `014_order_checkout_tokens.sql`, `015_create_wishlist.sql`을 배포 전에 적용합니다.
   현재 로컬 DB에는 이 두 파일도 적용했습니다. 기존 주문은 그대로 유지됩니다.
2. 기존 `db.properties`의 `db.url`, `db.user`, `db.password`를 확인합니다.
   파일 형식은 `db.properties.example`을 참고하세요.
3. Tomcat 실행 환경에 `GAMESTORE_DB_CONFIG`를 db.properties의 절대 경로로 지정합니다.
   예: PowerShell에서 `$env:GAMESTORE_DB_CONFIG = 'C:\Working\gamestore\gamestore\db.properties'`
   환경 변수를 설정한 터미널에서 Tomcat을 실행해야 적용됩니다.
   IDE 또는 Windows 서비스로 실행한다면 해당 실행 환경에 설정하고 재시작하세요.
   미설정 시 기존처럼 프로세스 작업 폴더의 `db.properties`를 읽습니다.
   비밀번호가 들어 있는 설정 파일은 WAR나 소스 저장소에 추가하지 마세요.
4. 프로젝트 폴더에서 아래 재배포 명령을 실행합니다. 테스트·WAR 생성·Tomcat 복사를 한 번에 처리합니다.
5. `/gamestore/register`에서 가입한 뒤 `/gamestore/login`으로 로그인합니다.

## 재배포 명령

프로젝트 폴더(`C:\Working\gamestore\gamestore`)에서 실행하세요.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1
```

이 스크립트는 기존 WAR가 없어도 새로 빌드합니다.
배포용 WAR는 `.artifacts/gamestore.war`에 생성하므로 `target` 정리와 분리됩니다.
일반 `mvn package`의 기본 출력 위치는 여전히 `target/gamestore.war`입니다.
빌드가 실패하면 기존 Tomcat WAR를 교체하지 않습니다.
복사는 임시 파일에서 SHA-256을 확인한 뒤 완료된 WAR를 교체합니다.
Tomcat 실행·재시작은 하지 않으며, 실행 중인 Tomcat의 자동 배포가 끝난 뒤 브라우저를 새로고침하세요.
Tomcat을 꺼 둔 상태라면 복사 후 기존 방식대로 실행하세요.

기본 Tomcat 위치는 현재 Windows 사용자 폴더의 `scoop/apps/tomcat10/current`입니다.
다른 설치 위치는 `-TomcatHome 'D:\Servers\tomcat'`으로 지정할 수 있습니다.
이미 테스트한 변경을 빠르게 배포하려면 끝에 `-SkipTests`를 붙일 수 있습니다.
이 명령의 ExecutionPolicy 설정은 해당 PowerShell 실행에만 적용됩니다.

WAR 출력 설정 참고: https://maven.apache.org/components/plugins-archives/maven-war-plugin-3.4.0/war-mojo.html#outputDirectory

## 현재 상태 백업

프로젝트 폴더에서 `powershell -NoProfile -ExecutionPolicy Bypass -File .\backup.ps1`을 실행합니다.
저장소 루트의 `backups/`에 날짜별 새 폴더를 만들며 현재 소스·이미지·Git 기록,
배포된 WAR·Tomcat 설정·DB 접속 설정과 PostgreSQL 전체 덤프를 보관합니다.
프로젝트 ZIP의 모든 파일 해시와 별도 임시 DB로 실제 복원한 모든 테이블의 내용을 검증합니다.
백업은 로컬 사용자·SYSTEM·관리자만 접근할 수 있고 Git에서 제외합니다.
복구 절차와 백업 구성은 `docs/backup-restore.md`에 있습니다.

## 인디 게임숍 UI

크림색 바탕과 살구·세이지 색상을 사용하는 공통 테마를 적용했습니다.
홈(`/gamestore/home`)은 실제 DB 게임으로 구성하는 추천 배너와 최대 3개의 추천 카드,
전체 게임 목록으로 이동하는 링크로 구성됩니다.
추천은 준비된 표지의 게임을 우선 선택하며 판매량·평점 순위는 사용하지 않습니다.
게임 목록(`/gamestore/browse`)에서 제목·장르 검색, 장르 필터와 정렬을 함께 사용할 수 있습니다.
예: `/gamestore/browse?q=moon&genre=Simulation&sort=price_asc`.
한 페이지는 9개이며 `page=2`로 다음 페이지를 요청할 수 있습니다.
DB에서 검색·필터·정렬·개수 계산 후 LIMIT/OFFSET으로 해당 페이지의 상품만 가져옵니다.
개수·상품·장르 목록은 같은 읽기 스냅샷에서 조회합니다.
페이지 링크는 검색어·장르·정렬을 유지하고, 검색·장르·정렬을 바꾸면 첫 페이지부터 표시합니다.
잘못된 페이지 값은 첫 페이지로, 전체 범위를 넘는 양의 페이지 번호는 마지막 페이지로 맞춥니다.
정렬 값은 `added`(ID 기준 최신 추가순, 기본값), `price_asc`(낮은 가격),
`price_desc`(높은 가격), `title`(제목순)입니다. 같은 가격은 ID가 큰 게임부터 표시합니다.
장르를 바꾸거나 검색·정렬을 적용해도 다른 선택 조건은 URL에 유지됩니다.
이전 `/home?q=...&genre=...` 검색 주소는 조건을 보존하여 새 목록으로 이동합니다.
상단 홈·게임 목록 메뉴를 구분했고, 카트·주문 완료·상세에서 쇼핑을 계속하면 게임 목록으로 이동합니다.
검색 결과가 없을 때는 조건을 지울 수 있는 빈 상태 화면을 보여줍니다.

공통 JSP 조각은 `WEB-INF/jspf/head.jspf`, `header.jspf`, `footer.jspf`, `game-card.jspf`입니다.
각 JSP 조각에 UTF-8을 명시했고, 금액은 JSTL로 천 단위 구분자를 표시합니다.
스타일은 `css/style.css`의 `:root` 색상 변수와 반응형 규칙에서 관리합니다.
별도 CSS 프레임워크나 외부 폰트 다운로드 없이 동작합니다.
관리자 화면의 표지 선택에는 `js/store.js`의 이미지 미리보기를 연결했습니다.

게임 상세는 기존 DB의 `description`을 읽어 내용이 있을 때 설명 섹션을 표시합니다.
관리자 게임 등록에는 선택 입력인 게임 설명(최대 5,000자)을 함께 저장합니다.
등록된 게임은 관리자 목록의 `説明を編集`에서 설명을 추가·수정할 수 있습니다.
설명 편집 주소는 `/gamestore/admin/games/description?id=게임ID`입니다.
설명만 갱신하므로 제목·가격·장르·이미지는 유지됩니다.
일본어와 줄바꿈을 유지하며 HTML은 실행하지 않고 문자 그대로 표시합니다.
빈 설명을 저장하면 상세 페이지의 설명 영역이 사라집니다.
입력 검증 또는 저장 실패 시 작성한 내용을 유지합니다. 기존 DB에 description TEXT가 있어 추가 SQL은 필요하지 않습니다.

현재 데모 게임 6개의 일본어 소개는 표지와 장르에 맞춰 작성한 가상 게임 소개입니다.
`sql/011_seed_game_descriptions.sql`에 보관했으며 현재 로컬 DB에는 적용했습니다.
이 SQL은 제목·이미지가 일치하고 설명이 비어 있는 게임만 채우므로 이후 관리자가 수정한 설명을 덮어쓰지 않습니다.
한 문장으로 실행되며 별도의 BEGIN·COMMIT은 필요하지 않습니다. DB 내용은 재배포 없이 상세 페이지에 반영됩니다.

홈·로그인·관리자·카트·주문·완료 화면을 실제 Tomcat에서 확인했고,
390px 모바일, 1024px 중간 화면, 1280px 데스크톱 너비에서 표시를 검증했습니다.
모바일 관리자 표는 표 영역 안에서만 가로 스크롤됩니다.
임시 테스트 계정으로 게임 등록→구매→주문 내역까지 검증했으며 테스트 데이터는 정리했습니다.
홈·게임 목록 미리보기는 `docs/previews/home-desktop.jpg`, `home-mobile.jpg`,
`browse-desktop.jpg`, `browse-mobile.jpg`에 저장했습니다.
분리한 홈·게임 목록은 390px, 768px, 1280px에서 가로 넘침 없이 표시되는 것을 확인했습니다.

## 동작과 코드 위치

- `JdbcUserRepository`: PreparedStatement로 계정 생성·조회. 사용자명은 소문자로 정규화하고 DB UNIQUE 제약으로 중복을 방지합니다.
- `PasswordHasher`: 계정별 무작위 salt와 PBKDF2-HMAC-SHA256 600,000회로 비밀번호를 저장합니다.
  평문 비밀번호나 해시는 세션에 보관하지 않습니다.
- `LoginServlet`, `RegisterServlet`, `LogoutServlet`: 입력 검증, 오류 표시, 세션 로그인 및 POST 로그아웃.
- `AuthFilter`: 카트 조회·추가·삭제 및 구매·주문 페이지 로그인 확인, 모든 POST 폼의 CSRF 토큰 확인, UTF-8 요청 처리.
- `AuthSession`: 로그인 시 세션 ID와 CSRF 토큰 갱신, 30분 미사용 만료, 로그인 후 요청했던 카트·상품 페이지로 복귀.
- `WEB-INF/views/`: 직접 URL로 열 수 없는 로그인·회원가입 JSP.
- `WEB-INF/jspf/header.jspf`: 전체 화면에서 공유하는 로그인 메뉴.
- `OrderRepository`: 주문 저장·확인 번호별 저장 및 조회·회원별 조회에 필요한 메서드를 정의하는 인터페이스.
- `JdbcOrderRepository`: 주문과 상세 항목을 하나의 트랜잭션으로 저장하고, 회원 ID로 제한하여 조회.
- `AppContextListener`: 애플리케이션에서 사용하는 주문 저장소를 JDBC 구현으로 연결.
- `AdminGameServlet`: 관리자 전용 게임 목록과 신규 등록. 준비된 로컬 이미지에서 표지를 선택합니다.
- `AdminGameServlet`의 `/admin/games/edit`: 같은 입력 검증으로 제목·가격·장르·표지·설명을 수정합니다.
- `AdminGameStatusServlet`: 관리자 전용 POST 판매 중지·재개. 명시한 상태를 저장하며 GET으로 변경하지 않습니다.
- `AdminGameDescriptionServlet`: 관리자 전용 기존 게임 설명 편집. 서버에서 5,000자 제한을 검증하며 기존 인증·CSRF 검사를 적용합니다.
- `HomeServlet`: 추천 배너·최대 3개 추천 게임과 실제 전체 개수. 이전 검색 URL은 목록으로 이동합니다.
- `BrowseServlet`: 공개 게임 목록, 제목·장르 검색과 장르 필터, 가격·제목·추가순 정렬 및 9개씩 페이지 표시.
- `JdbcGameRepository.findPage`, `CatalogPage`: DB 조건 검색·안정적인 정렬·LIMIT/OFFSET과 페이지 메타데이터.
- `CheckoutTokens`: 회원·카트 버전·확인한 가격에 묶인 주문 확인 번호 발급. 카트 변경 시 이전 확인 번호를 무효화합니다.
- `WishlistServlet`, `JdbcWishlistRepository`, `WishlistView`: 회원별 찜 저장·삭제·9개씩 목록 표시 및 카드·상세 하트 상태.

## 주문 중복 방지와 찜 목록

주문 확인 화면(`/checkout`)에서 서버가 발급한 `checkoutToken`을 숨김 필드로 보냅니다.
같은 카트를 여러 탭에서 확인하거나 새로고침하면 같은 확인 번호를 사용합니다.
`orders.checkout_token UUID`와 `(user_id, checkout_token)` 고유 인덱스로 DB에서도 중복을 막습니다.
동시에 같은 번호가 제출되면 주문과 상세 항목은 한 번만 저장하고 같은 완료 주소를 반환합니다.
저장 후 응답이 끊긴 경우에도 확인 번호로 저장된 주문을 찾아 안내합니다.
기존 주문의 확인 번호는 NULL로 유지되며 구매 이력은 바뀌지 않습니다.

카트를 추가·삭제한 뒤 오래된 확인 화면을 제출하면 새로 확인하도록 안내합니다.
가격이 바뀌면 새 금액과 새 확인 번호로 재확정을 요구합니다.
완료된 주문을 다시 제출해도 그 사이 새로 담은 카트는 지우지 않습니다.
처리 중에는 버튼도 비활성화하지만, 실제 중복 방지는 서버와 DB가 수행합니다.
CSRF 토큰과 주문 확인 번호는 서로 다른 역할이며 주문에는 둘 다 필요합니다.

찜 목록은 `/gamestore/wishlist`와 상단 `お気に入り` 메뉴에서 엽니다.
홈 추천 카드·전체 게임 카드·상세 페이지의 하트로 추가하고 다시 누르면 삭제합니다.
`wishlist_items`의 기본키 `(user_id, game_id)`로 같은 게임을 여러 번 저장하지 않습니다.
추가·삭제는 POST와 CSRF 검사를 사용하며 요청의 userId 값은 사용하지 않습니다.
모든 조회·삭제 조건은 로그인 세션의 회원 ID를 사용합니다.
로그아웃 후 다시 로그인해도 찜이 유지되고, 판매 중지된 게임도 표시와 삭제가 가능합니다.
찜 목록은 최근 추가 순으로 9개씩 표시합니다. 중복 추가는 최초 저장 시각을 바꾸지 않습니다.
비회원은 로그인으로 이동하며 로그인 전의 추가·삭제 요청은 자동으로 실행하지 않습니다.
하트를 누른 뒤에는 허용된 사이트 내부 주소로만 돌아가며 검색·장르·정렬·페이지 조건을 유지합니다.

DB 적용·확인은 다음 도구로도 가능합니다. 현재 로컬 DB에는 이미 적용했습니다.

```powershell
java -cp "target/classes;target/gamestore/WEB-INF/lib/*" scripts/MemberFeaturesDatabaseSetup.java --check
```

새 환경에 014·015만 적용하려면 `--apply`를 사용합니다.
화면 예시는 `docs/previews/wishlist-desktop.jpg`, `wishlist-mobile.jpg`, `checkout-confirmation.jpg`에 저장했습니다.

## 관리자 지정과 게임 등록

1. 웹에서 사용할 계정을 일반 회원으로 가입합니다.
2. DB 클라이언트(pgAdmin 등)에서 `sql/009_promote_admin.sql`을 엽니다.
3. `target_username TEXT := 'CHANGE_ME';`의 CHANGE_ME를 가입한 아이디로 바꾼 뒤 실행합니다.
   잘못된 기본값 그대로 실행하거나 없는 계정을 지정하면 오류로 중단됩니다.
4. 로그아웃 후 다시 로그인하면 상단에 **ゲーム管理** 메뉴가 표시됩니다.
   주소는 `/gamestore/admin/games`입니다.
5. 제목(최대 200자), 정수 가격(0 이상), 장르(최대 50자), 이미지를 선택하고
   선택 입력인 설명(최대 5,000자)을 작성해 등록합니다.
   게임 ID는 자동 생성되며 등록 즉시 홈페이지·상세·장바구니·주문에 사용할 수 있습니다.
6. 기존 게임의 설명은 목록의 `説明を編集`에서 작성한 뒤 `説明を保存`으로 저장합니다.
   `詳細ページを確認`으로 실제 표시를 확인할 수 있습니다.
7. 제목·가격·장르·표지까지 수정하려면 `情報を編集`을 누르고 `変更を保存`으로 저장합니다.
8. `販売を休止`를 누르면 홈·게임 목록에서 숨기고 구매를 차단합니다.
   다시 `販売を再開`를 누르면 같은 게임 ID로 판매를 재개합니다.

판매 상태는 `games.is_active BOOLEAN NOT NULL DEFAULT TRUE`에 저장합니다.
중지한 게임도 직접 상세 주소에서는 판매 휴지 안내와 설명을 볼 수 있습니다.
카트 조회·주문 확인은 DB의 최신 가격과 판매 상태를 반영합니다.
기존 카트의 판매 중지 상품은 표시를 유지하여 사용자가 삭제할 수 있고 구매 진행은 막습니다.
주문 확정 전에 가격이 바뀌면 409 응답으로 새 금액을 보여주고 재확정을 요구합니다.
주문 저장 트랜잭션에서도 게임 행을 ID 순서로 FOR SHARE 잠근 뒤 가격·판매 상태를 재검사합니다.
관리자의 변경과 주문 확정이 겹쳐도 변경된 상태를 무시한 새 주문은 저장하지 않습니다.
판매 중지는 행을 삭제하지 않아 과거 주문이 유지되며 구매 당시 가격은 order_items에 보존됩니다.
과거 주문의 제목·장르·표지는 현재 게임 정보를 표시하고, 금액은 구매 당시 값을 표시합니다.

모든 회원가입은 USER 권한으로 저장됩니다. 웹에는 관리자 생성·권한 변경 기능이 없습니다.
관리자 URL은 요청할 때마다 DB 권한을 다시 검사합니다.
SQL로 `UPDATE users SET role = 'USER' WHERE username = '대상아이디';`를 실행하면 기존 관리자 세션도 접근이 차단됩니다.
이 방식에서 권한 변경을 수행하는 주체는 DB에 접속하는 운영자입니다.
애플리케이션 DB 계정의 비밀번호와 DB 접속 권한은 운영자가 관리해야 합니다.

샘플 제품은 Aetherbound(¥4,800), Moonlit Harvest(¥3,200), Abyssal Relay(¥4,500)입니다.
세 표지는 내장 image_gen으로 만든 가상 게임 이미지이며 `src/main/webapp/images/`에 포함했습니다.
생성 프롬프트와 파일 이름은 `docs/game-cover-prompts.md`에 기록했습니다.
추가 샘플 6개는 다음과 같습니다. 현재 로컬 카탈로그는 기존 6개와 합쳐 총 12개입니다.

| 제목 | 장르 | 가격 |
| --- | --- | --- |
| Lantern Post | Adventure | ¥2,400 |
| Cloudberry Cafe | Simulation | ¥1,800 |
| Tideglass | Puzzle | ¥1,600 |
| Mossbound | Platformer | ¥2,200 |
| Starlight Courier | Racing | ¥2,600 |
| Fablewood Chronicles | RPG | ¥3,600 |

각 게임은 일본어 설명과 개별 표지를 포함한 가상 데모 상품입니다.
새 표지 6개도 내장 image_gen으로 생성했으며 프롬프트는 `docs/expanded-catalog-covers.md`에 보관했습니다.
`sql/013_expand_demo_catalog.sql`은 같은 제목 또는 이미지가 있으면 추가하지 않으며 기존 상품을 수정하지 않습니다.
현재 로컬 DB에는 이미 적용했습니다. 새 환경에서는 배포 후 다음 명령으로 추가할 수 있습니다.

```powershell
java -cp "target/classes;target/gamestore/WEB-INF/lib/*" scripts/DemoCatalogSetup.java --apply
```

`--check`는 게임 개수·설명 길이·표지 파일 유무만 확인합니다.
실제 Tomcat에서 첫 페이지 9개·둘째 페이지 3개, 검색·정렬 조건 유지,
장르 변경 시 첫 페이지 복귀와 390px 모바일 페이지 이동을 확인했습니다.
미리보기는 `docs/previews/catalog-expanded-page-1.jpg`, `catalog-expanded-page-2.jpg`, `catalog-expanded-mobile.jpg`입니다.

현재 게임 추가 화면은 준비된 이미지 선택 방식입니다.
새 이미지 파일은 소스의 images 폴더에 넣고 재배포하면 목록에 표시됩니다.
게임 정보 수정과 판매 중지·재개는 관리자 목록에서 사용할 수 있습니다. 표지는 준비된 로컬 이미지에서 선택합니다.

DB 구조와 샘플 데이터 확인(프로젝트 폴더에서 빌드 후 실행):

```powershell
java -cp "target/classes;target/gamestore/WEB-INF/lib/*" scripts/CatalogDatabaseSetup.java --check
```

`--apply`는 008·010·012·014·015 SQL을 실행하며, 실제 계정의 관리자 승격은 하지 않습니다.
판매 상태만 확인하거나 012만 적용하려면 같은 클래스 경로로
`scripts/SaleStatusDatabaseSetup.java --check` 또는 `--apply`를 실행할 수 있습니다.

사용자명은 영문·숫자·밑줄 4~30자, 비밀번호는 8~128자입니다.
회원가입 후 로그인 화면으로 이동하고, 로그인 성공 시 홈 또는 요청했던 구매·주문목록 화면으로 이동합니다.
로그아웃하면 세션과 장바구니를 비웁니다.
게임 탐색·상세는 비회원도 이용할 수 있지만 카트는 회원 전용입니다.
`/cart`, `/cart/remove`, `/cart.jsp`는 서버 필터에서 로그인 여부를 검사합니다.
비회원이 카트 메뉴를 누르면 로그인 후 카트로, 상품 담기를 누르면 로그인 후 해당 상품 상세로 돌아갑니다.
로그인 전의 담기·삭제 요청은 실행하지 않으며, 로그인 후 상품 상세에서 다시 담기를 눌러야 합니다.

## 검증

`mvn test package`로 비밀번호 검증, 로그인·가입·로그아웃, CSRF·인증 필터,
회원별 주문 접근 제한 테스트를 실행하고 WAR를 생성합니다.
일반 자동 테스트 102개로 로그인·인증, 회원 전용 카트, 관리자 권한·CSRF, 게임 등록·입력 검증,
설명 등록·편집·길이 제한·오류 시 입력 유지, 홈 추천 제한·기존 검색 주소 이동,
게임 목록 검색·장르·정렬 및 주문 요청 처리를 검증합니다.
게임 정보 수정·판매 중지·재개, 카트 갱신·판매 중지 차단과 가격 변경 시 재확정도 검증합니다.
주문 확인 번호 재사용·변경·실패 후 재시도·새 카트 보존, 찜 회원 분리·입력 검증·외부 주소 차단도 검사합니다.
실제 PostgreSQL을 사용하는 통합 테스트 36개는 기본적으로 건너뜁니다.
`mvn -Dgamestore.dbTest=true test`로 실행하며, 고유한 임시 스키마를 만들었다가 정리합니다.
이 테스트는 테스트 스키마를 만들 수 있는 DB 권한이 필요합니다.
검증 항목은 신규 저장소 인스턴스에서 재조회, 회원별 접근 제한, 구매 가격·시각 유지,
상세 저장 실패 시 전체 롤백, 검색·정렬, 판매 중지 시 과거 주문 보존·새 주문 차단,
가격 변경 시 저장 차단과 동시에 진행 중인 판매 중지 트랜잭션 대기,
주문 확인 번호별 반복·동시 저장, 회원 분리, 롤백 후 재시도, 실제 커밋 직후 응답 오류 복구입니다.
로컬 DB에서 13개 모두 통과했습니다.
카탈로그 통합 테스트 23개는 관리자 SQL 기본값 차단·선택 계정 승격,
게임 ID 자동 생성·문자열 저장, 샘플 중복 방지, 등록 게임의 주문 저장,
설명 저장·재조회·편집·비우기·잘못된 ID 차단, 전체 정보 수정 시 판매 상태 유지,
판매 중지·재개와 반복 마이그레이션, 페이지 간 중복 방지·검색·정렬·범위 보정·SQL 문자 처리와
추가 샘플의 반복 적용·관리자 수정 보존, 찜 저장·동시 추가·회원별 조회·삭제,
판매 중지 상품 표시·페이지 분리·반복 마이그레이션을 검증하며 로컬 DB에서 통과했습니다.
검색·정렬 검증은 실제 DB 쿼리 통합 테스트로 옮기고 서블릿 테스트는 조건 전달과 페이지 표시를 검사합니다.
주문 중복 방지·찜 목록 적용 후 `mvn -Dgamestore.dbTest=true test package`의 138개 테스트가 모두 통과했습니다.
실제 Tomcat에서도 같은 확인 번호로 주문 POST 4개를 동시에 보내 같은 완료 주소와 주문 한 건이 생성됨을 확인했습니다.
완료 후 재전송·새 카트 보존·카트 삭제 후 재추가·가격 변경 시 새 확인 번호,
회원 A/B의 찜 분리·재로그인 유지·CSRF·외부 주소 차단·모바일 하트·찜 페이지 이동도 검증했습니다.
검증용 계정·찜·주문·게임은 정리했습니다.
실제 Tomcat에서도 임시 계정과 게임으로 설명 등록→편집→상세 확인→비우기,
CSRF 오류·길이 초과·누락 필드 차단, 일본어·줄바꿈·HTML 문자 표시와 390px 모바일 화면을 확인했습니다.
설명 편집 미리보기는 `docs/previews/admin-description-editor.jpg`,
`admin-description-editor-mobile.jpg`에 저장했습니다. 임시 테스트 데이터는 정리했습니다.
게임 정보 수정·판매 중지·재개도 실제 Tomcat HTTP 요청과 관리자 화면에서 확인했습니다.
일반 회원·CSRF 오류 차단, 이미 담긴 상품의 중지와 주문 차단, 가격 재확정,
기존 주문 가격 유지와 판매 재개 후 구매를 임시 계정·게임·주문으로 검증한 뒤 정리했습니다.
게임 정보 편집 미리보기는 `docs/previews/admin-game-edit.jpg`, `admin-game-edit-mobile.jpg`입니다.
별도 Tomcat 10.1에서도 회원가입 → 로그인 → 장바구니 → 주문 → 완료·목록 조회를 확인했고,
Tomcat 프로세스를 종료한 뒤 새 프로세스에서 다시 로그인해 같은 주문이 조회되는 것을 확인했습니다.
검증에 사용한 계정과 주문은 이후 정리했습니다.
관리자 화면도 실제 HTTP로 일반 회원 GET·POST 차단, role 파라미터로 승격 불가,
SQL 승격 후 게임 등록, 표지 파일 응답, HTML 문자 이스케이프, 새 게임 구매,
SQL 권한 해제 후 기존 세션 차단을 확인했습니다. 해당 테스트 계정·게임·주문은 정리했습니다.
실제 PostgreSQL·Tomcat 환경에서는 다음 흐름도 확인하세요.

1. 가입 성공, 중복 사용자명, 확인 비밀번호 불일치.
2. 잘못된 비밀번호로 로그인 실패, 정상 로그인 후 사용자명 표시.
3. 비로그인 카트 접근 → 로그인 → 카트로 복귀. 비로그인 상품 담기 → 로그인 → 해당 상세로 복귀 → 다시 담기.
4. A 회원 주문 후 B 회원에게 주문 목록과 완료 URL이 노출되지 않는지 확인.
5. 로그아웃 후 주문목록 접근 시 로그인 화면으로 이동.
6. 주문한 뒤 Tomcat을 재시작하고 다시 로그인해 주문이 유지되는지 확인.

## 주문 DB 구조와 동작

`orders.user_id`는 `users.user_id`를 참조하며 NULL을 허용하지 않습니다.
`order_items.order_id`로 주문 상세를 연결하고, `order_items.price`에 구매 당시 가격을 보관합니다.
주문 생성 시 게임 가격을 DB에서 다시 조회합니다. 상품 가격이 이후 바뀌어도 주문 상세는 구매 가격을 표시합니다.
목록·검색·완료 조회 모두 로그인한 회원 ID를 조건에 포함합니다.
주문과 상세 항목이 모두 저장된 후에만 장바구니를 비웁니다.
DB 오류가 발생하면 트랜잭션을 롤백하고, 장바구니와 오류 안내를 구매 화면에 표시합니다.
네트워크 오류로 완료 여부가 불확실한 경우 재시도 전에 주문 내역을 확인하세요.
주문은 서버 재시작·WAR 재배포 후에도 DB에 유지됩니다.
세션 장바구니는 서버 재시작 시 보존을 보장하지 않습니다.

DB 구조 확인은 빌드 후 다음 명령으로도 가능합니다(프로젝트 폴더에서 실행).

```powershell
java -cp "target/classes;target/gamestore/WEB-INF/lib/*" scripts/OrderDatabaseSetup.java --check
```

`--apply`를 지정하면 주문 테이블이 비어 있고 user_id가 없을 때만 007 SQL을 적용합니다.
기존에 작성하던 `src/main/java/com/JdbcOrderRepositoryTest.java`는 수동 연습 파일로 유지했습니다.
자동 DB 통합 테스트는 `src/test/java/com/gamestore/repository/JdbcOrderRepositoryTest.java`입니다.

비밀번호 저장 기준: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
Tomcat 호환성: https://tomcat.apache.org/whichversion
