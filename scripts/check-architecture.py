#!/usr/bin/env python3
"""Deterministic source-boundary checks for the modular monolith."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
PRODUCTION_ROOTS = (
    ROOT / "src/main/scala",
    ROOT / "frontend/src/main/scala",
    ROOT / "shared/src/main/scala",
)


def scala_sources(root: Path):
    return sorted(root.rglob("*.scala"))


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


errors = []
sources = [path for root in PRODUCTION_ROOTS for path in scala_sources(root)]

for path in sources:
    line_count = len(path.read_text(encoding="utf-8").splitlines())
    if line_count > 800:
        errors.append(f"{relative(path)}:{line_count}: production Scala file exceeds 800 lines")

forbidden_imports = {
    "src/main/scala/oathdigital/model": (
        "application", "gameplay", "persistence", "serialization", "server"
    ),
    "src/main/scala/oathdigital/gameplay": (
        "application", "persistence", "presentation", "protocol",
        "serialization", "server"
    ),
    "src/main/scala/oathdigital/application": (
        "persistence", "serialization", "server"
    ),
}
for prefix, packages in forbidden_imports.items():
    root = ROOT / prefix
    needles = tuple(f"import oathdigital.{package}" for package in packages)
    for path in scala_sources(root):
        text = path.read_text(encoding="utf-8")
        for needle in needles:
            if needle in text:
                errors.append(f"{relative(path)}: forbidden inward dependency: {needle}")

for path in scala_sources(ROOT / "frontend/src/main/scala/oathdigital/frontend"):
    if "Renderer" not in path.name:
        continue
    text = path.read_text(encoding="utf-8")
    for package in ("application", "gameplay", "server"):
        needle = f"import oathdigital.{package}"
        if needle in text:
            errors.append(f"{relative(path)}: renderer imports {package}")

for path in scala_sources(ROOT / "shared/src/main/scala"):
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = re.match(r"\s*import oathdigital\.([A-Za-z0-9_]+)", line)
        if match and match.group(1) != "protocol":
            errors.append(
                f"{relative(path)}:{line_number}: shared protocol imports {match.group(1)}"
            )

for path in scala_sources(ROOT / "src/main/scala/oathdigital/gameplay"):
    if "rulesText" in path.read_text(encoding="utf-8"):
        errors.append(f"{relative(path)}: gameplay must not inspect catalog rulesText")

legacy_patterns = {
    r"\boathdigital\.setup\b": "legacy setup package",
    r"\bSetupEventWire\b": "deleted setup wire",
    r"\bSetupState\b": "deleted bounded setup aggregate",
    r"\bBeginSetup\b": "deleted bounded setup command",
    r"\bBrowserMemory\w*\b": "deleted browser-memory stack",
}
for path in sources:
    text = path.read_text(encoding="utf-8")
    for pattern, label in legacy_patterns.items():
        if re.search(pattern, text):
            errors.append(f"{relative(path)}: contains {label}")

definition = re.compile(r"\s*(?:sealed trait|final case class)\s+([A-Za-z0-9_]+)")
shared_only = {
    "GameProjection", "SetupPlayerProjection", "CardDetailsProjection",
    "PendingCardDecisionProjection", "PlayerBoardProjection",
    "FirstGameBootstrapRequest", "BootstrapParticipantRequest",
}
for path in sources:
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = definition.match(line)
        if match and match.group(1) in shared_only and "shared/src/main" not in relative(path):
            errors.append(
                f"{relative(path)}:{line_number}: duplicate shared DTO {match.group(1)}"
            )

command_definition = re.compile(
    r"\s*(?:sealed trait|final case class)\s+(GameIntent|GameCommand)(?:\s|\()"
)
for path in sources:
    rel = relative(path)
    if rel.endswith("application/GameCommands.scala") or "shared/src/main" in rel:
        continue
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if command_definition.match(line):
            errors.append(f"{rel}:{line_number}: duplicate command vocabulary")

if errors:
    print("architecture check failed:")
    for error in sorted(set(errors)):
        print(f"- {error}")
    sys.exit(1)

print(f"architecture check passed: {len(sources)} production Scala files")
