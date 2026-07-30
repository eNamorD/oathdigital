"""Build the runtime component catalog from the archived ingestion inventory.

This one-way migration tool is kept with the reference material. The Scala
application reads only docs/catalog/new-foundations-component-catalog.json.
"""

import csv
import difflib
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
LEGACY_OCR = ROOT / "reference/catalog-ingestion/legacy-component-ocr.tsv"
OUTPUT = ROOT / "docs/catalog/new-foundations-component-catalog.json"
REVIEW = ROOT / "reference/catalog-ingestion/runtime-catalog-review.csv"

SUITS = ("arcane", "beast", "discord", "hearth", "nomad", "order")

RULE_OVERRIDES = {
    "denizen:extra-provisions": "+1 defense die.",
    "denizen:longbows": "+1 attack die.",
    "relic:black-sword": "Pay 2 Supply: +5 attack dice.",
    "relic:fearsome-shield": "Pay 2 Supply: +2 defense dice.",
    "relic:the-grand-scepter": (
        "This relic cannot be removed from play or added to a lineage. "
        "If you are Imperial and victorious against a Citizen, you may pay "
        "1 Favor to exile them. ACTION: You may become a Citizen if an Exile. "
        "IN NEGOTIATION: May offer Citizenship."
    ),
    "legacy:beloved": (
        "Set Foundation IV to Teeming World. WHEN TRADING: You may trade with "
        "advisers held by other players whose pawns are at your site."
    ),
    "legacy:the-mouth": (
        "Set Foundation VI to Festival. ACTION: Burn 1 Favor to activate a "
        "dormant legacy if you meet its goal. You can even activate legacies "
        "in the Public Ambitions foundation."
    ),
    "legacy:reformer": (
        "Set Foundation V to Grand Council. CHRONICLE (START OF THRONE): If "
        "you won, you may move and swap denizens at sites you rule, ignoring "
        "the locked restriction."
    ),
    "legacy:pathfinder": (
        "Set Foundation I to Wide Horizons. TRAVEL: You may ignore the "
        "Mountain, Island, and Pass powers."
    ),
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


def denizen_suit(index, component):
    source = component["printEvidence"][0]["provenance"][0]["file"]
    if source == "unchanged-denizens.pdf":
        return SUITS[index // 27]
    page = component["printEvidence"][0]["provenance"][0]["page"]
    return SUITS[min((page - 1) // 2, 5)]


def handler(kind, identifier, face=None):
    base = f"{kind}.{identifier.split(':', 1)[-1]}"
    return f"{base}.{face}" if face else base


def match_legacy_blocks(components, manifest, ocr):
    legacy_items = [item for item in manifest if item["kind"] == "legacy"]
    choices = []
    for item in legacy_items:
        filename = item["crop"]
        lines = ocr[filename]
        title = lines[0]["text"] if lines else item["name"]
        choices.append((filename, title))

    scored = []
    for component in components:
        for filename, title in choices:
            score = difflib.SequenceMatcher(
                None, normalized(component["name"]), normalized(title)
            ).ratio()
            scored.append((score, component["definitionId"], filename))

    matches = {}
    used_components = set()
    used_files = set()
    for score, definition_id, filename in sorted(scored, reverse=True):
        if definition_id in used_components or filename in used_files:
            continue
        matches[definition_id] = (filename, score)
        used_components.add(definition_id)
        used_files.add(filename)
    return matches


def main():
    archived = json.loads(ARCHIVE.read_text())
    manifest = json.loads(MANIFEST.read_text())
    ocr = parse_ocr(OCR)
    ocr.update(parse_ocr(LEGACY_OCR))
    crop_by_definition = {
        item["definitionId"]: item["crop"] for item in manifest
    }
    components = archived["components"]

    denizen_sources = [
        component for component in components if component["kind"] == "denizen"
    ]
    denizens = []
    base_index = 0
    for component in denizen_sources:
        is_base = (
            component["printEvidence"][0]["provenance"][0]["file"]
            == "unchanged-denizens.pdf"
        )
        suit_index = base_index if is_base else 0
        if is_base:
            base_index += 1
        identifier = f"denizen:{slug(component['name'])}"
        lines = ocr.get(crop_by_definition[component["definitionId"]], [])
        denizens.append(
            {
                "id": identifier,
                "name": component["name"],
                "suit": denizen_suit(suit_index, component),
                "handlers": [handler("denizen", identifier)],
                "rulesText": RULE_OVERRIDES.get(
                    identifier, rules_text(lines, "denizen")
                ),
            }
        )

    relics = []
    for component in (
        item for item in components if item["kind"] == "relic"
    ):
        identifier = f"relic:{slug(component['name'])}"
        lines = ocr.get(crop_by_definition[component["definitionId"]], [])
        defense_candidates = [
            int(line["text"])
            for line in lines
            if re.fullmatch(r"[0-4]", line["text"])
            and line["y"] > 0.65
            and line["x"] > 0.65
        ]
        relics.append(
            {
                "id": identifier,
                "name": component["name"],
                "role": (
                    "grand-scepter"
                    if component["name"] in {"Grand Scepter", "The Grand Scepter"}
                    else "ordinary"
                ),
                "defense": (
                    3
                    if identifier == "relic:the-grand-scepter"
                    else defense_candidates[0] if defense_candidates else 0
                ),
                "handlers": [handler("relic", identifier)],
                "rulesText": RULE_OVERRIDES.get(
                    identifier, rules_text(lines, "relic")
                ),
            }
        )

    faces_by_id = {}
    for component in (
        item for item in components if item["kind"] == "edifice-face"
    ):
        printed_id = component["printedComponentId"]["value"].lower()
        face = component["face"]["state"]
        lines = ocr.get(crop_by_definition[component["definitionId"]], [])
        faces_by_id.setdefault(printed_id, {})[face] = {
            "name": component["name"],
            "handlers": [handler("edifice", printed_id, face)],
            "rulesText": rules_text(lines, "edifice-face"),
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
                "id": f"edifice:{printed_id}",
                "suit": suit,
                "intact": faces["intact"],
                "ruined": faces["ruined"],
            }
        )

    legacy_components = [
        item for item in components if item["kind"] == "legacy"
    ]
    legacy_matches = match_legacy_blocks(legacy_components, manifest, ocr)
    legacies = []
    legacy_scores = {}
    for component in legacy_components:
        identifier = f"legacy:{slug(component['name'])}"
        filename, score = legacy_matches[component["definitionId"]]
        legacy_scores[identifier] = score
        legacies.append(
            {
                "id": identifier,
                "name": component["name"],
                "handlers": [handler("legacy", identifier)],
                "rulesText": RULE_OVERRIDES.get(
                    identifier, rules_text(ocr[filename], "legacy")
                ),
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
                    "suit;rulesText;icons",
                    "sheet-order suit inference;Vision OCR",
                )
            )
        for component in relics:
            writer.writerow(
                (
                    "relic",
                    component["id"],
                    component["name"],
                    "defense;rulesText;icons",
                    "Vision OCR",
                )
            )
        for component in edifices:
            writer.writerow(
                (
                    "edifice",
                    component["id"],
                    f"{component['intact']['name']} / {component['ruined']['name']}",
                    "suit;intact.rulesText;ruined.rulesText;icons",
                    "printed ID range;Vision OCR",
                )
            )
        for component in legacies:
            writer.writerow(
                (
                    "legacy",
                    component["id"],
                    component["name"],
                    "rulesText;icons",
                    f"Vision OCR; title-match={legacy_scores[component['id']]:.2f}",
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
