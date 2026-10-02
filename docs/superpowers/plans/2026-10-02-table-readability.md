# Table Readability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A player reads the table without leaning in: a shorter player strip, site powers on the map, larger glyphs with distinct secret and defense-die shapes, a discard pile that opens as a card list, and a Supply figure that explains the Rest it will return.

**Architecture:** Two server-side projection additions (a region's discard cards, a board's banked warbands and the Supply band table) land first, each with its codec and tests. The frontend then wires two clicks to the existing card-inspection overlay (`CardInspection.openCards` for the pile, `CardInspection.openText` for Supply), with the Supply text built by a pure `SupplyAtRest` object. The glyph shapes change in `TokenSprite`. Everything visual (strip row, site box and footer, glyph sizes) is CSS in `frontend/styles.css`, done as Impeccable `layout` and `polish` passes and verified in the browser over a scratch database, since no suite reads the stylesheet. DESIGN.md is amended last and the Impeccable sidecar refreshed.

**Tech Stack:** Scala 3, Scala.js, ujson with hand-written `exact` codecs, munit (jsdom for the frontend). Build through `./sbtw`. Impeccable (`~/.claude/skills/impeccable`) for the CSS passes and the design detector.

**Spec:** `docs/superpowers/specs/2026-10-02-table-readability-design.md`. Read it whole; it is short. The plan argues from its Layout, Glyphs, Discard pile overlay, Supply overlay and Testing sections.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` on both projects. An unused import or private member fails the build.
- Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- `shared/src/main` may import only `oathdigital.protocol`. `application` must not import `persistence`, `serialization` or `server`.
- Never touch the live database `var/oathdigital`. Browser checks use a scratch copy only, on port 8081, started through Bash (not `preview_start`).
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Never commit `node_modules`, `.tooling` or `target`.
- Work in a git worktree: `git worktree add -b table-readability .claude/worktrees/table-readability main`, then `ln -s "$(pwd)/.tooling" .claude/worktrees/table-readability/.tooling`, then `EnterWorktree`. Before merging from the main checkout, `ExitWorktree(keep)`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- Codec rule: every new field is written always and decoded with a default, so a payload from before this plan still decodes; every `exact` field set gains the new name.
- Visibility rule: the one function `GamePresentationProjector.identifiesCard` decides whether a viewer sees a card's face. No other test of knowledge.
- UI work follows Impeccable. Run `~/.claude/skills/impeccable/scripts/impeccable context` once per session with cwd at the worktree root. Read `~/.claude/skills/impeccable/reference/craft-floor.md` immediately before any CSS edit. Verify in one batched pass plus at most one confirming pass. Impeccable's worktree rule is satisfied by this plan's worktree.
- DESIGN.md forbids `transition`, `animation` and resting shadows. Functional text holds the 11px floor in the panes; on the map, text may shrink with the map (spec, "Ruling on map text").
- Baselines at `c07b5509`: 480 frontend tests. Record the server count from your first full run of `./sbtw test` and use it afterwards.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

### Decisions this plan makes that the spec left open

1. **`discardCount` and `discardTopCardKind` stay constructor fields.** The spec says they are derived. Removing them would touch every `GameRegion(...)` call in the frontend tests for nothing; instead the projector computes both from `discardCards`, so the wire carries three consistent fields and the DTO shape is unchanged for old callers.
2. **The current band is marked with the suffix "(now)"**, as in "4–8 warbands: Supply 5 (now)". The overlay shows plain text lines; a suffix survives a screen reader.
3. **CSS has no jsdom test.** The spec names `SiteBoxLayoutSuite` and `SuitGlyphSuite` for the footer and glyph sizes; no suite reads `styles.css`, so those checks are browser measurements in Task 9. The jsdom suites cover DOM changes only.
4. **`MapViewStateSuite` needs no re-check.** The compact name's width is the card's width to its border; a larger corner glyph sits above the name (padding-top), not beside it.
5. **The strip row is `minmax(150px, 17%)` with cards at 0.7em, not the spec's `minmax(140px, 16%)`.** The arithmetic: a 16% row at 900px is 144px, less the 39px pane header is 105px of content; the pane content pads 6px, the board 6px, each top and bottom, and the card row adds 3px, so the card may be at most 81px tall. At 0.72em of 0.82rem a 13ex card is about 86px. At 0.7em it is about 84px, which a 17% row (153px, 114px of content) holds at 900px and a 150px row (111px of content) holds at 768px. Task 7 measures and may step once.

### File structure

- `shared/.../projection/WorldProjectionDtos.scala` — `SetupRegionProjection.discardCards`.
- `shared/.../projection/ActionProjectionDtos.scala` — `PlayerBoardProjection` bag fields; new `SupplyBandProjection`.
- `shared/.../projection/GameProjectionDto.scala` — `supplyBands`.
- `shared/.../projection/WorldProjectionCodec.scala`, `GameProjectionCodec.scala` — the codecs.
- `src/main/scala/oathdigital/gameplay/powers/PlayerFacts.scala` — `onBoards`, `atSites`, `banked`.
- `src/main/scala/oathdigital/gameplay/phases/rest/FinishRestProcedure.scala` — reads `PlayerFacts.banked`.
- `src/main/scala/oathdigital/application/GamePresentationProjector.scala` — discard cards, bag fields.
- `src/main/scala/oathdigital/application/GameProjection.scala` — `supplyBands`.
- `frontend/.../SupplyAtRest.scala` — new, pure: the overlay's lines.
- `frontend/.../ServerUiSupport.scala` — `pileDisplay` as a button.
- `frontend/.../WorldBoardRenderer.scala` — pile and Supply clicks.
- `frontend/.../TokenSprite.scala` — two paths.
- `frontend/.../frontend.scala` — `SupplyBand` alias.
- `frontend/styles.css` — strip, site, glyph sizes, hover.
- `DESIGN.md`, `.impeccable/design.json`, `docs/ROADMAP.md`.

---

### Task 1: Project a discard pile's cards

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionDtos.scala:62-67`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionCodec.scala:143-153`
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala:60-96`
- Test: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`
- Test: `src/test/scala/oathdigital/application/GamePresentationProjectorDiscardSuite.scala` (new)

**Interfaces:**
- Produces: `SetupRegionProjection.discardCards: Vector[CardDetailsProjection]`, top of the pile first. A card the viewer may identify is `cardDetails(id, None, hidden = false)`; any other is `hiddenCard(kind)` (`cardId == "hidden"`, `hidden == true`).

- [ ] **Step 1: Write the failing codec test**

In `ProjectionProtocolSuite`, after the test that uses `restSupplyGain = None` (about line 91), add:

```scala
  test("a region's discard cards round-trip, hidden and known alike"):
    val withPile = projection.copy(world = projection.world.map(region =>
      region.copy(discardCards = Vector(known, hidden), discardCount = 2,
        discardTopCardKind = Some("denizen"))))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(withPile)),
      Right(withPile))

  test("a payload without discard cards decodes to an empty pile"):
    val raw = ujson.read(GameProjectionCodec.encode(projection))
    raw("world").arr.foreach(region => region.obj.remove("discardCards"))
    val decoded = GameProjectionCodec.decode(ujson.write(raw))
    assertEquals(decoded.map(_.world.map(_.discardCards)),
      Right(projection.world.map(_ => Vector.empty)))
```

`known` and `hidden` are the suite's existing `CardDetailsProjection` vals.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: compile error, `discardCards` is not a member of `SetupRegionProjection`.

- [ ] **Step 3: Add the field and the codec**

In `WorldProjectionDtos.scala`, `SetupRegionProjection` becomes:

```scala
final case class SetupRegionProjection(
    regionId: String,
    sites: Vector[SetupSiteProjection],
    discardCount: Int = 0,
    discardTopCardKind: Option[String] = None,
    /** The pile top first. A card the viewer may not identify is the
      * projector's `hiddenCard`, so the pile's size is always public and
      * its faces are not. */
    discardCards: Vector[CardDetailsProjection] = Vector.empty
)
```

In `WorldProjectionCodec.scala`, replace `encodeRegion` and `decodeRegion`:

```scala
  def encodeRegion(value: SetupRegionProjection): ujson.Value = ujson.Obj(
    "regionId" -> value.regionId, "discardCount" -> value.discardCount,
    "discardTopCardKind" -> stringOption(value.discardTopCardKind),
    "discardCards" -> encoded(value.discardCards)(encodeCard),
    "sites" -> encoded(value.sites)(encodeSite))
  def decodeRegion(raw: ujson.Value, path: String): Result[SetupRegionProjection] = for
    value <- obj(raw, path)
    _ <- exact(value, Set("regionId", "discardCount", "discardTopCardKind",
      "discardCards", "sites"), path)
    region <- string(value, "regionId", path); count <- intOr(value, "discardCount", path, 0)
    top <- optionalAbsent(value, "discardTopCardKind", path)(string)
    cardRaws <- default(value, "discardCards", path, Vector.empty[ujson.Value])(array)
    cards <- traverse(cardRaws, s"$path.discardCards")(decodeCard)
    raws <- array(value, "sites", path); sites <- traverse(raws, s"$path.sites")(decodeSite)
  yield SetupRegionProjection(region, sites, count, top, cards)
```

- [ ] **Step 4: Run the codec test**

Run: `./sbtw "testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: PASS, including the suite's existing unexpected-field rejection tests.

- [ ] **Step 5: Write the failing projector test**

Create `src/test/scala/oathdigital/application/GamePresentationProjectorDiscardSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** A discard pile's cards, top first, through the one visibility rule. */
class GamePresentationProjectorDiscardSuite extends munit.FunSuite:
  private val projector = new GamePresentationProjector(catalog)

  private def cradle(ready: ReadyGame, viewer: PlayerId) =
    projector.readyWorld(ready, Some(viewer)).find(_.regionId == "cradle")
      .getOrElse(fail("no cradle"))

  // Appended in this order, so Scryer is on top.
  private val table = Table.start.discarded(Region.Cradle, "Magician's Code", "Scryer")

  test("a discard pile projects its cards top first, as backs"):
    val region = cradle(table.ready, p2)
    assertEquals(region.discardCards.size, region.discardCount)
    assertEquals(region.discardCards.take(2).map(_.cardId), Vector("hidden", "hidden"))
    assertEquals(region.discardCards.take(2).map(_.name),
      Vector("Facedown denizen", "Facedown denizen"))
    assertEquals(region.discardTopCardKind, Some("denizen"))

  test("a peeked card shows its face to the peeker and a back to others"):
    val scryer = CatalogNames.worldCard("Scryer")
    val peeked = table.update(ready => ready.copy(knowledge = ready.knowledge.copy(
      advisers = ready.knowledge.advisers.updated(p1, Vector(scryer))))).ready
    assertEquals(cradle(peeked, p1).discardCards.take(2).map(_.name),
      Vector("Scryer", "Facedown denizen"))
    assertEquals(cradle(peeked, p1).discardCards.head.hidden, false)
    assertEquals(cradle(peeked, p2).discardCards.take(2).map(_.cardId),
      Vector("hidden", "hidden"))
```

If `CatalogNames` lives elsewhere than `oathdigital.testkit`, take the import from `Table.scala`'s own import of it.

- [ ] **Step 6: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.application.GamePresentationProjectorDiscardSuite"`
Expected: FAIL. `discardCards` is empty, so `take(2)` gives `Vector()`.

- [ ] **Step 7: Project the cards**

In `GamePresentationProjector.scala`, the private `region` function takes the region and builds the cards. Replace lines 89-96 with:

```scala
  private def region(id: String, where: Region, sites: Vector[SiteId],
      states: Map[SiteId, SiteState] = Map.empty,
      discard: Vector[CardId] = Vector.empty,
      ready: Option[ReadyGame] = None,
      viewer: Option[PlayerId] = None): SetupRegionProjection =
    // The pile is stored top last; the client reads it top first. A card is
    // a face only where the viewer identifies it in THIS pile (a peek), so
    // nothing a player once saw elsewhere leaks through a discard.
    val cards = discard.reverse.map { card =>
      if ready.exists(game => identifiesCard(game, viewer, card, None,
          CardContainer.RegionalDiscard(where)))
      then cardDetails(card, None, hidden = false)
      else hiddenCard(cardKind(card))
    }
    SetupRegionProjection(id, sites.map(site =>
      siteProjection(site, states.get(site), ready, viewer)),
      cards.size, cards.headOption.map(_.cardKind), cards)
```

Then pass the region at the three call sites (lines 62-65, 70-76 and 80-86): `region("cradle", Region.Cradle, ...)`, `region("provinces", Region.Provinces, ...)`, `region("hinterland", Region.Hinterland, ...)`, keeping every other argument as it is.

- [ ] **Step 8: Run both suites and the redaction suite**

Run: `./sbtw "testOnly oathdigital.application.GamePresentationProjectorDiscardSuite oathdigital.application.GamePresentationProjectorAdviserRedactionSuite oathdigital.protocol.ProjectionProtocolSuite"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionCodec.scala src/main/scala/oathdigital/application/GamePresentationProjector.scala shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala src/test/scala/oathdigital/application/GamePresentationProjectorDiscardSuite.scala
git commit -m "feat(projection): carry a discard pile's cards, top first"
```

---

### Task 2: Project banked warbands and the Supply bands

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/PlayerFacts.scala:19-29`
- Modify: `src/main/scala/oathdigital/gameplay/phases/rest/FinishRestProcedure.scala:73-89`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:264-285`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala:33-35`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionCodec.scala:155-191`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/GameProjectionCodec.scala:15-23, 63-75, 104-137`
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala:157-187`
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala:108-114`
- Test: `src/test/scala/oathdigital/application/SupplyProjectionSuite.scala`
- Test: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`

**Interfaces:**
- Produces: `PlayerBoardProjection.bankedWarbands`, `printedWarbands`, `siteWarbands`, `restSupplyBase: Int`; `GameProjection.supplyBands: Vector[SupplyBandProjection]` with `SupplyBandProjection(from: Int, to: Option[Int], supply: Int)`, in the engine's band order (9+, 4–8, 0–3); `PlayerFacts.onBoards(ready, kind)`, `PlayerFacts.atSites(ready, kind)`, `PlayerFacts.banked(ready, kind)`.

- [ ] **Step 1: Write the failing DTO round-trip test**

In `ProjectionProtocolSuite`, add:

```scala
  test("a board's bag figures and the Supply bands round-trip"):
    val withBag = projection.copy(
      playerBoards = projection.playerBoards.map(_.copy(bankedWarbands = 5,
        printedWarbands = 14, siteWarbands = 3, restSupplyBase = 5)),
      supplyBands = Vector(SupplyBandProjection(9, None, 6),
        SupplyBandProjection(4, Some(8), 5), SupplyBandProjection(0, Some(3), 4)))
    assertEquals(GameProjectionCodec.decode(GameProjectionCodec.encode(withBag)),
      Right(withBag))

  test("a payload without bag figures or bands decodes to zero and none"):
    val raw = ujson.read(GameProjectionCodec.encode(projection))
    raw.obj.remove("supplyBands")
    raw("playerBoards").arr.foreach { board =>
      Vector("bankedWarbands", "printedWarbands", "siteWarbands", "restSupplyBase")
        .foreach(board.obj.remove) }
    val decoded = GameProjectionCodec.decode(ujson.write(raw))
    assertEquals(decoded.map(_.supplyBands), Right(Vector.empty))
    assertEquals(decoded.map(_.playerBoards.map(_.bankedWarbands)),
      Right(projection.playerBoards.map(_ => 0)))
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: compile error on `bankedWarbands` and `SupplyBandProjection`.

- [ ] **Step 3: Add the DTO fields and the codecs**

In `ActionProjectionDtos.scala`, append four fields to `PlayerBoardProjection` after `banners`:

```scala
    banners: Vector[BannerProjection] = Vector.empty,
    /** The warbands of this player's kind still in their bag: printed, less
      * those on the board and at sites. Public, as the bag is on the table. */
    bankedWarbands: Int = 0,
    printedWarbands: Int = 0,
    siteWarbands: Int = 0,
    /** The Supply the band for `bankedWarbands` returns at Rest, before
      * unspent Supply is added and the track's ceiling applied. */
    restSupplyBase: Int = 0
```

Right after `BannerProjection`, add:

```scala
/** One row of the Rest table: warbands in the bag from `from` to `to`
  * (`None` is "or more") return `supply`. */
final case class SupplyBandProjection(from: Int, to: Option[Int], supply: Int)
```

In `GameProjectionDto.scala`, append after `restSupplyGain`:

```scala
    ,restSupplyGain: Option[Int] = None
    ,supplyBands: Vector[SupplyBandProjection] = Vector.empty
```

In `WorldProjectionCodec.scala`, `encodeBoard` gains, after the `"banners"` entry:

```scala
    "bankedWarbands" -> value.bankedWarbands,
    "printedWarbands" -> value.printedWarbands,
    "siteWarbands" -> value.siteWarbands,
    "restSupplyBase" -> value.restSupplyBase)
```

`decodeBoard`: add the four names to its `exact` set; after the banners block add

```scala
    banked <- intOr(value, "bankedWarbands", path, 0)
    printed <- intOr(value, "printedWarbands", path, 0)
    atSites <- intOr(value, "siteWarbands", path, 0)
    restBase <- intOr(value, "restSupplyBase", path, 0)
  yield PlayerBoardProjection(player, warbands, favor, up, down, committed, total, supply, pawn,
    advisers, relics, vision, banners, banked, printed, atSites, restBase)
```

In `GameProjectionCodec.scala`: add `"supplyBands"` to `Fields`; in `encodeValue` after `"restSupplyGain"`:

```scala
    "restSupplyGain" -> intOption(value.restSupplyGain),
    "supplyBands" -> encoded(value.supplyBands)(b => ujson.Obj(
      "from" -> b.from, "to" -> intOption(b.to), "supply" -> b.supply)))
```

in `decodeValue` after `restGain`:

```scala
    bandRaws <- default(value, "supplyBands", path, Vector.empty[ujson.Value])(array)
    bands <- traverse(bandRaws, s"$path.supplyBands") { (raw, child) => for
      row <- obj(raw, child); _ <- exact(row, Set("from", "to", "supply"), child)
      from <- int(row, "from", child); to <- optionalAbsent(row, "to", child)(int)
      supply <- int(row, "supply", child)
    yield SupplyBandProjection(from, to, supply) }
  yield GameProjection(game, sequence, phase, active, players, world, pawns, controls,
    ready, completed, resources, siteResources, actionOpen,
    sources, actions,
    deckCount, deckTop, boards, oathkeeper, banners, minor,
    banks, tracks, relicDeck, preview,
    walkerDecision, walkerWaiting, phasePowers, viewer,
    supplyMaximum, restGain, bands)
```

`intOption` writes `None` as JSON null and `optionalAbsent` reads null or absent as `None`; both are already used for `restSupplyGain`.

- [ ] **Step 4: Run the codec test**

Run: `./sbtw "testOnly oathdigital.protocol.ProjectionProtocolSuite"`
Expected: PASS.

- [ ] **Step 5: Write the failing projection test**

In `SupplyProjectionSuite`, add:

```scala
  test("a board carries its bag and the band the bag falls in"):
    val board = Table.start.warbands(p1, 3).ready
    val projected = project(board, p1)
    val mine = projected.playerBoards.find(_.playerId == p1.value)
      .getOrElse(fail("no board for p1"))
    assertEquals(mine.printedWarbands, MaterialBankState.ExileWarbandsPerLineage)
    assertEquals(mine.bankedWarbands,
      mine.printedWarbands - mine.warbands - mine.siteWarbands)
    // The same number the Act button promises, read from the same bag.
    assertEquals(Some(mine.restSupplyBase), projected.restSupplyGain)

  test("the band table is the engine's, in the engine's order"):
    assertEquals(project(Table.start.ready, p1).supplyBands, Vector(
      SupplyBandProjection(9, None, 6), SupplyBandProjection(4, Some(8), 5),
      SupplyBandProjection(0, Some(3), 4)))
```

Add `SupplyBandProjection` to the suite's `oathdigital.protocol.projection` import.

- [ ] **Step 6: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.application.SupplyProjectionSuite"`
Expected: FAIL, `printedWarbands` is 0 and `supplyBands` is empty.

- [ ] **Step 7: Split `PlayerFacts.banked` into its parts**

Replace lines 19-29 of `PlayerFacts.scala` with:

```scala
  /** The warbands of `kind` on player boards. */
  def onBoards(ready: ReadyGame, kind: ForceKind): Int =
    ready.game.current.players
      .filter(state => PlayerForceKind.of(ready, state).contains(kind))
      .map(_.board.warbands).sum

  /** The warbands of `kind` standing at sites. */
  def atSites(ready: ReadyGame, kind: ForceKind): Int =
    ready.game.current.map.sites.values.map(_.forces).collect {
      case SiteForces.Occupied(`kind`, count) => count }.sum

  /** The warbands of `kind` left in their bank: the printed supply less
    * those on boards and at sites. The one count Rest's Supply band and the
    * table's Supply overlay are both read from. */
  def banked(ready: ReadyGame, kind: ForceKind): Int =
    math.max(0, ready.banks.warbandSupply.getOrElse(kind, 0) -
      onBoards(ready, kind) - atSites(ready, kind))
```

- [ ] **Step 8: Make Rest read it**

In `FinishRestProcedure.scala`, replace the private `bankedWarbands` (lines 73-89) with:

```scala
  /** The warbands the resting player's own bank holds right now -- the one
    * count both the Supply band and its preview are read from, so neither
    * can be derived a different way and disagree with the other. The count
    * itself is `PlayerFacts.banked`, which the table's Supply overlay also
    * reads; only the guard on an unbounded supply is Rest's.
    */
  private def bankedWarbands(ready: ReadyGame, player: PlayerState)
      : Either[OathViolation, Int] =
    val kind = ForceKind.Exile(player.lineage)
    Either.cond(ready.banks.warbandSupply.contains(kind),
      PlayerFacts.banked(ready, kind),
      UnsupportedRestState(s"no bounded warband supply for $kind"))
```

Add `import oathdigital.gameplay.powers.PlayerFacts` to the file.

- [ ] **Step 9: Project the figures**

In `GamePresentationProjector.playerBoards`, before the `PlayerBoardProjection(` call inside the `map`, compute:

```scala
      val kind = PlayerForceKind.of(ready, player)
      val printed = kind.fold(0)(ready.banks.warbandSupply.getOrElse(_, 0))
      val atSites = kind.fold(0)(PlayerFacts.atSites(ready, _))
      val banked = kind.fold(0)(PlayerFacts.banked(ready, _))
      val restBase = FinishRestProcedure.ExileSupply.baseSupplyFor(banked).getOrElse(0)
```

and append `banked, printed, atSites, restBase` after the `banners(...)` argument. Add the imports `oathdigital.gameplay.powers.PlayerFacts` and `oathdigital.gameplay.phases.rest.FinishRestProcedure`. `setupPlayerBoards` is unchanged; its defaults are zero.

In `GameProjection.scala`, after `restSupplyGain = ...`:

```scala
        supplyBands = FinishRestProcedure.ExileSupply.refreshBands.map(band =>
          SupplyBandProjection(band.warbandsInBank.minimum,
            Option.when(band.warbandsInBank.maximum != Int.MaxValue)(
              band.warbandsInBank.maximum), band.baseSupply)))
```

`SupplyBandProjection` comes from `oathdigital.protocol.projection`, which the file already imports.

- [ ] **Step 10: Run the suites**

Run: `./sbtw "testOnly oathdigital.application.SupplyProjectionSuite oathdigital.model.SupplySuite oathdigital.protocol.ProjectionProtocolSuite" "testOnly oathdigital.gameplay.phases.rest.*"`
Expected: PASS. If the rest package has no suites under that name, run `./sbtw test` instead and expect the baseline count.

- [ ] **Step 11: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/PlayerFacts.scala src/main/scala/oathdigital/gameplay/phases/rest/FinishRestProcedure.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/GameProjectionDto.scala shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionCodec.scala shared/src/main/scala/oathdigital/protocol/projection/GameProjectionCodec.scala src/main/scala/oathdigital/application/GamePresentationProjector.scala src/main/scala/oathdigital/application/GameProjection.scala src/test/scala/oathdigital/application/SupplyProjectionSuite.scala shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala
git commit -m "feat(projection): banked warbands and the Supply bands on every board"
```

---

### Task 3: The discard pile opens as a card list

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala:144-166`
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala:161-162`
- Modify: `frontend/styles.css:581-588, 747`
- Test: `frontend/src/test/scala/oathdigital/frontend/DiscardPileSuite.scala` (new)

**Interfaces:**
- Consumes: `GameRegion.discardCards` (Task 1); `CardInspection.openCards(title, cards, origin)`.
- Produces: `ServerUiSupport.pileDisplay(label, count, topCardKind, opens: Option[(String, Vector[CardDetails])] = None)`. With `opens`, the pile is a `button.pile-display.pile-open`, disabled when the count is 0.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/test/scala/oathdigital/frontend/DiscardPileSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.model.PlayerColor
import org.scalajs.dom

/** A region's discard pile opens as a card list, top first. */
class DiscardPileSuite extends munit.FunSuite:
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private val site = GameSite("site:woods", "Deep Woods", looseFavor = 0,
    looseSecrets = 0, denizenCapacity = 3, relicCapacity = 0,
    denizens = Vector.empty, relics = GameSiteRelics(0), defense = 0)

  private val top = CardDetails("hidden", "denizen", "Facedown denizen",
    orientation = Some("face-down"), hidden = true)
  private val known = CardDetails("denizen:scryer", "denizen", "Scryer",
    suit = Some("arcane"))

  private def projection(pile: Vector[CardDetails]): GameProjection =
    GameProjection("game", 1L, "act", Some("red"),
      Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
      Vector(GameRegion("cradle", Vector(site), pile.size,
        pile.headOption.map(_.cardKind), pile)),
      Vector.empty, Vector.empty, ready = true, completed = false)

  private def world(pile: Vector[CardDetails]): dom.Element =
    WorldBoardRenderer.world(projection(pile), None, canControl = true,
      SessionDrafts.empty, new RecordingControls())

  private def discard(root: dom.Element): dom.html.Button =
    all(root, ".region .pile-display").headOption
      .getOrElse(fail("no discard pile")).asInstanceOf[dom.html.Button]

  override def afterEach(context: AfterEach): Unit = CardInspection.clear()

  test("the pile is a button that opens its cards top first"):
    var opened = Vector.empty[(String, Vector[String])]
    CardInspection.onOpen:
      case CardInspection.Request.Cards(title, cards, _) =>
        opened :+= (title -> cards.map(_.name))
      case _ => ()
    val pile = discard(world(Vector(top, known)))
    assertEquals(pile.tagName.toLowerCase, "button")
    assertEquals(pile.getAttribute("aria-label"), "Cradle discard pile, 2 cards")
    pile.click()
    assertEquals(opened, Vector("Cradle discard pile (2)" ->
      Vector("Facedown denizen", "Scryer")))

  test("an empty pile keeps its look and opens nothing"):
    val pile = discard(world(Vector.empty))
    assert(pile.disabled)
    assertEquals(all(pile, ".pile-empty").size, 1)
    assertEquals(pile.textContent.contains("x0"), true)
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.DiscardPileSuite"`
Expected: FAIL on `tagName`: the pile is a `div`.

- [ ] **Step 3: Make the pile a button when it has cards to show**

In `ServerUiSupport.scala`, replace `pileDisplay` (lines 144-166):

```scala
  /** A deck or a pile: its label, one back for the top card, and the count.
    * With `opens`, a title and the pile's cards top first, the pile is a
    * button that lists them in the card overlay, the way a log line's card
    * list opens (`CardInspection.openCards`). Decks pass nothing: a deck's
    * order is nobody's to see.
    */
  private[frontend] def pileDisplay(
      label: String,
      count: Int,
      topCardKind: Option[String],
      opens: Option[(String, Vector[CardDetails])] = None
  ): dom.Element =
    val pile = opens.fold(element("div", "pile-display")) { case (title, cards) =>
      val node = button("", "pile-display pile-open")
      node.setAttribute("aria-label", s"$title, $count cards")
      node.disabled = count == 0
      node.onclick = _ => CardInspection.openCards(s"$title ($count)", cards, node)
      node
    }
    pile.appendChild(text("span", "pile-label", s"$label:"))
    val css = pileCardClasses(count)
    val symbol = pileSymbol(count, topCardKind)
    val back = text("span", css, if symbol.isEmpty then " " else symbol)
    back.setAttribute("role", "img")
    back.setAttribute("aria-label", if count == 0 then "Empty pile"
      else topCardKind match {
        case Some("denizen") => "Denizen card on top"
        case Some("vision") => "Vision card on top"
        case _ => "Facedown card; type hidden"
      })
    pile.appendChild(back)
    pile.appendChild(text("span", "pile-count", s"x$count"))
    pile
```

In `WorldBoardRenderer.scala`, line 161-162 becomes:

```scala
     section.appendChild(pileDisplay("Discard", region.discardCount,
       region.discardTopCardKind,
       Some(s"$name discard pile" -> region.discardCards)))
```

`name` is the region's display name computed just above it.

- [ ] **Step 4: Run the suite**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.DiscardPileSuite oathdigital.frontend.BoardSurfaceSuite oathdigital.frontend.SiteBoxLayoutSuite"`
Expected: PASS.

- [ ] **Step 5: Style the button like the pile it was**

Read `~/.claude/skills/impeccable/reference/craft-floor.md` first. In `frontend/styles.css`, after the `.pile-display > .pile-empty` rule (line 588), add:

```css
/* The pile as a control: the same row, on the same fill, with the title
   pill's hover. A button resets the text it inherits, so the colour and
   font are given back. */
.pile-open { border: 0; padding: 0; background: none; color: inherit;
  font: inherit; cursor: pointer; }
.pile-open:disabled { cursor: default; }
```

and extend the hover list at line 747 to `.player-title:hover, .player-slot-vision:hover, .pile-open:not(:disabled):hover { background: #302a20; }`.

- [ ] **Step 6: Run the detector**

Run: `~/.claude/skills/impeccable/scripts/impeccable detect --json frontend/styles.css`
Expected: no new finding against the lines you added. Fix any it reports.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala frontend/styles.css frontend/src/test/scala/oathdigital/frontend/DiscardPileSuite.scala
git commit -m "feat(frontend): a discard pile opens its cards in the overlay"
```

---

### Task 4: Supply opens "Supply at Rest"

**Files:**
- Create: `frontend/src/main/scala/oathdigital/frontend/SupplyAtRest.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/frontend.scala` (one alias)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala:61-62`
- Modify: `frontend/styles.css` (one rule beside `.pile-open`)
- Test: `frontend/src/test/scala/oathdigital/frontend/SupplyAtRestSuite.scala` (new)
- Test: `frontend/src/test/scala/oathdigital/frontend/PlayerBoardSuite.scala`

**Interfaces:**
- Consumes: `PlayerBoard.bankedWarbands`, `printedWarbands`, `siteWarbands`, `restSupplyBase`; `GameProjection.supplyBands`, `supplyMaximum` (Task 2); `CardInspection.openText`.
- Produces: `SupplyAtRest.lines(role: String, board: PlayerBoard, bands: Vector[SupplyBand], maximum: Int): Vector[String]`; `type SupplyBand = protocol.projection.SupplyBandProjection`.

- [ ] **Step 1: Write the failing pure test**

Create `frontend/src/test/scala/oathdigital/frontend/SupplyAtRestSuite.scala`:

```scala
package oathdigital.frontend

/** The lines behind a board's Supply figure. */
class SupplyAtRestSuite extends munit.FunSuite:
  private val bands = Vector(SupplyBand(9, None, 6), SupplyBand(4, Some(8), 5),
    SupplyBand(0, Some(3), 4))

  private val board = PlayerBoard("red", warbands = 6, favor = 0,
    faceUpSecrets = 0, faceDownSecrets = 0, committedSecrets = 0,
    totalSecrets = 0, supply = 5, pawnSiteId = None, advisers = Vector.empty,
    relics = Vector.empty, revealedVision = None, bankedWarbands = 5,
    printedWarbands = 14, siteWarbands = 3, restSupplyBase = 5)

  test("an Exile reads the bag, the table with its band marked, and the Rest"):
    assertEquals(SupplyAtRest.lines("exile", board, bands, 7), Vector(
      "Warbands in bag: 5 (14 printed, 6 on board, 3 at sites).",
      "0–3 warbands: Supply 4",
      "4–8 warbands: Supply 5 (now)",
      "9 or more warbands: Supply 6",
      "Next Rest: 5 from the band, plus unspent Supply, capped at 7."))

  test("an empty bag marks the lowest band"):
    val lines = SupplyAtRest.lines("Exile", board.copy(bankedWarbands = 0,
      restSupplyBase = 4), bands, 7)
    assertEquals(lines(1), "0–3 warbands: Supply 4 (now)")
    assertEquals(lines(2), "4–8 warbands: Supply 5")

  test("a citizen is told the Chancellor's rule and no table"):
    assertEquals(SupplyAtRest.lines("citizen", board, bands, 7),
      Vector("Citizens copy the Chancellor's Supply."))
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SupplyAtRestSuite"`
Expected: compile error, `SupplyBand` and `SupplyAtRest` not found.

- [ ] **Step 3: Write the alias and the object**

In `frontend/src/main/scala/oathdigital/frontend/frontend.scala`, next to the `PlayerBoard` alias (line 74):

```scala
type SupplyBand = protocol.projection.SupplyBandProjection
```

Create `frontend/src/main/scala/oathdigital/frontend/SupplyAtRest.scala`:

```scala
package oathdigital.frontend

/** The text behind a board's Supply figure: what the bag holds, the Rest
  * table with the current band marked, and the Supply the next Rest
  * returns. The numbers are the server's (`PlayerBoard`), so this never
  * re-derives a rule; it only says it.
  */
private[frontend] object SupplyAtRest:
  def lines(role: String, board: PlayerBoard, bands: Vector[SupplyBand],
      maximum: Int): Vector[String] =
    if !role.equalsIgnoreCase("exile") then
      Vector("Citizens copy the Chancellor's Supply.")
    else
      val bag = s"Warbands in bag: ${board.bankedWarbands} " +
        s"(${board.printedWarbands} printed, ${board.warbands} on board, " +
        s"${board.siteWarbands} at sites)."
      val table = bands.sortBy(_.from).map { band =>
        val range = band.to.fold(s"${band.from} or more warbands")(to =>
          s"${band.from}–$to warbands")
        val now = band.from <= board.bankedWarbands &&
          band.to.forall(board.bankedWarbands <= _)
        s"$range: Supply ${band.supply}" + (if now then " (now)" else "")
      }
      val rest = s"Next Rest: ${board.restSupplyBase} from the band, " +
        s"plus unspent Supply, capped at $maximum."
      (bag +: table) :+ rest
```

- [ ] **Step 4: Run the pure suite**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SupplyAtRestSuite"`
Expected: PASS.

- [ ] **Step 5: Write the failing board test**

In `PlayerBoardSuite`, after the title-pill test (about line 196), add:

```scala
  test("Supply is a button that opens Supply at Rest for that board"):
    var opened = Vector.empty[(String, Vector[String])]
    CardInspection.onOpen:
      case CardInspection.Request.Text(title, lines, _) => opened :+= (title -> lines)
      case _ => ()
    val supply = one(render(), ".resources .supply-open")
      .getOrElse(fail("no Supply button")).asInstanceOf[dom.html.Button]
    assertEquals(supply.textContent, "Supply 7/7")
    supply.click()
    CardInspection.clear()
    assertEquals(opened.map(_._1), Vector("Supply at Rest"))
    assert(opened.head._2.head.startsWith("Warbands in bag: "), opened.head._2.head)
```

The fixture's `board` has zero bag figures and the projection has no bands; the first line is enough to prove the wiring. `render()` builds the projection with `supplyMaximum = 7`.

- [ ] **Step 6: Run it to see it fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.PlayerBoardSuite"`
Expected: FAIL, "no Supply button".

- [ ] **Step 7: Make Supply a button**

In `WorldBoardRenderer.playerBoards`, replace lines 61-62:

```scala
     resources.appendChild(text("span", "resource",
       s"Supply ${board.supply}/${value.supplyMaximum}"))
```

with:

```scala
     // Supply is the one figure with a rule behind it: the bag sets the
     // band, the band sets the Rest. The figure opens that rule.
     val supply = button(s"Supply ${board.supply}/${value.supplyMaximum}",
       "resource supply-open")
     supply.onclick = _ => CardInspection.openText("Supply at Rest",
       SupplyAtRest.lines(player.role, board, value.supplyBands,
         value.supplyMaximum), supply)
     resources.appendChild(supply)
```

`player` is the `GamePlayer` the enclosing `foreach` iterates; its `role` is what the identity line prints.

- [ ] **Step 8: Run the board suite**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.PlayerBoardSuite"`
Expected: PASS, including the existing test that reads `.resource` texts as `Vector("Warbands 3", "2", "1/2", "Supply 7/7")`.

- [ ] **Step 9: Give the button the pill's look**

In `frontend/styles.css`, extend the `.pile-open` rule from Task 3 to `.pile-open, .supply-open { ... }` and the hover list to include `.supply-open:hover`. Add nothing else: the figure stays inline text in the resources row.

- [ ] **Step 10: Run the detector and commit**

Run: `~/.claude/skills/impeccable/scripts/impeccable detect --json frontend/styles.css`
Expected: nothing new.

```bash
git add frontend/src/main/scala/oathdigital/frontend/SupplyAtRest.scala frontend/src/main/scala/oathdigital/frontend/frontend.scala frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala frontend/styles.css frontend/src/test/scala/oathdigital/frontend/SupplyAtRestSuite.scala frontend/src/test/scala/oathdigital/frontend/PlayerBoardSuite.scala
git commit -m "feat(frontend): Supply opens the bag, the band table and the Rest it returns"
```

---

### Task 5: Secret and defense-die glyphs get their own shapes

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/TokenSprite.scala:23-27, 47-49, 66-95`
- Test: `frontend/src/test/scala/oathdigital/frontend/SuitGlyphSuite.scala`

**Interfaces:**
- Produces: sprite symbols `secret`, `secret-burnt`, `defense-die` with the spec's paths and `fill-rule="evenodd"` on their `<path>`.

- [ ] **Step 1: Read how the sprite is built**

Read `TokenSprite.scala` lines 66-95 (`mount`): it creates one `<symbol>` per entry with a `<path>` whose `d` is the path data. Note how attributes are set, so Step 3 adds `fill-rule` the same way.

- [ ] **Step 2: Write the failing test**

In `SuitGlyphSuite`, add:

```scala
  test("a secret is a solid book and a defense die a shield, both even-odd"):
    val root = dom.document.createElement("div")
    TokenSprite.mount(root)
    def path(id: String): dom.Element =
      Option(root.querySelector(s"symbol#token-$id path"))
        .getOrElse(fail(s"no path for $id"))
    assert(path("secret").getAttribute("d").startsWith("M4 3h13a2 2 0 0 1 2 2v16H7a3 3 0 0 1-3-3zM8 6v12"))
    assert(path("defense-die").getAttribute("d").startsWith("M12 1.5 20.5 4.8"))
    assertEquals(path("secret").getAttribute("fill-rule"), "evenodd")
    assertEquals(path("secret-burnt").getAttribute("fill-rule"), "evenodd")
    assertEquals(path("defense-die").getAttribute("fill-rule"), "evenodd")
```

If the symbol ids carry a different prefix than `token-`, read `mount` and use the one it writes.

- [ ] **Step 3: Run it to see it fail**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SuitGlyphSuite"`
Expected: FAIL on the first `assert`.

- [ ] **Step 4: Change the paths**

In `TokenSprite.glyphs`, replace the three entries:

```scala
    ("secret", "secret",
      "M4 3h13a2 2 0 0 1 2 2v16H7a3 3 0 0 1-3-3zM8 6v12h1.6V6z"),
    ("secret-burnt", "burnt secret",
      "M4 3h13a2 2 0 0 1 2 2v16H7a3 3 0 0 1-3-3zM8 6v12h1.6V6z" +
        "M11.4 3l1.9.4-1.7 4.3 2.7 2.6-2.6 3 1.6 7.6-1.9.4-1.8-8.2 2.5-2.9-2.4-2.3z"),
```

and

```scala
    ("defense-die", "defense die",
      "M12 1.5 20.5 4.8v6.4c0 5.2-3.6 9.4-8.5 11.3-4.9-1.9-8.5-6.1-8.5-11.3V4.8z" +
        "M9 8h2.6v2.6H9zm3.4 4.2H15v2.6h-2.6z"),
```

`mount` already sets `fill-rule="evenodd"` on every `<path>` (line 88), so the cut-out spine and pips render as holes with no change there. Update the object's doc comment: the secret is a solid book, the defense die a shield.

- [ ] **Step 5: Run the suite**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.SuitGlyphSuite oathdigital.frontend.SiteFaceSuite oathdigital.frontend.CardInspectionOverlaySuite"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/main/scala/oathdigital/frontend/TokenSprite.scala frontend/src/test/scala/oathdigital/frontend/SuitGlyphSuite.scala
git commit -m "feat(frontend): a solid book for secrets and a shield for the defense die"
```

---

### Task 6: Glyph sizes (Impeccable `polish`)

**Files:**
- Modify: `frontend/styles.css:43, 68, 482, 491, 496, 735, 816` and one new rule

This task is an Impeccable `polish` pass. The brief is the spec's size table; the pass may adjust a value by a step after seeing it rendered, but every row changes and nothing else does.

- [ ] **Step 1: Load the playbook**

From the worktree root, run `~/.claude/skills/impeccable/scripts/impeccable context --target frontend/styles.css` unless this session has already run it. Read `~/.claude/skills/impeccable/reference/polish.md` and DESIGN.md's Cards, Dice, Sites and Shape First Rule. Immediately before the first CSS edit, read `reference/craft-floor.md`.

- [ ] **Step 2: Apply the size table**

| Rule | Line | From | To |
|---|---|---|---|
| `.token-glyph` | 43 | `width: 1.05em; height: 1.05em; vertical-align: -0.16em` | `width: 1.25em; height: 1.25em; vertical-align: -0.22em` |
| `.die-face .token-glyph` | 68 | `0.95em` | `1.1em` |
| `.card-header .token-glyph` | 482 | `1.2em` | `1.5em` |
| `.card-tokens` | 491 | `font-size: 0.8em` | `font-size: 1em` |
| `.card-defense` | 496 | `font-size: 0.8em` | `font-size: 1em` |
| `.map-compact .card-header .token-glyph` | 816 | `width: 1.05em; height: 1.05em` | `width: 1.5em; height: 1.5em` |

Add, after the `.map-compact .card-header .token-glyph` rule:

```css
/* The corner glyph grew; the centred name starts under it, not behind it. */
.map-compact .site .card-face { padding-top: 1.7em; }
```

(merge into the existing `.map-compact .site .card-face` rule at line 812, whose `padding: 0.25em` becomes `padding: 1.7em 0.25em 0.25em`, rather than adding a second rule.)

Add, after the `.resource` rule (about line 733):

```css
/* The strip's favor and secret figures are read from across the table. */
.resources .token-glyph { width: 1.3em; height: 1.3em; }
```

Update the comments the DESIGN.md Cards section quotes ("suit glyph (1.2em)", "token row at 0.8em") where they appear in the stylesheet.

- [ ] **Step 3: Scan**

Run: `~/.claude/skills/impeccable/scripts/impeccable detect --json frontend/styles.css`
Expected: nothing new. The frontend suites do not read CSS; run `./sbtw "frontend/test"` anyway and expect the baseline count plus Tasks 3-5's additions.

- [ ] **Step 4: Commit**

```bash
git add frontend/styles.css
git commit -m "style(frontend): larger glyphs on cards, dice and the strip"
```

---

### Task 7: The player strip to card height (Impeccable `layout`)

**Files:**
- Modify: `frontend/styles.css:666, 679-699, 735`

An Impeccable `layout` pass on the Players pane. Brief: the strip row becomes `minmax(150px, 17%)` (decision 5 above); a board's card row renders at the compact size so a board fits that row at 1440×900 and 1024×768; identity, resources, pills and banners are unchanged; nothing scrolls vertically.

- [ ] **Step 1: Load the playbook**

Read `~/.claude/skills/impeccable/reference/layout.md` and DESIGN.md's Layout and Player Boards sections. Read `reference/craft-floor.md` immediately before the first edit.

- [ ] **Step 2: Measure before**

Start the scratch server (Task 9, Step 1 shows how) and at 1440×900 record, with `javascript_tool`: `document.querySelector('.pane-players').getBoundingClientRect().height`, a `.player-board`'s height, and a `.board-cards .card-face`'s height. Expected before: about 234, about 115, about 103.

- [ ] **Step 3: Edit**

In `.game-table`'s `grid-template-rows` (line 666), replace `minmax(150px, 26%)` with `minmax(150px, 17%)`.

Replace the `.player-board .board-cards` rule (line 735) and its comment with:

```css
/* Cards on a board are read at a glance, not studied; the overlay is where
   a card is studied. Font size is the only handle, so the box follows. At
   0.7em of the board's 0.82rem a card is about 84px tall, which with the
   pane's and the board's padding is what a 17% row holds at 900px and a
   150px row at 768px. The name keeps the 0.7rem floor (`.card-name`). */
.player-board .board-cards { align-items: flex-start; font-size: 0.7em; }
```

Update the comment above `.game-table .player-board` (lines 680-685): the strip is a card row tall, and the number of boards it fits sideways before scrolling at 1024px.

- [ ] **Step 4: Measure after**

Reload (the server copies `styles.css` on start, so restart it). Record the same three numbers at 1440×900 and 1024×768. Expected: the pane at about 153 and 150; the board no taller than the pane's content box; no vertical scrollbar on `.pane-players > .pane-content` (`scrollHeight <= clientHeight`). If the board overflows by a few px, step the card font to 0.68em; if the row has slack over 12px, step it to 0.72em. One confirming pass, then stop.

- [ ] **Step 5: Scan and commit**

Run: `~/.claude/skills/impeccable/scripts/impeccable detect --json --scope layout frontend/styles.css`
Expected: nothing new.

```bash
git add frontend/styles.css
git commit -m "style(frontend): the player strip is one card row tall"
```

---

### Task 8: Site powers stay on the map (Impeccable `layout`)

**Files:**
- Modify: `frontend/styles.css:220, 287-293, 793, 800-810`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala:82-84` (one comment)

An Impeccable `layout` pass on the site box. Brief: the compact map no longer hides the footer; the box grows so the footer never costs the card row; powers wrap to two lines; text on the map may fall under 11px (spec, "Ruling on map text").

- [ ] **Step 1: Load the playbook**

Read DESIGN.md's Sites section and The Glance Layer Rule. Read `reference/craft-floor.md` immediately before the first edit.

- [ ] **Step 2: Edit**

1. `.site` (line 220): `--site-h: 19.5rem;` becomes `--site-h: 21.5rem;`. Update the comment above `.site` that explains the fixed height.
2. `.site-powers` (lines 289-292) becomes:

```css
/* Two lines of labels, clipped, never ellipsized mid-word: the box has
   room for two, each power's rules text rides on its hover title, and the
   player zooms in to read what shrank with the map. */
.site-powers { min-width: 0; margin: 0; overflow: hidden;
  display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2;
  line-height: 1.25; }
```

3. Delete `.map-compact .site-footer { display: none; }` (line 793) and amend the Glance Layer comment above `.map-compact .site` (lines 762-771): the study layer is no longer hidden; it scales with the map.
4. The two compact `--card-w` formulas (lines 803-810) subtract the footer: in both, change `- 30px` to `- 30px - 2.6rem` and `- 34px` to `- 34px - 2.6rem` (two 1.25 lines of 0.86rem plus the footer's gap). Update the comment above them.
5. In `ServerUiSupport.siteDetails` (lines 82-84), the comment "One line of labels: the box has no room for a second line" becomes "Two lines of labels at most (`.site-powers`); each power's rules text rides on its hover title."

- [ ] **Step 3: Measure**

Restart the scratch server. At 1440×900 Fit, with `javascript_tool`: `document.querySelector('.map-content').style.getPropertyValue('--map-scale')` or the zoom label (expect about 0.47); for a site with two or more powers, `.site-footer` is visible (`getBoundingClientRect().height > 0`) and lies inside its `.site` box (`footer.bottom <= site.bottom`); the `.site-cards` row still holds three card boxes with no clipping (`cards.bottom <= footer.top`). Zoom to 100% and read a two-power site's labels. Repeat at 1024×768. If a footer's second line is clipped on a three-power site, raise `--site-h` by 0.5rem and the formulas' `2.6rem` to `3.1rem`. One confirming pass, then stop.

- [ ] **Step 4: Scan and commit**

Run: `~/.claude/skills/impeccable/scripts/impeccable detect --json --scope layout frontend/styles.css`
Expected: nothing new.

```bash
git add frontend/styles.css frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala
git commit -m "style(frontend): site powers stay on the map and scale with it"
```

---

### Task 9: Browser verification over the scratch database

**Files:** none edited unless a check fails; then the file the failure names, with a commit of its own.

- [ ] **Step 1: Start the scratch server**

From the worktree root:

```bash
S=/private/tmp/claude-501/-Users-roman-projects-oathdigital/c17e13c6-2b51-40f4-a67d-0fd7cda78149/scratchpad/db-readability
mkdir -p $S && cp var/oathdigital.script var/oathdigital.properties var/oathdigital.log $S/ && cp -r var/oathdigital.lobs $S/
./sbtw "runMain oathdigital.server.OathServer --port 8081 --database-path $S/oathdigital"
```

Run the last line through Bash with `run_in_background` and wait for "Start completed" in its log. Open `http://127.0.0.1:8081/?mode=server&gameId=manual-1790308527747-879693` in the built-in browser at 1440×900 (`resize_window`). Red is the viewer.

- [ ] **Step 2: One batched pass**

Check, with `read_page`, `javascript_tool` and one screenshot per viewport:

1. Strip: Tasks 7's numbers hold; both boards show their card faces; "Supply 5/7" is a button.
2. Supply click: the overlay opens titled "Supply at Rest" with five lines; the band marked "(now)" matches the bag count; Escape closes it.
3. Discard click on a region with cards: the overlay lists that many backs top first, titled "Cradle discard pile (N)"; on an empty pile the button is disabled.
4. Site powers: at Fit a two-power site's footer is inside its box; at 100% its labels read; hovering a label shows its rules text.
5. Glyphs: on a site card the suit glyph is visibly larger than before (screenshot beside the Task 7 before-shot); in the strip the secret reads as a solid book and the site's defense dice as shields; `.resources .token-glyph` measures about 14px.
6. Dark fill, no new scrollbars: `document.querySelectorAll('.pane-content').forEach(p => p.scrollHeight > p.clientHeight)` is false for players and world.
7. Repeat 1, 4 and 6 at 1024×768.
8. Console: `read_console_messages` with `onlyErrors` shows nothing new.

- [ ] **Step 3: Fix and confirm**

Fix every failure in one batch, restart the server, confirm once, and stop. Commit each fix against the file it touches (`style(frontend): ...` or `fix(frontend): ...`).

- [ ] **Step 4: Stop the server and reset the viewport**

Kill the server process (`lsof -ti :8081 | xargs kill`) and call `resize_window` with preset `desktop`.

---

### Task 10: DESIGN.md, the sidecar, the roadmap and the gates

**Files:**
- Modify: `DESIGN.md:368-399, 435-446, 560-575, 601-616, 694-699`
- Modify: `.impeccable/design.json` (through `/impeccable document`)
- Modify: `docs/ROADMAP.md:22-32`
- Modify: `docs/superpowers/specs/2026-10-02-table-readability-design.md` (status line and the decisions above)

- [ ] **Step 1: Amend DESIGN.md**

Make these edits, keeping the surrounding prose:

- **The Glance Layer Rule** (line 376): replace "The study layer (site powers and requirement, a card's defense, restriction and badge) is hidden; a zoom or the overlay carries it." with "The study layer (site powers and requirement, a card's defense, restriction and badge) scales with the map: it is on the board at every zoom and read by zooming in. Text on the map may fall under 11px; the Eleven-Pixel Floor is a pane rule." Replace "the site box is fixed and clips" with "the site box is fixed at 21.5rem and clips", and in the `--card-w` sentence add "the footer's two lines" to the list of what is subtracted.
- **Layout** (line 436): `19.5rem` becomes `21.5rem`. In the Players pane paragraph, after "The card row sets the board's height", add "at 0.7em, one compact card tall, so the strip row is `minmax(150px, 17%)`" (or the values Task 7 settled on).
- **Cards** (line 567): "suit glyph (1.2em)" becomes "suit glyph (1.5em)"; "token row at 0.8em" becomes "token row and defense row at 1em". In the Compact paragraph, after "the suit glyph moves to the top-left corner", add "at 1.5em, with the face padded 1.7em at the top so the name starts under it".
- **Dice** (line 602): "symbols in Token Symbol at 0.9em" becomes "symbols in Token Symbol at 0.9em with the glyph at 1.1em".
- **Sites** (line 611): "fixed 19.5rem height" becomes "fixed 21.5rem height"; "Powers are one ellipsized line with hover titles, hidden in compact mode." becomes "Powers are two clipped lines of labels with hover titles, on the board at every zoom."
- **Player Boards** (line 695): "225px minimum" becomes "300px minimum"; "Cards on a board render at 0.86em" becomes "Cards on a board render at 0.7em" (or Task 7's value); add "The Supply figure and a region's discard pile are buttons on the pill hover that open the text and card-list overlays."
- Under **Colors > Tertiary** or wherever the token glyph list names the secret's shape (search "book"), say the secret is a solid book and the defense die a shield; add `.token-glyph` base size 1.25em and the strip's 1.3em to Typography's Hierarchy list if sizes are listed there.

- [ ] **Step 2: Refresh the sidecar**

Invoke the Impeccable skill with `document`, scoped to refreshing `.impeccable/design.json` from the amended DESIGN.md. Do not let it rewrite DESIGN.md. If it proposes DESIGN.md changes, decline them and finish.

- [ ] **Step 3: Roadmap and spec**

In `docs/ROADMAP.md`, delete these six bullets from Phase - Cleanup tasks: "Keep site powers visible", "Shrink the player strip", "Enlarge glyphs", "Make secrets and defense dice look different", "Show the backs of all cards in a discard pile", "Show which warbands map to which Supply in the UI". Leave the others.

In the spec, change the status line to "designed 2026-10-02, built 2026-10-02" and add a "Decisions made while building" section carrying this plan's four decisions.

- [ ] **Step 4: Gates**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: server baseline plus the new tests in Tasks 1 and 2; frontend 480 plus the new tests in Tasks 3, 4 and 5 (expect 489 or close; the exact count is the sum of tests you added).

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both clean.

- [ ] **Step 5: Commit**

```bash
git add DESIGN.md .impeccable/design.json docs/ROADMAP.md docs/superpowers/specs/2026-10-02-table-readability-design.md
git commit -m "docs: record the table readability pass in DESIGN.md, the sidecar and the roadmap"
```

- [ ] **Step 6: Hand back**

Report the branch and worktree; do not merge. The merge runs from the main checkout after `ExitWorktree(keep)`, with `git merge --no-edit table-readability`, then the worktree and branch are removed.
