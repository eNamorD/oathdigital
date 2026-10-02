# Card Classes Slice 4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Site handlers are typed power ids that each site object names,
Homeland readers use the printed `homeland` suit, and the 13 sites with an
implemented River or Travel power sit beside that power, which names its site
instead of looking it up in the catalog.

**Architecture:** Two scripted, reviewable steps. Task 1 changes
`Site.handlers` from a constructor `Vector[String]` to an abstract
`Vector[PowerId]` that every site object fills from named members (as a
denizen names its `power`), and moves every Homeland reader and every test
lookup off handler strings. Task 2 moves the 13 implemented sites out of
`catalog/holding/` into `RiverSitePower.scala` and `TravelSitePowers.scala`,
makes both powers static (registered whichever sites a catalog lists), and
deletes `ExecutableCatalog.siteWithHandler`.

**Tech Stack:** Scala 3.9, sbt (`./sbtw`), munit, Python 3 (edit scripts).

**Spec:** `docs/superpowers/specs/2026-10-01-card-classes-design.md`
(slice 4). Read it first. The slice 3 plan,
`docs/superpowers/plans/2026-10-01-card-classes-slice3.md`, did the same
moves for relics and edifices; this plan follows its patterns.

## Global Constraints

- Printed ids and power ids do not change (`"site:deep-woods"`,
  `"site.deep-woods.homeland-beast"`, `"site.headwaters.river"`).
- Site objects keep their names (`HeadwatersSite`) and their printed numbers;
  a move changes only the object's file and package.
- References run one way: behaviour to card. A site object never refers to
  its power.
- `gameplay/` never contains the string `rulesText` and never reads `.text`.
- `catalog/` imports only `model` (and the JDK).
- Production Scala files are at most 800 lines.
- Compiler flags are `-Werror` with unused imports, privates and locals.
  Remove every import the compiler flags.
- `ExecutableCatalog` keeps its id lookups and its power index
  (`denizenWithPower`, `relicWithPower`, `edificeWithPower`, `printedPower`);
  the spec keeps them. Only `siteWithHandler` goes.
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

The spec fixes the target; these are the choices for getting there, settled
with the user on 2026-10-01.

- **Sites name their handlers.** A site object declares each power id as a
  member named after the handler's suffix and lists them in `handlers`:

  ```scala
  object HeadwatersSite extends Site(SiteId("site:headwaters"), "Headwaters",
      defense = 1, capacity = 2, relicSlots = 1,
      recoverDifficulty = Some(4),
      startingResources = Tokens(0, 0),
      forgeRequirements = None,
      homeland = None):
    val river = PowerId("site.headwaters.river")
    val mountain = PowerId("site.headwaters.mountain")
    val handlers: Vector[PowerId] = Vector(river, mountain)
  ```

  `Site.handlers` becomes an abstract `def handlers: Vector[PowerId]`. All
  24 sites take this shape, held or not. A Homeland handler
  (`site.deep-woods.homeland-beast`) is named `homelandPower`, because
  `homeland` is already the site's printed suit. A site power names its
  handler as `HeadwatersSite.river`. Grand Canal ("Act as if this site is a
  Coast") stays possible: it will be an edifice power that makes Coast a
  state query; it does not depend on how a site declares its handlers.
- **Silent lookups are rewritten by hand, file by file.** A
  `Vector[PowerId]` still accepts `contains("site.narrow-pass.pass")` and
  silently finds nothing. The prototype found every site-handler consumer by
  renaming the field and compiling; the Task 1 script rewrites each one
  explicitly. Denizen, relic, edifice and legacy `handlers` (the derived
  `PrintsPowers.handlers`) stay `Vector[String]`; their `contains("…")`
  lookups are correct and unchanged.
- **Homeland reads the printed suit.** `CardPlay.homelandSuit`,
  `GameStartRules` and `FirstGameChronicleGenerator` read `Site.homeland`
  instead of parsing a `.homeland-<suit>` handler. A `CardCatalogSuite`
  check keeps the two declarations in agreement. The presentation layer's
  `SitePowerText` still names a site power from its handler suffix; it is
  display text in `application/`, not a gameplay rule.
- **Implemented means registered.** A site moves when one of its handlers is
  registered in `WalkerPowerCatalog.default` or `PhasePowerCatalog.default`:
  the 4 River sites and the 9 other sites with a Travel terrain power, 13 in
  all. Homeland and Plains handlers are not registered powers, so those 11
  sites stay in `HoldingSites.scala`. `site.take-wealth` is a game rule, not
  a printed handler.
- **One destination per site.** A site with a River power sits in
  `gameplay/powers/wake/RiverSitePower.scala`, even when it also has a
  Travel power (Headwaters, Tidal Marshes). The other 9 sit in
  `gameplay/powers/travel/TravelSitePowers.scala`, which imports the two
  from `wake`.
- **Site powers are static.** As in slices 2 and 3, a power no longer
  depends on which cards a catalog lists. `RiverSitePower.forCatalog`
  becomes `RiverSitePower.all`, `isRiver(catalog, site)` becomes
  `isRiver(site)`, and `TravelSitePowers.forCatalog` becomes
  `TravelSitePowers.all`, keeping the same powers in the same order. A
  catalog without a site never puts it into play, so its power is inert
  there.
- **Legacies keep the catalog lookup.** No legacy power is implemented, so
  all 36 legacies stay in `HoldingLegacies.scala`. `RuleSourceIndex` keeps
  `catalog.legacy(id)`, the same id lookup it uses for denizens, relics,
  edifices and sites. This replaces the spec's "`RuleSourceIndex` reads
  legacies statically": a static read would gain nothing and would ignore a
  test's variant catalog.
- **Two lines over 80 columns move verbatim.** The headers of
  `DesolateShoreSite` and `TidalMarshesSite` were generated over 80 columns
  in slice 1. The move keeps them as they are.

## Files

- Modify: `src/main/scala/oathdigital/catalog/Cards.scala` (`Site`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingSites.scala`
- Modify: `src/main/scala/oathdigital/catalog/CatalogModel.scala`
- Modify: `src/main/scala/oathdigital/catalog/CatalogHandlerInventory.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`
- Modify: `src/main/scala/oathdigital/gameplay/setup/GameStartRules.scala`
- Modify: `src/main/scala/oathdigital/application/FirstGameChronicleGenerator.scala`
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala`
- Modify: `src/main/scala/oathdigital/gameplay/RuleSourceIndex.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/wake/RiverSitePower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
  (imports only)
- Modify (tests): `testkit/TestCards.scala`, `gameplay/HomelandRuleSuite.scala`,
  `gameplay/cards/CardCatalogSuite.scala`,
  `catalog/CatalogHandlerInventorySuite.scala`,
  `catalog/ExecutableCatalogSuite.scala`, `gameplay/TravelProcedureSuite.scala`,
  `gameplay/powers/travel/TravelFixture.scala`,
  `gameplay/TravelSitePowersSuite.scala`, `gameplay/WakeAutoEndSuite.scala`,
  `gameplay/CampaignPowersSuite.scala`, `gameplay/RuleSourceIndexSuite.scala`,
  `application/ForgeWalkerFixture.scala`,
  `gameplay/setup/GameStartRulesSuite.scala`,
  `gameplay/setup/FirstGameSetupFixture.scala`,
  `application/FirstGameChronicleGeneratorSuite.scala`,
  `gameplay/powers/wake/RiverFixture.scala`,
  `gameplay/powers/wake/RiverSitePowerSuite.scala` (all under
  `src/test/scala/oathdigital/`)
- Modify: `docs/ROADMAP.md` (tick slice 4)

## Scripts

The edits are scripted so a reviewer can read one script instead of 40
hand edits. Save each script below into `<scratchpad>/s4/` (the session's
scratchpad directory; the code blocks are complete), and run them against
the worktree root. The scripts are not committed. Each was run against a
copy of `main` at `aaa94303`: after Task 1 the suite passed 2539 tests, and
after Task 2 it passed 2539 tests with the architecture check green.

### Shared import helper (`s4_imports.py`)

```python
"""Import editing shared by the card classes slice 4 scripts."""
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

### Task 1: Sites name their handlers as power ids

**Files:** `Cards.scala`, `HoldingSites.scala`, `CatalogModel.scala`,
`CatalogHandlerInventory.scala`, `CardPlay.scala`, `GameStartRules.scala`,
`FirstGameChronicleGenerator.scala`, `GamePresentationProjector.scala`,
`RuleSourceIndex.scala`, `RiverSitePower.scala` (one line), and the tests
listed under **Files** above except `ExecutableCatalogSuite`,
`RiverFixture` and `RiverSitePowerSuite`.

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `abstract class Site(id, name, defense, capacity, relicSlots,
  recoverDifficulty, startingResources, forgeRequirements, homeland:
  Option[Suit])` with `def handlers: Vector[PowerId]`; every site object
  has one `val <suffix>: PowerId` per handler (`AncientCitySite.river`,
  `HeadwatersSite.mountain`, `NarrowPassSite.pass`, `FairIsleSite.island`,
  `TidalMarshesSite.coast`, `DeepWoodsSite.homelandPower`);
  `CardPlay.homelandSuit(catalog, site): Option[Suit]` reads
  `Site.homeland`; `TestCards.siteLike(base)(id, forgeRequirements,
  homeland, handlers: Vector[PowerId])`.

- [ ] **Step 1: Write the failing test**

In `src/test/scala/oathdigital/testkit/TestCards.scala`, give `siteLike` a
`homeland` parameter. Replace:

```scala
  /** `base` with another id, printed Forge cost or handlers. */
  def siteLike(base: Site)(id: SiteId = base.id,
      forgeRequirements: Option[Tokens] = base.forgeRequirements,
      handlers: Vector[String] = base.handlers): Site =
    new Site(id, base.name, base.defense, base.capacity, base.relicSlots,
      base.recoverDifficulty, base.startingResources, forgeRequirements,
      base.homeland, handlers) {}
```

with:

```scala
  /** `base` with another id, printed Forge cost, Homeland or handlers. */
  def siteLike(base: Site)(id: SiteId = base.id,
      forgeRequirements: Option[Tokens] = base.forgeRequirements,
      homeland: Option[Suit] = base.homeland,
      handlers: Vector[String] = base.handlers): Site =
    new Site(id, base.name, base.defense, base.capacity, base.relicSlots,
      base.recoverDifficulty, base.startingResources, forgeRequirements,
      homeland, handlers) {}
```

In `src/test/scala/oathdigital/gameplay/HomelandRuleSuite.scala`, change the
import `oathdigital.testkit.{CatalogNames, Table}` to
`oathdigital.testkit.{CatalogNames, Table, TestCards}`, change the class
doc's "The site's handler names the suit, and the discard is" to "The site
prints its Homeland suit, and the discard is", and replace the first test:

```scala
  test("the Homeland suit is read from the site's handler"):
    assertEquals(CardPlay.homelandSuit(catalog, SiteId("site:deep-woods")),
      Some(Suit.Beast))
    assertEquals(CardPlay.homelandSuit(catalog, SiteId("site:ancient-city")), None)
```

with:

```scala
  test("the Homeland suit is the one the site prints"):
    assertEquals(CardPlay.homelandSuit(catalog, SiteId("site:deep-woods")),
      Some(Suit.Beast))
    assertEquals(CardPlay.homelandSuit(catalog, SiteId("site:ancient-city")), None)
    // A variant Ancient City printing an Order Homeland keeps its handlers.
    val city = catalog.site(SiteId("site:ancient-city")).get
    val variant = catalog.copy(sites = catalog.sites.map(site =>
      if site.id == city.id then
        TestCards.siteLike(city)(homeland = Some(Suit.Order))
      else site))
    assertEquals(CardPlay.homelandSuit(variant, city.id), Some(Suit.Order))
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.HomelandRuleSuite"`
Expected: FAIL, 1 of 6: "the Homeland suit is the one the site prints",
`None` obtained where `Some(Order)` was expected (the suit still comes from
the handler string).

- [ ] **Step 3: Save and run the handler script**

Save `s4_handlers.py` (below) into `<scratchpad>/s4/`, then run:

```bash
python3 <scratchpad>/s4/s4_handlers.py <worktree root>
```

Expected output: `site handlers are power ids; Homeland reads the printed
suit`. Every replacement asserts its old text is present, so a mismatch
stops the script with the file and text.

```python
"""Card classes slice 4, Task 1: site handlers become named power ids, and
Homeland readers use the printed `homeland` suit.

Usage: python3 s4_handlers.py <worktree root>
"""
import re
import sys

W = sys.argv[1].rstrip("/") + "/"
M = W + "src/main/scala/oathdigital/"
T = W + "src/test/scala/oathdigital/"


def edit(path, *pairs):
    s = open(path).read()
    for old, new in pairs:
        assert old in s, (path, old)
        s = s.replace(old, new)
    open(path, "w").write(s)


def member(handler):
    """`site.deep-woods.homeland-beast` -> `homelandPower`; else the suffix.
    `homeland` is taken: it is the site's printed suit."""
    suffix = handler.rsplit(".", 1)[1]
    return "homelandPower" if suffix.startswith("homeland-") else suffix


edit(M + "catalog/Cards.scala", (
    '/** A site. It prints no rules text; `handlers` are its power ids. */\n',
    '/** A site. It prints no rules text. A site object names each of its\n'
    '  * power ids as a member and lists them in `handlers`. */\n'), (
    '    val homeland: Option[Suit],\n'
    '    val handlers: Vector[String]\n'
    ')\n',
    '    val homeland: Option[Suit]\n'
    '):\n'
    '  def handlers: Vector[PowerId]\n'))

p = M + "catalog/holding/HoldingSites.scala"
s = open(p).read()


def site(m):
    ids = re.findall(r'"([^"]+)"', m.group(2))
    names = [member(h) for h in ids]
    assert len(set(names)) == len(names), ids
    lines = [f"    homeland = {m.group(1)}):"]
    lines += [f'  val {n} = PowerId("{h}")' for n, h in zip(names, ids)]
    lines.append(f"  val handlers: Vector[PowerId] = "
                 f"Vector({', '.join(names)})")
    return "\n".join(lines)


s, n = re.subn(r"    homeland = ([^\n]*),\n    handlers = Vector\(([^)]*)\)\)",
               site, s)
assert n == 24, n
s = s.replace("import oathdigital.catalog.{Site}\n",
              "import oathdigital.catalog.Site\n")
s = s.replace("import oathdigital.model.{SiteId, Suit, Tokens}\n",
              "import oathdigital.model.{PowerId, SiteId, Suit, Tokens}\n")
open(p, "w").write(s)

edit(M + "catalog/CatalogModel.scala", (
    "firstBy(sites.flatMap(s => s.handlers.map(PowerId(_) -> s)))",
    "firstBy(sites.flatMap(s => s.handlers.map(_ -> s)))"))
edit(M + "catalog/CatalogHandlerInventory.scala", (
    "catalog.sites.flatMap(_.handlers) ++",
    "catalog.sites.flatMap(_.handlers.map(_.value)) ++"))
edit(M + "gameplay/powers/wake/RiverSitePower.scala", (
    '_.handlers.exists(_.endsWith(".river"))',
    '_.handlers.exists(_.value.endsWith(".river"))'))
edit(M + "gameplay/RuleSourceIndex.scala", (
    "IndexedRuleSource(RuleSourceRef.Site(siteId), rawIds(definition.handlers),",
    "IndexedRuleSource(RuleSourceRef.Site(siteId), definition.handlers,"))
edit(M + "application/GamePresentationProjector.scala", (
    "  private def sitePower(handler: String): SitePowerProjection =\n"
    "    val kind = SitePowerText.kindOf(handler)\n",
    "  private def sitePower(handler: PowerId): SitePowerProjection =\n"
    "    val kind = SitePowerText.kindOf(handler.value)\n"))
edit(M + "gameplay/actions/CardPlay.scala", (
    "  /** The suit whose Homeland `site` is, from its `site.<id>.homeland-<suit>`\n"
    "    * handler (CR p. 31). */\n"
    "  def homelandSuit(catalog: ExecutableCatalog, site: SiteId): Option[Suit] =\n"
    "    catalog.site(site).toVector.flatMap(_.handlers).flatMap(handler =>\n"
    "      handler.split('.').lastOption.filter(_.startsWith(\"homeland-\"))\n"
    "        .flatMap(kind => Suit.fromKey(kind.stripPrefix(\"homeland-\"))))\n"
    "      .headOption\n",
    "  /** The suit whose Homeland `site` is, as the site prints it (CR p. 31). */\n"
    "  def homelandSuit(catalog: ExecutableCatalog, site: SiteId): Option[Suit] =\n"
    "    catalog.site(site).flatMap(_.homeland)\n"))
edit(M + "gameplay/setup/GameStartRules.scala", (
    "        homelandSuit(catalog.site(stored.site).get.handlers) match\n",
    "        catalog.site(stored.site).get.homeland match\n"), (
    "  private def homelandSuit(handlers: Vector[String]): Option[Suit] =\n"
    "    handlers.collectFirst {\n"
    "      case handler if handler.contains(\".homeland-\") =>\n"
    "        handler.substring(handler.indexOf(\".homeland-\") + 10)\n"
    "    }.flatMap(Suit.fromKey)\n\n", ""))
edit(M + "application/FirstGameChronicleGenerator.scala", (
    "      homelandSuit(catalog, siteId) match {\n",
    "      catalog.site(siteId).get.homeland match {\n"), (
    "  private def homelandSuit(catalog: ExecutableCatalog, siteId: SiteId): Option[Suit] =\n"
    "    catalog.site(siteId).get.handlers.collectFirst {\n"
    "      case handler if handler.contains(\".homeland-\") =>\n"
    "        handler.substring(handler.indexOf(\".homeland-\") + 10)\n"
    "    }.flatMap(Suit.fromKey)\n\n", ""))

# Tests. A `Vector[PowerId]` still accepts `contains("…")` and silently finds
# nothing, so every site-handler lookup is rewritten here, by file.
edit(T + "testkit/TestCards.scala", (
    "      handlers: Vector[String] = base.handlers): Site =\n"
    "    new Site(id, base.name, base.defense, base.capacity, base.relicSlots,\n"
    "      base.recoverDifficulty, base.startingResources, forgeRequirements,\n"
    "      homeland, handlers) {}\n",
    "      handlers: Vector[PowerId] = base.handlers): Site =\n"
    "    val printed = handlers\n"
    "    new Site(id, base.name, base.defense, base.capacity, base.relicSlots,\n"
    "      base.recoverDifficulty, base.startingResources, forgeRequirements,\n"
    "      homeland) { val handlers: Vector[PowerId] = printed }\n"))
edit(T + "gameplay/cards/CardCatalogSuite.scala", (
    "    val ids = powers.map(_.id.value) ++ catalog.sites.flatMap(_.handlers)\n",
    "    val ids = powers.map(_.id.value) ++\n"
    "      catalog.sites.flatMap(_.handlers).map(_.value)\n"), (
    "      catalog.sites.flatMap(_.handlers).map(PowerId(_))).toSet\n",
    "      catalog.sites.flatMap(_.handlers)).toSet\n"))
edit(T + "catalog/CatalogHandlerInventorySuite.scala", (
    "      catalog.sites.flatMap(_.handlers) ++",
    "      catalog.sites.flatMap(_.handlers.map(_.value)) ++"))
for f in ("gameplay/TravelProcedureSuite.scala",
          "gameplay/powers/travel/TravelFixture.scala",
          "gameplay/TravelSitePowersSuite.scala"):
    s = open(T + f).read()
    new = s.replace("_.handlers.exists(_.endsWith(",
                    "_.handlers.exists(_.value.endsWith(")
    assert new != s, f
    open(T + f, "w").write(new)
edit(T + "gameplay/TravelSitePowersSuite.scala", (
    "    PowerId(catalog.sites.find(_.id == siteId).fold(\n"
    "      fail(s\"missing $siteId\"))(_.handlers.find(_.endsWith(suffix)).get))\n",
    "    catalog.sites.find(_.id == siteId).fold(\n"
    "      fail(s\"missing $siteId\"))(_.handlers.find(_.value.endsWith(suffix)).get)\n"), (
    "handlers = Vector(fixturePower.value))",
    "handlers = Vector(fixturePower))"))
edit(T + "gameplay/WakeAutoEndSuite.scala", (
    '_.handlers.exists(_.contains("river"))',
    '_.handlers.exists(_.value.contains("river"))'))
edit(T + "gameplay/CampaignPowersSuite.scala", (
    "  private val pass = catalog.sites.find(_.handlers.contains(\n"
    "    \"site.narrow-pass.pass\")).get.id\n",
    "  private val pass = catalog.sites.find(_.handlers.contains(\n"
    "    PowerId(\"site.narrow-pass.pass\"))).get.id\n"))
edit(T + "gameplay/RuleSourceIndexSuite.scala", (
    "    assertEquals(printed.handlerIds,\n",
    "    assertEquals(printed.powerIds,\n"))
edit(T + "application/ForgeWalkerFixture.scala", (
    '      !site.handlers.exists(_.contains(".homeland-"))).get.id',
    '      site.homeland.isEmpty).get.id'))
edit(T + "gameplay/setup/GameStartRulesSuite.scala", (
    "      .flatMap(site => catalog.sites.find(_.id == site).get.handlers.collectFirst {\n"
    "        case handler if handler.contains(\".homeland-\") =>\n"
    "          val suit = Suit.fromKey(\n"
    "            handler.substring(handler.indexOf(\".homeland-\") + 10)).get\n",
    "      .flatMap(site => catalog.sites.find(_.id == site).get.homeland.map {\n"
    "        suit =>\n"))
edit(T + "gameplay/setup/FirstGameSetupFixture.scala", (
    "    catalog.sites.find(_.id == siteId).get.handlers.collectFirst:\n"
    "      case handler if handler.contains(\".homeland-\") =>\n"
    "        val suit = Suit.fromKey(\n"
    "          handler.substring(handler.indexOf(\".homeland-\") + 10)).get\n"
    "        val edifice = catalog.edifices.find(_.suit == suit).get\n"
    "        siteId -> EdificeId(edifice.id.value)\n",
    "    catalog.sites.find(_.id == siteId).get.homeland.map: suit =>\n"
    "      val edifice = catalog.edifices.find(_.suit == suit).get\n"
    "      siteId -> EdificeId(edifice.id.value)\n"))
edit(T + "application/FirstGameChronicleGeneratorSuite.scala", (
    "      val handlerSuit = site.handlers.collectFirst:\n"
    "        case handler if handler.contains(\".homeland-\") =>\n"
    "          Suit.fromKey(handler.substring(handler.indexOf(\".homeland-\") + 10)).get\n"
    "      handlerSuit match\n",
    "      site.homeland match\n"))
print("site handlers are power ids; Homeland reads the printed suit")
```

- [ ] **Step 4: Add the agreement check**

The handler and the `homeland` field now both declare a Homeland, so a
check keeps them in step. In
`src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala`, insert
before `test("printed numbers are non-negative"):`:

```scala
  test("a site prints a Homeland suit exactly when a handler names it"):
    catalog.sites.foreach { site =>
      val named = site.handlers.map(_.value).collect {
        case id if id.contains(".homeland-") =>
          id.substring(id.indexOf(".homeland-") + ".homeland-".length)
      }
      assertEquals(named, site.homeland.toVector.map(_.key), site.id)
    }

```

- [ ] **Step 5: Review the result**

- `HoldingSites.scala`: each of the 24 sites ends with `homeland = …):`,
  one `val <name> = PowerId("…")` per handler, and
  `val handlers: Vector[PowerId] = Vector(…)`. The six Homeland sites name
  theirs `homelandPower`.
- `grep -rn 'homeland-' src/main` shows only `PowerId("site.….homeland-…")`
  declarations, `RuleNotes.homelandDiscard` and `SitePowerText` (display
  text); no gameplay code parses a handler.
- `grep -rn 'handlers.contains("' src/test` shows only denizen and relic
  lookups (`CampaignProcedureSuite`, `MinorActionsSuite`,
  `RuleResolutionSuite`, `GameApplicationServiceSuite`), which stay strings.

- [ ] **Step 6: Run the suite to see it pass**

Run: `./sbtw test`
Expected: PASS, 2539 tests (2538 before, plus the agreement check).

- [ ] **Step 7: Run the gates**

Run: `./sbtw frontend/test`, `python3 scripts/check-architecture.py` and
`python3 scripts/check-markdown-links.py`.
Expected: all pass (the frontend suite does not compile `catalog/`).

- [ ] **Step 8: Commit**

```bash
git add -u src/main/scala src/test/scala
git commit -m "refactor(cards): sites name their handlers as power ids

Site.handlers is a Vector[PowerId] each site object fills from named
members, and Homeland readers use the printed homeland suit instead of
parsing a handler string.

Co-Authored-By: <the committing model's own line>"
```

---

### Task 2: Site powers name their sites

**Files:** `HoldingSites.scala`, `RiverSitePower.scala`,
`TravelSitePowers.scala`, `PhasePowerCatalog.scala`,
`WalkerPowerCatalog.scala`, `CatalogModel.scala`, `NewFoundations.scala`
(imports), `CardCatalogSuite.scala`, `ExecutableCatalogSuite.scala`,
`RiverFixture.scala`, `RiverSitePowerSuite.scala`,
`TravelSitePowersSuite.scala`, `CampaignPowersSuite.scala`,
`docs/ROADMAP.md`.

**Interfaces:**
- Consumes: Task 1's named site members (`AncientCitySite.river`,
  `HeadwatersSite.river`, `HeadwatersSite.mountain`, `RiverbankSite.river`,
  `TidalMarshesSite.river`, `TidalMarshesSite.coast`,
  `BrokenPeaksSite.mountain`, `DesolateShoreSite.coast`,
  `FairIsleSite.coast`, `FairIsleSite.island`, `GreenShoreSite.coast`,
  `HiddenPlaceSite.mountain`, `MinesSite.mountain`, `NarrowPassSite.pass`,
  `RockyCoastSite.coast`, `SunkenIslesSite.coast`,
  `SunkenIslesSite.island`).
- Produces: `RiverSitePower(id: PowerId, site: SiteId)`,
  `RiverSitePower.all: Vector[RiverSitePower]`,
  `RiverSitePower.isRiver(site: SiteId): Boolean`,
  `TravelSitePowers.all: Vector[ContributingPower]`; the four River sites
  in package `oathdigital.gameplay.powers.wake`, the other nine in
  `oathdigital.gameplay.powers.travel`. `ExecutableCatalog.siteWithHandler`
  is gone.

- [ ] **Step 1: Extend the placement and registration tests**

In `src/test/scala/oathdigital/gameplay/cards/CardCatalogSuite.scala`, in
the test "a card leaves the holding files once it is implemented", replace:

```scala
        catalog.edifices.collect { case card if held(card) ==
            implemented(card.id, card.intact.powers ++ card.ruined.powers) =>
          card.intact.name }
```

with:

```scala
        catalog.edifices.collect { case card if held(card) ==
            implemented(card.id, card.intact.powers ++ card.ruined.powers) =>
          card.intact.name } ++
        catalog.sites.collect { case site
          if held(site) == site.handlers.exists(registered) => site.name }
```

In the test "card powers are registered whichever cards a catalog lists",
replace:

```scala
        .filter(id => Vector("denizen.", "relic.", "edifice.")
          .exists(id.value.startsWith)).toSet
    assertEquals(cardPowers(catalog.copy(denizens = Vector.empty,
      relics = Vector.empty, edifices = Vector.empty)), cardPowers(catalog))
```

with:

```scala
        .filter(id => Vector("denizen.", "relic.", "edifice.", "site.")
          .exists(id.value.startsWith)).toSet
    assertEquals(cardPowers(catalog.copy(denizens = Vector.empty,
      relics = Vector.empty, edifices = Vector.empty, sites = Vector.empty)),
      cardPowers(catalog))
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.cards.CardCatalogSuite"`
Expected: FAIL, 2 of 12. "a card leaves the holding files once it is
implemented" lists the 13 implemented sites, from "Ancient City" to "Tidal
Marshes". "card powers are registered whichever cards a catalog lists"
misses the River and Travel powers for the catalog without sites.

- [ ] **Step 3: Save and run the site script**

Save `s4_sites.py` (below) into `<scratchpad>/s4/`, next to
`s4_imports.py`, then run:

```bash
python3 <scratchpad>/s4/s4_sites.py <worktree root>
```

Expected output: `moved 13 sites; River and Travel powers name their
sites`.

```python
"""Card classes slice 4, Task 2: implemented sites move beside their powers,
and the River and Travel site powers name their sites.

Usage: python3 s4_sites.py <worktree root>
"""
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import s4_imports as imports

W = sys.argv[1].rstrip("/") + "/"
M = W + "src/main/scala/oathdigital/"
T = W + "src/test/scala/oathdigital/"
RIVER = "gameplay/powers/wake/RiverSitePower.scala"
TRAVEL = "gameplay/powers/travel/TravelSitePowers.scala"
# A site with a River power sits beside the River, even if it also has a
# Travel power (Headwaters, Tidal Marshes).
DEST = {
    "AncientCitySite": RIVER,
    "HeadwatersSite": RIVER,
    "RiverbankSite": RIVER,
    "TidalMarshesSite": RIVER,
    "BrokenPeaksSite": TRAVEL,
    "DesolateShoreSite": TRAVEL,
    "FairIsleSite": TRAVEL,
    "GreenShoreSite": TRAVEL,
    "HiddenPlaceSite": TRAVEL,
    "MinesSite": TRAVEL,
    "NarrowPassSite": TRAVEL,
    "RockyCoastSite": TRAVEL,
    "SunkenIslesSite": TRAVEL,
}
HOLDING = "oathdigital.catalog.holding"


def edit(path, *pairs):
    s = open(path).read()
    for old, new in pairs:
        assert old in s, (path, old)
        s = s.replace(old, new)
    open(path, "w").write(s)


# 1. Move the site objects.
f = M + "catalog/holding/HoldingSites.scala"
parts = re.split(r"\n(?=object \w+Site extends Site\()", open(f).read())
kept, blocks = [parts[0]], {}
for part in parts[1:]:
    site = re.match(r"object (\w+Site) ", part).group(1)
    if site in DEST:
        blocks[site] = part.rstrip("\n")
    else:
        kept.append(part)
assert set(blocks) == set(DEST), set(DEST) - set(blocks)
s = "\n".join(kept)
open(f, "w").write(s if s.endswith("\n") else s + "\n")

package_of = {}
for rel in (RIVER, TRAVEL):
    dest = M + rel
    s = open(dest).read()
    lines = s.split("\n")
    last = max(i for i, l in enumerate(lines) if l.startswith("import "))
    j = last + 1
    while j < len(lines) and lines[j].startswith("  "):
        j += 1
    for site in sorted(DEST):
        if DEST[site] == rel:
            lines[j:j] = [""] + blocks[site].split("\n")
            j += 1 + blocks[site].count("\n") + 1
    s = "\n".join(lines)
    s = imports.add(s, "oathdigital.catalog", {"Site"})
    s = imports.add(s, "oathdigital.model", {"PowerId", "SiteId", "Tokens"})
    pkg = re.search(r"^package (\S+)", s, re.M).group(1)
    for site in DEST:
        if DEST[site] == rel:
            package_of[site] = pkg
    open(dest, "w").write(s)

# 2. The River power names its sites.
edit(M + RIVER, (
    "final case class RiverSitePower(id: PowerId, site: SiteId,\n"
    "    catalog: ExecutableCatalog) extends PhasePower:\n",
    "final case class RiverSitePower(id: PowerId, site: SiteId)\n"
    "    extends PhasePower:\n"), (
    "      other != site && isRiver(catalog, other))\n",
    "      other != site && isRiver(other))\n"), (
    """  /** The reviewed River handlers, one power each. */
  val supported: Vector[PowerId] = Vector("site.ancient-city.river",
    "site.headwaters.river", "site.riverbank.river",
    "site.tidal-marshes.river").map(PowerId(_))

  def isRiver(catalog: ExecutableCatalog, site: SiteId): Boolean =
    catalog.site(site).exists(_.handlers.exists(_.value.endsWith(".river")))

  /** A River power for each reviewed handler `catalog` has. */
  def forCatalog(catalog: ExecutableCatalog): Vector[RiverSitePower] =
    supported.flatMap(id => catalog.siteWithHandler(id).map(definition =>
      RiverSitePower(id, definition.id, catalog)))
""",
    """  /** One River power for each River site. */
  val all: Vector[RiverSitePower] = Vector(
    RiverSitePower(AncientCitySite.river, AncientCitySite.id),
    RiverSitePower(HeadwatersSite.river, HeadwatersSite.id),
    RiverSitePower(RiverbankSite.river, RiverbankSite.id),
    RiverSitePower(TidalMarshesSite.river, TidalMarshesSite.id))

  def isRiver(site: SiteId): Boolean = all.exists(_.site == site)
"""))
s = open(M + RIVER).read()
s = imports.remove(s, "oathdigital.catalog", {"ExecutableCatalog"})
open(M + RIVER, "w").write(s)

# 3. The Travel terrain powers name their sites.
p = M + TRAVEL
s = open(p).read()
start = s.index("/** Catalog-bound site contributions")
assert s.rstrip().endswith(
    "NarrowPassSitePower(id, site, coastSites, coastOrIslandSites)")
s = s[:start] + '''/** The sites' Travel terrain contributions. Static site topology lives on
  * the power objects; command state supplies only the current route through
  * PowerCtx.operation.
  */
object TravelSitePowers:
  private enum Terrain { case Mountain, Island, Coast, NarrowPass }
  import Terrain._
  private final case class Supported(id: PowerId, site: SiteId,
      terrain: Terrain)

  /** Explicit reviewed Travel handlers, in power id order. A power comes
    * only from this list, never from a `*.coast`-looking handler id.
    */
  private val supported = Vector(
    Supported(BrokenPeaksSite.mountain, BrokenPeaksSite.id, Mountain),
    Supported(DesolateShoreSite.coast, DesolateShoreSite.id, Coast),
    Supported(FairIsleSite.coast, FairIsleSite.id, Coast),
    Supported(FairIsleSite.island, FairIsleSite.id, Island),
    Supported(GreenShoreSite.coast, GreenShoreSite.id, Coast),
    Supported(HeadwatersSite.mountain, HeadwatersSite.id, Mountain),
    Supported(HiddenPlaceSite.mountain, HiddenPlaceSite.id, Mountain),
    Supported(MinesSite.mountain, MinesSite.id, Mountain),
    Supported(NarrowPassSite.pass, NarrowPassSite.id, NarrowPass),
    Supported(RockyCoastSite.coast, RockyCoastSite.id, Coast),
    Supported(SunkenIslesSite.coast, SunkenIslesSite.id, Coast),
    Supported(SunkenIslesSite.island, SunkenIslesSite.id, Island),
    Supported(TidalMarshesSite.coast, TidalMarshesSite.id, Coast))
  private val coastSites = supported.collect {
    case Supported(_, site, Coast) => site
  }.toSet
  private val coastOrIslandSites = supported.collect {
    case Supported(_, site, Coast | Island) => site
  }.toSet

  val all: Vector[ContributingPower] = supported.map:
    case Supported(id, site, Mountain) => MountainSitePower(id, site)
    case Supported(id, site, Island) => IslandSitePower(id, site)
    case Supported(id, site, Coast) =>
      CoastSitePower(id, site, coastOrIslandSites)
    case Supported(id, site, NarrowPass) =>
      NarrowPassSitePower(id, site, coastSites, coastOrIslandSites)
'''
s = imports.remove(s, "oathdigital.catalog", {"ExecutableCatalog"})
s = imports.add(s, "oathdigital.gameplay.powers.wake",
                {"HeadwatersSite", "TidalMarshesSite"})
open(p, "w").write(s)

# 4. Registration and the catalog index.
edit(M + "gameplay/powers/PhasePowerCatalog.scala", (
    "      RiverSitePower.forCatalog(catalog))", "      RiverSitePower.all)"))
edit(M + "gameplay/powers/WalkerPowerCatalog.scala", (
    "      TravelSitePowers.forCatalog(catalog) ++",
    "      TravelSitePowers.all ++"))
edit(M + "catalog/CatalogModel.scala", (
    "  def siteWithHandler(handler: PowerId): Option[Site] =\n"
    "    siteByHandler.get(handler)\n", ""), (
    "  private lazy val siteByHandler =\n"
    "    firstBy(sites.flatMap(s => s.handlers.map(_ -> s)))\n", ""))

# 5. Imports of the moved sites.
def code_without_imports(s):
    return re.sub(r"^import [^\n]*\n(?:  [^\n]*\n)*", "", s, flags=re.M)


files = [f for f in glob.glob(M + "**/*.scala", recursive=True) +
         glob.glob(T + "**/*.scala", recursive=True)
         if "/catalog/holding/" not in f]
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
    for site, pkg in package_of.items():
        if f"{HOLDING}.{site}" in code_without_imports(new):
            new = new.replace(f"{HOLDING}.{site}", site)
            if pkg != own:
                new = imports.add(new, pkg, {site})
    if re.search(r"^import " + re.escape(HOLDING) + r"\._", new, re.M):
        for pkg in sorted(set(package_of.values())):
            new = imports.add(new, pkg, {c for c, p in package_of.items()
                                         if p == pkg})
    if new != s:
        open(f, "w").write(new)

# 6. Tests.
edit(T + "catalog/ExecutableCatalogSuite.scala", (
    "    assertEquals(\n"
    "      catalog.site(SiteId(\"site:deep-woods\")).map(_.id),\n"
    "      catalog.siteWithHandler(PowerId(\"site.deep-woods.homeland-beast\"))\n"
    "        .map(_.id))\n", ""))
edit(T + "gameplay/powers/wake/RiverFixture.scala", (
    "  lazy val river: RiverSitePower = RiverSitePower.forCatalog(catalog)\n",
    "  lazy val river: RiverSitePower = RiverSitePower.all\n"), (
    "RiverSitePower.isRiver(catalog, site)", "RiverSitePower.isRiver(site)"), (
    "import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog\n", ""))
edit(T + "gameplay/powers/wake/RiverSitePowerSuite.scala", (
    "RiverSitePower.isRiver(catalog, site)", "RiverSitePower.isRiver(site)"))
edit(T + "gameplay/TravelSitePowersSuite.scala", (
    "  private val powers = TravelSitePowers.forCatalog(catalog)\n",
    "  private val powers = TravelSitePowers.all\n"), (
    "    assert(!TravelSitePowers.forCatalog(augmented).map(_.id).contains(fixturePower))\n",
    "    assert(!WalkerPowerCatalog.default(augmented).powers.map(_.id)\n"
    "      .contains(fixturePower))\n"), (
    "import oathdigital.gameplay.powers.travel.TravelSitePowers\n",
    "import oathdigital.gameplay.powers.WalkerPowerCatalog\n"
    "import oathdigital.gameplay.powers.travel.TravelSitePowers\n"))
edit(T + "gameplay/CampaignPowersSuite.scala", (
    "  private val passPower = TravelSitePowers.forCatalog(catalog).collectFirst {\n",
    "  private val passPower = TravelSitePowers.all.collectFirst {\n"))
print(f"moved {len(blocks)} sites; River and Travel powers name their sites")
```

- [ ] **Step 4: Review the move**

- `HoldingSites.scala` keeps 11 sites: the 6 Homelands and the 5 Plains.
- `RiverSitePower.scala` opens with Ancient City, Headwaters, Riverbank and
  Tidal Marshes; `TravelSitePowers.scala` opens with the other 9, and its
  `supported` list keeps the old power id order (Broken Peaks mountain
  first, Tidal Marshes coast last).
- Neither power file imports `ExecutableCatalog` any more, and
  `grep -rn siteWithHandler src` finds nothing.
- `NewFoundations.scala` changes only its imports; its `sites` vector is
  unchanged.

- [ ] **Step 5: Run the suite to see it pass**

Run: `./sbtw test`
Expected: PASS, 2539 tests.

- [ ] **Step 6: Tick the ROADMAP**

In `docs/ROADMAP.md`, replace the line
`- [ ] **Slice 4 - sites and legacies.**` with:

```markdown
- [x] **Slice 4 - sites and legacies.** Site handlers are power ids each
  site object names, Homeland rules read the printed suit, and the 13 sites
  with River or Travel powers sit beside those powers, which name their
  sites. Legacies keep their catalog lookup: none is implemented yet.
```

- [ ] **Step 7: Run the gates**

Run: `./sbtw frontend/test`, `python3 scripts/check-architecture.py` and
`python3 scripts/check-markdown-links.py`.
Expected: all pass.

- [ ] **Step 8: Commit**

```bash
git add -u src/main/scala src/test/scala docs/ROADMAP.md
git commit -m "refactor(powers): River and Travel site powers name their sites

The 13 sites with an implemented River or Travel power move beside it.
Both powers are registered whichever sites a catalog lists, and
ExecutableCatalog.siteWithHandler is gone.

Co-Authored-By: <the committing model's own line>"
```
