-- Persist auditable report-generation requests and validated structured drafts.

CREATE TABLE IF NOT EXISTS ai_report_drafts (
    id                  BIGSERIAL PRIMARY KEY,
    analysis_run_id     BIGINT NOT NULL REFERENCES ai_analysis_runs(id) ON DELETE CASCADE,
    case_id             VARCHAR(128) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    language            VARCHAR(16) NOT NULL,
    clinician_decision  VARCHAR(32) NOT NULL,
    clinician_note      TEXT,
    request_payload     JSONB,
    response_payload    JSONB,
    report_payload      JSONB,
    generator_source    VARCHAR(64),
    model_name          VARCHAR(255),
    validation_errors   JSONB,
    error_message       TEXT,
    requested_by        BIGINT REFERENCES users(id) ON DELETE SET NULL,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_reports_analysis_created
    ON ai_report_drafts(analysis_run_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_reports_status
    ON ai_report_drafts(status);
