import json
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from main import app
from pathorchestra_ai.jobs import AnalysisJobManager, AnalysisRuntimeSettings


def _job_record(job_id: str, status: str = "succeeded") -> dict:
    return {
        "job_id": job_id,
        "slide_id": 201,
        "case_id": "CASE-201",
        "status": status,
        "stage": "complete" if status == "succeeded" else status,
        "stage_progress": 1.0 if status == "succeeded" else 0.25,
        "message": "Analysis completed" if status == "succeeded" else "Running",
        "created_at": "2026-07-18T00:00:00+00:00",
        "started_at": "2026-07-18T00:00:01+00:00",
        "completed_at": (
            "2026-07-18T00:00:02+00:00" if status == "succeeded" else None
        ),
        "error": None,
    }


class AnalysisJobManagerTest(unittest.TestCase):
    def test_job_runs_in_background_and_persists_result(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            clam_root = root / "clam"
            (clam_root / "models").mkdir(parents=True)
            (clam_root / "models" / "model_clam.py").touch()
            checkpoint_dir = root / "checkpoints"
            checkpoint_dir.mkdir()
            resnet_weights = root / "resnet.pth"
            resnet_weights.touch()
            slide_path = root / "slide.tif"
            slide_path.write_bytes(b"demo slide")
            captured_configs = []

            def fake_analyzer(config, progress):
                captured_configs.append(config)
                progress("features", 0.5, "Encoding")
                artifact_dir = config.output_dir / "artifacts"
                artifact_dir.mkdir(parents=True)
                (artifact_dir / "overview.png").write_bytes(b"png")
                result = {
                    "status": "succeeded",
                    "artifacts": {
                        "model_evidence_overview": "artifacts/overview.png"
                    },
                }
                (config.output_dir / "result.json").write_text(
                    json.dumps(result), encoding="utf-8"
                )
                return result

            settings = AnalysisRuntimeSettings(
                analysis_root=root / "analyses",
                clam_root=clam_root,
                checkpoint_dir=checkpoint_dir,
                resnet_weights=resnet_weights,
            )
            manager = AnalysisJobManager(settings, analyzer=fake_analyzer)
            try:
                submitted = manager.submit(slide_id=201, file_path=slide_path)
                deadline = time.monotonic() + 3.0
                record = submitted
                while record["status"] not in {"succeeded", "failed"}:
                    self.assertLess(time.monotonic(), deadline)
                    time.sleep(0.01)
                    record = manager.get(submitted["job_id"])

                self.assertEqual("succeeded", record["status"])
                self.assertEqual(
                    "artifacts/overview.png",
                    manager.get_result(submitted["job_id"])["artifacts"][
                        "model_evidence_overview"
                    ],
                )
                self.assertTrue(
                    (manager.run_dir(submitted["job_id"]) / "job.json").is_file()
                )
                self.assertEqual(1, len(captured_configs))
                self.assertTrue(
                    captured_configs[0].cache_dir.is_relative_to(
                        settings.analysis_root / "cache"
                    )
                )
            finally:
                manager.shutdown()


class FakeAnalysisManager:
    def __init__(self, run_dir: Path, status: str = "succeeded"):
        self.job_id = "a" * 32
        self.record = _job_record(self.job_id, status=status)
        self._run_dir = run_dir
        self.result = {
            "status": "succeeded",
            "artifacts": {
                "model_evidence_overview": "artifacts/overview.png"
            },
        }

    def capabilities(self):
        return {"configured": True, "missing": [], "queue_concurrency": 1}

    def submit(self, **_kwargs):
        return self.record

    def get(self, job_id):
        if job_id != self.job_id:
            raise KeyError(job_id)
        return self.record

    def get_result(self, job_id):
        self.get(job_id)
        if self.record["status"] != "succeeded":
            raise RuntimeError("Analysis result is not available while status is running")
        return self.result

    def run_dir(self, job_id):
        self.get(job_id)
        return self._run_dir


class AnalysisApiTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        artifact_dir = self.root / "artifacts"
        artifact_dir.mkdir()
        (artifact_dir / "overview.png").write_bytes(b"image data")
        (self.root / "secret.txt").write_text("not an artifact", encoding="utf-8")
        self.manager = FakeAnalysisManager(self.root)
        self.manager_patch = patch(
            "routers.ai_analysis._get_analysis_job_manager",
            return_value=self.manager,
        )
        self.manager_patch.start()
        self.client = TestClient(app)

    def tearDown(self):
        self.client.close()
        self.manager_patch.stop()
        self.temp_dir.cleanup()

    def test_submit_and_read_analysis_result(self):
        response = self.client.post(
            "/internal/ai/analyses",
            json={
                "slide_id": 201,
                "case_id": "CASE-201",
                "file_path": "C:/demo/slide.tif",
            },
        )

        self.assertEqual(202, response.status_code, response.text)
        body = response.json()
        self.assertEqual(self.manager.job_id, body["job_id"])
        self.assertEqual(
            f"/internal/ai/analyses/{self.manager.job_id}/result",
            body["result_url"],
        )

        result = self.client.get(body["result_url"])
        self.assertEqual(200, result.status_code, result.text)
        self.assertEqual("succeeded", result.json()["status"])

    def test_artifact_endpoint_allows_only_result_manifest_files(self):
        allowed = self.client.get(
            f"/internal/ai/analyses/{self.manager.job_id}/files/"
            "artifacts/overview.png"
        )
        denied = self.client.get(
            f"/internal/ai/analyses/{self.manager.job_id}/files/secret.txt"
        )

        self.assertEqual(200, allowed.status_code, allowed.text)
        self.assertEqual(b"image data", allowed.content)
        self.assertEqual(404, denied.status_code)

    def test_result_returns_conflict_while_job_is_running(self):
        self.manager.record = _job_record(self.manager.job_id, status="running")

        response = self.client.get(
            f"/internal/ai/analyses/{self.manager.job_id}/result"
        )

        self.assertEqual(409, response.status_code)

    def test_unknown_job_returns_not_found(self):
        response = self.client.get(f"/internal/ai/analyses/{'b' * 32}")

        self.assertEqual(404, response.status_code)


if __name__ == "__main__":
    unittest.main()
