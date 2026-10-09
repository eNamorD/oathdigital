# Catalog Batch 5, Slice 1 (Muster and Trade Sources) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let powers change where a Muster or Trade may draw, and implement the five cards that do: Golem Legions (199), Map Library (76), Skilled Merchants (238), Old Songs (229) and Pressgangs (6).

**Architecture:**

- **The source decision becomes the gate.**
  - `MusterSource.atSite` offers every card at the pawn site.
  - A card that holds favor or secrets is shown `DecisionOption.Unavailable` with the reason `MusterSource.Occupied`, "Already holds favor or secrets".
  - The walker accepts only an available option (`DecisionQueries.accepts`), so nothing else needs to check.
- **`MusterSource.resolve` becomes a lookup.**
  - It finds the answered card at any site in play or among the actor's advisers.
  - It no longer checks the pawn site or the tokens.
  - A Muster or Trade resumed after its cost, whose source now holds tokens, therefore still finds its source.
- **Each card is a `SelectedModifier`** (`persistent = false`) in `gameplay/powers/economy/`.
  - They act through Transforms on the source decision's window, `MusterSourceSelection` or `TradeSourceSelection`, which folds `Vector(decide)`.
  - **Wideners** fold at priority 0 and add options: Golem Legions adds itself, and Map Library and Skilled Merchants add the cards in the pawn's region.
  - **Exemptions** fold at priority 1 and turn `Occupied` options available: Pressgangs every one, Old Songs those with exactly one secret and no favor.
  - An exemption also marks the action's own `PayCost` onto the answered source `intoOccupied`, because `PayCostRules` refuses a payment onto an occupied card.
- **One shared helper, `EconomySources`,** in `gameplay/powers/economy/`, holds the widening, the exemption, the `intoOccupied` mark and the region's cards.
- **Registration.**
  - The five powers join `ActionModifiers`.
  - Their reviewed stubs in `MusterPowers` are retired.
  - Each card object moves out of `catalog/holding/` into its power's file.
- **The approved engine change** is the source query and `resolve` (spec, "The bar"). There is no new operation, walker window, query kind, option kind, `NoteArg` kind, protocol change or frontend change.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. The frontend gets no change.

**Spec:** Read both documents before starting:

- `docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md`: "The bar", "Slice 1: Muster and Trade sources", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-08-catalog-batch-5-rulings.md`: "Rules that apply to the whole batch" and "Slice 1: Muster and Trade sources".

The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- **Build rules.** `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails the build.
  - Each file below lists the imports it needs.
  - When a card move removes the last use of an import in a holding file (`SiteOnly` or `AdviserOnly`, say), remove the import. The compiler names it.
  - A refutable pattern in a `val` also fails, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
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
- **The token-free rule** (rulings, whole batch): favor and secrets may not be placed on a card that already holds favor or secrets, unless a card explicitly allows it or a battle plan does it. The Rest cleanup returns them, so a used card is usable again after the next Rest.
- **Edifices are denizens:** a power that names denizens or cards as Muster or Trade sources includes edifices.
- **The reason text, exactly:** "Already holds favor or secrets".
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end. This plan adds 25 server tests and no frontend test.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one says what it costs if it is wrong.

1. **The exemption marks the payment onto the answered source, whatever it holds.**
   - The spec says an exemption marks the `PayCost` "when the chosen source holds tokens".
   - The plan marks the actor's `PayCost` placed onto the answered source card whenever the exemption is selected. It reads the answer from `PowerCtx.answered`.
   - The source decision is the gate: an occupied card is answerable only when an exemption made it available. A mark onto a token-free card changes nothing.
   - This keeps the fold a function of the answers alone, as the walker's re-fold on every command requires.
   - Cost if wrong: none found. Every other `PayCost` keeps the check.
2. **No slice 1 power writes a line.**
   - Power log lines design, ruling 1: a chosen modifier stays named on its action's start line.
   - The Muster and Trade lines already name the source wherever it stands ("Mustered 2 warbands with Golem Legions").
   - Task 2 pins that line for an adviser source.
3. **Map Library's region includes the pawn's own site.** The pawn's site cards are offered already, so the widening skips them.
4. **Map Library is selectable only while the pawn stands at its site.** Ruling the site is not enough. `SelectedModifier.selectable` is overridden.
5. **A widened card that holds tokens arrives unavailable,** with the same reason. An exemption folds after it, so Pressgangs makes an occupied Golem Legions available.
6. **The resume seam is tested with a stub.** No slice 1 power asks a question after the cost. `MusterProcedureSuite` adds a stub that asks one at the gain window, so a walk parks after the cost has put the favor on the source.
7. **The stub test powers are renamed.**
   - `EconomyFixture.AddAdviserSource` becomes `AddSource`, since it may now add any card.
   - `EconomyProjectionSuite`'s "an option the preview drops is not shown" adds a card that is not in play, which `resolve` still refuses.
8. **Registration pins.** Each id joins `PowerKindsCatalogSuite.modifiers` (`persistent: false`, so `PlayerSelected`). Each suite runs through the production catalogs (`PowerRules.withWalkerPowers`), so an unregistered power fails its suite.

## File Structure

Production, under `src/main/scala/oathdigital/` unless stated:

| File | Change | Responsibility | Task |
|---|---|---|---|
| `gameplay/actions/economy/MusterSource.scala` | modify | the source query, `offered`, `Occupied`, `resolve` as a lookup, `cardOf` | 1 |
| `gameplay/actions/economy/EconomyTree.scala` | modify | reads the new `atSite` | 1 |
| `gameplay/operations/PayCostRules.scala` | modify | doc comment | 1 |
| `gameplay/powers/economy/MovingMarket.scala` | modify | doc comment | 1 |
| `gameplay/powers/economy/EconomySources.scala` | create | widening, exemption, `intoOccupied`, the region's cards | 2, 3, 4 |
| `gameplay/powers/economy/GolemLegions.scala` | create | Golem Legions' card and Muster modifier | 2 |
| `gameplay/powers/economy/MapLibrary.scala` | create | Map Library's card and Trade modifier | 3 |
| `gameplay/powers/economy/SkilledMerchants.scala` | create | Skilled Merchants' card and Trade modifier | 3 |
| `gameplay/powers/economy/Pressgangs.scala` | create | Pressgangs' card and Muster modifier | 4 |
| `gameplay/powers/economy/OldSongs.scala` | create | Old Songs' card and Trade modifier | 4 |
| `gameplay/powers/ActionModifiers.scala` | modify | registers each power | 2, 3, 4 |
| `gameplay/powers/MusterPowers.scala` | modify | retires each reviewed stub | 2, 3, 4 |
| `catalog/holding/HoldingArcaneDenizens.scala` | modify | Golem Legions and Map Library leave | 2, 3 |
| `catalog/holding/HoldingHearthDenizens.scala` | modify | Skilled Merchants and Old Songs leave | 3, 4 |
| `catalog/holding/HoldingOrderDenizens.scala` | modify | Pressgangs leaves | 4 |
| `gameplay/cards/NewFoundations.scala` | modify | imports each moved card from the economy package | 2, 3, 4 |

Tests, under `src/test/scala/oathdigital/` unless stated:

| File | Change | Tests added | Task |
|---|---|---|---|
| `gameplay/EconomyFixture.scala` | modify | 0 (a renamed stub and a new one) | 1 |
| `gameplay/MusterProcedureSuite.scala` | modify | 2 (3 rewritten) | 1 |
| `application/EconomyProjectionSuite.scala` | modify | 1 (1 rewritten) | 1 |
| `gameplay/powers/economy/GolemLegionsSuite.scala` | create | 5 | 2 |
| `application/gamelog/LogScripts.scala` | modify | 0 (a script) | 2 |
| `application/gamelog/GameLogActionLineSuite.scala` | modify | 1 | 2 |
| `gameplay/powers/economy/MapLibrarySuite.scala` | create | 4 | 3 |
| `gameplay/powers/economy/SkilledMerchantsSuite.scala` | create | 3 | 3 |
| `gameplay/powers/economy/PressgangsSuite.scala` | create | 5 | 4 |
| `gameplay/powers/economy/OldSongsSuite.scala` | create | 4 | 4 |
| `gameplay/PowerKindsCatalogSuite.scala` | modify | 0 (list entries) | 2, 3, 4 |

Docs: `docs/ROADMAP.md` and the batch 5 design spec, in Task 5.

---

### Task 1: The source decision is the gate, and `resolve` is a lookup

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/MusterSource.scala` (whole file)
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala` (`tree`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/PayCostRules.scala` (doc comment)
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/MovingMarket.scala` (class doc comment)
- Modify: `src/test/scala/oathdigital/gameplay/EconomyFixture.scala`
- Modify: `src/test/scala/oathdigital/gameplay/MusterProcedureSuite.scala`
- Modify: `src/test/scala/oathdigital/application/EconomyProjectionSuite.scala`

**Interfaces:**
- Consumes: `DecisionOption.Unavailable`, `OptionReason.Written`, `RuleSourceRef.{SiteCard, Edifice, Adviser}`.
- Produces, in `oathdigital.gameplay.actions.economy`:
  - `final case class MusterSource(card: CardId, suit: Suit, origin: RuleSourceRef)`. The `option` field is gone.
  - `MusterSource.Occupied: OptionReason`, the written reason "Already holds favor or secrets".
  - `MusterSource.atSite(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId): Vector[DecisionOption]`.
  - `MusterSource.offered(card: SiteDenizenState): DecisionOption`: the card's option, `Unavailable(_, Occupied)` while it holds tokens.
  - `MusterSource.resolve(...)`: same signature, now a lookup.
  - `MusterSource.cardOf(state: ReadyGame, actor: PlayerId, ref: DecisionOptionRef): Option[SiteDenizenState]`.
  - Test stubs: `EconomyFixture.AddSource(id: PowerId, card: DenizenId)` and `EconomyFixture.AskAtGain(id: PowerId)`, with `AskAtGain.decisionId` and `AskAtGain.go`.

- [ ] **Step 1: Rename the stub and add the resume stub**

In `src/test/scala/oathdigital/gameplay/EconomyFixture.scala`, replace the `AddAdviserSource` case class with:

```scala
  /** Stub of ContributingPower for the Muster source: adds `card` to the
    * source decision, after the options it offers, as Golem Legions adds
    * itself. */
  final case class AddSource(id: PowerId, card: DenizenId)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.MusterSourceSelection -> Vector(Transform((_, operations) =>
        operations.map {
          case decide: Decide => decide.query match {
            case DecisionQuery.ChooseOne(options, heading) =>
              decide.copy(query = DecisionQuery.ChooseOne(options :+
                DecisionOption.Denizen(DecisionOptionRef.Denizen(card)),
                heading)): Operation
            case _ => decide
          }
          case other => other
        })))

  /** Stub of ContributingPower for the Muster gain window: asks a question
    * before the gain, so a walk parks after the cost has placed its favor
    * on the source. */
  final case class AskAtGain(id: PowerId) extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
        Decide(AskAtGain.decisionId, ctx.activePlayer,
          DecisionQuery.ChooseOne(Vector(
            DecisionOption.Button(AskAtGain.go, "Go")))) +: operations)))

  object AskAtGain:
    val decisionId: String = "test.ask-at-gain"
    val go: DecisionOptionRef.Button = DecisionOptionRef.Button("go")
```

- [ ] **Step 2: Rewrite the Muster tests the change affects, and add the new ones**

In `src/test/scala/oathdigital/gameplay/MusterProcedureSuite.scala`:

Change the imports to:

```scala
import oathdigital.gameplay.actions.economy.{MusterProcedure, MusterSource}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}
```

and the fixture import inside the class to:

```scala
  import EconomyFixture.{AddSource, AskAtGain, FreePayment}
```

Add, beside `ancientCity`:

```scala
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
```

Replace `parked` and `muster` so they take the powers the walk uses:

```scala
  private def parked(ready: ReadyGame,
      powers: WalkerPowers = WalkerPowers.empty): ParkedMuster =
    val tree = accepted(MusterProcedure.build(catalog, ready, p1))
    ParkedMuster(tree, Outcomes.parked(
      ProcedureWalker.advance(ready, tree, None, powers)).tree)

  private def muster(ready: ReadyGame, ref: DecisionOptionRef,
      powers: WalkerPowers = WalkerPowers.empty): ReadyGame =
    val walk = parked(ready, powers)
    val answer = Answered(MusterProcedure.decisionId,
      DecisionAnswer.ChooseOneAnswer(ref), p1)
    Outcomes.finished(ProcedureWalker.resolve(ready, walk.tree, walk.pending,
      answer, powers)).treeless
```

Replace the test "a card carrying tokens is not offered, so there is nothing to start" with:

```scala
  test("a card carrying tokens is shown unavailable with why, so there is " +
      "nothing to start"):
    val ready = atAlchemist.tokens(alchemist, secrets = 1).ready
    // The start itself is legal, so an empty preview is the absence of a
    // source and not a build failure swallowed by `startOptions`.
    accepted(MusterProcedure.build(catalog, ready, p1))
    val walk = parked(ready)
    val decide = present(ProcedureWalker.parkedDecide(ready, walk.tree,
      walk.pending, WalkerPowers.empty), "the source decision")
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(alchemist)),
        OptionReason.Written("Already holds favor or secrets"))),
      Some("Choose a card to Muster from")))
    assertEquals(MusterProcedure.startOptions(catalog, ready, p1,
      WalkerPowers.empty), Vector.empty)
    assertEquals(MusterProcedure.startOptions(catalog, atAlchemist.ready, p1,
      WalkerPowers.empty).map(_.option.ref),
      Vector(DecisionOptionRef.Denizen(alchemist)))
```

Replace the test "resolve names why a reference is not a source" with these two:

```scala
  test("resolve finds a source at any site in play or among the actor's " +
      "advisers, whatever it holds"):
    val ready = atAlchemist.tokens(alchemist, secrets = 1)
      .adviser(p1, magiciansCode).denizen(wrestlers, at = brokenPeaks).ready
    assertEquals(MusterSource.resolve(catalog, ready, p1,
      DecisionOptionRef.Denizen(alchemist)), Right(MusterSource(alchemist,
        Suit.Arcane, RuleSourceRef.SiteCard(ancientCity, alchemist))))
    assertEquals(MusterSource.resolve(catalog, ready, p1,
      DecisionOptionRef.Denizen(magiciansCode)), Right(MusterSource(
        magiciansCode, Suit.Arcane, RuleSourceRef.Adviser(p1, magiciansCode))))
    assertEquals(MusterSource.resolve(catalog, ready, p1,
      DecisionOptionRef.Denizen(wrestlers)), Right(MusterSource(wrestlers,
        Suit.Order, RuleSourceRef.SiteCard(brokenPeaks, wrestlers))))

  test("resolve names why a reference is not a source"):
    // Magician's Code is in no site and no adviser of p1's: first in the
    // deck, then another player's adviser.
    assertEquals(MusterSource.resolve(catalog, atAlchemist.ready, p1,
      DecisionOptionRef.Denizen(magiciansCode)),
      Left(EconomyCardUnavailable(ancientCity, magiciansCode)))
    assertEquals(MusterSource.resolve(catalog,
      atAlchemist.adviser(p2, magiciansCode).ready, p1,
      DecisionOptionRef.Denizen(magiciansCode)),
      Left(EconomyCardUnavailable(ancientCity, magiciansCode)))
    assertEquals(MusterSource.resolve(catalog, atAlchemist.ready, p1,
      DecisionOptionRef.Button("site")), Left(InvalidEventOrder(
        "button/site is not a card a Muster or Trade can draw on")))
```

Replace the last test, "a power that adds an option to the source decision has it hidden, because the acceptance rule leaves it no legal path", with:

```scala
  test("a power that adds the actor's adviser to the source decision has " +
      "it accepted, and the favor is placed on the adviser"):
    val ready = atAlchemist.adviser(p1, magiciansCode).ready
    val powers = WalkerPowers(Vector(AddSource(
      PowerId("test.add-source"), magiciansCode)))
    assertEquals(MusterProcedure.startOptions(catalog, ready, p1, powers)
      .map(_.option.ref), Vector(DecisionOptionRef.Denizen(alchemist),
        DecisionOptionRef.Denizen(magiciansCode)))
    val after = Look(muster(ready, DecisionOptionRef.Denizen(magiciansCode),
      powers))
    assertEquals(after.tokensOn(magiciansCode), Tokens(1, 0))
    assertEquals(after.tokensOn(alchemist), Tokens(0, 0))

  test("a Muster resumed after its cost, whose source now holds the favor, " +
      "still finds its source"):
    val rules = new OathRules(catalog, walkerPowerCatalog =
      WalkerPowers(Vector(AskAtGain(PowerId("test.ask-at-gain")))))
    val ready = atAlchemist.ready
    val started = accepted(rules.startWalker(Ready(ready), ActionRef.Muster,
      p1))
    val asked = accepted(rules.resolveWalker(started.state, p1,
      MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(alchemist))))
    assertEquals(Look(Outcomes.ready(asked.state)).tokensOn(alchemist),
      Tokens(1, 0))
    val done = accepted(rules.resolveWalker(asked.state, p1,
      AskAtGain.decisionId, ChooseOneAnswer(AskAtGain.go)))
    // 1, and p1 holds no Arcane adviser.
    assertEquals(Look(Outcomes.ready(done.state)).boardWarbands(p1), 3 + 1)
```

`OathRules` is in this suite's own package, `oathdigital.gameplay`, so it needs no import.

- [ ] **Step 3: Update the projection suite**

In `src/test/scala/oathdigital/application/EconomyProjectionSuite.scala`:

Change `import EconomyFixture.AddAdviserSource` to `import EconomyFixture.AddSource`, and add beside `magiciansCode`:

```scala
  /** A plain Order denizen, in the deck unless a test places it. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
```

Replace the body of "an option the preview drops is not shown" with:

```scala
    val board = atAlchemist.ready
    val started = rules.startWalker(Ready(board), ActionRef.Muster, p1)
      .getOrElse(fail("the action must start"))
    val parked = Outcomes.ready(started.state)
    val context = ScopedProjectionContext(parked, Some(p1))
    // The stub adds Wrestlers as a second Muster source. Wrestlers is in no
    // site and no player's advisers, so MusterSource.resolve refuses it, and
    // the preview keeps only the sources the walk accepts.
    val withPower = new WalkerDecisionProjector(catalog,
      new GamePresentationProjector(catalog), WalkerPowers(Vector(
        AddSource(PowerId("test.add-source"), wrestlers))))
    val options = withPower.project(context).flatMap(_.query)
      .map(_.offeredOptions)
      .getOrElse(fail("the decision must project"))
    assertEquals(options.map(_.id), Vector(alchemist.value))
```

Add after it:

```scala
  test("a parked Muster shows a card holding favor or secrets as " +
      "unavailable, with why"):
    val board = atAlchemist.denizen(wrestlers, at = "Ancient City")
      .tokens(wrestlers, favor = 1).ready
    val options = parkedOptions(board, ActionRef.Muster, Vector.empty)
    assertEquals(options.map(option =>
      (option.id, option.state, option.reason)), Vector(
        (alchemist.value, "available", None),
        (wrestlers.value, "unavailable",
          Some("Already holds favor or secrets"))))
```

- [ ] **Step 4: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.MusterProcedureSuite oathdigital.application.EconomyProjectionSuite"`

Expected: compile failure in `MusterProcedureSuite`: `MusterSource` still takes four arguments, its `option` among them. Once Step 5 removes the field, these tests would fail on the old behavior:
- "a card carrying tokens is shown unavailable": the query offers no option.
- Both `resolve` tests: `EconomyCardNotEmpty` or `EconomyCardUnavailable`.
- "a power that adds the actor's adviser": the preview drops Magician's Code.
- "a Muster resumed after its cost": the second answer fails with `EconomyCardNotEmpty`.
- "a parked Muster shows a card holding favor or secrets": Wrestlers is absent.

- [ ] **Step 5: Rewrite `MusterSource`**

Replace `src/main/scala/oathdigital/gameplay/actions/economy/MusterSource.scala` with:

```scala
package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** What a Muster or Trade draws on: the card its cost is placed on, the suit
  * that decides which advisers match and which favor bank a Trade draws from,
  * and where the card stands. Resolved from the answered reference on every
  * command and never persisted.
  */
final case class MusterSource(card: CardId, suit: Suit, origin: RuleSourceRef)

object MusterSource:

  /** Why a card that holds favor or secrets is shown at the source decision
    * but cannot be chosen: the token-free rule. A power that lets the actor
    * draw on such a card makes its option available again.
    */
  val Occupied: OptionReason =
    OptionReason.Written("Already holds favor or secrets")

  /** The sources the base rule offers: every card at the actor's pawn site,
    * in site order, one that holds favor or secrets shown unavailable. The
    * decision is the gate: the walker accepts only an available option.
    */
  def atSite(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Vector[DecisionOption] =
    for
      siteId <- pawnSite(state, actor).toVector
      site <- state.game.current.map.sites.get(siteId).toVector
      card <- site.denizens
      if catalog.suitOf(card.id).nonEmpty
    yield offered(card)

  /** `card` as an option of the source decision: available while it holds
    * no favor or secret, and unavailable for [[Occupied]] otherwise.
    */
  def offered(card: SiteDenizenState): DecisionOption =
    val option = card match
      case value: DenizenState =>
        DecisionOption.Denizen(DecisionOptionRef.Denizen(value.id))
      case value: EdificeState =>
        DecisionOption.Edifice(DecisionOptionRef.Edifice(value.id))
    if card.tokens.isEmpty then option
    else DecisionOption.Unavailable(option, Occupied)

  /** The source a reference names, wherever it stands: at a site in play, or
    * among the actor's advisers. It checks neither the pawn site nor the
    * tokens. The source decision offers only what a rule allows, and the
    * walker accepts only an available option, so a power that lets a Muster
    * or Trade draw on another card adds it to the decision and changes
    * nothing here. A Muster or Trade resumed after its cost finds its source
    * although the cost put tokens on it.
    */
  def resolve(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      ref: DecisionOptionRef): Either[OathViolation, MusterSource] = for
    id <- cardIdOf(ref).toRight(OathViolation.InvalidEventOrder(
      s"${ref.kind}/${ref.wireId} is not a card a Muster or Trade can draw on"))
    siteId <- pawnSite(state, actor).toRight(OathViolation.PawnSiteMissing(actor))
    found <- locate(state, actor, id)
      .toRight(OathViolation.EconomyCardUnavailable(siteId, id))
    suit <- catalog.suitOf(id).toRight(
      OathViolation.UnsupportedEconomyState(s"no catalog suit for $id"))
  yield MusterSource(id, suit, found.origin)

  /** The card a reference names, at a site in play or among the actor's
    * advisers, for a power that reads what it holds.
    */
  def cardOf(state: ReadyGame, actor: PlayerId, ref: DecisionOptionRef)
      : Option[SiteDenizenState] =
    cardIdOf(ref).flatMap(locate(state, actor, _)).map(_.card)

  /** The actor's faceup advisers of `suit`: how many warbands, favor or
    * secrets a Muster or Trade adds to its base yield.
    */
  def matching(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      suit: Suit): Int =
    state.game.current.players.find(_.player == actor).toVector
      .flatMap(_.advisers).count:
        case DenizenState(id, Orientation.FaceUp, _) =>
          catalog.suitOf(id).contains(suit)
        case _ => false

  /** A card and where it stands. */
  private final case class Located(card: SiteDenizenState,
      origin: RuleSourceRef)

  /** `id` at the first site in play that holds it, in map order, or else
    * among the actor's advisers. */
  private def locate(state: ReadyGame, actor: PlayerId, id: CardId)
      : Option[Located] =
    val current = state.game.current
    val atSites = current.map.inPlay.iterator.flatMap(siteId =>
      current.map.sites.get(siteId).iterator.flatMap(_.denizens).collect {
        case card if card.id == id => Located(card, originAt(siteId, card))
      })
    val held = current.players.find(_.player == actor).iterator
      .flatMap(_.advisers).collect {
        case card: DenizenState if card.id == id =>
          Located(card, RuleSourceRef.Adviser(actor, card.id))
      }
    (atSites ++ held).nextOption()

  private def originAt(siteId: SiteId, card: SiteDenizenState)
      : RuleSourceRef = card match
    case value: DenizenState => RuleSourceRef.SiteCard(siteId, value.id)
    case value: EdificeState => RuleSourceRef.Edifice(siteId, value.id)

  private def cardIdOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None

  private def pawnSite(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
```

- [ ] **Step 6: Read the new `atSite` in the tree**

In `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala`, in `tree`, replace

```scala
      MusterSource.atSite(catalog, state, actor).map(_.option),
```

with

```scala
      MusterSource.atSite(catalog, state, actor),
```

- [ ] **Step 7: Update the two doc comments**

In `src/main/scala/oathdigital/gameplay/operations/PayCostRules.scala`, replace the paragraph

```scala
  * Muster and Trade check the same fact earlier (`MusterSource`, with its own
  * rejection) so their projection can drop the option. This is the enforcement
  * point for every other `PayCost`.
```

with

```scala
  * Muster and Trade show a card that holds tokens as unavailable at their
  * source decision (`MusterSource.Occupied`), so the decision is their gate.
  * A power that makes such a card a source again marks the action's payment
  * `intoOccupied`. This is the enforcement point for every `PayCost`.
```

In `src/main/scala/oathdigital/gameplay/powers/economy/MovingMarket.scala`, replace

```scala
  * One `Branch` is appended to the Trade's root, after the source, cost and
  * gain, as Knights Errant appends to a Muster's. A decision inside the
  * Economy `Branch` would be re-derived through `MusterSource.resolve`,
  * which refuses a source holding the Trade's tokens.
```

with

```scala
  * One `Branch` is appended to the Trade's root, after the source, cost and
  * gain, as Knights Errant appends to a Muster's. It went there because
  * `MusterSource.resolve` once refused a source holding the Trade's tokens
  * on resume. `resolve` is now a lookup, and the `Branch` stays at the root.
```

- [ ] **Step 8: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.MusterProcedureSuite oathdigital.application.EconomyProjectionSuite"`
Expected: PASS.

- [ ] **Step 9: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS. A suite that asserted a token-bearing card is absent from a Muster or Trade decision now sees it as unavailable. Fix such an assertion to read `option.available` or the state, and name each one in the commit message body. If a failure is anything else, stop and report it.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/economy/MusterSource.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala \
  src/main/scala/oathdigital/gameplay/operations/PayCostRules.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/MovingMarket.scala \
  src/test/scala/oathdigital/gameplay/EconomyFixture.scala \
  src/test/scala/oathdigital/gameplay/MusterProcedureSuite.scala \
  src/test/scala/oathdigital/application/EconomyProjectionSuite.scala
git commit -m "feat(economy): the source decision gates Muster and Trade, and resolve finds a source anywhere"
```

---

### Task 2: Golem Legions

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/GolemLegions.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `GolemLegionsCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (economy import)
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/GolemLegionsSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`

**Interfaces:**
- Consumes: `MusterSource.offered` (Task 1), `SelectedModifier`, `Transform`.
- Produces:
  - `private[economy] object EconomySources` with `widened(operations: Vector[Operation], cards: Vector[SiteDenizenState]): Vector[Operation]`, and a private `withOptions` that Tasks 3 and 4 reuse.
  - `GolemLegionsCard`, `final case class GolemLegions`, `GolemLegions.id`, `GolemLegions.forCatalog(catalog)`, in `oathdigital.gameplay.powers.economy`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/economy/GolemLegionsSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PhasePowerHarness, PowerRules, Refusals, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class GolemLegionsSuite extends munit.FunSuite:
  private val golem = CatalogNames.denizen("Golem Legions")
  private val selected = Vector(GolemLegions.id)
  private val home = CatalogNames.site("Ancient City")
  /** An Arcane adviser besides Golem Legions. */
  private val magiciansCode = CatalogNames.denizen("Magician's Code")
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val vowOfBeastkin = CatalogNames.denizen("Vow of Beastkin")
  private val rules = PowerRules.withWalkerPowers
  private val projector = new GameProjector(catalog)

  /** p1 at Ancient City, where no card stands, holding Golem Legions faceup,
    * with the start's 1 favor, 7 Supply and 3 warbands on the board. */
  private def holdingGolem: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, golem)

  private def controls(ready: ReadyGame): Vector[String] =
    projector.project("golem", LoadedGame(Ready(ready), 4), p1).legalControls

  private def musterStarted(ready: ReadyGame): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Muster, p1, selected))

  private def musteredOnGolem(ready: ReadyGame): EconomyActions.Completed =
    EconomyActions.muster(rules, ready, p1, selected,
      DecisionOptionRef.Denizen(golem))

  test("selected, it offers Golem Legions as a Muster source, so a Muster " +
      "with no card at the pawn site can start"):
    val ready = holdingGolem.ready
    assertEquals(rules.startWalker(Ready(ready), ActionRef.Muster, p1)
      .left.toOption, Some(OathViolation.NoPlayableOption(ActionRef.Muster.key)))
    assert(controls(ready).contains("beginMuster"), controls(ready))
    assertEquals(PhasePowerHarness.offered(musterStarted(ready), p1),
      Some(Vector("denizen" -> golem.value)))

  test("mustering on it places the favor on Golem Legions, which is its " +
      "own matching adviser"):
    val ready = holdingGolem.ready
    val done = musteredOnGolem(ready)
    val after = Look(done.result)
    assertEquals(after.tokensOn(golem), Tokens(1, 0))
    assertEquals(after.favor(p1), Look(ready).favor(p1) - 1)
    // 1, plus 1 for Golem Legions, a faceup Arcane adviser.
    assertEquals(after.boardWarbands(p1), Look(ready).boardWarbands(p1) + 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("it counts itself once, beside the actor's other Arcane advisers"):
    val ready = holdingGolem.adviser(p1, magiciansCode).ready
    // 1, plus Golem Legions and Magician's Code.
    assertEquals(Look(musteredOnGolem(ready).result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 3)

  test("while it holds favor it is shown unavailable and refused, so it " +
      "musters once between Rests"):
    val ready = holdingGolem.denizen(errandBoy, at = home)
      .tokens(golem, favor = 1).ready
    val started = musterStarted(ready)
    assertEquals(PhasePowerHarness.queryOf(started, p1).map(_.offeredOptions
      .map(option => (option.id, option.state, option.reason))), Some(Vector(
        (errandBoy.value, "available", None),
        (golem.value, "unavailable", Some("Already holds favor or secrets")))))
    assertEquals(rules.resolveWalker(started.state, p1,
      MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(golem))).left.toOption,
      Some(Refusals.notOffered(MusterProcedure.decisionId)))

  test("Vow of Beastkin still narrows the widened sources"):
    // Golem Legions is Arcane and matches itself. Errand Boy is Beast and
    // matches the Vow. Wrestlers is Order and matches nothing, so the Vow
    // hides it.
    val ready = holdingGolem.adviser(p1, vowOfBeastkin)
      .denizen(errandBoy, at = home).denizen(wrestlers, at = home).ready
    assertEquals(PhasePowerHarness.offered(musterStarted(ready), p1),
      Some(Vector("denizen" -> errandBoy.value, "denizen" -> golem.value)))
```

- [ ] **Step 2: Add the Game Log script and its test**

In `src/test/scala/oathdigital/application/gamelog/LogScripts.scala`, add the import

```scala
import oathdigital.gameplay.powers.economy.GolemLegions
```

and add after `initiatedMuster`:

```scala
  /** p1 musters on Golem Legions, their adviser, with no card at their site.
    * Golem Legions is selected for the Muster. */
  def golemMuster(using munit.Location): Script =
    val (service, act) = atTable("golem-muster", tableWithOnePawnPerSite
      .adviser(p1, "Golem Legions").banditsRefilled)
    act.after(GameCommand.StartWalker(ActionRef.Muster,
      StartPayload(p1, Vector(GolemLegions.id))))
    Script("golem-muster", service, p1)
```

In `src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala`, add after "Muster under Initiation Rite":

```scala
  test("Muster on Golem Legions: the line names the adviser as the source"):
    val mine = lines(golemMuster, None)
    // 1, plus 1 for Golem Legions, a faceup Arcane adviser.
    assert(mine.contains("Mustered 2 warbands with Golem Legions"), mine)
```

- [ ] **Step 3: Pin the registration**

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, add `"denizen.golem-legions"` to the end of `modifiers`, after `"denizen.special-envoy"`.

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.GolemLegionsSuite oathdigital.application.gamelog.GameLogActionLineSuite"`
Expected: compile failure, `GolemLegions` is not a member of `oathdigital.gameplay.powers.economy`.

- [ ] **Step 5: Write the shared helper**

Create `src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.MusterSource
import oathdigital.model._

/** What the Muster and Trade source powers share (catalog batch 5 design,
  * "Slice 1: Muster and Trade sources"). Each rewrites the source decision,
  * which its window folds as `Vector(decide)`.
  */
private[economy] object EconomySources:

  /** `operations` with `cards` added to each source decision, after the
    * options it offers. A card the decision offers already is not added
    * again, and one that holds favor or secrets arrives unavailable, as it
    * would at the pawn site.
    */
  def widened(operations: Vector[Operation],
      cards: Vector[SiteDenizenState]): Vector[Operation] =
    withOptions(operations) { options =>
      val offered = options.map(_.ref).toSet
      options ++ cards.map(MusterSource.offered)
        .filterNot(option => offered(option.ref))
    }

  /** `operations` with `change` applied to the options of each choose-one
    * decision. */
  private def withOptions(operations: Vector[Operation])(
      change: Vector[DecisionOption] => Vector[DecisionOption])
      : Vector[Operation] = operations.map {
    case decide: Decide => decide.query match {
      case one: DecisionQuery.ChooseOne =>
        decide.copy(query = one.copy(options = change(one.options)))
      case _ => decide
    }
    case other => other
  }
```

- [ ] **Step 6: Write the power and move its card**

Create `src/main/scala/oathdigital/gameplay/powers/economy/GolemLegions.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object GolemLegionsCard extends Denizen(DenizenId("199"), "Golem Legions", Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.golem-legions"),
    persistent = false, cost = Cost.free,
    text = "You may muster by placing [favor] on Golem Legions. _It " +
      "counts itself as a matching adviser._")
  val powers: Vector[PrintedPower] = Vector(power)

/** Golem Legions (card 199, adviser-only), a selected Muster modifier with
  * no cost: its holder may Muster on Golem Legions itself, which takes the
  * Muster's favor as a card at a site would.
  *
  * It adds Golem Legions to the source decision, after the cards at the pawn
  * site ([[EconomySources.widened]]). The token-free rule still applies: while
  * Golem Legions holds favor or secrets it is shown unavailable, so it is
  * mustered on once between Rests unless Pressgangs is also selected.
  *
  * "It counts itself as a matching adviser" is a reminder. A selectable
  * Golem Legions is a faceup Arcane adviser, which `MusterSource.matching`
  * already counts. It writes no line: the start line names it, and the
  * Muster's own line names it as the source.
  */
final case class GolemLegions private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = GolemLegionsCard.id
  def id: PowerId = GolemLegions.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterSourceSelection -> Vector(Transform((ctx, operations) =>
      EconomySources.widened(operations, held(ctx.state, ctx.activePlayer)))))

  /** Golem Legions among `actor`'s advisers. */
  private def held(ready: ReadyGame, actor: PlayerId)
      : Vector[SiteDenizenState] =
    ready.game.current.players.find(_.player == actor).toVector
      .flatMap(_.advisers).collect {
        case card: DenizenState if card.id == cardId => card
      }

object GolemLegions:
  val id: PowerId = GolemLegionsCard.power.id

  def forCatalog(catalog: ExecutableCatalog): GolemLegions =
    new GolemLegions(catalog)
```

Delete the `GolemLegionsCard` object (and its trailing blank line) from `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`. Remove an import the deletion leaves unused.

In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add `GolemLegionsCard` to the `oathdigital.gameplay.powers.economy` import, in alphabetical order:

```scala
import oathdigital.gameplay.powers.economy.{AnimalPlaymatesCard,
  AwaitedReturnCard, BirdsongCard, CupOfPlentyCard, CurfewCard,
  DefameCard, DowntroddenCard, GolemLegionsCard, InitiationRiteCard,
  KnightsErrantCard, MountedLibraryCard, MovingMarketCard, RowdyPubCard,
  TheOldOakCard, VillageIdiotCard, VowOfBeastkinCard, VowOfPovertyCard}
```

- [ ] **Step 7: Register the power and retire its stub**

In `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`, add `GolemLegions` to the economy import:

```scala
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, AwaitedReturn,
  Birdsong, CupOfPlenty, Curfew, Defame, Downtrodden, GolemLegions,
  InitiationRite, MountedLibrary, MovingMarket, RowdyPub, TheOldOak,
  VillageIdiot, VowOfBeastkin, VowOfPoverty}
```

and add `Vector(GolemLegions.forCatalog(catalog)) ++` after `Vector(Defame.forCatalog(catalog)) ++`.

In `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala`, delete the line `object GolemLegions extends ReviewedPower("denizen.golem-legions", None, economy)` and remove `GolemLegions` from `powers`.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.GolemLegionsSuite oathdigital.application.gamelog.GameLogActionLineSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/GolemLegions.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/GolemLegionsSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  src/test/scala/oathdigital/application/gamelog/LogScripts.scala \
  src/test/scala/oathdigital/application/gamelog/GameLogActionLineSuite.scala
git commit -m "feat(economy): Golem Legions musters on itself"
```

---

### Task 3: Map Library and Skilled Merchants

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala` (add `inPawnRegion`)
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/MapLibrary.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/SkilledMerchants.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `MapLibraryCard`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` (remove `SkilledMerchantsCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (economy import)
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/MapLibrarySuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/SkilledMerchantsSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `EconomySources.widened` (Task 2), `PowerAccess.pawnSite`, `SiteRulers.siteOf`.
- Produces:
  - `EconomySources.inPawnRegion(ready: ReadyGame, actor: PlayerId): Vector[SiteDenizenState]`.
  - `MapLibraryCard`, `MapLibrary`, `MapLibrary.id`, `MapLibrary.forCatalog`; `SkilledMerchantsCard`, `SkilledMerchants`, `SkilledMerchants.id`, `SkilledMerchants.forCatalog`.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/economy/MapLibrarySuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ModifierOffers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PhasePowerHarness, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class MapLibrarySuite extends munit.FunSuite:
  private val library = CatalogNames.denizen("Map Library")
  private val selected = Vector(MapLibrary.id)
  private val map = Table.start.unchecked.game.current.map
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** Another site in play in the pawn's region. */
  private val near = map.inPlay.find(site => site != home &&
    map.regionOf(site) == map.regionOf(home)).get
  /** A site in play in another region. */
  private val away = map.inPlay.find(site =>
    map.regionOf(site) != map.regionOf(home)).get
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val hallowedSpring = CatalogNames.edifice("Hallowed Spring")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City beside Map Library, with the start's 1 faceup secret
    * to pay a Trade for favor. Wrestlers and the intact Hallowed Spring stand
    * at the other site in the region, Errand Boy in another region, and the
    * Order bank holds 3 favor. */
  private def staged: Table = Table.start.pawn(p1, at = home)
    .denizen(library, at = home).denizen(wrestlers, at = near)
    .edifice(hallowedSpring, EdificeSide.Intact, at = near)
    .denizen(errandBoy, at = away).bankFavor(Suit.Order, 3)

  private def tradeStarted(ready: ReadyGame): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1, selected,
      Vector(DecisionOptionRef.Button("favor"))))

  private def tradeModifiers(ready: ReadyGame): Vector[PowerId] =
    accepted(ModifierOffers.offerable(WalkerPowerCatalog.default(catalog),
      ready, p1, ActionRef.Trade)).map(_.id)

  test("selected, a Trade offers every card at the sites in the pawn's " +
      "region, edifices too, and none elsewhere"):
    assertEquals(PhasePowerHarness.offered(tradeStarted(staged.ready), p1),
      Some(Vector("denizen" -> library.value, "denizen" -> wrestlers.value,
        "edifice" -> hallowedSpring.value)))

  test("trading with a card at another site places the secret there and " +
      "gains favor of its suit"):
    val ready = staged.ready
    val done = EconomyActions.trade(rules, ready, p1, selected, "favor",
      DecisionOptionRef.Denizen(wrestlers))
    val after = Look(done.result)
    assertEquals(after.tokensOn(wrestlers), Tokens(0, 1))
    // 1, and p1 holds no Order adviser.
    assertEquals(after.favor(p1), Look(ready).favor(p1) + 1)
    assertEquals(after.bankFavor(Suit.Order), 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("a card at another site that holds favor or secrets is shown " +
      "unavailable"):
    val started = tradeStarted(staged.tokens(wrestlers, favor = 1).ready)
    assertEquals(PhasePowerHarness.queryOf(started, p1).map(_.offeredOptions
      .map(option => (option.id, option.state))), Some(Vector(
        library.value -> "available", wrestlers.value -> "unavailable",
        hallowedSpring.value -> "available")))

  test("it is selectable only while the pawn stands at its site"):
    assert(tradeModifiers(staged.ready).contains(MapLibrary.id))
    // p1 rules Map Library's site with two warbands, but the pawn stands in
    // another region.
    val ruledOnly = staged.pawn(p1, at = away).warbandsAt(home, p1, 2).ready
    assert(!tradeModifiers(ruledOnly).contains(MapLibrary.id))
```

Create `src/test/scala/oathdigital/gameplay/powers/economy/SkilledMerchantsSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, EconomyActions, Look,
  PhasePowerHarness, PowerRules, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class SkilledMerchantsSuite extends munit.FunSuite:
  private val merchants = CatalogNames.denizen("Skilled Merchants")
  private val library = CatalogNames.denizen("Map Library")
  private val map = Table.start.unchecked.game.current.map
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** Another site in play in the pawn's region. */
  private val near = map.inPlay.find(site => site != home &&
    map.regionOf(site) == map.regionOf(home)).get
  /** A site in play in another region. */
  private val away = map.inPlay.find(site =>
    map.regionOf(site) != map.regionOf(home)).get
  /** A plain Arcane denizen. */
  private val alchemist = CatalogNames.denizen("Alchemist")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val hallowedSpring = CatalogNames.edifice("Hallowed Spring")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City beside Alchemist, holding Skilled Merchants faceup,
    * with the start's 1 faceup secret. Wrestlers and the intact Hallowed
    * Spring stand at the other site in the region, Errand Boy in another
    * region, and the Order bank holds 3 favor. */
  private def staged: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, merchants).denizen(alchemist, at = home)
    .denizen(wrestlers, at = near)
    .edifice(hallowedSpring, EdificeSide.Intact, at = near)
    .denizen(errandBoy, at = away).bankFavor(Suit.Order, 3)

  private def tradeStarted(ready: ReadyGame, modifiers: Vector[PowerId])
      : OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1, modifiers,
      Vector(DecisionOptionRef.Button("favor"))))

  test("selected, a Trade offers every card at the sites in the pawn's " +
      "region, edifices too, and none elsewhere"):
    assertEquals(PhasePowerHarness.offered(tradeStarted(staged.ready,
      Vector(SkilledMerchants.id)), p1), Some(Vector(
        "denizen" -> alchemist.value, "denizen" -> wrestlers.value,
        "edifice" -> hallowedSpring.value)))

  test("trading with a card at another site places the secret there"):
    val ready = staged.ready
    val done = EconomyActions.trade(rules, ready, p1,
      Vector(SkilledMerchants.id), "favor",
      DecisionOptionRef.Denizen(wrestlers))
    val after = Look(done.result)
    assertEquals(after.tokensOn(wrestlers), Tokens(0, 1))
    // 1: Skilled Merchants is Hearth, not Order.
    assertEquals(after.favor(p1), Look(ready).favor(p1) + 1)

  test("with Map Library, each card is offered once"):
    val ready = staged.denizen(library, at = home).ready
    assertEquals(PhasePowerHarness.offered(tradeStarted(ready,
      Vector(MapLibrary.id, SkilledMerchants.id)), p1), Some(Vector(
        "denizen" -> alchemist.value, "denizen" -> library.value,
        "denizen" -> wrestlers.value, "edifice" -> hallowedSpring.value)))
```

- [ ] **Step 2: Pin the registrations**

In `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`, add `"denizen.map-library"` and `"denizen.skilled-merchants"` to the end of `modifiers`.

- [ ] **Step 3: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.MapLibrarySuite oathdigital.gameplay.powers.economy.SkilledMerchantsSuite"`
Expected: compile failure, `MapLibrary` and `SkilledMerchants` are not members of `oathdigital.gameplay.powers.economy`.

- [ ] **Step 4: Add the region's cards to the helper**

In `src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala`, add the import

```scala
import oathdigital.gameplay.PowerAccess
```

and add after `widened`:

```scala
  /** Every card at the sites in play in the region of `actor`'s pawn, the
    * pawn's own site included, in map then site order. */
  def inPawnRegion(ready: ReadyGame, actor: PlayerId)
      : Vector[SiteDenizenState] =
    val map = ready.game.current.map
    val region = PowerAccess.pawnSite(ready, actor).flatMap(map.regionOf)
    map.inPlay.filter(site => region.exists(map.regionOf(site).contains))
      .flatMap(site => map.sites.get(site).toVector.flatMap(_.denizens))
```

- [ ] **Step 5: Write the two powers and move their cards**

Create `src/main/scala/oathdigital/gameplay/powers/economy/MapLibrary.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.{PowerAccess, SiteRulers}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object MapLibraryCard extends Denizen(DenizenId("76"), "Map Library", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.map-library"),
    persistent = false, cost = Cost.free,
    text = "While your pawn is at this site, you may trade with a card " +
      "at any site in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Map Library (card 76, site-only), a selected Trade modifier with no cost,
  * selectable only while its user's pawn stands at Map Library's site: the
  * Trade may draw on any card at any site in the pawn's region, the pawn's
  * own site and edifices included.
  *
  * It adds those cards to the source decision, after the pawn site's
  * ([[EconomySources.widened]]). The token-free rule still applies. Skilled
  * Merchants adds the same cards, so with both selected each is offered
  * once. It writes no line: the start line names it, and the Trade's own
  * line names the source.
  */
final case class MapLibrary private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = MapLibraryCard.id
  def id: PowerId = MapLibrary.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeSourceSelection -> Vector(Transform((ctx, operations) =>
      EconomySources.widened(operations,
        EconomySources.inPawnRegion(ctx.state, ctx.activePlayer)))))

  /** The rulebook's access, and the pawn at Map Library's site: ruling the
    * site is not enough. */
  override def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    super.selectable(ready, actor) &&
      PowerAccess.pawnSite(ready, actor).exists(site =>
        SiteRulers.siteOf(ready, cardId).contains(site))

object MapLibrary:
  val id: PowerId = MapLibraryCard.power.id

  def forCatalog(catalog: ExecutableCatalog): MapLibrary =
    new MapLibrary(catalog)
```

Create `src/main/scala/oathdigital/gameplay/powers/economy/SkilledMerchants.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object SkilledMerchantsCard extends Denizen(DenizenId("238"), "Skilled Merchants", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.skilled-merchants"),
    persistent = false, cost = Cost.free,
    text = "You may trade with any denizens in your region.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Skilled Merchants (card 238, adviser-only), a selected Trade modifier
  * with no cost: the Trade may draw on any card at any site in the pawn's
  * region, the pawn's own site and edifices included.
  *
  * It adds those cards to the source decision, after the pawn site's
  * ([[EconomySources.widened]]). The token-free rule still applies. Map
  * Library adds the same cards, so with both selected each is offered once.
  * It writes no line: the start line names it, and the Trade's own line
  * names the source.
  */
final case class SkilledMerchants private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = SkilledMerchantsCard.id
  def id: PowerId = SkilledMerchants.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeSourceSelection -> Vector(Transform((ctx, operations) =>
      EconomySources.widened(operations,
        EconomySources.inPawnRegion(ctx.state, ctx.activePlayer)))))

object SkilledMerchants:
  val id: PowerId = SkilledMerchantsCard.power.id

  def forCatalog(catalog: ExecutableCatalog): SkilledMerchants =
    new SkilledMerchants(catalog)
```

Delete `MapLibraryCard` from `HoldingArcaneDenizens.scala` and `SkilledMerchantsCard` from `HoldingHearthDenizens.scala`, each with its trailing blank line. Remove an import a deletion leaves unused.

In `NewFoundations.scala`, add `MapLibraryCard` and `SkilledMerchantsCard` to the economy import:

```scala
import oathdigital.gameplay.powers.economy.{AnimalPlaymatesCard,
  AwaitedReturnCard, BirdsongCard, CupOfPlentyCard, CurfewCard,
  DefameCard, DowntroddenCard, GolemLegionsCard, InitiationRiteCard,
  KnightsErrantCard, MapLibraryCard, MountedLibraryCard, MovingMarketCard,
  RowdyPubCard, SkilledMerchantsCard, TheOldOakCard, VillageIdiotCard,
  VowOfBeastkinCard, VowOfPovertyCard}
```

- [ ] **Step 6: Register the powers and retire their stubs**

In `ActionModifiers.scala`, change the economy import to:

```scala
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, AwaitedReturn,
  Birdsong, CupOfPlenty, Curfew, Defame, Downtrodden, GolemLegions,
  InitiationRite, MapLibrary, MountedLibrary, MovingMarket, RowdyPub,
  SkilledMerchants, TheOldOak, VillageIdiot, VowOfBeastkin, VowOfPoverty}
```

and add after `Vector(GolemLegions.forCatalog(catalog)) ++`:

```scala
      Vector(MapLibrary.forCatalog(catalog)) ++
      Vector(SkilledMerchants.forCatalog(catalog)) ++
```

In `MusterPowers.scala`, delete the `MapLibrary` and `SkilledMerchants` objects and remove both from `powers`.

- [ ] **Step 7: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.MapLibrarySuite oathdigital.gameplay.powers.economy.SkilledMerchantsSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/MapLibrary.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/SkilledMerchants.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/MapLibrarySuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/SkilledMerchantsSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(economy): Map Library and Skilled Merchants trade across the region"
```

---

### Task 4: Pressgangs and Old Songs

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala` (add `ExemptionPriority`, `exempted`, `intoOccupied`)
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/Pressgangs.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/OldSongs.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` (remove `PressgangsCard`)
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala` (remove `OldSongsCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (economy import)
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/PressgangsSuite.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/economy/OldSongsSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `MusterSource.Occupied` and `MusterSource.cardOf` (Task 1), `EconomySources.withOptions` (Task 2), `GolemLegions` (Task 2), `PowerAnswers.last`, `PowerCtx.answered`.
- Produces:
  - `EconomySources.ExemptionPriority: Int` (1).
  - `EconomySources.exempted(operations: Vector[Operation], admits: DecisionOptionRef => Boolean): Vector[Operation]`.
  - `EconomySources.intoOccupied(operations: Vector[Operation], actor: PlayerId, decisionId: String, answered: Vector[Answered]): Vector[Operation]`.
  - `PressgangsCard`, `Pressgangs`, `Pressgangs.id`, `Pressgangs.forCatalog`; `OldSongsCard`, `OldSongs`, `OldSongs.id`, `OldSongs.forCatalog`, `OldSongs.OneSecret`.

- [ ] **Step 1: Write the failing suites**

Create `src/test/scala/oathdigital/gameplay/powers/economy/PressgangsSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ModifierOffers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PhasePowerHarness, PowerRules, Refusals, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class PressgangsSuite extends munit.FunSuite:
  private val pressgangs = CatalogNames.denizen("Pressgangs")
  private val golem = CatalogNames.denizen("Golem Legions")
  private val selected = Vector(Pressgangs.id)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Arcane denizen. */
  private val alchemist = CatalogNames.denizen("Alchemist")
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val rules = PowerRules.withWalkerPowers
  private val projector = new GameProjector(catalog)

  /** p1 at Ancient City holding Pressgangs faceup, with 2 favor and 7
    * Supply, beside Alchemist, which holds 1 favor and 1 secret. */
  private def occupiedAlchemist: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, pressgangs).favor(p1, 2)
    .denizen(alchemist, at = home).tokens(alchemist, favor = 1, secrets = 1)

  private def musterStarted(ready: ReadyGame, modifiers: Vector[PowerId])
      : OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Muster, p1, modifiers))

  private def states(started: OathTransition)
      : Option[Vector[(String, String)]] =
    PhasePowerHarness.queryOf(started, p1).map(_.offeredOptions
      .map(option => (option.id, option.state)))

  private def controls(ready: ReadyGame): Vector[String] =
    projector.project("pressgangs", LoadedGame(Ready(ready), 4), p1)
      .legalControls

  private def modifiers(ready: ReadyGame, action: ActionRef)
      : Vector[PowerId] =
    accepted(ModifierOffers.offerable(WalkerPowerCatalog.default(catalog),
      ready, p1, action)).map(_.id)

  test("without it, a card holding favor or secrets is shown unavailable " +
      "and refused"):
    val ready = occupiedAlchemist.denizen(errandBoy, at = home).ready
    val started = musterStarted(ready, Vector.empty)
    assertEquals(states(started), Some(Vector(
      alchemist.value -> "unavailable", errandBoy.value -> "available")))
    assertEquals(rules.resolveWalker(started.state, p1,
      MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(alchemist))).left.toOption,
      Some(Refusals.notOffered(MusterProcedure.decisionId)))

  test("with only such a card at the site, Muster starts only with it " +
      "selected"):
    val ready = occupiedAlchemist.ready
    assertEquals(rules.startWalker(Ready(ready), ActionRef.Muster, p1)
      .left.toOption, Some(OathViolation.NoPlayableOption(ActionRef.Muster.key)))
    assert(controls(ready).contains("beginMuster"), controls(ready))
    assertEquals(states(musterStarted(ready, selected)),
      Some(Vector(alchemist.value -> "available")))

  test("selected, Muster places its favor beside the tokens already there"):
    val ready = occupiedAlchemist.ready
    val done = EconomyActions.muster(rules, ready, p1, selected,
      DecisionOptionRef.Denizen(alchemist))
    val after = Look(done.result)
    assertEquals(after.tokensOn(alchemist), Tokens(2, 1))
    // 1: Pressgangs is Order, not Arcane.
    assertEquals(after.boardWarbands(p1), Look(ready).boardWarbands(p1) + 1)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("with Golem Legions, Golem Legions is mustered on twice in one turn"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, golem)
      .adviser(p1, pressgangs).favor(p1, 2).ready
    val first = EconomyActions.muster(rules, ready, p1,
      Vector(GolemLegions.id), DecisionOptionRef.Denizen(golem))
    assertEquals(rules.startWalker(Ready(first.result), ActionRef.Muster, p1,
      Vector(GolemLegions.id)).left.toOption,
      Some(OathViolation.NoPlayableOption(ActionRef.Muster.key)))
    val second = EconomyActions.muster(rules, first.result, p1,
      Vector(GolemLegions.id, Pressgangs.id), DecisionOptionRef.Denizen(golem))
    assertEquals(Look(second.result).tokensOn(golem), Tokens(2, 0))

  test("it is a Muster modifier only"):
    val ready = occupiedAlchemist.ready
    assert(modifiers(ready, ActionRef.Muster).contains(Pressgangs.id))
    assert(!modifiers(ready, ActionRef.Trade).contains(Pressgangs.id))
```

Create `src/test/scala/oathdigital/gameplay/powers/economy/OldSongsSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ModifierOffers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PhasePowerHarness, PowerRules, Refusals, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class OldSongsSuite extends munit.FunSuite:
  private val songs = CatalogNames.denizen("Old Songs")
  private val selected = Vector(OldSongs.id)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Arcane denizen, holding one secret. */
  private val alchemist = CatalogNames.denizen("Alchemist")
  /** A plain Order denizen, holding one secret and one favor. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Beast denizen, holding nothing. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City holding Old Songs faceup, with the start's 1 faceup
    * secret, beside Alchemist (1 secret), Wrestlers (1 secret and 1 favor)
    * and Errand Boy (nothing). The Arcane bank holds 3 favor. */
  private def staged: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, songs).denizen(alchemist, at = home)
    .denizen(wrestlers, at = home).denizen(errandBoy, at = home)
    .tokens(alchemist, secrets = 1).tokens(wrestlers, favor = 1, secrets = 1)
    .bankFavor(Suit.Arcane, 3)

  private def tradeStarted(ready: ReadyGame, modifiers: Vector[PowerId])
      : OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Trade, p1, modifiers,
      Vector(DecisionOptionRef.Button("favor"))))

  private def states(started: OathTransition)
      : Option[Vector[(String, String)]] =
    PhasePowerHarness.queryOf(started, p1).map(_.offeredOptions
      .map(option => (option.id, option.state)))

  private def modifiers(ready: ReadyGame, action: ActionRef)
      : Vector[PowerId] =
    accepted(ModifierOffers.offerable(WalkerPowerCatalog.default(catalog),
      ready, p1, action)).map(_.id)

  test("selected, a card with exactly one secret and no favor is a Trade " +
      "source, and one with favor too is not"):
    assertEquals(states(tradeStarted(staged.ready, selected)), Some(Vector(
      alchemist.value -> "available", wrestlers.value -> "unavailable",
      errandBoy.value -> "available")))

  test("trading with it places the secret beside the one already there"):
    val ready = staged.ready
    val done = EconomyActions.trade(rules, ready, p1, selected, "favor",
      DecisionOptionRef.Denizen(alchemist))
    val after = Look(done.result)
    assertEquals(after.tokensOn(alchemist), Tokens(0, 2))
    // 1: Old Songs is Hearth, not Arcane.
    assertEquals(after.favor(p1), Look(ready).favor(p1) + 1)
    assertEquals(after.bankFavor(Suit.Arcane), 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("without it, the card with one secret is shown unavailable and " +
      "refused"):
    val started = tradeStarted(staged.ready, Vector.empty)
    assertEquals(states(started), Some(Vector(
      alchemist.value -> "unavailable", wrestlers.value -> "unavailable",
      errandBoy.value -> "available")))
    assertEquals(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(alchemist))).left.toOption,
      Some(Refusals.notOffered(TradeProcedure.decisionId)))

  test("it is a Trade modifier only"):
    val ready = staged.ready
    assert(modifiers(ready, ActionRef.Trade).contains(OldSongs.id))
    assert(!modifiers(ready, ActionRef.Muster).contains(OldSongs.id))
```

- [ ] **Step 2: Pin the registrations**

In `PowerKindsCatalogSuite.scala`, add `"denizen.pressgangs"` and `"denizen.old-songs"` to the end of `modifiers`.

- [ ] **Step 3: Run the suites to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.PressgangsSuite oathdigital.gameplay.powers.economy.OldSongsSuite"`
Expected: compile failure, `Pressgangs` and `OldSongs` are not members of `oathdigital.gameplay.powers.economy`.

- [ ] **Step 4: Add the exemption and the payment mark to the helper**

In `EconomySources.scala`, add the import

```scala
import oathdigital.gameplay.powers.PowerAnswers
```

and add after `inPawnRegion`:

```scala
  /** The priority an exemption folds at: after the powers that add cards,
    * at 0, so that a card one of them adds can be exempted too. */
  val ExemptionPriority: Int = 1

  /** `operations` with every source the token-free rule withheld made
    * available, when `admits` accepts its card. */
  def exempted(operations: Vector[Operation],
      admits: DecisionOptionRef => Boolean): Vector[Operation] =
    withOptions(operations)(_.map {
      case DecisionOption.Unavailable(option, MusterSource.Occupied)
          if admits(option.ref) => option
      case other => other
    })

  /** `operations` with `actor`'s payment onto the answered source marked
    * `intoOccupied`, which `PayCostRules` would refuse onto a card that holds
    * tokens. The source decision is the gate: an occupied card is answerable
    * only when an exemption made it available, so the mark changes nothing
    * for a token-free card. Every other payment keeps the check.
    */
  def intoOccupied(operations: Vector[Operation], actor: PlayerId,
      decisionId: String, answered: Vector[Answered]): Vector[Operation] =
    PowerAnswers.last(answered, decisionId).flatMap(idOf) match
      case None => operations
      case Some(card) => operations.map {
        case pay: PayCost if pay.player == actor &&
            pay.placedAt == Location.OnCard(card) =>
          pay.copy(intoOccupied = true)
        case other => other
      }

  private def idOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None
```

- [ ] **Step 5: Write the two powers and move their cards**

Create `src/main/scala/oathdigital/gameplay/powers/economy/Pressgangs.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object PressgangsCard extends Denizen(DenizenId("6"), "Pressgangs", Suit.Order):
  val power = PrintedPower(PowerId("denizen.pressgangs"),
    persistent = false, cost = Cost.free,
    text = "You can muster on cards that already have [favor] or " +
      "[secret] on them.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Pressgangs (card 6), a selected Muster modifier with no cost: the Muster
  * may draw on a card that holds any number of favor or secrets.
  *
  * It folds after the powers that add sources
  * ([[EconomySources.ExemptionPriority]]) and makes every source the
  * token-free rule withheld available, so with Golem Legions selected too,
  * Golem Legions may be mustered on twice in one turn. The Muster's payment
  * onto the answered source is marked `intoOccupied`. It does not apply to
  * a Trade. It writes no line: the start line names it.
  */
final case class Pressgangs private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = PressgangsCard.id
  def id: PowerId = Pressgangs.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)
  override def priority: Int = EconomySources.ExemptionPriority

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterSourceSelection -> Vector(Transform((_, operations) =>
      EconomySources.exempted(operations, _ => true))),
    PowerWindow.MusterCost -> Vector(Transform((ctx, operations) =>
      EconomySources.intoOccupied(operations, ctx.activePlayer,
        MusterProcedure.decisionId, ctx.answered))))

object Pressgangs:
  val id: PowerId = PressgangsCard.power.id

  def forCatalog(catalog: ExecutableCatalog): Pressgangs =
    new Pressgangs(catalog)
```

Create `src/main/scala/oathdigital/gameplay/powers/economy/OldSongs.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.economy.{MusterSource, TradeProcedure}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object OldSongsCard extends Denizen(DenizenId("229"), "Old Songs", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.old-songs"),
    persistent = false, cost = Cost.free,
    text = "You may trade with cards that have exactly one [secret] on " +
      "them.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Old Songs (card 229), a selected Trade modifier with no cost: the Trade
  * may also draw on a card that holds exactly one secret and no favor. A
  * card with one secret and any favor does not qualify, and a token-free
  * card qualifies as usual.
  *
  * It folds after the powers that add sources
  * ([[EconomySources.ExemptionPriority]]), so it also admits such a card
  * that Map Library or Skilled Merchants adds. The Trade's payment onto the
  * answered source is marked `intoOccupied`. It does not apply to a Muster.
  * It writes no line: the start line names it.
  */
final case class OldSongs private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = OldSongsCard.id
  def id: PowerId = OldSongs.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def priority: Int = EconomySources.ExemptionPriority

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeSourceSelection -> Vector(Transform((ctx, operations) =>
      EconomySources.exempted(operations, ref =>
        MusterSource.cardOf(ctx.state, ctx.activePlayer, ref)
          .exists(_.tokens == OldSongs.OneSecret)))),
    PowerWindow.TradeCost -> Vector(Transform((ctx, operations) =>
      EconomySources.intoOccupied(operations, ctx.activePlayer,
        TradeProcedure.decisionId, ctx.answered))))

object OldSongs:
  val id: PowerId = OldSongsCard.power.id
  /** What a card must hold for Old Songs to admit it. */
  val OneSecret: Tokens = Tokens(favor = 0, secrets = 1)

  def forCatalog(catalog: ExecutableCatalog): OldSongs =
    new OldSongs(catalog)
```

Delete `PressgangsCard` from `HoldingOrderDenizens.scala` and `OldSongsCard` from `HoldingHearthDenizens.scala`, each with its trailing blank line. Remove an import a deletion leaves unused.

In `NewFoundations.scala`, add `OldSongsCard` and `PressgangsCard` to the economy import:

```scala
import oathdigital.gameplay.powers.economy.{AnimalPlaymatesCard,
  AwaitedReturnCard, BirdsongCard, CupOfPlentyCard, CurfewCard,
  DefameCard, DowntroddenCard, GolemLegionsCard, InitiationRiteCard,
  KnightsErrantCard, MapLibraryCard, MountedLibraryCard, MovingMarketCard,
  OldSongsCard, PressgangsCard, RowdyPubCard, SkilledMerchantsCard,
  TheOldOakCard, VillageIdiotCard, VowOfBeastkinCard, VowOfPovertyCard}
```

- [ ] **Step 6: Register the powers and retire their stubs**

In `ActionModifiers.scala`, change the economy import to:

```scala
import oathdigital.gameplay.powers.economy.{AnimalPlaymates, AwaitedReturn,
  Birdsong, CupOfPlenty, Curfew, Defame, Downtrodden, GolemLegions,
  InitiationRite, MapLibrary, MountedLibrary, MovingMarket, OldSongs,
  Pressgangs, RowdyPub, SkilledMerchants, TheOldOak, VillageIdiot,
  VowOfBeastkin, VowOfPoverty}
```

and add after `Vector(SkilledMerchants.forCatalog(catalog)) ++`:

```scala
      Vector(Pressgangs.forCatalog(catalog)) ++
      Vector(OldSongs.forCatalog(catalog)) ++
```

In `MusterPowers.scala`, delete the `Pressgangs` and `OldSongs` objects and remove both from `powers`.

- [ ] **Step 7: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.* oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS. If "selected, Muster places its favor beside the tokens" fails with `CardOccupied` or `EconomyCardNotEmpty`, the cost mark did not apply. Check that `ctx.answered` holds the source answer at `MusterCost`, and stop and report if it does not.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/Pressgangs.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/OldSongs.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/main/scala/oathdigital/gameplay/powers/MusterPowers.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingHearthDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/PressgangsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/OldSongsSuite.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(economy): Pressgangs and Old Songs draw on cards that hold tokens"
```

---

### Task 5: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md` ("Now" paragraph; Phase - Catalog batch 5)
- Modify: `docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md` (new section)

- [ ] **Step 1: Run the gates**

Run, in order:
1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. Compare the test counts with the baseline.

- [ ] **Step 2: Record the plan-time notes in the design**

In `docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md`, add this section before "## Slice 2: Vow of Wandering":

```markdown
### Implementation notes from slice 1

- **The cost mark reads the answer.** An exemption marks the actor's
  `PayCost` onto the answered source `intoOccupied` whenever it is selected,
  not only when the source holds tokens. The source decision is the gate, so
  the mark changes nothing for a token-free card, and the fold depends on the
  answers alone.
- **No slice 1 power writes a line.** Each is named on its action's start
  line, and the Muster and Trade lines name the source wherever it stands.
```

- [ ] **Step 3: Update the roadmap**

In `docs/ROADMAP.md`:

- Delete the bullet `- [ ] **Slice 1: Muster and Trade sources.** Golem Legions, Map Library, Skilled Merchants, Old Songs, Pressgangs.` (two lines) under "### Phase - Catalog batch 5".
- In "## Now", append to the paragraph: `**Phase - Catalog batch 5** is in progress: slice 1 is done.`

Run `python3 scripts/check-markdown-links.py` again. Expected: pass.

- [ ] **Step 4: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-08-catalog-batch-5-design.md
git commit -m "docs: record catalog batch 5 slice 1"
```
