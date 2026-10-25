CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sku VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    safety_stock INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_safety_stock CHECK (safety_stock >= 0)
);

CREATE TABLE inventories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventories_product_id UNIQUE (product_id),
    CONSTRAINT fk_inventories_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_inventories_quantity CHECK (quantity >= 0)
);
