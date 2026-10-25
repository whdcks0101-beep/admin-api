CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (id),

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id),

    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products (id),

    CONSTRAINT uk_order_items_order_product
        UNIQUE (order_id, product_id),

    CONSTRAINT ck_order_items_quantity
        CHECK (quantity > 0)
);