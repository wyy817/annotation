-- Give the two composite-key child tables a surrogate BIGSERIAL primary key so
-- MyBatis-Plus can inject its standard id-based CRUD. This removes the startup
-- warnings ("Can not find table primary key ... Not found @TableId annotation")
-- without introducing a meaningless single-column identity: the natural composite
-- keys are preserved as UNIQUE constraints, so uniqueness guarantees are unchanged.
--
-- PostgreSQL DDL is transactional and Flyway runs each migration in one transaction,
-- so a failure rolls the whole file back rather than leaving a half-migrated schema.

-- clinical_case_slides: (case_id, slide_id) -> surrogate id + unique natural key.
ALTER TABLE clinical_case_slides DROP CONSTRAINT IF EXISTS clinical_case_slides_pkey;
ALTER TABLE clinical_case_slides ADD COLUMN IF NOT EXISTS id BIGSERIAL;
ALTER TABLE clinical_case_slides ADD PRIMARY KEY (id);
ALTER TABLE clinical_case_slides
    ADD CONSTRAINT uq_clinical_case_slide_natural UNIQUE (case_id, slide_id);

-- clinical_case_review_triggers: (case_id, trigger_code) -> surrogate id + unique natural key.
ALTER TABLE clinical_case_review_triggers DROP CONSTRAINT IF EXISTS clinical_case_review_triggers_pkey;
ALTER TABLE clinical_case_review_triggers ADD COLUMN IF NOT EXISTS id BIGSERIAL;
ALTER TABLE clinical_case_review_triggers ADD PRIMARY KEY (id);
ALTER TABLE clinical_case_review_triggers
    ADD CONSTRAINT uq_clinical_case_review_trigger_natural UNIQUE (case_id, trigger_code);
