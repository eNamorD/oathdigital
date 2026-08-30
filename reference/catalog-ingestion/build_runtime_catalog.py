"""Build or verify the runtime catalog from reviewed reference inputs.

The no-argument mode only verifies equality, so it cannot overwrite reviewed
runtime data. The Scala application reads only the catalog under docs/catalog.
"""

import argparse
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ARCHIVE = (
    ROOT
    / "reference/catalog-ingestion/archive/ingestion-component-catalog.json"
)
OCR = ROOT / "reference/catalog-ingestion/component-ocr.tsv"
RUNTIME_DENIZENS = (
    ROOT
    / "reference/catalog-ingestion/runtime-denizen-definitions.json"
)
RELIC_TRANSCRIPTIONS = (
    ROOT / "reference/catalog-ingestion/relic-transcriptions.json"
)
LEGACY_TRANSCRIPTIONS = (
    ROOT / "reference/catalog-ingestion/legacy-transcriptions.json"
)
EDIFICE_RULE_OVERRIDES = (
    ROOT / "reference/catalog-ingestion/edifice-rule-overrides.json"
)
OUTPUT = ROOT / "docs/catalog/new-foundations-component-catalog.json"
REVIEWED_RUNTIME_POWERS = (
    ROOT / "reference/catalog-ingestion/reviewed-runtime-powers.json"
)
PERSISTENT_POWER_IDS = {
    "denizen.vow-of-peace",
    "denizen.relic-worship",
    "edifice.e13.ruined",
    "edifice.e17.intact",
    "edifice.e17.ruined",
}

def slug(value):
    return re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")


def normalized(value):
    return re.sub(r"[^a-z0-9]", "", value.lower())


def parse_ocr(path):
    result = {}
    current = None
    for raw_line in path.read_text().splitlines():
        if raw_line.startswith("FILE\t"):
            current = Path(raw_line.split("\t", 1)[1]).name
            result[current] = []
            continue
        if current is None:
            continue
        parts = raw_line.split("\t", 5)
        if len(parts) != 6:
            continue
        x, y, width, height, confidence, text = parts
        result[current].append(
            {
                "x": float(x),
                "y": float(y),
                "width": float(width),
                "height": float(height),
                "confidence": float(confidence),
                "text": text.strip(),
            }
        )
    return result


def rules_text(lines, kind):
    if kind == "legacy":
        candidates = lines[1:]
    else:
        ceiling = 0.42 if kind == "edifice-face" else 0.36
        candidates = [line for line in lines if line["y"] < ceiling]

    fragments = []
    for line in candidates:
        text = line["text"].strip()
        if not text:
            continue
        if re.fullmatch(r"[LER]\d{1,3}", text, flags=re.IGNORECASE):
            continue
        if re.fullmatch(r"[\d±+*•○◉□\s]+", text):
            continue
        if len(text) == 1 and not text.isalpha():
            continue
        fragments.append(text)
    return " ".join(fragments).strip()


def handler(kind, identifier, face=None):
    base = f"{kind}.{identifier}"
    return f"{base}.{face}" if face else base


def power(power_id, text):
    return {
        "id": power_id,
        "persistent": power_id in PERSISTENT_POWER_IDS,
        "rulesText": text,
    }


def indexed_by_name(records):
    return {normalized(record["name"]): record for record in records}


def apply_reviewed_powers(records, reviewed, family):
    by_id = {record["id"]: record for record in records}
    if set(by_id) != set(reviewed):
        raise ValueError(f"reviewed {family} power identities do not match runtime metadata")
    for identifier, powers in reviewed.items():
        by_id[identifier]["powers"] = powers


def apply_reviewed_edifice_faces(records, reviewed):
    by_id = {record["id"]: record for record in records}
    if set(by_id) != set(reviewed):
        raise ValueError("reviewed edifice power identities do not match runtime metadata")
    for identifier, faces in reviewed.items():
        for face in ("intact", "ruined"):
            by_id[identifier][face]["restrictions"] = faces[face]["restrictions"]
            by_id[identifier][face]["powers"] = faces[face]["powers"]


def normalized_markdown(text):
    text = re.sub(r"\s+(?:[EL][O0]?\d{1,2})$", "", text)
    headings = (
        "ACTION",
        "BATTLE PLAN",
        "CHRONICLE",
        "END OF ROUND",
        "SETUP",
        "TRAVEL",
        "WAKE",
        "WHEN EXPLORED",
        "WHEN NEGOTIATING",
        "WHEN PLAYED",
    )
    for heading in headings:
        text = re.sub(
            rf"\b{re.escape(heading)}\b:",
            f"**{heading}:**",
            text,
            flags=re.IGNORECASE,
        )
    text = re.sub(r"\b(cannot|must)\b", r"**\1**", text, flags=re.IGNORECASE)
    return text


def parse_args():
    parser = argparse.ArgumentParser(
        description="Build or verify the deterministic runtime catalog"
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="write generated JSON to this path instead of checking runtime equality",
    )
    return parser.parse_args()


def main():
    args = parse_args()
    archived = json.loads(ARCHIVE.read_text())
    denizens = json.loads(RUNTIME_DENIZENS.read_text())
    relic_transcriptions = indexed_by_name(
        json.loads(RELIC_TRANSCRIPTIONS.read_text())
    )
    legacy_transcriptions = indexed_by_name(
        json.loads(LEGACY_TRANSCRIPTIONS.read_text())
    )
    edifice_rule_overrides = json.loads(EDIFICE_RULE_OVERRIDES.read_text())
    reviewed_powers = json.loads(REVIEWED_RUNTIME_POWERS.read_text())
    for archived_name, printed_name in (
        ("Ancient Grit", "Ancient Writ"),
        ("Keeping Banner", "Weeping Banner"),
        ("King of Devotion", "Ring of Devotion"),
    ):
        relic_transcriptions[normalized(archived_name)] = relic_transcriptions[
            normalized(printed_name)
        ]
    ocr = parse_ocr(OCR)
    components = archived["components"]

    relics = []
    for component in (
        item for item in components if item["kind"] == "relic"
    ):
        transcription = relic_transcriptions.get(normalized(component["name"]))
        if transcription is None:
            raise ValueError(
                f"no reviewed relic transcription for {component['name']}"
            )
        is_grand_scepter = transcription["id"] == "grand-scepter"
        relics.append(
            {
                "id": transcription["id"],
                "name": transcription["name"],
                "role": "grand-scepter" if is_grand_scepter else "ordinary",
                "value": transcription["value"],
                "defense": transcription["defense"],
                "powers": [power(
                    handler("relic", slug(component["name"])),
                    transcription["rulesText"],
                )],
            }
        )

    faces_by_id = {}
    for component in (
        item for item in components if item["kind"] == "edifice-face"
    ):
        printed_id = component["printedComponentId"]["value"]
        face = component["face"]["state"]
        crop = f"{slug(component['definitionId'])}.jpg"
        lines = ocr.get(crop, [])
        faces_by_id.setdefault(printed_id, {})[face] = {
            "name": component["name"],
            "powers": [power(
                handler("edifice", printed_id.lower(), face),
                edifice_rule_overrides.get(
                    component["name"],
                    normalized_markdown(rules_text(lines, "edifice-face")),
                ),
            )],
        }

    edifices = []
    for printed_id, faces in sorted(
        faces_by_id.items(), key=lambda item: int(item[0][1:])
    ):
        number = int(printed_id[1:])
        suit = (
            "discord" if number <= 5
            else "nomad" if number <= 10
            else "arcane" if number <= 15
            else "order" if number <= 20
            else "hearth" if number <= 25
            else "beast"
        )
        edifices.append(
            {
                "id": printed_id,
                "suit": suit,
                "intact": faces["intact"],
                "ruined": faces["ruined"],
            }
        )

    legacy_components = [
        item for item in components if item["kind"] == "legacy"
    ]
    legacies = []
    for component in legacy_components:
        transcription = legacy_transcriptions[normalized(component["name"])]
        identifier = transcription["id"]
        legacies.append(
            {
                "id": identifier,
                "name": transcription["name"],
                "powers": [power(
                    handler("legacy", slug(component["name"])),
                    transcription["rulesText"],
                )],
            }
        )

    sites = []
    for component in (
        item for item in components if item["kind"] == "site"
    ):
        stats = component["statistics"]
        starting = {item["type"]: item["count"] for item in stats["startingResources"]}
        forge = {item["type"]: item["count"] for item in stats["forgeRequirements"]}
        identifier = component["definitionId"]
        sites.append(
            {
                "id": identifier,
                "name": component["name"],
                "defense": stats["defense"],
                "capacity": stats["capacity"],
                "relicSlots": stats["relicSlots"],
                "recoverDifficulty": stats["recoverDifficulty"],
                "startingResources": {
                    "favor": starting.get("favor", 0),
                    "secrets": starting.get("secret", 0),
                },
                "forgeRequirements": (
                    {
                        "favor": forge.get("favor", 0),
                        "secrets": forge.get("secret", 0),
                    }
                    if stats["capacity"] == 3
                    else None
                ),
                "handlers": [
                    f"site.{identifier.split(':', 1)[1]}.{slug(power)}"
                    for power in component["powers"]
                ],
            }
        )

    apply_reviewed_powers(denizens, reviewed_powers["denizens"], "denizen")
    apply_reviewed_powers(relics, reviewed_powers["relics"], "relic")
    apply_reviewed_edifice_faces(edifices, reviewed_powers["edifices"])
    apply_reviewed_powers(legacies, reviewed_powers["legacies"], "legacy")

    catalog = {
        "schemaVersion": "1.3.0",
        "catalogVersion": "2026.08.29-pre5",
        "denizens": denizens,
        "relics": relics,
        "edifices": edifices,
        "legacies": legacies,
        "sites": sites,
    }
    if args.output is None:
        runtime = json.loads(OUTPUT.read_text())
        if catalog != runtime:
            raise ValueError(
                "generated catalog differs from runtime catalog; inspect by "
                "passing --output to a temporary path"
            )
    else:
        args.output.write_text(
            json.dumps(catalog, indent=2, ensure_ascii=False) + "\n"
        )

    print(
        "built "
        f"{len(denizens)} denizens, {len(relics)} relics, "
        f"{len(edifices)} edifices, {len(legacies)} legacies, "
        f"and {len(sites)} sites"
    )


if __name__ == "__main__":
    sys.exit(main())
