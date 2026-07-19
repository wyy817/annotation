"""Single-GPU background job orchestration for CLAM analyses."""

from __future__ import annotations

import copy
import hashlib
import json
import logging
import os
import re
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

from .clam_pipeline import ClamAnalysisConfig, analyze_wsi

logger = logging.getLogger(__name__)

_JOB_ID_PATTERN = re.compile(r"^[0-9a-f]{32}$")


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass(frozen=True)
class AnalysisRuntimeSettings:
    analysis_root: Path
    clam_root: Path | None
    checkpoint_dir: Path | None
    resnet_weights: Path | None
    device: str = "auto"
    batch_size: int = 64
    max_workers: int = 1

    @classmethod
    def from_env(cls) -> "AnalysisRuntimeSettings":
        def optional_path(name: str) -> Path | None:
            value = os.environ.get(name, "").strip()
            return Path(value).expanduser().resolve() if value else None

        analysis_root = Path(
            os.environ.get("AI_ANALYSIS_ROOT", "./storage/ai-analyses")
        ).expanduser().resolve()
        return cls(
            analysis_root=analysis_root,
            clam_root=optional_path("CLAM_ROOT"),
            checkpoint_dir=optional_path("CLAM_CHECKPOINT_DIR"),
            resnet_weights=optional_path("CLAM_RESNET_WEIGHTS"),
            device=os.environ.get("CLAM_DEVICE", "auto"),
            batch_size=int(os.environ.get("CLAM_BATCH_SIZE", "64")),
            max_workers=int(os.environ.get("CLAM_MAX_WORKERS", "1")),
        )

    def missing_configuration(self) -> list[str]:
        missing: list[str] = []
        checks = {
            "CLAM_ROOT": (
                self.clam_root,
                Path(self.clam_root, "models", "model_clam.py")
                if self.clam_root
                else None,
            ),
            "CLAM_CHECKPOINT_DIR": (self.checkpoint_dir, self.checkpoint_dir),
            "CLAM_RESNET_WEIGHTS": (self.resnet_weights, self.resnet_weights),
        }
        for env_name, (configured, expected) in checks.items():
            if configured is None or expected is None or not expected.exists():
                missing.append(env_name)
        if self.batch_size < 1:
            missing.append("CLAM_BATCH_SIZE")
        if self.max_workers != 1:
            missing.append("CLAM_MAX_WORKERS must be 1 for the local GPU queue")
        return missing


class AnalysisJobManager:
    def __init__(
        self,
        settings: AnalysisRuntimeSettings,
        analyzer: Callable[..., dict[str, Any]] = analyze_wsi,
    ) -> None:
        self.settings = settings
        self._analyzer = analyzer
        self._jobs: dict[str, dict[str, Any]] = {}
        self._lock = threading.RLock()
        self._executor = ThreadPoolExecutor(
            max_workers=settings.max_workers,
            thread_name_prefix="clam-analysis",
        )
        (settings.analysis_root / "runs").mkdir(parents=True, exist_ok=True)
        (settings.analysis_root / "cache").mkdir(parents=True, exist_ok=True)

    def capabilities(self) -> dict[str, Any]:
        missing = self.settings.missing_configuration()
        return {
            "configured": not missing,
            "missing": missing,
            "device": self.settings.device,
            "batch_size": self.settings.batch_size,
            "queue_concurrency": self.settings.max_workers,
            "pipeline": "CAMELYON 40x CLAM 10-fold ensemble",
        }

    def submit(
        self,
        *,
        slide_id: int,
        file_path: Path,
        case_id: str | None = None,
        annotation_xml: Path | None = None,
        patch_h5: Path | None = None,
    ) -> dict[str, Any]:
        missing = self.settings.missing_configuration()
        if missing:
            raise RuntimeError(
                "AI analysis is not configured: " + ", ".join(missing)
            )
        file_path = file_path.expanduser().resolve()
        if not file_path.is_file():
            raise FileNotFoundError(f"Slide not found: {file_path}")
        if annotation_xml is not None:
            annotation_xml = annotation_xml.expanduser().resolve()
            if not annotation_xml.is_file():
                raise FileNotFoundError(
                    f"Annotation XML not found: {annotation_xml}"
                )
        if patch_h5 is not None:
            patch_h5 = patch_h5.expanduser().resolve()
            if not patch_h5.is_file():
                raise FileNotFoundError(f"Patch cache not found: {patch_h5}")

        job_id = uuid.uuid4().hex
        run_dir = self._run_dir(job_id)
        cache_key = self._slide_cache_key(file_path)
        record: dict[str, Any] = {
            "job_id": job_id,
            "slide_id": slide_id,
            "case_id": case_id,
            "status": "queued",
            "stage": "queued",
            "stage_progress": 0.0,
            "message": "Waiting for the local GPU worker",
            "created_at": _utc_now(),
            "started_at": None,
            "completed_at": None,
            "error": None,
            "result_path": None,
            "input": {
                "file_path": str(file_path),
                "annotation_xml": str(annotation_xml) if annotation_xml else None,
                "patch_h5": str(patch_h5) if patch_h5 else None,
            },
            "cache_key": cache_key,
        }
        with self._lock:
            self._jobs[job_id] = record
            self._write_record(record)
        self._executor.submit(
            self._run_analysis,
            job_id,
            file_path,
            annotation_xml,
            patch_h5,
        )
        return self.get(job_id)

    def get(self, job_id: str) -> dict[str, Any]:
        self._validate_job_id(job_id)
        with self._lock:
            record = self._jobs.get(job_id)
            if record is None:
                record = self._load_record(job_id)
                if record is not None:
                    self._jobs[job_id] = record
            if record is None:
                raise KeyError(job_id)
            return copy.deepcopy(record)

    def get_result(self, job_id: str) -> dict[str, Any]:
        record = self.get(job_id)
        if record["status"] != "succeeded":
            raise RuntimeError(
                f"Analysis result is not available while status is {record['status']}"
            )
        result_path = Path(record["result_path"])
        return json.loads(result_path.read_text(encoding="utf-8"))

    def run_dir(self, job_id: str) -> Path:
        self.get(job_id)
        return self._run_dir(job_id)

    def shutdown(self, wait: bool = True) -> None:
        self._executor.shutdown(wait=wait, cancel_futures=False)

    def _run_analysis(
        self,
        job_id: str,
        file_path: Path,
        annotation_xml: Path | None,
        patch_h5: Path | None,
    ) -> None:
        self._update(
            job_id,
            status="running",
            stage="initializing",
            stage_progress=0.0,
            message="Preparing CLAM analysis",
            started_at=_utc_now(),
        )
        record = self.get(job_id)
        run_dir = self._run_dir(job_id)
        cache_dir = self.settings.analysis_root / "cache" / record["cache_key"]

        def progress(stage: str, fraction: float, message: str) -> None:
            self._update(
                job_id,
                stage=stage,
                stage_progress=round(float(fraction), 4),
                message=message,
            )

        try:
            config = ClamAnalysisConfig(
                slide_path=file_path,
                output_dir=run_dir,
                cache_dir=cache_dir,
                clam_root=self.settings.clam_root,
                checkpoint_dir=self.settings.checkpoint_dir,
                resnet_weights=self.settings.resnet_weights,
                annotation_xml=annotation_xml,
                patch_h5=patch_h5,
                device=self.settings.device,
                batch_size=self.settings.batch_size,
            )
            self._analyzer(config, progress=progress)
            result_path = run_dir / "result.json"
            self._update(
                job_id,
                status="succeeded",
                stage="complete",
                stage_progress=1.0,
                message="Analysis completed",
                completed_at=_utc_now(),
                result_path=str(result_path),
            )
        except Exception as exc:
            logger.exception("CLAM analysis failed for job %s", job_id)
            self._update(
                job_id,
                status="failed",
                stage="failed",
                message="Analysis failed",
                completed_at=_utc_now(),
                error=str(exc)[:1000],
            )

    def _update(self, job_id: str, **changes: Any) -> None:
        with self._lock:
            record = self._jobs[job_id]
            record.update(changes)
            self._write_record(record)

    def _write_record(self, record: dict[str, Any]) -> None:
        run_dir = self._run_dir(record["job_id"])
        run_dir.mkdir(parents=True, exist_ok=True)
        destination = run_dir / "job.json"
        temporary = run_dir / "job.json.tmp"
        temporary.write_text(
            json.dumps(record, indent=2, ensure_ascii=True), encoding="utf-8"
        )
        os.replace(temporary, destination)

    def _load_record(self, job_id: str) -> dict[str, Any] | None:
        path = self._run_dir(job_id) / "job.json"
        if not path.is_file():
            return None
        record = json.loads(path.read_text(encoding="utf-8"))
        if record.get("status") in {"queued", "running"}:
            record.update(
                {
                    "status": "failed",
                    "stage": "interrupted",
                    "message": "The Python service restarted during analysis",
                    "completed_at": _utc_now(),
                    "error": "analysis_interrupted_by_service_restart",
                }
            )
            self._write_record(record)
        return record

    def _slide_cache_key(self, file_path: Path) -> str:
        stat = file_path.stat()
        identity = f"{file_path}|{stat.st_size}|{stat.st_mtime_ns}".encode("utf-8")
        digest = hashlib.sha256(identity).hexdigest()[:16]
        return f"slide-{digest}"

    def _run_dir(self, job_id: str) -> Path:
        self._validate_job_id(job_id)
        return self.settings.analysis_root / "runs" / job_id

    @staticmethod
    def _validate_job_id(job_id: str) -> None:
        if not _JOB_ID_PATTERN.fullmatch(job_id):
            raise KeyError(job_id)
