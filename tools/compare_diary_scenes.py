# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Run the DEV scene comparison with DEV's locked dependencies and shared pipeline.

Usage: uv run tools/compare_diary_scenes.py --dev-root <DAENGS_dev> <DEV tool arguments>
See docs/diary-slot-preview.md. No local copy of provider selection or writer prompts.
"""

import argparse
import os
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__, add_help=False)
    parser.add_argument("--dev-root", type=Path, required=True)
    args, forwarded = parser.parse_known_args()
    backend = args.dev_root.resolve() / "backend"
    tool = backend / "tools" / "compare_diary_scenes.py"
    if not tool.is_file():
        parser.error(
            "DEV scene comparison is missing; use the scene-context branch (PR #474)"
        )
    environment = {k: v for k, v in os.environ.items() if k != "VIRTUAL_ENV"}
    raise SystemExit(
        subprocess.run(
            [
                "uv",
                "run",
                "--project",
                str(backend),
                str(tool),
                *forwarded,
            ],
            check=False,
            env=environment,
        ).returncode
    )


if __name__ == "__main__":
    main()
