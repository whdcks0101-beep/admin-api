# Kafka 주문 이벤트와 Transactional Outbox

[프로젝트 README로 돌아가기](../README.md)

## 1. 해결하려는 문제

주문 커밋 후 Kafka로 직접 전송하면, DB 커밋과 Kafka 전송 사이에 프로세스 종료나 전송 실패가 발생할 수 있습니다.
이 경우 주문은 저장됐지만 후속 처리에 필요한 이벤트가 전달되지 않을 수 있습니다.

현재는 주문·주문 항목·재고 변경과 Outbox를 같은 DB 트랜잭션으로 저장합니다.
별도의 Relay가 Outbox를 읽어 전송하며 주문 API는 Kafka 전송 완료를 기다리지 않습니다.
예전 AFTER_COMMIT 직접 전송 리스너는 Outbox 방식으로 대체했습니다.

## 2. 처리 흐름과 트랜잭션 경계

```text
주문 요청
  → [트랜잭션 1] 주문·재고·주문 항목·Outbox(PENDING) 저장 → 커밋

주기 작업
  → 만료된 PROCESSING 복구 → 복구 트랜잭션 커밋
  → [트랜잭션 2] 한 건 잠금 조회 → PROCESSING·토큰·만료 시각 저장 → 커밋
  → DB 트랜잭션 밖에서 Kafka 전송 및 성공 응답 확인
  → [트랜잭션 3] 유효한 토큰을 조건으로 완료 또는 재시도 예약 → 커밋
```

`OrderOutboxWriter`의 `MANDATORY`는 기존 주문 트랜잭션이 없는 호출을 거부합니다.
스케줄러와 Relay 전체에는 트랜잭션을 걸지 않아 Kafka 응답을 기다리는 동안 DB 트랜잭션을 유지하지 않습니다.

## 3. 주요 구성 요소

| 구성 요소 | 역할 |
| --- | --- |
| OrderOutboxWriter | 주문 이벤트를 JSON으로 변환하고 주문 트랜잭션 안에서 저장 |
| OutboxEvent | 이벤트 내용, 상태, 실패 횟수, 선점 정보 |
| OutboxClaimService | 한 건 잠금 조회 후 처리 권한 저장 |
| OutboxEventSenderRegistry | eventType에 맞는 Sender 선택, 중복 유형 등록 거부 |
| OrderOutboxSender | 주문 이벤트 복원, Producer 호출, 성공 응답 대기 |
| OutboxStatusService | 토큰·상태·만료 조건부 UPDATE |
| OutboxRecoveryService | 만료된 PROCESSING을 PENDING으로 복구 |
| OutboxRelay | 선점·전송·결과 저장을 순차 조합 |
| OutboxScheduler | 복구 후 Relay 실행, 종료 후 설정된 간격만큼 대기 |

현재 등록한 업무 이벤트는 `ORDER_CREATED`입니다. 확장 구조를 만들었지만 여러 업무 이벤트를 모두 구현한 것은 아닙니다.
새 Sender를 빈으로 등록하면 Registry가 수집합니다. 새 이벤트의 Kafka 직렬화 지원, 메시지 계약과 소비자 중복 처리도 함께 검토해야 합니다.

## 4. 상태와 데이터

| 상태 | 의미 |
| --- | --- |
| PENDING | 처음 전송하거나 재시도할 차례를 기다림 |
| PROCESSING | 특정 작업이 유효 기간이 있는 처리 권한을 확보함 |
| PUBLISHED | Kafka 성공 응답 확인 후 DB 완료 처리까지 커밋됨 |

- `id`: Outbox 레코드 식별자. 현재 Kafka payload의 별도 eventId로 보내지는 않음
- `aggregate_id`: 업무 대상 식별자. 현재 주문 번호를 BIGINT로 저장
- `payload`: 주문 번호와 주문 생성 시각을 담은 JSON
- `claim_token`: 선점할 때마다 생성하는 UUID. 고정된 서버 ID가 아님
- `locked_until`: 애플리케이션이 관리하는 권한 만료 시각. 그 시각까지 DB 잠금을 유지한다는 의미는 아님
- `attempt_count`: 실패 기록에 성공한 횟수. 전체 전송 횟수나 만료 복구 횟수가 아님
- `next_attempt_at`: 다음 선점이 가능한 시각. 정확히 그 시각에 실행된다는 보장은 없음

Outbox 생성·선점·재시도·완료·만료 판단은 DB 시각을 기준으로 합니다. DB 연결 간 세션 시간대도 동일하게 유지해야 합니다.
이벤트 payload의 주문 생성 시각은 업무 데이터이며 Outbox 기록 시각과 구분합니다.

## 5. 다중 작업자 선점

MySQL의 `SELECT ... FOR UPDATE SKIP LOCKED`로 다른 트랜잭션이 잠근 이벤트를 건너뜁니다.
잠금 조회와 PROCESSING 변경을 같은 짧은 트랜잭션에서 수행하고, 커밋 후 전송합니다.
서버별 균등 분배나 엄격한 업무 순서를 보장하는 방식은 아닙니다.

완료·실패 처리는 다음 조건을 한 번의 UPDATE에서 검사합니다.

```sql
WHERE id = :outboxId
  AND status = 'PROCESSING'
  AND claim_token = :claimToken
  AND locked_until > CURRENT_TIMESTAMP(6)
```

수정 1건이면 처리 성공, 0건이면 조건 불일치입니다. 0건과 DB 예외를 로그에서 구분합니다.
만료 복구는 오래된 토큰을 지우고 PENDING으로 되돌립니다. 재선점에서는 새 토큰을 발급하므로 이전 작업이 새 작업의 DB 상태를 덮어쓰지 못합니다.

## 6. 중복 전달 가능성과 소비자 멱등성

이 구조는 중복 없는 전송이나 업무 전체의 exactly-once 처리를 보장하지 않습니다.

```text
Kafka 전송 성공 → DB 완료 처리 전 프로세스 종료 → 만료 복구 → 재전송

전송 진행 중 권한 만료 → 다른 작업자가 재선점·전송 → 이전 전송도 Kafka 도착
```

토큰은 DB 상태 변경 권한을 검사할 뿐, 이미 시작한 Kafka 전송을 취소하지 않습니다.
Future 결과 대기 타임아웃도 메시지가 전달되지 않았다는 확정 증거가 아닙니다.

주문 통계는 `order_id` 기본키와 다음 MySQL 쿼리로 동일 주문의 중복 반영을 막습니다.

```sql
INSERT INTO order_statistics (order_id, ordered_at)
VALUES (:orderId, :orderedAt)
ON DUPLICATE KEY UPDATE order_id = order_id;
```

현재 주문 생성 통계에 해당하는 설계입니다. 다른 이벤트나 문자·결제 같은 외부 효과에는 별도의 멱등성 설계가 필요합니다.
통계 행이 한 건이라는 사실만으로 Kafka 전송도 한 번이었다고 판단하지 않습니다.

## 7. 생산 측 재시도와 소비 측 DLT

- Outbox: 생산 측 전송 결과 불확실성·장애에 대비해 미완료 이벤트를 재시도
- Consumer 오류 처리: 수신 후 업무 처리 실패를 재시도하고 복구 불가능하거나 재시도가 소진되면 DLT로 전달
- 현재 재시도 가능한 소비 오류는 1초 간격 2회 재시도. 최초 시도를 포함하면 3회
- 역직렬화 오류 등 재시도하지 않는 오류는 바로 DLT 처리될 수 있음
- DLT는 원래 토픽 이름에 `.DLT`를 붙이고 같은 파티션 번호 사용
- 현재 두 소비 그룹은 동일 DLT를 사용하므로 원본 소비 그룹 헤더로 실패 출처를 구분
- DLT 전송 실패를 성공한 복구로 간주하지 않도록 설정

Outbox의 생산 측 실패가 자동으로 Consumer DLT에 들어가는 것은 아닙니다. DLT 자동 재처리 기능도 현재 구현하지 않았습니다.

## 8. 설정과 처리량

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| enabled | 미지정 시 비활성 | Outbox 스케줄러 활성 여부 |
| fixed-delay-ms | 5000 | 전체 주기 작업 종료 후 다음 실행까지 대기 |
| initial-delay-ms | 10000 | 최초 실행 전 대기 |
| claim-duration-seconds | 120 | 한 이벤트 처리 권한 유지 시간 |
| retry-delay-seconds | 30 | 명시적 전송 실패 기록 후 재시도 간격 |
| max-events-per-run | 100 | 서버별 실행당 최대 처리 건수 |
| recovery-batch-size | 100 | 실행당 최대 만료 복구 건수 |

마지막 네 설정은 `OutboxProperties`에서 최소 1을 검증합니다. 양수 검증은 적절한 운영값을 보장하지 않습니다.
한 건씩 선점해 순차 전송하며, 실행당 건수 제한은 실행 시간 제한이 아닙니다.
Sender의 `.get(10초)`는 Future 대기 제한이며, `send()` 자체의 대기까지 포함한 전체 처리 시간 제한은 아닙니다.

## 9. 검증 결과

### 자동 테스트

| 테스트 | 확인한 내용 |
| --- | --- |
| OutboxClaimServiceIntegrationTest | 실제 MySQL 선점·커밋, 다른 트랜잭션 잠금 건너뛰기, 토큰·만료 권한 검사, 복구·재선점, 실패 재시도 예약 |
| OrderOutboxTransactionTest | H2에서 주문과 Outbox 동시 커밋·롤백, 주문 요청에서 Producer 직접 호출 없음 |
| OutboxPropertiesTest | 기본값, 설정 덮어쓰기, 잘못된 재시도 간격 거부 |
| OrderStatisticServiceIntegrationTest | MySQL 중복 통계 방지와 날짜 검색 |
| KafkaRetryDltTest | Embedded Kafka 소비 실패 재시도·DLT와 잘못된 JSON의 원문 보존 |

선점 통합 테스트는 기본값과 다른 권한 유지 180초·재시도 45초를 주입해 실제 DB 반영을 검사합니다.
동시성 테스트는 두 스레드의 별도 트랜잭션으로 재현하며 다중 호스트 배포나 부하 테스트는 아닙니다.

### 로컬 수동 검증

- Kafka 중단 중에도 주문과 Outbox가 저장됨
- Kafka 재시작 후 미전송 이벤트가 자동으로 PUBLISHED 처리됨
- 8080·8081 동시 실행 환경에서 주문 이벤트 최종 처리 확인
- 8080의 Outbox 스케줄러만 끈 상태에서 8080에 접수한 주문을 8081 Relay가 전송하는 것 확인

마지막 검증은 접수 인스턴스와 전송 인스턴스의 분리를 확인한 것입니다. 프로세스를 전송 도중 강제 종료한 실험까지 수행한 것은 아닙니다.
실행 명령과 환경변수는 [README 테스트 안내](../README.md#테스트)를 참고합니다.

