#!/usr/bin/env python3
"""Validate repository-local Markdown links and heading fragments."""

from pathlib import Path
import re
import sys
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
MARKDOWN = [ROOT / "README.md"] + sorted((ROOT / "docs").rglob("*.md"))
LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
HEADING = re.compile(r"^#{1,6}\s+(.+?)\s*#*\s*$")


def slug(value: str) -> str:
    value = re.sub(r"<[^>]+>", "", value).strip().lower()
    value = re.sub(r"[^\w\- ]", "", value)
    return re.sub(r"[\s]+", "-", value)


def anchors(path: Path):
    found = set()
    counts = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = HEADING.match(line)
        if not match:
            continue
        base = slug(match.group(1))
        count = counts.get(base, 0)
        counts[base] = count + 1
        found.add(base if count == 0 else f"{base}-{count}")
    return found


errors = []
for source in MARKDOWN:
    text = source.read_text(encoding="utf-8")
    visible = []
    fence = None
    for line in text.splitlines(keepends=True):
        marker = re.match(r"^\s*(`{3,}|~{3,})", line)
        if marker:
            run = marker.group(1)
            if fence is None:
                fence = (run[0], len(run))
            elif run[0] == fence[0] and len(run) >= fence[1]:
                fence = None
            visible.append("\n")
        elif fence is None:
            visible.append(re.sub(r"(`+)(.*?)\1", "", line))
        else:
            visible.append("\n")
    for match in LINK.finditer("".join(visible)):
        raw = match.group(1).strip().split()[0].strip("<>")
        if raw.startswith(("http://", "https://", "mailto:")):
            continue
        target_text, _, fragment = raw.partition("#")
        target = source if not target_text else (source.parent / unquote(target_text)).resolve()
        if not target.exists():
            errors.append(f"{source.relative_to(ROOT)}: missing {raw}")
            continue
        if fragment and target.suffix.lower() == ".md" and unquote(fragment) not in anchors(target):
            errors.append(f"{source.relative_to(ROOT)}: missing fragment {raw}")

if errors:
    print("Markdown link check failed:")
    for error in sorted(set(errors)):
        print(f"- {error}")
    sys.exit(1)

print(f"Markdown link check passed: {len(MARKDOWN)} files")
