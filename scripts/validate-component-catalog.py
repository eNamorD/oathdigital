import json
import re
import sys
from pathlib import Path


root = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/catalog")
schema = json.loads(
    (root / "new-foundations-component-catalog.schema.json").read_text()
)
catalog = json.loads(
    (root / "new-foundations-component-catalog.json").read_text()
)
errors = []

families = ("denizens", "relics", "edifices", "legacies", "sites")
expected_counts = {
    "denizens": 255,
    "relics": 48,
    "edifices": 30,
    "legacies": 36,
    "sites": 24,
}
runtime_top_level = {"schemaVersion", "catalogVersion", *families}
ingestion_fields = {
    "sources",
    "authorityOrder",
    "corpusClaims",
    "identityPolicy",
    "printEvidence",
    "provenance",
    "transcription",
    "unresolved",
    "pendingTasks",
    "ingestedVersion",
    "reviewStatus",
    "confidence",
}
handler_pattern = re.compile(r"^[a-z][a-z0-9-]*(?:\.[a-z0-9-]+)+$")

if set(catalog) != runtime_top_level:
    errors.append(
        "top level must contain only schemaVersion, catalogVersion, and the "
        "five runtime component families"
    )
if catalog.get("schemaVersion") != schema["properties"]["schemaVersion"]["const"]:
    errors.append("schemaVersion mismatch")


def walk_ingestion_fields(value, path="$"):
    if isinstance(value, dict):
        for key, child in value.items():
            if key in ingestion_fields:
                errors.append(f"{path}: ingestion-only field {key} is forbidden")
            walk_ingestion_fields(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            walk_ingestion_fields(child, f"{path}[{index}]")


walk_ingestion_fields(catalog)

ids = {}
handlers = {}
for family in families:
    components = catalog.get(family)
    if not isinstance(components, list):
        errors.append(f"{family}: expected array")
        continue
    if len(components) != expected_counts[family]:
        errors.append(
            f"{family}: expected {expected_counts[family]}, found {len(components)}"
        )
    for index, component in enumerate(components):
        path = f"{family}[{index}]"
        identifier = component.get("id")
        if not isinstance(identifier, str) or not identifier:
            errors.append(f"{path}: missing non-empty id")
        elif identifier in ids:
            errors.append(f"{path}: duplicate id also used by {ids[identifier]}")
        else:
            ids[identifier] = path

        component_handlers = []
        if family == "edifices":
            for face in ("intact", "ruined"):
                component_handlers.extend(
                    component.get(face, {}).get("handlers", [])
                )
        else:
            component_handlers = component.get("handlers", [])
        for handler in component_handlers:
            if not isinstance(handler, str) or not handler_pattern.fullmatch(handler):
                errors.append(f"{path}: malformed handler key {handler!r}")
            elif handler in handlers:
                errors.append(
                    f"{path}: duplicate handler key also used by {handlers[handler]}"
                )
            else:
                handlers[handler] = path

for index, component in enumerate(catalog.get("denizens", [])):
    if not component.get("id", "").startswith("denizen:"):
        errors.append(f"denizens[{index}]: id must start with denizen:")
    if component.get("suit") not in {
        "arcane", "beast", "discord", "hearth", "nomad", "order"
    }:
        errors.append(f"denizens[{index}]: invalid suit")
    if len(component.get("handlers", [])) != 1:
        errors.append(f"denizens[{index}]: expected one stable handler key")

grand_scepters = [
    relic for relic in catalog.get("relics", [])
    if relic.get("role") == "grand-scepter"
]
if len(grand_scepters) != 1 or grand_scepters[0].get("name") not in {
    "Grand Scepter", "The Grand Scepter"
}:
    errors.append("relics must contain exactly one Grand Scepter role")

expected_edifice_ids = {f"edifice:e{number:02d}" for number in range(1, 31)}
actual_edifice_ids = {
    component.get("id") for component in catalog.get("edifices", [])
}
if actual_edifice_ids != expected_edifice_ids:
    errors.append("edifices must contain exactly E01 through E30")
for index, component in enumerate(catalog.get("edifices", [])):
    for face in ("intact", "ruined"):
        definition = component.get(face)
        if not isinstance(definition, dict):
            errors.append(f"edifices[{index}]: missing {face} face")
        elif len(definition.get("handlers", [])) != 1:
            errors.append(
                f"edifices[{index}].{face}: expected one stable handler key"
            )

for index, site in enumerate(catalog.get("sites", [])):
    path = f"sites[{index}]"
    if not site.get("id", "").startswith("site:"):
        errors.append(f"{path}: id must start with site:")
    has_forge = site.get("forgeRequirements") is not None
    if (site.get("capacity") == 3) != has_forge:
        errors.append(f"{path}: Forge requirements must match three-slot capacity")
    for field in ("defense", "capacity", "relicSlots"):
        value = site.get(field)
        if not isinstance(value, int) or value < 0:
            errors.append(f"{path}: {field} must be a non-negative integer")

if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)

print(
    "valid runtime catalog: "
    + ", ".join(f"{expected_counts[name]} {name}" for name in families)
)
