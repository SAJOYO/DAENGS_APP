import importlib.util
import io
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("probe", Path(__file__).parents[1] / "run_measurement_capabilities_review.py")
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


def archive(value):
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode="w") as tar:
        row = tarfile.TarInfo("databases/synthetic.db")
        row.size = len(value)
        tar.addfile(row, io.BytesIO(value))
    return output.getvalue()


class ProbeGuardTest(unittest.TestCase):
    def invoke(self, replies, result=None):
        with tempfile.TemporaryDirectory() as directory:
            args = ["probe", "--adb", "adb", "--serial", "synthetic", "--output", str(Path(directory) / "run.log")]
            with patch.object(sys, "argv", args), \
                    patch.object(probe.subprocess, "check_output", side_effect=replies), \
                    patch.object(probe.subprocess, "run", return_value=result) as run:
                with self.assertRaises(SystemExit) as failure:
                    probe.main()
                return str(failure.exception), run.call_count

    def test_running_app_never_starts_instrumentation(self):
        reason, calls = self.invoke([b"NAME\ncom.daengs.app.devtest\n"])
        self.assertIn("Development app is running", reason)
        self.assertEqual(0, calls)

    def test_app_starting_during_snapshot_never_starts_instrumentation(self):
        reason, calls = self.invoke([b"NAME\n", archive(b"original"), b"NAME\ncom.daengs.app.devtest:tracking\n"])
        self.assertIn("started during preflight", reason)
        self.assertEqual(0, calls)

    def test_junit_success_cannot_hide_a_database_change(self):
        reason, calls = self.invoke([b"NAME\n", archive(b"original"), b"NAME\n", archive(b"changed")],
            subprocess.CompletedProcess([], 0, "OK (1 test)\n", ""))
        self.assertIn("database or preferences changed", reason)
        self.assertEqual(1, calls)


if __name__ == "__main__":
    unittest.main()
