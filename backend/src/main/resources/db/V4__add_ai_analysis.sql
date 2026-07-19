-- Persist immutable CLAM result snapshots and normalized candidate regions.

CREATE TABLE IF NOT EXISTS ai_analysis_runs (
    id                         BIGSERIAL PRIMARY KEY,
    slide_id                   BIGINT NOT NULL REFERENCES slides(id) ON DELETE CASCADE,
    python_job_id              VARCHAR(64) NOT NULL UNIQUE,
    status                     VARCHAR(32) NOT NULL,
    stage                      VARCHAR(64),
    stage_progress             DOUBLE PRECISION NOT NULL DEFAULT 0,
    message                    TEXT,
    model_name                 VARCHAR(255),
    predicted_class            VARCHAR(64),
    tumor_evidence_score       DOUBLE PRECISION,
    evidence_band              VARCHAR(64),
    ensemble_disagreement      DOUBLE PRECISION,
    candidate_region_count     INTEGER,
    high_risk_region_count     INTEGER,
    candidate_area_proxy_mm2   DOUBLE PRECISION,
    result_payload             JSONB,
    artifacts                  JSONB,
    started_by                 BIGINT REFERENCES users(id) ON DELETE SET NULL,
    error_message              TEXT,
    started_at                 TIMESTAMP,
    completed_at               TIMESTAMP,
    created_at                 TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_candidate_regions (
    id                          BIGSERIAL PRIMARY KEY,
    analysis_run_id             BIGINT NOT NULL REFERENCES ai_analysis_runs(id) ON DELETE CASCADE,
    region_key                  VARCHAR(32) NOT NULL,
    evidence_tier               VARCHAR(32) NOT NULL,
    patch_count                 INTEGER NOT NULL,
    area_proxy_mm2              DOUBLE PRECISION,
    mean_attention_percentile   DOUBLE PRECISION,
    max_attention_percentile    DOUBLE PRECISION,
    mean_evidence_index         DOUBLE PRECISION,
    max_evidence_index          DOUBLE PRECISION,
    bbox_level0                 JSONB NOT NULL,
    centroid_level0             JSONB,
    representative_patch_path   VARCHAR(1024),
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (analysis_run_id, region_key)
);

CREATE INDEX IF NOT EXISTS idx_ai_runs_slide_created
    ON ai_analysis_runs(slide_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_runs_status
    ON ai_analysis_runs(status);
CREATE INDEX IF NOT EXISTS idx_ai_candidates_run
    ON ai_candidate_regions(analysis_run_id);
