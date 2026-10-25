package com.portfolio.admin_api.outbox.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.portfolio.admin_api.outbox.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
	
	@Query(value = "SELECT CURRENT_TIMESTAMP(6)", nativeQuery = true)
	LocalDateTime findDatabaseNow();

	@Query(value = """
			SELECT *
			FROM outbox_events
			WHERE status = 'PENDING'
			  AND next_attempt_at <= CURRENT_TIMESTAMP(6)
			ORDER BY next_attempt_at ASC, id ASC
			LIMIT :batchSize
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OutboxEvent> findPendingForClaim(@Param("batchSize") int batchSize);

	@Modifying
	@Query(value = """
			UPDATE outbox_events
			SET status = 'PUBLISHED',
			    published_at = CURRENT_TIMESTAMP(6),
			    claim_token = NULL,
			    locked_until = NULL
			WHERE id = :outboxId
			  AND status = 'PROCESSING'
			  AND claim_token = :claimToken
			  AND locked_until > CURRENT_TIMESTAMP(6)
			""", nativeQuery = true)
	int markPublishedIfOwned(@Param("outboxId") Long outboxId, @Param("claimToken") String claimToken);

	@Modifying
	@Query(value = """
			UPDATE outbox_events
			SET status = 'PENDING',
			    attempt_count = attempt_count + 1,
			    next_attempt_at = TIMESTAMPADD(
			        SECOND, :retryDelaySeconds, CURRENT_TIMESTAMP(6)
			    ),
			    claim_token = NULL,
			    locked_until = NULL
			WHERE id = :outboxId
			  AND status = 'PROCESSING'
			  AND claim_token = :claimToken
			  AND locked_until > CURRENT_TIMESTAMP(6)
			""", nativeQuery = true)
	int rescheduleIfOwned(@Param("outboxId") Long outboxId, @Param("claimToken") String claimToken,
			@Param("retryDelaySeconds") int retryDelaySeconds);

	@Modifying
	@Query(value = """
			UPDATE outbox_events
			SET status = 'PENDING',
			    next_attempt_at = CURRENT_TIMESTAMP(6),
			    claim_token = NULL,
			    locked_until = NULL
			WHERE status = 'PROCESSING'
			  AND locked_until <= CURRENT_TIMESTAMP(6)
			ORDER BY locked_until ASC, id ASC
			LIMIT :batchSize
			""", nativeQuery = true)
	int recoverExpiredClaims(@Param("batchSize") int batchSize);

}
