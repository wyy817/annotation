CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_reports_one_active_per_case
    ON ai_report_drafts(analysis_run_id, case_id)
    WHERE status IN ('QUEUED', 'RUNNING');
