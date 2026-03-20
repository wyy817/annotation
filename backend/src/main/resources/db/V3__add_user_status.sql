-- V3: Add status field to users table for admin approval workflow
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- status values: PENDING | ACTIVE | DISABLED

-- All existing users (seed accounts) remain ACTIVE
UPDATE users SET status = 'ACTIVE' WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
