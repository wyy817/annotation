"""Reproducible CLAM inference for breast lymph-node H&E whole-slide images.

The downloaded CAMELYON checkpoints use the original truncated ResNet-50
encoder, not the newer timm encoder in current CLAM releases. This adapter
loads that encoder explicitly, ensembles the ten CLAM folds, and keeps model
evidence separate from clinical conclusions.
"""

from __future__ import annotations

import hashlib
import importlib
import json
import logging
import math
import os
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Sequence

import cv2
import h5py
import numpy as np
from PIL import Image, ImageDraw

logger = logging.getLogger(__name__)

_OPENSLIDE_DLL_HANDLE: Any | None = None

ProgressCallback = Callable[[str, float, str], None]

SCHEMA_VERSION = "1.0"
CLAM_MODEL_NAME = "CAMELYON 40x CLAM 100% s1, 10-fold ensemble"
FEATURE_ENCODER_NAME = "ImageNet ResNet-50 truncated after layer3 (1024-d)"


@dataclass(frozen=True)
class ClamAnalysisConfig:
    slide_path: Path
    output_dir: Path
    clam_root: Path
    checkpoint_dir: Path
    resnet_weights: Path
    annotation_xml: Path | None = None
    patch_h5: Path | None = None
    cache_dir: Path | None = None
    device: str = "auto"
    batch_size: int = 64
    patch_size: int = 256
    patch_level: int = 0
    step_size: int = 256
    target_patch_size: int = 224
    attention_percentile_threshold: float = 0.95
    slide_score_gate: float = 0.50
    min_candidate_patches: int = 3
    render_downsample: float = 64.0

    def validate(self) -> None:
        required_files = {
            "slide": self.slide_path,
            "ResNet-50 weights": self.resnet_weights,
        }
        if self.annotation_xml is not None:
            required_files["annotation XML"] = self.annotation_xml
        if self.patch_h5 is not None:
            required_files["patch coordinate HDF5"] = self.patch_h5
        for label, path in required_files.items():
            if not Path(path).is_file():
                raise FileNotFoundError(f"{label} not found: {path}")
        if not Path(self.clam_root, "models", "model_clam.py").is_file():
            raise FileNotFoundError(f"CLAM source tree not found: {self.clam_root}")
        if not self.checkpoint_dir.is_dir():
            raise FileNotFoundError(
                f"CLAM checkpoint directory not found: {self.checkpoint_dir}"
            )
        if self.batch_size < 1:
            raise ValueError("batch_size must be positive")
        if not 0.0 < self.attention_percentile_threshold < 1.0:
            raise ValueError("attention_percentile_threshold must be between 0 and 1")
        if not 0.0 <= self.slide_score_gate <= 1.0:
            raise ValueError("slide_score_gate must be between 0 and 1")
        if self.min_candidate_patches < 1:
            raise ValueError("min_candidate_patches must be positive")


def _notify(
    callback: ProgressCallback | None,
    stage: str,
    fraction: float,
    message: str,
) -> None:
    fraction = max(0.0, min(1.0, float(fraction)))
    logger.info("%s %.1f%%: %s", stage, fraction * 100.0, message)
    if callback is not None:
        callback(stage, fraction, message)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _checkpoint_sort_key(path: Path) -> tuple[int, str]:
    stem_parts = path.stem.split("_")
    try:
        return int(stem_parts[1]), path.name
    except (IndexError, ValueError):
        return 10_000, path.name


def discover_checkpoints(checkpoint_dir: Path) -> list[Path]:
    checkpoints = sorted(
        checkpoint_dir.glob("s_*_checkpoint.pt"), key=_checkpoint_sort_key
    )
    if not checkpoints:
        raise FileNotFoundError(
            f"No s_*_checkpoint.pt files found in {checkpoint_dir}"
        )
    return checkpoints


def percentile_ranks(values: np.ndarray) -> np.ndarray:
    """Return stable zero-to-one ranks without assuming score calibration."""
    values = np.asarray(values, dtype=np.float64).reshape(-1)
    if values.size == 0:
        return np.empty(0, dtype=np.float32)
    if values.size == 1:
        return np.ones(1, dtype=np.float32)
    order = np.argsort(values, kind="mergesort")
    ranks = np.empty(values.size, dtype=np.float64)
    ranks[order] = np.arange(values.size, dtype=np.float64)
    return (ranks / float(values.size - 1)).astype(np.float32)


def _risk_band(score: float) -> str:
    if score >= 0.75:
        return "high_evidence"
    if score >= 0.50:
        return "intermediate_evidence"
    return "low_evidence"


def _region_evidence_tier(max_evidence: float, mean_evidence: float) -> str:
    if max_evidence >= 0.75 and mean_evidence >= 0.60:
        return "high"
    if max_evidence >= 0.55:
        return "moderate"
    return "low"


def _paint_patch_values(
    coords: np.ndarray,
    values: np.ndarray,
    canvas_shape: tuple[int, int],
    scale_xy: tuple[float, float],
    patch_size_level0: int,
) -> tuple[np.ndarray, np.ndarray]:
    height, width = canvas_shape
    scale_x, scale_y = scale_xy
    canvas = np.zeros((height, width), dtype=np.float32)
    tissue = np.zeros((height, width), dtype=np.uint8)
    for (x, y), value in zip(coords, values, strict=True):
        x0 = max(0, int(math.floor(float(x) / scale_x)))
        y0 = max(0, int(math.floor(float(y) / scale_y)))
        x1 = min(width, int(math.ceil((float(x) + patch_size_level0) / scale_x)))
        y1 = min(height, int(math.ceil((float(y) + patch_size_level0) / scale_y)))
        if x1 <= x0 or y1 <= y0:
            continue
        canvas[y0:y1, x0:x1] = np.maximum(canvas[y0:y1, x0:x1], value)
        tissue[y0:y1, x0:x1] = 1
    return canvas, tissue


def compute_candidate_regions(
    coords: np.ndarray,
    attention_percentiles: np.ndarray,
    slide_score: float,
    canvas_shape: tuple[int, int],
    scale_xy: tuple[float, float],
    patch_size_level0: int,
    mpp_xy: tuple[float | None, float | None],
    attention_threshold: float = 0.95,
    slide_score_gate: float = 0.50,
    min_candidate_patches: int = 3,
) -> tuple[dict[str, Any], np.ndarray, np.ndarray]:
    """Group high-attention patches into prototype evidence regions.

    The slide score gates candidate creation because attention ranks always have
    a top percentile, including on negative slides. Returned area values are
    patch-grid approximations, not tumor segmentation measurements.
    """
    coords = np.asarray(coords, dtype=np.int32)
    attention_percentiles = np.asarray(
        attention_percentiles, dtype=np.float32
    ).reshape(-1)
    if coords.ndim != 2 or coords.shape[1] != 2:
        raise ValueError("coords must have shape (n, 2)")
    if len(coords) != len(attention_percentiles):
        raise ValueError("coords and attention_percentiles must have equal length")

    high_values = (attention_percentiles >= attention_threshold).astype(np.float32)
    high_attention_mask, _ = _paint_patch_values(
        coords, high_values, canvas_shape, scale_xy, patch_size_level0
    )
    high_attention_mask = (high_attention_mask > 0).astype(np.uint8)

    empty_metrics = {
        "candidate_region_count": 0,
        "high_risk_region_count": 0,
        "high_attention_patch_count": int(np.count_nonzero(high_values)),
        "candidate_area_proxy_mm2": 0.0,
        "attention_percentile_threshold": float(attention_threshold),
        "slide_score_gate": float(slide_score_gate),
        "candidates": [],
    }
    if len(coords) == 0 or slide_score < slide_score_gate:
        return empty_metrics, np.zeros(canvas_shape, dtype=np.uint8), high_attention_mask

    kernel = np.ones((3, 3), dtype=np.uint8)
    connected_mask = cv2.morphologyEx(
        high_attention_mask, cv2.MORPH_CLOSE, kernel
    )
    component_count, labels, _, _ = cv2.connectedComponentsWithStats(
        connected_mask, connectivity=8
    )

    scale_x, scale_y = scale_xy
    center_x = np.clip(
        ((coords[:, 0] + patch_size_level0 / 2.0) / scale_x).astype(np.int32),
        0,
        canvas_shape[1] - 1,
    )
    center_y = np.clip(
        ((coords[:, 1] + patch_size_level0 / 2.0) / scale_y).astype(np.int32),
        0,
        canvas_shape[0] - 1,
    )
    patch_components = labels[center_y, center_x]
    patch_evidence = attention_percentiles * float(slide_score)

    mpp_x, mpp_y = mpp_xy
    patch_area_mm2: float | None = None
    if mpp_x is not None and mpp_y is not None:
        patch_area_mm2 = (
            patch_size_level0 * float(mpp_x) * patch_size_level0 * float(mpp_y)
        ) / 1_000_000.0

    candidates: list[dict[str, Any]] = []
    accepted_labels: list[int] = []
    for component_id in range(1, component_count):
        patch_indices = np.flatnonzero(
            (patch_components == component_id)
            & (attention_percentiles >= attention_threshold)
        )
        if patch_indices.size < min_candidate_patches:
            continue
        accepted_labels.append(component_id)
        component_coords = coords[patch_indices]
        component_evidence = patch_evidence[patch_indices]
        component_attention = attention_percentiles[patch_indices]
        representative_index = int(
            patch_indices[int(np.argmax(component_evidence))]
        )
        x1 = int(component_coords[:, 0].min())
        y1 = int(component_coords[:, 1].min())
        x2 = int(component_coords[:, 0].max() + patch_size_level0)
        y2 = int(component_coords[:, 1].max() + patch_size_level0)
        max_evidence = float(component_evidence.max())
        mean_evidence = float(component_evidence.mean())
        area_proxy = (
            float(patch_indices.size * patch_area_mm2)
            if patch_area_mm2 is not None
            else None
        )
        candidates.append(
            {
                "bbox_level0": [x1, y1, x2, y2],
                "centroid_level0": [
                    int(round(float(component_coords[:, 0].mean()) + patch_size_level0 / 2)),
                    int(round(float(component_coords[:, 1].mean()) + patch_size_level0 / 2)),
                ],
                "patch_count": int(patch_indices.size),
                "area_proxy_mm2": area_proxy,
                "mean_attention_percentile": float(component_attention.mean()),
                "max_attention_percentile": float(component_attention.max()),
                "mean_evidence_index": mean_evidence,
                "max_evidence_index": max_evidence,
                "evidence_tier": _region_evidence_tier(max_evidence, mean_evidence),
                "representative_patch_index": representative_index,
            }
        )

    candidates.sort(
        key=lambda item: (
            item["max_evidence_index"],
            item["mean_evidence_index"],
            item["patch_count"],
        ),
        reverse=True,
    )
    for index, candidate in enumerate(candidates, start=1):
        candidate["region_id"] = f"R{index:02d}"

    candidate_mask = np.isin(labels, accepted_labels).astype(np.uint8)
    total_area = sum(
        float(candidate["area_proxy_mm2"] or 0.0) for candidate in candidates
    )
    metrics = {
        "candidate_region_count": len(candidates),
        "high_risk_region_count": sum(
            candidate["evidence_tier"] == "high" for candidate in candidates
        ),
        "high_attention_patch_count": int(np.count_nonzero(high_values)),
        "candidate_area_proxy_mm2": total_area,
        "attention_percentile_threshold": float(attention_threshold),
        "slide_score_gate": float(slide_score_gate),
        "candidates": candidates,
    }
    return metrics, candidate_mask, high_attention_mask


def parse_annotation_mask(
    xml_path: Path,
    canvas_shape: tuple[int, int],
    scale_xy: tuple[float, float],
) -> np.ndarray:
    mask = np.zeros(canvas_shape, dtype=np.uint8)
    root = ET.parse(xml_path).getroot()
    polygons: list[np.ndarray] = []
    scale_x, scale_y = scale_xy
    for annotation in root.findall(".//Annotation"):
        coordinates = list(annotation.findall(".//Coordinate"))
        coordinates.sort(key=lambda node: float(node.attrib.get("Order", "0")))
        points = []
        for coordinate in coordinates:
            try:
                x = int(round(float(coordinate.attrib["X"]) / scale_x))
                y = int(round(float(coordinate.attrib["Y"]) / scale_y))
            except (KeyError, ValueError):
                continue
            points.append([x, y])
        if len(points) >= 3:
            polygons.append(np.asarray(points, dtype=np.int32).reshape(-1, 1, 2))
    if polygons:
        cv2.fillPoly(mask, polygons, 1)
    return mask


def compute_annotation_overlap(
    annotation_mask: np.ndarray,
    high_attention_mask: np.ndarray,
    candidate_mask: np.ndarray,
) -> dict[str, Any]:
    annotation = np.asarray(annotation_mask, dtype=bool)
    high_attention = np.asarray(high_attention_mask, dtype=bool)
    candidates = np.asarray(candidate_mask, dtype=bool)
    annotation_pixels = int(annotation.sum())
    attention_pixels = int(high_attention.sum())
    candidate_pixels = int(candidates.sum())
    attention_intersection = int(np.logical_and(annotation, high_attention).sum())
    candidate_intersection = int(np.logical_and(annotation, candidates).sum())
    return {
        "available": True,
        "high_attention_overlaps_annotation": attention_intersection > 0,
        "candidate_regions_overlap_annotation": candidate_intersection > 0,
        "high_attention_intersection_pixels": attention_intersection,
        "candidate_intersection_pixels": candidate_intersection,
        "high_attention_precision_proxy": (
            attention_intersection / attention_pixels if attention_pixels else 0.0
        ),
        "annotation_coverage_proxy": (
            attention_intersection / annotation_pixels if annotation_pixels else 0.0
        ),
        "candidate_precision_proxy": (
            candidate_intersection / candidate_pixels if candidate_pixels else 0.0
        ),
        "notice": (
            "Compatibility check against the public CAMELYON annotation; "
            "not a segmentation or clinical performance metric."
        ),
    }


def _ensure_openslide_runtime() -> None:
    global _OPENSLIDE_DLL_HANDLE
    if os.name != "nt":
        return
    if _OPENSLIDE_DLL_HANDLE is not None:
        importlib.import_module("openslide")
        return
    dll_candidates: list[Path] = []
    try:
        import openslide_bin  # type: ignore
    except ImportError:
        pass
    else:
        dll_candidates.append(Path(openslide_bin.__file__).resolve().parent)
    dll_candidates.append(Path(sys.prefix) / "Library" / "bin")
    dll_dir = next(
        (
            candidate
            for candidate in dll_candidates
            if (candidate / "libopenslide-1.dll").is_file()
            or (candidate / "libopenslide-0.dll").is_file()
        ),
        None,
    )
    if dll_dir is None:
        raise RuntimeError(
            "OpenSlide DLL not found in openslide-bin or the active Conda environment"
        )
    _OPENSLIDE_DLL_HANDLE = os.add_dll_directory(str(dll_dir))
    # Load OpenSlide before torchvision can load a conflicting TIFF DLL.
    importlib.import_module("openslide")


def _configure_external_clam(clam_root: Path, output_dir: Path) -> None:
    _ensure_openslide_runtime()
    matplotlib_dir = output_dir / ".matplotlib"
    matplotlib_dir.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("MPLCONFIGDIR", str(matplotlib_dir))
    clam_root_text = str(clam_root.resolve())
    if clam_root_text not in sys.path:
        sys.path.insert(0, clam_root_text)


def _prepare_patch_coordinates(
    config: ClamAnalysisConfig,
    callback: ProgressCallback | None,
) -> Path:
    if config.patch_h5 is not None:
        _notify(callback, "preprocess", 1.0, "Using supplied patch coordinate cache")
        return config.patch_h5

    cache_root = config.cache_dir or config.output_dir
    patches_dir = cache_root / "preprocess" / "patches"
    masks_dir = cache_root / "preprocess" / "masks"
    patches_dir.mkdir(parents=True, exist_ok=True)
    masks_dir.mkdir(parents=True, exist_ok=True)
    expected_h5 = patches_dir / f"{config.slide_path.stem}.h5"
    if expected_h5.is_file():
        _notify(callback, "preprocess", 1.0, "Using cached tissue patch coordinates")
        return expected_h5

    _configure_external_clam(config.clam_root, config.output_dir)
    from wsi_core.WholeSlideImage import WholeSlideImage

    _notify(callback, "preprocess", 0.05, "Opening WSI and detecting tissue")
    wsi = WholeSlideImage(str(config.slide_path))
    try:
        seg_level = wsi.wsi.get_best_level_for_downsample(64)
        wsi.segmentTissue(
            seg_level=seg_level,
            sthresh=8,
            mthresh=7,
            close=4,
            use_otsu=False,
            filter_params={"a_t": 100, "a_h": 16, "max_n_holes": 8},
            keep_ids=[],
            exclude_ids=[],
        )
        if not wsi.contours_tissue:
            raise RuntimeError("Tissue detection produced no usable contours")
        wsi.saveSegmentation(
            str(masks_dir / f"{config.slide_path.stem}_segmentation.pkl")
        )
        mask = wsi.visWSI(vis_level=seg_level, line_thickness=250)
        mask.save(masks_dir / f"{config.slide_path.stem}_tissue.png")
        _notify(callback, "preprocess", 0.35, "Enumerating tissue patch coordinates")
        wsi.process_contours(
            save_path=str(patches_dir),
            patch_level=config.patch_level,
            patch_size=config.patch_size,
            step_size=config.step_size,
            use_padding=True,
            contour_fn="four_pt",
        )
    finally:
        wsi.wsi.close()

    if not expected_h5.is_file():
        raise RuntimeError(f"CLAM did not create patch coordinates: {expected_h5}")
    _notify(callback, "preprocess", 1.0, "Tissue patch coordinates ready")
    return expected_h5


def _torch_load(
    path: Path,
    map_location: str = "cpu",
    allow_trusted_legacy_archive: bool = False,
) -> Any:
    import torch

    try:
        return torch.load(path, map_location=map_location, weights_only=True)
    except TypeError:
        return torch.load(path, map_location=map_location)
    except RuntimeError as exc:
        if not (
            allow_trusted_legacy_archive
            and "legacy .tar format" in str(exc)
        ):
            raise
        # Official resnet50-19c8e357.pth predates weights-only loading.
        return torch.load(path, map_location=map_location, weights_only=False)


def _resolve_device(requested: str) -> str:
    import torch

    if requested == "auto":
        return "cuda" if torch.cuda.is_available() else "cpu"
    if requested.startswith("cuda") and not torch.cuda.is_available():
        raise RuntimeError("CUDA was requested but torch.cuda.is_available() is false")
    return requested


def _build_feature_encoder(config: ClamAnalysisConfig, device: str):
    import torch

    _configure_external_clam(config.clam_root, config.output_dir)
    from models.resnet_custom_dep import resnet50_baseline

    model = resnet50_baseline(pretrained=False)
    state = _torch_load(
        config.resnet_weights, allow_trusted_legacy_archive=True
    )
    incompatible = model.load_state_dict(state, strict=False)
    allowed_unexpected = {
        key
        for key in incompatible.unexpected_keys
        if key.startswith("layer4.") or key.startswith("fc.")
    }
    unexpected = set(incompatible.unexpected_keys) - allowed_unexpected
    if incompatible.missing_keys or unexpected:
        raise RuntimeError(
            "ResNet-50 weights do not match the legacy CLAM encoder: "
            f"missing={incompatible.missing_keys}, unexpected={sorted(unexpected)}"
        )
    model.eval().to(device)
    if device.startswith("cuda"):
        torch.backends.cudnn.benchmark = True
    return model


def _extract_features(
    config: ClamAnalysisConfig,
    patch_h5: Path,
    device: str,
    callback: ProgressCallback | None,
) -> Path:
    import torch
    from torchvision import transforms

    cache_root = config.cache_dir or config.output_dir
    feature_dir = cache_root / "features"
    feature_dir.mkdir(parents=True, exist_ok=True)
    feature_path = feature_dir / f"{config.slide_path.stem}.h5"
    if feature_path.is_file():
        with h5py.File(feature_path, "r") as cached:
            if bool(cached.attrs.get("complete", False)):
                _notify(callback, "features", 1.0, "Using cached ResNet-50 features")
                return feature_path

    with h5py.File(patch_h5, "r") as source:
        coords = source["coords"][:].astype(np.int32)
        patch_level = int(source["coords"].attrs.get("patch_level", config.patch_level))
        patch_size = int(source["coords"].attrs.get("patch_size", config.patch_size))
    if len(coords) == 0:
        raise RuntimeError("Patch coordinate file is empty")

    encoder = _build_feature_encoder(config, device)
    transform = transforms.Compose(
        [
            transforms.Resize((config.target_patch_size, config.target_patch_size)),
            transforms.ToTensor(),
            transforms.Normalize(
                mean=[0.485, 0.456, 0.406],
                std=[0.229, 0.224, 0.225],
            ),
        ]
    )
    from openslide import OpenSlide

    partial_path = feature_path.with_suffix(".partial.h5")
    _notify(callback, "features", 0.01, f"Extracting {len(coords):,} patch features")
    slide = OpenSlide(str(config.slide_path))
    try:
        with h5py.File(partial_path, "w") as output:
            feature_dataset = output.create_dataset(
                "features",
                shape=(len(coords), 1024),
                dtype=np.float32,
                chunks=(min(config.batch_size, len(coords)), 1024),
                compression="lzf",
            )
            output.create_dataset("coords", data=coords, compression="lzf")
            output.attrs["complete"] = False
            output.attrs["patch_level"] = patch_level
            output.attrs["patch_size"] = patch_size
            for start in range(0, len(coords), config.batch_size):
                end = min(start + config.batch_size, len(coords))
                images = [
                    transform(
                        slide.read_region(
                            (int(x), int(y)), patch_level, (patch_size, patch_size)
                        ).convert("RGB")
                    )
                    for x, y in coords[start:end]
                ]
                batch = torch.stack(images).to(device, non_blocking=True)
                with torch.inference_mode():
                    features = encoder(batch)
                feature_dataset[start:end] = (
                    features.detach().float().cpu().numpy().astype(np.float32)
                )
                if start == 0 or end == len(coords) or end % (config.batch_size * 20) == 0:
                    _notify(
                        callback,
                        "features",
                        end / len(coords),
                        f"Encoded {end:,}/{len(coords):,} tissue patches",
                    )
            output.attrs["complete"] = True
            output.flush()
    finally:
        slide.close()
        del encoder
        if device.startswith("cuda"):
            torch.cuda.empty_cache()
    os.replace(partial_path, feature_path)
    return feature_path


def _load_clam_checkpoint(model: Any, checkpoint: Path) -> None:
    state = _torch_load(checkpoint)
    if isinstance(state, dict) and "state_dict" in state:
        state = state["state_dict"]
    cleaned = {}
    for key, value in state.items():
        clean_key = key.replace(".module", "")
        if clean_key.startswith("instance_loss_fn"):
            continue
        cleaned[clean_key] = value
    model.load_state_dict(cleaned, strict=True)


def _run_fold_ensemble(
    config: ClamAnalysisConfig,
    feature_path: Path,
    device: str,
    callback: ProgressCallback | None,
) -> tuple[np.ndarray, list[float], list[Path]]:
    import torch

    _configure_external_clam(config.clam_root, config.output_dir)
    from models.model_clam import CLAM_MB

    checkpoints = discover_checkpoints(config.checkpoint_dir)
    with h5py.File(feature_path, "r") as source:
        feature_array = source["features"][:].astype(np.float32)
    features = torch.from_numpy(feature_array).to(device)
    del feature_array

    fold_scores: list[float] = []
    fold_attention: list[np.ndarray] = []
    _notify(callback, "clam", 0.02, f"Running {len(checkpoints)} CLAM folds")
    with torch.inference_mode():
        for index, checkpoint in enumerate(checkpoints, start=1):
            model = CLAM_MB(
                gate=True,
                size_arg="small",
                dropout=0.25,
                n_classes=2,
                embed_dim=1024,
            )
            _load_clam_checkpoint(model, checkpoint)
            model.eval().to(device)
            _, probabilities, _, raw_attention, _ = model(features)
            score = float(probabilities[0, 1].detach().cpu())
            tumor_attention = raw_attention[1].detach().float().cpu().numpy()
            fold_scores.append(score)
            fold_attention.append(percentile_ranks(tumor_attention))
            del model, probabilities, raw_attention
            _notify(
                callback,
                "clam",
                index / len(checkpoints),
                f"Fold {index}/{len(checkpoints)} evidence score: {score:.4f}",
            )
    del features
    if device.startswith("cuda"):
        torch.cuda.empty_cache()
    mean_attention = np.mean(np.stack(fold_attention, axis=0), axis=0).astype(
        np.float32
    )
    return mean_attention, fold_scores, checkpoints


def _slide_metadata(slide_path: Path) -> tuple[dict[str, Any], int, tuple[float, float]]:
    _ensure_openslide_runtime()
    from openslide import OpenSlide

    slide = OpenSlide(str(slide_path))
    try:
        width, height = slide.dimensions
        target_level = slide.get_best_level_for_downsample(64)
        render_width, render_height = slide.level_dimensions[target_level]
        scale_xy = (width / render_width, height / render_height)
        mpp_x_raw = slide.properties.get("openslide.mpp-x")
        mpp_y_raw = slide.properties.get("openslide.mpp-y")
        mpp_x = float(mpp_x_raw) if mpp_x_raw else None
        mpp_y = float(mpp_y_raw) if mpp_y_raw else None
        metadata = {
            "file_name": slide_path.name,
            "dimensions_level0": [width, height],
            "level_count": slide.level_count,
            "render_level": target_level,
            "render_dimensions": [render_width, render_height],
            "mpp": [mpp_x, mpp_y],
            "vendor": slide.properties.get("openslide.vendor"),
        }
        return metadata, target_level, scale_xy
    finally:
        slide.close()


def _select_evidence_patch_indices(
    coords: np.ndarray,
    evidence: np.ndarray,
    candidates: Sequence[dict[str, Any]],
    limit: int = 3,
) -> list[int]:
    selected: list[int] = []
    for candidate in candidates:
        patch_index = int(candidate["representative_patch_index"])
        if patch_index not in selected:
            selected.append(patch_index)
        if len(selected) == limit:
            return selected
    minimum_distance_squared = float((512) ** 2)
    for patch_index in np.argsort(evidence)[::-1]:
        index = int(patch_index)
        point = coords[index].astype(np.float64)
        if all(
            float(np.sum((point - coords[existing]) ** 2))
            >= minimum_distance_squared
            for existing in selected
        ):
            selected.append(index)
        if len(selected) == limit:
            break
    return selected


def _render_artifacts(
    config: ClamAnalysisConfig,
    coords: np.ndarray,
    attention: np.ndarray,
    slide_score: float,
    metadata: dict[str, Any],
    render_level: int,
    scale_xy: tuple[float, float],
    candidate_metrics: dict[str, Any],
    candidate_mask: np.ndarray,
    annotation_mask: np.ndarray | None,
) -> tuple[dict[str, str], list[dict[str, Any]]]:
    _ensure_openslide_runtime()
    from openslide import OpenSlide

    artifact_dir = config.output_dir / "artifacts"
    artifact_dir.mkdir(parents=True, exist_ok=True)
    render_width, render_height = metadata["render_dimensions"]
    patch_level0 = int(
        round(config.patch_size * (2 ** config.patch_level))
    )
    attention_canvas, tissue_mask = _paint_patch_values(
        coords,
        attention,
        (render_height, render_width),
        scale_xy,
        patch_level0,
    )

    slide = OpenSlide(str(config.slide_path))
    try:
        base = np.asarray(
            slide.read_region(
                (0, 0), render_level, (render_width, render_height)
            ).convert("RGB"),
            dtype=np.uint8,
        )
        evidence_canvas = attention_canvas * float(slide_score)

        def blend_heatmap(values: np.ndarray, alpha: np.ndarray) -> np.ndarray:
            heat_bgr = cv2.applyColorMap(
                np.clip(values * 255.0, 0, 255).astype(np.uint8),
                cv2.COLORMAP_TURBO,
            )
            heat_rgb = cv2.cvtColor(heat_bgr, cv2.COLOR_BGR2RGB)
            return np.clip(
                base.astype(np.float32) * (1.0 - alpha[..., None])
                + heat_rgb.astype(np.float32) * alpha[..., None],
                0,
                255,
            ).astype(np.uint8)

        relative_alpha = np.where(
            tissue_mask > 0,
            0.08 + 0.47 * np.square(attention_canvas),
            0.0,
        ).astype(np.float32)
        evidence_alpha = np.where(
            tissue_mask > 0,
            0.02 + 0.68 * np.square(evidence_canvas),
            0.0,
        ).astype(np.float32)
        relative_overlay = blend_heatmap(attention_canvas, relative_alpha)
        evidence_overlay = blend_heatmap(evidence_canvas, evidence_alpha)

        if annotation_mask is not None:
            contours, _ = cv2.findContours(
                annotation_mask.astype(np.uint8),
                cv2.RETR_EXTERNAL,
                cv2.CHAIN_APPROX_SIMPLE,
            )
            cv2.drawContours(relative_overlay, contours, -1, (27, 150, 96), 3)
            cv2.drawContours(evidence_overlay, contours, -1, (27, 150, 96), 3)

        relative_path = artifact_dir / "relative_attention_overview.png"
        Image.fromarray(relative_overlay).save(relative_path, optimize=True)

        overview = Image.fromarray(evidence_overlay)
        draw = ImageDraw.Draw(overview)
        for candidate in candidate_metrics["candidates"][:12]:
            x1, y1, x2, y2 = candidate["bbox_level0"]
            box = [
                int(round(x1 / scale_xy[0])),
                int(round(y1 / scale_xy[1])),
                int(round(x2 / scale_xy[0])),
                int(round(y2 / scale_xy[1])),
            ]
            color = (185, 54, 45) if candidate["evidence_tier"] == "high" else (211, 125, 31)
            draw.rectangle(box, outline=color, width=3)
            draw.text((box[0] + 4, box[1] + 4), candidate["region_id"], fill=color)

        overview_path = artifact_dir / "model_evidence_overview.png"
        overview.save(overview_path, optimize=True)
        attention_path = artifact_dir / "attention_map.npz"
        np.savez_compressed(
            attention_path,
            attention=attention_canvas,
            evidence=evidence_canvas,
            tissue_mask=tissue_mask,
            candidate_mask=candidate_mask,
        )

        evidence = attention * float(slide_score)
        selected_indices = _select_evidence_patch_indices(
            coords, evidence, candidate_metrics["candidates"], limit=3
        )
        patch_artifacts: list[dict[str, Any]] = []
        width, height = metadata["dimensions_level0"]
        for ordinal, patch_index in enumerate(selected_indices, start=1):
            x, y = (int(value) for value in coords[patch_index])
            context_size = 512
            context_x = max(0, min(width - context_size, x - 128))
            context_y = max(0, min(height - context_size, y - 128))
            context = slide.read_region(
                (context_x, context_y), 0, (context_size, context_size)
            ).convert("RGB")
            context_draw = ImageDraw.Draw(context)
            local_box = [
                x - context_x,
                y - context_y,
                x - context_x + config.patch_size,
                y - context_y + config.patch_size,
            ]
            context_draw.rectangle(local_box, outline=(205, 86, 45), width=4)
            patch_path = artifact_dir / f"evidence_patch_{ordinal:02d}.png"
            context.save(patch_path, optimize=True)
            patch_artifacts.append(
                {
                    "rank": ordinal,
                    "path": patch_path.relative_to(config.output_dir).as_posix(),
                    "coordinate_level0": [x, y],
                    "attention_percentile": float(attention[patch_index]),
                    "evidence_index": float(evidence[patch_index]),
                }
            )
    finally:
        slide.close()

    return (
        {
            "model_evidence_overview": overview_path.relative_to(
                config.output_dir
            ).as_posix(),
            "relative_attention_overview": relative_path.relative_to(
                config.output_dir
            ).as_posix(),
            "attention_data": attention_path.relative_to(
                config.output_dir
            ).as_posix(),
        },
        patch_artifacts,
    )


def _round_result(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: _round_result(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_round_result(item) for item in value]
    if isinstance(value, tuple):
        return [_round_result(item) for item in value]
    if isinstance(value, (np.floating, float)):
        return round(float(value), 6)
    if isinstance(value, np.integer):
        return int(value)
    return value


def analyze_wsi(
    config: ClamAnalysisConfig,
    progress: ProgressCallback | None = None,
) -> dict[str, Any]:
    """Run the complete cached WSI-to-evidence analysis pipeline."""
    config.validate()
    config.output_dir.mkdir(parents=True, exist_ok=True)
    _ensure_openslide_runtime()
    started = time.perf_counter()
    timings: dict[str, float] = {}
    device = _resolve_device(config.device)

    stage_started = time.perf_counter()
    patch_h5 = _prepare_patch_coordinates(config, progress)
    timings["preprocess"] = time.perf_counter() - stage_started

    stage_started = time.perf_counter()
    feature_path = _extract_features(config, patch_h5, device, progress)
    timings["feature_extraction"] = time.perf_counter() - stage_started

    stage_started = time.perf_counter()
    attention, fold_scores, checkpoints = _run_fold_ensemble(
        config, feature_path, device, progress
    )
    timings["clam_ensemble"] = time.perf_counter() - stage_started

    with h5py.File(feature_path, "r") as source:
        coords = source["coords"][:].astype(np.int32)
        source_patch_size = int(source.attrs.get("patch_size", config.patch_size))
        source_patch_level = int(source.attrs.get("patch_level", config.patch_level))
    patch_size_level0 = int(round(source_patch_size * (2 ** source_patch_level)))
    slide_score = float(np.mean(fold_scores))
    metadata, render_level, scale_xy = _slide_metadata(config.slide_path)
    render_width, render_height = metadata["render_dimensions"]
    mpp = tuple(metadata["mpp"])

    candidate_metrics, candidate_mask, high_attention_mask = compute_candidate_regions(
        coords=coords,
        attention_percentiles=attention,
        slide_score=slide_score,
        canvas_shape=(render_height, render_width),
        scale_xy=scale_xy,
        patch_size_level0=patch_size_level0,
        mpp_xy=mpp,
        attention_threshold=config.attention_percentile_threshold,
        slide_score_gate=config.slide_score_gate,
        min_candidate_patches=config.min_candidate_patches,
    )

    annotation_mask = None
    annotation_overlap: dict[str, Any] = {"available": False}
    if config.annotation_xml is not None:
        annotation_mask = parse_annotation_mask(
            config.annotation_xml,
            (render_height, render_width),
            scale_xy,
        )
        annotation_overlap = compute_annotation_overlap(
            annotation_mask, high_attention_mask, candidate_mask
        )

    stage_started = time.perf_counter()
    artifacts, evidence_patches = _render_artifacts(
        config=config,
        coords=coords,
        attention=attention,
        slide_score=slide_score,
        metadata=metadata,
        render_level=render_level,
        scale_xy=scale_xy,
        candidate_metrics=candidate_metrics,
        candidate_mask=candidate_mask,
        annotation_mask=annotation_mask,
    )
    timings["artifact_rendering"] = time.perf_counter() - stage_started
    score_path = config.output_dir / "patch_evidence.npz"
    np.savez_compressed(
        score_path,
        coords=coords,
        attention_percentile=attention,
        evidence_index=attention * slide_score,
    )
    artifacts["patch_evidence"] = score_path.relative_to(
        config.output_dir
    ).as_posix()
    artifacts["evidence_patches"] = [item["path"] for item in evidence_patches]

    timings["total"] = time.perf_counter() - started
    result = {
        "schema_version": SCHEMA_VERSION,
        "status": "succeeded",
        "created_at": datetime.now(timezone.utc).isoformat(),
        "intended_use": {
            "mode": "research_prototype",
            "clinical_use": False,
            "notice": (
                "Decision-support demonstration only. CLAM attention is not a "
                "calibrated lesion probability or tumor segmentation. A clinician "
                "must review all evidence and make the final decision."
            ),
        },
        "slide": {
            **metadata,
            "patch_count": int(len(coords)),
            "patch_size_level0": patch_size_level0,
        },
        "model": {
            "name": CLAM_MODEL_NAME,
            "architecture": "CLAM_MB",
            "feature_encoder": FEATURE_ENCODER_NAME,
            "fold_count": len(checkpoints),
            "checkpoints": [checkpoint.name for checkpoint in checkpoints],
            "checkpoint_sha256": {
                checkpoint.name: _sha256(checkpoint) for checkpoint in checkpoints
            },
            "resnet_weights": config.resnet_weights.name,
            "resnet_weights_sha256": _sha256(config.resnet_weights),
            "aggregation": (
                "Mean tumor-class slide score and mean within-fold tumor-attention "
                "percentile across folds."
            ),
            "validation_scope": (
                "Source-domain compatibility demonstration; downloaded checkpoint "
                "split metadata was not supplied, so this is not held-out validation."
            ),
        },
        "inference": {
            "predicted_class": (
                "tumor_associated_evidence"
                if slide_score >= config.slide_score_gate
                else "no_tumor_associated_evidence"
            ),
            "tumor_evidence_score": slide_score,
            "evidence_band": _risk_band(slide_score),
            "fold_scores": fold_scores,
            "ensemble_disagreement": float(np.std(fold_scores)),
            "score_notice": (
                "Softmax-derived model evidence score; not a calibrated clinical probability."
            ),
        },
        "region_metrics": {
            **candidate_metrics,
            "area_notice": (
                "Candidate area is a patch-grid approximation and is not clinical tumor area."
            ),
            "risk_notice": (
                "High-risk count is a prototype evidence tier, not a diagnosis."
            ),
        },
        "annotation_overlap": annotation_overlap,
        "evidence_patches": evidence_patches,
        "artifacts": artifacts,
        "runtime": {
            "device": device,
            "timings_seconds": timings,
        },
        "configuration": {
            key: str(value) if isinstance(value, Path) else value
            for key, value in asdict(config).items()
            if key
            not in {
                "slide_path",
                "output_dir",
                "cache_dir",
                "clam_root",
                "checkpoint_dir",
                "resnet_weights",
            }
        },
    }
    result = _round_result(result)
    result_path = config.output_dir / "result.json"
    result_path.write_text(
        json.dumps(result, indent=2, ensure_ascii=True), encoding="utf-8"
    )
    _notify(progress, "complete", 1.0, f"Analysis saved to {result_path}")
    return result
