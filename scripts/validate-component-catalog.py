import json
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else "catalog-build")
schema = json.loads((root / "new-foundations-component-catalog.schema.json").read_text())
catalog = json.loads((root / "new-foundations-component-catalog.json").read_text())
errors = []
if catalog.get("schemaVersion") != schema["properties"]["schemaVersion"]["const"]:
    errors.append("schemaVersion mismatch")
source_ids = {s["sourceId"] for s in catalog["sources"]}
definition_ids = set()
for i, c in enumerate(catalog["components"]):
    prefix = f"components[{i}]"
    for key in ("definitionId","kind","name","printedComponentId","physicalCopyCount","copyCountEvidence","printEvidence","transcription"):
        if key not in c: errors.append(f"{prefix}: missing {key}")
    if c["definitionId"] in definition_ids: errors.append(f"{prefix}: duplicate definitionId")
    definition_ids.add(c["definitionId"])
    if "cardInstanceId" in c or "runtimeInstanceId" in c:
        errors.append(f"{prefix}: runtime instance ID is forbidden in catalog data")
    if c["physicalCopyCount"] != 1 and c["copyCountEvidence"] == "assumed-singleton-unless-source-proves-duplicates":
        errors.append(f"{prefix}: duplicate count lacks source evidence")
    printed_id = c["printedComponentId"]
    if printed_id is not None and (not printed_id["type"].endswith("-id") or not printed_id["value"]):
        errors.append(f"{prefix}: malformed typed printed ID")
    for evidence in c["printEvidence"]:
        for p in evidence["provenance"]:
            if p["sourceId"] not in source_ids: errors.append(f"{prefix}: unknown source {p['sourceId']}")
            if not isinstance(p["page"], int) or p["page"] < 1: errors.append(f"{prefix}: invalid page")
for c in catalog["components"]:
    if c["kind"] == "edifice-face":
        other = c["face"]["otherDefinitionId"]
        if other not in definition_ids: errors.append(f"{c['definitionId']}: missing paired face {other}")
if catalog["corpusClaims"].get("complete195DenizenCorpus") is not False:
    errors.append("catalog must not claim a complete 195-denizen corpus")
if catalog["identityPolicy"].get("runtimeCardInstanceIdsAllowed") is not False:
    errors.append("runtime CardInstanceIds must be forbidden")
if catalog["identityPolicy"].get("definitionIdIsIdentityWhenPrintedIdAbsent") is not True:
    errors.append("definitionId must be the identity fallback when no printed ID exists")
if catalog["atlas"].get("model") != "single-ordered-sequence":
    errors.append("Atlas must be one ordered sequence")
if catalog["atlas"].get("frontEnd") != "recent" or catalog["atlas"].get("backEnd") != "forgotten":
    errors.append("Atlas end orientation is invalid")
for family in catalog["bannerFamilies"]:
    if len(family["faceDefinitionIds"]) != 2 or family["runtimeState"].get("canFlip") is not True:
        errors.append(f"{family['physicalFamilyId']}: banner family must have two flippable faces")
for c in catalog["components"]:
    if c["kind"] == "player-board":
        supply = c.get("supply", {})
        if supply.get("maximum") != 7 or supply.get("remainingValues") != [7,6,5,4,3,2,1,0]:
            errors.append(f"{c['definitionId']}: invalid numeric Supply model")
    if c["kind"] == "site":
        stats = c.get("statistics", {})
        for field in ("defense","capacity","relicSlots","recoverDifficulty","startingResources","forgeRequirements"):
            if field not in stats:
                errors.append(f"{c['definitionId']}: missing site statistic {field}")
        if stats.get("defense") is None or stats.get("capacity") is None or stats.get("relicSlots") is None:
            errors.append(f"{c['definitionId']}: unresolved required site statistic")
        if c.get("unresolved"):
            errors.append(f"{c['definitionId']}: site still has unresolved production fields")
        if c.get("printedComponentId") is not None:
            errors.append(f"{c['definitionId']}: source does not show a printed site ID")
        resources = stats.get("startingResources", [])
        forge = stats.get("forgeRequirements", [])
        if [r.get("type") for r in resources] != ["favor","secret"]:
            errors.append(f"{c['definitionId']}: starting resources must be favor/secret counts")
        if stats.get("capacity") == 3:
            if [r.get("type") for r in forge] != ["favor","secret"]:
                errors.append(f"{c['definitionId']}: three-slot site requires favor/secret Forge requirements")
        elif forge:
            errors.append(f"{c['definitionId']}: Forge requirements only print on three-slot sites")
if len([c for c in catalog["components"] if c["kind"] == "site"]) != 24:
    errors.append("catalog must contain exactly 24 sites")
if not any(x["normativeSource"]["sourceId"] == "cr-2026-06" for x in catalog["conflicts"]):
    errors.append("missing normative Combined Rulebook conflict record")
if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print(f"valid: {len(catalog['components'])} singleton definitions")
