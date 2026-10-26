# 시스템 구성과 실행 흐름

## AWS 서비스 구성

```mermaid
flowchart TB
  B[브라우저] -->|HTTPS 443| C
  subgraph AWS[AWS Lightsail · Ubuntu · Docker Compose]
    C[Caddy · TLS / Reverse Proxy]
    C -->|화면| F[React 정적 파일 · Nginx]
    C -->|API / 로그인| A[Spring Boot]
    C -->|SSO| K[Keycloak]
    A -->|주문 / 재고 / Outbox / 통계| M[(MySQL)]
    K -->|별도 keycloak DB| M
    A -->|세션 / 캐시 / 요청 제한| R[(Redis)]
    A -->|Outbox Relay 발행| Q[Kafka · order-created]
    Q -->|독립 Consumer Group| A
    A -->|OIDC 토큰 교환| K
  end
  G[GitHub · backend / frontend] --> J[Jenkins · 개발 PC]
  J -->|테스트 / Docker build| I[버전별 이미지]
  I -->|SSH 전송 / Compose 교체| AWS
```

AWS는 단일 Lightsail 서버의 Compose 구성이다. 공개 진입점은 Caddy의 80/443이며 백엔드·DB·Redis·Kafka는 내부 네트워크에서 통신한다. Keycloak의 관리 경로는 외부 Caddy에서 차단한다. HTTPS 인증서는 Caddy가 발급·갱신한다.

브라우저의 화면과 API는 같은 HTTPS 출처를 사용한다. Spring은 전달된 프록시 헤더로 원래 HTTPS 주소를 해석하며 세션 쿠키에는 Secure·HttpOnly를 적용한다.

## 주문 생성과 비동기 통계

```mermaid
sequenceDiagram
  participant U as 사용자
  participant A as OrderService
  participant D as MySQL
  participant R as Outbox Relay
  participant K as Kafka
  participant S as 통계 Consumer
  U->>A: 주문 요청
  A->>D: 주문 / 재고 차감 / Outbox 저장
  Note over A,D: 하나의 DB 트랜잭션으로 커밋 또는 롤백
  A-->>U: 주문 생성 결과
  R->>D: PENDING 선점, PROCESSING + 토큰 저장
  Note over R,D: 짧은 선점 트랜잭션 종료 후 전송
  R->>K: OrderCreatedEvent
  R->>D: 소유 토큰·유효기간 검사 후 PUBLISHED
  K->>S: 주문 이벤트 전달
  S->>D: order_id 고유키로 중복 통계 저장 방지
```

Kafka 전송과 MySQL 완료 기록은 하나의 원자적 커밋이 아니다. 전송 성공 후 완료 기록이 실패하면 재전송될 수 있으므로, 통계 저장에서 동일 주문을 다시 반영하지 않도록 설계했다.

## SSO와 서비스 권한

```mermaid
flowchart LR
  B[브라우저] -->|SSO 로그인| K[Keycloak 인증]
  K -->|Authorization Code| A[Spring Security OIDC]
  A -->|issuer + subject 조회| L[(user_external_identities)]
  L --> U[(users · 서비스 권한)]
  U --> S[관리자 / 일반 사용자 권한 적용]
  A --> R[(Redis 공유 세션)]
```

Keycloak 계정과 서비스 회원은 별개다. 사전 등록한 계정만 연결하며 이메일 일치만으로 자동 연결하지 않는다. 서비스 관리자 권한은 Keycloak 관리 콘솔 권한과 구분한다.

## 로컬 Kubernetes 실습

Docker Desktop Kubernetes에서 동일 백엔드 Deployment를 2개 Pod로 실행했다. Redis 세션 공유, Pod 재생성, readiness/liveness, 롤링 업데이트와 롤백을 확인했다. 이 구성은 로컬 실습이며 AWS EKS 배포나 여러 물리 서버 구성으로 표현하지 않는다.
