CREATE TABLE document_reports
(
    id               BIGSERIAL PRIMARY KEY,
    document_id      VARCHAR(255)             NOT NULL,
    user_id          UUID                     NOT NULL,
    report_type_code VARCHAR(50)              NOT NULL,
    description      VARCHAR(1000),
    resolved         BOOLEAN DEFAULT FALSE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    status           VARCHAR(50)              NOT NULL,
    resolved_by      VARCHAR(100),
    resolved_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_document_reports_user UNIQUE (document_id, user_id)
);

-- Index for faster lookups
CREATE INDEX idx_document_reports_document_id ON document_reports (document_id);
CREATE INDEX idx_document_reports_user_id ON document_reports (user_id);