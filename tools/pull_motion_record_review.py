# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Copy the isolated review app's motion exports. Does not read preferences or credentials."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import subprocess
import tarfile
from uuid import UUID


def unpack(payload: bytes, root: Path) -> int:
    root = root.resolve()
    contents = {}
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as archive:
        for member in archive:
            if member.isdir():
                continue
            path = PurePosixPath(member.name)
            if (not member.isfile() or path.is_absolute() or ".." in path.parts
                    or path.parts[0] != "motion-record-review" or path.suffix != ".json"):
                raise ValueError("Unexpected export member")
            target = root.joinpath(*path.parts).resolve()
            target.relative_to(root)
            if target in contents or member.size > 64 * 1024 * 1024:
                raise ValueError("Duplicate or oversized export")
            contents[target] = archive.extractfile(member).read()
            json.loads(contents[target])
    for target, data in contents.items():
        if target.name == "sources.json":
            for source in json.loads(data)["sources"]:
                name = source["file"]
                if PurePosixPath(name).name != name or not name.endswith(".json"):
                    raise ValueError("Invalid source name")
                body = json.loads(contents[target.with_name(name)])["body"].encode("utf-8")
                if hashlib.sha256(body).hexdigest() != source["body_sha256"]:
                    raise ValueError("Source hash mismatch")
        if target.exists() and target.read_bytes() != data:
            raise ValueError("Capture changed: choose a fresh output directory")
    for target, data in contents.items():
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    return len(contents)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--capture", type=UUID, help="Optional capture directory UUID")
    args = parser.parse_args()
    source = "motion-record-review" + (f"/{args.capture}" if args.capture else "")
    command = [args.adb, "-s", args.serial, "exec-out", "run-as", "com.daengs.app.walkreview",
               "tar", "-cf", "-", "-C", "no_backup", source]
    # Bound the stream before holding it in memory. Terminate only this tar reader if oversized.
    with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE) as process:
        payload = process.stdout.read(128 * 1024 * 1024 + 1)
        if len(payload) > 128 * 1024 * 1024:
            process.kill()
            raise ValueError("Export limit exceeded; select one --capture")
        if process.wait(timeout=30) != 0:
            raise RuntimeError("ADB export failed")
    print(json.dumps({"copied_json_files": unpack(payload, args.output), "credentials_exported": False}))


if __name__ == "__main__":
    main()
