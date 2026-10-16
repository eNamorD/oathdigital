# Catalog Batch 8, Slice 12 (Card Play at Another Site) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement three cards:

- 250 Baron: "You may play to any site you rule, and you may discard a card from that site first."
- 188 New Growth: "You may play [suit-beast] and [suit-hearth] cards to any site _(that has space)_."
- 218 Forest Warden: "If you play a [suit-beast] card to a site, you may bury a card at the site first."

**Architecture:**

- **Site relics get a slot ref.** `DecisionOptionRef.SiteRelicSlot(site, index)` (kind `site-relic-slot`) names a relic by its place at a site, as `RelicSlot` names a player's relic. An option therefore never discloses a facedown relic.
  - The site projection lists relics by position: a relic the viewer knows is its card, and any other is a hidden back.
  - The board draws each relic in its place with the slot's key.
  - Only Forest Warden uses the ref in this slice. A separate task moves the older site-relic options (Recover, Skeleton Key and the rest) onto it.
- **A card play names its site.** `SearchPlacement.Site(site, replace)` carries the site. `CardPlay` and `CardSlots` read it instead of the pawn site.
  - `CardPlayRules.playSites` holds the powers' openings (`PlaySites`). A power uses them to offer sites other than the pawn's. Card play offers the pawn site first, then each opened site in map order.
  - A `DiscardPermission` gains a site scope (`appliesAt`) and a kind (`SiteRemoval.Discard` or `SiteRemoval.Bury`). Its line names the site.
  - A site card discarded in a play goes to the pile after the target site's region.
- **The Replace path accepts a bury.**
  - A Replace that only a bury covers is a `DecisionOption.Burying`, labelled "Bury and replace" on the board.
  - When a discard covers the same card too, the option stays a Replace and a follow-up asks "Bury or discard the replaced card".
  - At a site with room, each relic there is a "Bury and play" option.
  - After a Replace that discarded, a bury follow-up offers the site's other cards and relics.
- **The cards** are selected Search modifiers, like Crop Rotation, so they reach the facedown-adviser play too.
  - Baron opens the sites its user rules, with a discard permission scoped to them.
  - New Growth opens every site for a Beast or Hearth card.
  - Forest Warden adds a bury permission for a Beast card.
  - New Growth and Forest Warden read the played card's effective suit (`EffectiveSuit.of`), and so does the Homeland rule from now on.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`: "The bar" (the slice 12 bullet), "Slice 12: Card play at another site" and "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: the slice 12 entry. Task 6 replaces it with this plan's rulings, which are listed under "Rulings" below.
- `docs/superpowers/specs/2026-10-05-card-play-slots-design.md`, if present, for the slot builder and the placement procedure. The doc comments of `CardSlots` and `CardPlayProcedure` restate both.
- `src/main/scala/oathdigital/gameplay/powers/search/CropRotation.scala`: the pattern each new card follows.

**Starts from:** `main` at `466e619b` ("slice 11 done") or later. Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Rulings

The product owner settled these on 2026-10-16. They override the slice 12 entry of the rulings appendix where the two differ.

1. **The cards are selected modifiers.** Each is a free Search modifier the player picks, and the pick reaches the facedown-adviser play too.
2. **Baron** opens every site the player rules. Every play to a ruled site, the pawn site included, carries Baron's discard permission.
3. **New Growth** opens every site for a card whose effective suit is Beast or Hearth. "(that has space)" is a reminder only, so every site follows the pawn site's rules:
   - A site with room offers its empty slot.
   - A full site offers Replace options only under a discard or bury permission, and nothing otherwise.
4. **Forest Warden** gives one bury at the site, when the card's effective suit is Beast. Bury ignores locks, so any card there may go:
   - any card in the site's row: denizens, intact edifices, and cards the Hall of Ministers or Ancient Bloodline protect;
   - any relic there.
5. **Permissions follow the site played to.**
   - The Homeland rule applies where the site is the Homeland of the card's effective suit.
   - Crop Rotation, People's Favor Mob and Forest Warden apply at any site.
   - Baron applies at ruled sites.
6. **Bury or discard.**
   - A Replace covered only by Forest Warden is a bury, shown "Bury and replace".
   - A Replace covered only by a discard permission is a discard, shown "Replace".
   - When both cover it, the option reads "Replace". A follow-up choose-one titled "Bury or discard the replaced card" then offers the buttons "Bury" and "Discard".
   - Forest Warden's bury is spent by the replaced card or by the bury follow-up, never twice.
7. **Relics.**
   - At a site with room, each relic there is offered as "Bury and play", and the card goes to the first empty slot.
   - After a Replace that discarded, the follow-ups run in order. "Discard more cards from the site" offers row cards up to the discard permissions left. Then, if Forest Warden is still unused, "Bury a card at the site" is a choose-many from 0 to 1 over the remaining row cards and every relic there.
   - Relics are offered by their slot (`SiteRelicSlot`), never by their identity.
8. **Discard piles.** A site card discarded in a play goes to the pile of the region after the target site's region. A replaced adviser, and the discard-pile slot, keep the pile after the pawn's region.
9. **Order.** The slot question's options run in this order:
   1. the discard pile;
   2. the pawn site;
   3. each other site in map order, with its empty slot, then its Replace options, then its relics;
   4. the faceup plays;
   5. the facedown plays.
10. **Lines.**
    - Every discard permission writes "May discard a card at {site} first.": the Homeland rule, Crop Rotation, the Mob and Baron. This drops the old "{Red} … at their site".
    - Forest Warden writes "May bury a card at {site} first."
    - Baron and New Growth write nothing else. "Played {card} to {site}" now names the target site.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `WalkerDecisionProjector.scala` is at 702 lines; keep its additions small.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Line width.** Scala lines stay at or under 80 characters.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Test helper names** say what they build. A doc comment does not excuse a vague name.
- **Old replays need no support.** A journal written before this plan need not decode.
- **Hover reasons.** An unusable option stays listed, unavailable with its reason. It is never hidden.
- **Lessons from earlier slices' tests:**
  - To read a parked decision, match `ParkedNode.Decision(_, decide, _)` and compare `decide.decisionId`. The case's first field is the procedure, not the decision id.
  - Board a site's ruler with `warbandsAt(site, player, 1)`, and assert the ruler (`PowerAccess.ruledSites`) before relying on it.
  - The quiet table (`Table.start`) holds no warbands, cards or relics at any site. p1's pawn is at Ancient City (Cradle, room for three, no Homeland). Deep Woods is the Beast Homeland with room for three, Broken Peaks has room for one, and Buried Giant has room for one. Read a site's region from `ready.game.current.map.regionOf(site)` rather than assuming it.
- **Note keys** are declared through a `NoteSet`, as plain `val`s after the set. Regenerate `src/test/resources/notes/declared-keys.txt` with `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`, and check that the diff holds only the keys the task names.
- **Names, exactly:**
  - `DecisionOptionRef.SiteRelicSlot(site: SiteId, index: Int)`, kind `"site-relic-slot"`, wire id `"{site}:{index}"`. `DecisionOption.SiteRelicSlot(ref)`.
  - `DecisionOption.Burying(option: DecisionOption, card: CardId)`.
  - `SiteRelicsProjection(relics: Vector[CardDetailsProjection])`.
  - `DecisionOptionProjection.buries: Boolean = false`, on the wire as `"buries"`.
  - `SearchPlacement.Site(site: SiteId, replace: Option[CardId])`.
  - `enum SiteRemoval { case Discard, Bury }`, and `PlaySites(opens: (ReadyGame, SiteId) => Boolean)`, both in `CardPlayRules.scala`.
  - `DiscardPermission(source, note: SiteId => Note, removal, appliesAt)` and `DiscardPermission.lined(...)`.
  - `CardPlayRules.playSites`, `openSites`, `opens`, and the note key `buryFirst` (`"bury-first"`).
  - `CardSlot(option, placement, buried: Option[CardId] = None, mayBury: Boolean = false)`, plus `CardSlots.playSites`, `CardSlots.moreBuries` and `MoreBuries(options)`.
  - `CardPlayProcedure.replacedDecisionId(card)` = `cardplay.replaced.{kind}.{value}` and `CardPlayProcedure.buryDecisionId(card)` = `cardplay.bury.{kind}.{value}`.
  - `CardPlayProcedure.buryReplaced = DecisionOptionRef.Button("bury")`, `CardPlayProcedure.discardReplaced = DecisionOptionRef.Button("discard")` and `CardPlayProcedure.siteOf(ref): Option[SiteId]`.
  - `Baron`, `NewGrowth` and `ForestWarden` in `gameplay/powers/search/`, each with `id`, `forCatalog`, and its card object moved beside it.

---

### Task 1: A relic at a site has a slot

A seam with no card behind it yet. A site relic gets a positional ref, and the projection and the board draw relics in their places.

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionDtos.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionCodec.scala`
- Modify: `src/main/scala/oathdigital/application/GamePresentationProjector.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/SiteCardPresentation.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardHomes.scala`
- Test: `src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala`
- Test: `src/test/scala/oathdigital/application/SlotOptionRowsSuite.scala`
- Test: `src/test/scala/oathdigital/application/GameApplicationServiceProjectionSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/ServerUiSupportSuite.scala`
- Test: `frontend/src/test/scala/oathdigital/frontend/BoardOptionsRenderSuite.scala`
- Update as the compiler and the wire tests demand: `frontend/src/test/scala/oathdigital/frontend/HttpGameClientSuite.scala`, `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala`, `src/test/scala/oathdigital/application/GameServerGatewaySubmitBeginSuite.scala`, `src/test/scala/oathdigital/application/GameApplicationServiceReopenSuite.scala`

**Interfaces:**
- Produces: `DecisionOptionRef.SiteRelicSlot(site, index)`, `DecisionOption.SiteRelicSlot(ref)`, `SiteRelicsProjection(relics)` with `facedownCount` and `knownRelics` kept as derived `def`s, and the board key `site-relic-slot:{site}:{index}` on every site relic.

- [ ] **Step 1: Write the failing tests**

In `DecisionOptionRefSuite.scala`, after the relic slot tests:

```scala
  private val siteRelic = DecisionOptionRef.SiteRelicSlot(
    CatalogNames.site("Broken Peaks"), 1)

  test("a site relic slot spells itself as a kind and site-and-index id " +
      "and parses back"):
    assertEquals((siteRelic.kind, siteRelic.wireId),
      ("site-relic-slot", s"${siteRelic.site.value}:1"))
    assertEquals(DecisionOptionRef.fromWire(siteRelic.kind,
      siteRelic.wireId), Some(siteRelic))

  test("a malformed site relic slot is not a reference"):
    Vector("peaks", "peaks:", ":1", "peaks:-1", "peaks:x").foreach { id =>
      assertEquals(DecisionOptionRef.fromWire("site-relic-slot", id), None,
        id)
    }

  test("a site relic slot is presentable from the reference alone"):
    assertEquals(DecisionOption.forRef(siteRelic),
      Some(DecisionOption.SiteRelicSlot(siteRelic)))
```

In `SlotOptionRowsSuite.scala`, add a board with relics and two tests. The pawn-site relic is known to p1 through `identifiesCard`'s pawn-site clause. The far one is not.

```scala
  private val horn = CatalogNames.relic("Cracked Horn")
  private val peaks = CatalogNames.site("Broken Peaks")

  /** Cracked Horn lies at Broken Peaks, where p2 stands and p1 does not. */
  private lazy val relicBoard: ReadyGame =
    Table.start.relicAt(horn, at = peaks).ready

  test("a site relic slot shows its relic to a viewer at the site, and a " +
      "back to anyone else"):
    val option = DecisionOption.SiteRelicSlot(
      DecisionOptionRef.SiteRelicSlot(peaks, 0))
    val index = CardIndex.from(relicBoard.game).toOption
    val near = present(projector.optionProjection(relicBoard, Some(p2),
      index, option), "p2's row")
    assertEquals(near.card.map(_.cardId), Some(horn.value))
    val far = present(projector.optionProjection(relicBoard, Some(p1),
      index, option), "p1's row")
    assert(far.card.exists(_.hidden), far.toString)
    assertEquals(far.label,
      s"Facedown relic at ${presentation.siteLabel(peaks)}")
    assert(!far.label.contains("Horn"), far.label)

  test("a site relic slot past the site's relics is not presentable"):
    assertEquals(projector.optionProjection(relicBoard, Some(p1), None,
      DecisionOption.SiteRelicSlot(DecisionOptionRef.SiteRelicSlot(peaks,
        1))), None)
```

Check first that p2's quiet-table pawn stands at Broken Peaks (`Table.quietPawnSite(p2)`). If it does not, move the relic to p2's site and name that site.

In `GameApplicationServiceProjectionSuite.scala`, rewrite the assertions around line 126 that read `facedownCount`. The site's relics now project as a vector in state order: one hidden back per relic the viewer has not peeked at, and the card for one they have. Assert that the vector has two entries, that both are `hidden`, and that the JSON holds `"relics":[` rather than `"facedownCount"`. Keep the test's intent and board.

In `ServerUiSupportSuite.scala`, rewrite the assertions at lines 83 to 86 for the new `SiteCardPresentation.relics`:

```scala
    assertEquals(owner.relics.count(_.hidden), 1)
    assertEquals(other.relics.count(_.hidden), 2)
    assertEquals(owner.relics.filterNot(_.hidden).map(_.name),
      Vector("Ancient Crown"))
    assertEquals(other.relics.filterNot(_.hidden), Vector.empty)
```

In `BoardOptionsRenderSuite.scala`, add one test next to "a site card answers to the slot it would replace". Build its site relics with this suite's site fixtures, the way its site-slot tests build denizens:

```scala
  test("a site relic, known or not, answers to its slot"):
    val pick = option("site-relic-slot", "woods:0")
    val shown = recording(available(pick))
    val marked = one(world(shown.options),
      """[data-option-ref="site-relic-slot:woods:0"]""")
    click(marked)
    assertEquals(shown.picks, Vector(pick))
```

The suite's table fixture must hold one hidden relic at `woods`. Add it where the fixture builds `woods`.

- [ ] **Step 2: Run them and watch them fail**

Run: `./sbtw "testOnly oathdigital.model.DecisionOptionRefSuite oathdigital.application.SlotOptionRowsSuite" "frontend/testOnly oathdigital.frontend.BoardOptionsRenderSuite"`
Expected: compile errors naming `SiteRelicSlot`.

- [ ] **Step 3: The ref and the option**

In `Decisions.scala`, add the ref after `SiteSlot`:

```scala
  /** Relic `index` of `site`'s relics, in their order there: a relic named
    * by its place rather than by the card, so a facedown relic's identity
    * is never disclosed by an option. The site may contain a colon; the
    * index is always the text after the last one.
    */
  final case class SiteRelicSlot(site: SiteId, index: Int)
      extends DecisionOptionRef:
    require(index >= 0, "a site relic slot must be non-negative")
    val kind: String = "site-relic-slot"
    def wireId: String = s"${site.value}:$index"
```

In `fromWire`, add the case beside `"site-slot"`, and change the doc's count from sixteen variants to seventeen:

```scala
      case "site-relic-slot" => indexOf(wireId).map((site, index) =>
        SiteRelicSlot(SiteId(site), index))
```

Add the option beside `RelicSlot`, and its `forRef` case:

```scala
  final case class SiteRelicSlot(ref: DecisionOptionRef.SiteRelicSlot)
      extends DecisionOption
```

```scala
    case value: DecisionOptionRef.SiteRelicSlot => Some(SiteRelicSlot(value))
```

The compiler lists every other exhaustive match over refs or options. Give each one a case that reads like its neighbours.

- [ ] **Step 4: The projection**

In `WorldProjectionDtos.scala`, replace `SiteRelicsProjection`:

```scala
/** A site's relics in their places, as the viewer may see them: a relic
  * the viewer knows is its card, any other a hidden back. A relic's index
  * here is its `site-relic-slot`. */
final case class SiteRelicsProjection(
    relics: Vector[CardDetailsProjection] = Vector.empty):
  /** Every relic at the site; site relics lie facedown. */
  def facedownCount: Int = relics.size
  /** The relics the viewer knows. */
  def knownRelics: Vector[CardDetailsProjection] = relics.filterNot(_.hidden)
```

In `WorldProjectionCodec.scala`, encode `ujson.Obj("relics" -> encoded(value.relics)(encodeCard))`. Decode with `exact(value, Set("relics"), path)` and a `relics` array that defaults to empty, decoding each card with `decodeCard` at `s"$path.relics"`.

In `GamePresentationProjector.scala` (around line 126), build the relics in state order:

```scala
      SiteRelicsProjection(state.toVector.flatMap(_.relics).map(relic =>
        val known = for
          game <- ready
          player <- viewer
        yield game.knowledge.siteRelics.getOrElse(player, Map.empty)
          .getOrElse(siteId, Vector.empty).contains(relic.id)
        if known.contains(true) then
          cardDetails(relic.id, Some(Orientation.FaceDown), hidden = false)
        else hiddenCard("relic"))),
```

This keeps today's rule, in which only a peeked relic is known on the board.

- [ ] **Step 5: The option row and the log word**

In `WalkerDecisionProjector.optionProjection`, add a case beside `RelicSlot`:

```scala
      // The relic when the viewer may identify it, else its back: a slot
      // never discloses a facedown relic.
      case DecisionOption.SiteRelicSlot(slot) =>
        ready.game.current.map.sites.get(slot.site)
          .flatMap(_.relics.lift(slot.index)).flatMap(held =>
            card(ready, viewer, index, held.id) match
              case Some(details) => row(details.name, Some(details))
              case None => row(
                s"Facedown relic at ${presentation.siteLabel(slot.site)}",
                Some(presentation.hiddenCard("relic"))))
```

In `ChoiceWords.option`, add:

```scala
    case DecisionOptionRef.SiteRelicSlot(site, slot) =>
      before.game.current.map.sites.get(site).flatMap(_.relics.lift(slot))
        .fold(Vector[LogSpan](Text(s"relic ${slot + 1}")))(held =>
          card(held.id, before, after, viewer)) ++
        Vector(Text(" at "), words.site(site))
```

- [ ] **Step 6: The board**

In `SiteCardPresentation.scala`, replace `unknownRelicCount` and `peekedRelics` with `relics: Vector[CardDetails]`, set from `site.relics.relics`. Delete `PeekedRelicPresentation` if nothing else uses it.

In `ServerUiSupport.scala`, replace the peeked and unknown relic loops (lines 76 to 84) with one positional loop. It sits beside `siteSlot`, with a `siteRelicSlot(site, at)` key helper:

```scala
    // Each relic in its place answers to its slot: a known one is its face,
    // an unknown one its back.
    presentation.relics.zipWithIndex.foreach((card, at) =>
      val slot = siteRelicSlot(site, at)
      cards.appendChild(
        if card.hidden then
          val back = facedownCard("relic")
          options.decorate(back, Vector(slot))
          back
        else options.card(CardFace.render(card), card,
          Vector(BoardOptions.cardKey(card), slot))))
    (presentation.relics.size until site.relicCapacity)
      .foreach(_ => cards.appendChild(emptySlot("relic")))
```

```scala
  private def siteRelicSlot(site: GameSite, at: Int): String =
    BoardOptionViews.key("site-relic-slot", s"${site.siteId}:$at")
```

In `BoardHomes.has`, add a case for the new kind, and read known site relics from the positional vector:

```scala
      case "site-relic-slot" =>
        slot(option.id).exists((site, at) => sites(value)
          .exists(found => found.siteId == site &&
            found.relics.relics.isDefinedAt(at)))
```

```scala
  private def siteRelics(value: GameProjection): Vector[String] =
    sites(value).flatMap(_.relics.knownRelics.map(_.cardId))
```

The second one keeps its body; `knownRelics` is now the derived `def`.

- [ ] **Step 7: Update the wire fixtures**

`HttpGameClientSuite`'s hand-written site JSON and `ProjectionProtocolSuite`'s malformed-field test read `facedownCount`.
- Write the HTTP fixture's relics as `"relics":{"relics":[…]}`, with hidden backs encoded as `encodeCard` writes `hiddenCard("relic")`.
- Point the protocol test's wrong type at `relics` (an object where an array belongs), with its path `$.world[0].sites[0].relics.relics`.
- `GameServerGatewaySubmitBeginSuite` and `GameApplicationServiceReopenSuite` compile unchanged through the derived `def`s. Run them to confirm.

- [ ] **Step 8: Run the tests**

Run: `./sbtw "testOnly oathdigital.model.DecisionOptionRefSuite oathdigital.application.*" "frontend/test" "sharedJVM/test"`. If the shared project has another name, take it from `build.sbt`.
Expected: all pass, with the 5 new server tests and 1 new frontend test.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/model/Decisions.scala \
  shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionDtos.scala \
  shared/src/main/scala/oathdigital/protocol/projection/WorldProjectionCodec.scala \
  src/main/scala/oathdigital/application/GamePresentationProjector.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala \
  frontend/src/main/scala/oathdigital/frontend/SiteCardPresentation.scala \
  frontend/src/main/scala/oathdigital/frontend/ServerUiSupport.scala \
  frontend/src/main/scala/oathdigital/frontend/BoardHomes.scala \
  src/test/scala/oathdigital/model/DecisionOptionRefSuite.scala \
  src/test/scala/oathdigital/application/SlotOptionRowsSuite.scala \
  src/test/scala/oathdigital/application/GameApplicationServiceProjectionSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/ServerUiSupportSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/BoardOptionsRenderSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/HttpGameClientSuite.scala \
  shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala
git commit -m "feat(batch8): a relic at a site has a slot"
```

Add any other file the compiler made you touch, by its path.

---

### Task 2: A card play names its site

This is the engine seam. A play goes to any site its rules open. Permissions belong to the site, and so does the discard pile of its site cards. A test rule stands in for the cards.

**Files:**
- Modify: `src/main/scala/oathdigital/model/ActionValues.scala` (`SearchPlacement.Site`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/search/CropRotation.scala`, `.../powers/search/LandWarden.scala`, `.../powers/banner/PeoplesFavorMob.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` (the follow-up heading)
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` (the "Played … to" site)
- Modify: `src/test/resources/notes/declared-keys.txt` (regenerated)
- Test: `src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala`, `src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala`, `src/test/scala/oathdigital/gameplay/PlacementFixture.scala`
- Update: `src/test/scala/oathdigital/gameplay/HomelandLineSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/search/CropRotationSuite.scala`, `src/test/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMobSuite.scala`, `src/test/scala/oathdigital/testkit/PlacementPicks.scala`, and every test the compiler names for `SearchPlacement.Site` or `DiscardPermission`.

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces:
  - `SearchPlacement.Site(site: SiteId, replace: Option[CardId])`.
  - In `CardPlayRules.scala`:
    - `enum SiteRemoval { case Discard, Bury }`
    - `final case class PlaySites(opens: (ReadyGame, SiteId) => Boolean)`
    - `final case class DiscardPermission(source: PowerId, note: SiteId => Note, removal: SiteRemoval = SiteRemoval.Discard, appliesAt: (ReadyGame, SiteId) => Boolean = DiscardPermission.anySite)`
    - `DiscardPermission.lined(source: PowerId, from: PowerSourceRef, removal: SiteRemoval = SiteRemoval.Discard, appliesAt: (ReadyGame, SiteId) => Boolean = anySite): DiscardPermission`
    - `CardPlayRules.playSites: Vector[PlaySites]`, `openSites(sites: PlaySites)` and `opens(ready, site): Boolean`
    - `CardPlayRules.homeland(site: SiteId): DiscardPermission`
    - `CardPlayRules.discardFirst`, now "May discard a card at {0} first.", and `CardPlayRules.buryFirst`, "May bury a card at {0} first."
  - In `CardSlots`:
    - `CardSlot(option, placement, buried: Option[CardId] = None, mayBury: Boolean = false)` with `site: Option[SiteId]`
    - `CardSlots.playSites(ready, player, rules): Vector[SiteId]`
    - `CardSlots.permissions(catalog, ready, actor, card, site, rules)`
    - `CardSlots.moreDiscards(catalog, ready, actor, card, origin, rules, slot, buriedReplaced: Boolean = false, picked = Vector.empty)`
    - `CardSlots.credits(catalog, ready, actor, card, site, rules, discarded, buried: Option[CardId] = None)`
  - `CardPlay.operations(…, more: Vector[CardId] = Vector.empty, buried: Option[CardId] = None)`. This task only threads `buried` through; Task 4 uses it.
  - `CardPlayProcedure.siteOf(ref: DecisionOptionRef): Option[SiteId]`.

- [ ] **Step 1: Write the failing tests**

In `PlacementFixture.scala`, build the plain permissions with the new shape, and add a stub rule that opens one site:

```scala
  /** A plain site discard permission named `name`, of any card at any
    * site, whose line says nothing. */
  def plainPermission(name: String): DiscardPermission = DiscardPermission(
    PowerId(name), _ => Note(PowerId(name), _ => None))

  /** Leave to play to `site` as well as the pawn site. */
  def opening(site: SiteId): PlaySites = PlaySites((_, at) => at == site)
```

In `CardSlotsSuite.scala`, add these tests. They reuse the suite's `home`, `woods`, `card` (Errand Boy, Beast), `binding`, `wrestlers`, `rainBoots`, `slotsOf`, `options`, `siteSlot` and `replacing`. Read `atHome`'s definition first: it stands p1 at Ancient City with given cards there.

```scala
  private val giant = CatalogNames.site("Buried Giant")
  private val openWoods =
    CardPlayRules.default.openSites(PlacementFixture.opening(woods))

  test("a rule that opens another site offers its empty slot after the " +
      "pawn site's"):
    val sites = options(slotsOf(atHome().hand(p1, card).ready,
      rules = openWoods)).filter(_.ref.isInstanceOf[DecisionOptionRef.SiteSlot])
    assertEquals(sites, Vector(siteSlot(0), siteSlot(0, at = woods)))

  test("a full opened site offers nothing without a permission, and its " +
      "Replace options with one"):
    /** An Order card: no Homeland rule at Deep Woods. */
    val honor = CatalogNames.denizen("Code of Honor")
    val full = Vector(binding, wrestlers, rainBoots).foldLeft(
      atHome().hand(p1, card).hand(p1, honor))((t, held) =>
      t.denizen(held, at = woods)).ready
    def atWoods(played: DenizenId) = options(slotsOf(full, played,
      rules = openWoods)).filter(_.ref match
        case DecisionOptionRef.SiteSlot(`woods`, _) => true
        case _ => false)
    assertEquals(atWoods(honor), Vector.empty)
    // Errand Boy is Beast and Deep Woods the Beast Homeland: the Homeland
    // rule's permission applies at the site played to.
    assertEquals(atWoods(card), Vector(
      replacing(siteSlot(0, at = woods), binding),
      replacing(siteSlot(1, at = woods), wrestlers),
      replacing(siteSlot(2, at = woods), rainBoots)))

  test("a permission scoped to one site applies there and nowhere else"):
    val scoped = openWoods.permitSiteDiscard(testPermission.copy(
      appliesAt = (_, site) => site == woods))
    val ready = atHome(rainBoots).hand(p1, CatalogNames.denizen("Wrestlers"))
      .denizen(binding, at = woods).ready
    val replaced = slotsOf(ready, CatalogNames.denizen("Wrestlers"),
      rules = scoped).flatMap(_.replacedAtSite)
    assertEquals(replaced, Vector[CardId](binding))

  test("a site card discarded at another site goes to the pile after that " +
      "site's region, and the play's favor and hook are the site's"):
    val ready = atHome().hand(p1, card).denizen(binding, at = giant).ready
    val operations = accepted(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(giant, Some(binding)),
      CardPlay.Origin.TemporaryHand))
    val pile = present(ready.game.current.map.regionOf(giant)
      .map(CardPlay.nextRegion), "Buried Giant's next region")
    assertEquals(operations.collect { case d: Discard.Denizen => d.to },
      Vector(pile))
    assertNotEquals(Some(pile), CardPlay.discardRegion(ready, p1))
    assert(operations.exists {
      case Play(`card`, _, PositionedLocation(Location.Site(`giant`), _, _),
          _, _) => true
      case _ => false
    }, operations.toString)
    assertEquals(CardPlay.playedSource(ready, p1, card,
      SearchPlacement.Site(giant, None)),
      Some(RuleSourceRef.SiteCard(giant, card)))
```

The second test assumes the Homeland check reads the effective suit. Errand Boy's printed suit is Beast and nothing pledges it, so the answer is the same either way. Check `Play`'s field order and the `PositionedLocation` arity on `main`, and adjust the pattern to match.

In `CardPlayProcedureSuite.scala`, add one test for the follow-up at another site, with a stub rule that opens Deep Woods and grants two permissions:

```scala
  test("a Replace at another site asks its follow-up over that site's " +
      "cards"):
    val woods = CatalogNames.site("Deep Woods")
    val opensWoods = rulePower("test.opens-woods")(_
      .openSites(PlacementFixture.opening(woods))
      .permitSiteDiscard(testPermission).permitSiteDiscard(testPermission))
    val ready = others.foldLeft(atHome.hand(actor, card))(
      _.denizen(_, at = woods)).ready
    val parked = parkOnPlacement(ready, WalkerPowers(Vector(opensWoods)))
    val asked = Outcomes.parked(parked.place(
      DecisionOptionRef.SiteSlot(woods, 0)))
    val more = present(ProcedureWalker.parkedDecide(parked.ready,
      parked.tree, asked.tree, parked.powers), "the follow-up")
    assertEquals(more.decisionId, discardId)
    val done = Outcomes.finished(parked.resolve(asked.tree, discardId,
      DecisionAnswer.ChooseManyAnswer(Vector(
        DecisionOptionRef.Denizen(others(1))))))
    assertEquals(Look(done.treeless).cardsAt(woods),
      Vector[CardId](others(2), card))
    assertEquals(Look(done.treeless).cardsAt(home), Vector.empty[CardId])
```

Here the Homeland gives a third permission, so the follow-up allows up to 2. Don't assert the cap; the test only checks the site.

Update the lines in `HomelandLineSuite` (line 46), `CropRotationSuite` (both "discard-first" expectations) and `PeoplesFavorMobSuite` (line 207). For example:

```scala
    s"May discard a card at ${homeSite.value} first.", covers = false))
```

Use each suite's own site value, such as `home.value` in `CropRotationSuite`.

- [ ] **Step 2: Run them and watch them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.CardPlayProcedureSuite"`
Expected: compile errors naming `openSites`, `PlaySites` and `SearchPlacement.Site`'s arity.

- [ ] **Step 3: The placement and the rules**

In `ActionValues.scala`:

```scala
  /** A play to `site`'s row, discarding `replace` there first to make
    * room when it names a card. */
  final case class Site(site: SiteId, replace: Option[CardId])
      extends SearchPlacement
```

In `CardPlayRules.scala`, replace `DiscardPermission` and add `SiteRemoval` and `PlaySites`:

```scala
/** How a permission removes a card from the site a card is played to:
  * discarded, or buried (Forest Warden). */
enum SiteRemoval:
  case Discard, Bury

/** Leave to remove one card from the site a card is played to, before the
  * play. Any card of the site's card list may go: an edifice is a denizen
  * for placement. A discard of an intact edifice is refused as a locked
  * card's; a bury ignores locks, and may take a relic there too.
  *
  * `appliesAt` says at which sites the leave holds (Baron's: the sites its
  * user rules). `source` is the power or rule that gives it, and `note`
  * the line card play writes at the site when a removal it allowed is
  * made.
  */
final case class DiscardPermission(source: PowerId, note: SiteId => Note,
    removal: SiteRemoval = SiteRemoval.Discard,
    appliesAt: (ReadyGame, SiteId) => Boolean = DiscardPermission.anySite)

object DiscardPermission:
  val anySite: (ReadyGame, SiteId) => Boolean = (_, _) => true

  /** A permission from `from` whose line is its removal's: "May discard a
    * card at {site} first." or "May bury a card at {site} first." */
  def lined(source: PowerId, from: PowerSourceRef,
      removal: SiteRemoval = SiteRemoval.Discard,
      appliesAt: (ReadyGame, SiteId) => Boolean = anySite)
      : DiscardPermission =
    val key = removal match
      case SiteRemoval.Discard => CardPlayRules.discardFirst
      case SiteRemoval.Bury => CardPlayRules.buryFirst
    DiscardPermission(source,
      site => Note(source, _ => Some(key(from, NoteArg.Site(site)))),
      removal, appliesAt)

/** Leave to play to sites other than the pawn's: each site `opens` holds
  * of (Baron's, the sites its user rules; New Growth's, every site). */
final case class PlaySites(opens: (ReadyGame, SiteId) => Boolean)
```

In `CardPlayRules`:
- Add `playSites: Vector[PlaySites] = Vector.empty` as the last field.
- Document it in the class comment: "`playSites` are the leaves to play to sites other than the pawn's. Card play offers the pawn site, then every site one opens, in map order. Each site follows the pawn site's rules."
- Add:

```scala
  /** Adds leave to play to the sites `sites` opens. */
  def openSites(sites: PlaySites): CardPlayRules =
    copy(playSites = playSites :+ sites)

  /** Whether some leave opens `site`. */
  def opens(ready: ReadyGame, site: SiteId): Boolean =
    playSites.exists(_.opens(ready, site))
```

In the companion, replace `discardFirst` and `homeland`, and add `buryFirst`:

```scala
  /** "May discard a card at {site} first.": the line of the power or rule
    * that let a play to a site discard a card there. */
  val discardFirst: NoteKey = notes.key("discard-first", Vector(
    NotePart.Text("May discard a card at "), NotePart.Arg(0),
    NotePart.Text(" first.")))
  /** "May bury a card at {site} first.": the line of the power that let a
    * play to a site bury a card there (Forest Warden). */
  val buryFirst: NoteKey = notes.key("bury-first", Vector(
    NotePart.Text("May bury a card at "), NotePart.Arg(0),
    NotePart.Text(" first.")))

  /** The Homeland rule's permission (CR p. 31) for a play to `site`, the
    * Homeland of the played card's suit: one discard of any card there,
    * whatever that card's suit. */
  def homeland(site: SiteId): DiscardPermission =
    DiscardPermission.lined(RuleNotes.homelandDiscard,
      PowerSourceRef.Site(site), appliesAt = (_, at) => at == site)
```

Rewrite `CropRotation.permission` and `PeoplesFavorMob.permission` with `DiscardPermission.lined(id, PowerSourceRef.Card(cardId))` and `DiscardPermission.lined(id, PowerSourceRef.Banner(Banner.PeoplesFavor))`. Drop their `actor` parameter, and change their doc comments' quoted line to "May discard a card at {site} first.". In `LandWarden.siteRule`, match `case SearchPlacement.Site(_, _) => true`.

- [ ] **Step 4: `CardPlay` reads the placement's site**

In `CardPlay.scala`:

- **`playedSource`** reads the placement's site:

  ```scala
      case SearchPlacement.Site(site, _) =>
        Some(RuleSourceRef.SiteCard(site, card))
  ```

- **Removals.** Replace `PlacementPlan.siteDiscards` with `fromSite: Vector[Removed]`. `Removed` is a private case class: a card at the site that the play removes first, with what it carries, and whether it is buried.

  ```scala
    /** A card at the play's site the play removes first: discarded, or
      * buried when `buried`. */
    private final case class Removed(card: CardId, tokens: Tokens,
        buried: Boolean)
  ```

- **`plan`, `SearchPlacement.Site(siteId, replace)` case.**
  - Look the site up with `ready.game.current.map.sites.get(siteId)`, refusing with `InvalidSearchPlacement("the site is not in play")` when the map lacks it or `inPlay` does not list it.
  - Read the capacity from `siteId`.
  - Look `replace` up with `rowAt(ready, Some(siteId), replace.toVector)`, below.
  - `sitePlan` takes `siteId` as now.
  - Drop the `pawnSite` read from this case.

- **`atSite` becomes `rowAt` and `buryableAt`.**
  - `rowAt(ready, site: Option[SiteId], ids)` returns `Vector[Removed]` with `buried = false` for the named cards in the site's row. It keeps the "a discarded card is not at the site, or is named twice" refusal, and with no site it refuses with "nothing to discard: the play is not to a site" whenever `ids` is not empty.
  - `buryableAt(ready, site, id)` returns one `Removed` with `buried = true`, for a row card or a relic at the site. Otherwise it refuses with `InvalidSearchPlacement("a buried card is not at the site")`.

- **`operations`.** Give it `buried: Option[CardId] = None` after `more`, and build the removals in this order: the replaced card (buried when `buried` names it), the extra discards, then the extra bury.

  ```scala
      site = placement match
        case SearchPlacement.Site(at, _) => Some(at)
        case _ => None
      extra <- rowAt(ready, site, more)
      _ <- Either.cond(!extra.exists(e => plan.fromSite.exists(_.card ==
        e.card)), (), InvalidSearchPlacement("a site card is discarded twice"))
      bury <- buried.filterNot(id => plan.fromSite.exists(_.card == id))
        .fold[Either[OathViolation, Option[Removed]]](Right(None))(id =>
          buryableAt(ready, site, id).map(Some(_)))
      _ <- Either.cond(!bury.exists(b => extra.exists(_.card == b.card)), (),
        InvalidSearchPlacement("a site card is both discarded and buried"))
      removed = plan.fromSite.map(r =>
        if buried.contains(r.card) then r.copy(buried = true) else r) ++
        extra ++ bury.toVector
      operations <- operationsOf(catalog, ready, player, card, placement,
        origin, plan.copy(fromSite = removed))
  ```

- **`operationsOf`.**
  - Keep the pawn's next region for the play-area discards.
  - Build the site removals from the placement's site. A discard goes to `nextRegion` of that site's region. A bury is `Bury.standard`, with the card's suit from `catalog.suitOf` (a relic has none, and carries no favor).
  - Make `each` generic, `each[A, B](items)(f: A => Either[OathViolation, B])`, and flatten the bury's vector.

  ```scala
    /** A card the play removes from `from`, `site`'s row or relics: a row
      * card discarded to `region`'s pile with its tokens returned (a ruined
      * edifice back to its deck), or any card there buried with them
      * returned. */
    private def removal(catalog: ExecutableCatalog, actor: PlayerId,
        from: PositionedLocation, region: Region)(removed: Removed)
        : Either[OathViolation, Vector[CoreOperation]] =
      val Removed(card, tokens, buried) = removed
      if buried then buryable(card).map(target => Bury.standard(target, from,
        catalog.suitOf(card), tokens.favor, tokens.secrets, actor))
      else suitOf(catalog, card).flatMap(suit => card match
        case id: DenizenId => Right(Vector(Discard.Denizen(id, from, region,
          suit, tokens.favor, tokens.secrets, actor, required = true)))
        case id: EdificeId => Right(Vector(Discard.RuinedEdifice(id, from,
          suit, tokens.favor, tokens.secrets, actor, required = true)))
        case _ => Left(InvalidSearchPlacement(
          "only a card of the site's row is discarded")))

    private def buryable(card: CardId): Either[OathViolation, BuryableCard] =
      card match
        case id: DenizenId => Right(BuryableCard.Denizen(id))
        case id: EdificeId => Right(BuryableCard.Edifice(id))
        case id: RelicId => Right(BuryableCard.Relic(id))
        case _ => Left(InvalidSearchPlacement("the card cannot be buried"))
  ```

  Keep the order of the returned vector: site removals, play-area discards, the play, then the favor.

- **The object's doc comment.** Change "a discard of a card that is not there" to "a discard or bury of a card that is not at the site played to". Say that a site card discarded in a play goes to the pile after the site's region.

- [ ] **Step 5: `CardSlots` offers every opened site**

In `CardSlots.scala`:

- **`CardSlot`** gains `buried` and `mayBury` (Task 4 sets them) and `site`:

  ```scala
  /** One way to play a card: the option the placement decision offers, and
    * where the planner puts the card when it is chosen. A Replace option's
    * placement names the card it removes. `buried` is the card the play
    * buries ("Bury and replace" or "Bury and play"). `mayBury` marks a
    * Replace whose card may be buried instead of discarded: the play asks
    * which.
    */
  final case class CardSlot(option: DecisionOption, placement: SearchPlacement,
      buried: Option[CardId] = None, mayBury: Boolean = false):
    def ref: DecisionOptionRef = option.ref

    /** The site card this play removes to make room, for a Replace site
      * slot. */
    def replacedAtSite: Option[CardId] = placement match
      case SearchPlacement.Site(_, replaced) => replaced
      case _ => None

    /** The site the card is played to, for a site slot. */
    def site: Option[SiteId] = placement match
      case SearchPlacement.Site(at, _) => Some(at)
      case _ => None
  ```

- **`slots`.** The pawn site's slots are now `playSites(ready, player, rules).flatMap(site => siteSlots(catalog, ready, player, card, site, rules))`. These pairs are judged as every other candidate is.

- **`playSites`**:

  ```scala
    /** The sites `player` may play to under `rules`: the pawn site, then
      * each other site in play that a leave opens, in map order. */
    def playSites(ready: ReadyGame, player: PlayerState,
        rules: CardPlayRules): Vector[SiteId] =
      val pawn = player.pawnSite.toVector
      pawn ++ ready.game.current.map.inPlay.filter(site =>
        !pawn.contains(site) && rules.opens(ready, site))
  ```

- **`siteSlots`** takes the site instead of reading `pawnSite`, and keeps its body otherwise. `permissions` takes it too.

- **`permissions`**:

  ```scala
    /** The removal permissions of `actor`'s play of `card` to `site`: the
      * Homeland rule's first, when `site` is the Homeland of the card's
      * effective suit, then the powers' that hold there. */
    def permissions(catalog: ExecutableCatalog, ready: ReadyGame,
        actor: PlayerId, card: WorldCardId, site: SiteId,
        rules: CardPlayRules): Vector[DiscardPermission] =
      val homeland = for
        suit <- EffectiveSuit.of(catalog, ready, card)
        if CardPlay.homelandSuit(catalog, site).contains(suit)
      yield CardPlayRules.homeland(site)
      homeland.toVector ++ rules.siteDiscards.filter(_.appliesAt(ready, site))
  ```

  `actor` is no longer read here. Drop the parameter if `-Wunused` flags it, and update the callers.

- **`moreDiscards`** reads the slot's site, its row (`rowCards(ready, site)` replaces `siteCards`), and the discard permissions there. Add `buriedReplaced: Boolean = false` before `picked`. The discards left are the discard permissions less one, unless the replaced card was buried; offer the follow-up when at least one is left. Plan each option with `CardPlay.operations(…, Vector(other.id), buried = Option.when(buriedReplaced)(replacedId))`.

- **`credits`** takes the site and an optional bury. Discards spend discard permissions in order; a bury spends a bury permission:

  ```scala
    def credits(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
        card: WorldCardId, site: SiteId, rules: CardPlayRules,
        discarded: Vector[CardId], buried: Option[CardId] = None)
        : Either[OathViolation, Vector[Note]] =
      val permitted = permissions(catalog, ready, actor, card, site, rules)
      val discards = permitted.filter(_.removal == SiteRemoval.Discard)
      val buries = permitted.filter(_.removal == SiteRemoval.Bury)
      for
        _ <- Either.cond(discarded.size <= discards.size, (),
          OathViolation.InvalidSearchPlacement("more site cards are " +
            "discarded than the permissions in play allow"))
        _ <- Either.cond(buried.size <= buries.size, (),
          OathViolation.InvalidSearchPlacement("more site cards are buried " +
            "than the permissions in play allow"))
      yield (discards.take(discarded.size) ++ buries.take(buried.size))
        .map(_.note(site))
  ```

- **Doc comment.** Update the object's comment: the site slots are the pawn site's, then each opened site's, in map order.

In `siteSlots`, offer a Replace only when a discard permission holds there. Task 4 adds the bury forms:

```scala
      val discards = permitted.exists(_.removal == SiteRemoval.Discard)
      val replacing = if !discards then Vector.empty else …
```

- [ ] **Step 6: The procedure, the heading and the log line**

In `CardPlayProcedure.scala`:
- Add `siteOf`:

  ```scala
    /** The site a placement option plays the card to, for a site slot or a
      * site relic's bury. */
    def siteOf(ref: DecisionOptionRef): Option[SiteId] = ref match
      case DecisionOptionRef.SiteSlot(site, _) => Some(site)
      case DecisionOptionRef.SiteRelicSlot(site, _) => Some(site)
      case _ => None
  ```

- `playedAs` maps a `SiteRelicSlot` to `PlayedAs.AtSite`.
- `settled` builds its placement from the ref: `siteOf(ref).map(SearchPlacement.Site(_, None))`, else `placementOf(way)`. The `AtSite` arm of `placementOf` can no longer name a site, so remove it, and let `settled` use `siteOf` for `AtSite`.
- In `played`, call `credits` with the slot's site, and with no credits when the slot has none:

  ```scala
        val credited = slot.site.fold[Either[OathViolation, Vector[Note]]](
          Right(Vector.empty))(site => CardSlots.credits(catalog, ready, actor,
          card, site, rules, slot.replacedAtSite.toVector ++ extra))
  ```

In `WalkerDecisionProjector.headed`, the `cardplay.discard.` heading names the site of the follow-up's own cards, no longer the pawn's:

```scala
      else if decisionId.startsWith("cardplay.discard.") then
        followUpSite(ready, projected).map(site =>
          s"Discard more cards from ${presentation.siteLabel(site)}")
```

```scala
  /** The site a card play's follow-up is about: the site holding the cards
    * it offers, its locked card among them. */
  private def followUpSite(ready: ReadyGame,
      query: DecisionQueryProjection): Option[SiteId] =
    val rows = query match
      case many: DecisionQueryProjection.ChooseMany => many.options
      case _ => Vector.empty
    rows.iterator.flatMap(row =>
      DecisionOptionRef.fromWire(row.kind, row.id).flatMap {
        case DecisionOptionRef.SiteRelicSlot(site, _) => Some(site)
        case other => ready.game.current.map.sites.collectFirst {
          case (site, state)
              if state.denizens.exists(_.id.value == row.id) => site }
      }).nextOption()
```

In `ActionLines.playedAdviser`, pass the placement `ref` to `played`. Its `AtSite` arm names `CardPlayProcedure.siteOf(ref)` instead of `LogJournal.pawnSite(before, actor)`:

```scala
      case PlayedAs.AtSite => action(Vector(Text("Played ")) ++ named ++
        CardPlayProcedure.siteOf(ref).toVector.flatMap(site =>
          Vector(Text(" to "), words.site(site))))
```

Remove any import or parameter this leaves unused.

In `PlacementPicks.scala`, no change is needed yet; Task 4 extends `replaces`.

- [ ] **Step 7: Regenerate the note keys and run the tests**

Run: `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected diff in `declared-keys.txt`:
- every `discard-first` line now reads `May discard a card at {0} first.`;
- each `NoteSet` that includes `CardPlayRules.notes` gains a `bury-first = May bury a card at {0} first.` line.

Nothing else may change.

Run: `./sbtw "test"`
Expected: all pass, with the 5 new tests. Fix every suite the compiler names for the new `SearchPlacement.Site`, `DiscardPermission`, `permissions` and `credits` shapes. A changed expectation is legitimate only where these rulings change behaviour: the line wording and the Homeland's site. Anything else is a bug to fix in main code.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/model/ActionValues.scala \
  src/main/scala/oathdigital/gameplay/actions/CardPlayRules.scala \
  src/main/scala/oathdigital/gameplay/actions/CardPlay.scala \
  src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala \
  src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala \
  src/main/scala/oathdigital/gameplay/powers/search/CropRotation.scala \
  src/main/scala/oathdigital/gameplay/powers/search/LandWarden.scala \
  src/main/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMob.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/application/gamelog/ActionLines.scala \
  src/test/resources/notes/declared-keys.txt \
  src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala \
  src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala \
  src/test/scala/oathdigital/gameplay/PlacementFixture.scala \
  src/test/scala/oathdigital/gameplay/HomelandLineSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/search/CropRotationSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/banner/PeoplesFavorMobSuite.scala
git commit -m "feat(batch8): a card play names its site"
```

Add any other file the compiler made you touch, by its path.

---

### Task 3: Baron and New Growth

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/search/Baron.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/search/NewGrowth.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` (remove `BaronCard`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `NewGrowthCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/test/resources/notes/declared-keys.txt` (regenerated)
- Modify: `src/test/scala/oathdigital/gameplay/powers/SearchFixture.scala` (one helper)
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (one script)
- Create: `src/test/scala/oathdigital/gameplay/powers/search/BaronSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/search/NewGrowthSuite.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`

**Interfaces:**
- Consumes: from Task 2, `PlaySites`, `CardPlayRules.openSites`, `DiscardPermission.lined` (`appliesAt`) and `PowerAccess.ruledSites`.
- Produces: `Baron.id`, `Baron.forCatalog`, `NewGrowth.id`, `NewGrowth.forCatalog` and `NewGrowth.suits: Set[Suit]`.

- [ ] **Step 1: The fixture helper**

In `SearchFixture.scala`, after `placeReplacing`:

```scala
  /** Answers the parked placement of `card` with `ref`: a slot at another
    * site, which `place` would not pick. */
  def placeAt(from: OathTransition, card: WorldCardId,
      ref: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.resolveWalker(from.state, actor,
      PlacementFixture.cardPlayDecisionId(card, "place"),
      DecisionAnswer.ChooseOneAnswer(ref))
```

- [ ] **Step 2: Write the failing suites**

`BaronSuite.scala` follows `CropRotationSuite`'s shape:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.CardPlayRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PlacementPicks, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Baron (card 250), a selected Search modifier: "You may play to any site
  * you rule, and you may discard a card from that site first." */
class BaronSuite extends munit.FunSuite:
  import SearchFixture.{keep, placeAt, placeReplacing, rules, start}

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val baron = CatalogNames.denizen("Baron")
  private val modifiers = Vector(Baron.id)
  /** The plain Beast card each Search keeps and plays. */
  private val played = CatalogNames.denizen("Errand Boy")
  private val fillers = Vector("Code of Honor", "Messenger", "Shield Wall")
    .map(CatalogNames.denizen(_))
  private val home = CatalogNames.site("Ancient City")
  /** Room for one, no Homeland. */
  private val giant = CatalogNames.site("Buried Giant")
  private val peaks = CatalogNames.site("Broken Peaks")

  /** p1 in Act at Ancient City holding Baron faceup, with 5 Supply and
    * `played` on the world deck. p1 rules Buried Giant, which holds
    * `atGiant`; p2 rules Broken Peaks. */
  private def ruling(atGiant: Vector[DenizenId]): Table =
    atGiant.foldLeft(Table.start.pawn(p1, home).worldDeckTop(played)
      .supply(p1, 5).adviser(p1, baron).warbandsAt(giant, p1, 1)
      .warbandsAt(peaks, p2, 1))((t, card) => t.denizen(card, at = giant))

  private def toPlacement(ready: ReadyGame,
      selected: Vector[PowerId] = modifiers): OathTransition = accepted(for
    started <- start(ready, selected)
    kept <- keep(started, played)
  yield kept.copy(events = started.events ++ kept.events))

  private def slotsOf(at: OathTransition): Vector[(DecisionOptionRef,
      Option[CardId])] =
    PlacementPicks.siteSlotOptions(PlacementPicks.parkedOptions(at.state,
      walkerPowers)).map(option => option.ref -> PlacementPicks.replaces(
      option))

  test("selected, it offers each site the player rules after the pawn " +
      "site, and no site of another ruler"):
    val ready = ruling(Vector.empty).ready
    assertEquals(PowerAccess.ruledSites(ready, p1), Set(giant))
    assertEquals(slotsOf(toPlacement(ready)), Vector(
      DecisionOptionRef.SiteSlot(home, 0) -> None,
      DecisionOptionRef.SiteSlot(giant, 0) -> None))

  test("a full ruled site offers its card as Replace"):
    assertEquals(slotsOf(toPlacement(ruling(fillers.take(1)).ready)), Vector(
      DecisionOptionRef.SiteSlot(home, 0) -> None,
      DecisionOptionRef.SiteSlot(giant, 0) -> Some(fillers(0))))

  test("without the selection only the pawn site is offered"):
    assertEquals(slotsOf(toPlacement(ruling(Vector(fillers(0))).ready,
      Vector.empty)), Vector(DecisionOptionRef.SiteSlot(home, 0) -> None))

  test("its discard holds at a pawn site the player rules too"):
    val ready = ruling(Vector.empty).warbandsAt(home, p1, 1)
      .denizen(fillers(1), at = home).ready
    assert(slotsOf(toPlacement(ready)).contains(
      DecisionOptionRef.SiteSlot(home, 0) -> Some(fillers(1))))

  test("a full ruled site takes the play through Replace: the discard goes " +
      "to the pile after that site's region, with Baron's line"):
    val ready = ruling(fillers.take(1)).ready
    val asked = toPlacement(ready)
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite,
      fillers(0)))
    val end = SearchFixture.after(done)
    assertEquals(Look(end).cardsAt(giant), Vector[CardId](played))
    assert(Look(end).discardAfter(giant).contains(fillers(0)))
    assertEquals(NoteText.said(Baron.id, Vector(CardPlayRules.discardFirst),
      done.events), Vector(NoteText.Said("discard-first",
      s"May discard a card at ${giant.value} first.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assertWireRoundTrips(asked.events ++ done.events)

  test("an empty slot at a ruled site plays there and discards nothing"):
    val asked = toPlacement(ruling(Vector.empty).ready)
    val done = accepted(placeAt(asked, played,
      DecisionOptionRef.SiteSlot(giant, 0)))
    assertEquals(Look(SearchFixture.after(done)).cardsAt(giant),
      Vector[CardId](played))
    assertEquals(NoteText.said(Baron.id, Vector(CardPlayRules.discardFirst),
      done.events), Vector.empty)
```

`NewGrowthSuite.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.FacedownAdviserPlay
import oathdigital.gameplay.actions.CardPlayRules
import oathdigital.gameplay.powers.{NoteText, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PlacementPicks, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** New Growth (card 188), a selected Search modifier: "You may play
  * [suit-beast] and [suit-hearth] cards to any site (that has space)." */
class NewGrowthSuite extends munit.FunSuite:
  import SearchFixture.{keep, placeAt, rules, start}

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val newGrowth = CatalogNames.denizen("New Growth")
  private val crop = CatalogNames.denizen("Crop Rotation")
  private val beast = CatalogNames.denizen("Errand Boy")
  private val hearth = SearchFixture.anyPlainDenizensOf(Suit.Hearth).head
  private val order = SearchFixture.anyPlainDenizensOf(Suit.Order).head
  private val filler = CatalogNames.denizen("Code of Honor")
  private val home = CatalogNames.site("Ancient City")
  /** Room for one. */
  private val peaks = CatalogNames.site("Broken Peaks")
  private val woods = CatalogNames.site("Deep Woods")

  /** p1 in Act at Ancient City holding New Growth faceup, with 5 Supply and
    * `top` on the world deck. Broken Peaks is full. */
  private def growing(top: DenizenId): Table = Table.start.pawn(p1, home)
    .worldDeckTop(top).supply(p1, 5).adviser(p1, newGrowth)
    .denizen(filler, at = peaks)

  private def toPlacement(ready: ReadyGame, card: DenizenId,
      selected: Vector[PowerId] = Vector(NewGrowth.id)): OathTransition =
    accepted(for
      started <- start(ready, selected)
      kept <- keep(started, card)
    yield kept.copy(events = started.events ++ kept.events))

  private def siteSlotRefs(at: OathTransition): Vector[DecisionOptionRef] =
    PlacementPicks.siteSlotOptions(PlacementPicks.parkedOptions(at.state,
      walkerPowers)).map(_.ref)

  test("selected, a Beast card may go to every site with room, after the " +
      "pawn site in map order; a full site has no option"):
    val ready = growing(beast).ready
    val expected = DecisionOptionRef.SiteSlot(home, 0) +:
      ready.game.current.map.inPlay.filterNot(Set(home, peaks))
        .map(DecisionOptionRef.SiteSlot(_, 0))
    assertEquals(siteSlotRefs(toPlacement(ready, beast)), expected)

  test("a Hearth card may go too, and a card of another suit only to the " +
      "pawn site"):
    assert(siteSlotRefs(toPlacement(growing(hearth).ready, hearth))
      .contains(DecisionOptionRef.SiteSlot(woods, 0)))
    assertEquals(siteSlotRefs(toPlacement(growing(order).ready, order)),
      Vector(DecisionOptionRef.SiteSlot(home, 0)))

  test("with a discard permission, a full site offers its Replace"):
    val ready = growing(beast).adviser(p1, crop).ready
    val options = PlacementPicks.parkedOptions(toPlacement(ready, beast,
      Vector(NewGrowth.id, CropRotation.id)).state, walkerPowers)
    assert(options.exists(option =>
      option.ref == DecisionOptionRef.SiteSlot(peaks, 0) &&
        PlacementPicks.replaces(option).contains(filler)), options.toString)

  test("played at another site, the card stays there with its favor, and " +
      "replays"):
    val ready = growing(beast).ready
    val asked = toPlacement(ready, beast)
    val done = accepted(placeAt(asked, beast,
      DecisionOptionRef.SiteSlot(woods, 0)))
    val end = SearchFixture.after(done)
    assertEquals(Look(end).cardsAt(woods), Vector[CardId](beast))
    assertEquals(Look(end).cardsAt(home), Vector.empty[CardId])
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assertWireRoundTrips(asked.events ++ done.events)

  test("it applies to a facedown adviser played to another site"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, newGrowth)
      .adviser(p1, beast, facedown = true).ready
    val started = accepted(FacedownAdviserPlay.parkedOnPlacement(rules,
      Ready(ready), p1, beast, Vector(NewGrowth.id)))
    assert(siteSlotRefs(started).contains(DecisionOptionRef.SiteSlot(woods,
      0)))
```

`NoteText` and `CardPlayRules` may then be unused in `NewGrowthSuite`; drop them if so. If Code of Honor's suit makes Broken Peaks a Homeland match, pick another plain filler from `SearchFixture.anyPlainDenizensOf`. Errand Boy's favor is checked in Baron's suite, so leave it out here.

In `LogScripts.scala`, add a script beside `facedownAdviser`:

```scala
  /** p1 holds New Growth faceup and a facedown Beast adviser, which the
    * Play-Facedown-Adviser action plays to Deep Woods with New Growth
    * picked. */
  def facedownAdviserToAnotherSite(using munit.Location): Script =
    val beast = CatalogNames.denizen("Errand Boy")
    val (service, acted) = atTable("another-site", tableWithOnePawnPerSite
      .adviser(p1, CatalogNames.denizen("New Growth"))
      .adviser(p1, beast, facedown = true).banditsRefilled)
    …
```

Answer the modifier decision by picking New Growth, then the start button. Read `ModifierChoice`, and `ModifierPlay.started` in the test kit, for the decision id and the button ref. Answer the facedown pick with `beast`, and the placement with `DecisionOptionRef.SiteSlot(CatalogNames.site("Deep Woods"), 0)`. Then `start(act, ActionRef.PlayFacedownAdviser)` and return the `Script`.

In `GameLogActionLineSuite.scala`:

```scala
  test("a card played to another site names that site"):
    val script = facedownAdviserToAnotherSite
    assert(lines(script, None).exists(_.matches(
      "^Played .+ to Deep Woods$")), lines(script, None))
```

- [ ] **Step 3: Run them and watch them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.BaronSuite oathdigital.gameplay.powers.search.NewGrowthSuite"`
Expected: compile errors naming `Baron` and `NewGrowth`.

- [ ] **Step 4: The powers**

Move `BaronCard` from `HoldingOrderDenizens.scala` into `Baron.scala`, and `NewGrowthCard` from `HoldingBeastDenizens.scala` into `NewGrowth.scala`, keeping each object as printed. `NewFoundations` imports both packages, so it needs no change. Remove imports the holding files no longer use.

`Baron.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.{CardPlayRules, DiscardPermission,
  PlaySites}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object BaronCard extends Denizen(DenizenId("250"), "Baron", Suit.Order):
  val power = PrintedPower(PowerId("denizen.baron"),
    persistent = false, cost = Cost.free,
    text = "You may play to any site you rule, and you may discard a " +
      "card from that site first.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Baron (card 250), a selected Search modifier with no cost: "You may play
  * to any site you rule, and you may discard a card from that site first."
  * (catalog batch 8, slice 12).
  *
  * Through the `SearchPlayAdviser` window it opens every site its user
  * rules, and adds a discard permission that holds at those sites, the
  * pawn site included when they rule it. The Play-Facedown-Adviser action
  * selects its modifiers at the Search's window, so it applies there too.
  * The sites are read when the play is planned.
  *
  * Its line, "May discard a card at {site} first.", travels in the
  * permission, and card play writes it for the discard it allowed.
  */
final case class Baron private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = BaronCard.id
  def id: PowerId = Baron.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  private val notes = NoteSet(id)
  notes.include(CardPlayRules.notes)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match
        case tree: CardPlayProcedure.PlacementTree =>
          val ruled = Baron.ruledBy(ctx.activePlayer)
          tree.adjust(children)(_.openSites(PlaySites(ruled))
            .permitSiteDiscard(DiscardPermission.lined(id,
              PowerSourceRef.Card(cardId), appliesAt = ruled)))
        case _ => children)))

object Baron:
  val id: PowerId = BaronCard.power.id

  /** Whether `actor` rules the site, on the board the play is planned
    * on. */
  def ruledBy(actor: PlayerId): (ReadyGame, SiteId) => Boolean =
    (ready, site) => PowerAccess.ruledSites(ready, actor).contains(site)

  def forCatalog(catalog: ExecutableCatalog): Baron = new Baron(catalog)
```

`NewGrowth.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.actions.PlaySites
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object NewGrowthCard extends Denizen(DenizenId("188"), "New Growth",
    Suit.Beast):
  val power = PrintedPower(PowerId("denizen.new-growth"),
    persistent = false, cost = Cost.free,
    text = "You may play [suit-beast] and [suit-hearth] cards to any " +
      "site _(that has space)_.")
  val powers: Vector[PrintedPower] = Vector(power)

/** New Growth (card 188), a selected Search modifier with no cost: "You
  * may play [suit-beast] and [suit-hearth] cards to any site (that has
  * space)." (catalog batch 8, slice 12).
  *
  * Through the `SearchPlayAdviser` window it opens every site to a card
  * whose effective suit is Beast or Hearth. "(that has space)" is a
  * reminder: each site follows the pawn site's rules, so a full site
  * takes the card only through a Replace another permission allows. The
  * card's suit is read in hand, where no lens changes it during the
  * action. It writes no line: "Played {card} to {site}" says where.
  */
final case class NewGrowth private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = NewGrowthCard.id
  def id: PowerId = NewGrowth.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match
        case tree: CardPlayProcedure.PlacementTree
            if EffectiveSuit.of(catalog, ctx.state, tree.card)
              .exists(NewGrowth.suits) =>
          tree.adjust(children)(_.openSites(NewGrowth.everySite))
        case _ => children)))

object NewGrowth:
  val id: PowerId = NewGrowthCard.power.id
  /** The suits it lets go to any site. */
  val suits: Set[Suit] = Set(Suit.Beast, Suit.Hearth)
  val everySite: PlaySites = PlaySites((_, _) => true)

  def forCatalog(catalog: ExecutableCatalog): NewGrowth =
    new NewGrowth(catalog)
```

In `ActionModifiers.forCatalog`, add `Vector(Baron.forCatalog(catalog))` and `Vector(NewGrowth.forCatalog(catalog))` after `CropRotation`, and import both.

- [ ] **Step 5: Regenerate the note keys and run the tests**

Run: `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected diff: `denizen.baron` gains its `discard-first` and `bury-first` lines; nothing else changes.

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.* oathdigital.application.gamelog.* oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: all pass, with 6 Baron, 5 New Growth and 1 log test new. If `PowerKindsCatalogSuite` lists the selected modifiers by id, add the two ids there.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/search/Baron.scala \
  src/main/scala/oathdigital/gameplay/powers/search/NewGrowth.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/test/resources/notes/declared-keys.txt \
  src/test/scala/oathdigital/gameplay/powers/SearchFixture.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/search/BaronSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/search/NewGrowthSuite.scala
git commit -m "feat(batch8): Baron and New Growth play to other sites"
```

---

### Task 4: The Replace path accepts a bury

This is the seam behind Forest Warden. A bury permission makes Replace options buries, offers relics, and asks the two follow-ups. A test bury permission stands in for the card.

**Files:**
- Modify: `src/main/scala/oathdigital/model/Decisions.scala` (`DecisionOption.Burying`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala` (`buries`)
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala`
- Modify: `src/test/scala/oathdigital/testkit/PlacementPicks.scala`
- Test: `src/test/scala/oathdigital/gameplay/PlacementFixture.scala`, `src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala`, `src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala`, `src/test/scala/oathdigital/application/SlotOptionRowsSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/BoardOptionsRenderSuite.scala`, and the projection codec's round-trip suite (`shared/src/test/.../ProjectionProtocolSuite.scala`)

**Interfaces:**
- Consumes:
  - from Task 1, `DecisionOptionRef.SiteRelicSlot` and `DecisionOption.SiteRelicSlot`;
  - from Task 2, `SiteRemoval`, `CardSlot.buried` and `mayBury`, `CardPlay.operations(…, buried)`, `CardSlots.credits(…, buried)` and `moreDiscards(…, buriedReplaced)`.
- Produces:
  - `DecisionOption.Burying(option, card)`;
  - `CardSlots.moreBuries(catalog, ready, actor, card, origin, rules, slot, discarded: Vector[CardId], picked = Vector.empty): Option[MoreBuries]` and `MoreBuries(options: Vector[DecisionOption])`;
  - `CardPlayProcedure.replacedDecisionId`, `buryDecisionId`, `buryReplaced` and `discardReplaced`;
  - `DecisionOptionProjection.buries`;
  - `PlacementPicks.buries(option): Option[CardId]`.

- [ ] **Step 1: Write the failing tests**

In `PlacementFixture.scala`:

```scala
  /** A plain bury permission, of any card at any site, whose line says
    * nothing. */
  val testBury: DiscardPermission = DiscardPermission(PowerId("test.bury"),
    _ => Note(PowerId("test.bury"), _ => None), SiteRemoval.Bury)
```

In `CardSlotsSuite.scala` (with `hall` there already, the intact Hall of Ministers locked):

```scala
  private val burying = CardPlayRules.default.permitSiteDiscard(
    PlacementFixture.testBury)
  private def bury(option: DecisionOption, card: CardId): DecisionOption =
    DecisionOption.Burying(option, card)
  private val horn = CatalogNames.relic("Cracked Horn")

  test("a bury alone makes each Replace a bury, a locked card included"):
    val ready = atHome(rainBoots).hand(p1, card)
      .edifice(hall, EdificeSide.Intact, at = home).ready
    val site = slotsOf(ready, rules = burying)
      .filter(_.site.contains(home))
    assertEquals(site.map(_.option), Vector(siteSlot(2),
      bury(siteSlot(0), rainBoots), bury(siteSlot(1), hall)))
    assertEquals(site.map(_.buried), Vector(None, Some(rainBoots),
      Some(hall)))

  test("a bury and a discard together leave each Replace a Replace that " +
      "asks, and a card only a bury may take a bury"):
    val ready = atHome(rainBoots).hand(p1, card)
      .edifice(hall, EdificeSide.Intact, at = home).ready
    val both = slotsOf(ready, rules = burying.permitSiteDiscard(
      testPermission)).filter(_.site.contains(home))
    assertEquals(both.map(s => (s.option, s.mayBury)), Vector(
      (siteSlot(2), false), (replacing(siteSlot(0), rainBoots), true),
      (bury(siteSlot(1), hall), false)))

  test("at a site with room, each relic is offered as a bury, and a full " +
      "site offers none"):
    val ready = atHome().hand(p1, card).relicAt(horn, at = home).ready
    val relic = DecisionOption.SiteRelicSlot(
      DecisionOptionRef.SiteRelicSlot(home, 0))
    val slots = slotsOf(ready, rules = burying)
    assert(slots.exists(s => s.option == bury(relic, horn) &&
      s.buried.contains(horn) &&
      s.placement == SearchPlacement.Site(home, None)), slots.toString)
    val full = atHome(rainBoots, binding, wrestlers).hand(p1, card)
      .relicAt(horn, at = home).ready
    assert(!slotsOf(full, rules = burying).exists(_.option.ref ==
      relic.ref))

  test("a play buries the named card with its returns, a relic included, " +
      "and credits the bury"):
    val ready = atHome(rainBoots).hand(p1, card).relicAt(horn, at = home)
      .ready
    val operations = accepted(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(home, None), CardPlay.Origin.TemporaryHand,
      buried = Some(horn)))
    assertEquals(operations.collect { case b: Bury => b.card },
      Vector(BuryableCard.Relic(horn)))
    val replaced = accepted(CardPlay.operations(catalog, ready, p1, card,
      SearchPlacement.Site(home, Some(rainBoots)),
      CardPlay.Origin.TemporaryHand, buried = Some(rainBoots)))
    assertEquals(replaced.collect { case b: Bury => b.card },
      Vector(BuryableCard.Denizen(rainBoots)))
    assertEquals(replaced.collect { case d: Discard.Denizen => d }, Vector.empty)
    assertEquals(CardSlots.credits(catalog, ready, p1, card, home, burying,
      Vector.empty, Some(rainBoots)).map(_.map(_.power)),
      Right(Vector(PlacementFixture.testBury.source)))
    assertEquals(CardSlots.credits(catalog, ready, p1, card, home, burying,
      Vector(rainBoots)).left.toOption, Some(OathViolation
      .InvalidSearchPlacement("more site cards are discarded than the " +
        "permissions in play allow")))
```

The first test assumes `siteSlot(2)`, the empty slot after two cards. Check `atHome`'s signature and the edifice step's argument order on `main`.

In `CardPlayProcedureSuite.scala`, with rule stubs for the bury:

```scala
  private val buryAndDiscard = rulePower("test.bury-and-discard")(
    _.permitSiteDiscard(testPermission)
      .permitSiteDiscard(PlacementFixture.testBury))
  private val replacedId = CardPlayProcedure.replacedDecisionId(card)
  private val buryId = CardPlayProcedure.buryDecisionId(card)

  test("a Replace both may cover asks bury or discard, and Bury buries the " +
      "replaced card"):
    val parked = parkOnPlacement(fullSite, WalkerPowers(Vector(buryAndDiscard)))
    val asked = Outcomes.parked(parked.place(DecisionOptionRef.SiteSlot(home,
      0)))
    val choice = present(ProcedureWalker.parkedDecide(parked.ready,
      parked.tree, asked.tree, parked.powers), "the bury-or-discard choice")
    assertEquals(choice.decisionId, replacedId)
    assertEquals(choice.query, DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(CardPlayProcedure.buryReplaced, "Bury"),
      DecisionOption.Button(CardPlayProcedure.discardReplaced, "Discard")),
      heading = Some("Bury or discard the replaced card")))
    // The bury spent the bury permission, so the discard permission is
    // left: the discard follow-up asks next.
    val buried = Outcomes.parked(parked.resolve(asked.tree, replacedId,
      DecisionAnswer.ChooseOneAnswer(CardPlayProcedure.buryReplaced)))
    assertEquals(ProcedureWalker.parkedDecide(parked.ready, parked.tree,
      buried.tree, parked.powers).map(_.decisionId), Some(discardId))
    val done = Outcomes.finished(parked.resolve(buried.tree, discardId,
      DecisionAnswer.ChooseManyAnswer(Vector.empty)))
    assertEquals(done.treeless.game.current.commonCards.worldDeck.lastOption,
      Some(others(0)))
    assertEquals(Look(done.treeless).cardsAt(home),
      Vector[CardId](others(1), others(2), card))

  test("after Discard, the bury follow-up offers the other cards and the " +
      "relics, and buries one"):
    val horn = CatalogNames.relic("Cracked Horn")
    val ready = others.foldLeft(atHome.hand(actor, card))(
      _.denizen(_, at = home)).relicAt(horn, at = home).ready
    val parked = parkOnPlacement(ready, WalkerPowers(Vector(buryAndDiscard)))
    val asked = Outcomes.parked(parked.place(DecisionOptionRef.SiteSlot(home,
      0)))
    val chose = Outcomes.parked(parked.resolve(asked.tree, replacedId,
      DecisionAnswer.ChooseOneAnswer(CardPlayProcedure.discardReplaced)))
    val more = present(ProcedureWalker.parkedDecide(parked.ready,
      parked.tree, chose.tree, parked.powers), "the bury follow-up")
    assertEquals(more.decisionId, buryId)
    val relic = DecisionOption.SiteRelicSlot(
      DecisionOptionRef.SiteRelicSlot(home, 0))
    assertEquals(more.query match
      case many: DecisionQuery.ChooseMany =>
        (many.min, many.max, many.options)
      case other => fail(s"not a choose-many: $other"),
      (0, 1, others.tail.map(id =>
        DecisionOption.Denizen(DecisionOptionRef.Denizen(id))) :+ relic))
    val done = Outcomes.finished(parked.resolve(chose.tree, buryId,
      DecisionAnswer.ChooseManyAnswer(Vector(relic.ref))))
    assertEquals(done.treeless.game.current.commonCards.relicDeck.lastOption,
      Some(horn))
    assert(Look(done.treeless).discardAfter(home).contains(others(0)))
```

In `SlotOptionRowsSuite.scala`:

```scala
  test("a bury names the card it buries and is marked a bury"):
    val row = rowOf(DecisionOption.Burying(
      DecisionOption.SiteSlot(DecisionOptionRef.SiteSlot(home, 0)), wrestlers))
    assertEquals(row.label, s"Bury Wrestlers at $site")
    assertEquals((row.replaces.map(_.cardId), row.buries),
      (Some(wrestlers.value), true))
```

In the projection protocol suite, round-trip one option row with `buries = true`. Use the suite's existing option-row round-trip test as the model.

In `BoardOptionsRenderSuite.scala`:

```scala
  test("a bury option carries the Bury badge"):
    val buried = option("site-slot", "woods:0").copy(replaces = Some(fox),
      buries = true)
    val root = world(recording(available(buried)).options)
    assertEquals(all(root, ".board-option-badge").map(_.textContent),
      Vector("Bury"))
    assertEquals(one(root, """[data-option-ref="site-slot:woods:0"]""")
      .getAttribute("aria-label"), "Fox, Bury")
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.CardSlotsSuite oathdigital.gameplay.CardPlayProcedureSuite oathdigital.application.SlotOptionRowsSuite"`
Expected: compile errors naming `Burying`, `testBury` and `replacedDecisionId`.

- [ ] **Step 3: The option, the projection field and the badge**

In `Decisions.scala`, after `Replacing`:

```scala
  /** An option whose play buries `card` at the site first (Forest Warden):
    * the card in that site slot, or the relic in that site relic slot.
    * Wrapping for the reason `Replacing` wraps: `ref` is the wrapped
    * option's. The projector names the card as the viewer may see it.
    */
  final case class Burying(option: DecisionOption, card: CardId)
      extends DecisionOption:
    def ref: DecisionOptionRef = option.ref
    override def available: Boolean = option.available
```

In `ActionProjectionDtos.scala`, add `buries: Boolean = false` after `replaces`, documented as: "`buries` marks an option whose play buries `replaces` instead of discarding it, Forest Warden's 'Bury and replace' and 'Bury and play'." In `ActionProjectionCodec.scala`, encode `"buries" -> ujson.Bool(row.buries)`, add `"buries"` to the exact key set, and decode it with a `false` default, the way `state` defaults.

In `WalkerDecisionProjector.optionProjection`, beside `Replacing`:

```scala
      case DecisionOption.Burying(inner, card) =>
        val buried = shown(ready, viewer, index, card)
        val name = if buried.hidden then s"a ${buried.name.toLowerCase}"
          else buried.name
        optionProjection(ready, viewer, index, inner, details).map(row =>
          row.copy(label = buryingLabel(inner.ref, name),
            replaces = Some(buried), buries = true))
```

```scala
  /** A bury option's label: the card it buries, and where. */
  private def buryingLabel(ref: DecisionOptionRef, name: String): String =
    CardPlayProcedure.siteOf(ref).fold(s"Bury $name")(site =>
      s"Bury $name at ${presentation.siteLabel(site)}")
```

In `replacing()`'s `worded`, the verb follows the row: `if option.buries then "Bury" else "Discard"`. In `subjectCards` and `headed`, add the prefixes `"cardplay.replaced."` and `"cardplay.bury."` to the subject list. Give `cardplay.bury.` the heading `s"Bury a card at ${site}"` through `followUpSite`, as `cardplay.discard.` has.

In `ChoiceWords.labelled`, add `case DecisionOption.Burying(inner, _) => labelled(ref)(inner)`.

In `BoardOptions.mark`, the badge word follows `buries`:

```scala
    if found.option.replaces.nonEmpty then
      val word = if found.option.buries then "Bury" else "Replace"
      target.classList.add("board-option-replace")
      target.appendChild(text("span", "board-option-badge", word))
      Option(target.getAttribute("aria-label")).foreach(name =>
        target.setAttribute("aria-label", s"$name, $word"))
```

In `PlacementPicks.replaces`, add `case DecisionOption.Burying(_, card) => Some(card)`. Also add:

```scala
  /** The card `option` buries, read through any availability, price or
    * badge mark. */
  def buries(option: DecisionOption): Option[CardId] = option match
    case DecisionOption.Burying(_, card) => Some(card)
    case DecisionOption.Unavailable(inner, _) => buries(inner)
    case DecisionOption.Priced(inner, _) => buries(inner)
    case DecisionOption.Badged(inner, _) => buries(inner)
    case _ => None
```

- [ ] **Step 4: The slots and the follow-ups**

In `CardSlots.siteSlots`, build each row card's Replace from the two plans. A slot pair is no longer plain; return `Vector[CardSlot]` from `siteSlots` already judged, and leave the other candidates judged as before.

```scala
      val discards = permitted.exists(_.removal == SiteRemoval.Discard)
      val buries = permitted.exists(_.removal == SiteRemoval.Bury)
      def judged(option: DecisionOption, placement: SearchPlacement,
          buried: Option[CardId] = None): Option[DecisionOption] =
        judge(catalog, ready, actor, option, CardPlay.operations(catalog,
          ready, actor, card, placement, origin, buried = buried), picked)
      // A Replace a discard covers is a Replace; one only a bury may take
      // is a bury; one both may take asks which, after it is chosen.
      def replace(held: CardId, index: Int): Vector[CardSlot] =
        val placement = SearchPlacement.Site(site, Some(held))
        val discarded = Option.when(discards)(judged(
          DecisionOption.Replacing(slot(index), held), placement)).flatten
        val buried = Option.when(buries)(judged(
          DecisionOption.Burying(slot(index), held), placement,
          Some(held))).flatten
        (discarded, buried) match
          case (Some(d), Some(b)) if d.available && b.available =>
            Vector(CardSlot(d, placement, mayBury = true))
          case (_, Some(b)) if b.available || discarded.isEmpty =>
            Vector(CardSlot(b, placement, buried = Some(held)))
          case (Some(d), _) => Vector(CardSlot(d, placement))
          case (None, None) => Vector.empty
      val relics = if !room || !buries then Vector.empty
        else state.relics.zipWithIndex.flatMap((relic, index) =>
          val placement = SearchPlacement.Site(site, None)
          judged(DecisionOption.Burying(DecisionOption.SiteRelicSlot(
            DecisionOptionRef.SiteRelicSlot(site, index)), relic.id),
            placement, Some(relic.id)).map(CardSlot(_, placement,
              buried = Some(relic.id))))
```

The site's slots are its empty slot, then `replace` over the row, then `relics`. A bury is never refused by a lock. When the discard is refused but the bury is open, the bury shows, so a locked card still gets "Bury and replace".

Add `MoreBuries` and `moreBuries`:

```scala
/** The bury follow-up of a Replace site slot whose card was discarded: the
  * site's other row cards and its relics, at most one to bury. */
final case class MoreBuries(options: Vector[DecisionOption])
```

```scala
  /** The bury follow-up of `slot`, a Replace site slot whose card was
    * discarded with `discarded` after it: the site's remaining row cards
    * and every relic there, when a bury permission holds at the site;
    * `None` when nothing may be buried. */
  def moreBuries(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, card: WorldCardId, origin: CardPlay.Origin,
      rules: CardPlayRules, slot: CardSlot, discarded: Vector[CardId],
      picked: Vector[CardId] = Vector.empty): Option[MoreBuries] =
    for
      site <- slot.site
      replacedId <- slot.replacedAtSite
      if permissions(catalog, ready, actor, card, site, rules)
        .exists(_.removal == SiteRemoval.Bury)
      state <- ready.game.current.map.sites.get(site)
      gone = replacedId +: discarded
      rows = state.denizens.filterNot(c => gone.contains(c.id))
        .map(c => c.id -> optionOf(c))
      relics = state.relics.zipWithIndex.map((relic, index) =>
        (relic.id: CardId) -> (DecisionOption.SiteRelicSlot(
          DecisionOptionRef.SiteRelicSlot(site, index)): DecisionOption))
      options = (rows ++ relics).flatMap((id, option) => judge(catalog,
        ready, actor, option, CardPlay.operations(catalog, ready, actor,
          card, slot.placement, origin, discarded, buried = Some(id)),
        picked))
      if options.exists(_.available)
    yield MoreBuries(options)
```

In `CardPlayProcedure.played`, replace the one follow-up with three places held in one `Sequence`. The play's shape then stays two nodes before the hook, as `settled` builds it:

```scala
    val replacedId = replacedDecisionId(card)
    val discardId = discardDecisionId(card)
    val buryId = buryDecisionId(card)
    def chose(pending: PendingTree, ref: DecisionOptionRef): Boolean =
      pending.answered.collectFirst {
        case Answered(`replacedId`, DecisionAnswer.ChooseOneAnswer(r), _) =>
          r
      }.contains(ref)
    // The replaced card is buried when the slot is a bury, or its follow-up
    // says Bury.
    def buriesReplaced(pending: PendingTree): Boolean =
      slot.buried.exists(slot.replacedAtSite.contains) ||
        chose(pending, buryReplaced)
    def discarded(pending: PendingTree): Vector[CardId] =
      pending.answered.collectFirst {
        case Answered(`discardId`, DecisionAnswer.ChooseManyAnswer(refs), _) =>
          refs.flatMap(siteCard)
      }.getOrElse(Vector.empty)
    def buriedMore(pending: PendingTree): Option[CardId] =
      pending.answered.collectFirst {
        case Answered(`buryId`, DecisionAnswer.ChooseManyAnswer(refs), _) =>
          refs.flatMap(ref => siteCard(ref).orElse(siteRelic(ready, ref)))
      }.flatMap(_.headOption)
    val askReplaced: Operation =
      if !slot.mayBury then noFollowUp
      else Decide(replacedId, actor, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(buryReplaced, "Bury"),
        DecisionOption.Button(discardReplaced, "Discard")),
        heading = Some("Bury or discard the replaced card")))
    val moreDiscards = Branch((_, pending) => CardSlots.moreDiscards(catalog,
      ready, actor, card, from, rules, slot, buriesReplaced(pending), picked)
      .toVector.map(offer => Decide(discardId, actor,
        DecisionQuery.ChooseMany(0, offer.most, offer.options,
          heading = Some("Discard more cards from the site"),
          locked = Vector(offer.locked),
          confirm = Some(CountedConfirm("Discard", "more card", "more cards",
            Some("Discard no more")))))))
    val moreBuries = Branch((_, pending) =>
      if slot.buried.nonEmpty || buriesReplaced(pending) then Vector.empty
      else CardSlots.moreBuries(catalog, ready, actor, card, from, rules,
        slot, discarded(pending), picked).toVector.map(offer =>
        Decide(buryId, actor, DecisionQuery.ChooseMany(0, 1, offer.options,
          heading = Some("Bury a card at the site"),
          confirm = Some(CountedConfirm("Bury", "card", "cards",
            Some("Bury nothing")))))))
    val followUp = Sequence(Vector(askReplaced, moreDiscards, moreBuries))
```

`moreBuries` runs only after a Replace, and `moreBuries` returns `None` when `slot.replacedAtSite` is empty. So an empty-slot play asks no bury follow-up; its relics and row cards were offered as "Bury and play" and "Bury and replace" already. In the `play` Branch:

```scala
      val extra = discarded(pending)
      val buried =
        if buriesReplaced(pending) then slot.replacedAtSite
        else slot.buried.orElse(buriedMore(pending))
      val apply = BuildOps((state, _) => CardPlay.operations(catalog, state,
        actor, card, slot.placement, from, extra, buried))
      val credited = slot.site.fold[Either[OathViolation, Vector[Note]]](
        Right(Vector.empty))(site => CardSlots.credits(catalog, ready, actor,
        card, site, rules,
        slot.replacedAtSite.filterNot(buried.contains).toVector ++ extra,
        buried))
```

Add the ids, the buttons and the relic reader:

```scala
  private val replacedPrefix = "cardplay.replaced."
  private val buryPrefix = "cardplay.bury."

  /** `card`'s bury-or-discard decision id,
    * `cardplay.replaced.{kind}.{value}`. */
  def replacedDecisionId(card: WorldCardId): String =
    s"$replacedPrefix${card.kind}.${card.value}"

  /** `card`'s bury follow-up decision id, `cardplay.bury.{kind}.{value}`. */
  def buryDecisionId(card: WorldCardId): String =
    s"$buryPrefix${card.kind}.${card.value}"

  /** The answers of the bury-or-discard decision. */
  val buryReplaced: DecisionOptionRef.Button = DecisionOptionRef.Button("bury")
  val discardReplaced: DecisionOptionRef.Button =
    DecisionOptionRef.Button("discard")

  /** The relic a site relic slot names, on the board the play was planned
    * on. */
  private def siteRelic(ready: ReadyGame, ref: DecisionOptionRef)
      : Option[CardId] = ref match
    case DecisionOptionRef.SiteRelicSlot(site, index) =>
      ready.game.current.map.sites.get(site).flatMap(_.relics.lift(index))
        .map(_.id)
    case _ => None
```

Update the procedure's doc comment: steps 2 and 3 become the bury-or-discard choice, the discard follow-up, and the bury follow-up, each asked only when it applies.

- [ ] **Step 5: Run the tests**

Run: `./sbtw "test" "frontend/test"`
Expected: all pass, with the 7 new server tests, 1 new frontend test and 1 codec round trip.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/model/Decisions.scala \
  src/main/scala/oathdigital/gameplay/actions/cardplay/CardSlots.scala \
  src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala \
  shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala \
  shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala \
  src/main/scala/oathdigital/application/WalkerDecisionProjector.scala \
  src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala \
  frontend/src/main/scala/oathdigital/frontend/BoardOptions.scala \
  src/test/scala/oathdigital/testkit/PlacementPicks.scala \
  src/test/scala/oathdigital/gameplay/PlacementFixture.scala \
  src/test/scala/oathdigital/gameplay/CardSlotsSuite.scala \
  src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala \
  src/test/scala/oathdigital/application/SlotOptionRowsSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/BoardOptionsRenderSuite.scala \
  shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala
git commit -m "feat(batch8): the Replace path accepts a bury"
```

---

### Task 5: Forest Warden

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/search/ForestWarden.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `ForestWardenCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/test/resources/notes/declared-keys.txt` (regenerated)
- Create: `src/test/scala/oathdigital/gameplay/powers/search/ForestWardenSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PlacementFixture.scala` (`projectedPlacement`)

**Interfaces:**
- Consumes:
  - from Task 2, `DiscardPermission.lined(…, SiteRemoval.Bury)` and `CardPlayRules.buryFirst`;
  - from Task 3, `NewGrowth.id` and `SearchFixture.placeAt`;
  - from Task 4, `PlacementPicks.buries` and the decision ids.
- Produces: `ForestWarden.id` and `ForestWarden.forCatalog`.

- [ ] **Step 1: Write the failing suite**

```scala
package oathdigital.gameplay.powers.search

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.CardPlayRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.operations.AncientBloodline
import oathdigital.gameplay.powers.{NoteText, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PlacementPicks, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Forest Warden (card 218), a selected Search modifier: "If you play a
  * [suit-beast] card to a site, you may bury a card at the site first." */
class ForestWardenSuite extends munit.FunSuite:
  import SearchFixture.{keep, placeAt, placeReplacing, rules, start}

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val warden = CatalogNames.denizen("Forest Warden")
  private val modifiers = Vector(ForestWarden.id)
  private val beast = CatalogNames.denizen("Errand Boy")
  private val order = SearchFixture.anyPlainDenizensOf(Suit.Order).head
  private val filler = CatalogNames.denizen("Code of Honor")
  private val horn = CatalogNames.relic("Cracked Horn")
  private val home = CatalogNames.site("Ancient City")
  /** The Beast Homeland: its rule gives a discard as well. */
  private val woods = CatalogNames.site("Deep Woods")

  /** p1 in Act at `at` holding Forest Warden faceup, with 5 Supply and
    * `top` on the world deck. */
  private def warding(top: DenizenId, at: SiteId = home): Table =
    Table.start.pawn(p1, at).worldDeckTop(top).supply(p1, 5)
      .adviser(p1, warden)

  private def toPlacement(ready: ReadyGame, card: DenizenId = beast,
      selected: Vector[PowerId] = modifiers): OathTransition = accepted(for
    started <- start(ready, selected)
    kept <- keep(started, card)
  yield kept.copy(events = started.events ++ kept.events))

  private def parkedOptions(at: OathTransition): Vector[DecisionOption] =
    PlacementPicks.parkedOptions(at.state, walkerPowers)

  private def wardenSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(ForestWarden.id, Vector(CardPlayRules.buryFirst), events)

  test("selected, a Beast card's Replace is Bury and replace, and the bury " +
      "sends the card to the bottom of the world deck with the line"):
    val ready = warding(beast).denizen(filler, at = home)
      .tokens(filler, favor = 1).ready
    val asked = toPlacement(ready)
    assert(parkedOptions(asked).exists(o =>
      PlacementPicks.buries(o).contains(filler)), parkedOptions(asked).toString)
    val done = accepted(placeReplacing(asked, beast, PlayedAs.AtSite, filler))
    val end = SearchFixture.after(done)
    assertEquals(end.game.current.commonCards.worldDeck.lastOption,
      Some(filler))
    assertEquals(Look(end).cardsAt(home), Vector[CardId](beast))
    assertEquals(wardenSaid(done.events), Vector(NoteText.Said("bury-first",
      s"May bury a card at ${home.value} first.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assertWireRoundTrips(asked.events ++ done.events)

  test("a card of another suit gets no bury"):
    val asked = toPlacement(warding(order).denizen(filler, at = home).ready,
      order)
    assert(!parkedOptions(asked).exists(o => PlacementPicks.buries(o)
      .nonEmpty), parkedOptions(asked).toString)

  test("bury ignores locks: an intact edifice and a card Ancient Bloodline " +
      "locks may be buried"):
    val hall = CatalogNames.edifice("Hall of Ministers")
    val ready = warding(beast).edifice(hall, EdificeSide.Intact, at = home)
      .denizen(filler, at = home).warbandsAt(home, p2, 1)
      .adviser(p2, CatalogNames.denizen("Ancient Bloodline")).ready
    val buried = parkedOptions(toPlacement(ready)).filter(_.available)
      .flatMap(PlacementPicks.buries)
    assertEquals(buried.toSet, Set[CardId](hall, filler))

  test("at a site with room, a relic there is Bury and play"):
    val ready = warding(beast).relicAt(horn, at = home).ready
    val asked = toPlacement(ready)
    val done = accepted(placeAt(asked, beast,
      DecisionOptionRef.SiteRelicSlot(home, 0)))
    val end = SearchFixture.after(done)
    assertEquals(end.game.current.commonCards.relicDeck.lastOption,
      Some(horn))
    assertEquals(Look(end).cardsAt(home), Vector[CardId](beast))

  test("with the Homeland's discard too, Replace asks: Discard discards " +
      "with the Homeland's line"):
    val ready = warding(beast, at = woods).denizen(filler, at = woods).ready
    val asked = toPlacement(ready)
    val placed = accepted(placeReplacing(asked, beast, PlayedAs.AtSite,
      filler))
    val done = accepted(rules.resolveWalker(placed.state, p1,
      CardPlayProcedure.replacedDecisionId(beast),
      DecisionAnswer.ChooseOneAnswer(CardPlayProcedure.discardReplaced)))
    assert(Look(SearchFixture.after(done)).discardAfter(woods)
      .contains(filler))
    assertEquals(wardenSaid(placed.events ++ done.events), Vector.empty)

  test("a facedown relic at another site is offered by its slot, never by " +
      "its name"):
    val ready = warding(beast).adviser(p1, CatalogNames.denizen("New Growth"))
      .relicAt(horn, at = woods).ready
    val asked = toPlacement(ready, selected = Vector(ForestWarden.id,
      NewGrowth.id))
    val query = PlacementFixture.projectedPlacement(asked, p1)
    val row = query.offeredOptions.find(o => o.kind == "site-relic-slot" &&
      o.id == s"${woods.value}:0")
    assert(row.exists(r => r.buries && r.card.exists(_.hidden) &&
      !r.label.contains("Horn")), query.toString)
```

The last test needs a projection helper. Add it to `PlacementFixture`, reading the parked decision through `GameProjector` as `PhasePowerHarness.queryOf` does:

```scala
  /** The parked placement as `viewer` is offered it, projected with the
    * production walker powers. */
  def projectedPlacement(transition: OathTransition, viewer: PlayerId)(
      using munit.Location): DecisionQueryProjection =
    present(new GameProjector(catalog).project("targets",
      LoadedGame(transition.state, 30L), viewer).walkerDecision
      .flatMap(_.query), "the projected placement")
```

`AncientBloodline` may be unused once the test reads the card by name; drop the import if so. Check that the Bloodline holder p2 rules Ancient City (`warbandsAt(home, p2, 1)`) while p1's pawn stands there.

- [ ] **Step 2: Run it and watch it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.ForestWardenSuite"`
Expected: compile errors naming `ForestWarden`.

- [ ] **Step 3: The power**

Move `ForestWardenCard` from `HoldingBeastDenizens.scala` into `ForestWarden.scala`:

```scala
package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.actions.{CardPlayRules, DiscardPermission,
  SiteRemoval}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object ForestWardenCard extends Denizen(DenizenId("218"), "Forest Warden",
    Suit.Beast):
  val power = PrintedPower(PowerId("denizen.forest-warden"),
    persistent = false, cost = Cost.free,
    text = "If you play a [suit-beast] card to a site, you may bury a " +
      "card at the site first.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Forest Warden (card 218), a selected Search modifier with no cost: "If
  * you play a [suit-beast] card to a site, you may bury a card at the site
  * first." (catalog batch 8, slice 12).
  *
  * Through the `SearchPlayAdviser` window it adds one bury permission when
  * the played card's effective suit is Beast. Card play then offers each
  * card of the site's row as "Bury and replace" (a Replace the player is
  * asked about when a discard covers it too), each relic there as "Bury
  * and play" while the site has room, and a bury follow-up after a
  * Replace that discarded. Bury ignores locks.
  *
  * Its line, "May bury a card at {site} first.", travels in the
  * permission, and card play writes it for the bury it allowed.
  */
final case class ForestWarden private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = ForestWardenCard.id
  def id: PowerId = ForestWarden.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  private val notes = NoteSet(id)
  notes.include(CardPlayRules.notes)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match
        case tree: CardPlayProcedure.PlacementTree
            if EffectiveSuit.of(catalog, ctx.state, tree.card)
              .contains(Suit.Beast) =>
          tree.adjust(children)(_.permitSiteDiscard(DiscardPermission.lined(
            id, PowerSourceRef.Card(cardId), SiteRemoval.Bury)))
        case _ => children)))

object ForestWarden:
  val id: PowerId = ForestWardenCard.power.id

  def forCatalog(catalog: ExecutableCatalog): ForestWarden =
    new ForestWarden(catalog)
```

Register it in `ActionModifiers` after `NewGrowth`.

- [ ] **Step 4: Regenerate the note keys and run the tests**

Run: `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected diff: `denizen.forest-warden` gains its `discard-first` and `bury-first` lines; nothing else changes.

Run: `./sbtw "testOnly oathdigital.gameplay.powers.search.*"`
Expected: all pass, with the 6 new tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/search/ForestWarden.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/test/resources/notes/declared-keys.txt \
  src/test/scala/oathdigital/gameplay/PlacementFixture.scala \
  src/test/scala/oathdigital/gameplay/powers/search/ForestWardenSuite.scala
git commit -m "feat(batch8): Forest Warden buries a card at the site first"
```

---

### Task 6: Rulings, gates and status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md` (slice 12)
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` ("The bar", the slice 12 section, the codec sentence, and the Status line)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Record the rulings**

Replace the slice 12 entry of the rulings appendix with this plan's "Rulings" section, rewritten as the appendix writes entries: one bullet per card with its printed text quoted, then the shared rules. Say that ruling 3 replaces the appendix's earlier "Full sites are shown unavailable with the reason": a full site with no permission has no option.

- [ ] **Step 2: Update the design**

Replace the slice 12 bullet of "The bar" with:

```markdown
- **A card-play site** (slice 12). `SearchPlacement.Site` names its site.
  `CardPlay` and `CardSlots` read it instead of the pawn site.
  `CardPlayRules` lets a power open other sites (`PlaySites`) and scope a
  removal permission to sites, and a permission is a discard or a bury.
  The Replace path accepts a bury ("Bury and replace"), a relic may be
  buried before a play ("Bury and play"), and two follow-ups ask "Bury or
  discard the replaced card" and for one more bury. A site's relics gain
  a positional ref, `DecisionOptionRef.SiteRelicSlot`, and the site
  projection lists them by position.
```

In "Slice 12: Card play at another site", add a sentence after the first paragraph: "A relic at the site may be buried too, named by its slot."

In the codec sentence under "The bar", replace "a card-play site" with "a site relic slot": a play's site travels in its slot ref.

- [ ] **Step 3: Run the full gate**

Run: `./sbtw "test" "frontend/test"`
Expected: everything passes. The server count rises by about 5 + 5 + 12 + 8 + 6 = 36 new tests, plus any the reviews ask for. The frontend count rises by 2.

Run: `./sbtw "frontend/fullOptJS"`
Expected: it links.

Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: both pass.

- [ ] **Step 4: Record the status**

- In the design's Status line, write "Slices 1 to 12 are done."
- In the ROADMAP's "Now" paragraph, write "slices 1 to 12 are done", and keep naming whichever item it names as next.
- Mark item 12 done (`- [x] **12. Card play at another site:** Baron, New Growth, Forest Warden.`).

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 12 done"
```
