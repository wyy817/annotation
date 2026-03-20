"""
Internal API routes — called only by Spring Boot backend.
Phase 2: DeepZoom tile generation, semantic mask rendering, patch extraction.
Phase 3: SAM-assisted segmentation prediction.

Annotation coordinate conventions (matching Annotation.java):
  rect:    coordinates = [[x1,y1],[x2,y2]]   (top-left, bottom-right)
  polygon: coordinates = [[x1,y1],[x2,y2],...]
  brush:   coordinates = [{"points":[[x,y],...],"size":20}, ...]

Storage layout (all absolute paths under STORAGE_ROOT env var):
  tiles/{slide_id}/slide.dzi
  tiles/{slide_id}/slide_files/{level}/{col}_{row}.jpg
  thumbnails/{slide_id}.jpg
  masks/{slide_id}_mask.png
  patches/{slide_id}/{label_name}_patch_{nnnn}.png

SAM env vars:
  SAM_CHECKPOINT   — path to model weights (required for /sam/predict)
  SAM_MODEL_TYPE   — "vit_t" (MobileSAM, default) / "vit_b" / "vit_l" / "vit_h"
  SAM_PATCH_SIZE   — pixel size of extracted region fed to SAM (default 1024)
"""

import base64
import math
import os
import logging
import threading
from pathlib import Path
from typing import Any

# On Windows, load openslide-bin DLLs before any openslide import to avoid
# libtiff.dll ordinal conflicts with the conda environment's tiff.dll.
if os.name == "nt":
    try:
        import openslide
        if hasattr(openslide, "_load_library"):
            openslide._load_library()
    except Exception:
        pass
    try:
        import openslide_bin  # type: ignore
        os.add_dll_directory(str(Path(openslide_bin.__file__).parent))
    except Exception:
        pass

import cv2
import numpy as np
from PIL import Image
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

logger = logging.getLogger(__name__)
router = APIRouter()

STORAGE_ROOT = Path(os.environ.get("STORAGE_ROOT", "./storage")).resolve()


# ── helpers ──────────────────────────────────────────────────────────────────

def _mkdir(p: Path) -> Path:
    p.mkdir(parents=True, exist_ok=True)
    return p


def _tile_out_dir(slide_id: int) -> Path:
    return _mkdir(STORAGE_ROOT / "tiles" / str(slide_id))


def _thumbnail_path(slide_id: int) -> Path:
    _mkdir(STORAGE_ROOT / "thumbnails")
    return STORAGE_ROOT / "thumbnails" / f"{slide_id}.jpg"


def _mask_path(slide_id: int) -> Path:
    _mkdir(STORAGE_ROOT / "masks")
    return STORAGE_ROOT / "masks" / f"{slide_id}_mask.png"


def _patch_out_dir(slide_id: int) -> Path:
    return _mkdir(STORAGE_ROOT / "patches" / str(slide_id))


def _write_dzi(out_dir: Path, width: int, height: int,
               tile_size: int, overlap: int) -> None:
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<Image TileSize="{tile_size}" Overlap="{overlap}" Format="jpg"\n'
        '       xmlns="http://schemas.microsoft.com/deepzoom/2008">\n'
        f'    <Size Width="{width}" Height="{height}" />\n'
        '</Image>\n'
    )
    (out_dir / "slide.dzi").write_text(xml, encoding="utf-8")


# ── Request / Response models ─────────────────────────────────────────────────

class ProcessSlideRequest(BaseModel):
    slide_id: int
    file_path: str
    tile_size: int = 256


class ProcessSlideResponse(BaseModel):
    slide_id: int
    tile_dir: str
    width: int
    height: int
    max_zoom: int
    thumbnail_path: str


class GenerateMaskRequest(BaseModel):
    slide_id: int
    width: int
    height: int
    annotations: list[dict[str, Any]]
    mask_type: str = "SEMANTIC"   # SEMANTIC | COLOR | INSTANCE
    mask_scale: int = 1           # downsample factor (1/2/4/8)


class GenerateMaskResponse(BaseModel):
    slide_id: int
    mask_path: str


class ExtractPatchesRequest(BaseModel):
    slide_id: int
    file_path: str
    annotations: list[dict[str, Any]]
    patch_size: int = 256


class ExtractPatchesResponse(BaseModel):
    slide_id: int
    patch_dir: str
    patch_count: int


class SamPredictRequest(BaseModel):
    slide_id: int
    file_path: str
    level: int = 0                     # WSI pyramid level (0 = full resolution)
    points: list[list[float]] = []     # [[x,y], ...] in level-0 WSI global coords
    point_labels: list[int] = []       # 1=foreground, 0=background (per point)
    box: list[float] | None = None     # [x1,y1,x2,y2] in level-0 WSI coords
    patch_size: int = 1024             # pixel size of region extracted for SAM


class SamPredictResponse(BaseModel):
    polygons: list[list[list[float]]]  # contours in level-0 WSI coords: [[[x,y],...],...]
    score: float                       # confidence score of the best mask
    mask_b64: str                      # base64 PNG of the best mask (patch coords)


# ── SAM lazy loader ───────────────────────────────────────────────────────────

_SAM_MODEL_TYPE = os.environ.get("SAM_MODEL_TYPE", "vit_t")
_SAM_PATCH_SIZE = int(os.environ.get("SAM_PATCH_SIZE", "1024"))

_sam_predictor = None
_sam_lock = threading.Lock()
_sam_infer_lock = threading.Lock()  # SAM predictor is stateful; serialise per-request


def _get_sam_predictor():
    """Lazily load MobileSAM (or SAM) predictor. Thread-safe."""
    global _sam_predictor
    if _sam_predictor is not None:
        return _sam_predictor

    with _sam_lock:
        if _sam_predictor is not None:
            return _sam_predictor

        checkpoint = os.environ.get("SAM_CHECKPOINT", "")
        logger.info("SAM_CHECKPOINT resolved to: %r", checkpoint)
        if not checkpoint:
            raise HTTPException(status_code=503,
                                detail="SAM_CHECKPOINT env var is not set")
        if not Path(checkpoint).exists():
            raise HTTPException(status_code=503,
                                detail=f"SAM checkpoint not found: {checkpoint}")

        try:
            import torch
            try:
                from mobile_sam import sam_model_registry, SamPredictor
            except ImportError:
                from segment_anything import sam_model_registry, SamPredictor

            device = "cuda" if torch.cuda.is_available() else "cpu"
            sam = sam_model_registry[_SAM_MODEL_TYPE](checkpoint=checkpoint)
            sam.to(device=device)
            sam.eval()
            _sam_predictor = SamPredictor(sam)
            logger.info("SAM predictor loaded (type=%s, device=%s)", _SAM_MODEL_TYPE, device)
        except HTTPException:
            raise
        except Exception as e:
            raise HTTPException(status_code=503, detail=f"Failed to load SAM: {e}")

    return _sam_predictor


# ── Supported formats ─────────────────────────────────────────────────────────

# Extensions handled by OpenSlide (multi-resolution WSI formats)
OPENSLIDE_EXTS = {".svs", ".ndpi", ".tiff", ".tif", ".mrxs", ".scn", ".bif", ".vms", ".vmu"}

# Extensions handled by Pillow (plain raster images)
STANDARD_EXTS = {".png", ".jpg", ".jpeg"}


# ── Tile generation ───────────────────────────────────────────────────────────

def _gen_tiles_openslide(file_path: str, slide_id: int, tile_size: int):
    """DeepZoom tiles for any OpenSlide-supported WSI format."""
    from openslide import OpenSlide
    from openslide.deepzoom import DeepZoomGenerator

    slide = OpenSlide(file_path)
    try:
        width, height = slide.dimensions
        dz = DeepZoomGenerator(slide, tile_size=tile_size, overlap=1)
        max_zoom = dz.level_count - 1

        out_dir = _tile_out_dir(slide_id)
        files_dir = out_dir / "slide_files"

        for level in range(dz.level_count):
            level_dir = _mkdir(files_dir / str(level))
            cols, rows = dz.level_tiles[level]
            for col in range(cols):
                for row in range(rows):
                    tile = dz.get_tile(level, (col, row))
                    tile.save(str(level_dir / f"{col}_{row}.jpg"), "JPEG", quality=85)

        _write_dzi(out_dir, width, height, tile_size, overlap=1)

        thumb = slide.get_thumbnail((512, 512))
        thumb_path = _thumbnail_path(slide_id)
        thumb.save(str(thumb_path), "JPEG", quality=85)

        return width, height, max_zoom, str(out_dir), str(thumb_path)
    finally:
        slide.close()


def _gen_tiles_standard(file_path: str, slide_id: int, tile_size: int):
    """DeepZoom tiles for PNG/JPG via Pillow (overlap=0)."""
    img = Image.open(file_path).convert("RGB")
    width, height = img.size

    # DZI level numbering: level max_zoom = full resolution
    max_dim = max(width, height)
    max_zoom = math.ceil(math.log2(max_dim)) if max_dim > 1 else 0

    out_dir = _tile_out_dir(slide_id)
    files_dir = out_dir / "slide_files"

    for level in range(max_zoom + 1):
        divisor = 2 ** (max_zoom - level)
        lw = max(1, math.ceil(width / divisor))
        lh = max(1, math.ceil(height / divisor))
        level_img = img if level == max_zoom else img.resize((lw, lh), Image.LANCZOS)

        level_dir = _mkdir(files_dir / str(level))
        cols = max(1, math.ceil(lw / tile_size))
        rows = max(1, math.ceil(lh / tile_size))

        for col in range(cols):
            for row in range(rows):
                tile = level_img.crop((
                    col * tile_size,
                    row * tile_size,
                    min((col + 1) * tile_size, lw),
                    min((row + 1) * tile_size, lh),
                ))
                tile.save(str(level_dir / f"{col}_{row}.jpg"), "JPEG", quality=85)

    _write_dzi(out_dir, width, height, tile_size, overlap=0)

    thumb = img.copy()
    thumb.thumbnail((512, 512), Image.LANCZOS)
    thumb_path = _thumbnail_path(slide_id)
    thumb.save(str(thumb_path), "JPEG", quality=85)

    return width, height, max_zoom, str(out_dir), str(thumb_path)


# ── Mask rendering ────────────────────────────────────────────────────────────

def _draw_annotation(mask: np.ndarray, ann: dict) -> None:
    """Paint one annotation onto the mask with its class index."""
    ann_type = ann.get("type", "polygon")
    class_idx = int(ann.get("label_class_index", 1))
    coords = ann.get("coordinates", [])

    if ann_type == "rect":
        # coords = [[x1,y1],[x2,y2]]
        if len(coords) < 2:
            return
        x1, y1 = int(coords[0][0]), int(coords[0][1])
        x2, y2 = int(coords[1][0]), int(coords[1][1])
        cv2.rectangle(mask, (min(x1, x2), min(y1, y2)),
                      (max(x1, x2), max(y1, y2)), class_idx, thickness=-1)

    elif ann_type == "polygon":
        if len(coords) < 3:
            return
        pts = np.array(coords, dtype=np.int32).reshape((-1, 1, 2))
        cv2.fillPoly(mask, [pts], class_idx)

    elif ann_type == "brush":
        # coords = [{"points":[[x,y],...],"size":20}, ...]
        for stroke in coords:
            pts_raw = stroke.get("points", [])
            size = int(stroke.get("size", 10))
            if len(pts_raw) < 1:
                continue
            pts = np.array(pts_raw, dtype=np.int32)
            if len(pts_raw) == 1:
                cv2.circle(mask, (pts[0][0], pts[0][1]), size // 2, class_idx, -1)
            else:
                pts_reshaped = pts.reshape((-1, 1, 2))
                cv2.polylines(mask, [pts_reshaped], isClosed=False,
                              color=class_idx, thickness=size)
                for pt in pts:
                    cv2.circle(mask, (int(pt[0]), int(pt[1])), size // 2, class_idx, -1)


def _draw_annotation_value(mask: np.ndarray, ann: dict, value: int) -> None:
    """Paint one annotation with an arbitrary integer value (for instance masks)."""
    ann_type = ann.get("type", "polygon")
    coords = ann.get("coordinates", [])

    if ann_type == "rect":
        if len(coords) < 2:
            return
        x1, y1 = int(coords[0][0]), int(coords[0][1])
        x2, y2 = int(coords[1][0]), int(coords[1][1])
        cv2.rectangle(mask, (min(x1, x2), min(y1, y2)),
                      (max(x1, x2), max(y1, y2)), int(value), thickness=-1)

    elif ann_type == "polygon":
        if len(coords) < 3:
            return
        pts = np.array(coords, dtype=np.int32).reshape((-1, 1, 2))
        cv2.fillPoly(mask, [pts], int(value))

    elif ann_type == "brush":
        for stroke in coords:
            pts_raw = stroke.get("points", [])
            size = int(stroke.get("size", 10))
            if len(pts_raw) < 1:
                continue
            pts = np.array(pts_raw, dtype=np.int32)
            if len(pts_raw) == 1:
                cv2.circle(mask, (pts[0][0], pts[0][1]), size // 2, int(value), -1)
            else:
                pts_reshaped = pts.reshape((-1, 1, 2))
                cv2.polylines(mask, [pts_reshaped], isClosed=False,
                              color=int(value), thickness=size)
                for pt in pts:
                    cv2.circle(mask, (int(pt[0]), int(pt[1])), size // 2, int(value), -1)


# 20 distinct colours for class-index → RGB mapping (index 0 = black/background)
_CLASS_COLORS = np.array([
    [0, 0, 0],        # 0 background
    [255, 0, 0],      # 1 red
    [0, 255, 0],      # 2 green
    [0, 0, 255],      # 3 blue
    [255, 255, 0],    # 4 yellow
    [255, 0, 255],    # 5 magenta
    [0, 255, 255],    # 6 cyan
    [128, 0, 0],      # 7 maroon
    [0, 128, 0],      # 8 dark green
    [0, 0, 128],      # 9 navy
    [255, 128, 0],    # 10 orange
    [128, 0, 255],    # 11 purple
    [0, 128, 255],    # 12 sky blue
    [255, 128, 128],  # 13 salmon
    [128, 255, 128],  # 14 light green
    [128, 128, 255],  # 15 light blue
    [255, 255, 128],  # 16 light yellow
    [255, 128, 255],  # 17 pink
    [128, 255, 255],  # 18 light cyan
    [192, 192, 192],  # 19 silver
], dtype=np.uint8)


def _class_index_to_rgb(mask: np.ndarray) -> np.ndarray:
    """Convert a single-channel class-index mask to a 3-channel BGR image."""
    h, w = mask.shape
    rgb = np.zeros((h, w, 3), dtype=np.uint8)
    for idx in range(len(_CLASS_COLORS)):
        rgb[mask == idx] = _CLASS_COLORS[idx]
    # For indices beyond the palette, use modulo
    max_palette = len(_CLASS_COLORS)
    beyond = mask >= max_palette
    if beyond.any():
        for idx in np.unique(mask[beyond]):
            rgb[mask == idx] = _CLASS_COLORS[idx % max_palette]
    # Convert RGB to BGR for cv2.imwrite
    return cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)


# ── Patch extraction ──────────────────────────────────────────────────────────

def _annotation_bbox(ann: dict) -> tuple[int, int, int, int] | None:
    """Return (x1, y1, x2, y2) bounding box, or None if indeterminate."""
    ann_type = ann.get("type", "polygon")
    coords = ann.get("coordinates", [])

    if ann_type == "rect":
        if len(coords) < 2:
            return None
        x1, y1 = int(coords[0][0]), int(coords[0][1])
        x2, y2 = int(coords[1][0]), int(coords[1][1])
        return min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)

    elif ann_type == "polygon":
        if not coords:
            return None
        pts = np.array(coords, dtype=np.int32)
        return int(pts[:, 0].min()), int(pts[:, 1].min()), \
               int(pts[:, 0].max()), int(pts[:, 1].max())

    elif ann_type == "brush":
        all_pts = []
        for stroke in coords:
            all_pts.extend(stroke.get("points", []))
        if not all_pts:
            return None
        pts = np.array(all_pts, dtype=np.int32)
        return int(pts[:, 0].min()), int(pts[:, 1].min()), \
               int(pts[:, 0].max()), int(pts[:, 1].max())

    return None


def _read_region_openslide(file_path: str, x1: int, y1: int, x2: int, y2: int) -> np.ndarray:
    from openslide import OpenSlide
    slide = OpenSlide(file_path)
    try:
        region = slide.read_region((x1, y1), 0, (x2 - x1, y2 - y1)).convert("RGB")
        return cv2.cvtColor(np.array(region), cv2.COLOR_RGB2BGR)
    finally:
        slide.close()


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.post("/process-slide", response_model=ProcessSlideResponse)
def process_slide(req: ProcessSlideRequest):
    """Generate DeepZoom tiles from a WSI or standard image file."""
    if not Path(req.file_path).exists():
        raise HTTPException(status_code=404, detail=f"File not found: {req.file_path}")

    try:
        ext = Path(req.file_path).suffix.lower()
        if ext in OPENSLIDE_EXTS:
            width, height, max_zoom, tile_dir, thumb = _gen_tiles_openslide(
                req.file_path, req.slide_id, req.tile_size)
        elif ext in STANDARD_EXTS:
            width, height, max_zoom, tile_dir, thumb = _gen_tiles_standard(
                req.file_path, req.slide_id, req.tile_size)
        else:
            raise HTTPException(status_code=400, detail=f"Unsupported format: {ext}")
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("process-slide failed for slide_id=%d", req.slide_id)
        raise HTTPException(status_code=500, detail=str(e))

    return ProcessSlideResponse(
        slide_id=req.slide_id,
        tile_dir=tile_dir,
        width=width,
        height=height,
        max_zoom=max_zoom,
        thumbnail_path=thumb,
    )


@router.post("/generate-mask", response_model=GenerateMaskResponse)
def generate_mask(req: GenerateMaskRequest):
    """Render vector annotations into a mask PNG.

    mask_type controls the pixel value semantics:
      SEMANTIC  — single-channel, pixel value = label_class_index (default)
      INSTANCE  — single-channel, pixel value = unique annotation ordinal (1, 2, 3, …)
      COLOR     — 3-channel RGB, each class gets a distinct colour

    mask_scale is a downsample factor (1/2/4/8) applied to the output dimensions.
    """
    scale = max(1, req.mask_scale)
    out_w = max(1, req.width // scale)
    out_h = max(1, req.height // scale)
    mask_type = (req.mask_type or "SEMANTIC").upper()

    if mask_type == "COLOR":
        # Draw semantic mask first, then map to colours
        raw = np.zeros((req.height, req.width), dtype=np.uint8)
        for ann in req.annotations:
            try:
                _draw_annotation(raw, ann)
            except Exception as e:
                logger.warning("Skipping annotation (id=%s): %s", ann.get("id"), e)

        if scale > 1:
            raw = cv2.resize(raw, (out_w, out_h), interpolation=cv2.INTER_NEAREST)

        # Map class indices to distinct RGB colours
        mask_rgb = _class_index_to_rgb(raw)
        out_path = _mask_path(req.slide_id)
        if not cv2.imwrite(str(out_path), mask_rgb):
            raise HTTPException(status_code=500, detail="Failed to write mask PNG")

    elif mask_type == "INSTANCE":
        mask = np.zeros((req.height, req.width), dtype=np.uint16)
        for idx, ann in enumerate(req.annotations, start=1):
            try:
                _draw_annotation_value(mask, ann, idx)
            except Exception as e:
                logger.warning("Skipping annotation (id=%s): %s", ann.get("id"), e)

        if scale > 1:
            mask = cv2.resize(mask, (out_w, out_h), interpolation=cv2.INTER_NEAREST)

        out_path = _mask_path(req.slide_id)
        if not cv2.imwrite(str(out_path), mask):
            raise HTTPException(status_code=500, detail="Failed to write mask PNG")

    else:
        # SEMANTIC (default)
        mask = np.zeros((req.height, req.width), dtype=np.uint8)
        for ann in req.annotations:
            try:
                _draw_annotation(mask, ann)
            except Exception as e:
                logger.warning("Skipping annotation (id=%s): %s", ann.get("id"), e)

        if scale > 1:
            mask = cv2.resize(mask, (out_w, out_h), interpolation=cv2.INTER_NEAREST)

        out_path = _mask_path(req.slide_id)
        if not cv2.imwrite(str(out_path), mask):
            raise HTTPException(status_code=500, detail="Failed to write mask PNG")

    return GenerateMaskResponse(slide_id=req.slide_id, mask_path=str(out_path))


@router.post("/extract-patches", response_model=ExtractPatchesResponse)
def extract_patches(req: ExtractPatchesRequest):
    """Crop patches from the original image based on annotation ROIs."""
    if not Path(req.file_path).exists():
        raise HTTPException(status_code=404, detail=f"File not found: {req.file_path}")

    ext = Path(req.file_path).suffix.lower()
    is_openslide = ext in OPENSLIDE_EXTS

    # For standard images, load once; for OpenSlide formats, read per-region
    img_standard: np.ndarray | None = None
    img_h = img_w = 0
    if not is_openslide:
        img_standard = cv2.imread(req.file_path, cv2.IMREAD_COLOR)
        if img_standard is None:
            raise HTTPException(status_code=500, detail="cv2 failed to open image")
        img_h, img_w = img_standard.shape[:2]
    else:
        from openslide import OpenSlide
        with OpenSlide(req.file_path) as _s:
            img_w, img_h = _s.dimensions

    patch_dir = _patch_out_dir(req.slide_id)
    patch_count = 0

    for ann in req.annotations:
        bbox = _annotation_bbox(ann)
        if bbox is None:
            continue

        x1, y1, x2, y2 = bbox
        x1 = max(0, x1)
        y1 = max(0, y1)
        x2 = min(img_w, x2)
        y2 = min(img_h, y2)
        if x2 <= x1 or y2 <= y1:
            continue

        try:
            if is_openslide:
                patch = _read_region_openslide(req.file_path, x1, y1, x2, y2)
            else:
                patch = img_standard[y1:y2, x1:x2]

            patch = cv2.resize(patch, (req.patch_size, req.patch_size),
                               interpolation=cv2.INTER_LINEAR)
        except Exception as e:
            logger.warning("Failed to extract patch for ann id=%s: %s",
                           ann.get("id"), e)
            continue

        label_name = ann.get("label_name", "unknown").replace(" ", "_")
        out_path = patch_dir / f"{label_name}_patch_{patch_count:04d}.png"
        cv2.imwrite(str(out_path), patch)
        patch_count += 1

    return ExtractPatchesResponse(
        slide_id=req.slide_id,
        patch_dir=str(patch_dir),
        patch_count=patch_count,
    )


# ── SAM prediction ────────────────────────────────────────────────────────────

@router.post("/sam/predict", response_model=SamPredictResponse)
def sam_predict(req: SamPredictRequest):
    """Run SAM/MobileSAM inference on a WSI region.

    Coordinate system: all input coords (points, box) are in **level-0** WSI global
    pixels.  The returned polygon coordinates are also in level-0 WSI global pixels.

    The endpoint extracts a `patch_size × patch_size` pixel region at the requested
    pyramid `level` centred on the prompt, runs SAM, then maps the result back to
    level-0 global coordinates.
    """
    if not req.points and req.box is None:
        raise HTTPException(status_code=400,
                            detail="Provide at least one point or a box")
    if req.points and len(req.points) != len(req.point_labels):
        raise HTTPException(status_code=400,
                            detail="points and point_labels must have equal length")

    predictor = _get_sam_predictor()

    if not Path(req.file_path).exists():
        raise HTTPException(status_code=404,
                            detail=f"File not found: {req.file_path}")

    ext = Path(req.file_path).suffix.lower()
    is_openslide = ext in OPENSLIDE_EXTS

    try:
        import torch

        # ── Step 1: compute centre of prompts (level-0 coords) ─────────────────
        if req.box is not None:
            bx1, by1, bx2, by2 = req.box
            center_x = (bx1 + bx2) / 2.0
            center_y = (by1 + by2) / 2.0
        else:
            pts_arr = np.array(req.points, dtype=np.float64)
            center_x = float(np.mean(pts_arr[:, 0]))
            center_y = float(np.mean(pts_arr[:, 1]))

        # ── Step 2: extract image region ───────────────────────────────────────
        if is_openslide:
            from openslide import OpenSlide
            slide = OpenSlide(req.file_path)
            try:
                level = min(req.level, slide.level_count - 1)
                downsample = slide.level_downsamples[level]
                half_l0 = req.patch_size * downsample / 2.0
                tl_x = int(max(0, center_x - half_l0))
                tl_y = int(max(0, center_y - half_l0))
                region_pil = slide.read_region(
                    (tl_x, tl_y), level, (req.patch_size, req.patch_size)
                ).convert("RGB")
                image_rgb = np.array(region_pil, dtype=np.uint8)
            finally:
                slide.close()
        else:
            # Standard PNG/JPG: only level 0
            downsample = 1.0
            img_bgr = cv2.imread(req.file_path, cv2.IMREAD_COLOR)
            if img_bgr is None:
                raise HTTPException(status_code=500, detail="Failed to open image")
            img_h, img_w = img_bgr.shape[:2]
            half = req.patch_size // 2
            tl_x = int(max(0, center_x - half))
            tl_y = int(max(0, center_y - half))
            br_x = min(img_w, tl_x + req.patch_size)
            br_y = min(img_h, tl_y + req.patch_size)
            patch_bgr = img_bgr[tl_y:br_y, tl_x:br_x]
            image_rgb = cv2.cvtColor(patch_bgr, cv2.COLOR_BGR2RGB)

        # ── Step 3: convert prompts to patch-local pixel coordinates ───────────
        local_pts: list[list[float]] = []
        for px, py in req.points:
            local_pts.append([(px - tl_x) / downsample,
                               (py - tl_y) / downsample])

        local_box: np.ndarray | None = None
        if req.box is not None:
            bx1, by1, bx2, by2 = req.box
            local_box = np.array([
                (bx1 - tl_x) / downsample,
                (by1 - tl_y) / downsample,
                (bx2 - tl_x) / downsample,
                (by2 - tl_y) / downsample,
            ], dtype=np.float32)

        pt_coords = np.array(local_pts, dtype=np.float32) if local_pts else None
        pt_labels = np.array(req.point_labels, dtype=np.int32) if local_pts else None

        # ── Step 4: SAM inference (serialised — predictor is stateful) ─────────
        with _sam_infer_lock:
            predictor.set_image(image_rgb)
            with torch.no_grad():
                masks, scores, _ = predictor.predict(
                    point_coords=pt_coords,
                    point_labels=pt_labels,
                    box=local_box,
                    multimask_output=True,
                )

        # ── Step 5: pick best mask and extract contours ─────────────────────────
        best_idx = int(np.argmax(scores))
        best_mask = (masks[best_idx] > 0).astype(np.uint8) * 255
        best_score = float(scores[best_idx])

        contours, _ = cv2.findContours(
            best_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE
        )

        # Map contours back to level-0 WSI global coordinates
        global_polygons: list[list[list[float]]] = []
        for cnt in contours:
            if len(cnt) < 3:
                continue
            pts_global = [
                [tl_x + float(pt[0][0]) * downsample,
                 tl_y + float(pt[0][1]) * downsample]
                for pt in cnt
            ]
            global_polygons.append(pts_global)

        # ── Step 6: encode best mask as base64 PNG ──────────────────────────────
        _, buf = cv2.imencode(".png", best_mask)
        mask_b64 = base64.b64encode(buf.tobytes()).decode("utf-8")

        return SamPredictResponse(
            polygons=global_polygons,
            score=best_score,
            mask_b64=mask_b64,
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.exception("SAM prediction failed for slide_id=%d", req.slide_id)
        raise HTTPException(status_code=500, detail=str(e))
