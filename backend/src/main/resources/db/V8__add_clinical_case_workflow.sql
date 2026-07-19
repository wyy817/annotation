-- Persist hospital case intake, slide binding, decisions, review routing, and audit history.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(128);

ALTER TABLE ai_analysis_runs
    ADD COLUMN IF NOT EXISTS case_reference VARCHAR(32);

ALTER TABLE ai_report_drafts
    ADD COLUMN IF NOT EXISTS senior_decision VARCHAR(32),
    ADD COLUMN IF NOT EXISTS senior_note TEXT,
    ADD COLUMN IF NOT EXISTS stale BOOLEAN NOT NULL DEFAULT FALSE;

CREATE SEQUENCE IF NOT EXISTS clinical_case_reference_seq START WITH 100;

CREATE TABLE IF NOT EXISTS clinical_cases (
    id                       BIGSERIAL PRIMARY KEY,
    case_reference           VARCHAR(32) NOT NULL UNIQUE,
    accession_reference      VARCHAR(128) NOT NULL,
    disease_profile_id       VARCHAR(64) NOT NULL,
    specimen_name            VARCHAR(128) NOT NULL,
    specimen_site_key        VARCHAR(128) NOT NULL,
    stain                    VARCHAR(32) NOT NULL,
    patient_age              SMALLINT NOT NULL CHECK (patient_age BETWEEN 0 AND 120),
    received_at              TIMESTAMP NOT NULL,
    due_at                   TIMESTAMP NOT NULL,
    assigned_pathologist_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status                   VARCHAR(32) NOT NULL DEFAULT 'READY',
    review_required          BOOLEAN NOT NULL DEFAULT FALSE,
    qc_sample                BOOLEAN NOT NULL DEFAULT FALSE,
    feedback_status          VARCHAR(32) NOT NULL DEFAULT 'NOT_ELIGIBLE',
    feedback_version         INTEGER NOT NULL DEFAULT 0,
    created_by               BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (due_at > received_at)
);

CREATE TABLE IF NOT EXISTS clinical_case_slides (
    case_id       BIGINT NOT NULL REFERENCES clinical_cases(id) ON DELETE CASCADE,
    slide_id      BIGINT NOT NULL REFERENCES slides(id) ON DELETE RESTRICT,
    is_primary    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (case_id, slide_id),
    UNIQUE (slide_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_clinical_case_primary_slide
    ON clinical_case_slides(case_id) WHERE is_primary;

CREATE TABLE IF NOT EXISTS clinical_case_review_triggers (
    case_id       BIGINT NOT NULL REFERENCES clinical_cases(id) ON DELETE CASCADE,
    trigger_code  VARCHAR(64) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (case_id, trigger_code)
);

CREATE TABLE IF NOT EXISTS clinical_case_decisions (
    id             BIGSERIAL PRIMARY KEY,
    case_id        BIGINT NOT NULL REFERENCES clinical_cases(id) ON DELETE CASCADE,
    decision_stage VARCHAR(32) NOT NULL,
    decision_type  VARCHAR(32) NOT NULL,
    note           TEXT,
    actor_id       BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_name     VARCHAR(128) NOT NULL,
    actor_role     VARCHAR(32) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS clinical_case_audit_events (
    id          BIGSERIAL PRIMARY KEY,
    case_id     BIGINT NOT NULL REFERENCES clinical_cases(id) ON DELETE CASCADE,
    event_type  VARCHAR(64) NOT NULL,
    actor_id    BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_name  VARCHAR(128) NOT NULL,
    detail_key  VARCHAR(128) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_clinical_cases_assignee_status
    ON clinical_cases(assigned_pathologist_id, status);
CREATE INDEX IF NOT EXISTS idx_clinical_cases_status_due
    ON clinical_cases(status, due_at);
CREATE INDEX IF NOT EXISTS idx_clinical_case_decisions_case_created
    ON clinical_case_decisions(case_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_clinical_case_audit_case_created
    ON clinical_case_audit_events(case_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_runs_case_created
    ON ai_analysis_runs(case_reference, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_reports_case_stale
    ON ai_report_drafts(case_id, stale, created_at DESC);
