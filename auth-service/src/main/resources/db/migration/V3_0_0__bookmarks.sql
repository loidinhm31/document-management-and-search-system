-- Document bookmarks table to handle many-to-many relationship
CREATE TABLE document_bookmarks
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID    NOT NULL,
    document_id VARCHAR NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- Composite unique constraint to prevent duplicate bookmarks
    UNIQUE (user_id, document_id)
);

-- Add indexes for fast lookups
CREATE INDEX idx_bookmarks_user_id ON document_bookmarks (user_id);
CREATE INDEX idx_bookmarks_document_id ON document_bookmarks (document_id);