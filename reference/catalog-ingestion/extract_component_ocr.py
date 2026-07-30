"""Crop the supplied print sheets into one upright image per component.

This is archival tooling for the manual-review source set. It is not used by
the runtime catalog or the Scala build.
"""

import json
import re
import sys
from collections import defaultdict
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CATALOG = ROOT / "docs/catalog/new-foundations-component-catalog.json"
DEFAULT_RENDERS = Path("/private/tmp/oath-catalog-renders")
DEFAULT_OUTPUT = Path("/private/tmp/oath-runtime-catalog/crops")


def natural_slot(value):
    if isinstance(value, int):
        return (value,)
    return tuple(
        int(part) if part.isdigit() else part
        for part in re.split(r"([0-9]+)", str(value))
    )


def crop_grid(image_path, records, columns, rows, output, positions=None, rotate_cards=0):
    image = Image.open(image_path).convert("RGB").rotate(180)
    width, height = image.size
    left, right = 0.09 * width, 0.91 * width
    top, bottom = 0.075 * height, 0.925 * height
    cell_width = (right - left) / columns
    cell_height = (bottom - top) / rows

    for index, record in enumerate(records):
        row, column = positions[index] if positions else divmod(index, columns)
        box = (
            round(left + column * cell_width),
            round(top + row * cell_height),
            round(left + (column + 1) * cell_width),
            round(top + (row + 1) * cell_height),
        )
        card = image.crop(box)
        if rotate_cards:
            card = card.rotate(rotate_cards, expand=True)
        card.thumbnail((1400, 1800), Image.Resampling.LANCZOS)
        safe_id = re.sub(r"[^a-z0-9]+", "-", record["definitionId"].lower())
        card.save(output / f"{safe_id}.jpg", quality=94)


def main():
    catalog_path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_CATALOG
    render_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_RENDERS
    output = Path(sys.argv[3]) if len(sys.argv) > 3 else DEFAULT_OUTPUT
    output.mkdir(parents=True, exist_ok=True)

    catalog = json.loads(catalog_path.read_text())
    grouped = defaultdict(list)
    supported = {"denizen", "relic", "edifice-face", "legacy"}
    for component in catalog["components"]:
        if component["kind"] not in supported:
            continue
        provenance = component["printEvidence"][0]["provenance"][0]
        grouped[
            component["kind"],
            provenance["sourceId"],
            provenance["file"],
            provenance["page"],
        ].append((natural_slot(provenance.get("sheetSlot", 1)), component))

    manifest = []
    for (kind, source_id, filename, page), entries in sorted(grouped.items()):
        records = [component for _, component in sorted(entries)]
        positions = None
        rotate_cards = 0
        if filename == "unchanged-denizens.pdf":
            render_name = f"base-{page}.png"
            columns, rows = 4, 2
        elif filename == "denizens.pdf":
            render_name = f"denizens-{page}.png"
            columns, rows = 4, 2
        elif filename == "edifices.pdf":
            render_name = f"edifices-{page}.png"
            columns, rows = 4, 2
        elif filename == "legacies.pdf":
            render_name = f"legacies-{page}.png"
            columns, rows = 4, 2
            rotate_cards = -90
        elif filename == "relics.pdf":
            render_name = f"relics-{page}.png"
            columns, rows = 4, 3
            positions = [
                (2, 0), (1, 0), (2, 1), (1, 1),
                (2, 2), (1, 2), (2, 3), (1, 3),
                (0, 0), (0, 1), (0, 2), (0, 3),
            ]
            rotate_cards = -90
        elif filename == "relic-grand-scepter.pdf":
            render_name = "relic-grand-scepter-1.png"
            columns, rows = 1, 1
            positions = None
            rotate_cards = -90
        else:
            continue

        crop_grid(
            render_dir / render_name,
            records,
            columns,
            rows,
            output,
            positions=positions,
            rotate_cards=rotate_cards,
        )
        manifest.extend(
            {
                "definitionId": component["definitionId"],
                "kind": kind,
                "name": component["name"],
                "sourceId": source_id,
                "file": filename,
                "page": page,
                "crop": f"{re.sub(r'[^a-z0-9]+', '-', component['definitionId'].lower())}.jpg",
            }
            for component in records
        )

    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"cropped {len(manifest)} components into {output}")


if __name__ == "__main__":
    main()
