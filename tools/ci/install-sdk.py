#!/usr/bin/env python3
"""Install SDK packages with bounded retries on a sequential CI runner.

Every attempt is capped by --timeout, so a stalled download or a prompt waiting
on stdin cannot eat the whole job budget before the retry logic ever runs.
Worst case wall clock is 3 attempts * timeout + 30s of retry delay, i.e. about
46 minutes with the default 15 minute timeout.
"""
import argparse
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

ATTEMPTS = 3
DEFAULT_TIMEOUT = 900
# One answer per licence the packages may still need; a short feed would leave
# the remaining prompts blocking on stdin.
LICENCE_ANSWERS = "y\n" * 64


def find_cli(sdk_root):
    """Classic sdkmanager if present, otherwise the newer `android` CLI."""
    candidates = [
        str(sdk_root / "cmdline-tools" / "latest" / "bin"),
        str(sdk_root / "tools" / "bin"),
        os.environ.get("PATH", ""),
    ]
    search = os.pathsep.join(entry for entry in candidates if entry)
    for name in ("sdkmanager", "android"):
        found = shutil.which(name, path=search)
        if found:
            return Path(found)
    return None


def build_command(cli, sdk_root, packages):
    if cli.name.startswith("sdkmanager"):
        return [str(cli), f"--sdk_root={sdk_root}", "--install", *packages]
    return [str(cli), "sdk", "install", *packages]


def clear_operations(temporary, known):
    for operation in sorted(set(temporary.glob("PackageOperation*")) - known):
        if operation.is_symlink() or not operation.is_dir():
            continue
        shutil.rmtree(operation)


def install(
    sdk_root,
    packages,
    command,
    timeout=DEFAULT_TIMEOUT,
    runner=subprocess.run,
    sleeper=time.sleep,
):
    temporary = sdk_root / ".temp"
    known = set(temporary.glob("PackageOperation*"))
    for attempt in range(1, ATTEMPTS + 1):
        print(f"SDK installation attempt {attempt}/{ATTEMPTS}: {', '.join(packages)}", flush=True)
        try:
            # No capture: the child keeps our stdout/stderr so CI logs it live.
            returncode = runner(command, input=LICENCE_ANSWERS, text=True, timeout=timeout).returncode
        except subprocess.TimeoutExpired:
            returncode = 1
            print(f"Attempt {attempt} timed out after {timeout}s", file=sys.stderr, flush=True)
        if returncode == 0:
            return 0
        if attempt == ATTEMPTS:
            print(f"SDK installation failed after {ATTEMPTS} attempts", file=sys.stderr, flush=True)
            return returncode
        try:
            # Clear leftovers if an interrupted sdkmanager missed its exit cleanup.
            clear_operations(temporary, known)
        except OSError as error:
            print(f"Could not clear SDK temp leftovers: {error}", file=sys.stderr, flush=True)
        delay = attempt * 10
        print(f"SDK installation failed; retrying with fresh downloads in {delay}s", flush=True)
        sleeper(delay)


def main(argv=None):
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("packages", nargs="+")
    parser.add_argument(
        "--timeout",
        type=int,
        default=DEFAULT_TIMEOUT,
        help="per-attempt timeout in seconds (default: %(default)s)",
    )
    args = parser.parse_args(argv)
    root = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not root:
        parser.error("ANDROID_SDK_ROOT or ANDROID_HOME must identify the SDK")
    sdk_root = Path(root).resolve()
    cli = find_cli(sdk_root)
    if not cli:
        parser.error(f"Found neither sdkmanager nor android CLI in {sdk_root} or PATH")
    print(f"Using SDK CLI {cli}", flush=True)
    return install(
        sdk_root,
        args.packages,
        build_command(cli, sdk_root, args.packages),
        timeout=args.timeout,
    )


if __name__ == "__main__":
    raise SystemExit(main())
