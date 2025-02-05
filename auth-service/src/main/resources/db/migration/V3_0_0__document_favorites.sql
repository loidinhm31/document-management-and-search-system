-- Document favorites table to handle many-to-many relationship
CREATE TABLE document_favorites
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID    NOT NULL,
    document_id VARCHAR NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    -- Composite unique constraint to prevent duplicate favorites
    UNIQUE (user_id, document_id)
);

-- Add indexes for fast lookups
CREATE INDEX idx_favorites_user_id ON document_favorites (user_id);
CREATE INDEX idx_favorites_document_id ON document_favorites (document_id);