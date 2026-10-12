# Catalog Batch 8, Slice 1 (No New Engine) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement three cards that need little or no engine change: Ward of Silence (202), Hospitality (171) and Vow of Wisdom (203).

**Architecture:**

- **Hospitality** is a Travel rule, like Tyrant. At `TravelCost` it appends a `Branch` after the pawn's move. The branch reads the destination's cards and the traveller's faceup advisers when the walk reaches it. With one stocked matching bank it gains 1 favor from it. With several it asks first, which parks a decision after the move.
- **Ward of Silence** is a `PaidAction` with a free printed cost. Its `build` asks for the target and then X. It pays X secrets onto its card and flips X of the target's faceup secrets facedown, in one batch. Its own `usable` checks the empty-card rule, which a free printed cost skips.
- **Rewrites reach into a card play.** `OperationRewrite` today leaves every custom composite alone, so it cannot reach the site play's favor gain inside `CardPlayProcedure.PlacementBody`. A new model trait, `RewritableComposite`, lets a composite rebuild itself with its children rewritten, and `OperationRewrite` descends it. `PlacementBody` implements it and keeps the rewrite through every later `adjust`. `PlacementTree` always holds one body, so a rewrite and an `adjust` compose in either order.
- **Vow of Wisdom** is an `OperationRewrite` at `SearchPlayAdviser`. It follows its holder's `Play` to a site with a `Gain.Secrets` of 1, and drops the play's `Gain.Favor`.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`: "Goal and scope", "The bar", "Slice 1: No new engine", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: "Rules that apply to the whole batch" and the three slice 1 entries.

Task 1 amends both with this plan's rulings. Read them again after Task 1.

**Starts from:** `main` at `69092795` or later. No other batch 8 slice has started.

Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
  - Each new file below lists the imports it needs.
  - When a card moves out of a holding catalog file, remove any import that file no longer uses.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Names, exactly:**
  - `WardOfSilence` (case object) and `WardOfSilenceCard`. Decisions `power.ward-of-silence.target` and `power.ward-of-silence.amount`.
  - `Hospitality` (final case class with `forCatalog`) and `HospitalityCard`. Decision `travel.hospitality`.
  - `VowOfWisdom` (case object) and `VowOfWisdomCard`.
  - `oathdigital.model.RewritableComposite`, a trait extending `Operation`, with `def rewritten(rewrite: Operation => Operation): Operation`.
- **Texts, exactly:**
  - Ward of Silence's unavailable target: "No faceup secrets". Its line: "Flipped 2 secrets on {Blue}'s board facedown.", the action's own line.
  - Ward of Silence's headings: "Ward of Silence: flip secrets of a player at your site", and "Ward of Silence: place X secrets on this card to flip X of their secrets facedown", confirmed with "Flip".
  - Hospitality's heading: "Hospitality: gain 1 favor from which bank?". Its unavailable bank: "This bank is empty".
  - Hospitality and Vow of Wisdom write no line of their own. The generic lines say it: "Gained 1 favor from the Order bank" and "Gained 1 secret".
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 17 server tests and no frontend test.
  - It changes one existing test: `PowerKindsCatalogSuite`'s persistent-rule list gains `denizen.vow-of-wisdom` (Task 4).
  - Task 4 adds a level to every card play's node path. A test that pins such a path is updated in Task 4 and named in the report.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner reviews these with the plan. Task 1 writes all of them into the spec.

1. **Rewrites reach into a card play.** The design gave Vow of Wisdom an `OperationRewrite` at `SearchPlayAdviser`, but the operation rewrite design left custom composites alone. The site play's `Gain.Favor` is built inside one, `CardPlayProcedure.PlacementBody`. A rewrite was also lost when it ran before the first `adjust`, which replaced the default children with a fresh body. So:
   - A model trait, `RewritableComposite`, lets a composite rebuild itself with each child passed through a rewrite. `OperationRewrite.ops` and `.nodes` descend it, unless it carries its own window.
   - `PlacementBody` implements it. The rewrite joins the closure that plans the body's children, so every later `adjust` keeps it.
   - `PlacementTree`'s children are always one `PlacementBody`, under the default rules until a contributor adjusts them. So a rewrite and an `adjust` compose in either order.
   - Every card play's node path gains one level. A game parked inside a card play when the server updates may not resume.
   The other custom composites carry their own windows and stay as they are. Any of them may implement the trait when a power needs it.
2. **Vow of Wisdom always gains a secret.** Its holder's play to a site gains 1 secret, whether or not the card's suit bank holds favor, and gains no favor. The rewrite follows the `Play` to a site with `Gain.Secrets(holder, 1)` and drops the play's `Gain.Favor`.
3. **Ward of Silence pays inside its action.** Its printed cost is free, so the engine places nothing and does not check the empty-card rule. Its `usable` checks that its card could take one secret (`Costs.affordable`): a faceup secret, and an empty card. It also checks that another player at the site holds a faceup secret. Otherwise the Act menu shows it unavailable with the engine's "Nothing to do now". A reason of its own would need a change to `PhasePower`, which is out of scope.
4. **Ward of Silence's line** is the action's own: "Flipped 2 secrets on {Blue}'s board facedown." Neither question posts a "Chose" line.
5. **Hospitality's banks.** A matching bank is the suit of a card at the destination (a denizen or an edifice) and of one of the traveller's faceup advisers. Marriage counts as its suit, as `AdviserSuits` reads it. With one matching bank that holds favor, Hospitality gains from it without asking, even when other matching banks are empty. With two or more, it asks. Every matching bank is listed, and an empty one is shown unavailable with "This bank is empty". With none holding favor, nothing happens.
6. **Hospitality writes no line.** Its question uses the `travel.` decision prefix, so it posts no "Chose" line. The generic gain line names the bank, and it names the traveller when the traveller is not the active player.
7. **Hospitality off turn.** The rule follows the pawn that moves, not the active player, so it already covers an off-turn Travel. Slice 15 tests that with Palanquin.

## File map

**Spec (Task 1):** the design and the rulings appendix.

**Hospitality (Task 2):**
- `src/main/scala/oathdigital/gameplay/powers/travel/Hospitality.scala`: new. `HospitalityCard` moves out of `catalog/holding/HoldingNomadDenizens.scala`.
- Registration: `TravelModifiers.scala`, and the `powers.travel` import in `NewFoundations.scala`.
- Tests: new `HospitalitySuite` and `HospitalityLogSuite`. `PowerKindsCatalogSuite` gains one test.

**Ward of Silence (Task 3):**
- `src/main/scala/oathdigital/gameplay/powers/action/WardOfSilence.scala`: new. `WardOfSilenceCard` moves out of `catalog/holding/HoldingArcaneDenizens.scala`.
- Registration: `TargetPowers.scala`, and the `powers.action` import in `NewFoundations.scala`.
- Tests: new `WardOfSilenceSuite`.

**Vow of Wisdom (Task 4):**
- `src/main/scala/oathdigital/model/Operation.scala`: `RewritableComposite`.
- `src/main/scala/oathdigital/gameplay/powerresolver/OperationRewrite.scala`: descends a `RewritableComposite`.
- `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`: `PlacementBody` implements the trait; `PlacementTree` always holds one body.
- `src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfWisdom.scala`: new. `VowOfWisdomCard` moves out of `catalog/holding/HoldingArcaneDenizens.scala`.
- Registration: `CardPlayTriggers.scala`, and the `powers.cardplay` import in `NewFoundations.scala`.
- Tests: new `VowOfWisdomSuite`. `OperationRewriteSuite` and `CardPlayRulesSuite` gain one test each. `PowerKindsCatalogSuite`'s persistent-rule list gains an entry.

**Roadmap (Task 5):** `docs/ROADMAP.md`, and the design's status.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`

- [ ] **Step 1: Amend the design**

1. In the **Status** paragraph, replace `No slice has started.` with `Slice 1 is planned.`

2. In "The bar", before the bullet that begins `- **A Set Oathkeeper Goal operation** (slice 2).`, add:

```markdown
- **Rewrites inside a card play** (slice 1). A model trait,
  `RewritableComposite`, lets a custom composite rebuild itself with its
  children rewritten, and `OperationRewrite` descends it.
  `CardPlayProcedure.PlacementBody` implements it and keeps the rewrite
  through every later `adjust`, and `PlacementTree` always holds one body,
  so a rewrite at `SearchPlayAdviser` reaches the site play's favor gain
  (Vow of Wisdom).
```

3. Replace the whole "## Slice 1: No new engine" section, up to the next `##` heading, with:

```markdown
## Slice 1: No new engine

- **Ward of Silence** is an ACTION power with a free printed cost. Its
  `build` chooses the target and asks X with a `ChooseAmount`. It then
  pays X secrets onto its card and flips X of the target's secrets facedown
  with `FlipSecrets`, in one batch. Sleight of Hand gives the target choice
  and Blood Pact the amount. Its own `usable` checks the empty-card rule,
  which a free printed cost skips.
- **Hospitality** runs after the pawn's move in Travel, as Tyrant does. It
  asks which bank when more than one matches, which parks a decision after
  the move.
- **Vow of Wisdom** is an `OperationRewrite` at `SearchPlayAdviser`. It
  follows its holder's `Play` to a site with a gain of 1 secret, and drops
  the play's favor gain.

### Plan-time rulings

- **Rewrites reach into a card play.** The operation rewrite design left
  custom composites alone, and the site play's favor gain is built inside
  one. A rewrite that ran before the first `adjust` was also lost. The
  `RewritableComposite` trait fixes both for `PlacementBody`, and
  `PlacementTree` always holds one body. Every card play's node path gains
  a level, so a game parked inside a card play when the server updates may
  not resume.
- **Vow of Wisdom always gains a secret.** Its holder's play to a site
  gains 1 secret whether or not the suit bank holds favor, and no favor.
- **Ward of Silence pays inside its action.** Its `usable` checks that its
  card could take one secret (a faceup secret and an empty card), and that
  another player at the site holds a faceup secret. Otherwise the Act menu
  shows the engine's "Nothing to do now".
- **Ward of Silence's line** is the action's own: "Flipped 2 secrets on
  {Blue}'s board facedown." Neither question posts a "Chose" line.
- **Hospitality's banks.** A matching bank is the suit of a card at the
  destination and of one of the traveller's faceup advisers, Marriage as
  `AdviserSuits` reads it. One matching bank with favor is used without
  asking. With two or more it asks, listing every matching bank, and an
  empty one is shown unavailable with "This bank is empty".
- **Hospitality writes no line.** Its question posts no "Chose" line, and
  the generic gain line names the bank, and the traveller when they are not
  the active player. The rule follows the pawn that moves, so it covers an
  off-turn Travel; slice 15 tests that with Palanquin.
```

- [ ] **Step 2: Amend the rulings appendix**

Replace the whole "## Slice 1: No new engine" section, up to `## Slice 2`, with:

```markdown
## Slice 1: No new engine

- **202 Ward of Silence.** "X [secret] ACTION: Choose a player whose pawn is
  at your site. Flip X [secret] on their board facedown."
  - The target is another player whose pawn is at the user's site.
  - X runs from one to the smaller of the user's faceup secrets and the
    target's faceup secrets. The user places X secrets on the card, and X
    of the target's faceup secrets flip facedown, in one batch.
  - A player at the site with no faceup secrets is shown unavailable as a
    target, with "No faceup secrets".
  - The action is usable when its card could take a secret (the user has a
    faceup secret and the card is empty) and another player at the site has
    a faceup secret. Otherwise the Act menu shows it unavailable with
    "Nothing to do now" (plan-time ruling).
  - Line: "Ward of Silence: Flipped 2 secrets on {Blue}'s board facedown."
- **171 Hospitality.** "After traveling, gain [favor] from one favor bank
  that matches both a card at your site and one of your advisers."
  - After the pawn arrives, the matching banks are the suits shared by a
    card at the new site and one of the holder's faceup advisers.
  - With one matching bank that holds favor, it gains from it without
    asking. With several, it asks which, listing every matching bank; an
    empty one is shown unavailable with "This bank is empty". With none
    holding favor, nothing happens (plan-time ruling).
  - It applies to every Travel the holder makes, including one made off turn
    (Palanquin).
  - It writes no line: the generic gain line names the bank.
- **203 Vow of Wisdom.** "You must gain [secret] instead of [favor] when you
  play to a site." When its holder plays a card to a site, the play gains
  1 secret and no favor. There is no choice. The secret is gained whether
  or not the card's suit bank holds favor (plan-time ruling). It writes no
  line: the generic line reads "Gained 1 secret".
```

Re-wrap every edited paragraph to 78 characters.

- [ ] **Step 3: Check the links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: no broken link.

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md
git commit -m "docs(batch8): record slice 1 plan-time rulings"
```

---

### Task 2: Hospitality gains favor after Travel

This is the slice's riskiest seam: a decision parked after the pawn's move in Travel's cost node. It comes first. If the walk will not park on `travel.hospitality` after the move, stop and ask the product owner. The same applies if the destination look-ahead hides the destination because of it. Do not change the walker or `TravelProcedure`.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/travel/Hospitality.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `HospitalityCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Test: `src/test/scala/oathdigital/gameplay/powers/travel/HospitalitySuite.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/HospitalityLogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `TravelRoute.pawnMove` (package-private to `powers.travel`), `AdviserSuits.count(catalog, ready, player, suit)`, `PowerAnswers.one`, `PowerAnswers.missing`.
- Produces: `Hospitality.forCatalog(catalog): Hospitality`, `Hospitality.id`, `Hospitality.decisionId = "travel.hospitality"`, `Hospitality.heading`, `Hospitality.emptyBank`, `HospitalityCard`.

- [ ] **Step 1: Write the failing tests**

Create `HospitalitySuite.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  PhasePowerHarness}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

/** Hospitality: after its holder travels, 1 favor from a bank matching both
  * a card at the destination and one of the holder's faceup advisers. */
class HospitalitySuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val hospitality = CatalogNames.denizen("Hospitality")
  /** Order. */
  private val messenger = CatalogNames.denizen("Messenger")
  /** Beast. */
  private val wolves = CatalogNames.denizen("Wolves")
  /** Nomad. */
  private val elders = CatalogNames.denizen("Elders")
  /** Order. */
  private val siegeEngines = CatalogNames.denizen("Siege Engines")
  /** Beast. */
  private val faeMerchant = CatalogNames.denizen("Fae Merchant")
  /** Arcane. */
  private val bloodPact = CatalogNames.denizen("Blood Pact")

  /** The actor at Buried Giant holds Hospitality (Nomad), Messenger (Order)
    * and Wolves (Beast) faceup, or Hospitality facedown when
    * `hospitalityFacedown`. `atCoast` stand at the coast, and only `stocks`
    * hold favor. */
  private def holdingHospitalityAndCoastHolding(atCoast: Vector[DenizenId],
      stocks: (Suit, Int)*)(hospitalityFacedown: Boolean = false)
      : ReadyGame =
    on(board(supply = 7))(table => atCoast.foldLeft(table
      .adviser(actor, hospitality, facedown = hospitalityFacedown)
      .adviser(actor, messenger).adviser(actor, wolves)
      .banksHoldingOnly(stocks*))((placed, card) =>
        placed.denizen(card, coast)))

  private def bank(suit: Suit): DecisionOptionRef.FavorBank =
    DecisionOptionRef.FavorBank(suit)

  private def favorGains(events: Vector[OathEvent]): Vector[Gain.Favor] =
    PhasePowerHarness.ops(events).collect { case gain: Gain.Favor => gain }

  test("with two stocked matching banks it asks after the move, shows an " +
      "empty one unavailable, and the chosen bank gives 1 favor"):
    val ready = holdingHospitalityAndCoastHolding(
      Vector(elders, siegeEngines, faeMerchant),
      Suit.Nomad -> 2, Suit.Order -> 2)()
    val asked = accepted(travel(ready, coast))
    val decide = parkedDecision(asked.state)
    assertEquals(decide.decisionId, Hospitality.decisionId)
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Suit.all.filter(Set(Suit.Order, Suit.Beast, Suit.Nomad)).map(suit =>
        val shown = DecisionOption.FavorBank(bank(suit))
        if suit == Suit.Beast then DecisionOption.Unavailable(shown,
          OptionReason.Written(Hospitality.emptyBank))
        else shown),
      heading = Some(Hospitality.heading)))
    // The pawn arrived before the question.
    assertEquals(Look(Outcomes.ready(asked.state)).pawn(actor), coast)
    val done = accepted(answeringStep(asked, Hospitality.decisionId,
      bank(Suit.Order)))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).favor(actor), Look(ready).favor(actor) + 1)
    assertEquals(Look(result).bankFavor(Suit.Order), 1)
    assertEquals(Look(result).bankFavor(Suit.Nomad), 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), result)
    assertWireRoundTrips(done.events)

  test("an empty bank cannot be chosen"):
    val ready = holdingHospitalityAndCoastHolding(
      Vector(elders, siegeEngines, faeMerchant),
      Suit.Nomad -> 2, Suit.Order -> 2)()
    val asked = accepted(travel(ready, coast))
    assert(answeringStep(asked, Hospitality.decisionId, bank(Suit.Beast))
      .isLeft)

  test("one stocked matching bank gives its favor without asking"):
    val ready = holdingHospitalityAndCoastHolding(Vector(elders, siegeEngines),
      Suit.Nomad -> 2)()
    val done = accepted(travel(ready, coast))
    val result = Outcomes.ready(done.state)
    assertEquals(Look(result).pawn(actor), coast)
    assertEquals(favorGains(done.events),
      Vector(Gain.Favor(actor, Suit.Nomad, 1)))
    assertEquals(Look(result).bankFavor(Suit.Nomad), 1)

  test("a bank matching only a card at the site, or only an adviser, gives " +
      "nothing"):
    // Arcane is at the coast but not among the advisers; Order is among the
    // advisers but not at the coast.
    val ready = holdingHospitalityAndCoastHolding(Vector(bloodPact),
      Suit.Arcane -> 2, Suit.Order -> 2)()
    val done = accepted(travel(ready, coast))
    assertEquals(favorGains(done.events), Vector.empty)

  test("a facedown Hospitality does nothing"):
    val ready = holdingHospitalityAndCoastHolding(Vector(elders),
      Suit.Nomad -> 2)(hospitalityFacedown = true)
    val done = accepted(travel(ready, coast))
    assertEquals(favorGains(done.events), Vector.empty)
```

Create `HospitalityLogSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.{GameApplicationService, GameCommand,
  ParkedServiceFixture, TreeDecision}
import oathdigital.gameplay.powers.travel.Hospitality
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}
import LogScripts.formatter

/** How Hospitality's question reads in the Game Log: no "Chose" line and no
  * line of its own; the generic gain line names the bank. */
class HospitalityLogSuite extends munit.FunSuite:
  private val destination = CatalogNames.site("Broken Peaks")

  /** p1 holds Hospitality (Nomad) and Messenger (Order) faceup and Travels
    * from Ancient City to Broken Peaks, where Elders (Nomad) and Siege
    * Engines (Order) stand. Only the Nomad and Order banks hold favor, and
    * p1 picks the Order bank. The log as p2 sees it. */
  private def travelledToBrokenPeaksPickingOrder: Vector[String] =
    val table = Table.start.pawn(p1, "Ancient City").supply(p1, 7)
      .adviser(p1, "Hospitality").adviser(p1, "Messenger")
      .denizen("Elders", "Broken Peaks")
      .denizen("Siege Engines", "Broken Peaks")
      .banksHoldingOnly(Suit.Nomad -> 2, Suit.Order -> 2)
    val (service, _) = table.service()
    val started = accepted(service.handle("g", 0L,
      ParkedServiceFixture.startTravel(p1)))
    val picked = accepted(service.handle("g", started.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1, destination)))
    accepted(service.handle("g", picked.nextSequence,
      GameCommand.ResolveWalker(p1, TreeDecision(Hospitality.decisionId,
        DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.FavorBank(Suit.Order))))))
    texts(service)

  private def texts(service: GameApplicationService): Vector[String] =
    formatter.format(present(accepted(service.history("g")),
      "a stored game").steps, Some(p2)).map(LogScripts.text)

  test("the bank question posts no Chose line and the gain line names the " +
      "bank"):
    val log = travelledToBrokenPeaksPickingOrder
    assert(log.exists(_.startsWith("Gained 1 favor from the Order bank")),
      log.toString)
    assert(!log.exists(_.startsWith("Chose")), log.toString)
    assert(!log.exists(_.startsWith("Hospitality")), log.toString)
```

In `PowerKindsCatalogSuite`, after the Tyrant test, add:

```scala
  test("Hospitality's rule is automatic whatever its flag: it is a must, " +
      "not a modifier"):
    assertEquals(flag("denizen.hospitality"), Some(false))
    assertEquals(oathdigital.gameplay.powers.travel.Hospitality
      .forCatalog(catalog).resolution, PowerResolution.Automatic)
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.HospitalitySuite oathdigital.application.gamelog.HospitalityLogSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: compile failure, `Hospitality` not found.

- [ ] **Step 3: Write the rule**

Delete `HospitalityCard` from `HoldingNomadDenizens.scala`. Create `Hospitality.scala`:

```scala
package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.economy.AdviserSuits
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object HospitalityCard extends Denizen(DenizenId("171"), "Hospitality",
    Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.hospitality"),
    persistent = false, cost = Cost.free,
    text = "After traveling, gain [favor] from one favor bank that " +
      "matches both a card at your site and one of your advisers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Hospitality (card 171, adviser-only), an automatic rule of a faceup
  * adviser: "After traveling, gain favor from one favor bank that matches
  * both a card at your site and one of your advisers."
  *
  * After the traveller's pawn moves in the cost node, a branch reads the
  * destination's cards (denizens and edifices) and the traveller's faceup
  * advisers (`AdviserSuits`, so Marriage counts). The matching banks are
  * the suits both share. With one that holds favor, 1 favor is gained from
  * it. With two or more, the traveller is asked first, and every matching
  * bank is listed, an empty one unavailable with "This bank is empty". With
  * none holding favor, nothing happens. The rule follows the pawn that
  * moves, so an off-turn Travel counts too. Only Travel walks `TravelCost`.
  *
  * The catalog marks the power `persistent: false`, but it is a must, so it
  * keeps the default automatic resolution, as Tyrant does.
  *
  * It writes no line. The question is under the `travel.` prefix, so it
  * posts no "Chose" line, and the generic gain line names the bank.
  */
final case class Hospitality private (catalog: ExecutableCatalog)
    extends ContributingPower:
  import Hospitality._
  def id: PowerId = Hospitality.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      TravelRoute.pawnMove(ctx.operation).fold(operations)(route =>
        operations :+ Branch((live, _) =>
          welcome(live, route.player, route.destination))))))

  override def applicable(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).exists(route =>
      holds(ctx.state, route.player))

  /** The banks matching both a card at `site` and one of `guest`'s faceup
    * advisers, in suit order. */
  private def matching(ready: ReadyGame, guest: PlayerId, site: SiteId)
      : Vector[Suit] =
    val atSite = ready.game.current.map.sites.get(site).toVector
      .flatMap(_.denizens).flatMap(card => catalog.suitOf(card.id)).toSet
    Suit.all.filter(suit => atSite(suit) &&
      AdviserSuits.count(catalog, ready, guest, suit) > 0)

  private def welcome(ready: ReadyGame, guest: PlayerId, site: SiteId)
      : Vector[Operation] =
    val banks = matching(ready, guest, site)
    banks.filter(stocked(ready, _)) match
      case Vector() => Vector.empty
      case Vector(only) => Vector(gain(guest, _ => Some(only)))
      case _ => Vector(
        Decide(decisionId, guest, DecisionQuery.ChooseOne(
          banks.map(option(ready, _)), heading = Some(heading))),
        gain(guest, chosen))

object Hospitality:
  val id: PowerId = HospitalityCard.power.id
  val Favor: Int = 1
  /** Under the `travel.` prefix, so the Game Log narrates it. */
  val decisionId: String = "travel.hospitality"
  val heading: String = "Hospitality: gain 1 favor from which bank?"
  val emptyBank: String = "This bank is empty"

  def forCatalog(catalog: ExecutableCatalog): Hospitality =
    new Hospitality(catalog)

  private def stocked(ready: ReadyGame, suit: Suit): Boolean =
    ready.banks.favor.getOrElse(suit, 0) > 0

  private def option(ready: ReadyGame, suit: Suit): DecisionOption =
    val shown = DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))
    if stocked(ready, suit) then shown
    else DecisionOption.Unavailable(shown, OptionReason.Written(emptyBank))

  private def chosen(pending: PendingTree): Option[Suit] =
    PowerAnswers.one(pending, decisionId).collect {
      case DecisionOptionRef.FavorBank(suit) => suit }

  /** 1 favor from the bank `bank` names, read when the walk reaches it. */
  private def gain(guest: PlayerId, bank: PendingTree => Option[Suit])
      : Operation = BuildOps((ready, pending) =>
    bank(pending).toRight(PowerAnswers.missing(decisionId)).map(suit =>
      if stocked(ready, suit) then
        Vector[CoreOperation](Gain.Favor(guest, suit, Favor))
      else Vector.empty))

  /** Whether `player` has Hospitality faceup among their advisers. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player)
      .exists(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) =>
          card == HospitalityCard.id
        case _ => false
      })
```

Register it in `TravelModifiers.forCatalog`, before `Vector(Tyrant)`: `Vector(Hospitality.forCatalog(catalog)) ++`. Add `HospitalityCard` to the `oathdigital.gameplay.powers.travel` import in `NewFoundations.scala`, in alphabetical order.

- [ ] **Step 4: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.travel.* oathdigital.application.gamelog.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.catalog.* oathdigital.gameplay.cards.* oathdigital.gameplay.Travel*"`
Expected: PASS. If the first test cannot park on `travel.hospitality`, or the coast drops out of the destinations, stop and ask the product owner (see the task's opening note).

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/travel/Hospitality.scala src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala src/main/scala/oathdigital/gameplay/powers/travel/TravelModifiers.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/travel/HospitalitySuite.scala src/test/scala/oathdigital/application/gamelog/HospitalityLogSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(batch8): Hospitality gains favor after Travel"
```

---

### Task 3: Ward of Silence flips a player's secrets facedown

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/action/WardOfSilence.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `WardOfSilenceCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/TargetPowers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Test: `src/test/scala/oathdigital/gameplay/powers/action/WardOfSilenceSuite.scala`

**Interfaces:**
- Consumes: `PaidAction`, `PowerAccess.pawnSite`, `Costs.affordable`, `PowerAnswers.one`, `PowerAnswers.amount`, `NoteSupport.answer`, `NoteSupport.board`.
- Produces: `WardOfSilence` (case object) with `targetDecisionId`, `amountDecisionId`, `noFaceupSecrets`, `flipped`; `WardOfSilenceCard`.

- [ ] **Step 1: Write the failing tests**

Create `WardOfSilenceSuite.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{CatalogNames, Look, ParkedNode, PhasePowerHarness,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

/** Ward of Silence: X secrets placed on the card, and X of a player's
  * faceup secrets flipped facedown. */
class WardOfSilenceSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val ward = CatalogNames.denizen("Ward of Silence")
  private val source = DecisionOptionRef.Denizen(ward)

  /** p1's Act at Ancient City holding Ward of Silence as an adviser and
    * `own` faceup secrets. p2 stands there with `targetFaceUp` faceup and 1
    * facedown secret; p3 stands there with 2 facedown secrets only. */
  private def wardHeldWithP2AndP3AtTheSite(own: Int = 3,
      targetFaceUp: Int = 2): ReadyGame =
    Table.start.turn(p1, Phase.Act).adviser(p1, ward).secrets(p1, own)
      .pawn(p2, "Ancient City").secrets(p2, targetFaceUp, 1)
      .pawn(p3, "Ancient City").secrets(p3, 0, 2).ready

  /** The options of the decision the use is parked on. */
  private def parkedOptions(t: OathTransition): Vector[DecisionOption] =
    ParkedNode.of(t.state, catalog,
      phasePowers = PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) =>
        decide.query match
          case DecisionQuery.ChooseOne(options, _, _) => options
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a parked decision, got $other")

  /** Why the Act menu shows Ward of Silence unavailable, if it does. */
  private def menuReason(ready: ReadyGame): Option[OptionReason] =
    PhaseMenu.offers(PhaseMenu.Menu.Act, catalog, ready, p1,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty)
      .map(_.option).collectFirst {
        case DecisionOption.Unavailable(shown, reason)
            if shown.ref == source => reason
      }

  private def secretsOf(ready: ReadyGame, id: PlayerId): (Int, Int) =
    (Look(ready).faceUpSecrets(id), Look(ready).faceDownSecrets(id))

  private def targeted(ready: ReadyGame): OathTransition =
    val t = accepted(use(ready, WardOfSilence, source))
    accepted(answer(t, p1, WardOfSilence.targetDecisionId,
      pick(DecisionOptionRef.Player(p2))))

  test("it offers the players at the site, one with no faceup secret " +
      "unavailable, and places nothing yet"):
    val t = accepted(use(wardHeldWithP2AndP3AtTheSite(), WardOfSilence,
      source))
    assert(awaits(t, WardOfSilence.targetDecisionId))
    assertEquals(parkedOptions(t), Vector[DecisionOption](
      DecisionOption.Player(DecisionOptionRef.Player(p2)),
      DecisionOption.Unavailable(
        DecisionOption.Player(DecisionOptionRef.Player(p3)),
        OptionReason.Written(WardOfSilence.noFaceupSecrets))))
    assertEquals(Look(after(t)).tokensOn(ward), Tokens(0, 0))

  test("X secrets go on the card as X of the target's faceup secrets " +
      "flip facedown"):
    val ready = wardHeldWithP2AndP3AtTheSite()
    val started = accepted(use(ready, WardOfSilence, source))
    val chose = accepted(answer(started, p1, WardOfSilence.targetDecisionId,
      pick(DecisionOptionRef.Player(p2))))
    assert(awaits(chose, WardOfSilence.amountDecisionId))
    val done = accepted(answer(chose, p1, WardOfSilence.amountDecisionId,
      ChooseAmountAnswer(2)))
    val end = after(done)
    assertEquals(Look(end).tokensOn(ward), Tokens(0, 2))
    assertEquals(secretsOf(end, p1), (1, 0))
    assertEquals(secretsOf(end, p2), (0, 3))
    val events = started.events ++ chose.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)

  test("X is bounded by the smaller of the two faceup counts"):
    // The target's two faceup secrets bound X under the user's three.
    val byTarget = targeted(wardHeldWithP2AndP3AtTheSite())
    assertEquals(answer(byTarget, p1, WardOfSilence.amountDecisionId,
      ChooseAmountAnswer(3)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision ${WardOfSilence.amountDecisionId} amount 3 is " +
          "outside 1..2")))
    // The user's one faceup secret bounds X under the target's two.
    val byUser = targeted(wardHeldWithP2AndP3AtTheSite(own = 1))
    assertEquals(answer(byUser, p1, WardOfSilence.amountDecisionId,
      ChooseAmountAnswer(2)).left.toOption,
      Some(OathViolation.InvalidEventOrder(
        s"decision ${WardOfSilence.amountDecisionId} amount 2 is " +
          "outside 1..1")))

  test("its line names the target and the secrets flipped"):
    val done = accepted(answer(targeted(wardHeldWithP2AndP3AtTheSite()), p1,
      WardOfSilence.amountDecisionId, ChooseAmountAnswer(2)))
    assertEquals(NoteText.said(WardOfSilence, done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"Flipped 2 secrets on ${p2.value}'s board facedown.",
        covers = false)))

  test("it is unavailable without a faceup secret, with nobody at the " +
      "site holding one, or while its card holds a secret"):
    val unusable = Vector(
      wardHeldWithP2AndP3AtTheSite(own = 0),
      wardHeldWithP2AndP3AtTheSite(targetFaceUp = 0),
      Table.from(wardHeldWithP2AndP3AtTheSite()).tokens(ward, secrets = 1)
        .ready)
    unusable.foreach { ready =>
      assert(!usableNow(ready).exists(_.power.id == WardOfSilence.id))
      assertEquals(menuReason(ready),
        Some(OptionReason.Written("Nothing to do now")))
    }
```

The amount refusal text follows `BloodPactSuite`'s. If `main` words it differently, copy the wording from `BloodPactSuite`'s first test.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.WardOfSilenceSuite"`
Expected: compile failure, `WardOfSilence` not found.

- [ ] **Step 3: Write the action**

Delete `WardOfSilenceCard` from `HoldingArcaneDenizens.scala`. Create `WardOfSilence.scala`:

```scala
package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object WardOfSilenceCard extends Denizen(DenizenId("202"), "Ward of Silence",
    Suit.Arcane):
  val power = PrintedPower(PowerId("denizen.ward-of-silence"),
    persistent = false, cost = Cost.free,
    text = "X [secret] **ACTION:** Choose a player whose pawn is at " +
      "your site. Flip X [secret] on their board facedown.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Ward of Silence (card 202), ACTION: "X [secret] Choose a player whose
  * pawn is at your site. Flip X [secret] on their board facedown."
  *
  * The printed cost is free, so the X secrets are paid inside the action,
  * onto the card, once X is known. The user chooses another player whose
  * pawn is at their site; one with no faceup secret is shown unavailable
  * with "No faceup secrets". X runs from one to the smaller of the user's
  * faceup secrets and the target's. The payment and the flip run in one
  * batch.
  *
  * It is usable when its card could take one secret (a faceup secret, and
  * the empty-card rule, which a free printed cost does not check) and
  * another player at the site has a faceup secret. Otherwise the Act menu
  * shows it unavailable with "Nothing to do now".
  *
  * Its line is the action's: "Flipped {2 secrets} on {Blue}'s board
  * facedown." It narrates both questions.
  */
case object WardOfSilence extends PaidAction(WardOfSilenceCard.power):
  val cardId: DenizenId = WardOfSilenceCard.id
  val targetDecisionId: String = "power.ward-of-silence.target"
  val amountDecisionId: String = "power.ward-of-silence.amount"
  val noFaceupSecrets: String = "No faceup secrets"
  /** "Flipped {n secrets} on {Blue}'s board facedown." */
  val flipped: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("Flipped "), NotePart.Arg(0), NotePart.Text(" on "),
    NotePart.Arg(1), NotePart.Text("'s board facedown.")))
  override def noteKeys: Vector[NoteKey] = Vector(flipped)
  override def narratedDecisions: Set[String] =
    Set(targetDecisionId, amountDecisionId)

  override def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean =
    Costs.affordable(ready, player, Location.OnCard(cardId),
      Cost(secret = 1)) && targets(ready, player).exists(faceUp(_) > 0)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askTarget(live, player)),
    Branch((live, pending) => askAmount(live, player, pending)),
    BuildOps((live, pending) => ward(live, player, pending)),
    Note(id, flippedNote(_, source)))))

  private def faceUp(player: PlayerState): Int = player.board.faceUpSecrets

  /** The other players whose pawns are at `actor`'s site. */
  private def targets(ready: ReadyGame, actor: PlayerId)
      : Vector[PlayerState] =
    PowerAccess.pawnSite(ready, actor).toVector.flatMap(site =>
      ready.game.current.players.filter(p => p.player != actor &&
        p.pawnSite.contains(site)))

  private def askTarget(ready: ReadyGame, actor: PlayerId)
      : Vector[Operation] =
    val found = targets(ready, actor)
    if !found.exists(faceUp(_) > 0) then Vector.empty
    else Vector(Decide(targetDecisionId, actor, DecisionQuery.ChooseOne(
      found.map(option), heading = Some(
        "Ward of Silence: flip secrets of a player at your site"))))

  private def option(target: PlayerState): DecisionOption =
    val shown = DecisionOption.Player(DecisionOptionRef.Player(target.player))
    if faceUp(target) > 0 then shown
    else DecisionOption.Unavailable(shown,
      OptionReason.Written(noFaceupSecrets))

  /** The answered target, while it has a faceup secret. */
  private def chosen(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Option[PlayerState] =
    PowerAnswers.one(pending, targetDecisionId).flatMap(ref =>
      targets(ready, actor).find(p =>
        DecisionOptionRef.Player(p.player) == ref && faceUp(p) > 0))

  /** The most X can be: the smaller of the two faceup counts. */
  private def most(ready: ReadyGame, actor: PlayerId, target: PlayerState)
      : Int = ready.game.current.players.find(_.player == actor)
    .fold(0)(user => math.min(faceUp(user), faceUp(target)))

  private def askAmount(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    chosen(ready, actor, pending).toVector.map(target =>
      Decide(amountDecisionId, actor, DecisionQuery.ChooseAmount(1,
        most(ready, actor, target), Some("Ward of Silence: place X " +
          "secrets on this card to flip X of their secrets facedown"),
        "Flip")))

  private def ward(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = for
    target <- chosen(ready, actor, pending)
      .toRight(PowerAnswers.missing(targetDecisionId))
    amount <- PowerAnswers.amount(pending, amountDecisionId)
      .toRight(PowerAnswers.missing(amountDecisionId))
    _ <- Either.cond(amount >= 1 && amount <= most(ready, actor, target), (),
      OathViolation.InvalidEventOrder(
        s"Ward of Silence cannot flip $amount secrets"))
  yield Vector[CoreOperation](
    PayCost(actor, Location.OnCard(cardId), Cost(secret = amount)),
    FlipSecrets(target.player, amount, SecretSide.FaceUp,
      SecretSide.FaceDown))

  private def faceDown(ready: ReadyGame, player: PlayerId): Int =
    NoteSupport.board(ready, player).fold(0)(_.faceDownSecrets)

  /** The secrets the step before flipped on the target's board. */
  private def flippedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    target <- NoteSupport.answer(states, targetDecisionId).collect {
      case DecisionOptionRef.Player(player) => player }
    step <- states.previous
    count = faceDown(step._2, target) - faceDown(step._1, target)
    if count > 0
  yield flipped(card, NoteArg.Amount(count, NoteUnit.Secret),
    NoteArg.Player(target))
```

Register it in `TargetPowers.forCatalog`, after `SleightOfHand`. Add `WardOfSilenceCard` to the `oathdigital.gameplay.powers.action` import in `NewFoundations.scala`, in alphabetical order.

- [ ] **Step 4: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.action.* oathdigital.gameplay.PhaseMenuSuite oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/action/WardOfSilence.scala src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala src/main/scala/oathdigital/gameplay/powers/action/TargetPowers.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/action/WardOfSilenceSuite.scala
git commit -m "feat(batch8): Ward of Silence flips a player's secrets facedown"
```

---

### Task 4: Rewrites inside a card play, and Vow of Wisdom

The engine half comes first. Its own tests pin it before Vow of Wisdom relies on it.

**Files:**
- Modify: `src/main/scala/oathdigital/model/Operation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/OperationRewrite.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfWisdom.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala` (remove `VowOfWisdomCard`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import)
- Test: `src/test/scala/oathdigital/gameplay/powerresolver/OperationRewriteSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/CardPlayRulesSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfWisdomSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`

**Interfaces:**
- Consumes: `OperationRewrite.ops`, `CardPlayProcedure.PlacementTree.adjust`.
- Produces: `oathdigital.model.RewritableComposite` with `rewritten(rewrite: Operation => Operation): Operation`; `CardPlayProcedure.PlacementBody` implementing it; `VowOfWisdom` (case object) with `Secrets: Int = 1`; `VowOfWisdomCard`.

- [ ] **Step 1: Write the failing engine tests**

In `OperationRewriteSuite.scala`, after `WindowlessComposite`, add:

```scala
/** A windowless composite that rebuilds itself with its children
  * rewritten, as a card play's body does. */
private final case class RebuiltComposite(
    override val children: Vector[Operation]) extends RewritableComposite:
  def rewritten(rewrite: Operation => Operation): Operation =
    RebuiltComposite(children.map(rewrite))
```

and, after the test "a node with its own window and a custom composite are left alone", add:

```scala
  test("a rewritable composite is entered by ops and by nodes, and rebuilt " +
      "with its children rewritten"):
    assertEquals(OperationRewrite.ops(foldingSequence,
      Vector(RebuiltComposite(Vector(gainOne))))(gainByMoment),
      Vector[Operation](RebuiltComposite(Vector(GainSupply(actor, 2)))))
    val build = BuildOps((_, _) => Right(Vector(gainOne)))
    val note = Note(PowerId("test.note"), _ => None)
    assertEquals(OperationRewrite.nodes(foldingSequence,
      Vector(RebuiltComposite(Vector(build))))(
      (_, _) => { case _: BuildOps => Vector(note) }),
      Vector[Operation](RebuiltComposite(Vector(note))))
```

In `CardPlayRulesSuite`, which imports `oathdigital.model._`, add this test after "two contributors compose in either order":

```scala
  test("a rewrite of the play's body survives a later adjust"):
    val tree = as[CardPlayProcedure.PlacementTree](
      build(Table.start.hand(p1, card).ready, p1, card))
    val marker = Note(PowerId("test.marker"), _ => None)
    val marked = tree.children.map {
      case body: RewritableComposite =>
        body.rewritten(child => Sequence(Vector(child, marker)))
      case other => other
    }
    val body = as[CardPlayProcedure.PlacementBody](
      tree.adjust(marked)(_.limitAdvisers(2)).head)
    assertEquals(body.rules.adviserLimit, 2)
    assert(body.children.nonEmpty && body.children.forall {
      case Sequence(Vector(_, last), _) => last eq marker
      case _ => false
    })
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powerresolver.OperationRewriteSuite oathdigital.gameplay.CardPlayRulesSuite"`
Expected: compile failure, `RewritableComposite` not found.

- [ ] **Step 3: The trait and the descent**

In `model/Operation.scala`, after `trait Operation`, add:

```scala
/** A custom composite that plans its children from inputs it keeps, so it
  * cannot be rebuilt from new children alone (a card play's body).
  * `rewritten` rebuilds it with each child passed through `rewrite`, and
  * the rebuilt node keeps the rewrite whenever it plans its children
  * again. `OperationRewrite` descends it unless it carries a window of its
  * own.
  */
trait RewritableComposite extends Operation:
  def rewritten(rewrite: Operation => Operation): Operation
```

In `OperationRewrite.scala`:

1. In the object's doc, replace `It descends through `Sequence`, `Repeat`, `Branch` and `BuildOps`,` with `It descends through `Sequence`, `Repeat`, `Branch`, `BuildOps` and a `RewritableComposite`,`, re-wrapped.
2. In `ops`' doc, replace `only the walker's own composites are descended.` with `only the walker's own composites and a `RewritableComposite` are descended.`
3. In `nodes`' doc, after `Sequence` and `Repeat` are descended, never matched.`, add ` A `RewritableComposite` it does not match is entered.`
4. In `Ops.at`, before `case other => other`, add:

```scala
      case composite: RewritableComposite =>
        composite.rewritten(at(ready, pending))
```

5. In `Nodes.at`, before `case other =>`, add:

```scala
      case composite: RewritableComposite =>
        matcher(ready, pending).lift(composite).fold[Operation](
          composite.rewritten(at(ready, pending)))(placed)
```

Add `RewritableComposite` to the file's `oathdigital.model` import.

- [ ] **Step 4: The card play's body**

In `CardPlayProcedure.scala`, replace `PlacementBody` with:

```scala
  /** The placement subtree planned under one set of [[CardPlayRules]]. A
    * rewrite of its children joins the planning, so a later `adjust` keeps
    * it. */
  final class PlacementBody private[cardplay](val rules: CardPlayRules,
      childrenAt: CardPlayRules => Vector[Operation])
      extends RewritableComposite:
    override val children: Vector[Operation] = childrenAt(rules)
    private[cardplay] def adjust(change: CardPlayRules => CardPlayRules)
        : PlacementBody = new PlacementBody(change(rules), childrenAt)
    def rewritten(rewrite: Operation => Operation): Operation =
      new PlacementBody(rules, childrenAt.andThen(_.map(rewrite)))
```

In `PlacementTree`, replace `override val children: Vector[Operation] = childrenAt(CardPlayRules.default)` with:

```scala
    override val children: Vector[Operation] =
      Vector(new PlacementBody(CardPlayRules.default, childrenAt))
```

In `PlacementTree`'s doc, replace ``children` is the play under the default rules.` with ``children` is the play under the default rules, in one body, so a rewrite of the body and an [[adjust]] compose in either order.`, re-wrapped. Import `RewritableComposite` if the file does not import `oathdigital.model._`.

- [ ] **Step 5: Run the whole server suite**

Every card play's tree now has one more level, so run everything.

Run: `./sbtw test`
Expected: PASS, with the two new tests. A test that pins a card play's node path or parked position fails because of the extra level. Update the path in that test, and name the test in your report. Any other failure means the body broke something: fix it before going on.

- [ ] **Step 6: Commit the engine half**

```bash
git add src/main/scala/oathdigital/model/Operation.scala src/main/scala/oathdigital/gameplay/powerresolver/OperationRewrite.scala src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala src/test/scala/oathdigital/gameplay/powerresolver/OperationRewriteSuite.scala src/test/scala/oathdigital/gameplay/CardPlayRulesSuite.scala
git commit -m "feat(batch8): operation rewrites reach into a card play's body"
```

Add to that commit any test whose node path you updated in Step 5.

- [ ] **Step 7: Write the failing Vow of Wisdom tests**

In `PowerKindsCatalogSuite`, add `"denizen.vow-of-wisdom"` to the end of `persistentRules`.

Create `VowOfWisdomSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap

/** Vow of Wisdom (card 203): its holder's play to a site gains 1 secret and
  * no favor. */
class VowOfWisdomSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, park}

  private val actor = Table.p1
  private val vow = CatalogNames.denizen("Vow of Wisdom")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val home = CatalogNames.site("Ancient City")
  private val powers = WalkerPowers(Vector(VowOfWisdom))
  private val toSite = DecisionOptionRef.SiteSlot(home, 0)

  /** p1's Act at Ancient City, which holds no card, with Wrestlers in hand,
    * the Order bank holding `orderFavor`, and the advisers `held` adds. */
  private def wrestlersInHandAtEmptyAncientCity(orderFavor: Int)(
      held: Table => Table): ReadyGame =
    held(Table.start.turn(actor, Phase.Act).pawn(actor, home)
      .hand(actor, wrestlers).bankFavor(Suit.Order, orderFavor)).ready

  /** Wrestlers played from hand to Ancient City's first slot. */
  private def playedToSite(ready: ReadyGame): WalkerOutcome.Finished =
    val tree = build(ready, actor, wrestlers)
    Unwrap.as[WalkerOutcome.Finished](answer(ready, tree,
      park(ready, tree, powers), powers,
      CardPlayProcedure.placementDecisionId(wrestlers), toSite, actor))

  private def secretsOf(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(actor) + Look(ready).faceDownSecrets(actor)

  test("a play to a site gains a secret and no favor"):
    val ready = wrestlersInHandAtEmptyAncientCity(orderFavor = 3)(
      _.adviser(actor, vow))
    val done = playedToSite(ready)
    assertEquals(secretsOf(done.treeless), secretsOf(ready) + 1)
    assertEquals(Look(done.treeless).favor(actor), Look(ready).favor(actor))
    assertEquals(Look(done.treeless).bankFavor(Suit.Order), 3)
    assertWireRoundTrips(done.events)

  test("with the suit bank empty, the play still gains a secret"):
    val ready = wrestlersInHandAtEmptyAncientCity(orderFavor = 0)(
      _.adviser(actor, vow))
    val done = playedToSite(ready)
    assertEquals(secretsOf(done.treeless), secretsOf(ready) + 1)
    assertEquals(Look(done.treeless).favor(actor), Look(ready).favor(actor))

  test("without the Vow faceup, the play gains the favor"):
    val unheld = wrestlersInHandAtEmptyAncientCity(orderFavor = 3)(identity)
    val facedown = wrestlersInHandAtEmptyAncientCity(orderFavor = 3)(
      _.adviser(actor, vow, facedown = true))
    Vector(unheld, facedown).foreach { ready =>
      val done = playedToSite(ready)
      assertEquals(Look(done.treeless).favor(actor),
        Look(ready).favor(actor) + 1)
      assertEquals(secretsOf(done.treeless), secretsOf(ready))
      assertEquals(Look(done.treeless).bankFavor(Suit.Order), 2)
    }
```

If the play to Ancient City asks a further question on `main` (the Homeland rule's discard, for example), answer it with no discard in `playedToSite`. Then say so in your report.

- [ ] **Step 8: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.VowOfWisdomSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: compile failure, `VowOfWisdom` not found.

- [ ] **Step 9: Write the rule**

Delete `VowOfWisdomCard` from `HoldingArcaneDenizens.scala`. Create `VowOfWisdom.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.{AdviserOnly, Denizen, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OperationRewrite, PowerCtx, Transform}
import oathdigital.model._

object VowOfWisdomCard extends Denizen(DenizenId("203"), "Vow of Wisdom",
    Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-wisdom"),
    persistent = true, cost = Cost.free,
    text = "You **must** gain [secret] instead of [favor] when you play " +
      "to a site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Vow of Wisdom (card 203, adviser-only), a persistent rule of a faceup
  * adviser: "You must gain secret instead of favor when you play to a
  * site."
  *
  * While its holder has it faceup, a rewrite at `SearchPlayAdviser` follows
  * the holder's `Play` to a site with a gain of 1 secret, and drops the
  * play's favor gain. The secret is gained whether or not the card's suit
  * bank holds favor. There is no choice. A WHEN PLAYED power's own gain
  * sits under its own window, so the rewrite leaves it alone.
  *
  * It writes no line: the play's generic line reads "Gained 1 secret".
  */
case object VowOfWisdom extends ContributingPower:
  val cardId: DenizenId = VowOfWisdomCard.id
  val id: PowerId = VowOfWisdomCard.power.id
  val Secrets: Int = 1
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      OperationRewrite.ops(ctx, children)((_, _) => {
        case play @ Play(_, _, Location.Site(_), _, _) =>
          Vector[CoreOperation](play,
            Gain.Secrets(ctx.activePlayer, Secrets))
        case Gain.Favor(player, _, _) if player == ctx.activePlayer =>
          Vector.empty
      }))))

  override def applicable(ctx: PowerCtx): Boolean =
    ctx.state.game.current.players.find(_.player == ctx.activePlayer)
      .exists(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == cardId
        case _ => false
      })
```

Register it in `CardPlayTriggers.forCatalog`, last: `Vector(SacredGround) ++ Vector(VowOfWisdom)`. In that object's doc, replace `the triggers that reward a play and the rules that forbid one` with `the triggers that reward a play and the rules that change or forbid one`. Add `VowOfWisdomCard` to the `oathdigital.gameplay.powers.cardplay` import in `NewFoundations.scala`, in alphabetical order.

- [ ] **Step 10: Run the tests**

Run: `./sbtw "testOnly oathdigital.gameplay.CardPlay* oathdigital.gameplay.powers.cardplay.* oathdigital.gameplay.powers.search.* oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.catalog.* oathdigital.gameplay.cards.*"`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/cardplay/VowOfWisdom.scala src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/cardplay/VowOfWisdomSuite.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala
git commit -m "feat(batch8): Vow of Wisdom gains a secret for a play to a site"
```

---

### Task 5: Gates and roadmap

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status)

- [ ] **Step 1: Run the gates**

Run, in order:

1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server count is the baseline plus 17, and the frontend count is the baseline. If a count differs, find out why before going on.

- [ ] **Step 2: Mark the slice done**

In `docs/ROADMAP.md`, under "### Phase - Catalog batch 8", change `- [ ] **1. No new engine:** Ward of Silence, Hospitality, Vow of Wisdom.` to `- [x] **1. No new engine:** Ward of Silence, Hospitality, Vow of Wisdom.` In the "## Now" section, replace `**Phase - Catalog batch 8** is designed and starts with its slice 1.` with `**Phase - Catalog batch 8** is under way: slice 1 is done, and slice 2 is next.`

In the design's **Status** paragraph, replace `Slice 1 is planned.` with `Slice 1 is done.` Re-wrap the paragraph to 78 characters.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md
git commit -m "docs(batch8): slice 1 done"
```
