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
