CREATE TABLE document_comments
(
    id          bigint PRIMARY KEY,
    document_id VARCHAR       NOT NULL,
    user_id     UUID          NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    parent_id   bigint,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    edited      BOOLEAN                  DEFAULT FALSE,
    deleted     BOOLEAN                  DEFAULT FALSE,
    version     BIGINT                   DEFAULT 0,
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES document_comments (id)
);

-- Keep the indexes
CREATE INDEX idx_document_comments_document_id ON document_comments (document_id) WHERE deleted = false;
CREATE INDEX idx_document_comments_parent_id ON document_comments (parent_id) WHERE deleted = false;
CREATE INDEX idx_document_comments_user_id ON document_comments (user_id);
CREATE INDEX idx_document_comments_created_at ON document_comments (created_at);