# Card Classes Slice 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every implemented relic and edifice sits in its power's file, each
relic and edifice power reads its card from the card object instead of
looking it up in the catalog, and the Grand Scepter and the Hall of Ministers
declare their own operation restrictions.

**Architecture:** Three scripted, reviewable steps. Task 1 moves the 36
implemented relic and edifice objects out of `catalog/holding/` and into their
power files, guarded by a test that any card leaves the holding files exactly
when it is implemented. Task 2 replaces `CatalogCards.relic` and
`CatalogCards.edifice` with static references, so a relic or edifice power is
always registered whatever cards a catalog lists, and deletes `CatalogCards`.
Task 3 turns `GrandScepter` and `HallOfMinisters` into objects that name their
cards, so neither depends on the catalog.

**Tech Stack:** Scala 3.9, sbt (`./sbtw`), munit, Python 3 (edit scripts).

**Spec:** `docs/superpowers/specs/2026-10-01-card-classes-design.md`
(slice 3). Read it first. The slice 2 plan,
`docs/superpowers/plans/2026-10-01-card-classes-slice2.md`, did the same
moves for denizens; this plan follows its patterns.

## Global Constraints

- Printed ids and power ids do not change (`"R01"`, `"E16"`,
  `"grand-scepter"`, `"relic.sticky-fire"`, `"edifice.e20.ruined"`).
- Card objects keep their names (`StickyFireCard`, `HallOfMinistersCard`) and
  their exact text; a move changes only the object's file and package.
- References run one way: behaviour to card. A card object never refers to
  its power.
- `gameplay/` never contains the string `rulesText` and never reads `.text`.
- `catalog/` imports only `model` (and the JDK).
- Production Scala files are at most 800 lines.
- Compiler flags are `-Werror` with unused imports, privates and locals.
  Remove every import the compiler flags.
- Sites and legacies stay where they are (slice 4). Site powers whose site is
  absent from a catalog are still omitted.
- The reviewed-catalog stubs (`ActionPowers`, `MusterPowers`, `RestPowers`,
  `NegotiationPowers`, `CampaignPowers`, `RecoverPowers`) are not touched;
  they belong to the reviewed-catalog cleanup item.
- Never touch the live database `var/oathdigital`.
- Gates at the end of every task: `./sbtw test frontend/test`,
  `python3 scripts/check-architecture.py`,
  `python3 scripts/check-markdown-links.py`.
- Commit trailer: the committing model's own line.
- Stage explicit paths only; never `git add -A`.

## Decisions made while planning

The spec fixes the target; these are the choices for getting there.

- **Implemented means registered, or enforced as a restriction.** A relic or
  edifice moves when one of its powers is registered in
  `WalkerPowerCatalog.default` or `PhasePowerCatalog.default` (27 relics, 7
  edifices), or when an operation restriction enforces its printed power: the
  Grand Scepter (`GrandScepter`) and the Hall of Ministers
  (`HallOfMinisters`). That is 36 cards. Cards that only have a stub in the
  reviewed catalog stay in holding.
- **One destination per card, listed explicitly.** Fourteen relic paid
  actions already name their holding object rather than a power-id string,
  so the slice 2 trick of finding the file by the id string does not work.
  The move script carries an explicit table instead. An edifice whose two
  faces are implemented in two files goes into the intact face's file:
  Towering Rampart into `ToweringRampart.scala` (not `CrackedRampart.scala`)
  and Marble Fountains into `MarbleFountains.scala` (not
  `MurkyFountain.scala`).
- **The Grand Scepter and the Hall of Ministers live in
  `gameplay/operations/`.** Their behaviour is an operation restriction, so
  their card objects go beside it, in `GrandScepter.scala` and
  `HallOfMinisters.scala`. This is the spec's "each card sits in its power's
  file", applied to the file that implements the power.
- **No more optional relic or edifice powers.** As in slice 2: a power that
  took the catalog only to find its card id becomes a `case object` and
  loses `forCatalog` (9 powers: Sticky Fire, Brass Army, Black Sword,
  Fearsome Shield, Bandit Standard, Bag of Siegeworks, Towering Rampart,
  Cracked Rampart, Sacred Ground). The other 12 keep a `forCatalog(catalog)`
  that returns the power itself, not an `Option`, because their behaviour
  still reads the catalog (Cup of Plenty, Great Market, Bandit Market, Great
  Forge, Broken Forge, Proving Grounds, Empty Grounds, Oaken Fortress,
  Rotting Fortress, Circlet of Command, Truthful Harp, Dragonskin Drum). Each
  keeps its card-id member under its old name (`relicId`, `edificeId`,
  `edifice`, `fortress` or `cardId`) so the body is unchanged.
- **Every relic and edifice power id comes from its card.** A power's
  `PowerId("relic.…")` or `PowerId("edifice.…")` literal becomes the card
  member that prints it (`StickyFireCard.power.id`,
  `ToweringRampartCard.ruined.power.id`, `BrassArmyCard.campaign.id`). This
  also covers the paid actions slice 2 left with literals (Crystal Vial,
  Brass Horse) and Horned Mask and Marble Fountains.
- **The restrictions hold whatever a catalog lists.** There is one Grand
  Scepter and one Hall of Ministers, so `GrandScepter` and
  `HallOfMinisters` become `case object`s naming their cards, and
  `OperationRestrictions.printedBy` always includes both. A catalog without
  the card never puts it into play, so the restriction is inert there.
- **Lookups that stay.** `ActiveModifier.printedBy` keeps
  `catalog.relicWithPower`: it maps a modifier chosen at run time to its card,
  a real index lookup. `ExecutableCatalog.relicWithPower` and
  `edificeWithPower` stay, with their `ExecutableCatalogSuite` index tests.
- **`CatalogCards` is deleted.** After Task 2 it has no members left. Its
  test in `SelectedModifierSuite` goes with it.

---

## File Structure

| Path | Change |
|------|--------|
| `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala` | Loses 28 relics |
| `src/main/scala/oathdigital/catalog/holding/HoldingEdifices.scala` | Loses 8 edifices |
| 34 power files under `src/main/scala/oathdigital/gameplay/powers/` | Gain their card object |
| `src/main/scala/oathdigital/gameplay/operations/GrandScepter.scala` | Gains `TheGrandScepterCard`; `GrandScepter` becomes an object |
| `src/main/scala/oathdigital/gameplay/operations/HallOfMinisters.scala` | Gains `HallOfMinistersCard`; `HallOfMinisters` becomes an object |
| `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala` | Always prints both restrictions |
| `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` | Imports the moved objects |
| `src/main/scala/oathdigital/application/{FirstGameChronicleGenerator,ImplementedCardCatalog}.scala` | Import the scepter from `gameplay.operations` |
| 21 relic and edifice power files that used `CatalogCards` | Static card reference; 9 become `case object`s |
| `src/main/scala/oathdigital/gameplay/powers/CatalogCards.scala` | Deleted |
| Aggregator scaladocs (list in Task 2) | Say only site powers can be omitted |
| `src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala` | Two tests generalised to relics and edifices |
| `src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala` | Restrictions hold whatever the catalog lists |
| Test callers of `forCatalog(...).get`, `CatalogCards`, the holding scepter | Drop `.get`, name the object, import from the new package |
| `docs/ROADMAP.md` | Slice 3 ticked |

The three edit scripts share one import helper. Save all four from this plan
into `<scratchpad>/s3/` before Task 1 (the code blocks below are complete
files). They take the worktree root as their only argument and assert on
anything unexpected rather than guessing. Each was run once, in order, on a
throwaway copy of `main` at `4a340575`, and the result passed every gate.

### Shared import helper (`s3_imports.py`)

```python
"""Import editing shared by the card classes slice 3 scripts."""
import re


def render(pkg, names):
    names = sorted(set(names))
    if len(names) == 1:
        return f"import {pkg}.{names[0]}\n"
    lines, cur = [], f"import {pkg}.{{"
    for i, name in enumerate(names):
        piece = name + (", " if i < len(names) - 1 else "}")
        if len(cur) + len(piece.rstrip()) > 80:
            lines.append(cur.rstrip())
            cur = "  " + piece
        else:
            cur += piece
    lines.append(cur)
    return "\n".join(lines) + "\n"


def _find(src, pkg):
    return re.search(r"^import " + re.escape(pkg) + r"\.(\{[^}]*\}|\w+)\n",
                     src, re.M)


def names_of(src, pkg):
    m = _find(src, pkg)
    if not m:
        return set()
    sel = m.group(1)
    if sel.startswith("{"):
        found = {x.strip() for x in sel[1:-1].replace("\n", " ").split(",")}
        assert not any("=>" in x for x in found), (pkg, sel)
        return found
    return {sel}


def add(src, pkg, names):
    """Adds `names` to the import of `pkg`, unless a wildcard covers them."""
    if re.search(r"^import " + re.escape(pkg) + r"\._\n", src, re.M):
        return src
    m = _find(src, pkg)
    if m:
        merged = names_of(src, pkg) | set(names)
        return src[:m.start()] + render(pkg, merged) + src[m.end():]
    line = render(pkg, names)
    starts = [m for m in re.finditer(r"^import (\S+)", src, re.M)]
    after = [m for m in starts if m.group(1) > pkg]
    if after:
        at = after[0].start()
        return src[:at] + line + src[at:]
    last = starts[-1]
    end = src.index("\n", last.start()) + 1
    while src[end:end + 2] == "  ":
        end = src.index("\n", end) + 1
    return src[:end] + line + src[end:]


def remove(src, pkg, names):
    """Drops `names` from the import of `pkg`; drops the line when empty."""
    m = _find(src, pkg)
    if not m:
        return src
    left = names_of(src, pkg) - set(names)
    return src[:m.start()] + (render(pkg, left) if left else "") + src[m.end():]
```

---

### Task 1: Move implemented relics and edifices beside their powers

**Files:**
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingRelics.scala`,
  `src/main/scala/oathdigital/catalog/holding/HoldingEdifices.scala`
- Modify: the 36 destination files in the script's `DEST` table
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/MurkyFountain.scala`
  and the other files that import a moved card (the script finds them)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Test: `src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: the 36 card objects in their new packages, for example
  `oathdigital.gameplay.powers.campaign.StickyFireCard`,
  `oathdigital.gameplay.powers.setup.GreatMarketCard`,
  `oathdigital.gameplay.operations.TheGrandScepterCard` and
  `oathdigital.gameplay.operations.HallOfMinistersCard`. Task 2's script
  finds every `object …Card extends Relic(` or `extends Edifice(` under
  `gameplay/`.

- [ ] **Step 1: Generalise the holding test**

In `CardCatalogSuite.scala`, replace the test
`"a denizen leaves the holding files once one of its powers is registered"`
(the whole test body) with:

```scala
  test("a card leaves the holding files once it is implemented"):
    val registered = (WalkerPowerCatalog.default(catalog).powers.map(_.id) ++
      PhasePowerCatalog.default(catalog).powers.map(_.id)).toSet
    // The Grand Scepter and the Hall of Ministers are implemented as
    // operation restrictions, not as registered powers.
    val restrictions = Set[CardId](RelicId("grand-scepter"), EdificeId("E16"))
    def held(card: AnyRef): Boolean =
      card.getClass.getPackageName == "oathdigital.catalog.holding"
    def implemented(id: CardId, printed: Vector[PrintedPower]): Boolean =
      restrictions(id) || printed.exists(power => registered(power.id))
    val misplaced =
      catalog.denizens.collect { case card
          if held(card) == implemented(card.id, card.powers) => card.name } ++
        catalog.relics.collect { case card
          if held(card) == implemented(card.id, card.powers) => card.name } ++
        catalog.edifices.collect { case card if held(card) ==
            implemented(card.id, card.intact.powers ++ card.ruined.powers) =>
          card.intact.name }
    assertEquals(misplaced, Vector.empty)
```

and change the suite's imports to:

```scala
import oathdigital.catalog.{AdviserOnly, ExecutableCatalog, Locked,
  PrintedPower, PrintsPowers, SiteOnly}
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.model.{CardId, EdificeId, PowerId, RelicId}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: FAIL in `a card leaves the holding files once it is implemented`,
listing the 36 names (Sticky Fire … The Grand Scepter, Great Market … Oaken
Fortress, Hall of Ministers). The other 10 tests pass.

- [ ] **Step 3: Save and run the move script**

Save `s3_move.py` (below) into `<scratchpad>/s3/`, then run:

```bash
python3 <scratchpad>/s3/s3_move.py <worktree root>
```

Expected output: `moved 36 cards; updated 24 files`.

```python
"""Card classes slice 3, Task 1: move implemented relics and edifices beside
their powers.

Usage: python3 s3_move.py <worktree root>
"""
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import s3_imports as imports

W = sys.argv[1].rstrip("/") + "/"
M = W + "src/main/scala/oathdigital/"
T = W + "src/test/scala/oathdigital/"
P = "gameplay/powers/"
DEST = {
    "StickyFireCard": P + "campaign/StickyFire.scala",
    "BrassHorseCard": P + "action/BrassHorse.scala",
    "TruthfulHarpCard": P + "search/TruthfulHarp.scala",
    "HornedMaskCard": P + "wake/HornedMask.scala",
    "CupOfPlentyCard": P + "economy/CupOfPlenty.scala",
    "WhistleCard": P + "action/Whistle.scala",
    "DowsingSticksCard": P + "action/DowsingSticks.scala",
    "SkeletonKeyCard": P + "action/SkeletonKey.scala",
    "OracularPigCard": P + "action/OracularPig.scala",
    "CircletOfCommandCard": P + "targeting/CircletOfCommand.scala",
    "IvoryEyeCard": P + "action/IvoryEye.scala",
    "ShiftingMapCard": P + "action/ShiftingMap.scala",
    "BookOfRecordsCard": P + "action/BookOfRecords.scala",
    "DragonskinDrumCard": P + "travel/DragonskinDrum.scala",
    "CrystalVialCard": P + "action/CrystalVial.scala",
    "BoneDiceCard": P + "action/BoneDice.scala",
    "BrassArmyCard": P + "campaign/BrassArmy.scala",
    "FearsomeShieldCard": P + "campaign/FearsomeShield.scala",
    "BanditStandardCard": P + "campaign/BanditStandard.scala",
    "AmberFlameCard": P + "action/AmberFlame.scala",
    "BlackSwordCard": P + "campaign/BlackSword.scala",
    "BarbedNetCard": P + "action/BarbedNet.scala",
    "BagOfSiegeworksCard": P + "campaign/BagOfSiegeworks.scala",
    "MagicCarpetCard": P + "action/MagicCarpet.scala",
    "MagicWaterskinCard": P + "action/MagicWaterskin.scala",
    "DemonTailCard": P + "action/DemonTail.scala",
    "ClayRattleCard": P + "action/ClayRattle.scala",
    "TheGrandScepterCard": "gameplay/operations/GrandScepter.scala",
    "GreatMarketCard": P + "setup/GreatMarketRules.scala",
    "GreatForgeCard": P + "setup/GreatForgeRules.scala",
    "SacredGroundCard": P + "cardplay/SacredGround.scala",
    "MarbleFountainsCard": P + "wake/MarbleFountains.scala",
    "HallOfMinistersCard": "gameplay/operations/HallOfMinisters.scala",
    "ToweringRampartCard": P + "campaign/ToweringRampart.scala",
    "ProvingGroundsCard": P + "setup/ProvingGroundsRules.scala",
    "OakenFortressCard": P + "targeting/FortressRules.scala",
}
HOLDING = "oathdigital.catalog.holding"
TRAITS = ("AdviserOnly", "Locked", "SiteOnly")
KINDS = {"Relic": ({"PrintedPower", "Relic"}, {"Cost", "PowerId", "RelicId"}),
         "Edifice": ({"Edifice", "EdificeFace", "PrintedPower"},
                     {"Cost", "EdificeId", "PowerId", "Suit"})}

blocks, package_of = {}, {}
for kind, name in (("Relic", "HoldingRelics"), ("Edifice", "HoldingEdifices")):
    f = M + f"catalog/holding/{name}.scala"
    parts = re.split(rf"\n(?=object \w+Card extends {kind}\()", open(f).read())
    kept = [parts[0]]
    for part in parts[1:]:
        card = re.match(r"object (\w+Card) ", part).group(1)
        if card in DEST:
            blocks[card] = (kind, part.rstrip("\n"))
        else:
            kept.append(part)
    s = "\n".join(kept)
    if not s.endswith("\n"):
        s += "\n"
    used = {t for t in TRAITS if re.search(rf"\bwith {t}\b", s)}
    s = imports.remove(s, "oathdigital.catalog", set(TRAITS) - used)
    open(f, "w").write(s)
assert set(blocks) == set(DEST), set(DEST) - set(blocks)

for card, rel in DEST.items():
    kind, block = blocks[card]
    dest = M + rel
    s = open(dest).read()
    lines = s.split("\n")
    last = max(i for i, l in enumerate(lines) if l.startswith("import "))
    j = last + 1
    while j < len(lines) and lines[j].startswith("  "):
        j += 1
    lines[j:j] = [""] + block.split("\n")
    s = "\n".join(lines)
    catalog_names, model_names = KINDS[kind]
    used = {t for t in TRAITS if re.search(rf"\bwith {t}\b", block)}
    s = imports.add(s, "oathdigital.catalog", catalog_names | used)
    s = imports.add(s, "oathdigital.model", model_names)
    package_of[card] = re.search(r"^package (\S+)", s, re.M).group(1)
    open(dest, "w").write(s)

def code_without_imports(s):
    return re.sub(r"^import [^\n]*\n(?:  [^\n]*\n)*", "", s, flags=re.M)


files = [f for f in glob.glob(M + "**/*.scala", recursive=True) +
         glob.glob(T + "**/*.scala", recursive=True)
         if "/catalog/holding/" not in f]
touched = 0
for f in files:
    s = open(f).read()
    new = s
    own = re.search(r"^package (\S+)", new, re.M).group(1)
    imported = imports.names_of(new, HOLDING) & set(DEST)
    if imported:
        new = imports.remove(new, HOLDING, imported)
        for pkg in sorted({package_of[c] for c in imported}):
            if pkg != own:
                new = imports.add(new, pkg, {c for c in imported
                                             if package_of[c] == pkg})
    for card, pkg in package_of.items():
        if f"{HOLDING}.{card}" in code_without_imports(new):
            new = new.replace(f"{HOLDING}.{card}", card)
            if pkg != own:
                new = imports.add(new, pkg, {card})
    if re.search(r"^import " + re.escape(HOLDING) + r"\._", new, re.M):
        for pkg in sorted(set(package_of.values())):
            new = imports.add(new, pkg, {c for c, p in package_of.items()
                                         if p == pkg})
    if new != s:
        open(f, "w").write(new)
        touched += 1
print(f"moved {len(blocks)} cards; updated {touched} files")
```

- [ ] **Step 4: Review the move**

Run `git diff --stat` and spot-check:
- `ClayRattle.scala` gains `object ClayRattleCard` below its imports and
  loses `import oathdigital.catalog.holding.ClayRattleCard`.
- `MurkyFountain.scala` imports `MarbleFountainsCard` from
  `oathdigital.gameplay.powers.wake`.
- `OperationRestrictions.scala` loses its holding import (the scepter is now
  in the same package).
- `FirstGameChronicleGeneratorSuite.scala` and
  `GameServerGatewaySubmitBeginSuite.scala` import
  `oathdigital.gameplay.operations.TheGrandScepterCard` and use the bare name.
- `NewFoundations.scala` gains `import oathdigital.gameplay.operations.{…}`,
  `…powers.setup.{…}` and the new names in the existing package imports.

- [ ] **Step 5: Run the suite to see it pass**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: PASS, 11 tests.

- [ ] **Step 6: Run the gates**

Run: `./sbtw test frontend/test`, then
`python3 scripts/check-architecture.py` and
`python3 scripts/check-markdown-links.py`.
Expected: root 2539 tests pass, frontend 468 pass, both checks pass.

- [ ] **Step 7: Commit**

Stage the files `git status --short` lists (explicit paths, no `-A`), then:

```bash
git commit -m "refactor(cards): move implemented relics and edifices beside their powers"
```

---

### Task 2: Relic and edifice powers name their cards

**Files:**
- Modify: the 21 power files that call `CatalogCards`, plus
  `CrystalVial.scala`, `BrassHorse.scala`, `HornedMask.scala`,
  `MarbleFountains.scala`, `CrackedRampart.scala`
- Modify: `WalkerPowerCatalog.scala`, `PhasePowerCatalog.scala`,
  `ActionModifiers.scala`, `targeting/TargetProtections.scala`,
  `cardplay/CardPlayTriggers.scala`, `campaign/PlanRules.scala`,
  `campaign/BattlePlans.scala`, `campaign/SimplePlans.scala`,
  `travel/TravelModifiers.scala` (all under
  `src/main/scala/oathdigital/gameplay/powers/`)
- Delete: `src/main/scala/oathdigital/gameplay/powers/CatalogCards.scala`
- Modify: the test callers (`CampaignPlansSuite`, `SelectedModifierSuite`,
  `StickyFireSuite`, `SacredGroundSuite`, `TruthfulHarpSuite`,
  `GreatForgeRulesSuite`, `GreatMarketRulesSuite`, `ProvingGroundsRulesSuite`,
  `CircletOfCommandSuite`, `FortressRulesSuite`, `DragonskinDrumSuite`)
- Test: `src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala`

**Interfaces:**
- Consumes: Task 1's card objects under `gameplay/`.
- Produces: `case object`s `StickyFire`, `BrassArmy`, `BlackSword`,
  `FearsomeShield`, `BanditStandard`, `BagOfSiegeworks`, `ToweringRampart`,
  `CrackedRampart`, `SacredGround`; and `X.forCatalog(catalog): X` (no
  `Option`) for `CupOfPlenty`, `GreatMarket`, `BanditMarket`, `GreatForge`,
  `BrokenForge`, `ProvingGrounds`, `EmptyGrounds`, `OakenFortress`,
  `RottingFortress`, `CircletOfCommand`, `TruthfulHarp`, `DragonskinDrum`.
  `CatalogCards` no longer exists.

- [ ] **Step 1: Generalise the registration test**

In `CardCatalogSuite.scala`, replace the test
`"denizen powers are registered whichever denizens a catalog lists"` with:

```scala
  test("card powers are registered whichever cards a catalog lists"):
    def cardPowers(listed: ExecutableCatalog): Set[PowerId] =
      (WalkerPowerCatalog.default(listed).powers.map(_.id) ++
        PhasePowerCatalog.default(listed).powers.map(_.id))
        .filter(id => Vector("denizen.", "relic.", "edifice.")
          .exists(id.value.startsWith)).toSet
    assertEquals(cardPowers(catalog.copy(denizens = Vector.empty,
      relics = Vector.empty, edifices = Vector.empty)), cardPowers(catalog))
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: FAIL in `card powers are registered whichever cards a catalog
lists`: the left set lacks the 21 relic and edifice powers found through
`CatalogCards`. The other 10 tests pass.

- [ ] **Step 3: Save and run the static-reference script**

Save `s3_static.py` (below) into `<scratchpad>/s3/`, then run:

```bash
python3 <scratchpad>/s3/s3_static.py <worktree root>
```

Expected output:
`48 printed powers; 9 powers became objects, 12 keep a catalog`.

The script:
- replaces each relic or edifice power-id literal under `gameplay/` (outside
  the card declarations, the reviewed stubs and `gameplay/operations/`) with
  the card member that prints it;
- turns the 9 catalog-free powers into `case object`s, and gives the other 12
  a card-id member and a `forCatalog` that returns the power;
- rewrites callers (`.toVector`, `.get`, `++` chains), and lays out the
  `WalkerPowerCatalog` tail as one `Vector`;
- rewrites the aggregator scaladocs: only site powers can still be omitted;
- removes the `CatalogCards` test from `SelectedModifierSuite`, points
  `SacredGroundSuite` at `SacredGroundCard.id`, and deletes `CatalogCards`.

```python
"""Card classes slice 3, Task 2: relic and edifice powers name their cards.

Usage: python3 s3_static.py <worktree root>
"""
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import s3_imports as imports

W = sys.argv[1].rstrip("/") + "/"
M = W + "src/main/scala/oathdigital/"
T = W + "src/test/scala/oathdigital/"
REVIEWED = {M + "gameplay/powers/" + f for f in (
    "ActionPowers.scala", "MusterPowers.scala", "RestPowers.scala",
    "NegotiationPowers.scala", "CampaignPowers.scala", "RecoverPowers.scala")}
FOR = re.compile(r"  def forCatalog\(catalog: ExecutableCatalog\): "
                 r"Option\[(?P<cls>\w+)\] =\s*"
                 r"CatalogCards\.(?:relic|edifice)\(catalog, id\)\s*"
                 r"\.map\(new (?P=cls)\(_(?P<cat>, catalog)?\)\)\n")
PRUNE = (("oathdigital.gameplay.powers", "CatalogCards"),
         ("oathdigital.catalog", "ExecutableCatalog"),
         ("oathdigital.model", "RelicId"),
         ("oathdigital.model", "EdificeId"),
         ("oathdigital.gameplay.setup.FirstGameSetupFixture", "catalog"))


def header_end(lines, start):
    """Index of the line that ends the class header opened at `start`."""
    i = start
    while not lines[i].rstrip().endswith(":"):
        i += 1
    return i


def body_end(lines, start):
    """First top-level line after the body that opens after `start`."""
    i = start + 1
    while i < len(lines) and (lines[i] == "" or lines[i].startswith(" ")):
        i += 1
    while lines[i - 1] == "":
        i -= 1
    return i


def code_without_imports(s):
    return re.sub(r"^import [^\n]*\n(?:  [^\n]*\n)*", "", s, flags=re.M)


def prune(s):
    for pkg, name in PRUNE:
        if name in imports.names_of(s, pkg) and \
                not re.search(rf"\b{name}\b", code_without_imports(s)):
            s = imports.remove(s, pkg, {name})
    return s


def to_object(s, cls, field, typ, card):
    lines = s.split("\n")
    start = next(i for i, l in enumerate(lines)
                 if l.startswith(f"final case class {cls} private "
                                 f"({field}: {typ})"))
    lines[start] = lines[start].replace(
        f"final case class {cls} private ({field}: {typ})",
        f"case object {cls}")
    end = header_end(lines, start)
    if end == start + 1 and len(lines[start]) + len(lines[end]) - 3 <= 80:
        lines[start:end + 1] = [lines[start] + " " + lines[end].strip()]
        end = start
    lines.insert(end + 1, f"  val {field}: {typ} = {card}.id")
    lines = [l for l in lines if l != f"  def id: PowerId = {cls}.id"]
    comp = lines.index(f"object {cls}:")
    comp_end = body_end(lines, comp)
    members = lines[comp + 1:comp_end]
    del lines[comp:comp_end]
    while comp < len(lines) and lines[comp] == "" and \
            (comp == len(lines) - 1 or lines[comp - 1] == ""):
        del lines[comp]
    while members and members[-1] == "":
        members.pop()
    start = next(i for i, l in enumerate(lines)
                 if l.startswith(f"case object {cls}"))
    end = header_end(lines, start) + 1
    lines[end + 1:end + 1] = members + [""]
    return re.sub(r"\n{3,}", "\n\n", "\n".join(lines))


def keep_catalog(s, cls, field, typ, card):
    new = re.sub(rf"final case class {cls} private \({field}: {typ},"
                 r"\s*catalog: ExecutableCatalog\)",
                 f"final case class {cls} private (catalog: ExecutableCatalog)",
                 s)
    assert new != s, cls
    lines = new.split("\n")
    start = next(i for i, l in enumerate(lines)
                 if l.startswith(f"final case class {cls} "))
    if len(lines[start]) > 80 and " extends " in lines[start]:
        head, tail = lines[start].split(" extends ", 1)
        lines[start:start + 1] = [head, "    extends " + tail]
    lines.insert(header_end(lines, start) + 1,
                 f"  val {field}: {typ} = {card}.id")
    return "\n".join(lines)


# Each moved card's power ids, and the member that names each one.
paths = {}  # power id -> "Card.member" or "Card.face.member"
for f in glob.glob(M + "gameplay/**/*.scala", recursive=True):
    s = open(f).read()
    for m in re.finditer(r"^object (\w+Card) extends (Relic|Edifice)\(", s,
                         re.M):
        card, kind = m.group(1), m.group(2)
        block = re.match(r"[^\n]*\n(?:(?:  [^\n]*|)\n)*",
                         s[m.start():]).group(0)
        if kind == "Relic":
            for v in re.finditer(r'^  val (\w+) = PrintedPower\(PowerId\('
                                 r'"([^"]+)"\)', block, re.M):
                paths[v.group(2)] = f"{card}.{v.group(1)}"
        else:
            for face in ("intact", "ruined"):
                fm = re.search(rf"^  object {face} [^\n]*\n((?:    [^\n]*\n)*)",
                               block, re.M)
                for v in re.finditer(r'^    val (\w+) = PrintedPower\(PowerId'
                                     r'\("([^"]+)"\)', fm.group(1), re.M):
                    paths[v.group(2)] = f"{card}.{face}.{v.group(1)}"
card_package = {}
for f in glob.glob(M + "gameplay/**/*.scala", recursive=True):
    s = open(f).read()
    for m in re.finditer(r"^object (\w+Card) extends (?:Relic|Edifice)\(", s,
                         re.M):
        card_package[m.group(1)] = re.search(r"^package (\S+)", s,
                                             re.M).group(1)

objects, classes = set(), set()
sources = [f for f in glob.glob(M + "gameplay/**/*.scala", recursive=True)
           if f not in REVIEWED and "/gameplay/operations/" not in f]
for f in sources:
    s = open(f).read()
    orig = s
    own = re.search(r"^package (\S+)", s, re.M).group(1)
    for pid, path in paths.items():
        lit = f'PowerId("{pid}")'
        decl = re.compile(r"(val \w+ = PrintedPower\()" + re.escape(lit))
        guarded = decl.sub(lambda m: m.group(1) + "\0", s)
        if lit in guarded:
            guarded = guarded.replace(lit, f"{path}.id")
            card = path.split(".")[0]
            if card_package[card] != own:
                guarded = imports.add(guarded, card_package[card], {card})
        s = guarded.replace("\0", lit)
    for m in list(FOR.finditer(s)):
        cls = m.group("cls")
        cm = re.search(rf"^object {cls}:\n  val id: PowerId = (\w+Card)\.", s,
                       re.M)
        assert cm, (f, cls)
        card = cm.group(1)
        hm = re.search(rf"^final case class {cls} private \((\w+): "
                       r"(RelicId|EdificeId)", s, re.M)
        assert hm, (f, cls)
        field, typ = hm.group(1), hm.group(2)
        m = FOR.search(s)
        assert m.group("cls") == cls
        if m.group("cat"):
            s = s[:m.start()] + (
                f"  def forCatalog(catalog: ExecutableCatalog): {cls} =\n"
                f"    new {cls}(catalog)\n") + s[m.end():]
            s = keep_catalog(s, cls, field, typ, card)
            classes.add(cls)
        else:
            s = s[:m.start()] + s[m.end():]
            s = to_object(s, cls, field, typ, card)
            objects.add(cls)
    if s != orig:
        open(f, "w").write(prune(s))

p = M + "gameplay/powers/WalkerPowerCatalog.scala"
s = open(p).read()
old = """      Vector(Dazzle.forCatalog(catalog)) ++ GreatMarket.forCatalog(catalog) ++
      BanditMarket.forCatalog(catalog) ++ GreatForge.forCatalog(catalog) ++
      BrokenForge.forCatalog(catalog) ++ ProvingGrounds.forCatalog(catalog) ++
      EmptyGrounds.forCatalog(catalog) :+ TakeWealthLimit :+ ConspiracyWhenPlayed,"""
assert old in s, p
s = s.replace(old, """      Vector(Dazzle.forCatalog(catalog), GreatMarket.forCatalog(catalog),
        BanditMarket.forCatalog(catalog), GreatForge.forCatalog(catalog),
        BrokenForge.forCatalog(catalog), ProvingGrounds.forCatalog(catalog),
        EmptyGrounds.forCatalog(catalog), TakeWealthLimit,
        ConspiracyWhenPlayed),""")
open(p, "w").write(s)

callers = glob.glob(M + "**/*.scala", recursive=True) + \
    glob.glob(T + "**/*.scala", recursive=True)
for f in callers:
    s = open(f).read()
    new = s
    for cls in objects:
        new = re.sub(rf"\b{cls}\.forCatalog\(\w+\)\.toVector",
                     f"Vector({cls})", new)
        new = re.sub(rf"\b{cls}\s*\.forCatalog\(\w+\)\s*\.get\b", cls, new)
        new = re.sub(rf"\[{cls}\]", f"[{cls}.type]", new)
        new = re.sub(rf"(?<=\+\+ )\b{cls}\.forCatalog\(\w+\)"
                     rf"|\b{cls}\.forCatalog\(\w+\)(?= \+\+)",
                     f"Vector({cls})", new)
    for cls in classes:
        new = re.sub(rf"\b({cls}\.forCatalog\(\w+\))\.toVector",
                     r"Vector(\1)", new)
        new = re.sub(rf"(?<=\+\+ )\b({cls}\.forCatalog\(\w+\))"
                     rf"|\b({cls}\.forCatalog\(\w+\))(?= \+\+)",
                     lambda m: f"Vector({m.group(1) or m.group(2)})", new)
        new = re.sub(rf"\b({cls}\s*\.forCatalog\(\w+\))\s*\.get\b", r"\1",
                     new)
    if new != s:
        open(f, "w").write(prune(new))

p = T + "gameplay/powers/SelectedModifierSuite.scala"
s = open(p).read()
old = '''
  test("the catalog cards helper finds a power's card, or nothing"):
    assertEquals(CatalogCards.relic(catalog, PowerId("relic.dragonskin-drum")),
      Some(RelicId("R20")))
    assertEquals(CatalogCards.edifice(catalog, PowerId("edifice.e28.ruined")),
      Some(EdificeId("E28")))
    assertEquals(CatalogCards.relic(catalog, PowerId("relic.nobody")), None)
'''
assert old in s, p
open(p, "w").write(prune(s.replace(old, "")))

p = T + "gameplay/powers/cardplay/SacredGroundSuite.scala"
s = open(p).read()
old = "CatalogCards.edifice(catalog, SacredGround.id).get"
assert old in s, p
open(p, "w").write(prune(s.replace(old, "SacredGroundCard.id")))

DOCS = {
    "gameplay/powers/PhasePowerCatalog.scala": (
"""/** The production phase powers, beside [[WalkerPowerCatalog]]. A relic, edifice
  * or site power whose card is absent from `catalog` is omitted; a denizen
  * power names its card and is always present.
""",
"""/** The production phase powers, beside [[WalkerPowerCatalog]]. A site power
  * whose site is absent from `catalog` is omitted; every other card power
  * names its card and is always present.
"""),
    "gameplay/powers/WalkerPowerCatalog.scala": (
"""  * caller is running. A relic, edifice or site power whose card is absent
  * from `catalog` (e.g. a synthetic test catalog) is simply omitted, not a
  * construction failure. A denizen power names its card, so it is always
  * present.
""",
"""  * caller is running. A site power whose site is absent from `catalog`
  * (e.g. a synthetic test catalog) is simply omitted, not a construction
  * failure. Every other card power names its card, so it is always present.
"""),
    "gameplay/powers/ActionModifiers.scala": (
"""  * Initiation Rite, the rule that changes a Muster's cost. A relic power whose
  * card is absent from `catalog` is omitted; a denizen power names its card
  * and is always present.
""",
"""  * Initiation Rite, the rule that changes a Muster's cost. Each names its
  * card, so all are always present.
"""),
    "gameplay/powers/targeting/TargetProtections.scala": (
"""  * together. A relic or edifice power whose card is absent from
  * `catalog` is omitted; a denizen power names its card and is always
  * present.
""",
"""  * together. Each names its card, so all are always present.
"""),
    "gameplay/powers/cardplay/CardPlayTriggers.scala": (
"""  * triggers that reward a play and the rules that forbid one. A relic power
  * whose card is absent from `catalog` is omitted; a denizen power names its
  * card and is always present.
""",
"""  * triggers that reward a play and the rules that forbid one. Each names its
  * card, so all are always present.
"""),
    "gameplay/powers/campaign/PlanRules.scala": (
"""  * end). A relic power whose card is absent from `catalog` is
  * omitted; a denizen power names its card and is always present.
""",
"""  * end). Each names its card, so all are always present.
"""),
    "gameplay/powers/campaign/BattlePlans.scala": (
"""  * together: the title's defense, Outriders, Brass Army and Watchdog. A relic
  * plan whose card is absent from `catalog` is omitted; a denizen plan names
  * its card, and the title's defense, which no card prints, is always present.
""",
"""  * together: the title's defense, Outriders, Brass Army and Watchdog. All are
  * always present: each card plan names its card, and no card prints the
  * title's defense.
"""),
    "gameplay/powers/campaign/SimplePlans.scala": (
"""  * Spoils and Military Parade, registered together. A relic or edifice plan
  * whose card is absent from `catalog` is omitted; a denizen plan names its
  * card and is always present.
""",
"""  * Spoils and Military Parade, registered together. Each names its card, so
  * all are always present.
"""),
    "gameplay/powers/travel/TravelModifiers.scala": (
"""/** The Travel modifiers and rules that are not terrain, registered together. A
  * relic power whose card is absent from `catalog` is omitted; a denizen
  * power names its card and is always present. Terrain is
  * [[TravelSitePowers]].
""",
"""/** The Travel modifiers and rules that are not terrain, registered together.
  * Each names its card, so all are always present. Terrain is
  * [[TravelSitePowers]].
"""),
}
for rel, (old, new) in DOCS.items():
    p = M + rel
    s = open(p).read()
    assert old in s, p
    open(p, "w").write(s.replace(old, new))

os.remove(M + "gameplay/powers/CatalogCards.scala")
print(f"{len(paths)} printed powers; {len(objects)} powers became objects, "
      f"{len(classes)} keep a catalog")
```

- [ ] **Step 4: Review the result**

Spot-check:
- `StickyFire.scala`: `case object StickyFire extends BattlePlan:` then
  `val relicId: RelicId = StickyFireCard.id`, `val id: PowerId =
  StickyFireCard.power.id`, the old companion members, then the body.
- `CrackedRampart.scala`: `val id: PowerId = ToweringRampartCard.ruined.power.id`.
- `CupOfPlenty.scala`: `final case class CupOfPlenty private (catalog:
  ExecutableCatalog)` with `extends SelectedModifier:` on its own line, and
  `val cardId: RelicId = CupOfPlentyCard.id`.
- `BattlePlans.scala`: `Vector(BrassArmy)` in the chain.
- `grep -rn "CatalogCards" src` prints nothing.
- No new line is longer than 80 columns outside the moved card headers.

- [ ] **Step 5: Run the suite to see it pass**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: PASS, 11 tests.

- [ ] **Step 6: Run the gates**

Run: `./sbtw test frontend/test`, then
`python3 scripts/check-architecture.py` and
`python3 scripts/check-markdown-links.py`.
Expected: root 2538 tests pass (the `CatalogCards` test is gone), frontend
468 pass, both checks pass.

- [ ] **Step 7: Commit**

Stage the files `git status --short` lists. The script deleted
`CatalogCards.scala`; stage that deletion with
`git add src/main/scala/oathdigital/gameplay/powers/CatalogCards.scala`.
Then:

```bash
git commit -m "refactor(powers): relic and edifice powers name their cards"
```

---

### Task 3: The Grand Scepter and the Hall of Ministers declare their restrictions

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/operations/GrandScepter.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/HallOfMinisters.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala`
- Modify: `src/test/scala/oathdigital/gameplay/OperationResolutionSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala`
- Modify: `docs/ROADMAP.md`

**Interfaces:**
- Consumes: Task 1's `TheGrandScepterCard` and `HallOfMinistersCard` in
  `oathdigital.gameplay.operations`.
- Produces: `case object GrandScepter extends OperationRestriction` with
  `val relic: RelicId`, and `case object HallOfMinisters extends
  OperationRestriction`. `OperationRestrictions.printed` for any catalog is
  `Vector(LockedCards(…), HallOfMinisters, GrandScepter)`.

- [ ] **Step 1: Write the failing test**

In `OperationRestrictionsSuite.scala`, replace the test
`"the catalog prints one Grand Scepter restriction, for its scepter"` with:

```scala
  test("the Grand Scepter and the Hall of Ministers hold whichever cards a " +
      "catalog lists"):
    val bare = OperationRestrictions.forCatalog(
      catalog.copy(relics = Vector.empty, edifices = Vector.empty))
    assertEquals(bare.printed.filterNot(_.isInstanceOf[LockedCards]),
      Vector[OperationRestriction](HallOfMinisters, GrandScepter))
    assertEquals(GrandScepter.relic, scepter)
```

and change its operations import to:

```scala
import oathdigital.gameplay.operations.{GrandScepter, HallOfMinisters,
  LockedCards, OperationPipeline, OperationPolicy, OperationRestrictions}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite"`
Expected: FAIL to compile: `Found: …HallOfMinisters.type, Required:
oathdigital.model.OperationRestriction` (both are still case classes, so
their companions are not restrictions).

- [ ] **Step 3: Save and run the restriction script**

Save `s3_restrict.py` (below) into `<scratchpad>/s3/`, then run:

```bash
python3 <scratchpad>/s3/s3_restrict.py <worktree root>
```

Expected output: `restrictions declared by their cards`.

```python
"""Card classes slice 3, Task 3: the Grand Scepter and the Hall of Ministers
declare their own restrictions.

Usage: python3 s3_restrict.py <worktree root>
"""
import sys

W = sys.argv[1].rstrip("/") + "/"
O = W + "src/main/scala/oathdigital/gameplay/operations/"
T = W + "src/test/scala/oathdigital/gameplay/"


def edit(path, pairs):
    s = open(path).read()
    for old, new in pairs:
        assert s.count(old) == 1, (path, old)
        s = s.replace(old, new)
    open(path, "w").write(s)


edit(O + "GrandScepter.scala", [(
"""  * players, by a `Take` or a `Give`, keeps it in play and is allowed. The
  * scepter is never facedown, so a refusal reveals nothing.
  */
final case class GrandScepter(relic: RelicId) extends OperationRestriction:
""",
"""  * players, by a `Take` or a `Give`, keeps it in play and is allowed. The
  * scepter is never facedown, so a refusal reveals nothing.
  *
  * There is one Grand Scepter, so the restriction names its card and holds
  * whichever relics a catalog lists.
  */
case object GrandScepter extends OperationRestriction:
  val relic: RelicId = TheGrandScepterCard.id

""")])

edit(O + "HallOfMinisters.scala", [
("""import oathdigital.catalog.{Edifice, EdificeFace, ExecutableCatalog, Locked,
  PrintedPower}
""",
"""import oathdigital.catalog.{Edifice, EdificeFace, Locked, PrintedPower}
"""),
("""  * cannot discard them. The actor is the discard's own acting player; a
  * Vision's discard names none, so it is the active player's.
  */
final case class HallOfMinisters(catalog: ExecutableCatalog)
    extends OperationRestriction:
  private val hallPower = PowerId("edifice.e16.intact")

""",
"""  * cannot discard them. The actor is the discard's own acting player; a
  * Vision's discard names none, so it is the active player's.
  *
  * There is one Hall of Ministers, so the restriction names its card and
  * holds whichever edifices a catalog lists.
  */
case object HallOfMinisters extends OperationRestriction:
"""),
("""              case edifice: EdificeState if edifice.side == EdificeSide.Intact =>
                catalog.edifice(edifice.id)
                  .exists(_.intact.powers.exists(_.id == hallPower))
""",
"""              case edifice: EdificeState if edifice.side == EdificeSide.Intact =>
                edifice.id == HallOfMinistersCard.id
""")])

edit(O + "OperationRestrictions.scala", [
("""  * `printed` are the restrictions a catalog's cards print: [[LockedCards]],
  * holding every lock-icon card, the [[HallOfMinisters]], and one
  * [[GrandScepter]] per scepter relic. `active` adds the
  * [[ActiveModifier]] rule for the modifiers selected for the running action,
  * and the restrictions the offered powers register. Every one refuses as
  * `Impossible`: an optional operation is skipped, a required one rejects.
  *
  * `printed` reads the cards' `Locked` trait and the Grand Scepter's card.
  * Card classes slice 3 has the Grand Scepter declare its own restriction.
  */
""",
"""  * `printed` are the restrictions a catalog's cards print: [[LockedCards]],
  * holding every card whose `Locked` trait shows the lock icon, and the
  * [[HallOfMinisters]] and the [[GrandScepter]], which their cards declare.
  * `active` adds the [[ActiveModifier]] rule for the modifiers selected for
  * the running action, and the restrictions the offered powers register.
  * Every one refuses as `Impossible`: an optional operation is skipped, a
  * required one rejects.
  */
"""),
("""    val scepter = catalog.relic(TheGrandScepterCard.id)
      .map(relic => GrandScepter(relic.id))
    Vector(LockedCards(locked), HallOfMinisters(catalog)) ++ scepter
""",
"""    Vector(LockedCards(locked), HallOfMinisters, GrandScepter)
""")])

edit(T + "OperationResolutionSuite.scala", [
("""import oathdigital.gameplay.setup.FirstGameSetupFixture
""", ""),
("""    val restriction = HallOfMinisters(FirstGameSetupFixture.catalog)
""",
"""    val restriction = HallOfMinisters
""")])
print("restrictions declared by their cards")
```

- [ ] **Step 4: Run the suite to see it pass**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite oathdigital.gameplay.OperationResolutionSuite"`
Expected: PASS.

- [ ] **Step 5: Tick the ROADMAP**

In `docs/ROADMAP.md`, replace `- [ ] **Slice 3 - relics and edifices.**`
with:

```markdown
- [x] **Slice 3 - relics and edifices.** The 36 implemented relics and
  edifices sit in their power files; their powers read their card from the
  card object, `CatalogCards` is gone, and the Grand Scepter and the Hall of
  Ministers declare their own restrictions.
```

- [ ] **Step 6: Run the gates**

Run: `./sbtw test frontend/test`, then
`python3 scripts/check-architecture.py` and
`python3 scripts/check-markdown-links.py`.
Expected: root 2538 tests pass, frontend 468 pass, both checks pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/GrandScepter.scala \
  src/main/scala/oathdigital/gameplay/operations/HallOfMinisters.scala \
  src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala \
  src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala \
  src/test/scala/oathdigital/gameplay/OperationResolutionSuite.scala \
  docs/ROADMAP.md
git commit -m "refactor(operations): the Grand Scepter and the Hall of Ministers declare their restrictions"
```
