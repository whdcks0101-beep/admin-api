# Docker·Kubernetes 실행 가이드

명령은 프로젝트 최상위 폴더의 Windows PowerShell에서 실행합니다. Kubernetes 명령은 로컬 클러스터인 `docker-desktop`을 명시합니다.

## 현재 구성

```text
React 개발 서버 localhost:5173
  → localhost:8080
  → Docker Desktop LoadBalancer Service: backend
  → Kubernetes backend Pod 2개
  → Compose의 MySQL·Redis·Kafka·Keycloak 공유
```

| 구성 | 설정 파일 / 위치 |
| --- | --- |
| Java 17 실행 이미지 | `Dockerfile` |
| 빌드 전송 파일 제한 | `.dockerignore` — 실행 JAR과 Dockerfile만 포함 |
| 인프라 및 선택적 Compose 백엔드 | `compose.yaml` |
| Namespace | `k8s/namespace.yaml` — `admin-portfolio` |
| 일반 설정 | `k8s/backend-configmap.yaml` — `backend-config` |
| 민감한 설정 | 클러스터의 `backend-secret` — 실제 값을 담은 YAML은 저장하지 않음 |
| 백엔드 실행 | `k8s/backend-deployment.yaml` — 이미지 `admin-api:v3`, Pod 2개 |
| 외부 연결 | `k8s/backend-service.yaml` — LoadBalancer, 포트 8080 |
| 일회성 연결 점검 | `k8s/diagnostics/network-check.yaml` — 배포 필수 항목 아님 |

현재 로컬 노드는 1개입니다. Pod 2개는 같은 노드의 자원을 공유합니다. `host.docker.internal`과 LoadBalancer의 `localhost` 연결은 Docker Desktop 실습 환경을 위한 구성입니다.

## 1. 처음 준비하는 환경

### 실행 도구와 비밀값

JDK 17, Docker Desktop의 Linux 컨테이너 모드, `kubectl`을 준비합니다. Docker Desktop에서 Kubernetes를 `kubeadm` 방식으로 생성하고 확인합니다.

```powershell
kubectl --context docker-desktop get nodes
```

`Ready`가 되면 다음으로 진행합니다. `.env`가 없는 새 환경에서는 `.env.example`을 복사하고 실제 개발용 값을 직접 입력합니다. 기존 `.env`를 덮어쓰지 않습니다.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

[비밀값 설정 가이드](local-secrets.md)에 따라 네 변수를 준비합니다. Compose는 `.env`를 읽지만, Eclipse·Spring Boot·kubectl은 자동으로 읽지 않습니다. `KEYCLOAK_CLIENT_SECRET`은 로컬 `.env` 또는 현재 PowerShell 환경변수로 제공해야 Compose 구성이 해석됩니다.

### 인프라

Kafka는 명시적으로 생성한 외부 볼륨을 사용합니다. 아래 명령은 없으면 생성하고, 이미 있으면 기존 볼륨을 유지합니다.

```powershell
docker volume create admin-api-kafka-data
docker compose config --quiet
docker compose up -d mysql redis kafka keycloak
docker compose ps
```

기존 Kafka 데이터를 옮기는 경우에는 빈 볼륨을 연결하기 전에 정지 상태에서 백업·복원을 마쳐야 합니다. 이 프로젝트의 기존 개발 환경은 복원까지 완료했습니다.

MySQL·Redis는 `healthy`를 확인합니다. Kafka·Keycloak은 컨테이너가 `Up`인 것만으로 준비 완료라고 판단하지 않고 아래 요청도 확인합니다.

```powershell
docker exec admin-api-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
curl.exe --fail --silent --show-error --output NUL http://localhost:8180/realms/portfolio/.well-known/openid-configuration
```

새 Keycloak에는 `portfolio` Realm, `admin-api` Client, 로그인 사용자를 먼저 설정해야 두 번째 명령이 성공합니다. Compose는 이를 자동 생성하지 않습니다. Client는 Authorization Code / Standard Flow와 Client 인증을 사용하며, 이 실습의 콜백은 `http://localhost:8080/login/oauth2/code/keycloak`, 로그아웃 후 복귀 주소는 `http://localhost:5173/`입니다. Keycloak 관리자 계정과 애플리케이션 로그인 사용자는 구분합니다.

새 Kafka의 토픽을 준비합니다.

```powershell
docker exec admin-api-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic order-created --partitions 1 --replication-factor 1
docker exec admin-api-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic order-created.DLT --partitions 1 --replication-factor 1
```

| 클라이언트 위치 | Kafka 주소 |
| --- | --- |
| PC의 Eclipse·JAR | `localhost:9092` |
| Compose 백엔드 | `kafka:19092` |
| Kubernetes Pod | `host.docker.internal:29092` |

서로 다른 listener를 사용해 각 클라이언트가 실제 접근 가능한 주소를 Kafka에서 안내받도록 구성했습니다.

### 이미지와 Namespace

```powershell
.\gradlew.bat test bootJar
```

빌드 성공 후, 새 환경에 `v3` 이미지가 없을 때 생성합니다. 기존 버전 태그는 다른 내용으로 덮어쓰지 않습니다.

```powershell
docker build -t admin-api:v3 .
kubectl --context docker-desktop apply -f k8s/namespace.yaml
kubectl --context docker-desktop apply -f k8s/backend-configmap.yaml
```

`imagePullPolicy: Never`이므로 이 로컬 클러스터에서 사용할 이미지가 준비돼 있어야 합니다. 다른 클러스터에서는 레지스트리와 이미지 전달 방법을 별도로 구성합니다.

### Secret 최초 등록

아래 입력값은 MySQL `admin` 계정 비밀번호와 Keycloak `admin-api` Client Secret입니다. root 또는 Keycloak 관리자 비밀번호를 넣지 않습니다. 이미 Secret이 있다면 내용을 출력하지 않고 존재 여부만 확인하고 이 최초 생성 절차를 건너뜁니다.

```powershell
kubectl --context docker-desktop -n admin-portfolio get secret backend-secret
```

없는 경우 아래를 순서대로 실행합니다. Secret JSON은 파일·화면에 출력하지 않고 kubectl 표준 입력으로 전달합니다.

```powershell
$dbInput = Read-Host "MySQL admin password" -AsSecureString
$clientInput = Read-Host "Keycloak Client Secret" -AsSecureString
if ($dbInput.Length -eq 0 -or $clientInput.Length -eq 0) {
    throw "Both values are required."
}
$secretObject = @{
    apiVersion = 'v1'
    kind = 'Secret'
    metadata = @{ name = 'backend-secret'; namespace = 'admin-portfolio' }
    type = 'Opaque'
    stringData = @{
        SPRING_DATASOURCE_PASSWORD = [System.Net.NetworkCredential]::new('', $dbInput).Password
        KEYCLOAK_CLIENT_SECRET = [System.Net.NetworkCredential]::new('', $clientInput).Password
    }
}
$previousOutputEncoding = $OutputEncoding
try {
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $secretObject | ConvertTo-Json -Depth 5 |
        kubectl --context docker-desktop create -f -
    if ($LASTEXITCODE -ne 0) { throw "Secret creation failed." }
} finally {
    $OutputEncoding = $previousOutputEncoding
    Remove-Variable secretObject, dbInput, clientInput
}
```

### 백엔드 배포

PC의 Java 서버, Compose 백엔드, 기존 `port-forward`가 8080을 사용 중이면 먼저 종료합니다. Compose 백엔드만 종료하는 명령은 다음과 같습니다.

```powershell
docker compose stop backend
kubectl --context docker-desktop apply -f k8s/backend-deployment.yaml
kubectl --context docker-desktop apply -f k8s/backend-service.yaml
kubectl --context docker-desktop -n admin-portfolio rollout status deployment/backend --timeout=240s
kubectl --context docker-desktop -n admin-portfolio get deployment backend -o wide
kubectl --context docker-desktop -n admin-portfolio get service backend
```

Deployment `READY 2/2`, 이미지 `admin-api:v3`, Service 외부 주소 `localhost`를 확인합니다. 아래 요청과 실제 화면 조회로 확인합니다.

```powershell
curl.exe -i http://localhost:8080/actuator/health
curl.exe -i http://localhost:8080/actuator/health/liveness
curl.exe -i http://localhost:8080/actuator/health/readiness
```

React 개발 서버는 별도 프로젝트에서 실행해야 합니다. 화면은 `http://localhost:5173`이며 로그인·목록 조회를 확인합니다.

## 2. 기존 환경 다시 실행

Docker Desktop과 로컬 Kubernetes를 시작한 다음 실행합니다. 이미지·Secret·볼륨이 유지돼 있다면 재생성할 필요가 없습니다.

```powershell
docker compose up -d mysql redis kafka keycloak
docker compose ps
kubectl --context docker-desktop get nodes
kubectl --context docker-desktop -n admin-portfolio get secret backend-secret
kubectl --context docker-desktop apply -f k8s/backend-configmap.yaml
kubectl --context docker-desktop apply -f k8s/backend-deployment.yaml
kubectl --context docker-desktop apply -f k8s/backend-service.yaml
kubectl --context docker-desktop -n admin-portfolio rollout status deployment/backend --timeout=240s
```

인프라 준비 확인은 최초 실행과 동일합니다. Kubernetes 모드에서는 `docker compose up -d`로 모든 서비스를 시작하지 않습니다. Compose 백엔드까지 시작하면 8080 충돌이 발생할 수 있습니다.

## 3. 코드 변경 후 버전 업데이트

기존 `v3`를 덮어쓰지 않고 다음 버전의 이미지를 만듭니다. 아래 `v4`는 다음 배포의 예시이며 아직 배포한 버전은 아닙니다.

```powershell
.\gradlew.bat test bootJar
```

성공한 뒤 실행합니다.

```powershell
docker build -t admin-api:v4 .
```

`k8s/backend-deployment.yaml`의 `image`를 `admin-api:v4`으로 수정한 후 적용합니다.

```powershell
kubectl --context docker-desktop apply -f k8s/backend-deployment.yaml
kubectl --context docker-desktop -n admin-portfolio rollout status deployment/backend --timeout=240s
kubectl --context docker-desktop -n admin-portfolio get deployment backend -o wide
```

`maxSurge: 1`, `maxUnavailable: 0`, `minReadySeconds: 5`로 새 Pod가 준비된 후 기존 Pod를 순차 종료합니다. 종료 중인 Pod까지 포함하면 목록에 일시적으로 더 많은 Pod가 보일 수 있습니다.

ConfigMap이나 Secret을 환경변수로 주입하는 경우, 값만 변경해도 기존 Pod의 환경변수가 자동 갱신되지는 않습니다. 필요한 설정을 변경한 뒤 아래로 교체합니다.

```powershell
kubectl --context docker-desktop -n admin-portfolio rollout restart deployment/backend
```

## 4. 이력 확인과 롤백

```powershell
kubectl --context docker-desktop -n admin-portfolio rollout history deployment/backend
```

Revision은 이미지 태그와 다른 값입니다. 실행 이력에서 복구할 버전을 확인한 뒤 실제 번호를 입력합니다.

```powershell
$targetRevision = Read-Host "Restore revision number"
kubectl --context docker-desktop -n admin-portfolio rollout history deployment/backend --revision=$targetRevision
```

출력된 이미지와 설정이 복구 대상인지 확인하고 실행합니다.

```powershell
kubectl --context docker-desktop -n admin-portfolio rollout undo deployment/backend --to-revision=$targetRevision
kubectl --context docker-desktop -n admin-portfolio rollout status deployment/backend --timeout=240s
```

롤백은 Pod 템플릿을 복원합니다. DB 마이그레이션·업무 데이터·ConfigMap·Secret까지 되돌리지 않습니다. 롤백 후 로컬 Deployment YAML의 이미지도 정상 복구 버전에 맞춰야 다음 `apply`가 실패 버전을 다시 배포하지 않습니다. `revisionHistoryLimit: 3`으로 보관 이력에도 범위가 있습니다.

현재 `v1`은 비밀번호 외부화 이전의 로컬 실습 이미지이므로 공개하거나 향후 배포의 기본 복구 버전으로 사용하지 않습니다. 정상 `v2` 이후 버전을 복구 기준으로 사용합니다.

## 5. 상태 확인과 종료

```powershell
kubectl --context docker-desktop -n admin-portfolio get pods
kubectl --context docker-desktop -n admin-portfolio logs -l app.kubernetes.io/name=admin-api --prefix=true --tail=30
kubectl --context docker-desktop -n admin-portfolio get events --sort-by=.metadata.creationTimestamp
```

`logs -f`나 `get pods -w`에서 Ctrl+C는 관찰만 종료합니다. Pod는 계속 실행됩니다. LoadBalancer로 접속하므로 `port-forward` 창은 필요하지 않습니다.

데이터와 설정을 보존하고 실습 앱과 인프라를 잠시 멈출 때:

```powershell
kubectl --context docker-desktop -n admin-portfolio scale deployment/backend --replicas=0
docker compose stop backend mysql redis kafka keycloak
```

다음 실행에서 Deployment YAML을 `apply`하면 선언된 2개로 복원됩니다. 일상적인 종료에 Namespace 삭제, Kubernetes 클러스터 초기화, 볼륨 삭제를 사용하지 않습니다.

### Compose 또는 Eclipse 방식으로 전환

Kubernetes가 8080을 계속 제공하지 않도록 Service를 삭제하고 Pod를 0개로 줄입니다. 선언 파일과 Secret은 유지합니다.

```powershell
kubectl --context docker-desktop -n admin-portfolio scale deployment/backend --replicas=0
kubectl --context docker-desktop -n admin-portfolio delete service backend
```

Compose 모드에서는 먼저 `bootJar`를 성공시킨 뒤 `docker compose up -d --build backend`를 실행합니다. Eclipse 모드는 Compose 인프라만 실행하고 [환경변수 설정](local-secrets.md) 후 Eclipse에서 시작합니다. 다시 Kubernetes 모드로 돌아갈 때는 다른 백엔드와 포트 연결을 종료하고 Deployment·Service YAML을 적용합니다.

## 6. 실습에서 확인한 결과

- Compose 백엔드에서 주문 생성 → Outbox 처리 → Kafka 통계 Consumer 수신 확인.
- Kubernetes Pod 2개를 실행하고 Pod A에서 로그인 후 Pod B로 접속을 바꿔 Redis 세션 공유 확인.
- Pod 하나 삭제 후 새 이름의 Pod가 생성돼 2개로 복구되는 동작 확인.
- 존재하지 않는 로컬 이미지 배포 시 기존 Pod 유지 및 정상 Revision 롤백 확인.
- LoadBalancer Service 접속 상태에서 롤링 재시작 중 1초 간격 상태 조회가 모두 HTTP 200으로 응답한 것을 관찰.
- 비밀번호 외부화를 반영한 `v2` 이미지로 롤링 업데이트하고 Deployment `2/2` 확인.
- 주문 생성의 Command·Result 및 이벤트 저장 인터페이스를 반영한 `v3`를 배포하고 Deployment `2/2`, 상태 점검 URL 세 개의 HTTP 200 확인. API 응답 변환과 트랜잭션은 자동 테스트로 검증.

상태 조회 관찰은 해당 실습의 결과이며 모든 업무 요청과 장시간 부하의 무중단을 입증하는 것은 아닙니다. 기본 Readiness는 DB·Redis 연결까지 포함하지 않습니다. 단일 노드의 Pod 복구와 여러 물리 노드의 장애 대응은 구분합니다.
