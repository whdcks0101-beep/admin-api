# Jenkins에서 AWS Lightsail 배포

두 Jenkins 작업은 기존과 같이 **지금 빌드**로 수동 실행한다. 자동 트리거나 별도 승인 단계는 없다.

- 백엔드: GitHub checkout → 테스트 → JAR → Docker 이미지 → SSH 전송 → AWS backend 교체·상태 확인
- 프론트: GitHub checkout → 린트·테스트 → HTTPS 동일 출처용 이미지 → SSH 전송 → AWS frontend 교체·상태 확인
- 이미지 태그: `aws-빌드번호-Git커밋12자리`
- 공개 주소: https://13-124-81-142.sslip.io

## 최초 자격 증명 등록

Jenkins 관리 → Credentials → System → Global credentials → Add Credentials:

| 항목 | 값 |
| --- | --- |
| Kind | Secret file |
| File | PC의 `.ssh/LightsailDefaultKey-ap-northeast-2.pem` |
| ID | `lightsail-ssh-key` |

개인키 내용은 소스·콘솔에 붙여넣지 않는다. 기존 GitHub credential `github-admin-api`, agent label `admin-api-build`, 두 작업의 Script Path `Jenkinsfile`은 유지한다.

## 서버 준비와 동작

`deploy/lightsail`의 최초 배포를 완료하고 `apply-image.sh`를 서버 `/home/ubuntu/admin-portfolio`에 설치해야 한다. `known_hosts`는 브라우저 SSH에서 대조한 서버 공개키다. 서버 교체 시 지문을 다시 검증하고 갱신한다. 호스트 키 검증을 끄지 않는다.

`jenkins-deploy.sh`는 자격 증명의 키를 임시 디렉터리에 권한 600으로 복사한다. `docker save`를 SSH의 `sudo docker load`로 전송하며 성공·실패 모두 임시 키를 정리한다. 개인키나 `.env`는 Docker 이미지에 넣지 않는다.

서버 `apply-image.sh`는 서비스·태그를 검사하고 `flock`으로 두 작업의 배포를 직렬화한다. 대상 서비스만 교체하고 healthcheck를 기다린다. 성공한 이미지 태그만 `.env`에 원자적으로 기록한다. 기존 DB·Kafka·Redis 및 다른 서비스의 이미지 태그는 유지한다.

단일 컨테이너 교체 시 잠시 연결이 끊길 수 있다. 상태 점검 실패 시 Jenkins는 실패하며 자동 롤백하지 않는다. 이때 `.env`에는 이전 태그가 남지만 실행 중인 컨테이너는 실패한 새 이미지일 수 있다. 서버에서 로그를 확인한 다음 `.env`의 이전 이미지로 다음 명령을 실행해 복구한다.

```bash
cd /home/ubuntu/admin-portfolio
sudo docker compose up -d --no-deps --pull never --wait backend
# 프론트 복구는 backend 대신 frontend
```

서버 Compose/Caddy 설정 자체의 변경은 이 파이프라인이 자동 적용하지 않는다. 설정 변경은 검증 후 별도로 배포한다. 이전 이미지 삭제·볼륨 삭제는 자동 수행하지 않는다.

## 실행 확인

1. 변경 사항을 GitHub main에 push한다.
2. Jenkins에서 대상 작업의 **지금 빌드**를 누른다.
3. `Deployment verified`와 `Finished: SUCCESS`를 확인한다.
4. 공개 HTTPS 사이트의 로그인과 조회를 확인한다.

현재 파이프라인의 테스트에는 별도 활성화가 필요한 MySQL·Redis 통합 테스트는 포함하지 않는다. 성공 판정은 배포 상태 점검이며 실제 업무 흐름 확인과 구분한다.

## 기존 로컬 실습

기존 파이프라인은 각 저장소의 `Jenkinsfile.local`에 보존했다. 로컬 K8s 설명은 [기존 로컬 가이드](jenkins-local.md)를 참고한다. 로컬용 작업을 별도로 만든다면 Script Path를 `Jenkinsfile.local`로 지정한다. 기본 Jenkinsfile은 이제 AWS 배포용이다.

Jenkins와 Docker Desktop은 PC에서 실행하므로 빌드할 때 PC가 켜져 있어야 한다. AWS에 이미 배포된 서비스는 PC를 꺼도 계속 실행된다.