"""Internal asynchronous API for CLAM WSI analysis jobs."""

from __future__ import annotations

import threading
from pathlib import Path
from typing import Any

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field

from parvisight_ai.jobs import AnalysisJobManager, AnalysisRuntimeSettings

router = APIRouter()

_manager: AnalysisJobManager | None = None
_manager_lock = threading.Lock()


class CreateAnalysisRequest(BaseModel):
    slide_id: int = Field(gt=0)
    file_path: str = Field(min_length=1)
    case_id: str | None = Field(default=None, max_length=128)
    annotation_xml: str | None = None
    patch_h5: str | None = None


class AnalysisJobResponse(BaseModel):
    job_id: str
    slide_id: int
    case_id: str | None
    status: str
    stage: str
    stage_progress: float
    message: str
    created_at: str
    started_at: str | None
    completed_at: str | None
    error: str | None
    status_url: str
    result_url: str


def _get_analysis_job_manager() -> AnalysisJobManager:
    global _manager
    if _manager is not None:
        return _manager
    with _manager_lock:
        if _manager is None:
            _manager = AnalysisJobManager(AnalysisRuntimeSettings.from_env())
    return _manager


def _job_response(record: dict[str, Any]) -> AnalysisJobResponse:
    job_id = record["job_id"]
    return AnalysisJobResponse(
        **{
            key: record.get(key)
            for key in AnalysisJobResponse.model_fields
            if key not in {"status_url", "result_url"}
        },
        status_url=f"/internal/ai/analyses/{job_id}",
        result_url=f"/internal/ai/analyses/{job_id}/result",
    )


@router.get("/capabilities")
def capabilities() -> dict[str, Any]:
    return _get_analysis_job_manager().capabilities()


@router.post(
    "/analyses",
    response_model=AnalysisJobResponse,
    status_code=202,
)
def create_analysis(req: CreateAnalysisRequest) -> AnalysisJobResponse:
    manager = _get_analysis_job_manager()
    try:
        record = manager.submit(
            slide_id=req.slide_id,
            file_path=Path(req.file_path),
            case_id=req.case_id,
            annotation_xml=Path(req.annotation_xml) if req.annotation_xml else None,
            patch_h5=Path(req.patch_h5) if req.patch_h5 else None,
        )
    except FileNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    return _job_response(record)


@router.get("/analyses/{job_id}", response_model=AnalysisJobResponse)
def get_analysis(job_id: str) -> AnalysisJobResponse:
    try:
        return _job_response(_get_analysis_job_manager().get(job_id))
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Analysis job not found") from exc


@router.get("/analyses/{job_id}/result")
def get_analysis_result(job_id: str) -> JSONResponse:
    manager = _get_analysis_job_manager()
    try:
        result = manager.get_result(job_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Analysis job not found") from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc
    return JSONResponse(result)


def _artifact_paths(result: dict[str, Any]) -> set[str]:
    allowed: set[str] = set()

    def collect(value: Any) -> None:
        if isinstance(value, str):
            allowed.add(Path(value).as_posix())
        elif isinstance(value, dict):
            for nested in value.values():
                collect(nested)
        elif isinstance(value, list):
            for nested in value:
                collect(nested)

    collect(result.get("artifacts", {}))
    return allowed


@router.get("/analyses/{job_id}/files/{artifact_path:path}")
def get_analysis_artifact(job_id: str, artifact_path: str) -> FileResponse:
    manager = _get_analysis_job_manager()
    try:
        result = manager.get_result(job_id)
        run_dir = manager.run_dir(job_id).resolve()
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="Analysis job not found") from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc

    normalized = Path(artifact_path).as_posix()
    if normalized not in _artifact_paths(result):
        raise HTTPException(status_code=404, detail="Analysis artifact not found")
    target = (run_dir / Path(normalized)).resolve()
    try:
        target.relative_to(run_dir)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail="Analysis artifact not found") from exc
    if not target.is_file():
        raise HTTPException(status_code=404, detail="Analysis artifact not found")
    return FileResponse(target)
