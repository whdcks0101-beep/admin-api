# Lightsail 배포

Ubuntu 서버 한 대에서 Compose로 실행한다. 로컬 Kubernetes/Jenkins 설정과 별개다.

## 접속 구조

`브라우저 → HTTPS Caddy → React / Spring Boot / Keycloak`

- 공개 주소: `https://13-124-81-142.sslip.io`
- `/api/*`, `/login`, `/logout`, `/oauth2/*`: 백엔드
- `/sso/*`: Keycloak (관리 콘솔과 master realm은 공개 경로에서 차단)
- 나머지 경로: React
- MySQL, Redis, Kafka와 백엔드는 호스트에 포트를 공개하지 않는다.
- Keycloak의 `127.0.0.1:8180`은 서버 내부 관리용이다. 필요하면 SSH 터널과 관리 CLI를 사용한다.
- Caddy가 인증서를 발급·갱신한다. DNS가 서버 IP를 가리키고 80/443 인바운드가 열려 있어야 한다.
- sslip.io는 제3자가 제공하는 무료 DNS다. 소유 도메인으로 교체하려면 PUBLIC_HOST 및 OIDC 클라이언트 URL도 변경한다.

## 최초 준비

이 폴더의 파일을 서버 `/home/ubuntu/admin-portfolio`에 복사한 뒤 실행한다.

```bash
cd /home/ubuntu/admin-portfolio
bash init-env.sh
sudo docker compose config --quiet
```

`.env`는 서버에서 무작위로 생성하며 기존 파일을 덮어쓰지 않는다. Git에 올리거나 `docker compose config` 출력으로 비밀값을 노출하지 않는다. 검증에는 반드시 `--quiet`를 붙인다.

MySQL 초기화 스크립트는 **빈 볼륨의 첫 기동에서만** 실행된다. `.env` 값을 바꿔도 기존 DB 비밀번호가 바뀌지 않는다. Keycloak realm import 역시 기존 realm을 덮어쓰지 않는다.

## 이미지와 실행

PC에서 백엔드 JAR을 새로 빌드한 뒤 `admin-api:cloud-1` 이미지를 만든다. 백엔드는 `local,docker,cloud` 순서로 설정을 읽으며 cloud가 공개 URL·보안 쿠키·로그 수준을 덮어쓴다.

프론트 이미지는 **VITE_BACKEND_ORIGIN을 빈 문자열로 지정해** `admin-frontend:cloud-1`로 빌드해야 한다. 그러면 브라우저가 현재 HTTPS 주소로 API를 요청한다. 기존 localhost:8080 주소가 포함된 이미지는 사용하지 않는다.

이미지를 `docker save`로 저장해 서버로 전송하고 `sudo docker load`로 적재한 다음 실행한다.

```bash
sudo docker compose up -d
sudo docker compose ps
```

첫 실행에서 Keycloak 빌드와 DB 초기화, 인증서 발급에 시간이 걸릴 수 있다. 시작 직후 502가 보이면 컨테이너 상태를 먼저 확인한다.

## 첫 관리자 계정

서버에서 `sudo apt-get install -y python3-bcrypt`로 BCrypt 모듈을 준비한다. 그다음 브라우저 SSH 터미널에서 실행한다.

```bash
cd /home/ubuntu/admin-portfolio
python3 create-first-admin.py
```

로그인 이메일과 비밀번호를 직접 입력한다. 비밀번호는 표시되지 않고 명령행 인자나 파일로 저장하지 않으며, Spring DelegatingPasswordEncoder와 호환되는 `{bcrypt}` 해시만 DB에 저장한다. 회원이 하나라도 있으면 생성하지 않는다. 공개 회원가입 API가 아닌 SSH 관리자용 최초 설정 도구다. 이후 회원 생성은 관리자 화면을 사용한다.

## 확인 항목

### 클라우드 관리자 SSO 연결

브라우저 SSH 터미널에서 `python3 link-admin-sso.py`를 실행하고 기존 서비스 관리자 이메일과 새 SSO 비밀번호를 입력한다. Keycloak 관리용 비밀번호는 서버 `.env`에서 읽으며 화면에 출력하지 않는다. 입력한 SSO 비밀번호도 파일·명령행 인자에 저장하지 않는다.

새 Keycloak 사용자를 비활성 상태로 생성하고, 발급된 subject와 공개 issuer를 `user_external_identities`에 저장한 다음 활성화한다. 기존 Keycloak 사용자가 있거나 이미 연결된 회원이면 자동으로 재연결하거나 비밀번호를 바꾸지 않고 중단한다. 중간 실패 시 상태를 점검한 후 처리한다. 이 스크립트는 신뢰된 SSH 관리자용 초기 설정 도구이며 공개 이메일 자동 연결 기능이 아니다.

`python3 link-admin-sso.py --check`는 관리 API 및 DB 접근만 확인하고 계정을 변경하지 않는다. 최초 배포에서 이 확인은 통과했다. 실제 SSO 로그인·로그아웃 검증은 사용자가 계정 설정을 마친 후 진행한다.

- HTTPS 접속과 인증서 유효성
- React 상세 경로 새로고침
- 로그인 전 `/api/auth/me` 인증 오류 및 관리자 경로 차단
- 별도로 만든 클라우드 계정으로 로그인·로그아웃·CSRF 처리
- OIDC 클라우드 사용자와 서비스 회원 연결 후 SSO
- 주문 생성 → Outbox → Kafka → 통계 반영

로컬 사용자·비밀번호·업무 데이터는 복사하지 않는다. 현재 realm 파일은 클라이언트 설정만 생성한다. 서비스 회원과 SSO 사용자 연결은 별도 초기 설정이 필요하다.

## 운영 메모

로그 순환, 컨테이너 메모리 제한, Redis noeviction, Kafka 72시간 보관을 설정했다. Redis는 세션과 요청 제한 데이터도 저장하므로 캐시처럼 임의 축출하지 않는다. 메모리가 가득 차면 쓰기가 실패하므로 사용량을 확인해야 한다.

`docker compose down -v`는 DB 등 영구 데이터를 지우므로 사용하지 않는다. Caddy 인증서 볼륨도 보존한다. 서버 한 대의 구성으로, 실제 부하와 장애 상황에서 자원 사용 및 복구를 추가 확인한다.

설정 검증 완료와 실제 배포 검증은 구분한다. 이미지 적재·최초 기동·사용자 설정·HTTPS 로그인 검증을 마친 후 공개 주소를 포트폴리오에 기재한다.

## 2026-10-26 최초 배포 확인

- `admin-api:cloud-1`, `admin-frontend:cloud-1`을 PC에서 빌드하고 서버에 적재했다.
- 백엔드 기본 테스트 79개 중 60개 통과, 별도 실행 조건이 있는 19개 건너뜀. 실패 없음.
- 새 Kafka 볼륨의 소유권 문제를 확인하여 `kafka-volume-init`에서 디렉터리 소유자를 1000:1000으로 설정하도록 수정했다. 초기화 컨테이너의 종료 코드 0은 정상이다.
- Let's Encrypt 인증서 발급 및 외부 HTTPS 검증 완료. 인증서 검증을 끄지 않고 확인했다.
- `/`, `/inventories`, `/login`: HTTP 200.
- 비로그인 `/api/auth/me`, `/api/users`: HTTP 401 JSON.
- 외부 `/actuator/health`, `/sso/admin/`: HTTP 404.
- OIDC discovery의 issuer가 공개 HTTPS 주소와 일치하고, 백엔드에서 Keycloak 로그인 화면으로 이동하는 것까지 확인했다.
- 세션 쿠키의 Secure, HttpOnly 속성 확인.
- 이 최초 기동 확인 시점에는 클라우드 사용자 생성과 SSO 계정 연결 전이었다. 이후 실제 로그인·주문·통계·SSO 확인을 완료했으며, 공개 체험 계정의 2026-10-27 검증 결과는 [체험 안내](../../docs/portfolio/demo-access.md#aws-검증-기록-2026-10-27)에 기록했다.
