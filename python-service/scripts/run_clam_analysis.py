"""Command-line entry point for a cached Pathorchestra CLAM analysis."""

from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

from pathorchestra_ai import ClamAnalysisConfig, analyze_wsi


def _path(value: str) -> Path:
    return Path(value).expanduser().resolve()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run CLAM CAMELYON WSI analysis and generate evidence artifacts."
    )
    parser.add_argument("--slide", required=True, type=_path)
    parser.add_argument("--output-dir", required=True, type=_path)
    parser.add_argument("--clam-root", required=True, type=_path)
    parser.add_argument("--checkpoint-dir", required=True, type=_path)
    parser.add_argument("--resnet-weights", required=True, type=_path)
    parser.add_argument("--annotation-xml", type=_path)
    parser.add_argument("--patch-h5", type=_path)
    parser.add_argument("--cache-dir", type=_path)
    parser.add_argument("--device", default="auto")
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--attention-threshold", type=float, default=0.95)
    parser.add_argument("--slide-score-gate", type=float, default=0.50)
    parser.add_argument("--min-candidate-patches", type=int, default=3)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    def progress(stage: str, fraction: float, message: str) -> None:
        print(
            json.dumps(
                {
                    "stage": stage,
                    "progress": round(fraction, 4),
                    "message": message,
                },
                ensure_ascii=True,
            ),
            flush=True,
        )

    config = ClamAnalysisConfig(
        slide_path=args.slide,
        output_dir=args.output_dir,
        clam_root=args.clam_root,
        checkpoint_dir=args.checkpoint_dir,
        resnet_weights=args.resnet_weights,
        annotation_xml=args.annotation_xml,
        patch_h5=args.patch_h5,
        cache_dir=args.cache_dir,
        device=args.device,
        batch_size=args.batch_size,
        attention_percentile_threshold=args.attention_threshold,
        slide_score_gate=args.slide_score_gate,
        min_candidate_patches=args.min_candidate_patches,
    )
    result = analyze_wsi(config, progress=progress)
    print(
        json.dumps(
            {
                "result": str(config.output_dir / "result.json"),
                "tumor_evidence_score": result["inference"]["tumor_evidence_score"],
                "candidate_region_count": result["region_metrics"][
                    "candidate_region_count"
                ],
            },
            ensure_ascii=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
