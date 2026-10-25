CREATE TABLE order_statistics (
    order_id BIGINT NOT NULL,
    ordered_at DATETIME(6) NOT NULL,

    PRIMARY KEY (order_id),
    INDEX idx_order_statistics_ordered_at (ordered_at)
);