# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Probe an idle development installation without booting its Application or providers."""
import argparse
import hashlib
import io
import re
import subprocess
import tarfile
from pathlib import Path

PACKAGE = "com.daengs.app.devtest"
RUNNER = PACKAGE + ".test/com.daengs.app.walk.sync.MeasurementReadRunner"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--bootstrap-only", action="store_true", help="Verify isolation without reading credentials or making HTTP requests")
    args = parser.parse_args()
    adb = [args.adb, "-s", args.serial]

    def running():
        result = subprocess.check_output(adb + ["shell", "ps", "-A", "-o", "NAME"], timeout=20).decode()
        return any(n == PACKAGE or n.startswith(PACKAGE + ":") for n in result.split())

    if running():
        raise SystemExit("Development app is running. Finish any walk and close the app before retrying; no instrumentation was started.")

    def snapshot():
        # Hash in memory; never write private DB/prefs or their contents into reports.
        data = subprocess.check_output(adb + ["exec-out", "run-as", PACKAGE, "tar", "-cf", "-", "databases", "shared_prefs"], timeout=20)
        with tarfile.open(fileobj=io.BytesIO(data)) as archive:
            return {m.name: hashlib.sha256(archive.extractfile(m).read()).digest()
                    for m in archive.getmembers() if m.isfile()}

    before = snapshot()
    if running():
        raise SystemExit("Development app started during preflight; no instrumentation was started.")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    method = "isolatedBootstrap" if args.bootstrap_only else "readCapabilities"
    try:
        result = subprocess.run(adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
            "com.daengs.app.walk.sync.MeasurementCapabilitiesDeviceTest#" + method, RUNNER],
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90)
        log = result.stdout + result.stderr
        args.output.write_text(log, encoding="utf-8")
    finally:
        if snapshot() != before:
            raise SystemExit("Development database or preferences changed during the probe; inspection required.")
    if result.returncode or not re.search(r"(?m)^OK \(1 test\)\s*$", log) or "FAILURES!!!" in log:
        raise SystemExit(f"Probe did not pass. See {args.output}; database and preferences are unchanged.")
    print("PASS " + method + "; database and preferences are unchanged")
    for line in log.splitlines():
        if line.startswith("Authenticated capabilities:"):
            print(line)


if __name__ == "__main__":
    main()
