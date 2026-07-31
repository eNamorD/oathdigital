"""Build the runtime component catalog from the archived ingestion inventory.

This one-way migration tool is kept with the reference material. The Scala
application reads only docs/catalog/new-foundations-component-catalog.json.
"""

import csv
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ARCHIVE = (
    ROOT
    / "reference/catalog-ingestion/archive/ingestion-component-catalog.json"
)
MANIFEST = Path("/private/tmp/oath-runtime-catalog/crops/manifest.json")
OCR = ROOT / "reference/catalog-ingestion/component-ocr.tsv"
BASE_DENIZENS = (
    ROOT / "reference/catalog-ingestion/base-denizen-transcriptions.json"
)
NEW_FOUNDATIONS_DENIZENS = (
    ROOT
    / "reference/catalog-ingestion/new-foundations-denizen-transcriptions.json"
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
REVIEW = ROOT / "reference/catalog-ingestion/runtime-catalog-review.csv"

SUITS = ("arcane", "beast", "discord", "hearth", "nomad", "order")


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


def denizen_suit(index, component):
    source = component["printEvidence"][0]["provenance"][0]["file"]
    if source == "unchanged-denizens.pdf":
        return SUITS[index // 27]
    page = component["printEvidence"][0]["provenance"][0]["page"]
    return SUITS[min((page - 1) // 2, 5)]


def handler(kind, identifier, face=None):
    base = f"{kind}.{identifier}"
    return f"{base}.{face}" if face else base


def indexed_by_name(records):
    return {normalized(record["name"]): record for record in records}


def combined_rules_text(record):
    return " ".join(
        part
        for part in [*record.get("costSymbols", []), record["rulesText"]]
        if part
    ).strip()


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


def main():
    archived = json.loads(ARCHIVE.read_text())
    base_denizens = indexed_by_name(json.loads(BASE_DENIZENS.read_text()))
    base_denizens[normalized("News from Alfar")] = base_denizens[
        normalized("News from Afar")
    ]
    base_denizens[normalized("Old Fast Steed")] = base_denizens[
        normalized("A Fast Steed")
    ]
    base_denizens[normalized("Land Garden")] = base_denizens[
        normalized("Land Warden")
    ]
    nf_denizens = indexed_by_name(
        json.loads(NEW_FOUNDATIONS_DENIZENS.read_text())
    )
    relic_transcriptions = indexed_by_name(
        json.loads(RELIC_TRANSCRIPTIONS.read_text())
    )
    legacy_transcriptions = indexed_by_name(
        json.loads(LEGACY_TRANSCRIPTIONS.read_text())
    )
    edifice_rule_overrides = json.loads(EDIFICE_RULE_OVERRIDES.read_text())
    for archived_name, printed_name in (
        ("Ancient Grit", "Ancient Writ"),
        ("Keeping Banner", "Weeping Banner"),
        ("King of Devotion", "Ring of Devotion"),
    ):
        relic_transcriptions[normalized(archived_name)] = relic_transcriptions[
            normalized(printed_name)
        ]
    manifest = json.loads(MANIFEST.read_text())
    ocr = parse_ocr(OCR)
    crop_by_definition = {
        item["definitionId"]: item["crop"] for item in manifest
    }
    components = archived["components"]

    denizen_sources = [
        component for component in components if component["kind"] == "denizen"
    ]
    denizens = []
    for component in denizen_sources:
        key = normalized(component["name"])
        transcription = nf_denizens.get(key) or base_denizens.get(key)
        if transcription is None:
            raise ValueError(
                f"no reviewed denizen transcription for {component['name']}"
            )
        printed_id = int(transcription["id"])
        if printed_id >= 199:
            suit = SUITS[(printed_id - 199) // 10]
        elif key in nf_denizens:
            suit = denizen_suit(0, component)
        else:
            suit = base_denizens[key]["suit"]
        denizens.append(
            {
                "id": transcription["id"],
                "name": transcription["name"],
                "suit": suit,
                "handlers": [handler("denizen", slug(component["name"]))],
                "rulesText": combined_rules_text(transcription),
            }
        )

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
                "handlers": [handler("relic", slug(component["name"]))],
                "rulesText": transcription["rulesText"],
            }
        )

    faces_by_id = {}
    for component in (
        item for item in components if item["kind"] == "edifice-face"
    ):
        printed_id = component["printedComponentId"]["value"]
        face = component["face"]["state"]
        lines = ocr.get(crop_by_definition[component["definitionId"]], [])
        faces_by_id.setdefault(printed_id, {})[face] = {
            "name": component["name"],
            "handlers": [handler("edifice", printed_id.lower(), face)],
            "rulesText": edifice_rule_overrides.get(
                component["name"],
                normalized_markdown(rules_text(lines, "edifice-face")),
            ),
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
                "handlers": [handler("legacy", slug(component["name"]))],
                "rulesText": transcription["rulesText"],
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

    catalog = {
        "schemaVersion": "1.0.0",
        "catalogVersion": archived["catalogVersion"],
        "denizens": denizens,
        "relics": relics,
        "edifices": edifices,
        "legacies": legacies,
        "sites": sites,
    }
    OUTPUT.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n")

    with REVIEW.open("w", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(("kind", "id", "name", "fieldsToReview", "basis"))
        for component in denizens:
            writer.writerow(
                (
                    "denizen",
                    component["id"],
                    component["name"],
                    "rulesText",
                    "official base data or visual review of print card",
                )
            )
        for component in relics:
            writer.writerow(
                (
                    "relic",
                    component["id"],
                    component["name"],
                    "value;defense;rulesText",
                    "visual review of print card",
                )
            )
        for component in edifices:
            writer.writerow(
                (
                    "edifice",
                    component["id"],
                    f"{component['intact']['name']} / {component['ruined']['name']}",
                    "rulesText",
                    "visual overrides plus normalized OCR",
                )
            )
        for component in legacies:
            writer.writerow(
                (
                    "legacy",
                    component["id"],
                    component["name"],
                    "rulesText",
                    "visual review of print card",
                )
            )

    print(
        "built "
        f"{len(denizens)} denizens, {len(relics)} relics, "
        f"{len(edifices)} edifices, {len(legacies)} legacies, "
        f"and {len(sites)} sites"
    )


if __name__ == "__main__":
    sys.exit(main())
