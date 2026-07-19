# Pathorchestra Agent Handoff

Last updated: 2026-07-19

## Objective

Continue the seven-day product-grade prototype for Pathorchestra, initially
focused on breast cancer lymph-node H&E whole-slide images.

The local product must support this browser workflow:

1. Upload a WSI.
2. Create a clinical case and bind one Primary H&E WSI.
3. Assign a pathologist.
4. Manually run CLAM analysis.
5. Review the heatmap, candidate regions, and quantitative evidence.
6. Record a pathologist decision.
7. Route conditional cases to a senior pathologist.
8. Generate an AI-assisted report after the final clinical decision.
9. Persist the completed result in the feedback pool.

The public demo remains static and uses precomputed evidence plus
`localStorage`. It must not expose registration, upload, live inference,
credentials, or sensitive data.

## Confirmed Product Decisions

- Default language is English with Chinese switching.
- Visual character: precise, restrained, trustworthy, and flat by default.
- Roles:
  - `ANNOTATOR`: pathologist.
  - `REVIEWER`: senior pathologist.
  - `AI_DATA_ADMIN`: creates cases, binds slides, assigns pathologists, and
    validates feedback.
  - `SYSTEM_ADMIN`: system and user administration plus case intake, but no
    clinical decisions.
- The first disease profile is fixed to breast cancer lymph-node H&E.
- The first UI requires exactly one Primary H&E WSI, while the database allows
  future multi-slide expansion.
- CLAM is started manually by the assigned pathologist.
- Primary WSI binding becomes immutable once analysis starts.
- Patient age at collection is required as an integer from 0 through 120. Date
  of birth is not stored.
- Direct final decisions generate a report immediately.
- Conditional cases generate a report only after senior approval.
- Senior review triggers:
  - Explicit pathologist escalation.
  - Ensemble disagreement at or above `0.15`.
  - Tumor evidence score from `0.35` through `0.65`.
  - Reject/image-text discordance.
  - Stable 10% QC sampling.
- A modify decision alone does not trigger senior review.
- Public hosting is planned as a static GitHub Pages frontend. The private
  repository remains the source of truth.

## Implemented

### Persisted clinical workflow

Flyway migration `V8__add_clinical_case_workflow.sql` adds:

- Case records and generated case references.
- Primary slide binding.
- Review triggers.
- Pathologist and senior decisions.
- Audit events.
- Feedback status and versions.
- Case linkage for CLAM runs.
- Senior-review and stale-state fields for reports.
- User display names.

Spring endpoints are under `/api/cases` and cover:

- Case list and detail.
- Case creation options and creation.
- Start/read latest case-level CLAM analysis.
- Start clinical review.
- Record pathologist and senior decisions.
- Generate a report.
- Validate a feedback record.

### Frontend workflow

- The case store uses Spring/PostgreSQL in local mode and preserves the old
  precomputed/localStorage behavior in demo mode.
- AI/data and system administrators can create cases from:
  - The case dashboard.
  - A READY WSI action menu.
  - The completion state after uploading one WSI.
- The case creation drawer collects accession/reference, patient age, specimen
  name, Primary WSI, assigned pathologist, received time, and due time.
- Ordinary JPG/PNG annotation images are excluded from Primary WSI options.
- The pathologist cannot start review until CLAM has succeeded.
- A completed backend decision refreshes the persisted report instead of
  creating a browser-only report.
- Feedback validation uses the backend in local mode.
- English and Chinese workflow copy has been added.

### Local development accounts

| Role | Username | Password |
| --- | --- | --- |
| Pathologist | `annotator` | `anno123` |
| Senior pathologist | `reviewer` | `review123` |
| AI / data administrator | `aiadmin` | `data123` |
| System administrator | `sysadmin` | `system123` |

These are local seeded accounts only. Administrator self-registration remains
disabled.

## Verified State

- Flyway V8 applied successfully to PostgreSQL database `wsi_platform`.
- Backend test suite: 18 tests passed with `mvn test`.
- Frontend production build passed with `npm run build`.
- The local services were left running at:
  - Product: `http://127.0.0.1:5173`
  - Spring API: `http://127.0.0.1:8080`
  - Python API docs: `http://127.0.0.1:8000/docs`
- Real case-level inference was completed for `PO-26-0717-001`, bound to
  `tumor_001.tif`:
  - Analysis ID: `4`.
  - Tumor evidence score: `0.970272`.
  - Ensemble disagreement: `0.020626`.
  - Five candidate regions were produced.
  - Model overview, relative-attention overview, and three evidence patches
    were persisted and served through protected endpoints.
- The pathologist accepted this case. It is currently `completed` with feedback
  status `queued`.
- Report ID `7` completed successfully using generator source `LIVE_LLM` and
  model `gpt-5.6-sol-2026-07-09`.
- A targeted headless-browser check confirmed the real heatmap is visible after
  fixing the local case-page watcher loop.
- `PO-26-0717-006`, bound to `normal_001.tif`, remains ready and has not yet run
  the new case-level workflow.

## 2026-07-19 Session Progress

### Browser regression is green

`npm run check:visual` now passes all seven flows. Three harness issues in
`frontend/scripts/visual-check.mjs` were fixed (the product code was already
correct — only the checker was stale or fragile):

- The local WSI flow hard-asserted exactly two thumbnails in workspace 5, but an
  uploaded JPG (slide 6) legitimately makes three. Relaxed the count to `>= 2`
  while still requiring every thumbnail to load.
- The `local-positive-desktop` assertion was inverted: it flagged the completed
  case's persisted `LIVE_LLM` report ("Validated live model response") as a
  premature-report failure. Replaced it with a mode-aware `requiredText` — the
  completed local case must render that live-report label, while demo/precomputed
  cases assert the annotation-overlap "Public annotation compatibility" section
  (which only exists when `analysis.validation.available` is true, so it never
  appears on a real uploaded-slide analysis).
- The suite aborted on the first timeout, hiding the other flows' results. Each
  inspection now runs inside a `runStep` guard that records a failure and always
  prints the full results JSON.

`npm run build`, `npm run build:demo`, and the demo public-boundary check all
pass. The demo `dist` contains zero secret-like strings and no live `/api/<...>`
endpoint calls.

### Conditional senior-review path verified by focused tests

Rather than mutate the last pristine ready case, the conditional path is now
verified deterministically by 45 new backend tests (63 total, all passing):

- `ClinicalCaseServiceTest` (33): auth/role and assignment boundaries, WSI-format
  and duplicate-binding rejection, the CLAM review gate, senior-review trigger
  thresholds (low-confidence at 0.15, boundary band 0.35-0.65), direct completion
  (accept/modify with no trigger), conditional routing (reject -> discordance
  trigger -> `SENIOR_REVIEW`/`AWAITING_REVIEW`; escalate -> explicit-escalation
  trigger -> senior), senior approve -> `COMPLETED`/`QUEUED`, senior return ->
  `RETURNED`/`NOT_ELIGIBLE`, report-stale marking, and feedback-validation
  role/state restrictions.
- `CaseReportCoordinatorTest` (5): the report is gated on a completed case and
  carries BOTH the pathologist decision (type + note) and senior-review context
  (status + note); it omits senior context on direct completion; it fails when
  model evidence or the pathologist decision is missing.
- `ClinicalCaseControllerTest` (7): the controller only calls
  `reportCoordinator.generate` when the workflow reaches `completed` (so
  reject/escalate and senior-return generate no report), and maps
  SecurityException/IllegalArgument/IllegalState to 403/422/409.

### Case creation verified by wiring + tests

All three drawer entry points are confirmed wired: upload completion
(`openCaseCreateById`), the slide-card action (`openCaseCreate` -> `.id`), and
the Dashboard **New case** button (hidden in demo mode). Client-side validation
in `CaseCreateDrawer.vue` mirrors the backend (age 0-120, required fields,
due-after-received) and preselects the incoming slide; already-bound slides are
excluded from the options list, with the backend 409 as a backstop. The
server-side validation and duplicate rejection are covered by the new tests.

### MyBatis-Plus composite-key warnings removed

`V9__surrogate_keys_for_composite_child_tables.sql` adds a surrogate `BIGSERIAL`
primary key to `clinical_case_slides` and `clinical_case_review_triggers` and
demotes each natural composite key to a `UNIQUE` constraint (preserving all
existing uniqueness, the primary-slide partial index, and FKs). The two entities
now carry `@TableId(type = IdType.AUTO) Long id`. This is the supported
surrogate-key strategy, not a fake identity, and it removes the startup
`TableInfoHelper` / `DefaultSqlInjector` warnings at the source. The migration
was dry-run-validated inside a rolled-back transaction against the live schema
and data; it applies on the next `start-local.ps1` (Flyway). `mvn test` passes
with the entity change. The running backend was intentionally NOT restarted, so
the warning removal takes effect on the next start.

### Still open (require heavy/mutating live runs)

- A full live end-to-end exercise of the conditional path (real CLAM -> senior
  queue -> senior approve -> live LLM report) would consume the pristine
  `PO-26-0717-006` and a GPU CLAM run. The logic is proven by tests; this is
  optional live confirmation.
- Live case creation with a freshly uploaded WSI needs an unbound supported WSI
  (all CAMELYON TIFs are bound; the other READY files are excluded JPGs).

## Immediate Next Work

### P0: Finish browser regression verification

1. Run `npm run check:visual` from `frontend` again.
2. The previous full run failed before the final watcher fix because the local
   heatmap stayed in a loading loop.
3. A later targeted check found the heatmap successfully, but its final report
   assertion used the wrong string (`Live multimodal model response`). The
   actual English source label is `Validated live model response`.
4. Update the visual script only if the full rerun still has a stale or fragile
   assertion.
5. Run `npm run build:demo` after final frontend changes to confirm the public
   boundary remains intact.

### P0: Verify the conditional senior-review path

Exercise one complete conditional flow:

1. Run CLAM for a ready assigned case.
2. Start pathologist review.
3. Choose reject or escalate with a required note.
4. Confirm the case appears only in the senior pathologist queue.
5. Sign in as `reviewer` and test both return and approve behavior.
6. Confirm return does not create a report.
7. Confirm approval creates the report and queues feedback.
8. Confirm the report contains both pathologist and senior-review context.

The remaining normal seed case can be used, but doing so will mutate its
persisted state. Prefer creating another case from an additional WSI when one is
available if a pristine ready case should remain in the product.

### P0: Verify case creation through the browser

All existing CAMELYON seed slides are already bound, and the other READY files
are ordinary JPG images that are correctly excluded. Upload another supported
WSI, then verify:

- The upload completion action opens the drawer with that slide selected.
- The slide-card action does the same.
- Dashboard **New case** lists the unbound WSI.
- Invalid age, missing required fields, and due time before received time are
  blocked.
- A second attempt to bind the same WSI is rejected by the backend.
- The created case appears for its assigned pathologist.

### P1: Add focused workflow tests

Add backend tests for `ClinicalCaseService` and `ClinicalCaseController`:

- Role and assignment boundaries.
- WSI format and duplicate-binding validation.
- CLAM-required review gate.
- Trigger threshold boundaries.
- Direct completion and conditional completion.
- Report stale state after a changed decision.
- Feedback validation role and state restrictions.

Add browser coverage for the creation drawer and conditional route after the
API tests are stable.

### P1: Remove avoidable persistence warnings — DONE (2026-07-19)

Resolved with the supported surrogate-key strategy in migration `V9` plus
`@TableId(type = IdType.AUTO)` on `ClinicalCaseSlide` and
`ClinicalCaseReviewTrigger`. See the 2026-07-19 session notes above. The warning
disappears on the next `start-local.ps1` (Flyway applies V9). If a future agent
wants a different approach, the natural composite keys are preserved as `UNIQUE`
constraints, so no uniqueness guarantee is lost.

## Known Caveats

- PostgreSQL is version 18.4 while the bundled Flyway version officially lists
  support through PostgreSQL 16. Migration V8 succeeded, but the startup warning
  remains.
- Vite reports large chunks. This is not blocking the prototype; route/vendor
  splitting is a later optimization.
- The visual-check Vite proxy may log a transient WebSocket `ECONNABORTED` while
  pages navigate. Treat it as a failure only if the UI or protected HTTP
  requests also fail.
- Existing repository changes are intentionally uncommitted. Do not reset the
  worktree or restore the user-deleted `Hello.java`.
- Do not read, print, copy, commit, or expose the API key from the relay
  configuration text file. `start-local.ps1` loads it into process environment
  variables only.
- Public demo behavior must remain independent from the local PostgreSQL state.

## Important Files

- `RUNBOOK.md`: startup, accounts, and browser workflow.
- `PRODUCT.md`: product decisions and audience.
- `DESIGN.md`: visual system and interaction direction.
- `backend/src/main/resources/db/V8__add_clinical_case_workflow.sql`
- `backend/src/main/resources/db/V9__surrogate_keys_for_composite_child_tables.sql`
- `backend/src/test/java/com/wsi/platform/service/ClinicalCaseServiceTest.java`
- `backend/src/test/java/com/wsi/platform/service/CaseReportCoordinatorTest.java`
- `backend/src/test/java/com/wsi/platform/controller/ClinicalCaseControllerTest.java`
- `backend/src/main/java/com/wsi/platform/controller/ClinicalCaseController.java`
- `backend/src/main/java/com/wsi/platform/service/ClinicalCaseService.java`
- `backend/src/main/java/com/wsi/platform/service/CaseReportCoordinator.java`
- `backend/src/main/java/com/wsi/platform/config/DataInitializer.java`
- `frontend/src/components/CaseCreateDrawer.vue`
- `frontend/src/views/CaseDashboardView.vue`
- `frontend/src/views/CaseReviewView.vue`
- `frontend/src/views/FileBrowserView.vue`
- `frontend/src/stores/caseStore.js`
- `frontend/src/composables/useAiAnalysis.js`
- `frontend/src/composables/useAiReport.js`
- `frontend/scripts/visual-check.mjs`
- `scripts/start-local.ps1`
- `scripts/stop-local.ps1`

## Commands

Start or restart the complete local stack from the repository root:

```powershell
.\scripts\stop-local.ps1
.\scripts\start-local.ps1 -LlmModelOverride "gpt-5.6-sol"
```

Run verification:

```powershell
cd backend
mvn test

cd ..\python-service
conda run -n pathorchestra-py --no-capture-output python -m unittest discover -s tests -v

cd ..\frontend
npm run build
npm run build:demo
npm run check:visual
```

Logs and tracked process metadata are under `.run/`.

## Suggested First Action for the Next Agent

Read this file and `RUNBOOK.md`, inspect the dirty worktree without resetting
it, confirm the three local ports, and rerun `npm run check:visual`. Then proceed
with the conditional senior-review path before adding new modules.
