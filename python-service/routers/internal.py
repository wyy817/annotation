"""
Internal API routes — called only by Spring Boot backend.
Phase 2: DeepZoom tile generation, semantic mask rendering, patch extraction.

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
"""

import math
import os
import logging
from pathlib import Path
from typing import Any

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


# ── Tile generation ───────────────────────────────────────────────────────────

def _gen_tiles_svs(file_path: str, slide_id: int, tile_size: int):
    """DeepZoom tiles for SVS via OpenSlide."""
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


def _read_region_svs(file_path: str, x1: int, y1: int, x2: int, y2: int) -> np.ndarray:
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
        if ext == ".svs":
            width, height, max_zoom, tile_dir, thumb = _gen_tiles_svs(
                req.file_path, req.slide_id, req.tile_size)
        elif ext in (".png", ".jpg", ".jpeg"):
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
    """Render vector annotations into a single-channel semantic mask PNG.
    Pixel value = label_class_index; 0 = background."""
    mask = np.zeros((req.height, req.width), dtype=np.uint8)

    for ann in req.annotations:
        try:
            _draw_annotation(mask, ann)
        except Exception as e:
            logger.warning("Skipping annotation (id=%s) due to error: %s",
                           ann.get("id"), e)

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
    is_svs = ext == ".svs"

    # For standard images, load once; for SVS, read per-region
    img_standard: np.ndarray | None = None
    img_h = img_w = 0
    if not is_svs:
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
            if is_svs:
                patch = _read_region_svs(req.file_path, x1, y1, x2, y2)
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
