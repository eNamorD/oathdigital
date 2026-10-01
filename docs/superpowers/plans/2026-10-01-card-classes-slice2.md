# Card Classes Slice 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every implemented denizen's card object sits in its power's file,
and its power reads the card's id, power id and printed cost from that object
instead of looking the card up in the catalog.

**Architecture:** Three scripted, reviewable moves. Task 1 moves the 126
denizen objects whose power is registered out of `catalog/holding/` and into
their power files, guarded by a test that a denizen leaves the holding files
exactly when its power is registered. Task 2 lets `PaidAction` take a
`PrintedPower`, so every card's paid action charges what its card prints.
Task 3 replaces `CatalogCards.denizen`, `WhenPlayedPower.cardOf` and the
direct `denizenWithPower` lookups with static references: a denizen power is
always registered, whatever denizens a catalog lists.

**Tech Stack:** Scala 3.9, sbt (`./sbtw`), munit, Python 3 (edit scripts).

**Spec:** `docs/superpowers/specs/2026-10-01-card-classes-design.md`
(slice 2). Read it first. Slice 1's plan,
`docs/superpowers/plans/2026-10-01-card-classes-slice1.md`, shows the card
types this plan builds on.

## Global Constraints

- Printed ids and power ids do not change (`"9"`, `"denizen.alchemist"`).
- Card objects keep their names (`AlchemistCard`) and their exact text; a
  move changes only the object's file and package.
- References run one way: behaviour to card. A card object never refers to
  its power.
- `gameplay/` never contains the string `rulesText` and never reads `.text`.
- `catalog/` imports only `model` (and the JDK).
- Production Scala files are at most 800 lines.
- Compiler flags are `-Werror` with unused imports, privates and locals.
  Remove every import the compiler flags.
- Relic, edifice, site and legacy cards stay where they are (slices 3 and 4).
  Only their paid actions change in Task 2, to read the holding object's
  printed power.
- Never touch the live database `var/oathdigital`.
- Gates at the end of every task: `./sbtw test frontend/test`,
  `python3 scripts/check-architecture.py`,
  `python3 scripts/check-markdown-links.py`.
- Commit trailer: the committing model's own line.
- Stage explicit paths only; never `git add -A`.

## Decisions made while planning

The spec fixes the target; these are the choices for getting there.

- **Implemented means registered.** A denizen moves when one of its powers
  is registered in `WalkerPowerCatalog.default` or
  `PhasePowerCatalog.default`, the test `CardCatalogSuite` already uses. That
  is 126 denizens. The 36 denizens that only have a stub in the reviewed
  catalog (`ActionPowers`, `MusterPowers`, `RestPowers`,
  `NegotiationPowers`, `CampaignPowers`, `RecoverPowers`) stay in holding;
  those stubs are the reviewed-catalog cleanup item's concern.
- **Each card goes into the one file that implements its power.** Every one
  of the 126 powers lives in exactly one non-reviewed file, and no file
  implements two of them, so the target is unambiguous. The object goes just
  below the file's imports, above the power's scaladoc, as in the spec's
  card shape.
- **`PaidAction` keeps a two-argument constructor.** Its primary constructor
  becomes `(id: PowerId, cost: Cost)` and an auxiliary one takes a
  `PrintedPower`. Cards pass the printed power. The two Wandering Flame
  banner actions, which no card prints, pass an id and a cost.
- **No more optional powers for denizens.** A power that read its card id
  from the catalog now reads it from its card object, so `forCatalog` cannot
  fail and returns the power itself, not an `Option`. A power that took the
  catalog only to find its card id becomes a `case object` and loses
  `forCatalog` (32 powers). The other 62 keep a `forCatalog(catalog)`
  because their behaviour still reads the catalog; the cleanup item **Shrink
  cached catalog fields** removes those later. Each keeps a `cardId` member
  so the body is unchanged.
- **Relic and edifice lookups stay.** `CatalogCards.relic` and
  `CatalogCards.edifice` remain until slice 3, and so does the
  card-is-absent omission for those powers.
- `OperationRestrictions.printedBy` keeps `denizenWithPower`. It maps a
  modifier chosen at run time to its card, which is a real index lookup, not
  a power finding its own card.

---

## File Structure

| Path | Change |
|------|--------|
| `src/main/scala/oathdigital/catalog/holding/Holding*Denizens.scala` | Lose the 126 implemented denizens; imports trimmed |
| 126 power files under `src/main/scala/oathdigital/gameplay/powers/` | Gain their card object |
| `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` | Imports the moved objects |
| `src/main/scala/oathdigital/gameplay/powers/action/PaidAction.scala` | Takes a `PrintedPower` |
| 58 card paid actions, 2 banner paid actions | Pass the printed power, or an id and cost |
| 94 denizen power files with `forCatalog` | Static card reference; 32 become `case object`s |
| `src/main/scala/oathdigital/gameplay/powers/CatalogCards.scala` | Loses `denizen` |
| `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPower.scala` | Loses `cardOf` |
| `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala` | Reads Silver Tongue and Insomnia directly |
| Aggregator scaladocs (list in Task 3) | Say which powers can still be omitted |
| `src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala` | Two new tests |
| `src/test/scala/oathdigital/gameplay/powers/action/PaidActionSuite.scala` (new) | Paid action reads its printed power |
| Test callers of `forCatalog(...).get` | Drop `.get`, or name the object |
| `docs/ROADMAP.md` | Slice 2 ticked |

The three edit scripts share one import helper. Save all four from this plan
into the session scratchpad before Task 1 (the code blocks below are complete
files). They take the worktree root as their only argument and assert on
anything unexpected rather than guessing.

### Shared import helper (`s2_imports.py`)

```python
"""Import editing shared by the card classes slice 2 scripts."""
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

### Task 1: Implemented denizens move beside their powers

**Files:**
- Modify: `src/main/scala/oathdigital/catalog/holding/Holding{Arcane,Beast,Discord,Hearth,Nomad,Order}Denizens.scala`
- Modify: the 126 power files the script names (all under `src/main/scala/oathdigital/gameplay/powers/`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Test: `src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala`

**Interfaces:**
- Consumes: slice 1's card objects (`object AlchemistCard extends Denizen(...)`
  with `val power` and `val powers`).
- Produces: each implemented denizen's object in its power's package, for
  example `oathdigital.gameplay.powers.action.StorytellerCard` beside
  `Storyteller`. Tasks 2 and 3 refer to `XCard.power` and `XCard.id` from the
  same file without an import.

- [ ] **Step 1: Write the failing guard test**

Append to `CardCatalogSuite` (inside the class, after the last test):

```scala
  test("a denizen leaves the holding files once one of its powers is " +
      "registered"):
    val registered = (WalkerPowerCatalog.default(catalog).powers.map(_.id) ++
      PhasePowerCatalog.default(catalog).powers.map(_.id)).toSet
    val misplaced = catalog.denizens.filter(card =>
      (card.getClass.getPackageName == "oathdigital.catalog.holding") ==
        card.powers.exists(power => registered(power.id)))
    assertEquals(misplaced.map(_.name), Vector.empty)
```

A denizen is misplaced when it is in holding although a power of it is
registered, or out of holding although none is.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: FAIL. The obtained vector names the 126 implemented denizens
("Wrestlers", "Chaos Cult", "Outriders", ...).

- [ ] **Step 3: Save the scripts and run the move**

Save `s2_imports.py` and `s2_move.py` (below) into the scratchpad, then run:

```bash
python3 <scratchpad>/s2_move.py <worktree root>
```

Expected output: `moved 126 denizens`.

The script, per implemented denizen: cuts its object from the holding file,
pastes it below the imports of the one file that names its power id (the
reviewed-catalog stub files do not count), and adds the catalog and model
imports the object needs to that file. Then it trims each holding file's
restriction-trait import to the traits still used there, and adds an import
per package of moved objects to `NewFoundations.scala`. It asserts that each
card has at most one target and that no file receives two cards.

```python
"""Card classes slice 2, Task 1: move implemented denizens beside their powers.

Usage: python3 s2_move.py <worktree root>
"""
import collections
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import s2_imports as imports

W = sys.argv[1].rstrip("/") + "/"
M = W + "src/main/scala/oathdigital/"
REVIEWED = {M + "gameplay/powers/" + f for f in (
    "ActionPowers.scala", "MusterPowers.scala", "RestPowers.scala",
    "NegotiationPowers.scala", "CampaignPowers.scala", "RecoverPowers.scala")}
TRAITS = ("AdviserOnly", "Locked", "SiteOnly")

holding = sorted(glob.glob(M + "catalog/holding/Holding*Denizens.scala"))
parts_of = {}
for f in holding:
    parts_of[f] = re.split(r"\n(?=object \w+Card extends Denizen\()",
                           open(f).read())

sources = {f: open(f).read()
           for f in glob.glob(M + "**/*.scala", recursive=True)
           if "/catalog/" not in f and f not in REVIEWED}

moves = []  # (card, block, destination)
for f, parts in parts_of.items():
    for part in parts[1:]:
        card = re.match(r"object (\w+Card) ", part).group(1)
        pids = re.findall(r'PowerId\("([^"]+)"\)', part)
        dests = sorted({d for d, s in sources.items()
                        for p in pids if f'"{p}"' in s})
        if not dests:
            continue
        assert len(dests) == 1, (card, dests)
        moves.append((card, part.rstrip("\n"), dests[0]))

by_dest = collections.Counter(d for _, _, d in moves)
assert all(n == 1 for n in by_dest.values()), by_dest

package_of = {}
for card, block, dest in moves:
    s = sources[dest]
    lines = s.split("\n")
    last = max(i for i, l in enumerate(lines) if l.startswith("import "))
    j = last + 1
    while j < len(lines) and lines[j].startswith("  "):
        j += 1
    lines[j:j] = [""] + block.split("\n")
    s = "\n".join(lines)
    used = {t for t in TRAITS if re.search(rf"\bwith {t}\b", block)}
    s = imports.add(s, "oathdigital.catalog", {"Denizen", "PrintedPower"} | used)
    s = imports.add(s, "oathdigital.model",
                    {"Cost", "DenizenId", "PowerId", "Suit"})
    open(dest, "w").write(s)
    package_of[card] = re.search(r"^package (\S+)", s, re.M).group(1)

moved = {card for card, _, _ in moves}
for f, parts in parts_of.items():
    kept = [parts[0]] + [p for p in parts[1:]
                         if re.match(r"object (\w+Card) ", p).group(1)
                         not in moved]
    assert len(kept) > 1, f
    s = "\n".join(kept)
    if not s.endswith("\n"):
        s += "\n"
    used = {t for t in TRAITS if re.search(rf"\bwith {t}\b", s)}
    s = imports.remove(s, "oathdigital.catalog", set(TRAITS) - used)
    open(f, "w").write(s)

registry = M + "gameplay/cards/NewFoundations.scala"
s = open(registry).read()
for pkg in sorted(set(package_of.values())):
    s = imports.add(s, pkg, {c for c, p in package_of.items() if p == pkg})
open(registry, "w").write(s)
print(f"moved {len(moves)} denizens")
```

- [ ] **Step 4: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: PASS, every test. If the guard names a card, a power file names a
power id that is not its own or a registered power names its id indirectly:
move that object by hand to the file that registers the power, and say so in
the hand-back.

- [ ] **Step 5: Review the moved code by eye**

Run: `git diff --stat` and read three diffs in full:
`gameplay/powers/economy/InitiationRite.scala` (restriction traits, merged
catalog import), `gameplay/powers/action/Storyteller.scala` (a paid action)
and `gameplay/powers/whenplayed/Garrison.scala`. Each new object is
byte-for-byte the block removed from the holding file.

- [ ] **Step 6: Run the gates**

Run: `./sbtw test frontend/test`, then
`python3 scripts/check-architecture.py`, then
`python3 scripts/check-markdown-links.py`.
Expected: all green. The architecture check must still pass: a card object
declares `text = "..."` but never reads `.text`.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/catalog/holding src/main/scala/oathdigital/gameplay src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala
git commit -m "refactor(cards): move implemented denizens beside their powers"
```

(Stage the directories only after `git status --short` shows nothing else
changed in them.)

---

### Task 2: Paid actions take their printed power

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/PaidAction.scala`
- Modify: the 60 files with `extends PaidAction(` (58 cards, 2 banners)
- Test: `src/test/scala/oathdigital/gameplay/powers/action/PaidActionSuite.scala` (new)

**Interfaces:**
- Consumes: Task 1's `StorytellerCard` and the other moved objects; the
  holding objects for relics (`ClayRattleCard`) and edifices
  (`MarbleFountainsCard.ruined`).
- Produces:
  `abstract class PaidAction(final val id: PowerId, override val cost: Cost)`
  with `def this(power: PrintedPower)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/scala/oathdigital/gameplay/powers/action/PaidActionSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.PrintedPower
import oathdigital.gameplay.powers.banner.WanderingFlameMove
import oathdigital.model._

/** A card's paid action reads its id and cost from the power its card
  * prints; a banner's, which no card prints, names both. */
class PaidActionSuite extends munit.FunSuite:
  private val printed = PrintedPower(PowerId("denizen.example"),
    persistent = false, cost = Cost(favor = 2, secretBurnt = 1),
    text = "**ACTION:** Do the example.")

  private object Example extends PaidAction(printed):
    def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
        : Either[OathViolation, Operation] = Right(Sequence(Vector.empty))

  test("a card's paid action takes its id and cost from the printed power"):
    assertEquals(Example.id, PowerId("denizen.example"))
    assertEquals(Example.cost, Cost(favor = 2, secretBurnt = 1))

  test("a card's paid action charges what its card prints"):
    assertEquals(Storyteller.id, StorytellerCard.power.id)
    assertEquals(Storyteller.cost, StorytellerCard.power.cost)

  test("a banner's paid action names its own id and cost"):
    assertEquals(WanderingFlameMove.id,
      PowerId("banner.darkest-secret.wandering-flame.move"))
    assertEquals(WanderingFlameMove.cost, Cost.free)
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.PaidActionSuite"`
Expected: FAIL to compile: `Found: PrintedPower, Required: String` at
`extends PaidAction(printed)`.

- [ ] **Step 3: Give `PaidAction` the printed power**

Replace `src/main/scala/oathdigital/gameplay/powers/action/PaidAction.scala`
with:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.PrintedPower
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.model._

/** An ACTION phase power gated only by its cost. The engine pays `cost`
  * onto the power's source card and reads "payable, including the
  * empty-card rule" as its usability, so a subclass writes only `build`.
  *
  * A card's action passes its printed power, which carries the id and the
  * printed cost. A banner's action, which no card prints, names both.
  */
abstract class PaidAction(final val id: PowerId, override val cost: Cost)
    extends PhasePower:
  def this(power: PrintedPower) = this(power.id, power.cost)

  final def timing: PowerTiming = PowerTiming.Act
  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = true
```

- [ ] **Step 4: Point every paid action at its printed power**

Save `s2_paid.py` (below) into the scratchpad, then run:

```bash
python3 <scratchpad>/s2_paid.py <worktree root>
```

Expected output: `rewrote 60 paid actions`.

The script indexes every card object (holding or moved) by power id, then
rewrites each `extends PaidAction("<id>", <cost>)` to
`extends PaidAction(<Card>.power)`, or `<Card>.intact.power` /
`<Card>.ruined.power` for an edifice face. Crystal Vial and Brass Horse pass
`X.id.value`; the script reads the id from their companion. A relic or
edifice card lives in a holding file, so the script imports it. The two
Wandering Flame actions become
`PaidAction(PowerId("banner.darkest-secret.wandering-flame.<move|place>"), Cost.free)`.

Every hand-copied cost already equals its printed cost (checked while
planning), so no action's price changes. `CrystalVial.price` and the relic
companions' `id` vals stay; slice 3 moves those cards.

```python
"""Card classes slice 2, Task 2: paid actions take their printed power.

Usage: python3 s2_paid.py <worktree root>
"""
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import s2_imports as imports

W = sys.argv[1].rstrip("/") + "/"
M = W + "src/main/scala/oathdigital/"
files = glob.glob(M + "**/*.scala", recursive=True)
texts = {f: open(f).read() for f in files}

# Every card object: power id -> (card, face path, package).
printed = {}
for f, s in texts.items():
    package = re.search(r"^package (\S+)", s, re.M).group(1)
    for part in re.split(r"\n(?=object \w+Card extends )", s)[1:]:
        card = re.match(r"object (\w+Card) ", part).group(1)
        face = None
        for line in part.split("\n"):
            m = re.match(r"  object (intact|ruined) ", line)
            if m:
                face = m.group(1)
            m = re.search(r'PowerId\("([^"]+)"\)', line)
            if m:
                path = f"{card}.{face}.power" if face else f"{card}.power"
                printed[m.group(1)] = (card, path, package, f)

PAID = re.compile(r'PaidAction\(\s*(?:"([^"]+)"|(\w+)\.id\.value),'
                  r'\s*(Cost\.free|Cost\([^)]*\)|[\w.]+)\)')
count = 0
for f, s in texts.items():
    if "extends PaidAction(" not in s and "extends PaidAction(\n" not in s:
        continue
    def swap(m):
        pid = m.group(1)
        if pid is None:
            pid = re.search(r'val id: PowerId = PowerId\("([^"]+)"\)',
                            s).group(1)
        if pid.startswith("banner."):
            return f'PaidAction(\n    PowerId("{pid}"), {m.group(3)})'
        card, path, package, home = printed[pid]
        swap.needs.append((package, card, home))
        return f"PaidAction({path})"
    swap.needs = []
    new = re.sub(r"extends " + PAID.pattern,
                 lambda m: "extends " + swap(m), s)
    assert new != s, f
    for package, card, home in swap.needs:
        if home != f:
            new = imports.add(new, package, {card})
    open(f, "w").write(new)
    count += 1
print(f"rewrote {count} paid actions")
```

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.PaidActionSuite"`
Expected: PASS (3 tests).

- [ ] **Step 6: Run the gates**

Run: `./sbtw test frontend/test`, then
`python3 scripts/check-architecture.py`, then
`python3 scripts/check-markdown-links.py`.
Expected: all green.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers src/test/scala/oathdigital/gameplay/powers/action/PaidActionSuite.scala
git commit -m "refactor(powers): paid actions charge their printed cost"
```

---

### Task 3: Denizen powers name their cards

**Files:**
- Modify: the 94 denizen power files with a `forCatalog` that looks up the
  card (the script lists none by name; it finds the three lookup forms)
- Modify: the companion `id` of all 126 moved denizens' powers
- Modify: `src/main/scala/oathdigital/gameplay/powers/CatalogCards.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala`
- Modify: callers of `forCatalog(...).toVector`, `forCatalog(...) ++` and
  `forCatalog(...).get` in main and test code
- Modify: the aggregator scaladocs listed in Step 6
- Modify: `src/test/scala/oathdigital/gameplay/powers/SelectedModifierSuite.scala`
- Modify: `docs/ROADMAP.md`
- Test: `src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala`

**Interfaces:**
- Consumes: Task 1's card objects in the power files.
- Produces:
  - A power that took only its card id is a `case object` with
    `val cardId: DenizenId = XCard.id` and `val id: PowerId = XCard.power.id`;
    it has no `forCatalog`. Callers write `X` and `Vector(X)`.
  - A power that also needs the catalog keeps
    `def forCatalog(catalog: ExecutableCatalog): X` (no `Option`), and has
    `val cardId: DenizenId = XCard.id` as its first member.
  - `CatalogCards` keeps only `relic` and `edifice`.
  - `WhenPlayedPower` has no companion object.

- [ ] **Step 1: Write the failing test**

Append to `CardCatalogSuite`, and add `ExecutableCatalog` to its
`oathdigital.catalog` import (`import oathdigital.catalog.{AdviserOnly,
ExecutableCatalog, Locked,` / `  PrintsPowers, SiteOnly}`):

```scala
  test("denizen powers are registered whichever denizens a catalog lists"):
    def denizenPowers(listed: ExecutableCatalog): Set[PowerId] =
      (WalkerPowerCatalog.default(listed).powers.map(_.id) ++
        PhasePowerCatalog.default(listed).powers.map(_.id))
        .filter(_.value.startsWith("denizen.")).toSet
    assertEquals(denizenPowers(catalog.copy(denizens = Vector.empty)),
      denizenPowers(catalog))
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: FAIL. With no denizens listed, every power built through
`CatalogCards.denizen`, `WhenPlayedPower.cardOf` or `denizenWithPower` is
omitted, so the obtained set lacks about 90 ids; only the case-object paid
actions remain.

- [ ] **Step 3: Make the references static**

Save `s2_static.py` (below) into the scratchpad, then run:

```bash
python3 <scratchpad>/s2_static.py <worktree root>
```

Expected output: `126 cards; 32 powers became objects, 62 keep a catalog`.

What the script does, in order:

1. For each moved denizen, replaces `PowerId("<its power id>")` in its file
   with `<Card>.power.id`, except inside the card object itself.
2. Finds `def forCatalog(catalog: ExecutableCatalog): Option[X] =` built on
   `CatalogCards.denizen(catalog, id)`, `WhenPlayedPower.cardOf(catalog, id)`
   or `catalog.denizenWithPower(id)`, and drops a one-line comment above it
   that mentions `None`.
   - When the power is built with the catalog, `forCatalog` becomes
     `def forCatalog(catalog: ExecutableCatalog): X = new X(catalog)`. A
     `cardId: DenizenId` constructor parameter is replaced by the member
     `val cardId: DenizenId = <Card>.id`; a header longer than 80 columns
     breaks before `extends`.
   - Otherwise the class becomes a `case object`: `cardId` becomes a member,
     `def id: PowerId = X.id` and `import X._` go, and the companion's
     members (now `val id: PowerId = <Card>.power.id` first) move into the
     object, below `cardId`.
3. Rewrites `AdviserLimit.of` to
   `Vector(SilverTongue.forCatalog(catalog).limitFor(ready, player),`
   `Insomnia.limitFor(ready, player)).flatten.minOption.getOrElse(Default)`.
4. Rewrites callers in main and test code: `X.forCatalog(c).toVector` and a
   `forCatalog` operand of `++` become `Vector(...)`; `.get` goes; for a new
   object, `X.forCatalog(c).get` becomes `X` and a type argument `[X]`
   becomes `[X.type]`.
5. Removes `SelectedModifierSuite`'s `CatalogCards.denizen` assertion for
   Tents, and turns its "nothing" case into
   `CatalogCards.relic(catalog, PowerId("relic.nobody"))`.
6. Deletes the `WhenPlayedPower` companion (`cardOf`) and
   `CatalogCards.denizen`.
7. In every file it touched, removes `CatalogCards`, `ExecutableCatalog`,
   `DenizenId` and `FirstGameSetupFixture.catalog` imports nothing uses
   any more.

```python
"""Card classes slice 2, Task 3: denizen powers name their cards statically.

Usage: python3 s2_static.py <worktree root>
"""
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import s2_imports as imports

W = sys.argv[1].rstrip("/") + "/"
M = W + "src/main/scala/oathdigital/"
T = W + "src/test/scala/oathdigital/"
LOOKUP = (r"(?:CatalogCards\.denizen\(catalog, id\)"
          r"|WhenPlayedPower\.cardOf\(catalog, id\)"
          r"|catalog\.denizenWithPower\(id\))")
FOR = re.compile(r"(?P<doc>(?:  //[^\n]*\n|  /\*\*[^\n]*\*/\n)?)"
                 r"  def forCatalog\(catalog: ExecutableCatalog\): "
                 r"Option\[(?P<cls>\w+)\] =\s*" + LOOKUP +
                 r"\s*\.map\((?P<fn>[^\n]*)\)\n")
PRUNE = (("oathdigital.gameplay.powers", "CatalogCards"),
         ("oathdigital.catalog", "ExecutableCatalog"),
         ("oathdigital.model", "DenizenId"),
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


def to_object(s, cls, card):
    lines = s.split("\n")
    start = next(i for i, l in enumerate(lines)
                 if l.startswith(f"final case class {cls} private (cardId: "
                                 "DenizenId)"))
    lines[start] = lines[start].replace(
        f"final case class {cls} private (cardId: DenizenId)",
        f"case object {cls}")
    end = header_end(lines, start)
    lines.insert(end + 1, f"  val cardId: DenizenId = {card}.id")
    lines = [l for l in lines
             if l not in (f"  def id: PowerId = {cls}.id", f"  import {cls}._")]
    comp = lines.index(f"object {cls}:")
    comp_end = body_end(lines, comp)
    members = lines[comp + 1:comp_end]
    del lines[comp:comp_end]
    while comp < len(lines) and lines[comp] == "" and \
            (comp == len(lines) - 1 or lines[comp - 1] == ""):
        del lines[comp]
    start = next(i for i, l in enumerate(lines)
                 if l.startswith(f"case object {cls}"))
    end = header_end(lines, start)
    if end == start + 1 and len(lines[start]) + len(lines[end]) - 3 <= 80:
        lines[start:end + 1] = [lines[start] + " " + lines[end].strip()]
        end = start
    while members and members[-1] == "":
        members.pop()
    if members:
        lines[end + 2:end + 2] = members + [""]
    return re.sub(r"\n{3,}", "\n\n", "\n".join(lines))


def code_without_imports(s):
    return re.sub(r"^import [^\n]*\n(?:  [^\n]*\n)*", "", s, flags=re.M)


def prune(s):
    for pkg, name in PRUNE:
        if name in imports.names_of(s, pkg) and \
                not re.search(rf"\b{name}\b", code_without_imports(s)):
            s = imports.remove(s, pkg, {name})
    return s


cards = []  # (card, power id, file)
for f in glob.glob(M + "gameplay/**/*.scala", recursive=True):
    s = open(f).read()
    for m in re.finditer(r'^object (\w+Card) extends Denizen\([^\n]*\n'
                         r'  val power = PrintedPower\(PowerId\("([^"]+)"\)',
                         s, re.M):
        cards.append((m.group(1), m.group(2), f))

objects, classes = set(), set()
for card, pid, f in cards:
    s = open(f).read()
    s = s.replace(f'PowerId("{pid}")', f"{card}.power.id")
    s = s.replace(f"PrintedPower({card}.power.id",
                  f'PrintedPower(PowerId("{pid}")')
    m = FOR.search(s)
    if m:
        cls, fn = m.group("cls"), m.group("fn")
        doc = "" if "None" in m.group("doc") else m.group("doc")
        takes_catalog = re.search(r"\bcatalog\b", fn) is not None
        had_card = not fn.startswith("_ =>")
        if takes_catalog:
            s = s[:m.start()] + doc + (
                f"  def forCatalog(catalog: ExecutableCatalog): {cls} =\n"
                f"    new {cls}(catalog)\n") + s[m.end():]
            if had_card:
                new = re.sub(rf"final case class {cls} private \(cardId: "
                             r"DenizenId,\s*catalog: ExecutableCatalog\)",
                             f"final case class {cls} private "
                             "(catalog: ExecutableCatalog)", s)
                assert new != s, (f, cls)
                lines = new.split("\n")
                start = next(i for i, l in enumerate(lines)
                             if l.startswith(f"final case class {cls} "))
                if len(lines[start]) > 80 and " extends " in lines[start]:
                    head, tail = lines[start].split(" extends ", 1)
                    lines[start:start + 1] = [head, "    extends " + tail]
                lines.insert(header_end(lines, start) + 1,
                             f"  val cardId: DenizenId = {card}.id")
                s = "\n".join(lines)
            classes.add(cls)
        else:
            s = s[:m.start()] + s[m.end():]
            s = to_object(s, cls, card)
            objects.add(cls)
    open(f, "w").write(prune(s))

p = M + "gameplay/powers/AdviserLimit.scala"
s = open(p).read()
old = """    (SilverTongue.forCatalog(catalog).flatMap(_.limitFor(ready, player))
      .toVector ++ Insomnia.forCatalog(catalog)
      .flatMap(_.limitFor(ready, player)).toVector)
      .minOption.getOrElse(Default)"""
assert old in s, p
s = s.replace(old, """    Vector(SilverTongue.forCatalog(catalog).limitFor(ready, player),
      Insomnia.limitFor(ready, player)).flatten.minOption.getOrElse(Default)""")
open(p, "w").write(s)

callers = glob.glob(M + "**/*.scala", recursive=True) + \
    glob.glob(T + "**/*.scala", recursive=True)
for f in callers:
    s = open(f).read()
    new = s
    for cls in objects:
        new = re.sub(rf"\b{cls}\.forCatalog\(\w+\)\.(?:toVector)",
                     f"Vector({cls})", new)
        new = re.sub(rf"\b{cls}(\s+)\.forCatalog\(\w+\)\.get(?=\.)",
                     cls + r"\1", new)
        new = re.sub(rf"\b{cls}\s*\.forCatalog\(\w+\)\s*\.get\b", cls, new)
        new = re.sub(rf"\[{cls}\]", f"[{cls}.type]", new)
    for cls in objects:
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
old = """    assertEquals(CatalogCards.denizen(catalog, PowerId("denizen.tents")),
      Some(card))
"""
assert old in s, p
s = s.replace(old, "").replace(
    'CatalogCards.denizen(catalog, PowerId("denizen.nobody"))',
    'CatalogCards.relic(catalog, PowerId("relic.nobody"))')
open(p, "w").write(s)

p = M + "gameplay/powers/whenplayed/WhenPlayedPower.scala"
s = open(p).read()
s = re.sub(r"\n\nobject WhenPlayedPower:\n(?:  [^\n]*\n|\n)*$", "\n", s)
open(p, "w").write(prune(s))

p = M + "gameplay/powers/CatalogCards.scala"
s = open(p).read()
s = re.sub(r"  def denizen\(catalog[^\n]*\n[^\n]*\n\n", "", s)
open(p, "w").write(prune(s))

print(f"{len(cards)} cards; {len(objects)} powers became objects, "
      f"{len(classes)} keep a catalog")
```

- [ ] **Step 4: Compile and read the result**

Run: `./sbtw Test/compile`
Expected: success with no warnings. While planning, the scripts compiled
cleanly on a copy of `main` at `04d2f738`. If `main` has moved and a new
caller fails (for example `forCatalog(catalog).flatMap`), apply the same
rule by hand: no `Option`, `Vector(...)` where a vector is wanted.

Read three results in full: `economy/InitiationRite.scala` (a new case
object), `whenplayed/Riots.scala` (keeps a catalog) and
`recover/CatacombsContribution.scala` (a direct `denizenWithPower` lookup
before).

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: PASS, every test.

- [ ] **Step 6: Correct the omission docs**

These scaladocs say a power whose card is absent from the catalog is
omitted. That is now false for denizen powers. Rewrite each sentence to say
which powers can still be omitted: relic, edifice and site powers whose card
is absent; a denizen power names its card and is always present. Where an
aggregator holds only denizen powers, drop the sentence instead. Check what
each aggregator lists before choosing.

- `src/main/scala/oathdigital/gameplay/powers/CatalogCards.scala` (now:
  "Which relic or edifice prints a power, ...")
- `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- `src/main/scala/oathdigital/gameplay/powers/whenplayed/WhenPlayedPowers.scala`
- `src/main/scala/oathdigital/gameplay/powers/targeting/TargetProtections.scala`
- `src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala`
- `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRules.scala`
- `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlans.scala`
- `src/main/scala/oathdigital/gameplay/powers/campaign/SimplePlans.scala`
- `src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala`

Then run
`grep -rn -i "card is absent\|catalog without\|is omitted\|left out" src/main/scala/oathdigital/gameplay`
and fix any other sentence that still claims a denizen power can be omitted.

- [ ] **Step 7: Tick the ROADMAP**

In `docs/ROADMAP.md`, under `### Phase - Card classes`, replace

```markdown
- [ ] **Slice 2 - denizens beside their powers.**
```

with

```markdown
- [x] **Slice 2 - denizens beside their powers.** The 126 implemented
  denizens sit in their power files; powers read their card, power id and
  printed cost from the card object, and `CatalogCards.denizen` is gone.
```

Change nothing else in the ROADMAP.

- [ ] **Step 8: Run the gates**

Run: `./sbtw test frontend/test`, then
`python3 scripts/check-architecture.py`, then
`python3 scripts/check-markdown-links.py`.
Expected: all green. While planning, the root suite passed with all three
tasks applied: 2539 tests (2534 before, plus 5 new).

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay src/test/scala/oathdigital docs/ROADMAP.md
git commit -m "refactor(powers): denizen powers name their cards"
```

(Check `git status --short` first; stage nothing outside these paths.)
