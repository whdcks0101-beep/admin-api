package com.portfolio.admin_api.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.portfolio.admin_api.outbox.entity.OutboxEvent;
import com.portfolio.admin_api.outbox.entity.OutboxStatus;
import com.portfolio.admin_api.outbox.repository.OutboxEventRepository;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "app.outbox.enabled=false",
        "app.outbox.claim-duration-seconds=180",
        "app.outbox.retry-delay-seconds=45"
})
@ActiveProfiles("test")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OutboxClaimServiceIntegrationTest {

	@Container
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("outbox_test");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
	}

	@Autowired
	private OutboxClaimService claimService;

	@Autowired
	private OutboxEventRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private OutboxStatusService statusService;

	@Autowired
	private OutboxRecoveryService recoveryService;

	@BeforeEach
	void clearTestRecords() {
		// 개발 DB가 아닌, 이 테스트의 임시 MySQL 데이터만 정리한다.
		repository.deleteAllInBatch();
	}

	@Test
	@DisplayName("대기 이벤트를 선점하면 처리 상태와 토큰이 DB에 저장된다")
	void claimsPendingEvent() {
		OutboxEvent saved = repository.save(OutboxEvent.create("ORDER_CREATED", 100L, """
				{"orderId":100,"createdAt":"2026-10-24T10:00:00"}
				""", repository.findDatabaseNow()));

		// DB 시각 기준으로 확실히 조회 가능한 테스트 데이터를 만든다.
		jdbcTemplate.update("""
				UPDATE outbox_events
				SET next_attempt_at =
				    TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))
				WHERE id = ?
				""", saved.getId());

		LocalDateTime beforeClaim = repository.findDatabaseNow();
		OutboxEvent claimed = claimService.claimNext().orElseThrow();
		LocalDateTime afterClaim = repository.findDatabaseNow();

		assertThat(claimed.getId()).isEqualTo(saved.getId());
		assertThat(claimed.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(claimed.getClaimToken()).isNotBlank();
		assertThat(claimed.getLockedUntil()).isBetween(beforeClaim.plusSeconds(180), afterClaim.plusSeconds(180));
		OutboxEvent reloaded = repository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getLockedUntil()).isEqualTo(claimed.getLockedUntil());

		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(reloaded.getClaimToken()).isEqualTo(claimed.getClaimToken());

		// 이미 PROCESSING인 이벤트는 다시 선점하지 않는다.
		assertThat(claimService.claimNext()).isEmpty();
	}

	@Test
	@DisplayName("다른 작업자가 잠근 이벤트는 건너뛰고 다음 이벤트를 선점한다")
	void skipsEventLockedByAnotherWorker() throws Exception {
		OutboxEvent first = repository.save(OutboxEvent.create("ORDER_CREATED", 101L, """
				{"orderId":101,"createdAt":"2026-10-24T10:00:00"}
				""", repository.findDatabaseNow()));

		OutboxEvent second = repository.save(OutboxEvent.create("ORDER_CREATED", 102L, """
				{"orderId":102,"createdAt":"2026-10-24T10:01:00"}
				""", repository.findDatabaseNow()));

		// 첫 번째 이벤트가 먼저 조회되도록 순서를 명확히 지정한다.
		jdbcTemplate.update("""
				UPDATE outbox_events
				SET next_attempt_at =
				    TIMESTAMPADD(MINUTE, -2, CURRENT_TIMESTAMP(6))
				WHERE id = ?
				""", first.getId());

		jdbcTemplate.update("""
				UPDATE outbox_events
				SET next_attempt_at =
				    TIMESTAMPADD(MINUTE, -1, CURRENT_TIMESTAMP(6))
				WHERE id = ?
				""", second.getId());

		CountDownLatch lockAcquired = new CountDownLatch(1);
		CountDownLatch releaseLock = new CountDownLatch(1);

		var executor = Executors.newSingleThreadExecutor();

		try {
			var workerA = executor.submit(() -> {
				TransactionTemplate transaction = new TransactionTemplate(transactionManager);

				transaction.executeWithoutResult(status -> {
					var events = repository.findPendingForClaim(1);

					assertThat(events).hasSize(1);
					assertThat(events.get(0).getId()).isEqualTo(first.getId());

					// A가 첫 번째 이벤트의 잠금을 잡았다고 알린다.
					lockAcquired.countDown();

					try {
						if (!releaseLock.await(20, TimeUnit.SECONDS)) {
							throw new IllegalStateException("잠금 해제 신호 대기 시간이 초과됐습니다.");
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(e);
					}

					// A는 잠금만 검증하고 변경 없이 종료한다.
					status.setRollbackOnly();
				});
			});

			assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();

			// B는 테스트 스레드에서 별도 트랜잭션으로 실행한다.
			OutboxEvent claimedByB = claimService.claimNext().orElseThrow();

			assertThat(claimedByB.getId()).isEqualTo(second.getId());
			assertThat(claimedByB.getStatus()).isEqualTo(OutboxStatus.PROCESSING);

			releaseLock.countDown();
			workerA.get(10, TimeUnit.SECONDS);

			// A의 잠금이 풀렸으므로 첫 번째 이벤트도 선점할 수 있다.
			OutboxEvent claimedAfterRelease = claimService.claimNext().orElseThrow();

			assertThat(claimedAfterRelease.getId()).isEqualTo(first.getId());
		} finally {
			releaseLock.countDown();
			executor.shutdownNow();

			if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("테스트 작업자가 종료되지 않았습니다.");
			}
		}
	}

	@Test
	@DisplayName("다른 토큰으로는 완료할 수 없고 선점한 토큰으로만 완료된다")
	void completesOnlyWithMatchingToken() {
		OutboxEvent saved = repository.save(OutboxEvent.create("ORDER_CREATED", 103L, """
				{"orderId":103,"createdAt":"2026-10-24T10:00:00"}
				""", repository.findDatabaseNow()));

		jdbcTemplate.update("""
				UPDATE outbox_events
				SET next_attempt_at =
				    TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))
				WHERE id = ?
				""", saved.getId());

		OutboxEvent claimed = claimService.claimNext().orElseThrow();

		// 이번 테스트는 토큰 검증에 집중한다.
		// 권한이 유효한 상태를 DB 시각 기준으로 명확하게 설정한다.
		jdbcTemplate.update("""
				UPDATE outbox_events
				SET locked_until =
				    TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP(6))
				WHERE id = ?
				""", claimed.getId());

		// 1. 다른 토큰으로는 완료할 수 없다.
		boolean wrongTokenResult = statusService.markPublished(claimed.getId(), "wrong-token");

		assertThat(wrongTokenResult).isFalse();

		OutboxEvent unchanged = repository.findById(claimed.getId()).orElseThrow();

		assertThat(unchanged.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(unchanged.getClaimToken()).isEqualTo(claimed.getClaimToken());
		assertThat(unchanged.getPublishedAt()).isNull();

		// 2. 실제 선점 토큰으로는 완료할 수 있다.
		boolean correctTokenResult = statusService.markPublished(claimed.getId(), claimed.getClaimToken());

		assertThat(correctTokenResult).isTrue();

		OutboxEvent published = repository.findById(claimed.getId()).orElseThrow();

		assertThat(published.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
		assertThat(published.getPublishedAt()).isNotNull();
		assertThat(published.getClaimToken()).isNull();
		assertThat(published.getLockedUntil()).isNull();

		// 3. 이미 완료된 이벤트를 같은 토큰으로 다시 수정하지 않는다.
		assertThat(statusService.markPublished(claimed.getId(), claimed.getClaimToken())).isFalse();
	}

	@Test
	@DisplayName("선점 토큰이 일치해도 처리 권한이 만료되면 완료할 수 없다")
	void rejectsCompletionWhenClaimExpired() {
		OutboxEvent saved = repository.save(OutboxEvent.create("ORDER_CREATED", 104L, """
				{"orderId":104,"createdAt":"2026-10-24T10:00:00"}
				""", repository.findDatabaseNow()));

		jdbcTemplate.update("""
				UPDATE outbox_events
				SET next_attempt_at =
				    TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))
				WHERE id = ?
				""", saved.getId());

		OutboxEvent claimed = claimService.claimNext().orElseThrow();

		// 실제 권한 유지 시간을 기다리지 않고 만료된 상태를 만든다.
		jdbcTemplate.update("""
				UPDATE outbox_events
				SET locked_until =
				    TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))
				WHERE id = ?
				""", claimed.getId());

		boolean result = statusService.markPublished(claimed.getId(), claimed.getClaimToken());

		assertThat(result).isFalse();

		OutboxEvent reloaded = repository.findById(claimed.getId()).orElseThrow();

		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(reloaded.getPublishedAt()).isNull();
		assertThat(reloaded.getClaimToken()).isEqualTo(claimed.getClaimToken());
	}

	@Test
	@DisplayName("만료된 이벤트를 재선점하면 이전 토큰은 거절되고 새 토큰만 유효하다")
	void rejectsOldTokenAfterRecoveryAndReclaim() {
		OutboxEvent saved = repository.save(OutboxEvent.create("ORDER_CREATED", 105L, """
				{"orderId":105,"createdAt":"2026-10-24T10:00:00"}
				""", repository.findDatabaseNow()));

		jdbcTemplate.update("""
				UPDATE outbox_events
				SET next_attempt_at =
				    TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))
				WHERE id = ?
				""", saved.getId());

		OutboxEvent firstClaim = claimService.claimNext().orElseThrow();
		String oldToken = firstClaim.getClaimToken();

		// 첫 번째 작업자의 권한을 만료시킨다.
		jdbcTemplate.update("""
				UPDATE outbox_events
				SET locked_until =
				    TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))
				WHERE id = ?
				""", saved.getId());

		assertThat(recoveryService.recoverExpiredClaims()).isEqualTo(1);

		OutboxEvent recovered = repository.findById(saved.getId()).orElseThrow();

		assertThat(recovered.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(recovered.getClaimToken()).isNull();
		assertThat(recovered.getLockedUntil()).isNull();

		// 같은 이벤트를 새로운 처리 권한으로 선점한다.
		OutboxEvent secondClaim = claimService.claimNext().orElseThrow();
		String newToken = secondClaim.getClaimToken();

		assertThat(secondClaim.getId()).isEqualTo(firstClaim.getId());
		assertThat(newToken).isNotBlank().isNotEqualTo(oldToken);

		jdbcTemplate.update("""
				UPDATE outbox_events
				SET locked_until =
				    TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP(6))
				WHERE id = ?
				""", saved.getId());

		// 이전 작업자는 성공 처리도, 실패 처리도 할 수 없다.
		assertThat(statusService.markPublished(saved.getId(), oldToken)).isFalse();
		assertThat(statusService.recordFailure(saved.getId(), oldToken)).isFalse();

		OutboxEvent unchanged = repository.findById(saved.getId()).orElseThrow();

		assertThat(unchanged.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
		assertThat(unchanged.getClaimToken()).isEqualTo(newToken);
		assertThat(unchanged.getAttemptCount()).isZero();

		// 새 처리 권한을 가진 작업자만 완료할 수 있다.
		assertThat(statusService.markPublished(saved.getId(), newToken)).isTrue();

		assertThat(repository.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
	}

	@Test
	@DisplayName("유효한 토큰으로 실패 처리하면 설정된 45초 뒤 재시도로 예약된다")
	void schedulesRetryAfterFailure() {
		OutboxEvent saved = repository.save(OutboxEvent.create("ORDER_CREATED", 106L, """
				{"orderId":106,"createdAt":"2026-10-24T10:00:00"}
				""", repository.findDatabaseNow()));

		OutboxEvent claimed = claimService.claimNext().orElseThrow();

		LocalDateTime beforeFailure = repository.findDatabaseNow();

		boolean result = statusService.recordFailure(claimed.getId(), claimed.getClaimToken());

		LocalDateTime afterFailure = repository.findDatabaseNow();

		assertThat(result).isTrue();

		OutboxEvent reloaded = repository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(reloaded.getAttemptCount()).isEqualTo(1);
		assertThat(reloaded.getClaimToken()).isNull();
		assertThat(reloaded.getLockedUntil()).isNull();
		assertThat(reloaded.getPublishedAt()).isNull();

		assertThat(reloaded.getNextAttemptAt()).isBetween(beforeFailure.plusSeconds(45), afterFailure.plusSeconds(45));

		// 실패 처리가 끝나면 이전 토큰으로 다시 실패를 기록할 수 없다.
		assertThat(statusService.recordFailure(claimed.getId(), claimed.getClaimToken())).isFalse();

		assertThat(repository.findById(saved.getId()).orElseThrow().getAttemptCount()).isEqualTo(1);
	}

}
