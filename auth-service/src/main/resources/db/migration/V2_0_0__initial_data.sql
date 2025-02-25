-- Insert default roles
INSERT INTO roles (role_name, created_by, created_at, updated_by, updated_at)
VALUES ('ROLE_USER', 'system', current_timestamp, 'system', current_timestamp),
       ('ROLE_MENTOR','system', current_timestamp, 'system', current_timestamp),
       ('ROLE_ADMIN','system', current_timestamp, 'system', current_timestamp) ON CONFLICT (role_name) DO NOTHING;
