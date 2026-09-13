# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Run the opt-in synthetic measurement APK in separate Android processes."""
import argparse
from pathlib import Path
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--range-context", action="store_true", help="Verify range/scene return and bounded playback across three app processes")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    adb = [args.adb, "-s", args.serial]
    package = "com.daengs.app.locationreview"
    phases = (["prepareRangeScene", "verifyRangeSceneAndPlaybackEnd", "verifyBoundedReplayReopened"]
              if args.range_context else ["prepareScene", "verifySceneAndPrepareRange", "verifyRangeAndPrepareReplay", "verifyPausedReplay"])
    for phase in phases:
        subprocess.run(adb + ["shell", "am", "force-stop", package], check=True, timeout=20)
        result = subprocess.run(adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
            "com.daengs.app.ui.walk.MeasurementDeviceTest#" + phase,
            package + ".test/androidx.test.runner.AndroidJUnitRunner"],
            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=180)
        log = result.stdout + result.stderr
        (args.output / (phase + ".log")).write_text(log, encoding="utf-8")
        # am instrument can exit 0 even when JUnit fails. Verify the runner result explicitly.
        if result.returncode or not re.search(r"(?m)^OK \(1 test\)\s*$", log) or "FAILURES!!!" in log:
            raise SystemExit(f"FAIL {phase}: inspect {args.output / (phase + '.log')}")
        print(f"PASS {phase}", flush=True)


if __name__ == "__main__":
    main()
