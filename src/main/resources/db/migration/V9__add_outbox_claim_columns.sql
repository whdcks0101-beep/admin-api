ALTER TABLE outbox_events
    ADD COLUMN claim_token VARCHAR(36) NULL,
    ADD COLUMN locked_until DATETIME(6) NULL;

CREATE INDEX idx_outbox_processing_expiry
    ON outbox_events (status, locked_until, id);