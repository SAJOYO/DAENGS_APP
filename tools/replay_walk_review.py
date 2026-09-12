# /// script
# requires-python = ">=3.11"
# dependencies = ["pydantic>=2,<3", "tzdata"]
# ///
"""Replay a private review capture through the specified DEV checkout's pure facts calculator.

No network or database access. Run with uv run; output stays beside the private capture.
"""
import argparse
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import sys
from uuid import UUID


def replay(case: Path, backend: Path) -> dict:
    report = json.loads((case / "report.json").read_bytes())
    for name in ("detail", "storyboard"):
        if hashlib.sha256((case / (name + ".json")).read_bytes()).hexdigest() != report[name + "_sha256"]:
            raise ValueError(f"{name} does not match the captured report")
    raw = json.loads((case / "detail.json").read_bytes())
    if raw["id"] != report["server_walk_id"] or raw["client_session_id"] != report["client_session_id"]:
        raise ValueError("Capture and report refer to different walks")
    sys.path.insert(0, str(backend.resolve() / "src"))
    from daengs_walk.contracts import WalkEvidencePoint
    from daengs_walk.facts import compute_walk_facts

    result = compute_walk_facts(UUID(raw["id"]), datetime.fromisoformat(raw["started_at"]),
                                datetime.fromisoformat(raw["ended_at"]),
                                [WalkEvidencePoint.model_validate(p) for p in raw["points"]])
    fast = [segment for segment in result.segments if segment.dt > 0 and segment.dist / segment.dt > 7]
    git = lambda *args: subprocess.check_output(["git", "-C", str(backend), *args], text=True).strip()
    output = {
        "format": "walk-real-record-server-replay-v1",
        "backend_commit": git("rev-parse", "HEAD"),
        "backend_dirty": bool(git("status", "--porcelain")),
        "facts_source_sha256": hashlib.sha256((backend / "src/daengs_walk/facts.py").read_bytes()).hexdigest(),
        "detail_sha256": report["detail_sha256"],
        "storyboard_sha256": report["storyboard_sha256"],
        "point_count": len(raw["points"]),
        "facts": result.facts.model_dump(mode="json"),
        "quality": result.quality.model_dump(mode="json"),
        "connected_distance_m": sum(s.dist for s in result.segments),
        "over_7_mps": {"segments": len(fast), "distance_m": sum(s.dist for s in fast),
                       "duration_s": sum(s.dt for s in fast)},
        "app_distance_m": report["distance_m"],
        "app_trace_counts": dict(Counter(p["disposition"] for p in report.get("legacy_trace", {}).get("decisions", []))),
        "app_without_speed_limit_m": report.get("without_speed_limit", {}).get("distance_m"),
        "app_without_speed_or_min_distance_m": report.get("without_speed_or_min_distance", {}).get("distance_m"),
        "scene_relations": dict(Counter(s["relation"] for s in report["scenes"])),
    }
    (case / "server-facts-replay.json").write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", type=Path, required=True, help="Capture folder containing report.json")
    parser.add_argument("--backend", type=Path, required=True, help="DAENGS_dev/backend checkout")
    args = parser.parse_args()
    result = replay(args.case, args.backend)
    # Console summary omits account IDs, coordinates, and free-form scene content.
    print(json.dumps({k: v for k, v in result.items() if k != "facts"}, ensure_ascii=False, indent=2))
    print(json.dumps({k: result["facts"][k] for k in ("distance_m", "moving_distance_m", "duration_s", "moving_s")}, indent=2))


if __name__ == "__main__":
    main()
