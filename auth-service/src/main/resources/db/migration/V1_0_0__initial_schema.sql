CREATE SCHEMA IF NOT EXISTS dms;

SET search_path TO dms;

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA dms;

-- Create roles table
CREATE TABLE roles
(
    role_id    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_name  VARCHAR(20) NOT NULL UNIQUE,
    created_by VARCHAR(50) NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    updated_by VARCHAR(50) NOT NULL,
    updated_at TIMESTAMP   NOT NULL
);

-- Create users table
CREATE TABLE users
(
    user_id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username                VARCHAR(50)  NOT NULL UNIQUE,
    email                   VARCHAR(100) NOT NULL UNIQUE,
    password                VARCHAR(255),
    account_non_locked      BOOLEAN          DEFAULT true,
    account_non_expired     BOOLEAN          DEFAULT true,
    credentials_non_expired BOOLEAN          DEFAULT true,
    enabled                 BOOLEAN          DEFAULT true,
    credentials_expiry_date DATE,
    account_expiry_date     DATE,
    two_factor_secret       VARCHAR(255),
    is_two_factor_enabled   BOOLEAN          DEFAULT false,
    sign_up_method          VARCHAR(50),
    role_id                 UUID REFERENCES roles (role_id),
    created_by              VARCHAR(50)  NOT NULL,
    created_at              TIMESTAMP    NOT NULL,
    updated_by              VARCHAR(50)  NOT NULL,
    updated_at              TIMESTAMP    NOT NULL,
    CONSTRAINT users_role_fk FOREIGN KEY (role_id) REFERENCES roles (role_id)
);

-- Create audit_logs table
CREATE TABLE audit_logs
(
    id         UUID PRIMARY KEY      DEFAULT uuid_generate_v4(),
    user_id    UUID REFERENCES users (user_id),
    username   VARCHAR(50)  NOT NULL,
    action     VARCHAR(100) NOT NULL,
    details    TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    status     VARCHAR(20),
    timestamp  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create password_reset_token table
CREATE TABLE password_reset_token
(
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    token       VARCHAR(255) NOT NULL UNIQUE,
    user_id     UUID         NOT NULL,
    expiry_date TIMESTAMP    NOT NULL,
    used        BOOLEAN          DEFAULT false,
    created_by  VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_by  VARCHAR(50)  NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT password_reset_token_user_fk FOREIGN KEY (user_id) REFERENCES users (user_id)
);

-- Create indexes
CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_audit_logs_username ON audit_logs (username);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs (timestamp);
CREATE INDEX idx_password_reset_token ON password_reset_token (token);

-- Create refresh_tokens table
CREATE TABLE refresh_tokens
(
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    token       VARCHAR(255) NOT NULL UNIQUE,
    user_id     UUID         NOT NULL,
    expiry_date TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    user_agent  VARCHAR(255),
    ip_address  VARCHAR(45),
    created_by  VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_by  VARCHAR(50)  NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT refresh_tokens_user_fk FOREIGN KEY (user_id) REFERENCES users (user_id)
);

-- Create index for token lookup
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens (token);

-- Create index for user lookup
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);