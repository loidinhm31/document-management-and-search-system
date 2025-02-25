-- Create document_recommendations table
CREATE TABLE document_recommendations (
                                          id BIGSERIAL PRIMARY KEY,
                                          document_id VARCHAR(255) NOT NULL,
                                          mentor_id UUID NOT NULL,
                                          created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
                                          CONSTRAINT document_mentor_unique UNIQUE (document_id, mentor_id)
);

-- Add index on document_id for faster counting
CREATE INDEX idx_document_recommendations_document_id ON document_recommendations(document_id);

-- Add index on mentor_id for faster lookup of recommendations by mentor
CREATE INDEX idx_document_recommendations_mentor_id ON document_recommendations(mentor_id);

-- Comment on table
COMMENT ON TABLE document_recommendations IS 'Stores document recommendations by mentors';