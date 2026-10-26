# 핵심 구현 사례

## 1. 주문과 재고의 일관성

**문제:** 동시에 같은 재고를 변경하면 먼저 저장한 수량이 덮어써질 수 있다. 여러 상품 중 한 상품만 재고가 부족한 경우 앞서 차감한 재고도 복구되어야 한다.

**구현:** `Inventory`의 `@Version`으로 오래된 버전의 갱신을 거부한다. `OrderService.createOrder`에서 주문·주문 항목·재고 차감·Outbox 저장을 하나의 쓰기 트랜잭션으로 묶는다. 충돌은 API에서 409로 안내하고, 사용자가 재조회 후 다시 시도하도록 한다.

**검증:** 낙관적 락 충돌과 다품목 주문 실패 시 전체 롤백을 테스트한다. 실제 화면에서 주문 생성과 재고 차감도 확인했다.

소스: [Inventory](../../src/main/java/com/portfolio/admin_api/inventory/entity/Inventory.java), [OrderService](../../src/main/java/com/portfolio/admin_api/order/service/OrderService.java)

## 2. DB 커밋 후 이벤트 전달의 신뢰성

**문제:** DB 주문 저장이 성공해도 외부 Kafka 전송이 실패할 수 있다. 단순 커밋 후 리스너만으로는 재전송할 작업을 잃을 수 있다.

**구현:** 주문 트랜잭션에 이벤트를 Outbox로 함께 저장한다. Relay가 `FOR UPDATE SKIP LOCKED`로 한 건씩 선점하고 `PROCESSING` 상태와 claim token을 기록한다. 전송은 선점 트랜잭션 밖에서 수행한다. 완료·재시도 기록은 소유 토큰과 유효기간을 확인하고 DB 시간을 기준으로 판정한다. 만료된 선점은 복구한다.

**확장:** `OutboxEventSender` 구현체를 Registry로 찾아 주문 이벤트 전송과 상품 캐시 삭제 요청을 처리한다. 주문 서비스는 `OrderEventStore` 포트에 의존한다. 적용 범위는 주문 이벤트 저장 경계이며 전체 JPA 의존성을 분리한 구조로 주장하지 않는다.

**검증:** 주문과 Outbox의 동시 커밋·롤백, 선점·소유권·만료 복구 관련 테스트와 다중 인스턴스 실습을 수행했다.

소스: [OutboxEventRepository](../../src/main/java/com/portfolio/admin_api/outbox/repository/OutboxEventRepository.java), [OutboxRelay](../../src/main/java/com/portfolio/admin_api/outbox/service/OutboxRelay.java)

## 3. 중복 이벤트와 소비 실패 처리

**문제:** Kafka 전송 성공 후 Outbox 완료 기록에 실패하면 동일 이벤트가 재전송될 수 있다. 소비 단계에서도 재시도와 잘못된 JSON에 대응해야 한다.

**구현:** 통계 테이블의 `order_id` 고유키와 MySQL `ON DUPLICATE KEY UPDATE`를 사용해 같은 주문의 통계를 중복 저장하지 않는다. 로그 소비자와 통계 소비자는 Consumer Group을 분리한다. 처리 실패는 재시도 후 DLT에 보관하고 역직렬화 실패 메시지의 원문 바이트도 보존한다.

**검증:** 동일 주문 재처리 후 통계 건수 유지, 소비 실패 재시도·DLT, 잘못된 JSON 보존을 확인했다. 전송 자체를 exactly-once로 보장하는 방식이 아니라 중복 전달에 견디는 소비 처리를 구현했다.

소스: [통계 Repository](../../src/main/java/com/portfolio/admin_api/order/statistics/repository/OrderStatisticRepository.java), [Kafka 설계 문서](../kafka-outbox.md)

## 4. 인증과 서비스 권한 분리

**문제:** 외부 인증 성공만으로 서비스 관리자 권한을 부여하면 안 된다. 인스턴스를 교체하거나 여러 인스턴스를 사용해도 로그인 상태를 공유할 필요가 있다.

**구현:** Spring Security와 Keycloak OIDC를 연결하고 `issuer + subject`로 서비스 회원을 조회한다. 권한은 우리 DB에서 적용한다. 세션은 Spring Session Redis로 공유한다. CSRF 보호, API 401/403 응답, 자기 계정 삭제·권한 해제 방지를 적용했다.

**검증:** 미연결 SSO 계정 거부 테스트, AWS에서 일반 로그인·SSO 로그인·관리자 메뉴·SSO 로그아웃을 확인했다. 현재는 관리자 사전 등록 방식이다.

소스: [ExternalIdentityService](../../src/main/java/com/portfolio/admin_api/auth/service/ExternalIdentityService.java), [SecurityConfig](../../src/main/java/com/portfolio/admin_api/config/SecurityConfig.java)

## 5. Redis를 용도별로 사용

세션 공유 외에도 상품 조회 캐시와 Lua 기반 요청 제한을 적용했다. 상품 수정은 DB 변경과 캐시 무효화 요청을 Outbox에 함께 저장한다. 편집용 조회는 DB를 직접 읽는다. Redis 요청 제한은 여러 연결이 같은 카운터를 사용하며, 주문의 멱등성 키 구현과는 구분한다.

소스: [ProductService](../../src/main/java/com/portfolio/admin_api/product/service/ProductService.java), [RedisRateLimiter](../../src/main/java/com/portfolio/admin_api/common/ratelimit/RedisRateLimiter.java)

## 6. 배포 과정을 반복 가능한 작업으로 만들기

GitHub의 백엔드·프론트 저장소를 Jenkins가 checkout한다. 테스트 후 빌드 번호와 커밋으로 이미지 태그를 생성하고 SSH로 AWS에 전송한다. 서버는 배포 잠금으로 작업을 직렬화하고 대상 컨테이너만 교체한 뒤 healthcheck를 확인한다. 성공한 이미지 태그만 `.env`에 기록한다.

Jenkins는 개발 PC에서, 서비스는 AWS Lightsail에서 실행한다. Caddy가 HTTPS를 처리하고 공개 주소 하나로 React·API·SSO에 연결한다. Jenkins에서 두 작업의 배포 성공과 배포 후 로그인·목록 조회를 확인했다.

소스: [Jenkinsfile](../../Jenkinsfile), [서버 배포 스크립트](../../deploy/lightsail/apply-image.sh), [AWS Compose](../../deploy/lightsail/compose.yaml)
