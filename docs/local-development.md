# 로컬 개발 및 테스트 안내

## 로컬 실행

아래 명령은 Windows PowerShell 기준입니다.
아래는 PC에서 백엔드를 실행하는 방식입니다. Compose 백엔드 또는 Kubernetes Service가 이미 8080을 사용 중이면 [배포 가이드](deployment.md)의 실행 방식 전환 절차를 먼저 따릅니다.

1. JDK 17과 Docker Desktop을 준비합니다.
2. `compose.yaml`과 `src/main/resources/application-local.yaml`의 연결 설정을 확인합니다.
   로컬 `.env`는 `.env.example`을 참고해 준비합니다. 실행 방식별 비밀값 전달은 [설정 가이드](local-secrets.md)를 따릅니다. Spring Boot와 Eclipse는 `.env`를 자동으로 읽지 않습니다.
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

상세 설계와 검증 결과는 [Kafka·Outbox 설계 문서](kafka-outbox.md)를 참고합니다.

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
