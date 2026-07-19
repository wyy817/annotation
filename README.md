# Pathorchestra

**An AI-assisted pathology workflow platform for breast cancer lymph-node H&E whole-slide images.**

Pathorchestra turns incoming whole-slide image (WSI) cases into a prioritized work
queue, presents explainable image and text evidence in the slide viewer, keeps a
qualified pathologist in control of every decision, and routes reviewed
annotations into a governed feedback pool.

> [!IMPORTANT]
> This is a **seven-day product-grade prototype** and a **clinical
> decision-support demonstration** — *not* an autonomous diagnostic system and
> *not* a clinically validated medical device. It uses clearly labeled sample
> data (CAMELYON16) and must not be used for real diagnosis.

---

## The workflow

```
Upload WSI ─▶ Create case + bind Primary H&E WSI ─▶ Assign pathologist
     │
     ▼
Manual CLAM analysis ─▶ Review heatmap / candidate regions / metrics
     │
     ▼
Pathologist decision  (accept · modify · reject · escalate)
     │
     ├─ direct final decision ──────────────▶ AI-assisted report ─▶ feedback pool
     │
     └─ conditional (reject / escalate / low-confidence / boundary / QC)
            └─▶ Senior pathologist review ──▶ approve ─▶ report ─▶ feedback pool
                                            └▶ return  ─▶ back to pathologist
```

## Architecture

Three services plus a database. The **public demo** is a static build only; the
full clinical stack runs locally and is never exposed.

```
                 ┌───────────────────────────── LOCAL / PRIVATE ─────────────────────────────┐
                 │                                                                            │
  Browser ─────▶ │  Vue 3 + Vite frontend  ──HTTP──▶  Spring Boot API  ──▶  PostgreSQL        │
  :5173          │  (Element Plus, Pinia,            (:8080, MyBatis-Plus,   (wsi_platform)   │
                 │   OpenSeadragon)                   Flyway V1–V9)                            │
                 │                                        │                                    │
                 │                                        └──HTTP──▶  Python GPU service       │
                 │                                                   (:8000, FastAPI,          │
                 │                                                    CLAM + MobileSAM, Torch)  │
                 └────────────────────────────────────────────────────────────────────────────┘

  Public demo:   Vue "demo" build  ──▶  static host (Netlify)   [precomputed evidence,
                 (frontend/dist)                                 localStorage, NO backend,
                                                                 NO upload, NO API key]
```

## Tech stack

| Layer            | Technology |
| ---------------- | ---------- |
| Frontend         | Vue 3, Vite 5, Element Plus, Pinia, Vue Router, Vue I18n, OpenSeadragon, Fabric.js, ECharts |
| Backend          | Java 21, Spring Boot 3.3.5, MyBatis-Plus 3.5.9, Flyway |
| Database         | PostgreSQL |
| AI service       | Python, FastAPI, PyTorch / TorchVision / timm, MobileSAM, OpenSlide, a CLAM-style inference pipeline |
| Reporting        | Live multimodal LLM (via a configurable relay) with a deterministic fallback |

## Features

- **Case workflow** — create a clinical case, bind exactly one Primary H&E WSI,
  and assign a pathologist. Binding becomes immutable once analysis starts.
- **Explainable AI evidence** — model overview and relative-attention heatmaps,
  candidate regions, tumor-evidence score, ensemble disagreement, and per-region
  metrics, shown alongside provenance and stated limitations.
- **Clinician-in-control decisions** — accept, modify, reject, or escalate, with
  a full audit trail.
- **Conditional senior review** — reject/escalation, ensemble disagreement,
  boundary evidence, image–text discordance, or stable QC sampling route a case
  into a senior pathologist queue; a report is generated only after approval.
- **AI-assisted reporting** — reports carry both pathologist and senior-review
  context and are marked stale when a decision changes.
- **Governed feedback pool** — completed results are queued and validated by an
  AI/data administrator.
- **Bilingual UI** — English default with full Chinese parity.
- **Static public demo** — precomputed CAMELYON positive/negative evidence with
  a strict static boundary (no registration, upload, live inference, or secrets).

## Roles

| Role                       | System role      | Responsibilities |
| -------------------------- | ---------------- | ---------------- |
| Pathologist                | `ANNOTATOR`      | Triage cases, run analysis, review evidence, make the final decision |
| Senior pathologist         | `REVIEWER`       | Review escalated / flagged / QC-sampled cases |
| AI / data administrator    | `AI_DATA_ADMIN`  | Create cases, bind slides, assign pathologists, validate feedback |
| System administrator       | `SYSTEM_ADMIN`   | User and system administration, case intake (no clinical decisions) |

## Repository structure

```
.
├── backend/           Spring Boot API (Java 21, Maven, MyBatis-Plus, Flyway V1–V9)
├── frontend/          Vue 3 + Vite product (local mode + static demo mode)
├── python-service/    FastAPI GPU inference service (CLAM pipeline, MobileSAM)
├── scripts/           start-local.ps1 / stop-local.ps1 (local stack orchestration)
├── PRODUCT.md         Product decisions, users, and design principles
├── DESIGN.md          Visual system and interaction direction
├── RUNBOOK.md         Startup, accounts, and browser workflow
├── DEPLOY.md          Public static demo deployment (Netlify)
└── AGENT_HANDOFF.md   Engineering state and next work
```

## Prerequisites

- **Java 21** and **Maven**
- **Node.js** (18+) and npm
- **PostgreSQL** with a database named `wsi_platform`
- **Miniforge/Conda** with a `pathorchestra-py` environment (for the AI service)
- A CUDA-capable **GPU** is recommended for real CLAM inference (the AI service
  runs on CPU for lighter operations, but WSI inference expects a GPU)

## Getting started

### Quick start (Windows / PowerShell)

From the repository root, the helper script starts all three services and loads
the LLM relay configuration into environment variables only (the key is never
copied into the repo or logs):

```powershell
.\scripts\stop-local.ps1
.\scripts\start-local.ps1 -LlmModelOverride "gpt-5.6-sol"
```

Use `-DisableLlm` for deterministic report fallbacks without an external request.

Local URLs:

| Service          | URL |
| ---------------- | --- |
| Product (Vue)    | http://127.0.0.1:5173 |
| Spring API       | http://127.0.0.1:8080 |
| AI service docs  | http://127.0.0.1:8000/docs |

Local development accounts are documented in [`RUNBOOK.md`](RUNBOOK.md) (seeded
for local development only; self-registration exposes clinical roles only).

### Manual per-service startup

```bash
# Backend (Spring Boot API)
cd backend
mvn spring-boot:run          # applies Flyway migrations V1–V9 on startup

# Frontend (Vue product)
cd frontend
npm install
npm run dev                  # local mode against the Spring API

# AI service (FastAPI, in the pathorchestra-py conda env)
cd python-service
conda run -n pathorchestra-py python -m uvicorn main:app --port 8000
```

Backend datasource defaults live in `backend/src/main/resources/application.yml`
and can be overridden with the `WSI_DB_URL`, `WSI_DB_USERNAME`, and
`WSI_DB_PASSWORD` environment variables.

## Testing

```bash
# Backend — 63 unit tests (Mockito)
cd backend
mvn test

# AI service
cd python-service
conda run -n pathorchestra-py --no-capture-output python -m unittest discover -s tests -v

# Frontend — production build, demo build, and headless visual regression
cd frontend
npm run build
npm run build:demo
npm run check:visual         # drives Edge over the demo + local flows
```

## Public demo deployment

The public demo is a **static** build containing only precomputed CAMELYON
evidence and browser-local (`localStorage`) state — no backend, upload, live
inference, credentials, or patient data.

```bash
cd frontend
npm run build:demo           # outputs frontend/dist
```

Deploy `frontend/dist` to any static host. See [`DEPLOY.md`](DEPLOY.md) for the
step-by-step Netlify runbook (drag-and-drop, hardening headers, and the
pre-publish safety checklist).

## Documentation

| Document | Purpose |
| -------- | ------- |
| [PRODUCT.md](PRODUCT.md) | Product purpose, users, and design principles |
| [DESIGN.md](DESIGN.md) | Visual system, tokens, and interaction direction |
| [RUNBOOK.md](RUNBOOK.md) | Startup, local accounts, and the browser workflow |
| [DEPLOY.md](DEPLOY.md) | Public static demo deployment |
| [AGENT_HANDOFF.md](AGENT_HANDOFF.md) | Current engineering state and next work |

## Security & data boundary

- The public demo must remain static: no registration, upload, live inference,
  real credentials, or sensitive data.
- The LLM relay configuration (containing the API key) lives **outside** the
  repository and is read only at runtime; it is never committed or logged.
- Clinical uploads, generated tiles, exports, and runtime artifacts are
  gitignored (`data/`, `python-service/storage/`, `.run/`, `*.env`).

## Acknowledgments

- **CAMELYON16** — the public breast cancer lymph-node metastasis dataset used
  for demonstration evidence.
- **MobileSAM** — lightweight Segment Anything model used by the AI service.
- **CLAM** — the clustering-constrained attention multiple-instance learning
  approach that inspired the WSI inference pipeline.

## License

No license file is currently present in this repository. Until a license is
added, all rights are reserved by the authors. Add a `LICENSE` file to define
reuse terms before any public distribution.
