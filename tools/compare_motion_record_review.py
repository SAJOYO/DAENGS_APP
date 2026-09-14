# /// script
# requires-python = ">=3.11"
# dependencies = ["pydantic>=2,<3", "tzdata"]
# ///
"""Compare a private motion capture with actual Kotlin steps and an explicit DEV checkout.

Read-only/offline: no auth, DB, upload or active-view adoption. Server-backup replay is
never promoted to independent verification of the recording device's original journal.
"""
import argparse
from datetime import UTC, datetime
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import sys


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def require(condition, code):
    if not condition:
        raise ValueError(code)


def exact_time_refs(value):
    """Reject coercion before Pydantic can turn a JSON float/string into a time integer."""
    if isinstance(value, dict):
        for key, item in value.items():
            if key == "ingress_seq" or key.endswith(("_ns", "_millis")):
                require(item is None or type(item) is int, "trajectory_integer_precision")
            exact_time_refs(item)
    elif isinstance(value, list):
        for item in value:
            exact_time_refs(item)


def sources(folder):
    require(read(folder / "status.json")["state"] == "complete", "capture_incomplete")
    result = {}
    for source in read(folder / "sources.json")["sources"]:
        name = source["file"]
        require(Path(name).name == name and name.endswith(".json"), "source_path")
        require(name not in result, "source_duplicate")
        body = read(folder / name)["body"]
        require(hashlib.sha256(body.encode()).hexdigest() == source["body_sha256"], "source_hash")
        result[name] = (source, body)
    return result


def first_difference(left, right, path="$", *, distances=True):
    """Tolerate only explicitly named distance/speed fields; times/refs/order are exact."""
    numeric = {"distance_m", "distance_delta_m", "cumulative_distance_m", "walking_distance_m",
               "measured_displacement_m", "average_walking_speed_mps", "located_fraction_of_known_time"}
    if distances and path.rsplit(".", 1)[-1] in numeric and type(left) in (float, int) and type(right) in (float, int):
        if math.isfinite(left) and math.isfinite(right) and math.isclose(left, right, abs_tol=1e-7, rel_tol=1e-10):
            return None
    elif path.rsplit(".", 1)[-1] in {"max_gap_seconds", "max_edge_m"} and type(left) in (int, float) and type(right) in (int, float):
        if left == right:
            return None
    elif isinstance(left, dict) and isinstance(right, dict):
        if left.keys() != right.keys():
            return {"path": path + ".keys", "left": sorted(left), "right": sorted(right)}
        for key in left:
            difference = first_difference(left[key], right[key], f"{path}.{key}", distances=distances)
            if difference:
                return difference
        return None
    elif isinstance(left, (list, tuple)) and isinstance(right, (list, tuple)):
        if len(left) != len(right):
            return {"path": path + ".length", "left": len(left), "right": len(right)}
        for i, (a, b) in enumerate(zip(left, right)):
            difference = first_difference(a, b, f"{path}[{i}]", distances=distances)
            if difference:
                return difference
        return None
    elif type(left) is type(right) and left == right:
        return None
    return {"path": path, "left": left, "right": right}


def assemble_input(captured):
    from daengs_backend.schemas.walk import WalkPointUpload
    from daengs_backend.schemas.walk_motion import MotionManifest, MotionObservation
    from daengs_backend.schemas.walk_precision import PrecisionManifest, PrecisionPoint
    from daengs_backend.services import walk_motion_contract as motion
    from daengs_backend.services import walk_precision_contract as precision
    from daengs_backend.services.walk_finalize import walk_input_fingerprint

    def get(name):
        meta, body = captured[name + ".json"]
        require(meta["http_status"] == 200, f"{name}_http_{meta['http_status']}")
        return json.loads(body)

    detail = get("detail")
    raw = [WalkPointUpload.model_validate(p) for p in detail["points"]]
    status = get("motion-backup")
    manifest = MotionManifest.model_validate(status["manifest"])
    motion.validate_manifest(manifest)
    require(detail["client_session_id"] == manifest.client_session_id, "session_identity")
    require(walk_input_fingerprint(raw) == manifest.raw_input_fingerprint, "raw_fingerprint")
    require(manifest.point_count == len(raw) <= 10_000, "raw_count")
    epoch = manifest.epochs
    def millis(value):
        dt = datetime.fromisoformat(value) - datetime(1970, 1, 1, tzinfo=UTC)
        return dt.days * 86_400_000 + dt.seconds * 1000 + dt.microseconds // 1000
    require(millis(detail["started_at"]) == epoch[0].started_at_millis and
            millis(detail["ended_at"]) == epoch[-1].ended_at_millis, "control_wall_times")

    def pages(kind, status, m, contract, point_type):
        require(status["state"] == "complete" and status["calculation_verified"] is False, f"{kind}_receipt")
        fingerprint = contract.manifest_digest(m)
        require(status["manifest_fingerprint"] == fingerprint, f"{kind}_manifest")
        count = (m.point_count + 255) // 256
        require(status["received_chunks"] == list(range(count)), f"{kind}_received_chunks")
        points, hashes = [], []
        for i in range(count):
            page = get(f"{kind}-chunk-{i}")
            require(page["chunk_index"] == i and page["manifest_fingerprint"] == fingerprint, f"{kind}_chunk_identity")
            chunk = [point_type.model_validate(p) for p in page["points"]]
            require(len(chunk) == min(256, m.point_count - 256 * i), f"{kind}_chunk_count")
            hashes.append(contract.chunk_digest(chunk))
            require(page["chunk_fingerprint"] == hashes[-1], f"{kind}_chunk_hash")
            points.extend(chunk)
        require([p.client_seq for p in points] == list(range(m.point_count)), f"{kind}_order")
        evidence = contract.evidence_digest(fingerprint, hashes)
        require(status["evidence_fingerprint"] == evidence, f"{kind}_evidence")
        return points, evidence

    observations, evidence = pages("motion-backup", status, manifest, motion, MotionObservation)
    motion.validate_observations(manifest, observations, raw)
    precision_meta, precision_body = captured["motion-precision.json"]
    precision_fp = None
    if precision_meta["http_status"] == 200:
        ps = json.loads(precision_body)
        pm = PrecisionManifest.model_validate(ps["manifest"])
        require(pm.client_session_id == manifest.client_session_id and pm.point_count == len(raw)
                and pm.base_evidence_fingerprint == evidence, "precision_base_identity")
        points, precision_fp = pages("motion-precision", ps, pm, precision, PrecisionPoint)
        raw = precision.refine_points(points, raw)
    else:
        require(precision_meta["http_status"] == 404, "precision_unavailable")
    return detail, manifest, observations, raw, evidence, precision_fp


def compare(folder, backend):
    sys.path.insert(0, str(backend.resolve() / "src"))
    from daengs_backend.schemas.walk_trajectory import TrajectoryCalculation
    from daengs_backend.services.walk_motion_engine import replay
    from daengs_backend.services.walk_motion_contract import manifest_digest
    from daengs_backend.services.walk_trajectory_shadow import replay_shadow
    from daengs_walk.trajectory_view import compare_measurements, digest

    captured = sources(folder)
    header = read(folder / "capture.json")
    require(header["format"] == "walk-motion-review-v1" and header["input_origin"] == "server-backup"
            and header["independent_device_input_verified"] is False, "capture_provenance")
    detail, manifest, observations, raw, evidence, precision_fp = assemble_input(captured)
    require(header["server_walk_id"] == detail["id"], "capture_walk")
    kotlin = read(folder / "kotlin-replay.json")
    basis = "device-fix-bits-v1" if precision_fp else "stored-raw-v1-six-decimals"
    require(kotlin["coordinate_basis"] == basis and kotlin["manifest_fingerprint"] == manifest_digest(manifest)
            and kotlin["evidence_fingerprint"] == evidence and kotlin["precision_fingerprint"] == precision_fp
            and kotlin["input_origin"] == "server-backup" and kotlin["independent_device_input_verified"] is False,
            "kotlin_input_provenance")
    expected_steps = []
    cumulative = 0.0
    def on_step(step):
        nonlocal cumulative
        e, d = step["estimate"], step["decision"]
        seq = d["client_seq"]
        cumulative += d["distance_delta_m"]
        expected_steps.append({"ingress_seq": seq, "source_epoch": observations[seq].source_epoch,
                               "clock_epoch_id": observations[seq].clock_epoch_id,
                               "position_quality": e["position_quality"], "estimate_reasons": sorted(e["reasons"]),
                               "from_seq": d["from_seq"], "to_seq": seq, "connection": d["connection"],
                               "distance_use": d["distance_use"], "distance_delta_m": d["distance_delta_m"],
                               "reasons": sorted(d["reasons"]), "cumulative_distance_m": cumulative})
    expected_summary = replay(manifest, observations, raw, on_step=on_step)
    expected = replay_shadow(manifest, observations, raw, owner_id=header["owner_id"], precision_fingerprint=precision_fp)
    local_diff = first_difference(kotlin["summary"], expected_summary)
    step_diff = first_difference(kotlin["steps"], expected_steps)
    result = {"format": "walk-motion-comparison-v1", "input_origin": "server-backup",
              "independent_device_input_verified": False, "coordinate_basis": basis,
              "source_integrity": "matched", "point_count": len(raw), "app_version": header["app_version"],
              "kotlin_vs_checkout": {"outcome": "mismatch" if local_diff or step_diff else "equivalent",
                                     "first_summary_difference": local_diff, "first_step_difference": step_diff}}
    meta, body = captured["motion-calculation.json"]
    if meta["http_status"] != 200:
        result["motion_api"] = {"outcome": "unavailable", "http_status": meta["http_status"]}
    else:
        api = json.loads(body)
        provenance = {"version": "gps-motion-calculation-v1", "walk_id": detail["id"], "client_session_id": manifest.client_session_id,
                      "coordinate_basis": basis, "config_hash": manifest.policy.config_hash,
                      "policy_version": manifest.policy.version, "measurement_version": manifest.policy.measurement_version,
                      "manifest_fingerprint": manifest_digest(manifest), "evidence_fingerprint": evidence,
                      "precision_fingerprint": precision_fp, "device_result_verified": False}
        input_diff = first_difference(provenance, {k: api.get(k) for k in provenance})
        output_diff = first_difference(kotlin["summary"], {k: api.get(k) for k in expected_summary})
        result["motion_api"] = {"outcome": "incomparable" if input_diff else "mismatch" if output_diff else "equivalent",
                                "first_input_difference": input_diff, "first_result_difference": output_diff}
    meta, body = captured["trajectory-calculation.json"]
    if meta["http_status"] != 200:
        result["trajectory_api"] = {"outcome": "unavailable", "http_status": meta["http_status"]}
    else:
        require(meta["etag"] == '"' + hashlib.sha256(body.encode()).hexdigest() + '"', "trajectory_etag")
        wire = json.loads(body)
        exact_time_refs(wire)
        require(wire["device_result_verified"] is False and wire["measurement"]["source"] == "server", "trajectory_source")
        api = TrajectoryCalculation.model_validate(wire)
        require(str(api.walk_id) == detail["id"], "trajectory_walk")
        require(api.measurement.scope == expected.snapshot.scope, "trajectory_scope")
        require(api.result_digest == api.measurement.ref().result_digest, "trajectory_digest")
        require(api.measurement.measurement_id == "shadow-" + digest({"key": api.measurement.key.model_dump(), "result": api.result_digest}), "trajectory_id")
        verification = compare_measurements(expected.snapshot, api.measurement)
        expected_view = {"metrics": expected.snapshot.ledger.metrics().model_dump(),
                         "observed_runs": [s.model_dump() for s in expected.observed_runs],
                         "walking_sections": [s.model_dump() for s in expected.walking_sections],
                         "boundaries": expected.boundaries.model_dump(),
                         "locations": [p.model_dump() for p in expected.locations],
                         "motion_recording_duration_ns": expected.motion_active_duration_ns,
                         "observation_policy": expected.observation_policy.model_dump(),
                         "average_walking_speed_mps": expected.snapshot.ledger.metrics().average_walking_speed_mps,
                         "located_fraction_of_known_time": expected.snapshot.ledger.metrics().located_fraction_of_known_time}
        projection_diff = first_difference(expected_view, {k: api.model_dump()[k] for k in expected_view})
        epochs = {e.source_epoch: e for e in manifest.epochs}
        expected_times = []
        for event in expected.snapshot.ledger.journal.events:
            ref = event.ref
            if ref.ingress_seq is None:
                epoch = epochs[ref.source_epoch]
                time = epoch.started_at_millis if ref.control_kind == "epoch_start" else epoch.ended_at_millis
            else:
                dt = raw[ref.ingress_seq].at - datetime(1970, 1, 1, tzinfo=UTC)
                time = dt.days * 86_400_000 + dt.seconds * 1000 + dt.microseconds // 1000
            expected_times.append({"ref": ref.model_dump(), "original_wall_time_millis": time})
        wall_diff = first_difference(expected_times, [t.model_dump() for t in api.wall_times])
        ledger_diff = first_difference(expected.snapshot.result_payload(), api.measurement.result_payload())
        result["trajectory_api"] = {"outcome": verification.outcome if verification.outcome != "equivalent" else
                                     "mismatch" if projection_diff or wall_diff else "equivalent",
                                     "verification_reasons": verification.reasons,
                                     "first_ledger_difference": ledger_diff, "first_projection_difference": projection_diff,
                                     "first_wall_time_difference": wall_diff}
    git = lambda *args: subprocess.check_output(["git", "-C", str(backend), *args], text=True).strip()
    result["backend_commit"] = git("rev-parse", "HEAD")
    result["backend_dirty"] = bool(git("status", "--porcelain"))
    loaded_sources = {Path(module.__file__).resolve() for name, module in sys.modules.items()
                      if name.startswith(("daengs_backend.", "daengs_walk")) and getattr(module, "__file__", None)}
    result["backend_source_sha256"] = {p.relative_to(backend.resolve()).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                                       for p in sorted(loaded_sources) if p.is_relative_to(backend.resolve())}
    result["same_backup_comparison"] = "equivalent" if all(result[k]["outcome"] == "equivalent" for k in
        ("kotlin_vs_checkout", "motion_api", "trajectory_api")) else "not_equivalent"
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", type=Path, required=True)
    parser.add_argument("--backend", type=Path, required=True, help="Explicit DAENGS_dev/backend checkout")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = compare(args.case, args.backend)
    except Exception as error:
        # Pydantic errors can contain source coordinates. Keep console/report rejection bounded.
        message = str(error)
        result = {"format": "walk-motion-comparison-v1", "same_backup_comparison": "rejected",
                  "error_type": type(error).__name__,
                  "reason": message if re.fullmatch(r"[a-z0-9_-]{1,100}", message) else "invalid_capture_or_contract",
                  "independent_device_input_verified": False}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    # Detailed refs and differences stay in the private file, never in console output.
    print(json.dumps({k: result[k] for k in ("same_backup_comparison", "point_count", "coordinate_basis",
                                           "independent_device_input_verified", "reason") if k in result}, ensure_ascii=False))
    return 0 if result["same_backup_comparison"] == "equivalent" else 2


if __name__ == "__main__":
    raise SystemExit(main())
