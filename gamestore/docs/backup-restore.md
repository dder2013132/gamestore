# PLAYPORT 백업과 복구

이 백업은 실행 중인 사이트를 멈추지 않고 만든 복구용 사본입니다.
DB는 같은 스냅샷에서 덤프와 테이블 지문을 만들고, 별도 임시 DB에 복원하여 모든 사용자 테이블의 행 개수와 SHA-256이 일치하는지 확인합니다.
검증용 DB는 확인 후 제거합니다. 운영 DB를 덮어쓰지 않습니다.

## 담긴 파일

- `project.zip`: 작업 중인 변경과 새 파일, 이미지, SQL, 문서, 설정, `.git` 기록까지 포함한 프로젝트 전체.
  재생성할 수 있는 `target`, `.artifacts`와 백업 폴더 자체는 제외합니다.
- `runtime/gamestore.war`: 실제 Tomcat에 배포되어 있던 WAR.
- `runtime/tomcat-conf/`, `runtime/versions.txt`: Tomcat 설정과 실행 버전.
- `settings/db.properties`: 백업 시 실제 사용한 DB 접속 설정.
- `database.dump`: PostgreSQL custom 형식의 DB 구조·데이터·시퀀스 백업.
- `database-manifest.properties`, `database-verification.txt`: 백업 시점 DB 메타데이터와 복원 검증 결과.
- `project-files.json`, `project-verification.txt`: ZIP 내부 원본 파일별 크기·해시 검증 결과.
- `checksums.json`, `Verify-Backup.ps1`: 이후 손상 여부를 재검사하는 도구.
- `BACKUP-VERIFIED.txt`: 모든 검증이 성공한 백업에만 생성되는 완료 표시.

백업은 로컬 사용자·SYSTEM·관리자만 접근하도록 폴더 권한을 설정합니다.
DB 접속 설정과 회원 데이터도 포함하므로 이 폴더 전체가 복구 대상입니다.
Git에는 `backups/`를 추가하지 않습니다.

## 나중에 손상 여부 확인

백업 폴더에서 PowerShell을 열고 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Verify-Backup.ps1
```

이 명령은 백업을 변경하거나 DB에 접속하지 않고 각 파일의 크기와 해시만 확인합니다.

## 새 폴더에 프로젝트 복원

아래 명령은 백업 폴더에서 실행하며, 대상은 아직 사용하지 않는 새 폴더로 지정합니다.

```powershell
Expand-Archive -LiteralPath .\project.zip -DestinationPath C:\Working\gamestore-recovered
git -C C:\Working\gamestore-recovered status --short
```

프로젝트는 `C:\Working\gamestore-recovered\gamestore`에 있습니다.
커밋되지 않았던 변경도 ZIP에 들어 있으므로 Git 상태에 그대로 보일 수 있습니다.
Java 21, Maven, Tomcat 10.1 환경을 준비하면 기존 `deploy.ps1`을 사용할 수 있습니다.
급히 배포 파일만 필요하면 `runtime/gamestore.war`를 사용할 수 있습니다.

## 새 DB에 데이터 복원

PostgreSQL 18 도구와 빈 새 DB를 사용합니다.
접속할 호스트·포트·계정은 `database-manifest.properties`를 참고하여 아래 예시를 맞춥니다.
비밀번호는 도구가 물어볼 때 입력합니다.

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\createdb.exe' --host=localhost --port=5432 --username=postgres gamestore_recovered
& 'C:\Program Files\PostgreSQL\18\bin\pg_restore.exe' --exit-on-error --single-transaction --no-owner --no-privileges --host=localhost --port=5432 --username=postgres --dbname=gamestore_recovered .\database.dump
```

복원한 프로젝트의 `gamestore/db.properties`를 새 DB 이름(`gamestore_recovered`)과 새 환경의 계정에 맞게 수정합니다.
기존 접속 설정 원본은 백업의 `settings/db.properties`에 있습니다.
Tomcat 실행 환경의 `GAMESTORE_DB_CONFIG`도 복원한 설정 파일의 절대 경로로 지정합니다.

```powershell
$env:GAMESTORE_DB_CONFIG = 'C:\Working\gamestore-recovered\gamestore\db.properties'
```

그 환경 변수를 설정한 터미널에서 Tomcat을 실행합니다.
`runtime/tomcat-conf/`는 기존 설정을 확인하는 참고 사본이며, 다른 설치 환경에서는 필요한 값만 반영합니다.
DB 구조와 데이터가 함께 복원되므로 기존 마이그레이션 SQL을 처음부터 다시 실행할 필요는 없습니다.
회원 계정·게임 설명·판매 상태·주문 이력·찜 목록이 복원되며 로그인 세션과 세션 카트는 백업 대상이 아닙니다.

## 새로운 백업 만들기

프로젝트 폴더에서 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\backup.ps1
```

저장 위치는 저장소 루트의 `backups/gamestore-날짜-고유번호/`입니다.
이전 백업을 덮어쓰지 않고 새 폴더를 만듭니다.
DB를 덤프할 권한과 검증용 임시 DB를 만들고 제거할 권한이 필요합니다.
권한이나 검증 문제로 실패하면 완료 표시를 만들지 않고 생성된 파일을 보존합니다.
