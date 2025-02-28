CREATE TABLE document_reports
(
    id               BIGSERIAL PRIMARY KEY,
    document_id      VARCHAR(255)             NOT NULL,
    user_id          UUID                     NOT NULL,
    report_type_code VARCHAR(50)              NOT NULL,
    description      VARCHAR(1000),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    status           VARCHAR(50)              NOT NULL,
    processed        BOOLEAN DEFAULT FALSE,
    updated_by       UUID,
    updated_at       TIMESTAMP WITH TIME ZONE
);

-- Index for faster lookups
CREATE INDEX idx_document_reports_document_id ON document_reports (document_id);
CREATE INDEX idx_document_reports_user_id ON document_reports (user_id);