import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import cv2
import numpy as np
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw

from main import app
from routers import internal


class InternalApiTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        internal.STORAGE_ROOT = self.root / "storage"
        internal._tile_locks.clear()

        self.image_path = self.root / "sample.png"
        image = Image.new("RGB", (513, 385), (242, 224, 230))
        draw = ImageDraw.Draw(image)
        draw.rectangle((80, 70, 300, 260), fill=(154, 44, 74))
        draw.ellipse((190, 110, 430, 350), fill=(72, 112, 143))
        image.save(self.image_path)

        self.client = TestClient(app)

    def tearDown(self):
        self.client.close()
        self.temp_dir.cleanup()

    def test_health(self):
        response = self.client.get("/health")

        self.assertEqual(200, response.status_code)
        self.assertEqual({"status": "ok"}, response.json())

    def test_process_standard_image_generates_deepzoom_and_thumbnail(self):
        response = self.client.post(
            "/internal/process-slide",
            json={
                "slide_id": 101,
                "file_path": str(self.image_path),
                "tile_size": 256,
            },
        )

        self.assertEqual(200, response.status_code, response.text)
        result = response.json()
        self.assertEqual(513, result["width"])
        self.assertEqual(385, result["height"])
        self.assertEqual(10, result["max_zoom"])

        tile_dir = Path(result["tile_dir"])
        self.assertTrue((tile_dir / "slide.dzi").is_file())
        self.assertEqual(
            6,
            len(list((tile_dir / "slide_files" / "10").glob("*.jpg"))),
        )
        self.assertTrue(Path(result["thumbnail_path"]).is_file())

    def test_openslide_processing_defers_tiles_until_requested(self):
        wsi_path = self.root / "sample.tif"
        wsi_path.write_bytes(b"test-wsi-placeholder")

        with (
            patch("openslide.OpenSlide") as open_slide,
            patch("openslide.deepzoom.DeepZoomGenerator") as deepzoom,
        ):
            slide = open_slide.return_value
            slide.dimensions = (97_792, 221_184)
            slide.get_thumbnail.return_value = Image.new(
                "RGB", (227, 512), (220, 194, 204)
            )
            deepzoom.return_value.level_count = 19

            result = internal._gen_tiles_openslide(
                str(wsi_path), slide_id=201, tile_size=256
            )

        tile_dir = Path(result[3])
        self.assertTrue((tile_dir / "slide.dzi").is_file())
        self.assertTrue(Path(result[4]).is_file())
        self.assertFalse((tile_dir / "slide_files").exists())
        deepzoom.return_value.get_tile.assert_not_called()

    def test_on_demand_openslide_tile_is_cached(self):
        wsi_path = self.root / "sample.tif"
        wsi_path.write_bytes(b"test-wsi-placeholder")
        jpeg = b"\xff\xd8cached-tile\xff\xd9"
        params = {"file_path": str(wsi_path), "tile_size": 256}

        with patch.object(
            internal, "_render_openslide_tile", return_value=jpeg
        ) as render:
            first = self.client.get(
                "/internal/slides/202/tiles/18/4/7", params=params
            )
            second = self.client.get(
                "/internal/slides/202/tiles/18/4/7", params=params
            )

        self.assertEqual(200, first.status_code, first.text)
        self.assertEqual("image/jpeg", first.headers["content-type"])
        self.assertEqual(jpeg, first.content)
        self.assertEqual(jpeg, second.content)
        render.assert_called_once_with(str(wsi_path), 256, 18, 4, 7)
        self.assertTrue(
            (
                internal.STORAGE_ROOT
                / "tiles"
                / "202"
                / "slide_files"
                / "18"
                / "4_7.jpg"
            ).is_file()
        )

    def test_generate_scaled_semantic_color_and_instance_masks(self):
        annotations = [
            {
                "id": 1,
                "type": "rect",
                "coordinates": [[16, 20], [64, 60]],
                "label_class_index": 1,
            },
            {
                "id": 2,
                "type": "polygon",
                "coordinates": [[80, 12], [116, 12], [98, 52]],
                "label_class_index": 2,
            },
        ]
        base_payload = {
            "slide_id": 102,
            "width": 128,
            "height": 96,
            "annotations": annotations,
            "mask_scale": 2,
        }

        semantic = self.client.post(
            "/internal/generate-mask",
            json={**base_payload, "mask_type": "SEMANTIC"},
        )
        self.assertEqual(200, semantic.status_code, semantic.text)
        semantic_mask = cv2.imread(
            semantic.json()["mask_path"], cv2.IMREAD_UNCHANGED
        )
        self.assertEqual((48, 64), semantic_mask.shape)
        self.assertEqual(1, int(semantic_mask[15, 10]))
        self.assertEqual(2, int(semantic_mask[10, 49]))

        color = self.client.post(
            "/internal/generate-mask",
            json={**base_payload, "mask_type": "COLOR"},
        )
        self.assertEqual(200, color.status_code, color.text)
        color_mask = np.array(Image.open(color.json()["mask_path"]))
        self.assertEqual([255, 0, 0], color_mask[15, 10].tolist())
        self.assertEqual([0, 255, 0], color_mask[10, 49].tolist())

        instance = self.client.post(
            "/internal/generate-mask",
            json={**base_payload, "mask_type": "INSTANCE"},
        )
        self.assertEqual(200, instance.status_code, instance.text)
        instance_mask = cv2.imread(
            instance.json()["mask_path"], cv2.IMREAD_UNCHANGED
        )
        self.assertEqual(1, int(instance_mask[15, 10]))
        self.assertEqual(2, int(instance_mask[10, 49]))

    def test_extract_patch_sanitizes_label_and_writes_expected_size(self):
        response = self.client.post(
            "/internal/extract-patches",
            json={
                "slide_id": 103,
                "file_path": str(self.image_path),
                "patch_size": 32,
                "annotations": [
                    {
                        "id": 1,
                        "type": "rect",
                        "coordinates": [[10, 20], [90, 100]],
                        "label_name": "../../tumor/focus",
                    }
                ],
            },
        )

        self.assertEqual(200, response.status_code, response.text)
        result = response.json()
        self.assertEqual(1, result["patch_count"])
        patch_dir = Path(result["patch_dir"]).resolve()
        patch_files = list(patch_dir.glob("*.png"))
        self.assertEqual(1, len(patch_files))
        self.assertEqual(patch_dir, patch_files[0].resolve().parent)
        with Image.open(patch_files[0]) as patch_image:
            self.assertEqual((32, 32), patch_image.size)

    def test_validation_rejects_invalid_tile_size(self):
        response = self.client.post(
            "/internal/process-slide",
            json={
                "slide_id": 104,
                "file_path": str(self.image_path),
                "tile_size": 0,
            },
        )

        self.assertEqual(422, response.status_code)

    def test_sam_validates_file_before_loading_model(self):
        with patch("routers.internal._get_sam_predictor") as get_predictor:
            response = self.client.post(
                "/internal/sam/predict",
                json={
                    "slide_id": 105,
                    "file_path": str(self.root / "missing.png"),
                    "points": [[10, 10]],
                    "point_labels": [1],
                },
            )

        self.assertEqual(404, response.status_code)
        get_predictor.assert_not_called()


if __name__ == "__main__":
    unittest.main()
