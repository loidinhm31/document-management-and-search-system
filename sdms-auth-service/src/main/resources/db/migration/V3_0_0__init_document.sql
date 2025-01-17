-- Create documents table
CREATE TABLE documents
(
    id                UUID PRIMARY KEY      DEFAULT uuid_generate_v4(),
    filename          VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_path         VARCHAR(500) NOT NULL,
    file_size         BIGINT       NOT NULL,
    mime_type         VARCHAR(100) NOT NULL,
    document_type     VARCHAR(50)  NOT NULL,
    indexed_content   TEXT,
    user_id           UUID         NOT NULL,
    created_by        VARCHAR(50)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(50)  NOT NULL,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           BOOLEAN               DEFAULT FALSE,

    CONSTRAINT fk_user
        FOREIGN KEY (user_id)
            REFERENCES users (user_id)
);

-- Create index on user_id for faster lookups
CREATE INDEX idx_documents_user_id ON documents (user_id);

-- Create index on created_at for sorting
CREATE INDEX idx_documents_created_at ON documents (created_at);

-- Create document_metadata table for additional metadata
CREATE TABLE document_metadata
(
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id    UUID         NOT NULL,
    metadata_key   VARCHAR(100) NOT NULL,
    metadata_value TEXT,

    CONSTRAINT fk_document
        FOREIGN KEY (document_id)
            REFERENCES documents (id)
            ON DELETE CASCADE
);

-- Create index on document_id
CREATE INDEX idx_document_metadata_document_id ON document_metadata (document_id);