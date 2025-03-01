-- Create document_notes table
CREATE TABLE document_notes
(
    id          BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(255)             NOT NULL,
    mentor_id   UUID                     NOT NULL,
    content     VARCHAR(200)             NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    edited      BOOLEAN                  NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_document_mentor UNIQUE (document_id, mentor_id)
);

-- Add indexes to improve query performance
CREATE INDEX idx_document_notes_document_id ON document_notes (document_id);
CREATE INDEX idx_document_notes_mentor_id ON document_notes (mentor_id);
