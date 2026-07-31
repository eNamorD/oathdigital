"""Extract the retained base denizen transcription from public references.

Inputs:
1. Buried Giant's public cards.json export.
2. A local checkout of LeCodex/Oath, used only for printed power costs.

The output is archival migration input. Runtime code never reads it.
"""

import json
import re
import sys
from pathlib import Path


def class_bodies(source):
    for match in re.finditer(r"export class\s+(\w+)[^{]*\{", source):
        depth = 1
        index = match.end()
        while index < len(source) and depth:
            depth += (source[index] == "{") - (source[index] == "}")
            index += 1
        yield match.group(1), source[match.end():index - 1]


def parse_cost(expression):
    spent_text, separator, burnt_text = expression.partition("]], [[")

    def tokens(text, suffix):
        result = []
        for resource, count in re.findall(
            r"(Favor|Secret)\s*,\s*(\d+)", text
        ):
            token = resource.lower() + suffix
            result.extend(f"[{token}]" for _ in range(int(count)))
        return result

    return tokens(spent_text, "") + (
        tokens(burnt_text, "-burnt") if separator else []
    )


def parse_power_costs(repo):
    result = {}
    powers = repo / "src/oath/game/powers"
    for path in powers.glob("*.ts"):
        source = path.read_text()
        for class_name, body in class_bodies(source):
            match = re.search(
                r"cost\s*=\s*new ResourceCost\(([^;]+)\);", body
            )
            if match:
                result[class_name] = parse_cost(match.group(1))
    return result


def parse_denizen_powers(repo):
    source = (
        repo / "src/oath/game/cards/denizens.ts"
    ).read_text()
    return {
        name: re.findall(r'"([^"]+)"', powers)
        for name, powers in re.findall(
            r"^\s*(\w+):\s*\[OathSuit\.\w+,\s*\[([^\]]*)\]",
            source,
            flags=re.MULTILINE,
        )
    }


def normalize_symbols(text):
    replacements = {
        "`symbol:favor`": "[favor]",
        "`symbol:favorb`": "[favor-burnt]",
        "`symbol:secret`": "[secret]",
        "`symbol:secretb`": "[secret-burnt]",
        "`symbol:dicer`": "[attack-die]",
        "`symbol:diceb`": "[defense-die]",
        "`symbol:shield`": "[shield]",
        "`symbol:skull`": "[skull]",
        "`symbol:sword`": "[sword]",
        "`symbol:swordh`": "[hollow-sword]",
    }
    for suit in ("arcane", "beast", "discord", "hearth", "nomad", "order"):
        replacements[f"`symbol:suit-{suit}`"] = f"[suit-{suit}]"
    for source, target in replacements.items():
        text = text.replace(source, target)
    return (
        text.replace("**Action**:", "**ACTION:**")
        .replace("**When Played**,", "**WHEN PLAYED:**")
        .replace("**When Played**", "**WHEN PLAYED:**")
        .replace("**Wake**:", "**WAKE:**")
        .replace("**Rest**:", "**REST:**")
    )


def main():
    if len(sys.argv) != 4:
        raise SystemExit(
            "usage: extract_base_card_data.py CARDS_JSON LECODEX_REPO OUTPUT"
        )
    cards_path, repo_path, output_path = map(Path, sys.argv[1:])
    cards = json.loads(cards_path.read_text())
    repo = repo_path.resolve()
    power_costs = parse_power_costs(repo)
    denizen_powers = parse_denizen_powers(repo)

    records = []
    for card in cards:
        if (
            card.get("game") != "oath"
            or card.get("locale") != "en-US"
            or "Standard Deck" not in card.get("tags", [])
            or not re.fullmatch(r"OATH-\d{3}", card["id"])
            or int(card["id"].split("-")[1]) > 198
        ):
            continue
        key = re.sub(r"[^a-z0-9]", "", card["name"].lower())
        source_key = next(
            (
                candidate
                for candidate in denizen_powers
                if candidate.lower() == key
            ),
            None,
        )
        costs = []
        if source_key:
            candidate_costs = {
                tuple(power_costs[power])
                for power in denizen_powers[source_key]
                if power in power_costs
            }
            if len(candidate_costs) == 1:
                costs = list(candidate_costs.pop())
        records.append(
            {
                "id": card["id"].split("-")[1].lstrip("0") or "0",
                "name": card["name"],
                "suit": next(
                    tag.lower()
                    for tag in card["tags"]
                    if tag in {
                        "Arcane",
                        "Beast",
                        "Discord",
                        "Hearth",
                        "Nomad",
                        "Order",
                    }
                ),
                "costSymbols": costs,
                "rulesText": normalize_symbols(card["text"]),
            }
        )

    output_path.write_text(
        json.dumps(sorted(records, key=lambda item: int(item["id"])), indent=2)
        + "\n"
    )
    print(f"extracted {len(records)} base denizens")


if __name__ == "__main__":
    main()
