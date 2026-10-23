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

# The hard limit docs/architecture/codebase-structure.md sets for a Scala
# source file. This is the only place the number is written: the
# "stays at or below" tests in BackendArchitectureSuite read this
# declaration, so keep the name and the assignment on one line.
MAX_SCALA_FILE_LINES = 800


def scala_sources(root: Path):
    return sorted(root.rglob("*.scala"))


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


errors = []
sources = [path for root in PRODUCTION_ROOTS for path in scala_sources(root)]

for path in sources:
    line_count = len(path.read_text(encoding="utf-8").splitlines())
    if line_count > MAX_SCALA_FILE_LINES:
        errors.append(f"{relative(path)}:{line_count}: production Scala file "
                      f"exceeds {MAX_SCALA_FILE_LINES} lines")

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
    # Actions are the engine's procedures; powers contribute to them through
    # power windows (docs/architecture/gameplay-modules.md).
    "src/main/scala/oathdigital/gameplay/actions": ("gameplay.powers",),
    "src/main/scala/oathdigital/catalog": (
        "application", "gameplay", "persistence", "serialization", "server"
    ),
}
# Known exceptions, each a file and the one import it may keep. The attack
# line counts skull faces as the chosen plans score them, which only the
# plan cards know (ROADMAP "Campaign notes read the attack rules").
allowed_imports = {
    "src/main/scala/oathdigital/gameplay/actions/campaign/CampaignNotes.scala":
        "import oathdigital.gameplay.powers.campaign.AttackScore\n",
}
for prefix, packages in forbidden_imports.items():
    root = ROOT / prefix
    needles = tuple(f"import oathdigital.{package}" for package in packages)
    for path in scala_sources(root):
        text = path.read_text(encoding="utf-8")
        text = text.replace(allowed_imports.get(relative(path), "\0"), "")
        for needle in needles:
            if needle in text:
                errors.append(f"{relative(path)}: forbidden inward dependency: {needle}")

# The frontend renders projections over the shared protocol and never
# imports gameplay, application or server rules; the shared protocol
# imports nothing but itself (docs/architecture/codebase-structure.md).
# One exception: build.sbt grafts three `model` files into the Scala.js
# build, and `oathdigital.model.PlayerColor` from them is the one model
# name the frontend and the shared protocol reference, by import or fully
# qualified. Any other `oathdigital.<package>` reference, in an import or
# written out in full, is a violation.
GRAFTED_MODEL_FILES = ("Identity.scala", "PlayerColor.scala", "Resources.scala")
SHARED_MODEL_NAMES = {"PlayerColor"}
build_sbt = (ROOT / "build.sbt").read_text(encoding="utf-8")
for grafted in GRAFTED_MODEL_FILES:
    if f'"model" / "{grafted}"' not in build_sbt:
        errors.append(f"build.sbt: no longer grafts model/{grafted} into the "
                      "frontend; revisit the frontend gate's model exception")

qualified_reference = re.compile(
    r"\boathdigital\.([A-Za-z0-9_]+)(?:\.([A-Za-z0-9_]+))?")
# `import oathdigital.{application, model => m}` names packages inside the
# braces, where the pattern above cannot see them.
brace_group_import = re.compile(r"\bimport\s+oathdigital\.\{([^}]*)\}")


def outward_references(path: Path, allowed_packages: tuple[str, ...]):
    """Each (line number, package) naming a package outside `allowed_packages`,
    except the sanctioned model names."""
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        for group in brace_group_import.finditer(line):
            for item in group.group(1).split(","):
                package = item.strip().split("=>")[0].strip().split(".")[0]
                if package and package not in allowed_packages:
                    yield line_number, package
        for match in qualified_reference.finditer(line):
            package, member = match.group(1), match.group(2)
            if package in allowed_packages:
                continue
            if package == "model" and member in SHARED_MODEL_NAMES:
                continue
            yield line_number, package


for path in scala_sources(ROOT / "frontend/src/main/scala"):
    for line_number, package in outward_references(path, ("frontend", "protocol")):
        errors.append(f"{relative(path)}:{line_number}: frontend references {package}")

for path in scala_sources(ROOT / "shared/src/main/scala"):
    for line_number, package in outward_references(path, ("protocol",)):
        errors.append(
            f"{relative(path)}:{line_number}: shared protocol references {package}"
        )

printed_text = re.compile(r"\.text\b")
for path in scala_sources(ROOT / "src/main/scala/oathdigital/gameplay"):
    text = path.read_text(encoding="utf-8")
    if "rulesText" in text:
        errors.append(f"{relative(path)}: gameplay must not inspect catalog rulesText")
    if printed_text.search(text):
        errors.append(f"{relative(path)}: gameplay must not read printed power text")

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
    "PlayerBoardProjection",
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
