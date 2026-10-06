# Catalog Batch 4, Slice 2 (Restriction Icons and Card Moves) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make restriction icons govern card moves and swaps, not only card play, and implement four powers that move cards between advisers and sites: Homesteaders (127), Resettle (159), Roving Terror (46) and Moving Market (241).

**Architecture:**

- **The restriction-icon rule** is a new `OperationRestriction`, `RestrictionIcons`, in `gameplay/operations/`, beside `LockedCards`.
  - It joins `OperationRestrictions.printed`, so it holds wherever operations run.
  - It flattens each operation once and judges every `Move` of a card that leaves a site or a play area for another location.
  - An adviser-only card may not land faceup at a site, and a site-only card may not land faceup in a player's advisers.
  - The walker's search already hides an option whose required batch a restriction refuses, so no power filters icons itself.
- **The audit** of every implemented power that moves or swaps a card between advisers and sites found one power that the rule would wrongly refuse: Horned Mask. It now lands the card facedown in one `Move`, so its behavior is unchanged.
- **Each card is a power.**
  - Homesteaders, Resettle and Roving Terror are `PaidAction`s (ACTION powers) in `gameplay/powers/action/`.
  - Moving Market is a `SelectedModifier` (a Trade modifier) in `gameplay/powers/economy/`.
  - Each card's object moves out of `catalog/holding/` into its power's file.
- **One shared helper, `SiteRoom`,** in `gameplay/powers/`, says which sites have room.
  - Operations never check a site's capacity, so every power that moves a card to a site offers only sites with room.
- **Registration:**
  - Homesteaders joins `SelfActionPowers`, Resettle joins `OtherActionPowers` and Roving Terror joins `WorldActionPowers`.
  - Moving Market joins `ActionModifiers`, and its reviewed stub in `MusterPowers` is retired.
- **No engine change.** No new operation, window, query, option kind, `NoteArg` kind, protocol change or frontend change. The new restriction is the change outside `gameplay/powers/` that the product owner approved (spec, "The bar").

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. The frontend is Scala.js; it gets one new test and no production change.

**Spec:** Read both documents before starting:

- `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`: the sections "The bar", "The restriction-icon rule", "Seams used for the first time", "Implementation notes from the verification", "Log lines", "Slice 2: card moves", "Testing" and "Verify at plan time".
- `docs/superpowers/specs/2026-10-05-catalog-batch-4-rulings.md`: "Rules that apply to the whole batch" and the per-card rulings under "Slice 2: card moves".

The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- **Slice order.** Slices 1a and 1b run before this one, and slice 1b also edits `NewFoundations.scala` and the holding files.
  - Check with `git log main --oneline | grep "record catalog batch 4 slice 1"`.
  - Nothing in this slice depends on slice 1b's code. If 1b is not on `main` yet, work anyway, then merge `main` before the final commit. When resolving a conflict in `NewFoundations.scala`'s import lists or a holding file, keep both sides' changes.
- **Build rules.** `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails the build.
  - Each file below lists the imports it needs.
  - When a card move removes the last use of an import in a holding file (`SiteOnly`, for example), remove the import.
  - A refutable pattern in a `val` (such as `val Vector(a, b) = ...`) also fails, so bind each value separately. A case-class extractor of its own type (`val Chosen(t, done) = choose(...)`) is irrefutable and is fine.
- **File size.** Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Note templates.** A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this). A phase power's own line is key `used` (`NoteKey.Used`) or `used.<variant>`; either replaces "Used {card}".
- **Log lines, exactly** (spec, "Log lines", "Slice 2: card moves"). The acting player is not named, except where the line names the owner of a moved adviser, which may be the actor.

  | Card | Key | Line | When |
  |---|---|---|---|
  | Homesteaders | `used` | Homesteaders: Moved {card} to {site}. | an adviser moved |
  | Homesteaders | `used.none` | Homesteaders: No adviser could be moved. | nothing moved |
  | Resettle | `used` | Resettle: Moved {Blue}'s {card} to {site}. | an adviser moved; `{Blue}` is its owner, the actor included |
  | Resettle | `used.none` | Resettle: No Nomad adviser could be moved. | nothing moved |
  | Roving Terror | `used` | Roving Terror: Replaced {card} at {site}. | a card was discarded and Roving Terror moved; covers the generic Discarded line |
  | Roving Terror | `used.none` | Roving Terror: No card could be replaced. | nothing moved; the same note, so it also covers |
  | Moving Market | `moved` | Moving Market: Moved it to {site} and gained {n favor} from {the Nomad bank}. | it moved and favor was gained |
  | Moving Market | `moved.only` | Moving Market: Moved it to {site}. | it moved and the Nomad bank was empty (plan-time ruling 6) |

  No line when Moving Market is skipped. Every decision whose answer a line names is declared narrated, so it writes no generic "Chose" line.
- **Batch rulings that every task applies** (rulings, "Rules that apply to the whole batch"):
  - **Restriction icons govern play, moves and swaps.** A facedown adviser has no restrictions. Edifices are never advisers.
  - **Site capacity.** A power that moves a card to a site offers only sites with room.
  - **"Your site"** means the pawn's site.
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare against them at the end. This plan adds 30 server tests and 1 frontend test.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one says what it costs if it is wrong.

1. **What the rule judges.**
   - It judges every `Move` of a card, after flattening, whose source is a site or a play area and whose destination is a different location. A `Swap`, a `Take` and a `Give` flatten to such moves.
   - It judges where and how the card lands. The landing orientation is the move's `resultingOrientation`, or else the orientation the card shows where it lies.
     - A move to a site refuses an adviser-only card that lands faceup.
     - A move to a play area refuses a site-only card that lands faceup.
     - A card that lands facedown passes, since a facedown adviser has no restrictions.
   - It does not judge a card leaving a hand or a deck: `CardPlay` already checks the icons for a play from the hand, and refuses an adviser-only card to a site and a site-only card faceup to advisers.
   - It does not judge a move inside one play area. That only turns the card over: a facedown adviser played faceup in place, or a battle plan revealed by `CampaignPlanApplication`.
   - The refusal is `Impossible` with the code `restriction-icon`, like `locked`: an optional operation is skipped and a required one rejects.
   - Cost if wrong: a power that reveals a facedown site-only adviser in place is not refused. No implemented power does.
2. **The audit** (spec, "The restriction-icon rule"). The search was `Swap(`, `Take(Piece.Card`, `Give(Piece.Card`, `Move(Piece.Card` and `Play(` across `src/main/scala`.
   - **Horned Mask changes its operations.** It took a site card into the play area with a `Take` (landing faceup), then flipped it facedown with a separate `Flip`. The rule refuses the `Take` of a site-only card, so `HornedMaskSuite`'s "a site-only denizen becomes a facedown adviser" would fail. It now emits one `Move` that lands the card facedown. Its behavior, its tests and the knowledge players have of the card are unchanged: neither shape records knowledge for a card leaving a site's denizens (`CardKnowledgeMoves`).
   - **These pass unchanged:**
     - Dream Thief, Enchantress and Twin Brother swap advisers between play areas.
     - Great Herd swaps cards between sites.
     - Catacombs, Oracle, `SiteRelicTake` (Skeleton Key, Barbed Net), Magic Carpet, Conspiracy and Lost Tongue move relics or Visions, or move cards to or from piles and hands. Relics and Visions print no restriction icons.
     - The engine's own card moves are a play from a hand (`CardPlay`, `SetupProcedure`), a reveal in place (`CampaignPlanApplication`) or a discard to a pile (`CampaignRaid`).
   - Task 6 records this in the spec's section.
3. **"Not offered" names options, not the power.**
   - The rulings say Homesteaders is "not offered when that site is full", and that adviser-only and locked advisers "are not offered". Both mean options of its question.
   - Homesteaders and Resettle are usable whenever their cost is payable. When nothing can move, nothing is asked and the `used.none` line is written. This follows Enchantress and Dark Enforcer.
   - Every locked denizen in the catalog is also adviser-only, so the restriction-icon rule and `LockedCards` both hide it. The tests use a locked adviser-only card.
   - Cost if wrong: a player can spend an action on Homesteaders at a full site and move nothing.
4. **Resettle asks two questions:** which adviser, then which site.
   - The site question offers `Site` options: every site in play with room, map order, Resettle's own included. It is asked even when only one site has room.
   - The search tries every site for each adviser, so an adviser-only or locked adviser, refused at every site, is hidden from the first question.
   - With no site with room, the adviser question is not asked and `used.none` is written.
5. **Roving Terror takes the last slot at the site, not the discarded card's.**
   - A site's card row has no gaps, and `Move` appends.
   - Its candidates are the faceup denizens and ruined edifices at every other site in play, in map then site order, read through `SiteCards` with every suit.
   - An intact edifice is locked and is never a candidate. No denizen at a site is locked, since every locked denizen is adviser-only.
   - The discard and the move are one required batch, so a card whose discard a restriction refuses (the Hall of Ministers) is hidden.
6. **Moving Market's question and gain.**
   - It offers every empty slot at the other sites in play, as the ruling says, in map then slot order, then a "Skip" button.
     - A chosen slot moves the card to that site, where it takes the next slot.
     - Cost if wrong: a site with two empty slots shows two equivalent options.
   - The move is a required batch. The gain is a separate, optional batch, so an empty Nomad bank never rejects the move.
   - With nothing gained the line is `moved.only`, "Moved it to {site}.", rather than "gained 0 favor". The spec's table has no row for this, so Task 6 adds it.
   - The question's decision is narrated, so Skip writes nothing, as the spec says.
7. **Moving Market appends at the Trade's root**, after the source, cost and gain, as Knights Errant appends to a Muster.
   - A decision inside the Economy `Branch` would be re-derived through `MusterSource.resolve`, which refuses a source that holds tokens (`EconomyCardNotEmpty`), and the Trade's cost places tokens on Moving Market.
   - The root append avoids that, so this plan does not depend on reproducing the failure.
8. **Registration pins.**
   - Moving Market joins `PowerKindsCatalogSuite.modifiers` (`persistent: false`, so `PlayerSelected`).
   - The three ACTION powers are not pinned there: the flag does not decide how an ACTION activates.
   - Each suite runs through the production catalogs (`PhasePowerHarness`, `WalkerPowerCatalog.default`), so an unregistered power fails its suite.
9. **The spec's "Verify at plan time" items for this slice:**
   - **Moving Market's board panel needs no change.**
     - `BoardHomes.has` gives a `site-slot` option a home at any site below its capacity.
     - `ServerUiSupport.siteDetails` draws an open slot keyed `site-slot:{site}:{index}` at every site.
     - A `button` option has no home, so it is drawn as a Decide button in the Action panel. `BoardDecisionPanelSuite`'s "a Decide button submits at once" mixes a board option and a button.
     - `WalkerDecisionProjector` writes a slot's row as "Empty slot at {site}".
     - Task 5 adds a frontend test of the slots-plus-Skip question.
   - **Defame and Moving Market:** see ruling 7.
   - **Generic lines:**
     - `DetailLines` writes "{subject} discarded {card} to the {Region} discard" for a `Discard.Denizen`, which Roving Terror's line covers. A ruined edifice's discard writes no generic line.
     - No generic line exists for a card's `Move` between a play area and a site, or between sites (`DetailLines` reads card moves only to write "revealed" inside one play area). So Homesteaders, Resettle and Moving Market cover nothing.
     - The favor Moving Market gains is the actor's, inside a Trade run, where `DetailLines.gainedFavor` writes nothing, so its line covers nothing either.
   - **Registration:** described under Architecture; each card leaves `catalog/holding/`.
   - **Filler cards.** Homesteaders is filler in several action suites (Dark Enforcer, Bed of Roots, Armed Mob, Dream Thief, Honor Guard, Enchantress, Second Chance, Insomnia, Ballot Box). They read `usableNow(...).exists(_.power.id == X)` or a specific question's options, so its new ACTION changes none of them. The full test run confirms this.

## File Structure

Production, under `src/main/scala/oathdigital/` unless stated:

| File | Change | Responsibility | Task |
|---|---|---|---|
| `gameplay/operations/RestrictionIcons.scala` | create | the restriction-icon rule | 1 |
| `gameplay/operations/OperationRestrictions.scala` | modify | `printed` adds `RestrictionIcons` | 1 |
| `gameplay/powers/wake/HornedMask.scala` | modify | the take lands facedown in one `Move` | 1 |
| `gameplay/powers/SiteRoom.scala` | create | which sites have room, and their empty slots | 2 |
| `gameplay/powers/action/Homesteaders.scala` | create | Homesteaders' card and ACTION | 2 |
| `gameplay/powers/action/SelfActionPowers.scala` | modify | registers Homesteaders | 2 |
| `gameplay/powers/action/Resettle.scala` | create | Resettle's card and ACTION | 3 |
| `gameplay/powers/action/OtherActionPowers.scala` | modify | registers Resettle | 3 |
| `gameplay/powers/action/RovingTerror.scala` | create | Roving Terror's card and ACTION | 4 |
| `gameplay/powers/action/WorldActionPowers.scala` | modify | registers Roving Terror | 4 |
| `gameplay/powers/economy/MovingMarket.scala` | create | Moving Market's card and Trade modifier | 5 |
| `gameplay/powers/ActionModifiers.scala` | modify | registers Moving Market | 5 |
| `gameplay/powers/MusterPowers.scala` | modify | retires Moving Market's reviewed stub | 5 |
| `catalog/holding/HoldingHearthDenizens.scala` | modify | Homesteaders leaves | 2 |
| `catalog/holding/HoldingNomadDenizens.scala` | modify | Resettle and Moving Market leave | 3, 5 |
| `catalog/holding/HoldingBeastDenizens.scala` | modify | Roving Terror leaves | 4 |
| `gameplay/cards/NewFoundations.scala` | modify | imports each moved card from its power's package | 2–5 |

Tests, under `src/test/scala/oathdigital/` unless stated:

| File | Change | Tests | Task |
|---|---|---|---|
| `gameplay/RestrictionIconsSuite.scala` | create | 9 | 1 |
| `gameplay/OperationRestrictionsSuite.scala` | modify | 0 (one assertion filters the new rule) | 1 |
| `gameplay/powers/action/HomesteadersSuite.scala` | create | 5 | 2 |
| `gameplay/powers/action/ResettleSuite.scala` | create | 5 | 3 |
| `gameplay/powers/action/RovingTerrorSuite.scala` | create | 5 | 4 |
| `gameplay/powers/economy/MovingMarketSuite.scala` | create | 6 | 5 |
| `gameplay/PowerKindsCatalogSuite.scala` | modify | 0 (a list entry) | 5 |
| `frontend/src/test/scala/oathdigital/frontend/BoardDecisionPanelSuite.scala` | modify | 1 (frontend) | 5 |

Docs: `docs/ROADMAP.md` and the batch 4 design spec, in Task 6.

---

### Task 1: The restriction-icon rule, and Horned Mask's audit change

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/RestrictionIcons.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala` (imports, `printedBy`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala` (class doc comment; `take`'s `yield`)
- Create: `src/test/scala/oathdigital/gameplay/RestrictionIconsSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala` (the test "the Grand Scepter and the Hall of Ministers hold whichever cards a catalog lists")

**Interfaces:**
- Consumes: `OperationRestriction` (`model/OperationReason.scala`), `OperationRestrictions.inPlay` (`private[operations]`), `Operation.flatten`.
- Produces: `final case class RestrictionIcons(adviserOnly: Set[CardId], siteOnly: Set[CardId]) extends OperationRestriction`, with refusal code `"restriction-icon"`. Later tasks rely on it only through `OperationRestrictions.printed`, which every walker step and `PhasePowerHarness` already use.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/RestrictionIconsSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.operations.{OperationRestrictions, RestrictionIcons}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.present

/** The restriction-icon rule (catalog batch 4 design, "The restriction-icon
  * rule"): whichever operation moves it, an adviser-only card cannot land
  * faceup at a site, and a site-only card cannot land faceup in a player's
  * advisers.
  */
class RestrictionIconsSuite extends munit.FunSuite:
  private val set = OperationRestrictions.forCatalog(catalog)
  /** Adviser-only, and not locked. */
  private val adviserOnly = CatalogNames.denizen("Enchantress")
  /** Site-only. */
  private val siteOnly = CatalogNames.denizen("Wayside Inn")
  /** A plain Beast card, unrestricted. */
  private val plainCard = CatalogNames.denizen("Errand Boy")
  /** p1's pawn site on the quiet table. */
  private val home = CatalogNames.site("Ancient City")
  private val atHome = PositionedLocation(Location.Site(home))
  private val mine = PositionedLocation(Location.PlayArea(p1))
  private val theirs = PositionedLocation(Location.PlayArea(p2))

  private def refusal(ready: ReadyGame, operation: CoreOperation)
      : Option[String] =
    set.active(Vector.empty, Vector.empty)
      .flatMap(_.reason(ready, operation)).headOption.map(_.code)

  /** p1 holds the adviser-only card faceup; the plain card stands at p1's
    * site. */
  private def adviserOnlyHeld: ReadyGame =
    Table.start.adviser(p1, adviserOnly).denizen(plainCard, at = home).ready

  /** The site-only card stands at p1's site; p1 holds the plain card
    * faceup. */
  private def siteOnlyAtHome: ReadyGame =
    Table.start.denizen(siteOnly, at = home).adviser(p1, plainCard).ready

  test("an adviser-only adviser cannot be moved or swapped to a site"):
    val ready = adviserOnlyHeld
    assertEquals(Vector[CoreOperation](
      Move(Piece.Card(adviserOnly), mine, atHome),
      Swap(adviserOnly, mine, plainCard, atHome)).map(refusal(ready, _)),
      Vector.fill(2)(Some("restriction-icon")))

  test("a site-only card cannot be taken, moved or swapped into advisers " +
      "faceup"):
    val ready = siteOnlyAtHome
    assertEquals(Vector[CoreOperation](
      Take(Piece.Card(siteOnly), p1, Location.Site(home),
        Location.PlayArea(p1)),
      Move(Piece.Card(siteOnly), atHome, mine),
      Swap(siteOnly, atHome, plainCard, mine)).map(refusal(ready, _)),
      Vector.fill(3)(Some("restriction-icon")))

  test("a site-only card that lands facedown passes, as Horned Mask takes it"):
    assertEquals(refusal(siteOnlyAtHome, Move(Piece.Card(siteOnly), atHome,
      mine, resultingOrientation = Some(Orientation.FaceDown))), None)

  test("a facedown adviser has no restrictions"):
    val ready = Table.start.adviser(p1, siteOnly, facedown = true).ready
    assertEquals(Vector[CoreOperation](
      Move(Piece.Card(siteOnly), mine, theirs),
      Give(Piece.Card(siteOnly), p1, Location.PlayArea(p1),
        Location.PlayArea(p2))).map(refusal(ready, _)),
      Vector.fill(2)(None))

  test("a card that lands where its icon allows passes"):
    val held = Table.start.adviser(p1, adviserOnly).adviser(p2, plainCard)
      .ready
    assertEquals(refusal(held, Swap(adviserOnly, mine, plainCard, theirs)),
      None)
    val elsewhere =
      PositionedLocation(Location.Site(CatalogNames.site("Broken Peaks")))
    assertEquals(refusal(siteOnlyAtHome,
      Move(Piece.Card(siteOnly), atHome, elsewhere)), None)
    val plain = Table.start.adviser(p1, plainCard).ready
    assertEquals(refusal(plain, Move(Piece.Card(plainCard), mine, atHome)),
      None)

  test("a card leaving a hand, or turned over in its own play area, is not " +
      "judged"):
    val drawn = Table.start.hand(p1, adviserOnly).ready
    assertEquals(refusal(drawn, Move(Piece.Card(adviserOnly),
      PositionedLocation(Location.Hand(p1)), atHome,
      Some(Orientation.FaceUp))), None)
    val facedown = Table.start.adviser(p1, siteOnly, facedown = true).ready
    assertEquals(refusal(facedown, Move(Piece.Card(siteOnly), mine, mine,
      Some(Orientation.FaceUp))), None)

  test("Bury ignores the icons"):
    assertEquals(refusal(adviserOnlyHeld,
      Bury(BuryableCard.Denizen(adviserOnly), mine)), None)

  test("the refusal is Impossible, so an optional move is skipped"):
    val reason = set.active(Vector.empty, Vector.empty).flatMap(_.reason(
      adviserOnlyHeld, Move(Piece.Card(adviserOnly), mine, atHome))).head
    assertEquals(reason.kind, OperationReasonKind.Impossible)

  test("the catalog's icons fill the rule: Enchantress is adviser-only, " +
      "Wayside Inn site-only, and Errand Boy neither"):
    val icons = present(set.printed.collectFirst {
      case value: RestrictionIcons => value }, "the restriction-icon rule")
    assert(icons.adviserOnly(adviserOnly) && !icons.siteOnly(adviserOnly))
    assert(icons.siteOnly(siteOnly) && !icons.adviserOnly(siteOnly))
    assert(!icons.adviserOnly(plainCard) && !icons.siteOnly(plainCard))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.RestrictionIconsSuite"`
Expected: compile failure, `RestrictionIcons` is not a member of `oathdigital.gameplay.operations`.

- [ ] **Step 3: Write the rule**

Create `src/main/scala/oathdigital/gameplay/operations/RestrictionIcons.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** Restriction icons govern how a card is played, moved and swapped
  * (catalog batch 4 design, "The restriction-icon rule"). A `Move` that
  * takes a card out of a site or a play area to another location may not
  * land an adviser-only card faceup at a site, or a site-only card faceup in
  * a player's advisers. A `Swap`, a `Take` and a `Give` flatten to such
  * moves, so one restriction judges them all.
  *
  * A card lands as the move's `resultingOrientation` says, or else as it
  * shows where it lies. A facedown adviser has no restrictions, so a card
  * landing facedown passes. Only denizens print these icons: edifices are
  * never advisers, and relics and Visions print none.
  *
  * Card play from a hand is not judged here, since `CardPlay` checks the
  * icons itself. Nor is a move inside one play area, which only turns a card
  * over.
  */
final case class RestrictionIcons(adviserOnly: Set[CardId],
    siteOnly: Set[CardId]) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    Operation.flatten(operation).iterator.collect {
      case move @ Move(Piece.Card(card), from, to, _)
          if from.location != to.location &&
            OperationRestrictions.inPlay(from.location) => (move, card)
    }.flatMap((move, card) => refusal(ready, move, card)).nextOption()

  private def refusal(ready: ReadyGame, move: Move, card: CardId)
      : Option[OperationReason] = move.to.location match
    case _: Location.Site
        if adviserOnly(card) && landsFaceUp(ready, move, card) =>
      Some(refused(s"${card.value} is adviser-only and cannot be moved " +
        "to a site"))
    case _: Location.PlayArea
        if siteOnly(card) && landsFaceUp(ready, move, card) =>
      Some(refused(s"${card.value} is site-only and cannot be a faceup " +
        "adviser"))
    case _ => None

  private def landsFaceUp(ready: ReadyGame, move: Move, card: CardId)
      : Boolean =
    move.resultingOrientation.orElse(shown(ready, card, move.from.location))
      .forall(_ == Orientation.FaceUp)

  /** The orientation `card` shows where it lies, if it lies there. */
  private def shown(ready: ReadyGame, card: CardId, from: Location)
      : Option[Orientation] =
    val current = ready.game.current
    from match
      case Location.PlayArea(owner) => current.players
        .find(_.player == owner).flatMap(_.advisers.collectFirst {
          case DenizenState(`card`, orientation, _) => orientation })
      case Location.Site(site) => current.map.sites.get(site)
        .flatMap(_.denizens.collectFirst {
          case DenizenState(`card`, orientation, _) => orientation })
      case _ => None

  private def refused(detail: String): OperationReason =
    OperationReason("restriction-icon", detail, OperationReasonKind.Impossible)
```

- [ ] **Step 4: Add it to the printed restrictions**

In `src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala`:

- Change the catalog import to `import oathdigital.catalog.{AdviserOnly, ExecutableCatalog, Locked, SiteOnly}`.
- In the class doc comment, change "`printed` are the restrictions a catalog's cards print: [[LockedCards]], holding every card whose `Locked` trait shows the lock icon, and" to "`printed` are the restrictions a catalog's cards print: [[LockedCards]], holding every card whose `Locked` trait shows the lock icon, [[RestrictionIcons]], holding every adviser-only and site-only denizen, and".
- Replace `printedBy`'s last line, `Vector(LockedCards(locked), HallOfMinisters, GrandScepter)`, with:

```scala
    val adviserOnly: Set[CardId] =
      catalog.denizens.collect { case d: AdviserOnly => d.id: CardId }.toSet
    val siteOnly: Set[CardId] =
      catalog.denizens.collect { case d: SiteOnly => d.id: CardId }.toSet
    Vector(LockedCards(locked), RestrictionIcons(adviserOnly, siteOnly),
      HallOfMinisters, GrandScepter)
```

- [ ] **Step 5: Keep the printed-set assertion about other restrictions**

In `src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala`, the test "the Grand Scepter and the Hall of Ministers hold whichever cards a catalog lists" compares `bare.printed.filterNot(_.isInstanceOf[LockedCards])` with the Hall and the Scepter. Change that filter to:

```scala
    assertEquals(bare.printed.filterNot(restriction =>
      restriction.isInstanceOf[LockedCards] ||
        restriction.isInstanceOf[RestrictionIcons]),
      Vector[OperationRestriction](HallOfMinisters, GrandScepter))
```

and add `RestrictionIcons` to the file's `oathdigital.gameplay.operations` import.

- [ ] **Step 6: Run the rule's suites**

Run: `./sbtw "testOnly oathdigital.gameplay.RestrictionIconsSuite oathdigital.gameplay.OperationRestrictionsSuite"`
Expected: PASS, 9 tests in the new suite.

- [ ] **Step 7: See the audit's one failure**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.HornedMaskSuite"`
Expected: FAIL in "a site-only denizen becomes a facedown adviser, and the use is recorded once per turn". Wayside Inn is site-only, and Horned Mask's `Take` lands it faceup before its `Flip`.

- [ ] **Step 8: Land Horned Mask's card facedown in one move**

In `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`, replace the `yield` of `take`:

```scala
    yield discards ++ returns ++ Vector[CoreOperation](
      Take(Piece.Card(card.id), actor, Location.Site(siteId),
        Location.PlayArea(actor)),
      Flip(card.id, Location.PlayArea(actor), Orientation.FaceDown))
```

with:

```scala
    yield discards ++ returns :+ Move(Piece.Card(card.id),
      PositionedLocation(Location.Site(siteId)),
      PositionedLocation(Location.PlayArea(actor)),
      resultingOrientation = Some(Orientation.FaceDown))
```

In the class doc comment, replace "The effect is: discard the chosen adviser when the area is full, return the taken card's favor and secrets, `Take` the card into the play area and `Flip` it facedown." with:

```scala
  * The effect is: discard the chosen adviser when the area is full, return the
  * taken card's favor and secrets, and move the card into the play area
  * facedown in one `Move`. The restriction-icon rule then sees it land
  * facedown, so a site-only denizen can be taken: a facedown adviser has no
  * restrictions.
```

- [ ] **Step 9: Run Horned Mask's suite again**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.HornedMaskSuite"`
Expected: PASS, every test unchanged.

- [ ] **Step 10: Run the restriction and card-play suites**

Run: `./sbtw "testOnly oathdigital.gameplay.*Restriction* oathdigital.gameplay.CardPlay* oathdigital.gameplay.powers.action.* oathdigital.gameplay.powers.whenplayed.*"`
Expected: PASS. Dream Thief, Enchantress, Twin Brother and Great Herd pass unchanged, which confirms ruling 2.

- [ ] **Step 11: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/RestrictionIcons.scala \
  src/main/scala/oathdigital/gameplay/operations/OperationRestrictions.scala \
  src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala \
  src/test/scala/oathdigital/gameplay/RestrictionIconsSuite.scala \
  src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala
git commit -m "feat(operations): restriction icons govern card moves and swaps"
```

---

### Task 2: `SiteRoom` and Homesteaders

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/SiteRoom.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Homesteaders.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` (remove `HomesteadersCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `HomesteadersCard` from `gameplay.powers.action`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/HomesteadersSuite.scala`

**Interfaces:**
- Consumes: `RestrictionIcons` through `OperationRestrictions.printed` (Task 1), `PaidAction`, `PowerAccess.pawnSite`, `PlayerFacts.player`, `PowerAnswers.one`, `NoteSupport.answer`.
- Produces:
  - `object SiteRoom` in package `oathdigital.gameplay.powers`:
    - `def emptySlots(catalog: ExecutableCatalog, ready: ReadyGame, site: SiteId): Vector[Int]`
    - `def hasRoom(catalog: ExecutableCatalog, ready: ReadyGame, site: SiteId): Boolean`
    - `def open(catalog: ExecutableCatalog, ready: ReadyGame): Vector[SiteId]`
  - `object HomesteadersCard`, `final case class Homesteaders` and its companion: `id`, `decisionId = "power.homesteaders.adviser"`, `moved`, `nothing`, `forCatalog(catalog: ExecutableCatalog): Homesteaders`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/HomesteadersSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class HomesteadersSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val source = DecisionOptionRef.Denizen(homesteaders)
  private val power = Homesteaders.forCatalog(catalog)
  /** p1's pawn site on the quiet table, with room for three cards. */
  private val home = CatalogNames.site("Ancient City")
  /** A plain Hearth denizen. */
  private val landWarden = CatalogNames.denizen("Land Warden")
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** Adviser-only. */
  private val pilgrimage = CatalogNames.denizen("Pilgrimage")
  /** Locked and adviser-only. */
  private val marriage = CatalogNames.denizen("Marriage")

  /** p1's Act at Ancient City, holding Homesteaders faceup. */
  private def holdingHomesteaders: Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, homesteaders)

  /** The walk Homesteaders started and the walk after `card` was chosen. */
  private case class Chosen(started: OathTransition, answered: OathTransition)

  private def choose(ready: ReadyGame, card: DenizenId): Chosen =
    val started = accepted(use(ready, power, source))
    Chosen(started, accepted(answer(started, p1, Homesteaders.decisionId,
      pick(DecisionOptionRef.Denizen(card)))))

  test("it offers the player's faceup advisers, itself included, and not a " +
      "facedown one"):
    val ready = holdingHomesteaders.adviser(p1, landWarden)
      .adviser(p1, errandBoy, facedown = true).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, Homesteaders.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("denizen" -> homesteaders.value,
      "denizen" -> landWarden.value)))

  test("adviser-only and locked advisers are not offered"):
    val ready = holdingHomesteaders.adviser(p1, pilgrimage)
      .adviser(p1, marriage).ready
    val t = accepted(use(ready, power, source))
    assertEquals(offered(t, p1),
      Some(Vector("denizen" -> homesteaders.value)))

  test("the chosen adviser moves to the pawn's site with its tokens, and the " +
      "line names it"):
    val ready = holdingHomesteaders.adviser(p1, landWarden)
      .tokens(landWarden, favor = 1).ready
    val Chosen(t, done) = choose(ready, landWarden)
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = Look(after(done))
    assertEquals(end.cardsAt(home), Vector[CardId](landWarden))
    assertEquals(end.tokensOn(landWarden), Tokens(1, 0))
    assertEquals(end.advisers(p1), Vector[CardId](homesteaders))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Moved ${landWarden.value} to ${home.value}.",
      covers = false)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("it can move itself"):
    val Chosen(_, done) = choose(holdingHomesteaders.ready, homesteaders)
    assertEquals(Look(after(done)).cardsAt(home), Vector[CardId](homesteaders))
    assertEquals(Look(after(done)).advisers(p1), Vector.empty[CardId])

  test("with the pawn's site full it asks nothing and the line says so"):
    val ready = holdingHomesteaders.adviser(p1, landWarden)
      .denizen(errandBoy, at = home).denizen("Wrestlers", at = home)
      .denizen("Elders", at = home).ready
    val done = accepted(use(ready, power, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p1),
      Vector[CardId](homesteaders, landWarden))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No adviser could be moved.", covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.HomesteadersSuite"`
Expected: compile failure, `Homesteaders` is not found.

- [ ] **Step 3: Write `SiteRoom`**

Create `src/main/scala/oathdigital/gameplay/powers/SiteRoom.scala`:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** Room for a card at a site. Operations never check a site's capacity, so
  * a power that moves a card to a site offers only sites with room (catalog
  * batch 4 rulings, "Site capacity"). A site's card row has no gaps, so a
  * card moved there takes its first empty slot.
  */
object SiteRoom:
  /** The empty slots of `site`'s card row, in slot order: none when the site
    * is full or not in play. */
  def emptySlots(catalog: ExecutableCatalog, ready: ReadyGame, site: SiteId)
      : Vector[Int] =
    val map = ready.game.current.map
    (for
      state <- map.sites.get(site)
      if map.inPlay.contains(site)
      capacity <- catalog.site(site).map(_.capacity)
    yield (state.denizens.size until capacity).toVector)
      .getOrElse(Vector.empty)

  def hasRoom(catalog: ExecutableCatalog, ready: ReadyGame, site: SiteId)
      : Boolean = emptySlots(catalog, ready, site).nonEmpty

  /** The sites in play with room, in map order. */
  def open(catalog: ExecutableCatalog, ready: ReadyGame): Vector[SiteId] =
    ready.game.current.map.inPlay.filter(hasRoom(catalog, ready, _))
```

- [ ] **Step 4: Write Homesteaders**

Remove `object HomesteadersCard` (its five lines and the blank line after) from `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/action/Homesteaders.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers,
  SiteRoom}
import oathdigital.model._

object HomesteadersCard extends Denizen(DenizenId("127"), "Homesteaders", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.homesteaders"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Move one of your faceup advisers to your site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Homesteaders (card 127), ACTION, free: move one of the player's faceup
  * advisers to the pawn's site.
  *
  * The candidates are the player's faceup denizen advisers, Homesteaders
  * included, in adviser order, read live, and only while the pawn's site
  * has room ([[SiteRoom]]). The move is one `Move` in a required batch, so
  * the restriction-icon rule hides an adviser-only adviser and the Locked
  * rule a locked one; the question passes when nothing is left. The card
  * carries its favor and secrets. With no candidate nothing is asked and
  * one line says so.
  */
final case class Homesteaders private (catalog: ExecutableCatalog)
    extends PaidAction(HomesteadersCard.power):
  import Homesteaders._

  override def noteKeys: Vector[NoteKey] = Vector(moved, nothing)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => move(live, player, pending), required = true),
    Note(this.id, movedNote(_, player, source)))))

  /** The player's faceup denizen advisers, in adviser order, while the
    * pawn's site has room. */
  private def candidates(ready: ReadyGame, actor: PlayerId)
      : Vector[DenizenId] =
    val room = PowerAccess.pawnSite(ready, actor)
      .exists(SiteRoom.hasRoom(catalog, ready, _))
    if !room then Vector.empty
    else PlayerFacts.player(ready, actor).toOption.toVector
      .flatMap(_.advisers.collect {
        case DenizenState(id, Orientation.FaceUp, _) => id })

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    candidates(ready, actor) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(card => DecisionOption.Denizen(DecisionOptionRef.Denizen(card))),
        heading = Some("Homesteaders: move a faceup adviser to your site")),
        passWhenEmpty = true))

  /** No answer means no adviser could be moved. */
  private def move(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => (for
        site <- PowerAccess.pawnSite(ready, actor)
        card <- candidates(ready, actor)
          .find(card => DecisionOptionRef.Denizen(card) == ref)
      yield Vector[CoreOperation](Move(Piece.Card(card),
        PositionedLocation(Location.PlayArea(actor)),
        PositionedLocation(Location.Site(site))))).toRight(
        OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not an adviser Homesteaders can move"))

  /** Written when the chosen adviser now stands at the pawn's site. */
  private def movedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] =
    PowerSourceRef.of(source).map(card => (for
      case DecisionOptionRef.Denizen(chosen) <-
        NoteSupport.answer(states, decisionId)
      site <- PowerAccess.pawnSite(states.now, actor)
      if states.now.game.current.map.sites.get(site)
        .exists(_.denizens.exists(_.id == chosen))
    yield moved(card, NoteArg.Card(chosen), NoteArg.Site(site)))
      .getOrElse(nothing(card)))

object Homesteaders:
  val id: PowerId = HomesteadersCard.power.id
  val decisionId: String = "power.homesteaders.adviser"
  /** "Moved {card} to {site}." */
  val moved: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text(" to "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "No adviser could be moved." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No adviser could be moved.")))

  def forCatalog(catalog: ExecutableCatalog): Homesteaders =
    new Homesteaders(catalog)
```

The class imports its companion, so it names its own id `this.id`: a bare `id` would be ambiguous between `PaidAction.id` and the import, as in Dark Enforcer.

- [ ] **Step 5: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala`:

- Append `++ Vector(Homesteaders.forCatalog(catalog))` after `Vector(BedOfRoots.forCatalog(catalog))`.
- In the doc comment, change "The phase powers of catalog batch 2's slice 3 and catalog batch 3's slice 3a," to "The phase powers of catalog batch 2's slice 3, catalog batch 3's slice 3a and catalog batch 4's Homesteaders,".

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `HomesteadersCard` to the `oathdigital.gameplay.powers.action.{...}` import, in alphabetical order (after `GreatFeastCard`). The list entry `HomesteadersCard,` stays as it is: the name now resolves to the new import instead of the `catalog.holding._` wildcard.

- [ ] **Step 6: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.HomesteadersSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 7: Run the suites that hold Homesteaders as filler**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.* oathdigital.gameplay.powers.rest.InsomniaSuite oathdigital.gameplay.powers.whenplayed.* oathdigital.gameplay.powers.targeting.FortressRulesSuite"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/SiteRoom.scala \
  src/main/scala/oathdigital/gameplay/powers/action/Homesteaders.scala \
  src/main/scala/oathdigital/gameplay/powers/action/SelfActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/action/HomesteadersSuite.scala
git commit -m "feat(action): Homesteaders moves a faceup adviser to the pawn's site"
```

---

### Task 3: Resettle

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/Resettle.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `ResettleCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `ResettleCard` from `gameplay.powers.action`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/ResettleSuite.scala`

**Interfaces:**
- Consumes: `SiteRoom.open` (Task 2); `RestrictionIcons` through the printed restrictions (Task 1).
- Produces: `object ResettleCard`, `final case class Resettle` and its companion: `id`, `adviserDecisionId = "power.resettle.adviser"`, `siteDecisionId = "power.resettle.site"`, `moved`, `nothing`, `forCatalog(catalog: ExecutableCatalog): Resettle`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/ResettleSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class ResettleSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val resettle = CatalogNames.denizen("Resettle")
  private val source = DecisionOptionRef.Denizen(resettle)
  private val power = Resettle.forCatalog(catalog)
  /** p1's pawn site, with room for three cards. */
  private val home = CatalogNames.site("Ancient City")
  /** Two sites with room for one card each. */
  private val peaks = CatalogNames.site("Broken Peaks")
  private val giant = CatalogNames.site("Buried Giant")
  /** Plain Nomad denizens. */
  private val elders = CatalogNames.denizen("Elders")
  private val wildMounts = CatalogNames.denizen("Wild Mounts")
  private val mountainGiant = CatalogNames.denizen("Mountain Giant")
  /** A plain Hearth denizen. */
  private val landWarden = CatalogNames.denizen("Land Warden")
  /** Nomad and adviser-only. */
  private val pilgrimage = CatalogNames.denizen("Pilgrimage")
  /** Nomad, locked and adviser-only. */
  private val faithfulFriend = CatalogNames.denizen("Faithful Friend")
  /** A plain Beast denizen, filling Broken Peaks. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")

  /** p1's Act at Ancient City beside Resettle, site-only. p2 holds Wild
    * Mounts faceup with 1 favor on it. Errand Boy fills Broken Peaks; Buried
    * Giant is empty. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).denizen(resettle, at = home)
    .adviser(p2, wildMounts).tokens(wildMounts, favor = 1)
    .denizen(errandBoy, at = peaks)

  /** The walk Resettle started and the walk after `card` was chosen, parked
    * on the site question. */
  private case class Chosen(started: OathTransition, answered: OathTransition):
    def events: Vector[OathEvent] = started.events ++ answered.events

  private def chooseAdviser(ready: ReadyGame, card: DenizenId): Chosen =
    val started = accepted(use(ready, power, source))
    Chosen(started, accepted(answer(started, p1, Resettle.adviserDecisionId,
      pick(DecisionOptionRef.Denizen(card)))))

  private def chooseSite(chosen: Chosen, site: SiteId): OathTransition =
    accepted(answer(chosen.answered, p1, Resettle.siteDecisionId,
      pick(DecisionOptionRef.Site(site))))

  test("it offers every player's faceup Nomad advisers, the actor's own " +
      "included, and not other suits, facedown, adviser-only or locked ones"):
    val ready = staged.adviser(p1, elders).adviser(p2, landWarden)
      .adviser(p2, mountainGiant, facedown = true).adviser(p3, pilgrimage)
      .adviser(p3, faithfulFriend).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, Resettle.adviserDecisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(offered(t, p1), Some(Vector("denizen" -> elders.value,
      "denizen" -> wildMounts.value)))

  test("then it asks for a site in play with room, its own site included"):
    val chosen = chooseAdviser(staged.ready, wildMounts)
    assert(awaits(chosen.answered, Resettle.siteDecisionId),
      parked.parkedDecision(chosen.answered.state).toString)
    val options = offered(chosen.answered, p1).getOrElse(Vector.empty)
    assert(options.contains("site" -> home.value), options)
    assert(options.contains("site" -> giant.value), options)
    assert(!options.contains("site" -> peaks.value), options)

  test("the adviser moves to the chosen site with its tokens, and the line " +
      "names its owner"):
    val ready = staged.ready
    val chosen = chooseAdviser(ready, wildMounts)
    val done = chooseSite(chosen, giant)
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = Look(after(done))
    assertEquals(end.cardsAt(giant), Vector[CardId](wildMounts))
    assertEquals(end.tokensOn(wildMounts), Tokens(1, 0))
    assertEquals(end.advisers(p2), Vector.empty[CardId])
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used,
      s"Moved ${p2.value}'s ${wildMounts.value} to ${giant.value}.",
      covers = false)))
    val events = chosen.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)

  test("the actor's own adviser can go to Resettle's own site"):
    val done = chooseSite(chooseAdviser(staged.adviser(p1, elders).ready,
      elders), home)
    assertEquals(Look(after(done)).cardsAt(home),
      Vector[CardId](resettle, elders))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Moved ${p1.value}'s ${elders.value} to ${home.value}.",
      covers = false)))

  test("with no faceup Nomad adviser it asks nothing and the line says so"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(resettle, at = home).adviser(p2, landWarden).ready
    val done = accepted(use(ready, power, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](landWarden))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No Nomad adviser could be moved.", covers = false)))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ResettleSuite"`
Expected: compile failure, `Resettle` is not found.

- [ ] **Step 3: Write Resettle**

Remove `object ResettleCard` (its six lines and the blank line after) from `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/action/Resettle.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers, SiteRoom}
import oathdigital.model._

object ResettleCard extends Denizen(DenizenId("159"), "Resettle", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.resettle"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Move a faceup [suit-nomad] card from any " +
      "player's advisers to any site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Resettle (card 159, site-only), ACTION, free: move a faceup Nomad card
  * from any player's advisers to any site.
  *
  * Two questions. The first offers every player's faceup Nomad denizen
  * advisers, the actor's own included, in seat then adviser order, read
  * live, and only while some site in play has room ([[SiteRoom]]). The
  * second offers every site in play with room, in map order, Resettle's own
  * included. The move is one `Move` in a required batch, so the search hides
  * an adviser-only or locked adviser, which every site refuses, and the
  * first question passes when nothing is left. The card carries its favor
  * and secrets. With no candidate nothing is asked and one line says so.
  */
final case class Resettle private (catalog: ExecutableCatalog)
    extends PaidAction(ResettleCard.power):
  import Resettle._

  override def noteKeys: Vector[NoteKey] = Vector(moved, nothing)
  override def narratedDecisions: Set[String] =
    Set(adviserDecisionId, siteDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askAdviser(live, player)),
    Branch((live, pending) => askSite(live, player, pending)),
    BuildOps((live, pending) => move(live, pending), required = true),
    Note(this.id, movedNote(_, source)))))

  private final case class Candidate(owner: PlayerId, card: DenizenId)

  /** Every player's faceup Nomad denizen advisers, in seat then adviser
    * order, while some site in play has room. */
  private def candidates(ready: ReadyGame): Vector[Candidate] =
    if SiteRoom.open(catalog, ready).isEmpty then Vector.empty
    else for
      player <- ready.game.current.players
      card <- player.advisers.collect {
        case DenizenState(id, Orientation.FaceUp, _)
            if catalog.suitOf(id).contains(Suit.Nomad) => id }
    yield Candidate(player.player, card)

  private def askAdviser(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    candidates(ready) match
      case Vector() => Vector.empty
      case found => Vector(Decide(adviserDecisionId, actor,
        DecisionQuery.ChooseOne(found.map(c =>
          DecisionOption.Denizen(DecisionOptionRef.Denizen(c.card))),
          heading = Some("Resettle: choose a faceup Nomad adviser to move")),
        passWhenEmpty = true))

  /** Asked once an adviser is chosen. */
  private def askSite(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    if PowerAnswers.one(pending, adviserDecisionId).isEmpty then Vector.empty
    else Vector(Decide(siteDecisionId, actor, DecisionQuery.ChooseOne(
      SiteRoom.open(catalog, ready).map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some("Resettle: choose the site to move it to"))))

  /** No adviser answer means no adviser could be moved. */
  private def move(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    (PowerAnswers.one(pending, adviserDecisionId),
        PowerAnswers.one(pending, siteDecisionId)) match
      case (None, _) => Right(Vector.empty)
      case (Some(ref), Some(DecisionOptionRef.Site(site))) => (for
          chosen <- candidates(ready)
            .find(c => DecisionOptionRef.Denizen(c.card) == ref)
          if SiteRoom.open(catalog, ready).contains(site)
        yield Vector[CoreOperation](Move(Piece.Card(chosen.card),
          PositionedLocation(Location.PlayArea(chosen.owner)),
          PositionedLocation(Location.Site(site))))).toRight(
        OathViolation.InvalidEventOrder(
          s"Resettle cannot move ${ref.wireId} to ${site.value}"))
      case (Some(_), Some(other)) => Left(OathViolation.InvalidEventOrder(
        s"${other.wireId} is not a site Resettle can move to"))
      case (Some(_), None) => Left(PowerAnswers.missing(siteDecisionId))

  /** The owner is read before the move; written when the adviser now stands
    * at the chosen site. */
  private def movedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] =
    PowerSourceRef.of(source).map(card => (for
      case DecisionOptionRef.Denizen(chosen) <-
        NoteSupport.answer(states, adviserDecisionId)
      case DecisionOptionRef.Site(site) <-
        NoteSupport.answer(states, siteDecisionId)
      (before, after) <- states.previous
      owner <- before.game.current.players
        .find(_.advisers.exists(_.id == chosen))
      if after.game.current.map.sites.get(site)
        .exists(_.denizens.exists(_.id == chosen))
    yield moved(card, NoteArg.Player(owner.player), NoteArg.Card(chosen),
      NoteArg.Site(site))).getOrElse(nothing(card)))

object Resettle:
  val id: PowerId = ResettleCard.power.id
  val adviserDecisionId: String = "power.resettle.adviser"
  val siteDecisionId: String = "power.resettle.site"
  /** "Moved {Blue}'s {card} to {site}." */
  val moved: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text("'s "), NotePart.Arg(1),
    NotePart.Text(" to "), NotePart.Arg(2), NotePart.Text(".")))
  /** "No Nomad adviser could be moved." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No Nomad adviser could be moved.")))

  def forCatalog(catalog: ExecutableCatalog): Resettle =
    new Resettle(catalog)
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala`:

- Append `++ Vector(Resettle.forCatalog(catalog))` after `Vector(HonorGuard.forCatalog(catalog))`.
- In the doc comment, change "The phase powers of catalog batch 2's slice 4 and catalog batch 3's slice 3b, actions on others," to "The phase powers of catalog batch 2's slice 4, catalog batch 3's slice 3b and catalog batch 4's Resettle, actions on others,".

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `ResettleCard` to the `oathdigital.gameplay.powers.action.{...}` import, in alphabetical order (after `RelicBreakerCard`).

If `SiteOnly` is no longer used in `HoldingNomadDenizens.scala`, remove it from that file's import. Moving Market, Convoys and others still use it, so it should stay.

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.ResettleSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/Resettle.scala \
  src/main/scala/oathdigital/gameplay/powers/action/OtherActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/action/ResettleSuite.scala
git commit -m "feat(action): Resettle moves a faceup Nomad adviser to a site"
```

---

### Task 4: Roving Terror

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/RovingTerror.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala` (remove `RovingTerrorCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `RovingTerrorCard` from `gameplay.powers.action`)
- Create: `src/test/scala/oathdigital/gameplay/powers/action/RovingTerrorSuite.scala`

**Interfaces:**
- Consumes: `SiteCards` (`private[action]`: `at`, `discard`, `option`, `refOf`), `SiteRulers.siteOf`, `PaidAction`.
- Produces: `object RovingTerrorCard`, `final case class RovingTerror` and its companion: `id`, `decisionId = "power.roving-terror.card"`, `replaced`, `nothing`, `forCatalog(catalog: ExecutableCatalog): RovingTerror`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/action/RovingTerrorSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class RovingTerrorSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val terror = CatalogNames.denizen("Roving Terror")
  private val source = DecisionOptionRef.Denizen(terror)
  private val power = RovingTerror.forCatalog(catalog)
  /** p1's pawn site, and two other sites in play. */
  private val home = CatalogNames.site("Ancient City")
  private val peaks = CatalogNames.site("Broken Peaks")
  private val giant = CatalogNames.site("Buried Giant")
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** An edifice, locked while intact. */
  private val debate = CatalogNames.edifice("Hall of Debate")

  /** p1's Act at Ancient City beside Roving Terror, site-only, with the
    * start's 1 faceup secret. Errand Boy stands at Broken Peaks. */
  private def staged: Table = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).denizen(terror, at = home)
    .denizen(errandBoy, at = peaks)

  /** The walk Roving Terror started and the walk after `ref` was chosen. */
  private case class Chosen(started: OathTransition, answered: OathTransition)

  private def choose(ready: ReadyGame, ref: DecisionOptionRef): Chosen =
    val started = accepted(use(ready, power, source))
    Chosen(started, accepted(answer(started, p1, RovingTerror.decisionId,
      pick(ref))))

  test("it places a secret and offers the faceup denizens and ruined " +
      "edifices at the other sites, not its own site's or an intact edifice"):
    val ready = staged.denizen(wrestlers, at = home)
      .edifice(debate, EdificeSide.Intact, at = giant).ready
    val t = accepted(use(ready, power, source))
    assert(awaits(t, RovingTerror.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(terror), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> errandBoy.value)))

  test("the chosen denizen gets the standard discard, and Roving Terror " +
      "moves there with its secret"):
    val ready = staged.tokens(errandBoy, favor = 1, secrets = 1)
      .bankFavor(Suit.Beast, 2).ready
    val Chosen(t, done) = choose(ready, DecisionOptionRef.Denizen(errandBoy))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = Look(after(done))
    assertEquals(end.cardsAt(peaks), Vector[CardId](terror))
    assertEquals(end.cardsAt(home), Vector.empty[CardId])
    assertEquals(end.tokensOn(terror), Tokens(0, 1))
    assert(end.discardAfter(peaks).contains(errandBoy))
    // Errand Boy's favor goes back to the Beast bank, its secret to p1
    // facedown.
    assertEquals(end.bankFavor(Suit.Beast), 2 + 1)
    assertEquals(end.faceDownSecrets(p1), Look(ready).faceDownSecrets(p1) + 1)
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Replaced ${errandBoy.value} at ${peaks.value}.",
      covers = true)))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("a ruined edifice goes to the bottom of the edifice deck"):
    val ready = staged.edifice(debate, EdificeSide.Ruined, at = giant).ready
    val Chosen(_, done) = choose(ready, DecisionOptionRef.Edifice(debate))
    val end = after(done)
    assertEquals(Look(end).cardsAt(giant), Vector[CardId](terror))
    assertEquals(end.game.current.commonCards.edificeDeck.lastOption,
      Some(debate))

  test("with nothing to replace it asks nothing, keeps its secret, and the " +
      "line says so"):
    val ready = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(terror, at = home)
      .edifice(debate, EdificeSide.Intact, at = giant).ready
    val done = accepted(use(ready, power, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).cardsAt(home), Vector[CardId](terror))
    assertEquals(Look(after(done)).tokensOn(terror), Tokens(0, 1))
    assertEquals(NoteText.said(power, done.events), Vector(NoteText.Said(
      "used.none", "No card could be replaced.", covers = true)))

  test("it is unusable without a secret"):
    val broke = staged.secrets(p1, 0).ready
    assert(!usableNow(broke).exists(_.power.id == RovingTerror.id))
    assert(use(broke, power, source).isLeft)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.RovingTerrorSuite"`
Expected: compile failure, `RovingTerror` is not found.

- [ ] **Step 3: Write Roving Terror**

Remove `object RovingTerrorCard` (its six lines and the blank line after) from `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`. If that leaves `SiteOnly` unused in the file, remove it from the file's import.

Create `src/main/scala/oathdigital/gameplay/powers/action/RovingTerror.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object RovingTerrorCard extends Denizen(DenizenId("46"), "Roving Terror", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.roving-terror"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Discard a denizen card at any other site and " +
      "move this card there.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Roving Terror (card 46, site-only), ACTION: place 1 secret on this card,
  * then discard a denizen card at any other site and move this card there.
  *
  * The candidates are the faceup denizens and ruined edifices at every other
  * site in play, in map then site order, read live after the cost is paid
  * ([[SiteCards]], every suit). An intact edifice is locked, so it is never
  * offered. The chosen card gets the standard discard: favor to its suit's
  * bank, secrets to the player facedown. Roving Terror then moves to that
  * site carrying its secret, and takes the site's last slot, since a row
  * has no gaps. The discard and the move are one required batch, so a card
  * whose discard a restriction refuses (the Hall of Ministers) is hidden,
  * and the question passes when nothing is left. With no candidate the cost
  * stays paid and one line says so.
  *
  * The line covers the generic discard line.
  */
final case class RovingTerror private (catalog: ExecutableCatalog)
    extends PaidAction(RovingTerrorCard.power):
  import RovingTerror._

  private val cards = new SiteCards(catalog, Suit.all.toSet)
  override def noteKeys: Vector[NoteKey] = Vector(replaced, nothing)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => replace(live, player, pending),
      required = true),
    Note(this.id, replacedNote(_, source), covers = true))))

  private final case class Candidate(site: SiteId, card: SiteDenizenState,
      suit: Suit)

  /** The discardable cards at every site in play but Roving Terror's own. */
  private def candidates(ready: ReadyGame): Vector[Candidate] =
    SiteRulers.siteOf(ready, RovingTerrorCard.id).toVector.flatMap(here =>
      ready.game.current.map.inPlay.filter(_ != here).flatMap(site =>
        cards.at(ready, site).map((card, suit) =>
          Candidate(site, card, suit))))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    candidates(ready) match
      case Vector() => Vector.empty
      case found => Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
        found.map(c => cards.option(c.card)),
        heading = Some("Roving Terror: discard a card at another site and " +
          "move there")), passWhenEmpty = true))

  /** No answer means no card could be replaced. */
  private def replace(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => for
        here <- SiteRulers.siteOf(ready, RovingTerrorCard.id).toRight(
          OathViolation.InvalidEventOrder("Roving Terror is not at a site"))
        chosen <- candidates(ready).find(c => cards.refOf(c.card) == ref)
          .toRight(OathViolation.InvalidEventOrder(
            s"${ref.wireId} is not a card Roving Terror can replace"))
        discard <- cards.discard(ready, chosen.site, chosen.card, chosen.suit,
          actor, required = true)
      yield Vector(discard, Move(Piece.Card(RovingTerrorCard.id),
        PositionedLocation(Location.Site(here)),
        PositionedLocation(Location.Site(chosen.site))))

  /** The chosen card, read before the step, once Roving Terror stands at its
    * site. */
  private def replacedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] =
    PowerSourceRef.of(source).map(card => (for
      ref <- NoteSupport.answer(states, decisionId)
      (before, _) <- states.previous
      chosen <- candidates(before).find(c => cards.refOf(c.card) == ref)
      if SiteRulers.siteOf(states.now, RovingTerrorCard.id)
        .contains(chosen.site)
    yield replaced(card, NoteArg.Card(chosen.card.id),
      NoteArg.Site(chosen.site))).getOrElse(nothing(card)))

object RovingTerror:
  val id: PowerId = RovingTerrorCard.power.id
  val decisionId: String = "power.roving-terror.card"
  /** "Replaced {card} at {site}." */
  val replaced: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0), NotePart.Text(" at "),
    NotePart.Arg(1), NotePart.Text(".")))
  /** "No card could be replaced." */
  val nothing: NoteKey = NoteKey("used.none", Vector(
    NotePart.Text("No card could be replaced.")))

  def forCatalog(catalog: ExecutableCatalog): RovingTerror =
    new RovingTerror(catalog)
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala`:

- Append `++ Vector(RovingTerror.forCatalog(catalog))` after `Vector(GreatFeast.forCatalog(catalog))`.
- In the doc comment, change "The phase powers of catalog batch 3's slice 3c, actions on sites, banks and banners," to "The phase powers of catalog batch 3's slice 3c and catalog batch 4's Roving Terror, actions on sites, banks and banners,".

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `RovingTerrorCard` to the `oathdigital.gameplay.powers.action.{...}` import, in alphabetical order (after `ResettleCard`).

- [ ] **Step 5: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.RovingTerrorSuite"`
Expected: PASS, 5 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/RovingTerror.scala \
  src/main/scala/oathdigital/gameplay/powers/action/WorldActionPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/action/RovingTerrorSuite.scala
git commit -m "feat(action): Roving Terror replaces a card at another site"
```

---

### Task 5: Moving Market

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/MovingMarket.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala` (retire the stub)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `MovingMarketCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import `MovingMarketCard` from `gameplay.powers.economy`)
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/MovingMarketSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala` (`modifiers`)
- Modify: `frontend/src/test/scala/oathdigital/frontend/BoardDecisionPanelSuite.scala`

**Interfaces:**
- Consumes: `SiteRoom.emptySlots` (Task 2), `SelectedModifier`, `TradeProcedure.decisionId`, `SiteRulers.siteOf`.
- Produces: `object MovingMarketCard`, `final case class MovingMarket` and its companion: `id`, `decisionId = "trade.moving-market.move"`, `skip: DecisionOptionRef.Button`, `moved`, `movedOnly`, `forCatalog(catalog: ExecutableCatalog): MovingMarket`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/MovingMarketSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class MovingMarketSuite extends munit.FunSuite:
  private val market = CatalogNames.denizen("Moving Market")
  private val power = MovingMarket.forCatalog(catalog)
  private val modifiers = Vector(MovingMarket.id)
  /** p1's pawn site, and two other sites in play, each with room for one
    * card. */
  private val home = CatalogNames.site("Ancient City")
  private val peaks = CatalogNames.site("Broken Peaks")
  private val giant = CatalogNames.site("Buried Giant")
  /** A plain Beast denizen, filling Broken Peaks. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen to Trade with instead. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  /** p1 at Ancient City beside the token-free Moving Market, with the
    * start's 1 favor and 1 faceup secret, and the Nomad bank holding `nomad`
    * favor. Errand Boy fills Broken Peaks; Buried Giant is empty. */
  private def staged(nomad: Int = 3): Table = Table.start
    .pawn(p1, at = home).denizen(market, at = home)
    .denizen(errandBoy, at = peaks).bankFavor(Suit.Nomad, nomad)

  /** p1 Trades for favor with Moving Market, having selected it, and is
    * asked where to move it. */
  private def tradedWithMarket(ready: ReadyGame): OathTransition =
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1,
      modifiers, Vector(DecisionOptionRef.Button("favor"))))
    val asked = accepted(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(market))))
    asked.copy(events = started.events ++ asked.events)

  /** `asked`, answered with `ref`; the events run from the Trade's start. */
  private def answered(asked: OathTransition, ref: DecisionOptionRef)
      : OathTransition =
    val done = accepted(rules.resolveWalker(asked.state, p1,
      MovingMarket.decisionId, ChooseOneAnswer(ref)))
    done.copy(events = asked.events ++ done.events)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("after trading with it, one question offers every empty slot at the " +
      "other sites, then Skip"):
    val asked = tradedWithMarket(staged().ready)
    assert(parked.parkedDecision(asked.state)
      .exists(_.decision == MovingMarket.decisionId),
      parked.parkedDecision(asked.state).toString)
    val options = PhasePowerHarness.offered(asked, p1).getOrElse(Vector.empty)
    assert(options.contains("site-slot" -> s"${giant.value}:0"), options)
    assert(!options.exists((_, id) =>
      id.startsWith(home.value) || id.startsWith(peaks.value)), options)
    assertEquals(options.lastOption, Some("button" -> "skip"))

  test("a chosen slot moves it there with its tokens, and it gains 1 favor " +
      "from the Nomad bank"):
    val ready = staged().ready
    val done = answered(tradedWithMarket(ready),
      DecisionOptionRef.SiteSlot(giant, 0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = Look(done.state)
    assertEquals(end.cardsAt(giant), Vector[CardId](market))
    assertEquals(end.cardsAt(home), Vector.empty[CardId])
    // The Trade placed p1's secret on it, and the secret travels with it.
    assertEquals(end.tokensOn(market), Tokens(0, 1))
    // 1 favor from the Trade and 1 from Moving Market, both Nomad.
    assertEquals(end.favor(p1), 1 + 2)
    assertEquals(end.bankFavor(Suit.Nomad), 3 - 2)
    assertEquals(said(done.events), Vector(NoteText.Said("moved",
      s"Moved it to ${giant.value} and gained 1 favor from the Nomad bank.",
      covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events),
      Outcomes.ready(done.state))
    assertWireRoundTrips(done.events)

  test("Skip leaves it where it stands, gains nothing and writes nothing"):
    val done = answered(tradedWithMarket(staged().ready), MovingMarket.skip)
    val end = Look(done.state)
    assertEquals(end.cardsAt(home), Vector[CardId](market))
    assertEquals(end.favor(p1), 1 + 1)
    assertEquals(said(done.events), Vector.empty)

  test("with the Nomad bank emptied by the Trade it still moves, and the " +
      "line names only the move"):
    val done = answered(tradedWithMarket(staged(nomad = 1).ready),
      DecisionOptionRef.SiteSlot(giant, 0))
    val end = Look(done.state)
    assertEquals(end.cardsAt(giant), Vector[CardId](market))
    assertEquals(end.favor(p1), 1 + 1)
    assertEquals(said(done.events), Vector(NoteText.Said("moved.only",
      s"Moved it to ${giant.value}.", covers = false)))

  test("trading with another card, or without selecting it, asks nothing"):
    val ready = staged().denizen(wrestlers, at = home).ready
    val elsewhere = EconomyActions.trade(rules, ready, p1, modifiers, "favor",
      DecisionOptionRef.Denizen(wrestlers))
    val unselected = EconomyActions.trade(rules, ready, p1, Vector.empty,
      "favor", DecisionOptionRef.Denizen(market))
    Vector(elsewhere, unselected).foreach { done =>
      assertEquals(parked.parkedDecision(Ready(done.result)), None)
      assert(Look(done.result).cardsAt(home).contains(market))
      assertEquals(said(done.journal.events), Vector.empty)
    }

  test("it is a Trade modifier, offered when the card is in reach"):
    val offered = (ready: ReadyGame, action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(ready, p1, action)).map(_.id)
    assert(offered(staged().ready, ActionRef.Trade).contains(MovingMarket.id))
    assert(!offered(staged().ready, ActionRef.Muster)
      .contains(MovingMarket.id))
    val away = Table.start.denizen(market, at = giant).ready
    assert(!offered(away, ActionRef.Trade).contains(MovingMarket.id))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.MovingMarketSuite"`
Expected: compile failure, `MovingMarket` is not found.

- [ ] **Step 3: Write Moving Market**

Remove `object MovingMarketCard` (its seven lines and the blank line after) from `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`. Then check whether `SiteOnly` is still used in that file; it should be, by Convoys and others.

Create `src/main/scala/oathdigital/gameplay/powers/economy/MovingMarket.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers,
  SelectedModifier, SiteRoom}
import oathdigital.model._

object MovingMarketCard extends Denizen(DenizenId("241"), "Moving Market", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.moving-market"),
    persistent = false, cost = Cost.free,
    text = "After trading with Moving Market, you may move this card to " +
      "any site with capacity. If you do, gain [favor] from the " +
      "[suit-nomad] bank.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Moving Market (card 241, site-only), a selected Trade modifier with no
  * cost: after trading with Moving Market, the player may move it to any
  * site with capacity, and if they do, gains 1 favor from the Nomad bank.
  *
  * One `Branch` is appended to the Trade's root, after the source, cost and
  * gain, as Knights Errant appends to a Muster's. A decision inside the
  * Economy `Branch` would be re-derived through `MusterSource.resolve`,
  * which refuses a source holding the Trade's tokens.
  *
  * The `Branch` asks only when Moving Market was the Trade's source and
  * another site in play has room. Its one question offers every empty slot
  * at the other sites in play, in map then slot order, then Skip. A chosen
  * slot moves the card to that site with its tokens, the Trade's included;
  * it takes the site's next slot, since a row has no gaps. The move is a
  * required batch. The gain is its own optional batch, so an empty bank
  * never rejects the move, and the line then names only the move. Skip
  * writes nothing.
  */
final case class MovingMarket private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  import MovingMarket._

  val cardId: DenizenId = MovingMarketCard.id
  def id: PowerId = MovingMarket.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def noteKeys: Vector[NoteKey] = Vector(moved, movedOnly)
  override def narratedDecisions: Set[String] = Set(decisionId)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ offer(ctx.activePlayer))))

  private def offer(actor: PlayerId): Operation = Branch((ready, pending) =>
    val slots =
      if tradedWith(pending) then openSlots(ready) else Vector.empty
    if slots.isEmpty then Vector.empty
    else Vector(
      Decide(decisionId, actor, DecisionQuery.ChooseOne(
        slots.map(DecisionOption.SiteSlot(_)) :+
          DecisionOption.Button(skip, "Skip"),
        heading = Some("Moving Market: move it to another site?"))),
      BuildOps((live, answered) => move(live, answered), required = true),
      BuildOps((live, answered) => gain(live, actor, answered)),
      Note(id, movedNote(_, actor))))

  private def tradedWith(pending: PendingTree): Boolean =
    PowerAnswers.one(pending, TradeProcedure.decisionId)
      .contains(DecisionOptionRef.Denizen(cardId))

  /** Every empty slot at the other sites in play, in map then slot order. */
  private def openSlots(ready: ReadyGame): Vector[DecisionOptionRef.SiteSlot] =
    SiteRulers.siteOf(ready, cardId).toVector.flatMap(here =>
      ready.game.current.map.inPlay.filter(_ != here).flatMap(site =>
        SiteRoom.emptySlots(catalog, ready, site)
          .map(DecisionOptionRef.SiteSlot(site, _))))

  private def move(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case Some(`skip`) => Right(Vector.empty)
      case Some(slot: DecisionOptionRef.SiteSlot) => (for
          here <- SiteRulers.siteOf(ready, cardId)
          if openSlots(ready).contains(slot)
        yield Vector[CoreOperation](Move(Piece.Card(cardId),
          PositionedLocation(Location.Site(here)),
          PositionedLocation(Location.Site(slot.site))))).toRight(
        OathViolation.InvalidEventOrder(
          s"${slot.wireId} is not an empty slot Moving Market can move to"))
      case Some(other) => Left(OathViolation.InvalidEventOrder(
        s"${other.wireId} is not a slot or Skip"))
      case None => Left(PowerAnswers.missing(decisionId))

  /** The favor, once the card stands at the chosen slot's site. */
  private def gain(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    Right(PowerAnswers.one(pending, decisionId).collect {
      case slot: DecisionOptionRef.SiteSlot
          if SiteRulers.siteOf(ready, cardId).contains(slot.site) =>
        Gain.Favor(actor, Suit.Nomad, Gained)
    }.toVector)

  /** Written when the card stands at the chosen slot's site. The favor is
    * read from the gain's step; with none, the line names only the move. */
  private def movedNote(states: NoteStates, actor: PlayerId)
      : Option[PowerNote] = for
    case slot: DecisionOptionRef.SiteSlot <-
      NoteSupport.answer(states, decisionId)
    if SiteRulers.siteOf(states.now, cardId).contains(slot.site)
  yield
    val card = PowerSourceRef.Card(cardId)
    val gained = states.previous.fold(0)(NoteSupport.favor(_, actor))
    if gained > 0 then moved(card, NoteArg.Site(slot.site),
      NoteArg.Amount(gained, NoteUnit.Favor), NoteArg.Bank(Suit.Nomad))
    else movedOnly(card, NoteArg.Site(slot.site))

object MovingMarket:
  val id: PowerId = MovingMarketCard.power.id
  /** Under the `trade.` prefix, as Knights Errant's is under `muster.`. */
  val decisionId: String = "trade.moving-market.move"
  val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("skip")
  /** The favor a move gains, best-effort. */
  val Gained: Int = 1
  /** "Moved it to {site} and gained {n favor} from {the Nomad bank}." */
  val moved: NoteKey = NoteKey("moved", Vector(NotePart.Text("Moved it to "),
    NotePart.Arg(0), NotePart.Text(" and gained "), NotePart.Arg(1),
    NotePart.Text(" from "), NotePart.Arg(2), NotePart.Text(".")))
  /** "Moved it to {site}." */
  val movedOnly: NoteKey = NoteKey("moved.only", Vector(
    NotePart.Text("Moved it to "), NotePart.Arg(0), NotePart.Text(".")))

  def forCatalog(catalog: ExecutableCatalog): MovingMarket =
    new MovingMarket(catalog)
```

`MovingMarket` defines `def id` itself and imports its companion, as Great Herd does, so its body may name `id` bare. The three `PaidAction`s above write `this.id` instead, because there `id` is inherited.

- [ ] **Step 4: Register it, and retire its stub**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`:

- Add `MovingMarket` to the `oathdigital.gameplay.powers.economy.{...}` import, in alphabetical order.
- Append `++ Vector(MovingMarket.forCatalog(catalog))` after `Vector(Downtrodden.forCatalog(catalog))`.

In `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala`, delete the line `object MovingMarket extends ReviewedPower("denizen.moving-market", None, economy)`, and delete `MovingMarket, ` from the `powers` vector.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `MovingMarketCard` to the `oathdigital.gameplay.powers.economy.{...}` import, in alphabetical order (after `KnightsErrantCard`).

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, add `"denizen.moving-market"` to the end of `modifiers`.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.powers.PowerImplementationStatusSuite"`
Expected: PASS, 6 tests in `MovingMarketSuite`, and every other economy suite unchanged.

- [ ] **Step 6: Write the frontend test**

In `frontend/src/test/scala/oathdigital/frontend/BoardDecisionPanelSuite.scala`, add after the test "a Decide button submits at once, under its own rule":

```scala
  test("slots at several sites stay on the table, and a Skip button " +
      "submits at once"):
    val ui = new RecordingControls()
    def slot(site: String, at: Int) = DecisionOptionState("site-slot",
      s"$site:$at", s"Empty slot at Site $site")
    val move = DecisionQueryState.ChooseOne(Vector(slot("hills", 0),
      slot("marsh", 1), DecisionOptionState("button", "skip", "Skip")),
      Some("Moving Market: move it to another site?"))
    val panel = pane(ui, move, decision.copy(
      decisionId = "trade.moving-market.move", query = Some(move)))
    assertEquals(all(panel, ".board-decision-loose-option"), Vector.empty)
    val skip = as[dom.html.Button](one(panel, ".board-decision-decide"))
    assertEquals(skip.textContent, "Skip")
    skip.click()
    assertEquals(ui.submitted, Vector(Intent.ResolveWalker(
      "trade.moving-market.move",
      DecisionAnswerWire.ChooseOneWire("button", "skip"))))
```

The suite's sites `hills` and `marsh` have capacity 3, so both slots have a home on the table and neither is drawn as a loose option.

- [ ] **Step 7: Run it**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.BoardDecisionPanelSuite"`
Expected: PASS. No production frontend change is needed, which settles ruling 9's first item. If the test fails because a slot is drawn as a loose option or Skip is missing, stop and report: the spec says to ask the product owner.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/MovingMarket.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/MovingMarketSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  frontend/src/test/scala/oathdigital/frontend/BoardDecisionPanelSuite.scala
git commit -m "feat(economy): Moving Market moves after a Trade and gains a Nomad favor"
```

---

### Task 6: Gates and docs

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`

- [ ] **Step 1: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: PASS. The server count is the baseline plus 30, and the frontend count the baseline plus 1.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

If slice 1b reached `main` while you worked, merge `main` now, resolve as Global Constraints say, and run both gates again.

- [ ] **Step 2: Update the roadmap**

In `docs/ROADMAP.md`, under "## Now", the sentence ends "slice\n1a is done." or, once slice 1b is recorded, "slices\n1a and 1b are done.".

- Replace the first form with "slices\n1a and 2 are done.", or the second with "slices\n1a, 1b and 2 are done.".
- Delete the bullet "- [ ] 2. Restriction icons and card moves: the restriction-icon rule, Roving\n  Terror, Homesteaders, Resettle, Moving Market." under "## Next".

- [ ] **Step 3: Record the audit in the spec**

In `docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md`, section "The restriction-icon rule", replace the paragraph:

```markdown
Slice 2 audits every implemented power that moves or swaps a card between
advisers and sites, and records each behavior change in the slice's plan and
in this section before the slice merges.
```

with:

```markdown
The rule judges where and how a card lands: a move's `resultingOrientation`,
or else the orientation the card shows where it lies. It does not judge a
card leaving a hand, since `CardPlay` checks the icons for a play, nor a move
inside one play area, which only turns the card over.

Slice 2's audit found one implemented power the rule would have refused:
Horned Mask took a site card into the play area faceup and then flipped it
facedown, so a site-only denizen was refused between the two. It now lands
the card facedown in one `Move`, and its behavior is unchanged. Every other
implemented card move passes unchanged:

- Dream Thief, Enchantress and Twin Brother swap advisers between play areas.
- Great Herd swaps cards between sites.
- The rest move relics or Visions, which print no restriction icons, or move
  cards to and from piles and hands.
```

- [ ] **Step 4: Make the spec's slice 2 log table exact**

In the same spec, section "Slice 2: card moves" under "Log lines", ruling 9's generic-line check settles each "verify":

- In Roving Terror's first row, change the Covers cell "the Discard line (verify)" to "the Discarded line".
- In Homesteaders' and Resettle's first rows, empty the Covers cell "the Moved line (verify)": no generic line exists for those moves.
- In Moving Market's `moved` row, empty the Covers cell "the Moved and Gain lines (verify)": there is no card-move line, and a Trade writes no Gain line for the actor.
- Add this row after Moving Market's `moved` row:

```markdown
| Moving Market, key `moved.only`, the Nomad bank was empty | Moving Market: Moved it to {site}. | |
```

- [ ] **Step 5: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-05-catalog-batch-4-design.md
git commit -m "docs: record catalog batch 4 slice 2"
```
