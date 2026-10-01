# Card Classes Slice 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all 393 printed components out of the runtime JSON and into
Scala card objects, so the server, tests and packaging no longer read
`docs/catalog/new-foundations-component-catalog.json`.

**Architecture:** New card types (`Denizen`, `Relic`, `Edifice`, `Legacy`,
`Site`, `PrintedPower`, and the `Locked` / `SiteOnly` / `AdviserOnly` traits)
live in `oathdigital.catalog`. A throwaway script generates one object per
card into holding files under `catalog/holding/`, and the registry
`gameplay/cards/NewFoundations` lists them and builds the `ExecutableCatalog`
value. A temporary suite proves the generated catalog equals the JSON before
the switch. Then the loader, the path option, the packaging entries, the
validator, `CatalogRef` and the audit fingerprints go.

**Tech Stack:** Scala 3.9, sbt (`./sbtw`), munit, Python 3 (generator and
gate scripts).

**Spec:** `docs/superpowers/specs/2026-10-01-card-classes-design.md`
(slice 1). Read it first.

## Global Constraints

- Scala is the source of truth for card facts. The JSON ends as reference
  only, in `docs/catalog/reference/`.
- Printed ids and power ids do not change (`"9"`, `"R01"`, `"E01"`, `"L01"`,
  `"site:deep-woods"`, `"denizen.alchemist"`).
- Each `ExecutableCatalog` vector is sorted by id as a string
  (`sortBy(_.id.value)`), the order the JSON loader produced. Setup and tests
  depend on it (for example `catalog.sites.take(8)`).
- Card objects are named after the card with a `Card` suffix
  (`AlchemistCard`); sites take a `Site` suffix (`DeepWoodsSite`); an edifice
  is named after its intact face (`HallOfDebateCard`).
- Site handlers stay `Vector[String]` in this slice.
- `gameplay/` never contains the string `rulesText` and never reads `.text`.
- `catalog/` imports only `model` (and the JDK).
- Production Scala files are at most 800 lines.
- Compiler flags are `-Werror` with unused imports, privates and locals. Remove
  every import the compiler flags.
- Never touch the live database `var/oathdigital`.
- Gates at the end of every task: `./sbtw test frontend/test`,
  `python3 scripts/check-architecture.py`,
  `python3 scripts/check-markdown-links.py`.
- Commit trailer: the committing model's own line.
- Stage explicit paths only; never `git add -A`.

---

## File Structure

| Path | Responsibility |
|------|----------------|
| `src/main/scala/oathdigital/catalog/Cards.scala` (new) | Card types, printed power, restriction traits |
| `src/main/scala/oathdigital/catalog/CatalogModel.scala` | `ExecutableCatalog` and its indexes, over the new types |
| `src/main/scala/oathdigital/catalog/holding/*.scala` (generated) | One object per card not yet beside its power |
| `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (generated, then edited) | Registry: every card, and the production catalog |
| `src/test/scala/oathdigital/testkit/TestCards.scala` (new) | Variant cards for suites that graft powers or names |
| `src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala` (new) | The validator's checks, on the Scala catalog |
| `src/main/scala/oathdigital/catalog/CatalogLoader.scala` | Deleted |
| `scripts/validate-component-catalog.py`, the schema | Deleted |

Task order: (1) retire the audit fingerprints, (2) add types and generated
data beside the JSON, (3) switch the runtime to the Scala catalog, (4) retire
the JSON pipeline, (5) remove `CatalogRef`, (6) docs and ROADMAP.

---

### Task 1: Retire the audit fingerprints

Two hashes pin the catalog: `ReviewedPowerCatalog.AuditedCatalogFingerprint`
(handler ids, checked by `requireAudited` in nine places) and
`BeginRestProcedure.ExpectedHandlerInventory` (structural, checked when a
Rest begins). Once the catalog is code they guard nothing, and the switch in
Task 3 would trip them. Retire both first.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/ReviewedPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/PowerRuntime.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/ForgeRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/Search.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/challenge/PlaceBannerResourceProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/negotiation/NegotiationProcedure.scala`
- Modify: `src/main/scala/oathdigital/application/GeneratedFirstGamePlanFactory.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/rest/BeginRestProcedure.scala`
- Modify: `src/main/scala/oathdigital/catalog/CatalogHandlerInventory.scala`
- Modify: `src/main/scala/oathdigital/model/GameViolation.scala`
- Test: `src/test/scala/oathdigital/catalog/CatalogHandlerInventorySuite.scala`,
  `src/test/scala/oathdigital/gameplay/RestSuite.scala`,
  `src/test/scala/oathdigital/gameplay/ForgeProcedureSuite.scala`

**Interfaces:**
- Produces: `ReviewedPowerCatalog.resolver(catalog)` and `.registry(catalog)`
  keep their `Either[OathViolation, …]` signatures but always succeed.
  `CatalogHandlerInventory.handlerIds` stays; `entries`, `fingerprint` and
  `structuralFingerprint` are gone. `OathViolation.UnsupportedRuleCatalog`
  stays (the resolver's unclassified-handler case uses it);
  `OathViolation.UnsupportedRoundEndCatalogInventory` is gone.

- [ ] **Step 1: Delete the tripwire tests**

In `RestSuite.scala`, delete the whole test
`"changed inventory in every catalog family fails before runtime discovery"`
(it starts near line 289 and ends at `assert(changed.forall(rejects))`).

In `ForgeProcedureSuite.scala`, delete the whole test
`"P1: build refuses a site whose denizen carries a power outside the " +
"audited vocabulary"` (near line 262, ends at the
`.isInstanceOf[OathViolation.UnsupportedRuleCatalog])` line).

Replace `CatalogHandlerInventorySuite.scala` with:

```scala
package oathdigital.catalog

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog

class CatalogHandlerInventorySuite extends munit.FunSuite:
  test("the handler inventory lists every printed power and site handler once"):
    val expected = (catalog.denizens.flatMap(_.handlers) ++
      catalog.relics.flatMap(_.handlers) ++ catalog.legacies.flatMap(_.handlers) ++
      catalog.sites.flatMap(_.handlers) ++ catalog.edifices.flatMap(e =>
        e.intact.handlers ++ e.ruined.handlers)).distinct.sorted
    assertEquals(CatalogHandlerInventory.handlerIds(catalog), expected)
```

- [ ] **Step 2: Remove the fingerprints**

`CatalogHandlerInventory.scala` becomes:

```scala
package oathdigital.catalog

/** Factual catalog power inventory. It records declared IDs; it does not
  * decide whether or when a power is active.
  */
object CatalogHandlerInventory:
  def handlerIds(catalog: ExecutableCatalog): Vector[String] =
    (catalog.denizens.flatMap(_.powers.map(_.id.value)) ++
      catalog.relics.flatMap(_.powers.map(_.id.value)) ++
      catalog.legacies.flatMap(_.powers.map(_.id.value)) ++
      catalog.sites.flatMap(_.handlers) ++
      catalog.edifices.flatMap(e => e.intact.powers.map(_.id.value) ++
        e.ruined.powers.map(_.id.value)))
      .distinct.sorted
```

In `ReviewedPowerCatalog.scala`: delete `AuditedCatalogFingerprint` and
`requireAudited`, and make `resolver` and `registry` build without the check:

```scala
  def resolver(catalog: ExecutableCatalog): Either[OathViolation, PowerResolver] =
    registry(catalog).map(new PowerResolver(_))

  def registry(catalog: ExecutableCatalog): Either[OathViolation, PowerRegistry] =
    val audited = CatalogHandlerInventory.handlerIds(catalog)
      .map(PowerId.apply).toSet ++ syntheticIds
    Right(PowerRegistry.withAudited(audited, effective(catalog)*))
```

Check that `new PowerResolver(registry)` is how `PowerResolver` is built
today (`PowerResolver.scala`). The old code passed
`PowerRegistry.withAudited(...)` straight to `new PowerResolver(...)`, so
this is the same value.

In `PowerRuntime.scala`: delete `requireAudited`. In `resolve`, the
`UnknownAbility` case becomes:

```scala
      case PowerResolverError.UnknownAbility(source, id) =>
        OathViolation.UnsupportedRuleCatalog("a classified handler",
          s"unclassified-handler:${source.stableKey}:${id.value}")
```

Remove each `requireAudited` call:

- `ForgeRules.validate`: replace
  `PowerRuntime.requireAudited(catalog).flatMap { _ =>` and its closing `}`
  so the `for … yield empty -> cost` is the method body. Reword the doc
  comment to: "The complete pre-release component corpus was audited against
  CR p.25 / NF p.14 and contains no handler that changes the base Forge
  procedure."
- `Search.scala`: delete `validateSupportedState` entirely, and in
  `SearchProcedure.scala` delete the line
  `_ <- SearchRules.validateSupportedState(catalog, ready)`.
- `EconomyTree`, `CampaignProcedure`, `ChallengeProcedure`,
  `PlaceBannerResourceProcedure`, `NegotiationProcedure`: delete the line
  `_ <- PowerRuntime.requireAudited(catalog)`.
- `GeneratedFirstGamePlanFactory.build`: delete the two lines
  `_ <- ReviewedPowerCatalog.requireAudited(catalog)` and
  `.left.map(violation => BootstrapPlanFailure(violation.toString))`.

In `BeginRestProcedure.scala`: delete `ExpectedHandlerInventory`, the
`val actual = …structuralFingerprint…` line and the
`else if actual != ExpectedHandlerInventory then … UnsupportedRoundEndCatalogInventory …`
branch, and the `CatalogHandlerInventory` import. Reword the doc comment's
last sentence to: "The exile-only check stays here because Rest's cleanup and
Supply bands are reviewed only for that game."

In `GameViolation.scala`: delete `UnsupportedRoundEndCatalogInventory`.

- [ ] **Step 3: Compile and clear unused imports**

Run: `./sbtw Test/compile`
Expected: errors only for imports that are now unused (for example
`PowerRuntime` in the procedures, `ReviewedPowerCatalog` in
`GeneratedFirstGamePlanFactory`, `OathViolation._` in `Search.scala`,
`CatalogPower` / `UnsupportedRoundEndCatalogInventory` /
`UnsupportedRuleCatalog` in `RestSuite`). Remove each flagged import and
re-run until it compiles.

- [ ] **Step 4: Run the gates**

Run: `./sbtw test frontend/test && python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: all pass. `grep -rn "requireAudited\|AuditedCatalogFingerprint\|ExpectedHandlerInventory\|structuralFingerprint" src` prints nothing.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/ReviewedPowerCatalog.scala \
  src/main/scala/oathdigital/gameplay/PowerRuntime.scala \
  src/main/scala/oathdigital/gameplay/actions/ForgeRules.scala \
  src/main/scala/oathdigital/gameplay/actions/Search.scala \
  src/main/scala/oathdigital/gameplay/actions/search/SearchProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala \
  src/main/scala/oathdigital/gameplay/actions/campaign/CampaignProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/challenge/ChallengeProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/challenge/PlaceBannerResourceProcedure.scala \
  src/main/scala/oathdigital/gameplay/actions/negotiation/NegotiationProcedure.scala \
  src/main/scala/oathdigital/application/GeneratedFirstGamePlanFactory.scala \
  src/main/scala/oathdigital/gameplay/phases/rest/BeginRestProcedure.scala \
  src/main/scala/oathdigital/catalog/CatalogHandlerInventory.scala \
  src/main/scala/oathdigital/model/GameViolation.scala \
  src/test/scala/oathdigital/catalog/CatalogHandlerInventorySuite.scala \
  src/test/scala/oathdigital/gameplay/RestSuite.scala \
  src/test/scala/oathdigital/gameplay/ForgeProcedureSuite.scala
git commit -m "refactor(catalog): retire the audit fingerprints"
```

Add any other file Step 3 touched to the `git add`.

---

### Task 2: Card types and the generated Scala catalog

Add the new types beside the old definitions, generate every card into Scala,
and prove the result equals the JSON. Nothing switches yet.

**Files:**
- Create: `src/main/scala/oathdigital/catalog/Cards.scala`
- Create (generated): `src/main/scala/oathdigital/catalog/holding/*.scala`
- Create (generated): `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Create: `src/test/scala/oathdigital/gameplay/cards/NewFoundationsEquivalenceSuite.scala`
- Scratch (not committed): `<scratchpad>/generate_cards.py`

**Interfaces:**
- Produces, in package `oathdigital.catalog`:
  - `final case class PrintedPower(id: PowerId, persistent: Boolean, cost: Cost, text: String)` with `def rulesText: String`.
  - `trait Locked`, `trait SiteOnly`, `trait AdviserOnly`.
  - `trait PrintsPowers { def powers: Vector[PrintedPower]; final def handlers: Vector[String]; final def rulesText: String }`.
  - `abstract class Denizen(val id: DenizenId, val name: String, val suit: Suit) extends PrintsPowers`.
  - `abstract class Relic(val id: RelicId, val name: String, val value: Int, val defense: Int) extends PrintsPowers`.
  - `abstract class EdificeFace(val name: String) extends PrintsPowers`.
  - `abstract class Edifice(val id: EdificeId, val suit: Suit) { def intact: EdificeFace; def ruined: EdificeFace }`.
  - `abstract class Legacy(val id: LegacyId, val name: String) extends PrintsPowers`.
  - `abstract class Site(val id: SiteId, val name: String, val defense: Int, val capacity: Int, val relicSlots: Int, val recoverDifficulty: Option[Int], val startingResources: Tokens, val forgeRequirements: Option[Tokens], val homeland: Option[Suit], val handlers: Vector[String])`.
- Produces, in package `oathdigital.catalog.holding`: one object per card,
  for example `AlchemistCard`, `TheGrandScepterCard`, `HallOfDebateCard`
  (with nested `intact` / `ruined`), `IronHandCard`, `DeepWoodsSite`.
- Produces `oathdigital.gameplay.cards.NewFoundations` with
  `val denizens: Vector[Denizen]`, `relics: Vector[Relic]`,
  `edifices: Vector[Edifice]`, `legacies: Vector[Legacy]`,
  `sites: Vector[Site]`, each sorted by id string.

- [ ] **Step 1: Write the equivalence suite (fails: types missing)**

Create `src/test/scala/oathdigital/gameplay/cards/NewFoundationsEquivalenceSuite.scala`:

```scala
package oathdigital.gameplay.cards

import oathdigital.catalog._
import oathdigital.catalog.holding.TheGrandScepterCard
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.Suit

/** Temporary: the generated Scala catalog matches the JSON it was generated
  * from, field by field. Deleted with the loader once the server runs on the
  * Scala catalog (card classes slice 1, Task 3).
  */
class NewFoundationsEquivalenceSuite extends munit.FunSuite:
  private def restriction(card: AnyRef): CardRestrictions = card match
    case _: (Locked & AdviserOnly) => CardRestrictions.LockedAdviserOnly
    case _: Locked => CardRestrictions.Locked
    case _: SiteOnly => CardRestrictions.SiteOnly
    case _: AdviserOnly => CardRestrictions.AdviserOnly
    case _ => CardRestrictions.Unrestricted

  private def fromJson(powers: Vector[CatalogPower]) =
    powers.map(p => (p.id, p.persistent, p.rulesText))
  private def fromScala(powers: Vector[PrintedPower]) =
    powers.map(p => (p.id, p.persistent, p.rulesText))

  test("denizens match the JSON"):
    assertEquals(
      NewFoundations.denizens.sortBy(_.id.value).map(d => (d.id.value, d.name,
        d.suit, restriction(d), fromScala(d.powers))),
      catalog.denizens.map(d => (d.id.value, d.name, d.suit, d.restrictions,
        fromJson(d.powers))))

  test("relics match the JSON, and only The Grand Scepter is a scepter"):
    assertEquals(
      NewFoundations.relics.sortBy(_.id.value).map(r => (r.id.value, r.name,
        r.id == TheGrandScepterCard.id, r.value, r.defense,
        fromScala(r.powers))),
      catalog.relics.map(r => (r.id.value, r.name,
        r.role == RelicRole.GrandScepter, r.value, r.defense,
        fromJson(r.powers))))

  test("edifices match the JSON, face by face"):
    assertEquals(
      NewFoundations.edifices.sortBy(_.id.value).map(e => (e.id.value, e.suit,
        e.intact.name, restriction(e.intact), fromScala(e.intact.powers),
        e.ruined.name, restriction(e.ruined), fromScala(e.ruined.powers))),
      catalog.edifices.map(e => (e.id.value, e.suit,
        e.intact.name, e.intact.restrictions, fromJson(e.intact.powers),
        e.ruined.name, e.ruined.restrictions, fromJson(e.ruined.powers))))

  test("legacies match the JSON"):
    assertEquals(
      NewFoundations.legacies.sortBy(_.id.value).map(l =>
        (l.id.value, l.name, fromScala(l.powers))),
      catalog.legacies.map(l => (l.id.value, l.name, fromJson(l.powers))))

  test("sites match the JSON, with the homeland read from its handler"):
    def homeland(handlers: Vector[String]): Option[Suit] =
      handlers.collectFirst { case h if h.contains(".homeland-") =>
        Suit.fromKey(h.substring(h.indexOf(".homeland-") + 10)).get }
    assertEquals(
      NewFoundations.sites.sortBy(_.id.value).map(s => (s.id, s.name,
        s.defense, s.capacity, s.relicSlots, s.recoverDifficulty,
        s.startingResources, s.forgeRequirements, s.homeland, s.handlers)),
      catalog.sites.map(s => (s.id, s.name, s.defense, s.capacity,
        s.relicSlots, s.recoverDifficulty, s.startingResources,
        s.forgeRequirements, homeland(s.handlers), s.handlers)))
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.NewFoundationsEquivalenceSuite"`
Expected: compile errors, `Not found: type Locked`, `NewFoundations`, and so on.

- [ ] **Step 3: Write the card types**

Create `src/main/scala/oathdigital/catalog/Cards.scala`:

```scala
package oathdigital.catalog

import oathdigital.model.{Cost, DenizenId, EdificeId, LegacyId, PowerId,
  RelicId, SiteId, Suit, Tokens}

/** A power as its card prints it. `cost` is the run of favor and secret
  * symbols the printed text opens with; `text` is the rest, timing keyword
  * included. Gameplay never reads `text`: behaviour lives in the power.
  */
final case class PrintedPower(id: PowerId, persistent: Boolean, cost: Cost,
    text: String):
  require(text.trim.nonEmpty, "printed power text must not be blank")

  /** The printed line: the cost symbols in printed order, then `text`. */
  def rulesText: String =
    val symbols = Vector("favor" -> cost.favor, "secret" -> cost.secret,
      "favor-burnt" -> cost.favorBurnt, "secret-burnt" -> cost.secretBurnt)
      .flatMap((symbol, count) => Vector.fill(count)(s"[$symbol]"))
    if symbols.isEmpty then text else symbols.mkString("", " ", " ") + text

/** Printed restrictions a card mixes in. A locked adviser-only denizen mixes
  * in both `Locked` and `AdviserOnly`; an edifice's intact face is `Locked`.
  */
trait Locked
trait SiteOnly
trait AdviserOnly

/** A card, or an edifice face, that prints powers. */
trait PrintsPowers:
  def powers: Vector[PrintedPower]
  final def handlers: Vector[String] = powers.map(_.id.value)
  final def rulesText: String = powers.map(_.rulesText).mkString("\n\n")

abstract class Denizen(val id: DenizenId, val name: String, val suit: Suit)
    extends PrintsPowers

abstract class Relic(val id: RelicId, val name: String, val value: Int,
    val defense: Int) extends PrintsPowers

abstract class EdificeFace(val name: String) extends PrintsPowers

/** An edifice card: one suit, two named faces. A card object implements
  * `intact` and `ruined` with nested face objects. */
abstract class Edifice(val id: EdificeId, val suit: Suit):
  def intact: EdificeFace
  def ruined: EdificeFace

abstract class Legacy(val id: LegacyId, val name: String) extends PrintsPowers

/** A site. It prints no rules text; `handlers` are its power ids. */
abstract class Site(
    val id: SiteId,
    val name: String,
    val defense: Int,
    val capacity: Int,
    val relicSlots: Int,
    val recoverDifficulty: Option[Int],
    val startingResources: Tokens,
    val forgeRequirements: Option[Tokens],
    val homeland: Option[Suit],
    val handlers: Vector[String]
)
```

Scala 3 lets a nested `object intact` implement `def intact` (checked with a
3.9.0 probe).

- [ ] **Step 4: Write the generator**

Write this to the scratchpad as `generate_cards.py`. It is a one-shot tool
and is not committed.

```python
#!/usr/bin/env python3
"""One-shot: generate catalog/holding/*.scala and gameplay/cards/NewFoundations.scala
from the runtime JSON (card classes slice 1, Task 2). Not committed."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1])
CATALOG = json.loads((ROOT / "docs/catalog/new-foundations-component-catalog.json")
                     .read_text(encoding="utf-8"))
HOLDING = ROOT / "src/main/scala/oathdigital/catalog/holding"
REGISTRY = ROOT / "src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala"
LIMIT = 780
COST = [("favor", "favor"), ("secret", "secret"),
        ("favor-burnt", "favorBurnt"), ("secret-burnt", "secretBurnt")]
SUITS = {"discord": "Discord", "arcane": "Arcane", "order": "Order",
         "hearth": "Hearth", "beast": "Beast", "nomad": "Nomad"}
DENIZEN_TRAITS = {(): [], ("site-only",): ["SiteOnly"],
                  ("adviser-only",): ["AdviserOnly"],
                  ("adviser-only", "locked"): ["Locked", "AdviserOnly"]}
seen_names = set()


def pascal(name, suffix):
    words = re.split(r"[^A-Za-z0-9]+", name.replace("'", "").replace("\u2019", ""))
    out = "".join(w[0].upper() + w[1:] for w in words if w) + suffix
    assert out[0].isalpha() and out not in seen_names, name
    seen_names.add(out)
    return out


def lit(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'


def wrapped(text, indent):
    pieces, rest = [], text
    while len(rest) > 60:
        cut = rest.rfind(" ", 0, 60)
        cut = 60 if cut <= 0 else cut + 1
        pieces.append(rest[:cut])
        rest = rest[cut:]
    pieces.append(rest)
    return (" +\n" + " " * indent).join(lit(p) for p in pieces)


def split_cost(rules):
    m = re.match(r"((?:\[(?:favor|secret|favor-burnt|secret-burnt)\] )+)", rules)
    if not m:
        return {}, rules
    counts = {}
    for token in re.findall(r"\[([a-z-]+)\]", m.group(1)):
        counts[token] = counts.get(token, 0) + 1
    return counts, rules[len(m.group(1)):]


def render(counts, text):
    """Mirrors PrintedPower.rulesText."""
    symbols = " ".join(f"[{key}]" for key, _ in COST for _ in range(counts.get(key, 0)))
    return f"{symbols} {text}" if symbols else text


def cost_expr(counts):
    args = [f"{field} = {counts[key]}" for key, field in COST if counts.get(key)]
    return "Cost(" + ", ".join(args) + ")" if args else "Cost.free"


def power_names(powers):
    if len(powers) == 1:
        return ["power"]
    names = []
    for p in powers:
        parts = p["id"].split(".")
        if len(parts) == 2:
            names.append("power")
        else:
            words = parts[-1].split("-")
            names.append(words[0] + "".join(w.capitalize() for w in words[1:]))
    assert len(set(names)) == len(names), powers
    return names


def powered(powers, indent):
    pad = " " * indent
    names = power_names(powers)
    lines = []
    for name, p in zip(names, powers):
        counts, text = split_cost(p["rulesText"])
        assert render(counts, text) == p["rulesText"], p["id"]
        persistent = "true" if p["persistent"] else "false"
        lines += [f"{pad}val {name} = PrintedPower(PowerId({lit(p['id'])}),",
                  f"{pad}  persistent = {persistent}, cost = {cost_expr(counts)},",
                  f"{pad}  text = {wrapped(text, indent + 4)})"]
    lines.append(f"{pad}val powers: Vector[PrintedPower] = Vector({', '.join(names)})")
    return lines


def by_id(values):
    return sorted(values, key=lambda v: v["id"])


def denizen(d):
    name = pascal(d["name"], "Card")
    traits = DENIZEN_TRAITS[tuple(d["restrictions"] or ())]
    mix = "".join(f" with {t}" for t in traits)
    head = (f"object {name} extends Denizen(DenizenId({lit(d['id'])}), "
            f"{lit(d['name'])}, Suit.{SUITS[d['suit']]}){mix}:")
    return name, traits, [head] + powered(d["powers"], 2)


def relic(r):
    assert (r["role"] == "grand-scepter") == (r["id"] == "grand-scepter"), r["id"]
    name = pascal(r["name"], "Card")
    head = (f"object {name} extends Relic(RelicId({lit(r['id'])}), {lit(r['name'])}, "
            f"value = {r['value']}, defense = {r['defense']}):")
    return name, [], [head] + powered(r["powers"], 2)


def edifice(e):
    assert e["intact"]["restrictions"] == ["locked"] and e["ruined"]["restrictions"] is None
    name = pascal(e["intact"]["name"], "Card")
    lines = [f"object {name} extends Edifice(EdificeId({lit(e['id'])}), Suit.{SUITS[e['suit']]}):",
             f"  object intact extends EdificeFace({lit(e['intact']['name'])}) with Locked:"]
    lines += powered(e["intact"]["powers"], 4)
    lines.append(f"  object ruined extends EdificeFace({lit(e['ruined']['name'])}):")
    lines += powered(e["ruined"]["powers"], 4)
    return name, ["Locked"], lines


def legacy(l):
    name = pascal(l["name"], "Card")
    head = f"object {name} extends Legacy(LegacyId({lit(l['id'])}), {lit(l['name'])}):"
    return name, [], [head] + powered(l["powers"], 2)


def tokens(t):
    return f"Tokens({t['favor']}, {t['secrets']})"


def site(s):
    name = pascal(s["name"], "Site")
    homes = [h.split(".homeland-")[1] for h in s["handlers"] if ".homeland-" in h]
    assert len(homes) <= 1
    home = f"Some(Suit.{SUITS[homes[0]]})" if homes else "None"
    forge = f"Some({tokens(s['forgeRequirements'])})" if s["forgeRequirements"] else "None"
    recover = f"Some({s['recoverDifficulty']})" if s["recoverDifficulty"] is not None else "None"
    handlers = ", ".join(lit(h) for h in s["handlers"])
    lines = [f"object {name} extends Site(SiteId({lit(s['id'])}), {lit(s['name'])},",
             f"    defense = {s['defense']}, capacity = {s['capacity']}, relicSlots = {s['relicSlots']},",
             f"    recoverDifficulty = {recover},",
             f"    startingResources = {tokens(s['startingResources'])},",
             f"    forgeRequirements = {forge},",
             f"    homeland = {home},",
             f"    handlers = Vector({handlers}))"]
    return name, ["SUIT"] if homes else [], lines


def write_group(stem, kind_type, model_ids, entries):
    """Write entries into stem.scala, stem2.scala, ... under LIMIT lines each."""
    files, current, size = [], [], 0
    for entry in entries:
        block = entry[2] + [""]
        if current and size + len(block) > LIMIT - 12:
            files.append(current)
            current, size = [], 0
        current.append(entry)
        size += len(block)
    files.append(current)
    for index, group in enumerate(files):
        traits = sorted({t for _, ts, _ in group for t in ts if t != "SUIT"})
        uses_suit = kind_type in ("Denizen", "Edifice") or any("SUIT" in ts for _, ts, _ in group)
        catalog_names = sorted({kind_type} | set(traits) |
                               ({"EdificeFace"} if kind_type == "Edifice" else set()) |
                               ({"PrintedPower"} if kind_type != "Site" else set()))
        model_names = sorted(set(model_ids) | ({"Suit"} if uses_suit else set()) |
                             ({"Cost", "PowerId"} if kind_type != "Site" else set()))
        out = ["package oathdigital.catalog.holding", "",
               f"import oathdigital.catalog.{{{', '.join(catalog_names)}}}",
               f"import oathdigital.model.{{{', '.join(model_names)}}}", "",
               "// Generated from the retired runtime JSON (card classes slice 1).",
               "// Hand-edited from here on; a card moves out when it is implemented.", ""]
        for _, _, lines in group:
            out += lines + [""]
        name = stem if index == 0 else f"{stem}{index + 1}"
        (HOLDING / f"{name}.scala").write_text("\n".join(out).rstrip() + "\n", encoding="utf-8")
        print(f"wrote {name}.scala ({len(out)} lines, {len(group)} cards)")


def main():
    HOLDING.mkdir(parents=True, exist_ok=True)
    for old in HOLDING.glob("*.scala"):
        old.unlink()
    denizens = [denizen(d) for d in by_id(CATALOG["denizens"])]
    for suit_key, suit in SUITS.items():
        group = [e for e, d in zip(denizens, by_id(CATALOG["denizens"])) if d["suit"] == suit_key]
        write_group(f"Holding{suit}Denizens", "Denizen", ["DenizenId"], group)
    relics = [relic(r) for r in by_id(CATALOG["relics"])]
    write_group("HoldingRelics", "Relic", ["RelicId"], relics)
    edifices = [edifice(e) for e in by_id(CATALOG["edifices"])]
    write_group("HoldingEdifices", "Edifice", ["EdificeId"], edifices)
    legacies = [legacy(l) for l in by_id(CATALOG["legacies"])]
    write_group("HoldingLegacies", "Legacy", ["LegacyId"], legacies)
    sites = [site(s) for s in by_id(CATALOG["sites"])]
    write_group("HoldingSites", "Site", ["SiteId", "Tokens"], sites)

    def listing(label, kind, entries):
        body = ",\n".join(f"    {name}" for name, _, _ in entries)
        return [f"  val {label}: Vector[{kind}] = Vector(", body + ")", ""]

    registry = ["package oathdigital.gameplay.cards", "",
                "import oathdigital.catalog.{Denizen, Edifice, Legacy, Relic, Site}",
                "import oathdigital.catalog.holding._", "",
                "/** Every printed New Foundations component, listed by kind in printed-id",
                "  * order. A new card is added here and in its own file; `CardCatalogSuite`",
                "  * pins the counts so a forgotten card fails a test.",
                "  */",
                "object NewFoundations:"]
    registry += listing("denizens", "Denizen", denizens)
    registry += listing("relics", "Relic", relics)
    registry += listing("edifices", "Edifice", edifices)
    registry += listing("legacies", "Legacy", legacies)
    registry += listing("sites", "Site", sites)
    REGISTRY.parent.mkdir(parents=True, exist_ok=True)
    REGISTRY.write_text("\n".join(registry).rstrip() + "\n", encoding="utf-8")
    print(f"wrote NewFoundations.scala ({len(registry)} lines)")
    counts = (len(denizens), len(relics), len(edifices), len(legacies), len(sites))
    assert counts == (255, 48, 30, 36, 24), counts
    print("counts", counts)


main()
```

The `assert render(counts, text) == p["rulesText"]` line is the one-time
check that rebuilding the printed line reproduces every power's original
`rulesText` exactly.

- [ ] **Step 5: Run the generator**

Run: `python3 <scratchpad>/generate_cards.py /path/to/worktree`
Expected: one `wrote …` line per file (six denizen files or more, then
relics, edifices, legacies, sites, `NewFoundations.scala`), then
`counts (255, 48, 30, 36, 24)`. No assertion error. Every holding file is
under 800 lines.

Read two generated cards by eye: `grep -n -A8 "object AlchemistCard" src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens*.scala`
should show `extends Denizen(DenizenId("9"), "Alchemist", Suit.Arcane) with SiteOnly:`
and `cost = Cost(secret = 1, secretBurnt = 1)`. `HallOfDebateCard` should
have `object intact extends EdificeFace("Hall of Debate") with Locked:`.

- [ ] **Step 6: Run the equivalence suite**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.NewFoundationsEquivalenceSuite"`
Expected: 5 tests pass. If the compiler reports an unused import in a
generated file, fix the import set in `write_group` and re-run Step 5; do not
hand-edit generated files in this task.

- [ ] **Step 7: Run the gates**

Run: `./sbtw test frontend/test && python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: all pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/catalog/Cards.scala \
  src/main/scala/oathdigital/catalog/holding \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/cards/NewFoundationsEquivalenceSuite.scala
git commit -m "feat(catalog): generate every card as a Scala object"
```

---

### Task 3: Run on the Scala catalog

The switch. `ExecutableCatalog` holds the new types, every consumer moves
from `CardRestrictions` / `RelicRole` / `CatalogPower` to traits, the Grand
Scepter's id and `PrintedPower`, and the server and tests take
`NewFoundations.catalog`. The loader, its suite, the path option and the
equivalence suite go. This is the largest task; the compiler lists every site
to change.

**Files:**
- Modify: `src/main/scala/oathdigital/catalog/CatalogModel.scala` (rewrite)
- Delete: `src/main/scala/oathdigital/catalog/CatalogLoader.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`
- Modify: `src/main/scala/oathdigital/gameplay/setup/SetupProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/RuleSourceIndex.scala`
- Modify: `src/main/scala/oathdigital/application/ImplementedCardCatalog.scala`
- Modify: `src/main/scala/oathdigital/application/FirstGameChronicleGenerator.scala`
- Modify: `src/main/scala/oathdigital/application/PhasePowerProjector.scala`
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala`
- Modify: `src/main/scala/oathdigital/server/ServerConfig.scala`,
  `ServerRuntime.scala`, `OathServer.scala`
- Modify: `build.sbt` (frontend `unmanagedSources`)
- Modify: `scripts/check-architecture.py`
- Create: `src/test/scala/oathdigital/testkit/TestCards.scala`
- Delete: `src/test/scala/oathdigital/catalog/CatalogLoaderSuite.scala`,
  `src/test/resources/catalog/executable-subset.json`,
  `src/test/scala/oathdigital/gameplay/cards/NewFoundationsEquivalenceSuite.scala`
- Modify tests: listed in Step 6.

**Interfaces:**
- Consumes: Task 2's types and `NewFoundations` lists.
- Produces:
  - `ExecutableCatalog(ref: CatalogRef, denizens: Vector[Denizen], relics: Vector[Relic], edifices: Vector[Edifice], legacies: Vector[Legacy], sites: Vector[Site])`, with the same lookups as today; `printedPower(id): Option[PrintedPower]`.
  - `NewFoundations.catalog: ExecutableCatalog`.
  - `ServerRuntime.open(databasePath: Path, random: ChronicleRandomPort = …, gameIds: () => String = …)`.
  - `ServerConfig` without `catalogPath`.
  - Test helper `oathdigital.testkit.TestCards`: `power(id: String, text: String = "Test power."): PrintedPower`, `unrestricted(card: Denizen): Boolean`, `denizen(id: DenizenId, name: String, suit: Suit, powers: Vector[PrintedPower]): Denizen`, `denizenLike(base: Denizen)(name: String = base.name, powers: Vector[PrintedPower] = base.powers): Denizen`, `relic(id: RelicId, name: String, powers: Vector[PrintedPower]): Relic`, `edifice(id: EdificeId, suit: Suit, intactName: String, intactPowers: Vector[PrintedPower], ruinedName: String, ruinedPowers: Vector[PrintedPower]): Edifice`, `edificeLike(base: Edifice)(intact: Vector[PrintedPower] = base.intact.powers, ruined: Vector[PrintedPower] = base.ruined.powers): Edifice`.

- [ ] **Step 1: Write the test helper**

Create `src/test/scala/oathdigital/testkit/TestCards.scala`:

```scala
package oathdigital.testkit

import oathdigital.catalog._
import oathdigital.model.{Cost, DenizenId, EdificeId, PowerId, RelicId, Suit}

/** Cards for suites that graft a power or a name onto a catalog card, or
  * build a small catalog of their own. Card objects have no `copy`, so a
  * `…Like` variant rebuilds the card and keeps its printed restriction traits.
  */
object TestCards:
  def power(id: String, text: String = "Test power."): PrintedPower =
    PrintedPower(PowerId(id), persistent = false, Cost.free, text)

  /** True for a denizen that prints no restriction. */
  def unrestricted(card: Denizen): Boolean = card match
    case _: Locked | _: SiteOnly | _: AdviserOnly => false
    case _ => true

  def denizen(id: DenizenId, name: String, suit: Suit,
      powers: Vector[PrintedPower]): Denizen =
    val printed = powers
    new Denizen(id, name, suit) { val powers = printed }

  /** `base` with another name or other powers, keeping its restrictions. */
  def denizenLike(base: Denizen)(name: String = base.name,
      powers: Vector[PrintedPower] = base.powers): Denizen =
    val printed = powers
    base match
      case _: (Locked & AdviserOnly) =>
        new Denizen(base.id, name, base.suit) with Locked with AdviserOnly {
          val powers = printed }
      case _: AdviserOnly =>
        new Denizen(base.id, name, base.suit) with AdviserOnly {
          val powers = printed }
      case _: SiteOnly =>
        new Denizen(base.id, name, base.suit) with SiteOnly {
          val powers = printed }
      case _ =>
        new Denizen(base.id, name, base.suit) { val powers = printed }

  def relic(id: RelicId, name: String, powers: Vector[PrintedPower]): Relic =
    val printed = powers
    new Relic(id, name, value = 1, defense = 0) { val powers = printed }

  def edifice(id: EdificeId, suit: Suit, intactName: String,
      intactPowers: Vector[PrintedPower], ruinedName: String,
      ruinedPowers: Vector[PrintedPower]): Edifice =
    new Edifice(id, suit) {
      val intact: EdificeFace = new EdificeFace(intactName) with Locked {
        val powers = intactPowers }
      val ruined: EdificeFace = new EdificeFace(ruinedName) {
        val powers = ruinedPowers }
    }

  /** `base` with other powers on either face. */
  def edificeLike(base: Edifice)(
      intact: Vector[PrintedPower] = base.intact.powers,
      ruined: Vector[PrintedPower] = base.ruined.powers): Edifice =
    edifice(base.id, base.suit, base.intact.name, intact, base.ruined.name,
      ruined)
```

- [ ] **Step 2: Rewrite `ExecutableCatalog` and add `NewFoundations.catalog`**

Replace `src/main/scala/oathdigital/catalog/CatalogModel.scala` with:

```scala
package oathdigital.catalog

import oathdigital.model.{CardId, CatalogRef, DenizenId, EdificeId, LegacyId,
  PowerId, RelicId, SiteId, Suit}

/**
 * The complete runtime component catalog: card objects indexed by id and by
 * printed power. `gameplay.cards.NewFoundations.catalog` is the production
 * one; a test may `copy` in variant cards. Setup cards, player boards and
 * Visions are rules-owned code, not catalog data.
 */
final case class ExecutableCatalog(
    ref: CatalogRef,
    denizens: Vector[Denizen],
    relics: Vector[Relic],
    edifices: Vector[Edifice],
    legacies: Vector[Legacy],
    sites: Vector[Site]
):
  def denizen(id: DenizenId): Option[Denizen] = denizenById.get(id.value)
  def relic(id: RelicId): Option[Relic] = relicById.get(id.value)
  def edifice(id: EdificeId): Option[Edifice] = edificeById.get(id.value)
  def legacy(id: LegacyId): Option[Legacy] = legacyById.get(id.value)
  def site(id: SiteId): Option[Site] = siteById.get(id)

  /** The card whose printed powers include `power`. */
  def denizenWithPower(power: PowerId): Option[Denizen] =
    denizenByPower.get(power)
  def relicWithPower(power: PowerId): Option[Relic] = relicByPower.get(power)
  /** Either face's powers count. */
  def edificeWithPower(power: PowerId): Option[Edifice] =
    edificeByPower.get(power)
  def siteWithHandler(handler: PowerId): Option[Site] =
    siteByHandler.get(handler)

  /** A power printed on a denizen, relic, edifice face or legacy. Sites carry
    * handler IDs only, so a site handler has no printed power.
    */
  def printedPower(id: PowerId): Option[PrintedPower] = powerById.get(id)

  /** Suit of a denizen or edifice; other card kinds have none. */
  def suitOf(id: CardId): Option[Suit] =
    denizenById.get(id.value).map(_.suit)
      .orElse(edificeById.get(id.value).map(_.suit))

  // Built on first use, so a test's `copy` indexes its own components. The
  // first entry for a key wins, matching the scans these replace.
  private def firstBy[K, A](entries: Iterable[(K, A)]): Map[K, A] =
    entries.foldLeft(Map.empty[K, A]) { case (index, (key, value)) =>
      if index.contains(key) then index else index.updated(key, value)
    }

  private lazy val denizenById = firstBy(denizens.map(d => d.id.value -> d))
  private lazy val relicById = firstBy(relics.map(r => r.id.value -> r))
  private lazy val edificeById = firstBy(edifices.map(e => e.id.value -> e))
  private lazy val legacyById = firstBy(legacies.map(l => l.id.value -> l))
  private lazy val siteById = firstBy(sites.map(s => s.id -> s))
  private lazy val denizenByPower =
    firstBy(denizens.flatMap(d => d.powers.map(_.id -> d)))
  private lazy val relicByPower =
    firstBy(relics.flatMap(r => r.powers.map(_.id -> r)))
  private lazy val edificeByPower = firstBy(edifices.flatMap(e =>
    (e.intact.powers ++ e.ruined.powers).map(_.id -> e)))
  private lazy val siteByHandler =
    firstBy(sites.flatMap(s => s.handlers.map(PowerId(_) -> s)))
  private lazy val powerById = firstBy(
    (denizens.flatMap(_.powers) ++ relics.flatMap(_.powers) ++
      edifices.flatMap(e => e.intact.powers ++ e.ruined.powers) ++
      legacies.flatMap(_.powers)).map(p => p.id -> p))
```

Delete `src/main/scala/oathdigital/catalog/CatalogLoader.scala`.

In `NewFoundations.scala`, add `import oathdigital.catalog.ExecutableCatalog`
and `import oathdigital.model.CatalogRef` (merge into the existing catalog
import), and append to the object:

```scala
  /** The production catalog. Each kind is sorted by printed id as a string,
    * the order the JSON loader produced; setup and tests rely on it. */
  val catalog: ExecutableCatalog = ExecutableCatalog(
    CatalogRef("oath-new-foundations", "2026.08.29-pre5"),
    denizens.sortBy(_.id.value), relics.sortBy(_.id.value),
    edifices.sortBy(_.id.value), legacies.sortBy(_.id.value),
    sites.sortBy(_.id.value))
```

- [ ] **Step 3: Move the gameplay and application consumers**

`OperationRestrictions.scala`: imports become
`import oathdigital.catalog.{ExecutableCatalog, Locked}` and
`import oathdigital.catalog.holding.TheGrandScepterCard`. Delete `locking`.
`printedBy` becomes:

```scala
  private def printedBy(catalog: ExecutableCatalog): Vector[OperationRestriction] =
    val locked: Set[CardId] =
      (catalog.denizens.collect { case d: Locked => d.id: CardId } ++
        catalog.edifices.collect {
          case e if e.intact.isInstanceOf[Locked] => e.id: CardId }).toSet
    val scepter = catalog.relic(TheGrandScepterCard.id)
      .map(relic => GrandScepter(relic.id))
    Vector(LockedCards(locked), HallOfMinisters(catalog)) ++ scepter
```

and the class doc's last paragraph becomes: "`printed` reads the cards'
`Locked` trait and the Grand Scepter's card. Card classes slice 3 has the
Grand Scepter declare its own restriction."

`CardPlay.scala`: import `oathdigital.catalog.{AdviserOnly, ExecutableCatalog, SiteOnly}`.
The site check becomes

```scala
          _ <- Either.cond(!definition.isInstanceOf[AdviserOnly], (),
            InvalidSearchPlacement("adviser-only card cannot be played to a site"))
```

(a locked adviser-only card also mixes in `AdviserOnly`), and the adviser
check becomes `definition.isInstanceOf[SiteOnly]` in place of
`definition.restrictions == CardRestrictions.SiteOnly`.

`SetupProcedure.scala`: import `oathdigital.catalog.{ExecutableCatalog, SiteOnly}`;
the guard becomes `if !catalog.denizen(id).exists(_.isInstanceOf[SiteOnly]) =>`.

`RuleSourceIndex.scala`: `CatalogPower` becomes `PrintedPower` (import and the
`ids` parameter type).

`ImplementedCardCatalog.scala`: imports become
`import oathdigital.catalog.{ExecutableCatalog, PrintedPower}` and
`import oathdigital.catalog.holding.TheGrandScepterCard`;
`definition.role == RelicRole.Ordinary` becomes
`definition.id != TheGrandScepterCard.id`; `fullyImplemented` takes
`Vector[PrintedPower]`.

`FirstGameChronicleGenerator.scala`: import
`oathdigital.catalog.holding.TheGrandScepterCard` (drop `RelicRole`);
`relicPool = catalog.relics.filter(_.role == RelicRole.Ordinary).map(r => RelicId(r.id.value))`
becomes `relicPool = catalog.relics.filter(_.id != TheGrandScepterCard.id).map(_.id)`.

`PhasePowerProjector.scala`: `printed` returns the name and the printed line:

```scala
  private def printed(source: PowerSourceRef, power: PowerId)
      : Option[(String, String)] = source match
    case PowerSourceRef.Card(id: DenizenId) =>
      catalog.denizen(id).flatMap(d =>
        d.powers.find(_.id == power).map(p => d.name -> p.rulesText))
    case PowerSourceRef.Card(id: RelicId) =>
      catalog.relic(id).flatMap(r =>
        r.powers.find(_.id == power).map(p => r.name -> p.rulesText))
    case PowerSourceRef.Card(id: EdificeId) =>
      catalog.edifice(id).flatMap(e =>
        Vector(e.intact, e.ruined).flatMap(face =>
          face.powers.find(_.id == power).map(p => face.name -> p.rulesText))
          .headOption)
    case PowerSourceRef.Banner(_) => BannerFacePowers.printed(power)
    case PowerSourceRef.Site(_) =>
      SitePowerText.of(SitePowerText.kindOf(power.value)).map(site =>
        site.label -> site.text)
    case _ => None
```

and its caller binds `(name, text) <- printed(usable.source, usable.power.id)`
and yields `PhasePowerProjection(usable.power.id.value, source, name, text)`.

`GamePresentationProjector.scala`: import
`oathdigital.catalog.{AdviserOnly, ExecutableCatalog, Locked, PrintsPowers, SiteOnly}`;
`cardImplemented(definition: PrintsPowers)`; the edifice line becomes
`restrictions = face.map(restrictionName),`; the denizen line becomes
`Some(restrictionName(d)), Some(d.rulesText),`; and `restrictionName` becomes:

```scala
  private def restrictionName(card: AnyRef): String = card match
    case _: (Locked & AdviserOnly) => "locked-adviser-only"
    case _: Locked => "locked"
    case _: SiteOnly => "site-only"
    case _: AdviserOnly => "adviser-only"
    case _ => "unrestricted"
```

- [ ] **Step 4: Drop the catalog path from the server**

`ServerConfig.scala`: delete the `catalogPath` field, `DefaultCatalogPath`,
`"--catalog-path"` from `SupportedOptionOrder`, `[--catalog-path PATH] ` from
`usage`, the `val catalogPath = parsePath(…)` block, the
`"--catalog-path" -> catalogPath.left.toOption,` entry, and the
`catalogPath.toOption.get,` constructor argument.

`ServerRuntime.scala`: `open` loses its `catalogPath: Path` parameter. Replace
the `.flatMap { database => CatalogLoader.load(catalogPath)….map { catalog =>`
chain with `.map { database =>` and, as the block's first lines,
`val repository = database.eventStreams` and
`val catalog = NewFoundations.catalog` (import
`oathdigital.gameplay.cards.NewFoundations`; drop the `CatalogLoader`
import). Remove the now-extra closing brace.

`OathServer.scala`: drop `, catalogPath={}` from the log format and the
`config.catalogPath.toAbsolutePath.normalize.toString` argument (remove the
trailing comma on the line before), and call
`ServerRuntime.open(config.databasePath)`.

- [ ] **Step 5: Build and architecture rules**

`build.sbt`: in the `frontend` project's `Compile / unmanagedSources`,
delete the line
`shared / "oathdigital" / "catalog" / "CatalogModel.scala"` and the comma
that ended the line before it. Nothing in the frontend uses the catalog.

`scripts/check-architecture.py`: add a `catalog` entry to
`forbidden_imports`:

```python
    "src/main/scala/oathdigital/catalog": (
        "application", "gameplay", "persistence", "serialization", "server"
    ),
```

and extend the gameplay text rule:

```python
printed_text = re.compile(r"\.text\b")
for path in scala_sources(ROOT / "src/main/scala/oathdigital/gameplay"):
    text = path.read_text(encoding="utf-8")
    if "rulesText" in text:
        errors.append(f"{relative(path)}: gameplay must not inspect catalog rulesText")
    if printed_text.search(text):
        errors.append(f"{relative(path)}: gameplay must not read printed power text")
```

- [ ] **Step 6: Move the tests**

Delete `CatalogLoaderSuite.scala`, `src/test/resources/catalog/executable-subset.json`
and `NewFoundationsEquivalenceSuite.scala`.

`FirstGameSetupFixture.scala`: `catalog` becomes
`val catalog: ExecutableCatalog = NewFoundations.catalog`, and `catalogRef`
becomes `val catalogRef: CatalogRef = catalog.ref` (declared after
`catalog`). Import `oathdigital.gameplay.cards.NewFoundations` and
`oathdigital.catalog.holding.TheGrandScepterCard`; drop `java.nio.file.Paths`.
`relics` filters with `.filterNot(_.id == TheGrandScepterCard.id)`.

`ExecutableCatalogSuite.scala`: `catalog` becomes
`oathdigital.gameplay.cards.NewFoundations.catalog` (drop the loader and
`Paths` imports). The two `copy` tests use
`TestCards.denizenLike(d)(name = "Renamed")` and
`TestCards.denizenLike(alchemist)(name = "Shadow")` (import
`oathdigital.testkit.TestCards`).

Replace every restriction or role comparison, wherever the compiler reports
it, by this table (import the trait from `oathdigital.catalog`, or
`TestCards` from `oathdigital.testkit`):

| Old | New |
|-----|-----|
| `x.restrictions == CardRestrictions.Unrestricted` | `TestCards.unrestricted(x)` |
| `x.restrictions == CardRestrictions.SiteOnly` | `x.isInstanceOf[SiteOnly]` |
| `x.restrictions == CardRestrictions.LockedAdviserOnly` | `x.isInstanceOf[Locked & AdviserOnly]` |
| `r.role == RelicRole.Ordinary` | `r.id != TheGrandScepterCard.id` |

The sites are `PlacementFixture:39`, `CardPlayHooksSuite:34`,
`SearchFixture:40`, `ForestPathsSuite:17`, `SilverTongueSuite:74`,
`SetupProcedureSuite:51,69,71,73`, `CardPlayProcedureSuite:142,165`,
`MinorActionsSuite:232`, `AuthenticatedGameRoutesSuite:264`,
`GameApplicationServiceSuite:1631`, `FirstGameChronicleGeneratorSuite:64` and
`GameServerGatewaySubmitBeginSuite:90`.

Card variants:
- `PhasePowerSuite:66`: `d.copy(powers = d.powers :+ printed)` becomes
  `TestCards.denizenLike(d)(powers = d.powers :+ printed)`.
- `PhasePowerSuite:196-198`: the edifice `copy` becomes
  `TestCards.edificeLike(e)(intact = e.intact.powers :+ printed, ruined = e.ruined.powers :+ printed)`.
- `PhasePowerProjectorSuite:79-80`: becomes
  `TestCards.edificeLike(e)(intact = e.intact.powers :+ printed)`.
- `GamePresentationProjectorImplementedSuite:39-40`: `blank` becomes
  `TestCards.denizen(DenizenId("test-blank"), "Blank", catalog.denizens.head.suit, Vector.empty)`;
  drop the `CardRestrictions, DefinitionId, DenizenDefinition` import.
- `OpaqueIdSuite`: delete the `"DefinitionId keeps its validation and prints raw"`
  test and the `DefinitionId` import.
- `GameTrustBoundaryRoutesSuite`: the catalog becomes
  `ExecutableCatalog(CatalogRef("test", "1"), Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)`.

Replace the body of `ImplementedCardCatalogSuite` above the tests (the tests
stay as they are):

```scala
package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.{CatalogRef, DenizenId, EdificeId, PowerId, RelicId, Suit}
import oathdigital.testkit.TestCards

class ImplementedCardCatalogSuite extends munit.FunSuite:
  private val implemented: PowerId => Boolean = Set(
    "denizen.solar-hearth-child.done",
    "relic.cup-of-plenty.done",
    "edifice.hall-of-debate.intact",
    "edifice.hall-of-debate.ruined").map(PowerId(_))

  private def powers(ids: Vector[String]) = ids.map(TestCards.power(_))

  private def denizen(id: String, suit: Suit, powerIds: Vector[String]) =
    TestCards.denizen(DenizenId(id), id, suit, powers(powerIds))

  private def relic(id: String, powerIds: Vector[String]) =
    TestCards.relic(RelicId(id), id, powers(powerIds))

  private def edifice(id: String, intact: (String, Vector[String]),
      ruined: (String, Vector[String])) =
    TestCards.edifice(EdificeId(id), Suit.Hearth, intact._1,
      powers(intact._2), ruined._1, powers(ruined._2))

  private val catalog = ExecutableCatalog(
    ref = CatalogRef("test", "1"),
    denizens = Vector(
      denizen("solar-hearth-child", Suit.Hearth,
        Vector("denizen.solar-hearth-child.done")),
      denizen("unwired-card", Suit.Hearth, Vector("denizen.unwired-card.todo")),
      denizen("blank-card", Suit.Hearth, Vector.empty)),
    relics = Vector(
      relic("cup-of-plenty", Vector("relic.cup-of-plenty.done")),
      relic("unwired-relic", Vector("relic.unwired-relic.todo")),
      // The Grand Scepter's printed id: never an ordinary relic.
      relic("grand-scepter", Vector("relic.cup-of-plenty.done"))),
    edifices = Vector(
      edifice("hall-of-debate",
        "Hall of Debate" -> Vector("edifice.hall-of-debate.intact"),
        "Ruined Hall" -> Vector("edifice.hall-of-debate.ruined")),
      edifice("unwired-edifice",
        "Unwired" -> Vector("edifice.unwired-edifice.intact"),
        "Ruined Unwired" -> Vector("edifice.unwired-edifice.ruined"))),
    legacies = Vector.empty,
    sites = Vector.empty)
```

Server suites (the path argument goes):
- `ServerRuntimeSuite:19` and `ServerRoutesSuite:22,75,112,153`: drop the
  `Paths.get("docs/catalog/new-foundations-component-catalog.json")` argument
  to `ServerRuntime.open`.
- `ServerRoutesSuite:204-205`, `TrustedSeatRoutesSuite:289-295,414-416`:
  drop the `catalogPath` value and its argument to `ServerConfig(…)` and
  `ServerRuntime.open(…)`.
- `ServerConfigSuite`: delete every `catalogPath` assertion, every
  `"--catalog-path", …` argument pair and `"OATH_CATALOG_PATH" -> …` entry,
  and `"--catalog-path"` from the expected-order vectors. Add:

```scala
  test("the retired catalog path option is an unknown option"):
    val errors = ServerConfig.parse(Array("--catalog-path", "catalog.json"),
      Map.empty, version).left.toOption.get
    assert(errors.exists(_.startsWith("unknown option --catalog-path")), errors)
```

Remove any `Paths` import the compiler then flags.

- [ ] **Step 7: Compile until clean**

Run: `./sbtw Test/compile frontend/Test/compile`
Expected: compiles. Fix what remains by the rules above. Then
`grep -rn "CardRestrictions\|RelicRole\|CatalogPower\b\|DefinitionId\|CatalogLoader\|catalogPath\|DenizenDefinition\|RelicDefinition\|EdificeDefinition\|LegacyDefinition\|SiteDefinition" src frontend shared`
prints nothing.

- [ ] **Step 8: Run the gates**

Run: `./sbtw test frontend/test && python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: all pass. The test count drops by the deleted loader, equivalence
and `DefinitionId` tests, and rises by one (the retired-option test).

- [ ] **Step 9: Commit**

Stage every file this task touched by explicit path (`git status --short`
lists them; stage deletions with `git rm`), then:

```bash
git commit -m "refactor(catalog): run on the Scala card catalog"
```

---

### Task 4: Retire the JSON pipeline

The runtime no longer reads the JSON. Move it to the reference folder, delete
the validator, schema and generator, drop the packaging entries, and port the
validator's checks into `CardCatalogSuite`.

**Files:**
- Move: `docs/catalog/new-foundations-component-catalog.json` to
  `docs/catalog/reference/new-foundations-component-catalog.json`
- Create: `docs/catalog/reference/README.md`
- Delete: `docs/catalog/new-foundations-component-catalog.schema.json`,
  `scripts/validate-component-catalog.py`,
  `reference/catalog-ingestion/build_runtime_catalog.py`,
  `reference/catalog-ingestion/reviewed-runtime-powers.json`,
  `reference/catalog-ingestion/runtime-denizen-definitions.json`
- Modify: `reference/catalog-ingestion/README.md`
- Modify: `build.sbt`, `scripts/smoke-packaged-distribution.sh`,
  `scripts/verify-alpha-release.sh`, `README.md`, `.claude/launch.json`
- Modify: `src/test/scala/oathdigital/server/DesktopLaunchProfileSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala`

**Interfaces:**
- Consumes: `NewFoundations.catalog`; `WalkerPowerCatalog.default(catalog).powers`
  and `PhasePowerCatalog.default(catalog).powers` (each power has `id: PowerId`).

- [ ] **Step 1: Write `CardCatalogSuite`**

Create `src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala`:

```scala
package oathdigital.gameplay.cards

import oathdigital.catalog.PrintsPowers
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.model.PowerId

/** The printed-corpus checks `validate-component-catalog.py` ran on the JSON,
  * now run on the Scala catalog. Suits, restriction combinations, edifice
  * faces and the single Grand Scepter are types, so they need no check.
  */
class CardCatalogSuite extends munit.FunSuite:
  private val catalog = NewFoundations.catalog
  private val printing: Vector[PrintsPowers] =
    catalog.denizens ++ catalog.relics ++ catalog.legacies ++
      catalog.edifices.flatMap(e => Vector(e.intact, e.ruined))
  private val powers = printing.flatMap(_.powers)
  private val symbols = Set("attack-die", "defense-die", "favor",
    "favor-burnt", "hollow-sword", "round-die", "secret", "secret-burnt",
    "shield", "skull", "suit-arcane", "suit-beast", "suit-discord",
    "suit-hearth", "suit-nomad", "suit-order", "sword")
  private val symbol = "\\[([a-z-]+)\\]".r

  test("the catalog holds every printed component"):
    assertEquals(Vector(catalog.denizens.size, catalog.relics.size,
      catalog.edifices.size, catalog.legacies.size, catalog.sites.size),
      Vector(255, 48, 30, 36, 24))

  test("printed ids are the expected sets"):
    assertEquals(catalog.denizens.map(_.id.value).toSet,
      ((1 to 258).toSet -- Set(94, 110, 174)).map(_.toString))
    assertEquals(catalog.relics.map(_.id.value).toSet,
      (1 to 47).map(n => f"R$n%02d").toSet + "grand-scepter")
    assertEquals(catalog.edifices.map(_.id.value).toSet,
      (1 to 30).map(n => f"E$n%02d").toSet)
    assertEquals(catalog.legacies.map(_.id.value).toSet,
      (1 to 36).map(n => f"L$n%02d").toSet)
    assert(catalog.sites.forall(_.id.value.startsWith("site:")))

  test("card ids are unique across kinds"):
    val ids = catalog.denizens.map(_.id.value) ++ catalog.relics.map(_.id.value) ++
      catalog.edifices.map(_.id.value) ++ catalog.legacies.map(_.id.value) ++
      catalog.sites.map(_.id.value)
    assertEquals(ids.diff(ids.distinct), Vector.empty)

  test("power ids and site handlers are unique"):
    val ids = powers.map(_.id.value) ++ catalog.sites.flatMap(_.handlers)
    assertEquals(ids.diff(ids.distinct), Vector.empty)

  test("printed text is non-blank and uses only the symbol vocabulary"):
    powers.foreach { p =>
      assert(p.text.trim.nonEmpty, p.id)
      val unknown = symbol.findAllMatchIn(p.rulesText).map(_.group(1))
        .filterNot(symbols).toVector
      assertEquals(unknown, Vector.empty, p.id)
      assert(!p.rulesText.exists("©®�".contains(_)), p.id)
    }

  test("a site has Forge requirements exactly when it has three slots"):
    catalog.sites.foreach(s =>
      assertEquals(s.forgeRequirements.nonEmpty, s.capacity == 3, s.id))

  test("printed numbers are non-negative"):
    assert(catalog.relics.forall(r => r.value >= 0 && r.defense >= 0))
    assert(catalog.sites.forall(s => s.defense >= 0 && s.capacity >= 0 &&
      s.relicSlots >= 0 && s.recoverDifficulty.forall(_ >= 0)))

  test("every registered card power is printed in the catalog"):
    val printed = (powers.map(_.id) ++
      catalog.sites.flatMap(_.handlers).map(PowerId(_))).toSet
    val cardPrefixes = Vector("denizen.", "relic.", "edifice.", "legacy.", "site.")
    val registered = (WalkerPowerCatalog.default(catalog).powers.map(_.id) ++
      PhasePowerCatalog.default(catalog).powers.map(_.id))
      .filter(id => cardPrefixes.exists(id.value.startsWith))
    assertEquals(registered.filterNot(printed).distinct, Vector.empty)
```

- [ ] **Step 2: Run it**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: 8 tests pass. If the last test lists ids, a power is registered
under an id no card prints: that is a bug that existed before this slice.
Stop and report the ids; do not loosen the test.

- [ ] **Step 3: Move the JSON and delete the pipeline**

```bash
mkdir -p docs/catalog/reference
git mv docs/catalog/new-foundations-component-catalog.json docs/catalog/reference/new-foundations-component-catalog.json
git rm docs/catalog/new-foundations-component-catalog.schema.json scripts/validate-component-catalog.py \
  reference/catalog-ingestion/build_runtime_catalog.py \
  reference/catalog-ingestion/reviewed-runtime-powers.json \
  reference/catalog-ingestion/runtime-denizen-definitions.json
```

Create `docs/catalog/reference/README.md`:

```markdown
# Reference catalog

`new-foundations-component-catalog.json` is the last runtime catalog the game
loaded before the card classes phase (version `2026.08.29-pre5`). It is
reference only. The game no longer reads it, and it is not kept in sync.

The card data is Scala: card types in `src/main/scala/oathdigital/catalog/`,
cards not yet implemented in `catalog/holding/`, each implemented card beside
its power under `gameplay/powers/`, and the full list in
`gameplay/cards/NewFoundations.scala`.
```

In `reference/catalog-ingestion/README.md`, replace the first paragraph
(from "These files are review and regeneration inputs" through "placement
restrictions.") with:

```markdown
These files are the transcription evidence the runtime catalog was built
from. The generator (`build_runtime_catalog.py`) and its reviewed mirrors
were retired in the card classes phase: card data is now Scala, see
`docs/catalog/reference/README.md`. Nothing here is loaded or regenerated.
```

Then delete any later sentence in that README that tells a reader to run the
generator or edit `runtime-denizen-definitions.json` /
`reviewed-runtime-powers.json`.

- [ ] **Step 4: Drop the packaging entries**

`build.sbt`:
- In the first `Universal / mappings ++=` block, the JSON mapping goes and
  the block becomes:

```scala
    Universal / mappings ++= ((baseDirectory.value / "docs/operations") ** "*.md")
      .get
      .map(file => file -> s"share/oathdigital/${file.getName}"),
```

- In `bashScriptExtraDefines`, delete the two `OATH_CATALOG_PATH` lines.
- In `batScriptExtraDefines`, delete the `OATH_CATALOG_PATH` line.
- In `verifyPackageMappings`, delete
  `"share/oathdigital/new-foundations-component-catalog.json",`.

`scripts/smoke-packaged-distribution.sh`: delete the `catalog_path=…` line
and the `OATH_CATALOG_PATH="$catalog_path" \` line.

`scripts/verify-alpha-release.sh`: delete
`python3 scripts/validate-component-catalog.py`, and change
`architecture/catalog/Markdown checks` to `architecture/Markdown checks`.

`README.md`: delete the `validate-component-catalog.py` and
`build_runtime_catalog.py` lines from the check block, delete the sentence
"The catalog generator without `--output` is a non-writing equality check.",
drop ` --catalog-path docs/catalog/new-foundations-component-catalog.json`
from the `runMain` example, and change "default to `trusted-alpha` mode and
their bundled catalog while preserving" to "default to `trusted-alpha` mode
while preserving".

`.claude/launch.json`: drop
` --catalog-path docs/catalog/new-foundations-component-catalog.json` from
`runtimeArgs`.

`DesktopLaunchProfileSuite.scala`, test "unrelated environment variables pass
through": use `"OATH_UNRELATED" -> "/unrelated"` in place of
`"OATH_CATALOG_PATH" -> "/catalog.json"` in both lines.

- [ ] **Step 5: Check nothing still points at the old paths**

Run: `grep -rn "validate-component-catalog\|build_runtime_catalog\|OATH_CATALOG_PATH\|catalog-path\|component-catalog.schema" --exclude-dir=superpowers --exclude-dir=target --exclude-dir=node_modules . | grep -v "^./.git/"`
Expected: matches only in `docs/` prose that Task 6 rewrites
(`docs/operations/configuration.md`, `docs/catalog/README.md`,
`docs/architecture/*.md`, `docs/ROADMAP.md`). No match in code, scripts,
`build.sbt`, `README.md` or `.claude/`.

- [ ] **Step 6: Run the gates**

Run: `./sbtw test frontend/test verifyPackageMappings && python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py && sh -n scripts/smoke-packaged-distribution.sh && sh -n scripts/verify-alpha-release.sh`
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add docs/catalog/reference/README.md reference/catalog-ingestion/README.md \
  build.sbt scripts/smoke-packaged-distribution.sh scripts/verify-alpha-release.sh \
  README.md .claude/launch.json \
  src/test/scala/oathdigital/server/DesktopLaunchProfileSuite.scala \
  src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala
git commit -m "refactor(catalog): retire the runtime JSON pipeline"
```

(The `git mv` and `git rm` from Step 3 are already staged.)

---

### Task 5: Remove `CatalogRef`

The catalog is code, so the game's build is the pin. `CatalogRef` leaves
`OathGame`, the event envelope and the codecs. Decoding ignores a `catalog`
field in stored events, so existing journals still load.

**Files:**
- Modify: `src/main/scala/oathdigital/model/Identity.scala` (delete `CatalogRef`)
- Modify: `src/main/scala/oathdigital/model/GameState.scala` (`OathGame`)
- Modify: `src/main/scala/oathdigital/gameplay/setup/GameStartRules.scala`
- Modify: `src/main/scala/oathdigital/catalog/CatalogModel.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/main/scala/oathdigital/application/GameEventCodec.scala`,
  `GameApplicationService.scala`
- Modify: `src/main/scala/oathdigital/serialization/GameEventWire.scala`,
  `GameEventJsonSupport.scala`, `GameEventCodecAdapter.scala`,
  `WireError.scala`, `LifecycleEventCodec.scala`, `ActionEventCodec.scala`,
  `EndingEventCodec.scala`, `WalkerEventCodec.scala`
- Modify tests: `GameEventWireSuite`, `GameApplicationServiceSuite`,
  `TestGameFixtures`, `FirstGameSetupFixture`, `ImplementedCardCatalogSuite`,
  `GameTrustBoundaryRoutesSuite`, and
  `src/test/resources/serialization/search-event-stream-v4.json`

**Interfaces:**
- Produces: `OathGame(campaign: CampaignState, current: CurrentGameState)`;
  `GameEventEnvelope(formatVersion, gameId, sequence, eventType, event)`;
  `GameEventWire.encodeEvent(gameId, sequence, event)`,
  `encodeStream(gameId, events)` and `encodeStream(gameId, startSequence, events)`;
  `GameEventCodec.encodeEvent(gameId, sequence, event)`;
  `ExecutableCatalog(denizens, relics, edifices, legacies, sites)`.

- [ ] **Step 1: Write the failing test**

In `GameEventWireSuite`, add:

```scala
  test("an envelope carries no catalog, and a stored catalog field is ignored"):
    val event = OathEvent.BanditsRefilled(Vector.empty)
    val encoded = GameEventWire.encodeEvent("game", 0L, event).toOption.get
    assert(!encoded.obj.contains("catalog"))
    val stored = ujson.copy(encoded)
    stored.obj("catalog") = ujson.Obj("ruleset" -> "oath-new-foundations",
      "version" -> "2026.08.29-pre5")
    assertEquals(GameEventWire.decode(stored).map(_.event), Right(event))
```

Run: `./sbtw "testOnly oathdigital.serialization.GameEventWireSuite"`
Expected: compile error, `encodeEvent` takes four arguments.

- [ ] **Step 2: Remove the ref from the wire**

- `GameEventEnvelope`: delete the `catalog` field.
- `GameEventWire`: `encodeEvent`, both `encodeStream` overloads and the
  `encodeEvent` call inside `encodeStream` lose the `catalog` parameter and
  argument; `encode` drops the `"catalog" -> …` entry; `decode` drops
  `refValue <- requiredField(obj, "catalog", path)` and
  `ref <- decodeCatalog(…)`, passes no ref to `decodePayload`, and builds the
  envelope without it; `validateStream` drops the `CatalogMismatch` case;
  `validateEnvelope` drops the `validateEventCatalog` line; `decodePayload`
  loses `envelopeCatalog` and passes none to the four codec decoders.
- `GameEventJsonSupport`: delete `validateEventCatalog`, `encodeCatalog` and
  `decodeCatalog`.
- `LifecycleEventCodec`, `ActionEventCodec`, `EndingEventCodec`,
  `WalkerEventCodec`: each `…Decode(eventType, payload, path, envelopeCatalog)`
  loses the `envelopeCatalog: CatalogRef` parameter.
- `WireError`: delete `CatalogMismatch`.
- `GameEventCodec` (application port) and `GameEventCodecAdapter`:
  `encodeEvent(gameId: String, sequence: Long, event: OathEvent)`.
- `GameApplicationService`: the `encodeEvent` call drops `catalog.ref,`.

- [ ] **Step 3: Remove the ref from the model and catalog**

- `OathGame`: delete the `catalog: CatalogRef` field.
- `GameStartRules.evolve`: `OathGame(catalog.ref, …)` becomes `OathGame(…)`
  without the first argument.
- `ExecutableCatalog`: delete the `ref` field and the `CatalogRef` import.
- `NewFoundations.catalog`: drop the `CatalogRef(…)` argument and import.
- `Identity.scala`: delete `CatalogRef`.

- [ ] **Step 4: Move the tests**

- `GameEventWireSuite`: drop the `catalogRef` argument from every
  `encodeEvent` / `encodeStream` call, delete any test whose subject is a
  catalog mismatch or a missing or invalid `catalog` field, and remove
  `"catalog"` entries from inline JSON.
- `search-event-stream-v4.json`: delete both `"catalog": {…},` lines.
- `GameApplicationServiceSuite:1385,1391,1424,1464`: drop the `catalogRef`
  argument.
- `TestGameFixtures:122-123`: drop the `CatalogRef(…)` argument to `OathGame`.
- `FirstGameSetupFixture`: delete `catalogRef`.
- `ImplementedCardCatalogSuite` and `GameTrustBoundaryRoutesSuite`: drop the
  `CatalogRef` argument to `ExecutableCatalog` and its import.

Run: `./sbtw Test/compile` and fix what the compiler still reports by the
same rules; then `grep -rn "CatalogRef\|catalogRef\|envelopeCatalog\|CatalogMismatch" src`
prints nothing.

- [ ] **Step 5: Run the gates**

Run: `./sbtw test frontend/test && python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: all pass, including the new envelope test.

- [ ] **Step 6: Commit**

Stage every touched file by explicit path, then:

```bash
git commit -m "refactor(events): drop the catalog reference from games and envelopes"
```

---

### Task 6: Docs and ROADMAP

**Files:**
- Modify: `docs/catalog/README.md`, `docs/architecture/core-domain-model.md`,
  `docs/architecture/authoritative-events.md`,
  `docs/architecture/codebase-structure.md`,
  `docs/architecture/gameplay-modules.md`,
  `docs/architecture/rule-resolution.md`,
  `docs/architecture/server-event-journal.md`,
  `docs/operations/configuration.md`,
  `docs/rules/implementation-traceability.md`, `docs/rules/ambiguities.md`,
  `PRODUCT.md`, `docs/ROADMAP.md`

- [ ] **Step 1: Find every stale statement**

Run: `grep -rn "new-foundations-component-catalog\|catalog-path\|OATH_CATALOG_PATH\|validate-component-catalog\|CatalogRef\|catalogVersion\|catalog version\|authoritative\|fingerprint\|CatalogLoader\|rulesText\|ingestion" docs/catalog/README.md docs/architecture docs/operations docs/rules PRODUCT.md README.md`

- [ ] **Step 2: Rewrite each hit**

Apply these facts, keeping each doc's own voice and only the sentences that
are now wrong:

- Card data is Scala. Types live in `catalog/Cards.scala`; cards not yet
  implemented in `catalog/holding/`; implemented cards beside their power
  (from slice 2); the full list and the production `ExecutableCatalog` in
  `gameplay/cards/NewFoundations.scala`. That code is authoritative for
  inventory, printed identities, text, restrictions and handler keys.
- The JSON is reference only, at `docs/catalog/reference/`.
- There is no `--catalog-path` option and no `OATH_CATALOG_PATH`; packages
  bundle no catalog file.
- Games and event envelopes carry no catalog reference; the build is the pin.
  (In `authoritative-events.md`, remove "catalog disagreement" from what
  readers reject and the line about the setup payload repeating the ref.)
- There are no audit fingerprints. `CatalogHandlerInventory.handlerIds` still
  lists handler ids.
- Gameplay never reads printed text: `check-architecture.py` forbids
  `rulesText` and `.text` under `gameplay/`.
- `CardCatalogSuite` runs the checks the Python validator ran.

In `docs/catalog/README.md`, keep the data conventions (printed ids, edifice
faces, restriction grammar, `[token]` symbols) and rewrite the opening and any
section about editing the JSON, the generator or the validator, per the facts
above. In `server-event-journal.md`, the stale `OathServer var/oathdigital
docs/catalog/…` command becomes
`./sbtw 'runMain oathdigital.server.OathServer --database-path var/oathdigital'`.

- [ ] **Step 3: Update the ROADMAP**

In `docs/ROADMAP.md`, replace the body of `### Phase - Card classes` with:

```markdown
Design: `docs/superpowers/specs/2026-10-01-card-classes-design.md`.

- [x] **Slice 1 - data to Scala.** Every component is a Scala object; the
  runtime JSON, its loader, validator, schema, path option, packaging entry,
  `CatalogRef` and the audit fingerprints are gone.
- [ ] **Slice 2 - denizens beside their powers.**
- [ ] **Slice 3 - relics and edifices.**
- [ ] **Slice 4 - sites and legacies.**
```

Add two items to `### Phase - Cleanup tasks`:

```markdown
- [ ] **Shrink cached catalog fields.** About 242 powers hold a
  `catalog: ExecutableCatalog` field. Static card references (card classes
  slices 2-4) make many unnecessary; remove them as powers are touched.
- [ ] **Migrate `CatalogNames` to card objects.** About 76 test files look
  cards up by name through `CatalogNames`. Move them to direct object
  references such as `AlchemistCard`.
```

Then fix any other ROADMAP line the Step 1 grep pattern matches outside
`docs/superpowers` (for example the verification gate's "runtime-catalog
validation and equality", which becomes "the card catalog suite").

- [ ] **Step 4: Run the checks**

Run: `python3 scripts/check-markdown-links.py && python3 scripts/check-architecture.py`
Expected: both pass. Re-run the Step 1 grep: no hit states something false.

- [ ] **Step 5: Commit**

```bash
git add docs/catalog/README.md docs/architecture docs/operations docs/rules PRODUCT.md docs/ROADMAP.md
git commit -m "docs: record the card catalog in Scala"
```

(`git add docs/architecture docs/operations docs/rules` stages only the files
edited there; check `git status --short` first.)
