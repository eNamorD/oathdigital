#!/usr/bin/env python3
"""Measure and profile the root tests.

  scripts/profile-tests.py --gate
      Runs the gate (root/test frontend/test), reports its wall-clock and
      CPU time, and ranks the root suites and tests by time.

  scripts/profile-tests.py 'oathdigital.gameplay.powers.campaign.*'
      Runs the matching root suites under Java Flight Recorder, ranks them,
      and lists the hottest frames.

Everything is written under target/profile/. See docs/testing/profiling.md.
"""

import argparse
import collections
import os
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "target/profile"
REPORTS = ROOT / "target/test-reports"
STACK_DEPTH = 128
# Frames the test speed work tracks from run to run (inclusive share).
WATCHED = ("CardIndex$.", "OperationStateInvariant", "DomainValidation$.validate",
           "ContributionCollector$.gather", "WalkerPowerGather$.breaches",
           "java.lang.ClassValue")


def java_home() -> Path:
    configured = os.environ.get("JAVA_HOME")
    if configured:
        return Path(configured)
    return ROOT / ".tooling/jdk-17.0.19+10/Contents/Home"


def run_sbt(args, log_path):
    """Runs ./sbtw with args and copies its output to log_path.

    Returns the exit code, the wall-clock seconds and the CPU seconds of sbt
    and everything it started."""
    before = os.times()
    started = time.monotonic()
    with open(log_path, "w", encoding="utf-8") as log:
        process = subprocess.Popen(
            [str(ROOT / "sbtw"), *args], cwd=ROOT,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
            encoding="utf-8", errors="replace")
        for line in process.stdout:
            sys.stdout.write(line)
            log.write(line)
        code = process.wait()
    wall = time.monotonic() - started
    after = os.times()
    cpu = (after.children_user - before.children_user
           + after.children_system - before.children_system)
    return code, wall, cpu


def rank(since, top):
    """Ranks the root JUnit reports written after `since`."""
    suites, tests = [], []
    for path in REPORTS.glob("TEST-*.xml"):
        if path.stat().st_mtime < since:
            continue
        report = ET.parse(path).getroot()
        suite = report.get("name")
        suites.append((float(report.get("time") or 0),
                       int(report.get("tests") or 0), suite))
        for case in report.iter("testcase"):
            tests.append((float(case.get("time") or 0),
                          suite.rsplit(".", 1)[-1], case.get("name")))
    suites.sort(reverse=True)
    tests.sort(reverse=True)
    lines = [
        f"Root suites: {len(suites)}, tests: {sum(n for _, n, _ in suites)}, "
        f"summed suite time: {sum(t for t, _, _ in suites):.1f} s",
        "",
        f"Slowest {top} suites:",
    ]
    lines += [f"{t:8.2f} s {n:4d} tests  {name}" for t, n, name in suites[:top]]
    lines += ["", f"Slowest {top} tests:"]
    lines += [f"{t:8.3f} s  {suite} :: {name}" for t, suite, name in tests[:top]]
    return lines


def stacks(recording):
    """Yields each execution sample's frames, top of the stack first.

    Streams `jfr print` as text, so memory stays flat however many samples
    the recording holds. The JSON form of a deep recording runs to gigabytes."""
    process = subprocess.Popen(
        [str(java_home() / "bin/jfr"), "print", "--events", "jdk.ExecutionSample",
         "--stack-depth", str(STACK_DEPTH), str(recording)],
        stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    current = None
    for line in process.stdout:
        text = line.strip()
        if text.startswith("stackTrace = ["):
            current = []
        elif current is not None and text == "]":
            yield current
            current = None
        elif current is not None and text != "...":
            current.append(text.split("(", 1)[0])
    if process.wait() != 0:
        raise SystemExit(f"jfr print failed on {recording}")


def frames(recording, top):
    """Lists the hottest frames of the samples that ran project code.

    Self: the frame on top of the stack, whatever its package.
    Watched: samples with one of WATCHED anywhere on the stack.
    Inclusive: every oathdigital frame anywhere on the stack, once per sample."""
    leaf = collections.Counter()
    inclusive = collections.Counter()
    watched = collections.Counter()
    counted = 0
    for names in stacks(recording):
        if not any(name.startswith("oathdigital.") for name in names):
            continue
        counted += 1
        leaf[names[0]] += 1
        for fragment in WATCHED:
            if any(fragment in name for name in names):
                watched[fragment] += 1
        for name in set(names):
            if name.startswith("oathdigital."):
                inclusive[name] += 1
    if counted == 0:
        return ["No samples ran oathdigital code."]
    lines = [f"Samples that ran oathdigital code: {counted}", "",
             f"Top {top} self frames:"]
    lines += [f"{count * 100 / counted:5.1f}%  {name}"
              for name, count in leaf.most_common(top)]
    lines += ["", "Watched frames (samples with the fragment anywhere on the stack):"]
    lines += [f"{watched[fragment] * 100 / counted:5.1f}%  {fragment}"
              for fragment in WATCHED]
    lines += ["", f"Top {top * 2} inclusive oathdigital frames:"]
    lines += [f"{count * 100 / counted:5.1f}%  {name}"
              for name, count in inclusive.most_common(top * 2)]
    return lines


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("filters", nargs="*",
                        help="root test filters, as testOnly takes them")
    parser.add_argument("--gate", action="store_true",
                        help="time the gate instead of profiling")
    parser.add_argument("--top", type=int, default=25,
                        help="how many suites, tests and frames to list")
    options = parser.parse_args()
    if options.gate == bool(options.filters):
        parser.error("give either --gate or test filters")

    OUT.mkdir(parents=True, exist_ok=True)
    since = time.time()
    load = f"load average at start {os.getloadavg()[0]:.1f}"
    if options.gate:
        code, wall, cpu = run_sbt(["root/test", "frontend/test"], OUT / "gate.log")
        lines = [f"Gate: {wall:.0f} s wall clock, {cpu:.0f} s CPU, {load}, exit {code}", ""]
        lines += rank(since, options.top)
        report = OUT / "gate.txt"
    else:
        recording = OUT / "tests.jfr"
        recording.unlink(missing_ok=True)
        code, wall, _ = run_sbt([
            f"-J-XX:FlightRecorderOptions=stackdepth={STACK_DEPTH}",
            f"-J-XX:StartFlightRecording=filename={recording},"
            "settings=profile,dumponexit=true",
            "root/testOnly " + " ".join(options.filters),
        ], OUT / "profile.log")
        lines = [f"Profile of {' '.join(options.filters)}: "
                 f"{wall:.0f} s wall clock, {load}, exit {code}", ""]
        lines += rank(since, options.top) + [""] + frames(recording, options.top)
        report = OUT / "profile.txt"

    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    print(f"\nWritten to {report.relative_to(ROOT)}")
    return code


if __name__ == "__main__":
    sys.exit(main())
