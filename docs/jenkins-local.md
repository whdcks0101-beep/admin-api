# Jenkins 빌드·배포

Jenkins에서 `admin-api-pipeline`의 **지금 빌드**를 누르면 테스트부터 로컬 Kubernetes 배포까지 순서대로 실행합니다. GitHub push 자동 실행, 주기 실행, 별도 승인 단계는 사용하지 않습니다.

```text
수동 빌드 시작
  → GitHub main checkout
  → Gradle clean test / 테스트 결과 게시
  → bootJar / 실행 JAR 보관
  → admin-api:ci-${BUILD_NUMBER} 이미지 생성
  → backend Deployment 이미지 변경
  → rollout 완료 확인
```

## 실행 구성

| 항목 | 구성 |
| --- | --- |
| Jenkins 화면 | http://localhost:8090 |
| 컨트롤러 | `compose.jenkins.yaml`, 작업 설정과 이력 관리 |
| 에이전트 | `compose.jenkins.agent.yaml`, 이름 `admin-api-agent`, 라벨 `admin-api-build` |
| 실행 환경 | `docker/jenkins-agent/Dockerfile`, Remoting Java 21 / 프로젝트 빌드 Java 17 |
| 이미지·배포 도구 | Docker CLI, Buildx, kubectl 1.36.1 |
| 파이프라인 | 저장소 루트의 `Jenkinsfile` |
| Kubernetes 계정 | `k8s/ci/jenkins-deployer.yaml`의 `jenkins-deployer` |

컨트롤러와 에이전트는 별도 컨테이너이며 WebSocket으로 연결합니다. 에이전트 작업 공간과 Gradle 다운로드 캐시는 볼륨에 보관합니다. 에이전트는 로컬 Docker 소켓으로 Docker Desktop 엔진을 사용합니다. 이 소켓 접근은 이미지 생성뿐 아니라 해당 엔진의 컨테이너 관리 권한도 제공하므로 본인 저장소의 신뢰하는 작업을 실행하는 로컬 전용 구성입니다.

이미지는 Docker Desktop에 생성하고 같은 로컬 Kubernetes에서 `imagePullPolicy: Never`로 사용합니다. 외부 이미지 레지스트리로 push하는 단계는 없습니다.

## 최초 Jenkins 준비

1. [Kubernetes 실행 가이드](deployment.md)에 따라 인프라와 `backend` Deployment를 준비합니다.
2. `docker compose -f compose.jenkins.yaml up -d`로 컨트롤러를 시작합니다.
3. 초기 설정에서 권장 플러그인과 관리자 계정을 준비합니다. 초기 암호는 본인 터미널에서만 `docker exec admin-api-jenkins cat /var/jenkins_home/secrets/initialAdminPassword`로 확인합니다.
4. Nodes에서 Permanent Agent `admin-api-agent`를 생성합니다. 실행기 수 1, 작업 경로 `/home/jenkins/agent`, 라벨 `admin-api-build`, 라벨이 일치하는 작업만 실행하도록 설정합니다. 연결 방식은 `Launch agent by connecting it to the controller`입니다.
5. 다음 스크립트를 실행하고 노드 화면의 에이전트 Secret을 숨김 입력창에 넣습니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-JenkinsAgent.ps1
```

에이전트 이미지를 수정한 뒤에도 이 스크립트로 다시 빌드하고 컨테이너를 교체합니다. 기존 컨테이너를 단순 재시작하면 새 이미지가 적용되지 않습니다. 일반 재실행은 `docker start admin-api-jenkins admin-api-jenkins-agent`로 가능합니다.

## 인증정보와 작업 설정

Jenkins 관리 → Credentials → System → Global credentials에 등록합니다.

| ID | 종류 | 내용 |
| --- | --- | --- |
| `github-admin-api` | Username with password | GitHub 사용자명과 저장소 Contents 읽기 권한의 토큰 |
| `k8s-admin-portfolio` | Secret file | 배포 계정의 kubeconfig 파일 |

Kubernetes 권한과 접속 파일은 다음 순서로 준비합니다.

```powershell
kubectl --context docker-desktop apply -f k8s/ci/jenkins-deployer.yaml
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\New-JenkinsKubeconfig.ps1
```

생성 파일은 `%USERPROFILE%\.kube\jenkins\admin-api.json`이며 Git 저장소 밖에 있습니다. 파일 내용은 로그나 저장소에 복사하지 않고 Secret file로 업로드합니다. 서버 인증서 검증에는 클러스터 CA와 `tls-server-name`을 사용합니다.

배포 계정에는 `admin-portfolio`의 `backend` Deployment 조회·목록·감시·patch 권한을 부여합니다. Secret 직접 조회나 다른 Deployment 변경 권한은 부여하지 않습니다. 이 Role은 이미지 필드만 제한하는 정책은 아니며, `backend` Pod 템플릿을 변경할 수 있는 권한입니다.

Pipeline 작업 `admin-api-pipeline`을 생성하고 다음을 설정합니다.

- Definition: Pipeline script from SCM
- SCM: Git
- Repository URL: 본인의 `admin-api` GitHub 저장소 HTTPS 주소
- Credentials: `github-admin-api`
- Branch Specifier: `*/main`
- Script Path: `Jenkinsfile`

## 실행과 결과 확인

1. 변경을 커밋하고 GitHub `main`에 push합니다.
2. Jenkins에서 **지금 빌드**를 누릅니다.
3. Test Result와 Build Artifacts의 JAR을 확인합니다.
4. `deployment "backend" successfully rolled out`과 `Finished: SUCCESS`를 확인합니다.

```powershell
kubectl --context docker-desktop -n admin-portfolio get deployment backend -o wide
curl.exe -i http://localhost:8080/actuator/health/readiness
```

배포 이미지의 `ci-번호`가 Jenkins 빌드 번호와 일치하는지 확인하고 화면 로그인·조회도 확인합니다. `disableConcurrentBuilds()`로 같은 작업의 동시 실행을 막습니다. 기본 테스트는 H2·MockMvc·Embedded Kafka 등을 실행하며, 환경변수로 활성화하는 MySQL·Redis 통합 테스트는 현재 파이프라인에 포함하지 않습니다.

테스트 실패 시 이후 단계는 실행하지 않습니다. 롤아웃이 180초 이내 끝나지 않으면 Jenkins 빌드는 실패합니다. 자동 롤백은 수행하지 않으므로 [롤백 절차](deployment.md#4-이력-확인과-롤백)로 상태를 확인하고 복구합니다.

## 토큰 갱신

Kubernetes 토큰은 24시간 유효기간을 요청해 발급하며 실제 만료 시각은 스크립트가 출력합니다. 만료 시 위 `New-JenkinsKubeconfig.ps1`을 다시 실행한 뒤, Jenkins의 **기존 `k8s-admin-portfolio` 인증정보를 수정해 새 파일로 교체**합니다. 로컬 파일만 갱신하면 Jenkins에 업로드한 사본은 바뀌지 않습니다. 인증정보 ID는 유지합니다.

GitHub 토큰 만료 시에는 `github-admin-api`의 Password를 새 GitHub 토큰으로 교체합니다. GitHub 토큰, 에이전트 Secret, Kubernetes 토큰은 서로 다른 인증정보입니다.

## 이미지와 선언 파일 관리

`k8s/backend-deployment.yaml`의 `admin-api:v3`는 최초 환경 준비용 기본 이미지입니다. Jenkins는 실행 중인 Deployment를 `ci-번호` 이미지로 변경하며 저장소 YAML을 자동 수정하지 않습니다. CI 배포 후 이 YAML을 그대로 다시 적용하면 `v3`로 돌아갈 수 있습니다. 기존 환경을 재시작할 때는 Deployment를 재적용하지 말고 필요하면 `scale --replicas=2`를 사용합니다. 선언 파일을 수정해 적용해야 한다면 현재 유지할 이미지 태그도 함께 맞춥니다.

롤백에 사용할 이미지는 로컬에 남아 있어야 합니다. Jenkins의 최근 빌드 10개 보관 설정은 Docker 이미지 삭제 정책이 아닙니다. 빌드 번호를 초기화하거나 다른 작업에서 같은 이미지 이름과 번호를 재사용하지 않습니다.
