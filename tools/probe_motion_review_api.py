# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Read public OpenAPI only; distinguish runtime route registration from backup availability."""
import argparse
from datetime import UTC, datetime
import hashlib
import json
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler

REQUIRED = ("/app/walks/motion-capabilities", "/app/walks/{walk_id}/motion-backup",
            "/app/walks/trajectory-capabilities", "/app/walks/{walk_id}/trajectory-calculation")


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def probe(origin):
    uri = urlsplit(origin)
    if uri.scheme not in {"http", "https"} or not uri.hostname or uri.username or uri.password or uri.query or uri.fragment or uri.path not in {"", "/"}:
        raise ValueError("Expected a bare HTTP(S) origin, without credentials")
    request = Request(origin.rstrip("/") + "/openapi.json", headers={"Cache-Control": "no-cache"})
    with build_opener(NoRedirect).open(request, timeout=20) as response:
        body = response.read(8 * 1024 * 1024 + 1)
        if len(body) > 8 * 1024 * 1024:
            raise ValueError("OpenAPI response too large")
    paths = json.loads(body)["paths"]
    return {"origin": origin, "checked_at": datetime.now(UTC).isoformat(), "openapi_sha256": hashlib.sha256(body).hexdigest(),
            "required_get_routes": {path: "get" in paths.get(path, {}) for path in REQUIRED},
            "backup_availability": "not_checked", "recording_device_input": "not_checked"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--origin", action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = {"format": "walk-motion-route-probe-v1", "servers": [probe(origin) for origin in args.origin]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
