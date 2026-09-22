#!/usr/bin/env python3
"""Host tests for install-sdk.py; no SDK, no network, no real subprocesses."""
import contextlib
import importlib.util
import io
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

_spec = importlib.util.spec_from_file_location(
    "install_sdk",
    Path(__file__).with_name("install-sdk.py"),
)
install_sdk = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(install_sdk)

COMMAND = ["sdkmanager", "--install", "platforms;android-34"]
PACKAGES = ["platforms;android-34"]


class FakeRunner:
    """Stands in for subprocess.run: replays outcomes, records call kwargs."""

    def __init__(self, *outcomes, on_call=None):
        self.outcomes = list(outcomes)
        self.on_call = on_call
        self.calls = []

    def __call__(self, command, **kwargs):
        self.calls.append(kwargs)
        if self.on_call:
            self.on_call(len(self.calls))
        outcome = self.outcomes.pop(0)
        if outcome == "timeout":
            raise subprocess.TimeoutExpired(command, kwargs.get("timeout"))
        return subprocess.CompletedProcess(command, outcome)


class InstallTest(unittest.TestCase):

    def setUp(self):
        self.sleeps = []
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: shutil.rmtree(self.root, ignore_errors=True))

    def run_install(self, runner, timeout=5):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()) as err:
            code = install_sdk.install(
                self.root,
                PACKAGES,
                COMMAND,
                timeout=timeout,
                runner=runner,
                sleeper=self.sleeps.append,
            )
        return code, err.getvalue()

    def test_success_on_first_attempt(self):
        runner = FakeRunner(0)
        code, _ = self.run_install(runner)
        self.assertEqual(0, code)
        self.assertEqual(1, len(runner.calls))
        self.assertEqual([], self.sleeps)

    def test_retry_after_transient_failure(self):
        runner = FakeRunner(1, 0)
        code, _ = self.run_install(runner)
        self.assertEqual(0, code)
        self.assertEqual(2, len(runner.calls))
        self.assertEqual([10], self.sleeps)

    def test_exhaustion_reports_failure(self):
        runner = FakeRunner(1, 1, 7)
        code, err = self.run_install(runner)
        self.assertEqual(7, code)
        self.assertEqual(3, len(runner.calls))
        self.assertEqual([10, 20], self.sleeps)
        self.assertIn("failed after 3 attempts", err)
        # Child streams are inherited, so its diagnostics reach the caller.
        for key in ("capture_output", "stdout", "stderr"):
            self.assertNotIn(key, runner.calls[-1])

    def test_timeout_is_a_failed_attempt(self):
        runner = FakeRunner("timeout", 0)
        code, err = self.run_install(runner, timeout=42)
        self.assertEqual(0, code)
        self.assertEqual(2, len(runner.calls))
        self.assertEqual([42, 42], [call["timeout"] for call in runner.calls])
        self.assertIn("timed out after 42s", err)

    def test_timeouts_exhaust_attempts(self):
        runner = FakeRunner("timeout", "timeout", "timeout")
        code, _ = self.run_install(runner)
        self.assertNotEqual(0, code)
        self.assertEqual(3, len(runner.calls))

    def test_cleanup_only_clears_new_operations(self):
        temporary = self.root / ".temp"
        (temporary / "PackageOperation01" / "sub").mkdir(parents=True)
        (temporary / "unrelated").mkdir()

        def create_leftover(call):
            if call == 1:
                (temporary / "PackageOperation02" / "sub").mkdir(parents=True)

        code, _ = self.run_install(FakeRunner(1, 0, on_call=create_leftover))
        self.assertEqual(0, code)
        self.assertFalse((temporary / "PackageOperation02").exists())
        self.assertTrue((temporary / "PackageOperation01" / "sub").is_dir())
        self.assertTrue((temporary / "unrelated").is_dir())

    def test_cleanup_failure_does_not_mask_result(self):
        with mock.patch.object(install_sdk, "clear_operations", side_effect=OSError("denied")):
            code, err = self.run_install(FakeRunner(1, 0))
        self.assertEqual(0, code)
        self.assertIn("Could not clear SDK temp leftovers", err)


class CliDetectionTest(unittest.TestCase):

    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: shutil.rmtree(self.root, ignore_errors=True))
        self.bin = self.root / "cmdline-tools" / "latest" / "bin"
        self.bin.mkdir(parents=True)

    def make_executable(self, name):
        path = self.bin / name
        path.write_text("#!/bin/sh\n")
        path.chmod(0o755)
        return path

    def test_prefers_sdkmanager_over_android(self):
        self.make_executable("android")
        expected = self.make_executable("sdkmanager")
        with mock.patch.dict(os.environ, {"PATH": ""}, clear=True):
            self.assertEqual(expected, install_sdk.find_cli(self.root))

    def test_falls_back_to_android_cli(self):
        expected = self.make_executable("android")
        with mock.patch.dict(os.environ, {"PATH": ""}, clear=True):
            self.assertEqual(expected, install_sdk.find_cli(self.root))

    def test_command_shapes(self):
        self.assertEqual(
            ["/sdk/sdkmanager", "--sdk_root=/sdk", "--install", "platforms;android-34"],
            install_sdk.build_command(Path("/sdk/sdkmanager"), Path("/sdk"), PACKAGES),
        )
        self.assertEqual(
            ["/sdk/android", "sdk", "install", "platforms;android-34"],
            install_sdk.build_command(Path("/sdk/android"), Path("/sdk"), PACKAGES),
        )

    def test_missing_cli_fails_loudly(self):
        env = {"ANDROID_SDK_ROOT": str(self.root), "PATH": ""}
        with mock.patch.dict(os.environ, env, clear=True):
            with contextlib.redirect_stderr(io.StringIO()) as err:
                with self.assertRaises(SystemExit) as raised:
                    install_sdk.main(PACKAGES)
        self.assertEqual(2, raised.exception.code)
        self.assertIn("sdkmanager", err.getvalue())

    def test_missing_sdk_root_fails_loudly(self):
        with mock.patch.dict(os.environ, {"PATH": ""}, clear=True):
            with contextlib.redirect_stderr(io.StringIO()) as err:
                with self.assertRaises(SystemExit) as raised:
                    install_sdk.main(PACKAGES)
        self.assertEqual(2, raised.exception.code)
        self.assertIn("ANDROID_SDK_ROOT", err.getvalue())


if __name__ == "__main__":
    unittest.main()
