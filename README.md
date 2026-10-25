# admin-api

B2B 주문·재고 운영 관리자 프로젝트의 Spring Boot API입니다.
회원·상품·재고·주문 관리에서 시작해 인증, Redis 활용, Kafka 이벤트 처리와 Transactional Outbox를 단계적으로 구현했습니다.

학습·포트폴리오용 프로젝트로, 로컬 검증 결과를 운영 환경의 고가용성이나 성능 보장으로 표현하지 않습니다.
React 관리 화면은 별도 프로젝트이며, 이 디렉터리는 백엔드 프로젝트입니다.

## 주요 기능

| 영역 | 구현 내용 |
| --- | --- |
| 인증·권한 | Spring Security, DB 사용자 인증, Keycloak OIDC 로그인, CSRF 보호 |
| 회원 관리 | 회원 등록·조회·수정·삭제, 입력 검증과 이메일 중복 처리 |
| 상품·재고 | 상품 등록·수정·조회, 입출고, 낙관적 락으로 동시 변경 충돌 감지 |
| 주문 | 주문·주문 항목 저장과 재고 차감의 트랜잭션 처리, 검색·페이징·상세 조회 |
| Redis | 공유 로그인 세션, 상품 조회 캐시와 무효화, Lua 기반 요청 횟수 제한 |
| Kafka | 주문 생성 이벤트, 독립 Consumer Group, 소비 실패 재시도와 DLT |
| 주문 통계 | 이벤트 기반 일별 통계, 주문 번호 기준 중복 반영 방지 |
| Outbox | 주문 이벤트·상품 캐시 삭제 요청 저장, DB 선점, 토큰 검증, 재시도와 만료 복구 |
| 배포 | Docker 이미지, Kubernetes Pod 2개, 공유 세션, 상태 점검, 롤링 업데이트·롤백 |
| CI/CD | Jenkins 전용 에이전트, 테스트 결과·JAR 보관, 빌드 번호별 이미지 생성, Kubernetes 배포 |

요청 횟수 제한은 동일 요청의 중복 주문 생성 방지와 다릅니다.

## 기술 구성

- Java 17, Spring Boot 4.1.0, Gradle Wrapper
- Spring Data JPA, QueryDSL(OpenFeign fork), MySQL 8.4, Flyway
- Spring Security, OAuth2/OIDC, Keycloak
- Redis, Spring Session, Spring Cache
- Apache Kafka, Spring Kafka
- JUnit 5, AssertJ, Mockito, MockMvc, H2, Testcontainers, Embedded Kafka
- 로컬 인프라: Docker Compose, Docker Desktop Kubernetes
- CI/CD: Jenkins Pipeline, GitHub

하나의 Spring Boot 서비스를 Docker Desktop Kubernetes의 Pod 2개로 배포합니다. MySQL·Redis·Kafka·Keycloak은 Compose에서 실행하며, 독립 서비스로 분리한 MSA 구성은 아닙니다.

## 배포 문서

- [Docker·Kubernetes 실행 가이드](docs/deployment.md): 최초 준비, 재실행, 이미지 업데이트, 롤백, 종료
- [Jenkins 빌드·배포 가이드](docs/jenkins.md): 전용 에이전트, 인증정보, 수동 시작 파이프라인, 토큰 갱신
- [실행 환경별 비밀값 전달](docs/local-secrets.md): Compose `.env`, Eclipse 환경변수, Kubernetes Secret
- [Kafka·Outbox 설계](docs/kafka-outbox.md): 이벤트 처리와 재시도 설계

## 로컬 실행

아래 명령은 Windows PowerShell 기준입니다.
아래는 PC에서 백엔드를 실행하는 방식입니다. Compose 백엔드 또는 Kubernetes Service가 이미 8080을 사용 중이면 [배포 가이드](docs/deployment.md)의 실행 방식 전환 절차를 먼저 따릅니다.

1. JDK 17과 Docker Desktop을 준비합니다.
2. `compose.yaml`과 `src/main/resources/application-local.yaml`의 연결 설정을 확인합니다.
   로컬 `.env`는 `.env.example`을 참고해 준비합니다. 실행 방식별 비밀값 전달은 [설정 가이드](docs/local-secrets.md)를 따릅니다. Spring Boot와 Eclipse는 `.env`를 자동으로 읽지 않습니다.
3. Keycloak Realm·Client·사용자와 애플리케이션 OIDC 설정을 맞춥니다. Compose 기동만으로 해당 설정이 자동 구성된다고 가정하지 않습니다.
4. 애플리케이션 로그인 사용자를 준비합니다. Keycloak 관리자 계정과 애플리케이션 회원 계정은 구분합니다.

```powershell
docker volume create admin-api-kafka-data
docker compose up -d mysql redis kafka keycloak
docker compose ps
.\gradlew.bat bootRun
```

기본 프로필은 `local`입니다. 컨테이너 실행 상태뿐 아니라 서비스 준비 상태와 애플리케이션 시작 로그를 확인합니다.

| 서비스 | 로컬 접속 위치 |
| --- | --- |
| 백엔드 | http://localhost:8080 |
| Keycloak | http://localhost:8180 |
| MySQL | localhost:3306 / DB: admin_portfolio |
| Redis | localhost:6379 |
| Kafka | localhost:9092 |

`bootRun`은 서버 실행 중 계속 진행 상태로 표시됩니다. 기동 완료는 `Started ...` 로그로 확인합니다.
로그인 후 `/api/auth/me`로 인증 상태를 확인할 수 있습니다. 변경 API는 세션 인증과 CSRF 처리가 필요합니다.

### Kafka 토픽 준비

새 환경에서는 기본 이벤트 토픽과 DLT를 준비합니다. 복제 계수 1은 로컬 단일 브로커용입니다.

```powershell
docker exec admin-api-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic order-created --partitions 1 --replication-factor 1
docker exec admin-api-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic order-created.DLT --partitions 1 --replication-factor 1
```

### 두 번째 애플리케이션 실행

```powershell
.\gradlew.bat bootRun --args="--server.port=8081 --spring.profiles.active=local"
```

같은 설정으로 MySQL·Redis·Kafka를 공유합니다. 다중 애플리케이션 인스턴스 실습이며, DB·Redis·Kafka 자체의 고가용성 구성은 아닙니다.

## Outbox 설정

`application-local.yaml`의 `app.outbox`에서 조절합니다. 일반적인 설정 변경은 애플리케이션 재시작이 필요합니다.

```yaml
app:
  outbox:
    enabled: true
    fixed-delay-ms: 5000
    initial-delay-ms: 10000
    claim-duration-seconds: 120
    retry-delay-seconds: 30
    max-events-per-run: 100
    recovery-batch-size: 100
```

- `fixed-delay-ms`: 주기 작업 종료 후 다음 실행까지의 대기 시간
- `claim-duration-seconds`: 한 이벤트의 처리 권한 유지 시간
- `max-events-per-run`: 서버별 실행당 최대 건수. 한 건씩 선점하며 초당 처리량 제한은 아님
- `recovery-batch-size`: 실행당 최대 만료 복구 건수
- `enabled`: 생략하거나 false이면 Outbox 스케줄러를 등록하지 않음

실행 인자 `--app.outbox.enabled=false`로 특정 인스턴스의 Outbox 스케줄러만 끌 수 있습니다. 주문의 Outbox 저장은 계속 동작합니다.

상세 설계와 검증 결과는 [Kafka·Outbox 설계 문서](docs/kafka-outbox.md)를 참고합니다.

## 테스트

### 기본 테스트

```powershell
.\gradlew.bat test
```

H2 기반 트랜잭션 테스트, 단위 테스트, MockMvc 테스트, Embedded Kafka 테스트 등이 포함됩니다.
MySQL·실제 Redis 사용 테스트는 환경변수를 켜지 않으면 건너뜁니다. 기본 테스트 성공이 모든 외부 인프라 테스트 실행을 의미하지는 않습니다.

### MySQL 통합 테스트를 포함한 전체 재실행

Docker Desktop이 필요합니다. Testcontainers가 개발 DB와 분리된 임시 MySQL을 실행하고 Flyway 마이그레이션을 적용합니다.

```powershell
$previousMysqlTestFlag = $env:RUN_MYSQL_TESTS
try {
    $env:RUN_MYSQL_TESTS = 'true'
    .\gradlew.bat test --rerun-tasks
} finally {
    $env:RUN_MYSQL_TESTS = $previousMysqlTestFlag
}
```

### 실제 Redis 요청 제한 테스트

이 테스트는 Testcontainers가 아닌 `localhost:6379`의 Redis를 사용합니다. 로컬 개발 Redis를 준비하고 운영 Redis에서는 실행하지 않습니다.

```powershell
$previousRedisTestFlag = $env:RUN_REDIS_TESTS
try {
    $env:RUN_REDIS_TESTS = 'true'
    .\gradlew.bat test --tests '*OrderRateLimitIntegrationTest'
} finally {
    $env:RUN_REDIS_TESTS = $previousRedisTestFlag
}
```

결과: `build/reports/tests/test/index.html`. 성공 여부와 함께 실행·건너뜀 건수를 확인합니다.

## 대표 API

일부 API만 나열했으며, 인증과 권한에 따라 접근이 제한됩니다.

| 메서드 | 경로 | 역할 |
| --- | --- | --- |
| GET | /api/auth/me | 로그인 정보 |
| GET | /api/auth/csrf | CSRF 토큰 |
| GET / POST | /api/users | 회원 목록 / 등록 |
| POST | /api/products | 상품·초기 재고 등록 |
| GET / PUT | /api/products/{productId} | 상품 조회 / 수정 |
| GET | /api/inventories | 재고 목록 |
| POST | /api/products/{productId}/stock-in | 입고 |
| POST | /api/products/{productId}/stock-out | 출고 |
| GET / POST | /api/orders | 주문 검색 / 생성 |
| GET | /api/orders/{orderId} | 주문 상세 |
| GET | /api/order-statistics/daily | 기간별 일일 주문 통계 |

## 데이터베이스와 개발 원칙

주문 생성은 웹 요청을 `CreateOrderCommand`로 변환해 처리하고, `CreateOrderResult`를 API 응답으로 변환합니다. 이벤트 저장은 `OrderEventStore` 인터페이스를 통해 기존 Outbox 구현에 위임합니다. 주문·재고·Outbox의 기존 트랜잭션 경계는 유지하며, JPA와 조회 기능 전체를 분리한 구조는 아닙니다.

- 스키마는 `src/main/resources/db/migration`의 Flyway SQL로 변경합니다. 이미 적용한 마이그레이션 대신 새 버전을 추가합니다.
- 로컬의 `ddl-auto: validate`는 엔티티와 스키마의 일치를 검사합니다.
- 일반 테스트는 별도 H2 DB를 사용하고, MySQL 전용 쿼리·잠금은 MySQL 통합 테스트로 검증합니다.
- QueryDSL Q 클래스는 Gradle annotation processor가 생성합니다. 생성 파일을 직접 수정하지 않습니다.
- Eclipse에서는 Gradle Refresh를 사용합니다. 생성 소스 설정은 `build.gradle`에서 관리합니다.

## 공개·배포 전 확인

- 비밀번호는 로컬 `.env`·환경변수·Kubernetes Secret으로 전달합니다. `.env`는 커밋하지 않습니다. 외부화 이전에 만든 `v1` 이미지에는 기존 설정이 남아 있으므로 공개하지 않습니다.
- 회사 코드·업무 데이터·개인정보·실제 토큰은 저장소나 로그에 포함하지 않습니다.
- 로컬 실행용 설정을 외부에 그대로 노출하지 않습니다. TLS, 접근 제어, 백업·복구, 모니터링은 별도로 설계해야 합니다.
- 현재 검증은 로컬 환경 기준입니다. 부하 테스트와 운영 고가용성 구축은 별도 작업입니다.
