# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Copy only private review exports from the dedicated debug app; never credentials."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import subprocess
import tarfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
payload = subprocess.check_output([
    args.adb, "-s", args.serial, "exec-out", "run-as", "com.daengs.app.walkreview",
    "tar", "-cf", "-", "-C", "no_backup", "real-record-review",
])
root = args.output.resolve()
root.mkdir(parents=True, exist_ok=True)
copied = 0
contents = {}
with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as archive:
    for member in archive.getmembers():
        path = PurePosixPath(member.name)
        if not member.isfile():
            continue
        if path.is_absolute() or ".." in path.parts or path.parts[0] != "real-record-review":
            raise ValueError("Export contains an unexpected path")
        if path.suffix != ".json":
            raise ValueError("Review export must contain only JSON")
        target = root.joinpath(*path.parts).resolve()
        target.relative_to(root)
        content = archive.extractfile(member).read()
        json.loads(content)
        if target in contents:
            raise ValueError("Export contains duplicate paths")
        contents[target] = content
for target, content in contents.items():
    if target.name == "report.json":
        report = json.loads(content)
        for name in ("detail", "storyboard"):
            source = contents[target.with_name(name + ".json")]
            if hashlib.sha256(source).hexdigest() != report[name + "_sha256"]:
                raise ValueError("Exported source does not match the report hash")
for target, content in contents.items():
    if target.exists() and target.name != "last-request.json" and target.read_bytes() != content:
        raise ValueError("Previously captured source changed; use another output directory")
for target, content in contents.items():
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(content)
    copied += 1
print(json.dumps({"copied_json_files": copied, "output": str(root), "credentials_exported": False}))
