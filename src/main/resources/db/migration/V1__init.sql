CREATE TABLE categories (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(100) NOT NULL UNIQUE,
    description    TEXT,
    extract_fields TEXT,
    active         BOOLEAN NOT NULL DEFAULT TRUE
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE email_messages (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    gmail_id          VARCHAR(64)  NOT NULL UNIQUE,
    thread_id         VARCHAR(64),
    message_id_header VARCHAR(255),
    from_address      VARCHAR(320) NOT NULL,
    subject           VARCHAR(500) NOT NULL,
    body              MEDIUMTEXT   NOT NULL,
    received_at       DATETIME(6)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    category          VARCHAR(100),
    INDEX idx_email_status (status)
) DEFAULT CHARSET = utf8mb4;

CREATE TABLE ai_results (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    email_id       BIGINT      NOT NULL,
    provider       VARCHAR(20) NOT NULL,
    category       VARCHAR(100),
    confidence     DOUBLE,
    extracted_json MEDIUMTEXT,
    reply_text     MEDIUMTEXT,
    needs_human    BOOLEAN     NOT NULL DEFAULT FALSE,
    raw_response   MEDIUMTEXT,
    input_tokens   INT,
    output_tokens  INT,
    latency_ms     BIGINT,
    error          TEXT,
    selected       BOOLEAN     NOT NULL DEFAULT FALSE,
    reply_sent     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6) NOT NULL,
    CONSTRAINT fk_result_email FOREIGN KEY (email_id) REFERENCES email_messages (id) ON DELETE CASCADE,
    INDEX idx_result_email (email_id)
) DEFAULT CHARSET = utf8mb4;

-- Példa kategória, cseréld a sajátjaidra (vagy töltsd fel a /api/db/categories végponton)
INSERT INTO categories (name, description, extract_fields)
VALUES ('OTHER', 'Minden, ami nem illik más kategóriába', '');
