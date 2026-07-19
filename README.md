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

The local product requires three processes (Vue, Spring Boot, and FastAPI), a
PostgreSQL database, and the private AI assets used by the CLAM inference
adapter. The public demo does not require any of these services or model files.
The commands below target Windows 10/11 and PowerShell, which is the supported
path for `scripts/start-local.ps1`.

### Required software

- **Java 21 JDK** and **Maven** (`mvn.cmd` must be on `PATH`)
- **Node.js 18 or later** and npm (`npm.cmd` must be on `PATH`)
- **PostgreSQL** with `psql` on `PATH`
- **Git** for downloading the external CLAM source tree
- **Miniforge/Conda**
- An NVIDIA GPU with a working CUDA driver. The verified development setup is
  PyTorch `2.11.0+cu128`, TorchVision `0.26.0+cu128`, CUDA runtime `12.8`, and
  an NVIDIA GeForce RTX 5060. CPU mode is useful for API and UI checks, but a
  full WSI CLAM run should be treated as GPU work.

Check the toolchain before installing model assets:

```powershell
java -version
mvn -version
node --version
npm --version
psql --version
git --version
conda --version
```

### PostgreSQL database

Create an empty database before starting Spring Boot. Flyway creates and
updates the application tables automatically:

```powershell
psql -U postgres -h localhost -c "CREATE DATABASE wsi_platform;"
```

If the database already exists, PostgreSQL will report that fact and no action
is needed. The local defaults are defined in
`backend/src/main/resources/application.yml`; override them for another
installation instead of editing the source file:

```powershell
$env:WSI_DB_URL = "jdbc:postgresql://localhost:5432/wsi_platform"
$env:WSI_DB_USERNAME = "postgres"
$env:WSI_DB_PASSWORD = "<your-postgres-password>"
```

The checked-in development fallback is `postgres` / `123`. Change it through
environment variables for any shared, hosted, or clinical-like environment.

### Python and CUDA environment

Create the environment once, then install the service dependencies. Install a
CUDA-enabled PyTorch/TorchVision pair first, using the [official PyTorch
installation selector](https://pytorch.org/get-started/locally/) for the target
driver, and install the remaining pinned dependencies afterwards:

```powershell
conda create -n pathorchestra-py python=3.11 -y
conda activate pathorchestra-py

# Install the CUDA-compatible torch and torchvision wheels selected for the host.
# The current verified environment reports:
#   torch 2.11.0+cu128, torchvision 0.26.0+cu128, CUDA 12.8

cd python-service
python -m pip install -r requirements.txt
```

On Windows, OpenSlide also needs its native DLL. The code looks for the DLL in
the active Conda environment (`<conda-env>\Library\bin`) and, when present,
in the `openslide-bin` Python package. The simplest Windows setup is:

```powershell
conda install -n pathorchestra-py -c conda-forge openslide -y
conda run -n pathorchestra-py python -c "import openslide; print(openslide.__file__)"
```

If using a pip-only environment instead, install `openslide-bin` alongside the
already-pinned `openslide-python` package and confirm that
`libopenslide-1.dll` or `libopenslide-0.dll` is available. Without this native
runtime, WSI upload may succeed but thumbnail, DeepZoom tile, or CLAM requests
will fail.

Verify PyTorch and CUDA from the environment that will run FastAPI:

```powershell
conda run -n pathorchestra-py python -c "import torch, torchvision; print(torch.__version__, torchvision.__version__, torch.version.cuda); print('CUDA available:', torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU')"
```

### CLAM source tree

This project imports the original CLAM model classes directly from a separate
checkout; CLAM is not vendored into this repository and does not need to be
installed as a second Python package. The default launcher expects the source
tree beside this repository under `PathorchestraData\CLAM` and checks for
`CLAM\models\model_clam.py`.

The source repository is [mahmoodlab/CLAM](https://github.com/mahmoodlab/CLAM):

```powershell
$dataRoot = "D:\Users\yijun\OneDrive\ic_gcl\PathorchestraData"
New-Item -ItemType Directory -Force -Path $dataRoot | Out-Null
git clone https://github.com/mahmoodlab/CLAM.git "$dataRoot\CLAM"
Test-Path "$dataRoot\CLAM\models\model_clam.py"
```

If `CLAM` already exists, do not clone over it. Confirm that its `models`
directory contains `model_clam.py`, `resnet_custom_dep.py`, and the other source
files required by the adapter. The external CLAM repository is GPLv3 and has
its own non-commercial academic-use notice; review its license before any
redistribution.

### ResNet-50 encoder weights

The downloaded CAMELYON checkpoints were trained with the legacy CLAM
truncated ResNet-50 encoder (1024-dimensional features). This is why the
project expects the older `resnet50-19c8e357.pth` file rather than an arbitrary
newer ResNet-50 checkpoint. `scripts/start-local.ps1` reads this exact path:

```text
%USERPROFILE%\.cache\torch\hub\checkpoints\resnet50-19c8e357.pth
```

Download the official PyTorch weight file into that directory:

```powershell
$resnetDir = Join-Path $env:USERPROFILE ".cache\torch\hub\checkpoints"
New-Item -ItemType Directory -Force -Path $resnetDir | Out-Null
Invoke-WebRequest `
  -Uri "https://download.pytorch.org/models/resnet50-19c8e357.pth" `
  -OutFile (Join-Path $resnetDir "resnet50-19c8e357.pth")
Test-Path (Join-Path $resnetDir "resnet50-19c8e357.pth")
```

Do not commit this file. It is ignored by repository policy because model
weights are large binary assets.

### CAMELYON16/17 CLAM checkpoints

Download the trained checkpoints from the [official CLAM model-checkpoint
folder](https://drive.google.com/drive/folders/1NZ82z0U_cexP6zkx1mRk-QeJyKWk4Q7z?usp=sharing),
then extract the `camelyon_40x_cv` directory under:

```text
<PathorchestraData>\models\clam\camelyon_40x_cv\
```

The current runtime uses the 100% CLAM ensemble and requires all ten fold
files in this exact directory:

```text
PathorchestraData\models\clam\camelyon_40x_cv\
└── camelyon_40x_cv_CLAM_100_s1\
    ├── s_0_checkpoint.pt
    ├── s_1_checkpoint.pt
    ├── ...
    └── s_9_checkpoint.pt
```

The other downloaded folders (`CLAM_10`, `CLAM_25`, `CLAM_50`, `CLAM_75`, and
the `MIL_*` variants) are optional for future experiments. They are not needed
by the default application workflow. A quick check should report ten files:

```powershell
$checkpointDir = "$dataRoot\models\clam\camelyon_40x_cv\camelyon_40x_cv_CLAM_100_s1"
(Get-ChildItem -LiteralPath $checkpointDir -Filter "s_*_checkpoint.pt").Count
```

The adapter ensembles these ten folds to produce the persisted slide evidence,
relative-attention heatmap, candidate regions, and quantitative region metrics.
This is a research/demo inference path, not a clinically validated diagnostic
model.

### MobileSAM checkpoint

The local launcher also validates `python-service/model/mobile_sam.pt` for the
interactive SAM endpoint. The checkpoint can be obtained from the
[official MobileSAM repository](https://github.com/ChaoningZhang/MobileSAM/tree/master/weights).
Keep the file at:

```text
annotation\python-service\model\mobile_sam.pt
```

Use `-SamCheckpoint "D:\path\to\mobile_sam.pt"` when storing it elsewhere.

### Required local asset layout

With the default launcher and this repository location, the relevant files are:

```text
D:\Users\yijun\OneDrive\ic_gcl\
+-- annotation\
|   `-- python-service\model\mobile_sam.pt
`-- PathorchestraData\
    +-- CLAM\
    +-- models\clam\camelyon_40x_cv\camelyon_40x_cv_CLAM_100_s1\
    |   `-- s_0_checkpoint.pt ... s_9_checkpoint.pt
    `-- runs\service-ai\       # generated feature/evidence cache
```

The default data root is the parent directory of the repository plus
`PathorchestraData`. To store assets elsewhere, create the directory first and
pass it explicitly:

```powershell
New-Item -ItemType Directory -Force -Path "D:\PathorchestraData" | Out-Null
.\scripts\start-local.ps1 -DataRoot "D:\PathorchestraData" -LlmModelOverride "gpt-5.6-sol"
```

Keep WSI files, generated tiles, feature caches, checkpoints, and reports
outside Git. The application writes runtime data under `backend/data/` and
`PathorchestraData/runs/`; these locations are intentionally excluded from the
repository.

### Optional live LLM reporting

The UI and backend can use deterministic report fallback text without an
external model. For live multimodal reports, keep a relay configuration file
outside Git (for example, `大模型api接口.txt` in the workspace parent) with
these three fields:

```text
Base URL: <relay-openai-compatible-base-url>
api key: <secret>
model: <relay-model-alias>
```

The startup script discovers this file or accepts `-RelayConfigPath`; it passes
the values to Spring as `PATHORCHESTRA_LLM_BASE_URL`,
`PATHORCHESTRA_LLM_API_KEY`, and `PATHORCHESTRA_LLM_MODEL` without copying or
logging the key. Never commit the file or put the key in frontend code.

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
