CREATE TABLE document_comments
(
    id          BIGINT PRIMARY KEY,
    document_id VARCHAR                  NOT NULL,
    user_id     UUID                     NOT NULL,
    content     VARCHAR(200)             NOT NULL,
    parent_id   BIGINT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    edited      BOOLEAN  DEFAULT FALSE,
    flag        SMALLINT DEFAULT 1,
    FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES document_comments (id)
);

CREATE INDEX idx_document_comments_user_id ON document_comments (user_id);
CREATE INDEX idx_document_comments_created_at ON document_comments (created_at);