# /// script
# requires-python = ">=3.12"
# dependencies = ["pydantic==2.13.4", "tzdata==2026.3"]
# ///
"""Export DEV v3 contract data with synthetic temperatures and a fixed writer.

uv run tools/build_diary_slot_fixture.py --dev-src ../DAENGS_dev/backend/src
No DB, KMA, Gemini, credentials or production records are used.
"""

import argparse
import asyncio
import sys
from datetime import timedelta
from pathlib import Path


async def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dev-src", type=Path, required=True)
    parser.add_argument(
        "--output", type=Path,
        default=Path(__file__).resolve().parents[1]
        / "app/src/test/resources/diary-slots-preview-v3.json",
    )
    args = parser.parse_args()
    sys.path.insert(0, str(args.dev_src.resolve()))

    from daengs_backend.schemas.walk_diary_slots import SlotPreviewResponse
    from daengs_backend.services.walk_diary_slot_writing import write_slot_preview
    from daengs_evals.diary_slots_demo import demo_input
    from daengs_walk.diary_board import BaseBoardPolicy
    from daengs_walk.diary_input import DiaryInput, digest
    from daengs_walk.diary_slots import SlotPolicy, prepare_slot_preview
    from daengs_walk.diary_stamps import StampPolicy
    from daengs_walk.diary_temperature import GridTemperature

    source, route, _ = demo_input()
    raw = source.model_dump(mode="json")
    for index, record in enumerate(source.records[:2], 1):
        observed = record.anchor.event_at.replace(minute=0, second=0, microsecond=0)
        if index == 2:
            observed -= timedelta(hours=3)  # Too old: server must exclude this observation.
        payload = GridTemperature(
            provider="kma-vilage-fcst:ncst", query_point=record.anchor.point,
            requested_at=record.anchor.event_at, fetched_at=source.ended_at,
            grid=(61, 125), observed_at=observed, issued_at=observed, temperature_c=22.5,
        ).model_dump(mode="json")
        saved = next(b for b in raw["backgrounds"] if b["id"] == f"environment-{index}")
        saved.update(payload=payload, payload_sha256=digest(payload))
    # Third record has no environment source at all, while space and motion remain.
    raw["backgrounds"] = [b for b in raw["backgrounds"] if b["id"] != "environment-3"]
    raw["selected_background_ids"] = [b["id"] for b in raw["backgrounds"]]
    preview = prepare_slot_preview(
        DiaryInput.model_validate(raw), SlotPolicy(),
        BaseBoardPolicy(intermediate=StampPolicy(target_scene_count=3)), route=route,
    )

    async def fixed_writer(payload, schema):
        scenes = []
        for scene in payload["scenes"]:
            temperature = next(
                (e for e in scene["evidence"] if e["role"] == "grid_temperature_observation"), None,
            )
            scenes.append({
                "scene_id": scene["scene_id"],
                "background": "기록보다 80초 앞선 해당 격자의 기온 관측값은 22.5도였다."
                if temperature else "",
                "evidence_ids": [temperature["id"]] if temperature else [],
            })
        return {"scenes": scenes}

    written = await write_slot_preview(preview, fixed_writer)
    assert written.model_status == "accepted"
    assert written.policy.version == "diary-part-slots-v3"
    assert sum(e.part == "environment" for s in written.stamps for e in s.evidence) == 1
    assert any(d.reason == "weather_observation_too_old" for s in written.stamps for d in s.decisions)
    response = SlotPreviewResponse(preview=written, context_pending=False, excluded_backgrounds=())
    args.output.write_text(response.model_dump_json(indent=2) + "\n", encoding="utf-8")
    print(f"Exported {len(written.scenes)} synthetic scenes with policy {written.policy.version}")


if __name__ == "__main__":
    asyncio.run(main())
