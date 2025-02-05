-- Create a custom ID generation function
CREATE OR REPLACE FUNCTION generate_comment_id()
RETURNS bigint AS $$
DECLARE
    -- Use first 6 digits of epoch timestamp (in microseconds)
timestamp_part bigint;
    -- Use last 4 digits for sequence
    sequence_part bigint;
    -- Result will be a 10-digit number
    result bigint;
BEGIN
    -- Get current timestamp in microseconds (first 6 digits)
    timestamp_part := (EXTRACT(EPOCH FROM current_timestamp) * 1000000)::bigint % 1000000;

    -- Get sequence number (0-9999, will rollover)
    sequence_part := nextval('comment_id_seq') % 10000;

    -- Combine timestamp and sequence
    -- timestamp_part * 10000 shifts timestamp left 4 digits
    -- then add sequence_part to fill last 4 digits
    result := (timestamp_part * 10000 + sequence_part);

RETURN result;
END;
$$ LANGUAGE plpgsql;

-- Create sequence for the comment IDs
CREATE SEQUENCE comment_id_seq START WITH 1 INCREMENT BY 1;

-- Document comments table with improved ID generation
CREATE TABLE document_comments
(
    id          bigint                   DEFAULT generate_comment_id() PRIMARY KEY,
    document_id VARCHAR       NOT NULL,
    user_id     UUID          NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    parent_id   bigint,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    edited      BOOLEAN                  DEFAULT FALSE,
    deleted     BOOLEAN                  DEFAULT FALSE,
    version     BIGINT                   DEFAULT 0,
    -- Add foreign key reference to users table
    CONSTRAINT fk_comments_user
        FOREIGN KEY (user_id)
            REFERENCES users (user_id),
    -- Add self-referential foreign key for threaded replies
    CONSTRAINT fk_comments_parent
        FOREIGN KEY (parent_id)
            REFERENCES document_comments (id)
);

-- Create indexes for better query performance
CREATE INDEX idx_document_comments_document_id ON document_comments (document_id) WHERE deleted = false;
CREATE INDEX idx_document_comments_parent_id ON document_comments (parent_id) WHERE deleted = false;
CREATE INDEX idx_document_comments_user_id ON document_comments (user_id);
CREATE INDEX idx_document_comments_created_at ON document_comments (created_at);