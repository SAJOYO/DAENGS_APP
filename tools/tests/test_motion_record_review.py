# /// script
# requires-python = ">=3.11"
# dependencies = ["pydantic>=2,<3", "tzdata"]
# ///
"""Offline checks, including actual Kotlin exports from MotionRecordReviewCaptureTest.

uv run tools/tests/test_motion_record_review.py --backend ../DAENGS_dev/backend --kotlin-suite PATH
The synthetic HTTP responses below are not captured/deployed API responses.
"""
import argparse
from datetime import UTC, datetime
import hashlib
import io
import json
from pathlib import Path
import shutil
import sys
import tarfile
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import compare_motion_record_review as review
from pull_motion_record_review import unpack


def write(path, value):
    path.write_text(json.dumps(value), encoding="utf-8")


class ComparisonTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def copy(self, name="walking"):
        folder = self.root / name
        shutil.copytree(KOTLIN / name, folder)
        return folder

    def replace_response(self, folder, name, value):
        index = review.read(folder / "sources.json")
        source = next(s for s in index["sources"] if s["file"] == name + ".json")
        body = json.dumps(value, separators=(",", ":"))
        source.update(http_status=200, body_sha256=hashlib.sha256(body.encode()).hexdigest(),
                      etag='"' + hashlib.sha256(body.encode()).hexdigest() + '"')
        write(folder / (name + ".json"), {"body": body})
        write(folder / "sources.json", index)

    def fill_synthetic_api(self, folder, *, coarse=False):
        from daengs_backend.schemas.walk import WalkPointUpload
        from daengs_backend.schemas.walk_trajectory import TrajectoryCalculation
        from daengs_backend.services.walk_motion_engine import replay
        from daengs_backend.services.walk_motion_contract import manifest_digest
        from daengs_backend.services.walk_trajectory_shadow import replay_shadow

        captured = review.sources(folder)
        detail, manifest, points, raw, evidence, precision_fp = review.assemble_input(captured)
        if coarse:
            raw = [WalkPointUpload.model_validate(p) for p in detail["points"]]
            precision_fp = None
        expected = replay_shadow(manifest, points, raw, owner_id="fixture-owner", precision_fingerprint=precision_fp)
        snapshot = expected.snapshot.model_copy(update={"ledger": expected.snapshot.ledger.model_copy(update={"superseded": ()})})
        epochs = {e.source_epoch: e for e in manifest.epochs}
        times = []
        for event in snapshot.ledger.journal.events:
            if event.ref.ingress_seq is None:
                epoch = epochs[event.ref.source_epoch]
                at = epoch.started_at_millis if event.ref.control_kind == "epoch_start" else epoch.ended_at_millis
            else:
                elapsed = raw[event.ref.ingress_seq].at - datetime(1970, 1, 1, tzinfo=UTC)
                at = elapsed.days * 86_400_000 + elapsed.seconds * 1000 + elapsed.microseconds // 1000
            times.append({"ref": event.ref, "original_wall_time_millis": at})
        metrics = snapshot.ledger.metrics()
        api = TrajectoryCalculation(walk_id=detail["id"], measurement=snapshot,
            result_digest=snapshot.ref().result_digest, observation_policy=expected.observation_policy.model_dump(),
            wall_times=times, locations=expected.locations, observed_runs=expected.observed_runs,
            walking_sections=expected.walking_sections, boundaries=expected.boundaries, metrics=metrics,
            average_walking_speed_mps=metrics.average_walking_speed_mps,
            located_fraction_of_known_time=metrics.located_fraction_of_known_time,
            motion_recording_duration_ns=expected.motion_active_duration_ns)
        motion = replay(manifest, points, raw)
        motion.update(version="gps-motion-calculation-v1", walk_id=detail["id"], client_session_id=manifest.client_session_id,
                      policy_version=manifest.policy.version, measurement_version=manifest.policy.measurement_version,
                      config_hash=manifest.policy.config_hash, manifest_fingerprint=manifest_digest(manifest),
                      evidence_fingerprint=evidence, precision_fingerprint=precision_fp, device_result_verified=False,
                      coordinate_basis="device-fix-bits-v1" if precision_fp else "stored-raw-v1-six-decimals")
        self.replace_response(folder, "motion-calculation", motion)
        value = api.model_dump(mode="json")
        self.replace_response(folder, "trajectory-calculation", value)
        return value

    def test_all_32_actual_kotlin_exports_match_python_steps_and_candidate_projection(self):
        folders = sorted(p for p in KOTLIN.iterdir() if p.is_dir())
        self.assertEqual(32, len(folders))
        for source in folders:
            with self.subTest(case=source.name):
                folder = self.copy(source.name)
                self.fill_synthetic_api(folder)
                result = review.compare(folder, BACKEND)
                self.assertEqual("equivalent", result["same_backup_comparison"],
                    {k: result[k] for k in ("kotlin_vs_checkout", "motion_api", "trajectory_api")})
                self.assertFalse(result["independent_device_input_verified"])

    def test_first_observation_difference_is_reported_even_when_totals_match(self):
        folder = self.copy()
        self.fill_synthetic_api(folder)
        data = review.read(folder / "kotlin-replay.json")
        data["steps"][1]["reasons"] = ["HIGH_SPEED"]
        write(folder / "kotlin-replay.json", data)
        result = review.compare(folder, BACKEND)
        self.assertEqual("mismatch", result["kotlin_vs_checkout"]["outcome"])
        self.assertIsNone(result["kotlin_vs_checkout"]["first_summary_difference"])
        self.assertTrue(result["kotlin_vs_checkout"]["first_step_difference"]["path"].startswith("$[1].reasons"))

    def test_late_precision_result_is_incomparable_not_numerically_tolerated(self):
        folder = self.copy()
        self.fill_synthetic_api(folder, coarse=True)
        result = review.compare(folder, BACKEND)
        self.assertEqual("incomparable", result["motion_api"]["outcome"])
        self.assertEqual("incomparable", result["trajectory_api"]["outcome"])

    def test_valid_ledger_with_wrong_display_boundary_is_a_mismatch(self):
        folder = self.copy()
        api = self.fill_synthetic_api(folder)
        api["boundaries"]["last_walking"] = None
        self.replace_response(folder, "trajectory-calculation", api)
        result = review.compare(folder, BACKEND)
        self.assertEqual("mismatch", result["trajectory_api"]["outcome"])
        self.assertIsNone(result["trajectory_api"]["first_ledger_difference"])

    def test_changed_source_bytes_cannot_be_compared(self):
        folder = self.copy()
        data = review.read(folder / "detail.json")
        data["body"] += " "
        write(folder / "detail.json", data)
        with self.assertRaisesRegex(ValueError, "source_hash"):
            review.compare(folder, BACKEND)

    def test_incomplete_capture_never_reads_partial_success(self):
        folder = self.copy()
        write(folder / "status.json", {"state": "running"})
        with self.assertRaisesRegex(ValueError, "capture_incomplete"):
            review.compare(folder, BACKEND)

    def test_other_account_result_is_rejected(self):
        folder = self.copy()
        api = self.fill_synthetic_api(folder)
        api["measurement"]["scope"]["owner_id"] = "other-account"
        self.replace_response(folder, "trajectory-calculation", api)
        with self.assertRaisesRegex(ValueError, "trajectory_scope"):
            review.compare(folder, BACKEND)

    def test_reused_measurement_id_is_rejected(self):
        folder = self.copy()
        api = self.fill_synthetic_api(folder)
        api["measurement"]["measurement_id"] = "shadow-" + "0" * 64
        self.replace_response(folder, "trajectory-calculation", api)
        with self.assertRaisesRegex(ValueError, "trajectory_id"):
            review.compare(folder, BACKEND)

    def test_candidate_cannot_coerce_float_nanoseconds_to_integer(self):
        folder = self.copy()
        api = self.fill_synthetic_api(folder)
        api["motion_recording_duration_ns"] = float(api["motion_recording_duration_ns"])
        self.replace_response(folder, "trajectory-calculation", api)
        with self.assertRaisesRegex(ValueError, "trajectory_integer_precision"):
            review.compare(folder, BACKEND)

    def test_server_candidate_cannot_claim_to_be_a_device_snapshot(self):
        folder = self.copy()
        api = self.fill_synthetic_api(folder)
        api["measurement"]["source"] = "device"
        self.replace_response(folder, "trajectory-calculation", api)
        with self.assertRaisesRegex(ValueError, "trajectory_source"):
            review.compare(folder, BACKEND)

    def test_missing_api_is_unavailable_not_a_legacy_success(self):
        result = review.compare(self.copy(), BACKEND)
        self.assertEqual("equivalent", result["kotlin_vs_checkout"]["outcome"])
        self.assertEqual("unavailable", result["trajectory_api"]["outcome"])
        self.assertEqual("not_equivalent", result["same_backup_comparison"])

    def test_numeric_tolerance_never_applies_to_nanoseconds_or_refs(self):
        self.assertIsNone(review.first_difference({"distance_m": 10.0}, {"distance_m": 10.00000001}))
        self.assertIsNotNone(review.first_difference({"time_ns": 99999999999999}, {"time_ns": 100000000000000}))
        self.assertIsNotNone(review.first_difference({"seq": 1}, {"seq": 1.0}))
        self.assertIsNotNone(review.first_difference({"distance_m": True}, {"distance_m": 1}))
        self.assertIsNotNone(review.first_difference({"distance_m": float("nan")}, {"distance_m": float("nan")}))

    def test_export_refuses_path_escape_and_existing_capture_changes(self):
        def archive(name, content=b"{}"):
            stream = io.BytesIO()
            with tarfile.open(fileobj=stream, mode="w") as tar:
                info = tarfile.TarInfo(name)
                info.size = len(content)
                tar.addfile(info, io.BytesIO(content))
            return stream.getvalue()
        with self.assertRaises(ValueError):
            unpack(archive("motion-record-review/../escape.json"), self.root)
        safe = "motion-record-review/capture/status.json"
        self.assertEqual(1, unpack(archive(safe), self.root))
        with self.assertRaises(ValueError):
            unpack(archive(safe, b'{"changed":true}'), self.root)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend", type=Path, required=True)
    parser.add_argument("--kotlin-suite", type=Path, required=True)
    args, rest = parser.parse_known_args()
    BACKEND, KOTLIN = args.backend.resolve(), args.kotlin_suite.resolve()
    sys.path.insert(0, str(BACKEND / "src"))
    unittest.main(argv=[sys.argv[0], *rest])
