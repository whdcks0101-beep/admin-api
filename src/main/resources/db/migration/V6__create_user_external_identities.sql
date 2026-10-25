CREATE TABLE user_external_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,

    issuer VARCHAR(255)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,

    subject VARCHAR(255)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_external_identity_issuer_subject
        UNIQUE (issuer, subject),

    INDEX idx_external_identity_user_id (user_id),

    CONSTRAINT fk_external_identity_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);