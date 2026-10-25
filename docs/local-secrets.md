# 실행 환경별 비밀값 전달

공유 설정에는 비밀번호 대신 환경변수 참조를 사용합니다. `.env`는 로컬 개발용 평문 파일이며 `.gitignore`와 `.dockerignore`로 제외합니다. `.env.example`에는 값 없이 변수명만 제공합니다.

## Docker Compose

프로젝트 폴더의 `.env`를 Compose가 변수 치환에 사용합니다. 같은 이름의 PowerShell 환경변수가 있으면 그 값이 우선합니다.

- `DB_PASSWORD`: MySQL `admin` 계정 비밀번호. MySQL 컨테이너의 `MYSQL_PASSWORD`와 백엔드의 `SPRING_DATASOURCE_PASSWORD`로 전달합니다.
- `MYSQL_ROOT_PASSWORD`: MySQL 초기 root 비밀번호.
- `KC_BOOTSTRAP_ADMIN_PASSWORD`: Keycloak 초기 관리자 비밀번호.
- `KEYCLOAK_CLIENT_SECRET`: `portfolio` Realm의 `admin-api` Client Secret. 기존 PowerShell 환경변수를 사용하거나 로컬 `.env`에 직접 설정합니다.

`.env`에 있다고 모든 값이 컨테이너로 자동 전달되지는 않습니다. `compose.yaml`의 `environment`에서 필요한 값만 전달합니다. 비밀값을 출력하지 않는 구성 검사는 `docker compose config --quiet`로 수행합니다.

기존 MySQL 및 Keycloak 데이터 볼륨이 있으면 초기화 환경변수를 바꿔도 저장된 계정 비밀번호가 자동 변경되지 않습니다. 이번 변경은 기존 값을 외부 설정으로 이동한 것이며 비밀번호 변경 작업이 아닙니다.

## Eclipse / bootRun / java -jar

Spring Boot는 이 프로젝트의 `.env`를 자동으로 읽지 않습니다.

Eclipse의 Run Configurations → 실행 대상 → Environment에 `DB_PASSWORD`와 `KEYCLOAK_CLIENT_SECRET`을 설정합니다. 기존 실행 설정에 `SPRING_DATASOURCE_PASSWORD`가 있다면 그 값이 datasource 설정을 직접 덮어쓰므로 이를 사용할 수도 있습니다. 실행 설정 파일에 비밀값을 넣어 저장소에 공유하지 않습니다.

PowerShell에서는 현재 창에 값을 설정한 다음 같은 창에서 실행합니다. 입력값 자체를 명령에 적지 않습니다.

```powershell
$dbInput = Read-Host "MySQL admin password" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $dbInput).Password
Remove-Variable dbInput

$clientInput = Read-Host "Keycloak Client Secret" -AsSecureString
$env:KEYCLOAK_CLIENT_SECRET = [System.Net.NetworkCredential]::new("", $clientInput).Password
Remove-Variable clientInput

.\gradlew.bat bootRun
```

## Kubernetes

`backend-secret`의 기존 키를 그대로 사용합니다.

- `SPRING_DATASOURCE_PASSWORD`
- `KEYCLOAK_CLIENT_SECRET`

Deployment의 `envFrom.secretRef`가 환경변수로 전달합니다. 따라서 Kubernetes에 `DB_PASSWORD`라는 키를 추가할 필요는 없습니다. Secret 실제 값을 담은 YAML은 저장소에 보관하지 않습니다.

## 이미지와 기존 산출물

이미 생성한 JAR과 Docker 이미지에는 이전 설정이 남아 있을 수 있습니다. 소스 수정만으로 기존 이미지 내용이 바뀌지 않습니다. 새 JAR과 새 버전 이미지를 빌드하고 검증한 뒤 배포합니다. 이전 이미지에는 실제 비밀값이 포함될 수 있으므로 공개 레지스트리에 게시하지 않습니다.

이번 작업은 설정 외부화이며, 비밀번호 회전이나 전체 저장소의 비밀값 감사 완료를 의미하지 않습니다.
