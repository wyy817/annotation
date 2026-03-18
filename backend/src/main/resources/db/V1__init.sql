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
