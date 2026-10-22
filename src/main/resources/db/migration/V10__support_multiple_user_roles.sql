
-- V10: Support multiple roles per user

-- 1. Create user_roles table
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_user_roles
        PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id)
        REFERENCES roles(role_id)
);

-- 2. Preserve existing user roles
INSERT INTO user_roles (user_id, role_id)
SELECT user_id, role_id
FROM users;

-- 3. Remove old single-role column
ALTER TABLE users DROP COLUMN role_id;

-- 4. Index for role-based queries
CREATE INDEX idx_user_roles_role_id
ON user_roles(role_id);
