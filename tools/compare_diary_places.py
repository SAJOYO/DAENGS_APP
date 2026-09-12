# /// script
# requires-python = ">=3.12"
# dependencies = ["google-genai==2.18.1", "httpx==0.28.1", "pydantic==2.13.4", "tzdata==2026.3"]
# ///
"""Generate a private comparison for the exact scenes exported by the Debug app.

Uses the DEV writer prompt/schema, a public Place read API and optional Kakao address
lookup. Does not access the member DB or change the published diary. Input, evidence,
and output must stay outside Git. Keys are read locally and never included in output.
"""

import argparse
import asyncio
import hashlib
import json
import math
import sys
from datetime import UTC, datetime
from pathlib import Path

import httpx

PLACE_ONLY_PROMPT = """
이번 비교는 현재 조회한 장소 자료로 고정된 장면 위치의 배경만 쓴다.
원문은 앱에서 그대로 붙이므로 모델에는 전달하지 않는다.
주소만 있으면 행정구역 위치만 설명한다. 주소를 산책로·보도·공원·주택가로 해석하지 않는다.
시설명이 있으면 명칭과 등록 위치의 거리 관계를 1문장으로 설명한다.
진행·지남·통과·머무름·도착 등 동선이나 행동은 이 입력에 근거가 없으므로 쓰지 않는다.
각 장소의 현재 등록 정보를 과거 산책 당시의 영업·존재 상태로 단정하지 않는다.
"""


def read_keys(path):
    values = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line.lower().startswith("gemini:"):
            values["GEMINI_API_KEY"] = line.split(":", 1)[1].strip().strip("\"'")
        name, sep, value = line.partition("=")
        if sep and name.strip() in {
            "GEMINI_API_KEY",
            "GOOGLE_API_KEY",
            "DAENGS_KAKAO_REST_KEY",
        }:
            values[name.strip()] = value.strip().strip("\"'")
    return values


async def collect_places(client, scene, index, base_url, kakao_key, captured):
    point = scene["point"]
    evidence, diagnostics = [], []
    if point is None:
        return evidence, ["no_location"]
    lat, lng = point
    if not (
        math.isfinite(lat)
        and math.isfinite(lng)
        and -90 <= lat <= 90
        and -180 <= lng <= 180
    ):
        raise ValueError("Invalid scene coordinates")
    try:
        response = await client.post(
            base_url.rstrip("/") + "/v2/places/search",
            json={
                "lat": lat,
                "lng": lng,
                "radius_m": 250,
                "kinds": ["leisure", "cafe", "restaurant"],
                "limit_per_kind": 3,
            },
        )
        response.raise_for_status()
        candidates = []
        for group in response.json()["groups"]:
            for hit in group["results"]:
                place = hit["place"]
                distance = float(place["distance_m"])
                if not math.isfinite(distance) or not 0 <= distance <= 250:
                    continue
                candidates.append((distance, place, group["kind"]))
        seen = set()
        for distance, place, kind in sorted(candidates, key=lambda item: item[0]):
            source = f"{place['key']['source']}:{place['key']['ref']}"
            if source in seen:
                continue
            seen.add(source)
            name = str(place["name"])[:100]
            evidence.append(
                {
                    "id": f"s{index}-place{len(evidence) + 1}",
                    "part": "space",
                    "role": "scene_registered_point_distance",
                    "facts": {
                        "name": name,
                        "distance_m": round(distance, 1),
                        "kind": kind,
                        "retrieved_at": captured,
                        "source": source,
                        "interpretation": "현재 장소 목록의 등록 위치와 장면 좌표 사이 거리. 과거 영업 여부·방문·진입·접근·행동은 알 수 없음.",
                    },
                    "description": f"{name} · 등록 위치까지 약 {round(distance)}m · {source}\n조회: {captured}\n방문·영업·진입·행동의 증거가 아님.",
                }
            )
            if len(evidence) == 3:
                break
        diagnostics.append(f"places_ok:{len(evidence)}")
    except httpx.HTTPStatusError as error:
        diagnostics.append(f"places_http_{error.response.status_code}")
    except (httpx.RequestError, KeyError, ValueError, TypeError):
        diagnostics.append("places_unavailable")
    if not evidence and kakao_key:
        # The shared catalog can be empty in this area. Use a bounded, explicit live lookup.
        try:
            candidates = []
            for kind, params in (
                ("keyword", {"query": "공원"}),
                ("category", {"category_group_code": "CE7"}),
            ):
                response = await client.get(
                    f"https://dapi.kakao.com/v2/local/search/{kind}.json",
                    params={
                        **params,
                        "x": lng,
                        "y": lat,
                        "radius": 250,
                        "sort": "distance",
                        "size": 3,
                    },
                    headers={"Authorization": "KakaoAK " + kakao_key},
                )
                response.raise_for_status()
                for place in response.json()["documents"]:
                    distance = float(place["distance"])
                    # Keyword results may match a name outside the requested radius/category.
                    if not math.isfinite(distance) or not 0 <= distance <= 250:
                        continue
                    if kind == "keyword" and "공원" not in place.get(
                        "category_name", ""
                    ):
                        continue
                    candidates.append((distance, place))
            seen = set()
            for distance, place in sorted(candidates, key=lambda item: item[0]):
                if place["id"] in seen:
                    continue
                seen.add(place["id"])
                name = place["place_name"][:100]
                source = "kakao-local:" + place["id"]
                evidence.append(
                    {
                        "id": f"s{index}-place{len(evidence) + 1}",
                        "part": "space",
                        "role": "scene_registered_point_distance",
                        "facts": {
                            "name": name,
                            "distance_m": distance,
                            "category": place["category_name"],
                            "source": source,
                            "retrieved_at": captured,
                            "interpretation": "현재 장소 목록의 등록 위치와 장면 좌표 사이 거리. 과거 영업 여부·방문·진입·접근·행동은 알 수 없음.",
                        },
                        "description": f"{name} · 등록 위치까지 약 {round(distance)}m · {source}\n조회: {captured}\n방문·영업·진입·행동의 증거가 아님.",
                    }
                )
                if len(evidence) == 3:
                    break
            diagnostics.append(f"kakao_places_ok:{len(evidence)}")
        except httpx.HTTPStatusError as error:
            diagnostics.append(f"kakao_places_http_{error.response.status_code}")
        except (httpx.RequestError, KeyError, ValueError, TypeError):
            diagnostics.append("kakao_places_unavailable")
    if kakao_key:
        try:
            response = await client.get(
                "https://dapi.kakao.com/v2/local/geo/coord2address.json",
                params={"x": lng, "y": lat},
                headers={"Authorization": "KakaoAK " + kakao_key},
            )
            response.raise_for_status()
            docs = response.json()["documents"]
            if docs:
                address = docs[0].get("address") or {}
                name = " ".join(
                    str(address.get(f"region_{i}depth_name", "")) for i in (1, 2, 3)
                ).strip()
                if name:
                    evidence.append(
                        {
                            "id": f"s{index}-address",
                            "part": "space",
                            "role": "scene_address_reference",
                            "facts": {
                                "address": name,
                                "retrieved_at": captured,
                                "interpretation": "현재 역지오코딩으로 확인한 장면 좌표의 행정구역. 이동 방향이나 특정 시설 방문 근거가 아님.",
                            },
                            "description": f"{name} · Kakao 좌표 주소 변환\n조회: {captured}",
                        }
                    )
            diagnostics.append("address_ok")
        except httpx.HTTPStatusError as error:
            diagnostics.append(f"address_http_{error.response.status_code}")
        except (httpx.RequestError, KeyError, ValueError, TypeError):
            diagnostics.append("address_unavailable")
    return evidence, diagnostics


def assemble_result(request, snapshot_hash, all_evidence, writing, model, captured):
    expected = {scene["id"] for scene in request["scenes"] if all_evidence[scene["id"]]}
    written = {item.scene_id: item for item in writing.scenes}
    if len(written) != len(writing.scenes) or set(written) != expected:
        raise ValueError("Writer changed the scene set")
    scenes = []
    for original in request["scenes"]:
        evidence = all_evidence[original["id"]]
        paragraph = written.get(original["id"])
        background = paragraph.background.strip() if paragraph else ""
        citations = list(paragraph.evidence_ids) if paragraph else []
        allowed = {item["id"] for item in evidence}
        if (
            len(citations) != len(set(citations))
            or not set(citations) <= allowed
            or bool(background) != bool(citations)
        ):
            raise ValueError("Writer returned invalid citations")
        scenes.append(
            {
                "id": original["id"],
                "background": background,
                "evidence_ids": citations,
                "evidence": [
                    {"id": item["id"], "description": item["description"]}
                    for item in evidence
                ],
            }
        )
    return {
        "format": "diary-place-comparison-result-v1",
        "snapshot_sha256": snapshot_hash,
        "model_status": "accepted",
        "model": model,
        "retrieved_at": captured,
        "scenes": scenes,
    }


async def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--request", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--env-file", type=Path, required=True)
    parser.add_argument("--dev-src", type=Path, required=True)
    parser.add_argument("--place-api", default="http://daengback.weareithero.cloud")
    args = parser.parse_args()
    sys.path.insert(0, str(args.dev_src.resolve()))
    from daengs_backend.services.walk_diary_slot_writing import (
        MAX_INPUT_BYTES,
        MAX_OUTPUT_TOKENS,
        MAX_RESPONSE_BYTES,
        MODEL,
        PROMPT,
        TIMEOUT_SECONDS,
        WrittenScenes,
    )

    raw = args.request.read_bytes()
    if len(raw) > 128_000:
        raise ValueError("Input exceeds comparison budget")
    request = json.loads(raw)
    if (
        request["format"] != "diary-place-comparison-input-v1"
        or not 1 <= len(request["scenes"]) <= 12
    ):
        raise ValueError("Invalid comparison input")
    if len({s["id"] for s in request["scenes"]}) != len(request["scenes"]):
        raise ValueError("Duplicate scenes")
    keys = read_keys(args.env_file)
    key = keys.get("GEMINI_API_KEY") or keys.get("GOOGLE_API_KEY")
    if not key:
        raise ValueError("GEMINI_API_KEY missing")
    captured = datetime.now(UTC).isoformat()
    all_evidence, diagnostic = {}, []
    async with httpx.AsyncClient(timeout=10.0) as client:
        for index, scene in enumerate(request["scenes"], 1):
            evidence, status = await collect_places(
                client,
                scene,
                index,
                args.place_api,
                keys.get("DAENGS_KAKAO_REST_KEY", ""),
                captured,
            )
            all_evidence[scene["id"]] = evidence
            diagnostic.append(
                {
                    "scene_index": index,
                    "evidence_count": len(evidence),
                    "status": status,
                }
            )
    payload = {
        "scenes": [
            {
                "scene_id": scene["id"],
                "original": "",
                "evidence": [
                    {k: item[k] for k in ("id", "part", "role", "facts")}
                    for item in all_evidence[scene["id"]]
                ],
            }
            for scene in request["scenes"]
            if all_evidence[scene["id"]]
        ]
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix(".evidence.json").write_text(
        json.dumps(
            {
                "retrieved_at": captured,
                "snapshot_sha256": hashlib.sha256(raw).hexdigest(),
                "evidence": all_evidence,
                "diagnostics": diagnostic,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    if not payload["scenes"]:
        raise ValueError("No place evidence; model not called")
    if len(json.dumps(payload, ensure_ascii=False).encode()) > MAX_INPUT_BYTES:
        raise ValueError("Input exceeds DEV writer budget")
    from google import genai
    from google.genai import types

    async with genai.Client(
        api_key=key,
        http_options=types.HttpOptions(
            timeout=TIMEOUT_SECONDS * 1000,
            retry_options=types.HttpRetryOptions(attempts=1),
        ),
    ).aio as client:
        response = await asyncio.wait_for(
            client.models.generate_content(
                model=MODEL,
                contents=json.dumps(payload, ensure_ascii=False),
                config=types.GenerateContentConfig(
                    system_instruction=PROMPT + PLACE_ONLY_PROMPT,
                    temperature=0,
                    candidate_count=1,
                    max_output_tokens=MAX_OUTPUT_TOKENS,
                    automatic_function_calling=types.AutomaticFunctionCallingConfig(
                        disable=True
                    ),
                    response_mime_type="application/json",
                    response_json_schema=WrittenScenes.model_json_schema(),
                ),
            ),
            timeout=TIMEOUT_SECONDS + 2,
        )
    model_text = response.text
    if not model_text or len(model_text.encode()) > MAX_RESPONSE_BYTES:
        raise ValueError("Invalid model response size")
    writing = WrittenScenes.model_validate_json(model_text)
    result = assemble_result(
        request, hashlib.sha256(raw).hexdigest(), all_evidence, writing, MODEL, captured
    )
    result["writer_prompt_sha256"] = hashlib.sha256(
        (PROMPT + PLACE_ONLY_PROMPT).encode()
    ).hexdigest()
    args.output.write_text(
        json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(
        json.dumps(
            {
                "model_status": result["model_status"],
                "scenes": len(result["scenes"]),
                "generated": sum(bool(s["background"]) for s in result["scenes"]),
                "diagnostics": diagnostic,
            }
        )
    )


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as error:  # noqa: BLE001 - provider exceptions may expose credentials
        # Provider exceptions can contain request URLs or credentials. Keep output sanitized.
        print(f"Comparison failed: {type(error).__name__}", file=sys.stderr)
        raise SystemExit(1)
