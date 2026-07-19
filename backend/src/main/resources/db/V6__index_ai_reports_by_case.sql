CREATE INDEX IF NOT EXISTS idx_ai_reports_analysis_case_created
    ON ai_report_drafts(analysis_run_id, case_id, created_at DESC);
