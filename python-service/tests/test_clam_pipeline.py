import tempfile
import unittest
from pathlib import Path

import numpy as np

from pathorchestra_ai.clam_pipeline import (
    compute_annotation_overlap,
    compute_candidate_regions,
    discover_checkpoints,
    parse_annotation_mask,
    percentile_ranks,
)


class ClamPipelineUnitTest(unittest.TestCase):
    def test_percentile_ranks_are_stable_and_bounded(self):
        ranks = percentile_ranks(np.array([2.0, -1.0, 4.0, 3.0]))

        np.testing.assert_allclose(ranks, [1 / 3, 0.0, 1.0, 2 / 3])
        self.assertEqual(np.float32, ranks.dtype)

    def test_slide_score_gate_suppresses_negative_candidates(self):
        coords = np.array([[2, 2], [4, 2], [6, 2]], dtype=np.int32)
        attention = np.array([0.96, 0.98, 1.0], dtype=np.float32)

        metrics, candidate_mask, high_attention_mask = compute_candidate_regions(
            coords=coords,
            attention_percentiles=attention,
            slide_score=0.21,
            canvas_shape=(16, 16),
            scale_xy=(1.0, 1.0),
            patch_size_level0=2,
            mpp_xy=(0.5, 0.5),
            attention_threshold=0.95,
            slide_score_gate=0.50,
            min_candidate_patches=2,
        )

        self.assertEqual(0, metrics["candidate_region_count"])
        self.assertEqual(3, metrics["high_attention_patch_count"])
        self.assertEqual(0, int(candidate_mask.sum()))
        self.assertGreater(int(high_attention_mask.sum()), 0)

    def test_adjacent_high_attention_patches_form_one_candidate(self):
        coords = np.array(
            [[2, 2], [4, 2], [6, 2], [12, 12]], dtype=np.int32
        )
        attention = np.array([0.96, 0.98, 1.0, 0.99], dtype=np.float32)

        metrics, candidate_mask, _ = compute_candidate_regions(
            coords=coords,
            attention_percentiles=attention,
            slide_score=0.82,
            canvas_shape=(20, 20),
            scale_xy=(1.0, 1.0),
            patch_size_level0=2,
            mpp_xy=(0.5, 0.5),
            attention_threshold=0.95,
            slide_score_gate=0.50,
            min_candidate_patches=2,
        )

        self.assertEqual(1, metrics["candidate_region_count"])
        self.assertEqual(1, metrics["high_risk_region_count"])
        self.assertEqual([2, 2, 8, 4], metrics["candidates"][0]["bbox_level0"])
        self.assertAlmostEqual(
            0.000003, metrics["candidate_area_proxy_mm2"], places=9
        )
        self.assertGreater(int(candidate_mask.sum()), 0)

    def test_xml_mask_and_overlap_are_computed_at_render_scale(self):
        xml = """<?xml version="1.0"?>
<ASAP_Annotations><Annotations><Annotation Type="Polygon"><Coordinates>
<Coordinate Order="0" X="4" Y="4" />
<Coordinate Order="1" X="12" Y="4" />
<Coordinate Order="2" X="12" Y="12" />
<Coordinate Order="3" X="4" Y="12" />
</Coordinates></Annotation></Annotations></ASAP_Annotations>"""
        with tempfile.TemporaryDirectory() as temp_dir:
            xml_path = Path(temp_dir) / "annotation.xml"
            xml_path.write_text(xml, encoding="utf-8")
            annotation = parse_annotation_mask(
                xml_path, canvas_shape=(10, 10), scale_xy=(2.0, 2.0)
            )

        high_attention = np.zeros((10, 10), dtype=np.uint8)
        high_attention[3:5, 3:5] = 1
        candidates = np.zeros((10, 10), dtype=np.uint8)
        candidates[0:2, 0:2] = 1
        overlap = compute_annotation_overlap(
            annotation, high_attention, candidates
        )

        self.assertTrue(overlap["high_attention_overlaps_annotation"])
        self.assertFalse(overlap["candidate_regions_overlap_annotation"])
        self.assertGreater(overlap["high_attention_intersection_pixels"], 0)

    def test_checkpoint_discovery_uses_fold_order(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            for fold in [9, 1, 0, 2]:
                (root / f"s_{fold}_checkpoint.pt").touch()

            names = [path.name for path in discover_checkpoints(root)]

        self.assertEqual(
            [
                "s_0_checkpoint.pt",
                "s_1_checkpoint.pt",
                "s_2_checkpoint.pt",
                "s_9_checkpoint.pt",
            ],
            names,
        )


if __name__ == "__main__":
    unittest.main()
