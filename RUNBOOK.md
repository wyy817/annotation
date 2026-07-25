# PARVISIGHT Local Runbook

## Start the complete stack

Prerequisites: Java 21, Maven, Node.js, PostgreSQL, Miniforge, and the
`parvisight-py` Conda environment. The `wsi_platform` PostgreSQL database
must exist and be reachable with the settings in
`backend/src/main/resources/application.yml`.

From the repository root:

```powershell
.\scripts\start-local.ps1 -LlmModelOverride "gpt-5.6-sol"
```

The script starts the GPU inference service, Spring Boot API, and Vue product.
It loads MobileSAM from `python-service/model/mobile_sam.pt` by default and
validates the checkpoint before starting any process. Use `-SamCheckpoint` to
select another weight file and `-SamModelType` when switching away from the
default `vit_t` MobileSAM architecture.

It discovers the relay configuration text file in the parent workspace by its
`Base URL`, `api key`, and `model` fields. Values are passed to the Spring
process as environment variables only; the key is never copied into the
repository or logs. Use `-RelayConfigPath` to select a file explicitly.

The relay's configured model alias was unavailable during integration testing,
while `gpt-5.6-sol` completed the full multimodal request. The explicit override
above keeps that operational choice visible and leaves the source configuration
unchanged. Model availability is relay-specific and may change. Use
`-DisableLlm` for deterministic report fallbacks without an external request.

Local URLs:

- Product: `http://127.0.0.1:5173`
- Spring API: `http://127.0.0.1:8080`
- AI service docs: `http://127.0.0.1:8000/docs`

Development accounts:

| Role | Username | Password |
| --- | --- | --- |
| Pathologist | `annotator` | `anno123` |
| Senior pathologist | `reviewer` | `review123` |
| AI / data administrator | `aiadmin` | `data123` |
| System administrator | `sysadmin` | `system123` |

These accounts are seeded only for local product development. Self-registration
continues to expose the clinical roles only; administrator accounts are created
by server-side configuration.

## Browser workflow

1. Sign in as `aiadmin` or `sysadmin`, open the slide library, and upload a WSI.
2. When the slide reaches `READY`, choose **Create case from slide**, or open
   **Case queue** and choose **New case**.
3. Enter the accession/reference, patient age at collection, specimen name,
   received and due times, and assign a pathologist. The first disease profile
   requires exactly one Primary H&E WSI.
4. Sign in as the assigned pathologist, open the case, and choose **Run
   analysis**. The case remains non-reviewable until CLAM succeeds.
5. Inspect the persisted heatmap, candidate regions, and quantitative metrics,
   then start review and accept, modify, reject, or escalate the finding.
6. A direct final decision queues the AI report immediately. Conditional cases
   first enter the senior pathologist queue; report generation starts only after
   approval. The completed result is also added to the feedback pool.

`PO-26-0717-001` and `PO-26-0717-006` are seeded from the local CAMELYON tumor
and normal slides when those READY files are available. Runtime case state,
decisions, reports, audit events, and feedback status are persisted in
PostgreSQL; local browser state from the public demo is intentionally ignored.

Logs and process metadata are written under `.run/`, which is ignored by Git.
The stop script only terminates listener processes recorded there by the start
script; unrelated services using the same ports are left untouched.

## Stop the stack

```powershell
.\scripts\stop-local.ps1
```

## Public demo build

The public build contains only precomputed CAMELYON demonstration evidence and
browser-local workflow state. It has no registration, upload, or live inference.

```powershell
cd frontend
npm run build:demo
```

The deployable static site is written to `frontend/dist`. It contains the real,
precomputed positive and negative CAMELYON evidence used by the public demo,
but no source WSI, patient identity, API key, registration, or upload endpoint.

## Verification

```powershell
cd backend
mvn test

cd ..\python-service
conda run -n parvisight-py --no-capture-output python -m unittest discover -s tests -v

cd ..\frontend
npm run build
npm run build:demo
npm run check:visual
```
