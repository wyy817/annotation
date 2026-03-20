-- WSI Annotation Platform - Initial Schema
-- PostgreSQL 15+

CREATE TABLE IF NOT EXISTS workspaces (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    dir_path    VARCHAR(1024),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS slides (
    id             BIGSERIAL PRIMARY KEY,
    workspace_id   BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    file_name      VARCHAR(512) NOT NULL,
    original_path  VARCHAR(1024),
    tile_dir       VARCHAR(1024),
    thumbnail_path VARCHAR(1024),
    format         VARCHAR(16),                -- PNG | JPG | SVS
    file_size      BIGINT,
    width          INTEGER,
    height         INTEGER,
    tile_size      INTEGER DEFAULT 256,
    max_zoom       INTEGER,
    status         VARCHAR(32) DEFAULT 'PENDING',   -- PENDING | PROCESSING | READY | ERROR
    anno_status    VARCHAR(32) DEFAULT 'NOT_STARTED', -- NOT_STARTED | IN_PROGRESS | DONE
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS labels (
    id          BIGSERIAL PRIMARY KEY,
    slide_id    BIGINT NOT NULL REFERENCES slides(id) ON DELETE CASCADE,
    name        VARCHAR(128) NOT NULL,
    color       VARCHAR(16) NOT NULL,   -- e.g. #FF0000
    class_index INTEGER NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS annotations (
    id          BIGSERIAL PRIMARY KEY,
    slide_id    BIGINT NOT NULL REFERENCES slides(id) ON DELETE CASCADE,
    label_id    BIGINT REFERENCES labels(id) ON DELETE SET NULL,
    anno_type   VARCHAR(32) NOT NULL,   -- polygon | rect | brush
    coordinates JSONB NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS export_tasks (
    id          BIGSERIAL PRIMARY KEY,
    slide_id    BIGINT REFERENCES slides(id) ON DELETE SET NULL,
    status      VARCHAR(32) DEFAULT 'PENDING',  -- PENDING | PROCESSING | DONE | ERROR
    zip_path    VARCHAR(1024),
    total       INTEGER DEFAULT 0,
    ready       INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    skipped     INTEGER DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_slides_workspace_id  ON slides(workspace_id);
CREATE INDEX IF NOT EXISTS idx_slides_status        ON slides(status);
CREATE INDEX IF NOT EXISTS idx_labels_slide_id      ON labels(slide_id);
CREATE INDEX IF NOT EXISTS idx_annotations_slide_id ON annotations(slide_id);

-- V2: Add users table + review fields on slides

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20) NOT NULL DEFAULT 'ANNOTATOR', -- ANNOTATOR | REVIEWER
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Review fields for slides
ALTER TABLE slides
    ADD COLUMN IF NOT EXISTS annotator_id     BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS review_status    VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    ADD COLUMN IF NOT EXISTS reviewer_id      BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS reviewer_comment TEXT,
    ADD COLUMN IF NOT EXISTS reviewed_at      TIMESTAMP;

-- review_status values: NOT_SUBMITTED | PENDING_REVIEW | APPROVED | REJECTED

CREATE INDEX IF NOT EXISTS idx_slides_review_status ON slides(review_status);
CREATE INDEX IF NOT EXISTS idx_slides_annotator_id  ON slides(annotator_id);

-- V3: Add remark field for annotator notes
ALTER TABLE users ADD COLUMN IF NOT EXISTS remark TEXT;

-- V3 (phase 3): User status for admin approval workflow
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
-- status values: PENDING | ACTIVE | DISABLED
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- EXPORT FORMAT
ALTER TABLE export_tasks ADD COLUMN format VARCHAR(32) DEFAULT 'COCO';

-- V4: Annotation version history
CREATE TABLE IF NOT EXISTS annotation_versions (
    id                   BIGSERIAL PRIMARY KEY,
    slide_id             BIGINT NOT NULL REFERENCES slides(id) ON DELETE CASCADE,
    version_number       INT NOT NULL,
    created_by           BIGINT REFERENCES users(id),
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    trigger              VARCHAR(20) NOT NULL,   -- SUBMIT | RESUBMIT | REJECT | APPROVE
    annotations_snapshot JSONB NOT NULL,
    labels_snapshot      JSONB NOT NULL,
    reviewer_comment     TEXT
);

CREATE INDEX IF NOT EXISTS idx_annotation_versions_slide_id ON annotation_versions(slide_id);

-- V5: Audit logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT REFERENCES users(id) ON DELETE SET NULL,
    username   VARCHAR(64),
    action     VARCHAR(32) NOT NULL,   -- SAVE | SUBMIT | RESUBMIT | APPROVE | REJECT | IMPORT
    slide_id   BIGINT REFERENCES slides(id) ON DELETE SET NULL,
    detail     JSONB,
    ip_address VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_slide_id  ON audit_logs(slide_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id   ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action    ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);