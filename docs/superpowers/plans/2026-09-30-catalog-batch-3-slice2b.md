# Catalog Batch 3, Slice 2b (Triggers, Rest, Wake and Protections) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Insomnia (97), Quartermaster (258), Saddle Makers (142) and Lost Tongue (157), with the refactor P4, and correct the Circlet of Command (R15) and the Forgotten Vault (75).

**Architecture:**

- This is the second half of the spec's slice 2. Slice 2a (`2026-09-30-catalog-batch-3-slice2a.md`) holds the Muster, Trade and Search modifiers, and runs first.
- **P4.** Silver Tongue's adviser limit becomes a class, `HolderAdviserLimit`, parameterised by card. Silver Tongue and Insomnia each hold one, and `AdviserLimit.of` takes the lowest limit. `PlacementTree.adjust` is fixed so that a second limit keeps the first one's work.
- Insomnia's REST and Quartermaster's WAKE are `PhasePower`s, as Silver Tongue's REST and Marble Fountains' WAKE are.
- Saddle Makers is a persistent rule on the faceup card-play hook, as Gossip and Book Binders are.
- Lost Tongue hides the holder's relics and banners at a Raid's targets and at a Conspiracy's targets, and registers an operation restriction that refuses a `Take` of them. Before it, a `Take` gains `leaving` children, so that a refused banner `Take` skips the banner's leaving operations with it.
- The Circlet of Command and the Forgotten Vault stop narrowing a Challenge and a Conspiracy.

**Engine changes.** The spec's bar allows none, but the product owner chose two on 2026-09-30:

- `Take` gains a `leaving` field, with an optional codec field.
- `PlacementTree.adjust` keeps the nodes an earlier contributor appended.

Neither adds an operation, window, query, option kind, `NoteArg` kind, protocol message or frontend change.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change.

**Spec:** `docs/superpowers/specs/2026-09-29-catalog-batch-3-design.md` ("Powers-side refactors", "Prerequisite", "Log lines", "Slice 2", "Testing", "Verify at plan time"), with the per-card rulings in `docs/superpowers/specs/2026-09-29-catalog-batch-3-rulings.md` ("Rules that apply to the whole batch", "Slice 2" and "Target protections corrected"). Read both before starting, and the "Take for Challenge and Conspiracy" section of `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md`. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails the build. Each file below lists the imports it needs.
- Production Scala files stay at or under 800 lines (`BackendArchitectureSuite`, `scripts/check-architecture.py`).
- Import rules:
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - No power name appears in walker sources.
- Never touch the live database `var/oathdigital`.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Re-check `git log --oneline -1` before any amend, reset or rebase.
- Work in a git worktree:
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- A note template starts with an argument or a capital letter (`PowerNoteCatalogSuite` checks this).
- Log lines, exactly (spec, "Log lines", "Slice 2"):
  - Insomnia (REST): "Insomnia: {Red} gained {1} secret." Key `used`, `NoteSupport.gainedKey(NoteKey.Used)`, covering the generic gain line, as Tutor's line does.
  - Quartermaster (WAKE): "Quartermaster: {Red} gained {n} Supply." Key `used`, as Wayside Inn's line. On a full track it writes nothing, and the log falls back to "Used Quartermaster".
  - Saddle Makers: "Saddle Makers: {Blue} gained {n} favor from {the Nomad bank}." Key `gained`, `NoteSupport.gainedFromKey`, covering the generic gain line, as Gossip's line does. An empty bank writes nothing.
  - Lost Tongue, for each option it hides at a Raid's targets: "Lost Tongue: {Blue}'s banners and relics cannot be targeted." Key `shielded`, the Circlet's wording.
  - **No line:** Insomnia's adviser limit, as for Silver Tongue. Lost Tongue's refused `Take`, and the banners it hides from a Conspiracy: a refused operation is never offered.
- Batch rulings that every task applies (rulings, "Rules that apply to the whole batch"):
  - **Cards a player rules.** A player rules their faceup advisers and the denizens and edifices, on either face, at the sites they rule (`RuledCards.of`, P1).
  - **Suits.** Only faceup cards have a suit. Relics and banners have none.
  - **Amounts are best effort.** A gain resolves to what its source holds.
- Baseline: `main` after slice 2a passes 2298 server and 466 frontend tests. Record the count from your first full `./sbtw test` run in the worktree and compare against it at the end.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.

## Rulings made at plan time

These settle what the spec and rulings leave open. Each one names what it costs if it is wrong.

1. **No registration pins.** As in slices 1 and 2a. Every suite below runs through the production catalogs, so an unregistered power fails its suite.
2. **The Insomnia stub stays.** `RestPowers.Insomnia` stays, for the reason slice 2a gives: removing a reviewed stub makes the legacy resolver reject the card as unclassified. Once `PhasePowerCatalog` covers Insomnia, `PowerImplementationStatus` reports it implemented, and the legacy Rest stops recording an ignored-rule diagnostic for it. `RestSuite`'s diagnostic test therefore drops Insomnia, and Naysayers takes its place in the site-card check (Task 1).
3. **`PlacementTree.adjust` keeps appended nodes.** A contributor that appends a node after the placement body hands the next contributor two children: Silver Tongue appends its limit check.
   - Today `adjust` then starts a new body from the default rules. That drops every earlier change, such as the Mob's discard permission, and the earlier check.
   - With one limit card this never happens, because the transforms fold in source-key order and the Mob and Crop Rotation sort before Silver Tongue.
   - Insomnia sorts before Silver Tongue, so a holder of both would lose the Mob's or Crop Rotation's permission.
   
   `adjust` now changes the body wherever it is and keeps the other nodes. Task 1 pins it. If it is wrong, two limit cards and a discard permission conflict.
4. **`Take.leaving`.** The product owner chose this on 2026-09-30.
   - `Take` gains `leaving: Vector[CoreOperation] = Vector.empty`, and its children become `leaving :+ move`.
   - A Raid's and a Conspiracy's banner `Take` carry the banner's favor return or secret burn there. A restriction that refuses the `Take` refuses its leaving operations with it, whether the `Take` runs in a `BuildOps` batch or alone in a tree.
   - The codec writes `leaving` only when it is non-empty, so a `Take` without it encodes as before and old journals decode unchanged.
   - No log line reads those leaving operations today: Campaign losses count burnt favor from a board only.
5. **Conspiracy hides the protected banners.** The product owner chose this on 2026-09-30.
   - Lost Tongue narrows a played Conspiracy's targets to drop the holder's banners, through `ConspiracyTargets.narrowed`, which therefore stays.
   - It writes no line. A Conspiracy left with no target asks nothing.
   - The holder's relics stay offered, because a Conspiracy moves a relic by a `Give`, which Lost Tongue allows.
   - The `Take` restriction still guards every other `Take`, including a Raid's spoils when the attacker's rule of a nomad card changed between the target choice and the transfer.
6. **Lost Tongue reads "rules a nomad card" when it applies:** at the target fold and when the `Take` runs, through `RuledCards.of`. Bandits take nothing, so they are never refused.
7. **Quartermaster's reach** (spec, "Verify at plan time"). `PhasePowerProcedure.sources` offers a card at any site the player rules, wherever the pawn is (`PowerAccess.accessible`), so the ruler of its site is offered it. A player whose pawn stands there without ruling it is offered the source, but `usable` refuses it. Task 2 pins both.
8. **Saddle Makers' node count** (spec, "Verify at plan time"). It appends the gain and its note whenever another player plays a nomad or order denizen faceup, whatever the banks hold, as Gossip does.
9. **Insomnia's REST is always usable.** A secret gain is best effort, and the engine enforces once per turn.

## File Structure

Production, under `src/main/scala/oathdigital/`:

| File | Change | Responsibility |
|---|---|---|
| `gameplay/powers/rest/HolderAdviserLimit.scala` | Create | P4: "You can only have two advisers", for one card |
| `gameplay/powers/rest/SilverTongue.scala` | Modify | Uses `HolderAdviserLimit` |
| `gameplay/powers/rest/Insomnia.scala` | Create | The limit, and REST: gain 1 secret |
| `gameplay/powers/AdviserLimit.scala` | Modify | The lowest limit of Silver Tongue and Insomnia |
| `gameplay/actions/cardplay/CardPlayProcedure.scala` | Modify | `PlacementTree.adjust` keeps appended nodes |
| `gameplay/powers/wake/Quartermaster.scala` | Create | WAKE: the ruler of its site gains 1 Supply |
| `gameplay/powers/cardplay/SaddleMakers.scala` | Create | 2 favor when another player plays a nomad or order card faceup |
| `model/CoreOperations.scala` | Modify | `Take.leaving` |
| `serialization/WalkerOperationCodec.scala` | Modify | Encodes and decodes `leaving` |
| `gameplay/actions/campaign/CampaignRaid.scala` | Modify | The banner's leaving operations go into its `Take` |
| `gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala` | Modify | The same |
| `application/gamelog/CampaignLines.scala` | Modify | `Take` patterns gain a field |
| `gameplay/powers/targeting/LostTongue.scala` | Create | Hides and refuses the holder's relics and banners |
| `gameplay/powers/targeting/CircletOfCommand.scala` | Modify | Campaign targets only |
| `gameplay/powers/targeting/ForgottenVault.scala` | Modify | Campaign targets only |
| `gameplay/powers/targeting/ConspiracyTargets.scala` | Modify | Doc: Lost Tongue is its only user |
| `gameplay/powers/targeting/TargetProtections.scala` | Modify | Registers Lost Tongue |
| `gameplay/powers/PhasePowerCatalog.scala` | Modify | Registers Insomnia and Quartermaster |
| `gameplay/powers/WalkerPowerCatalog.scala` | Modify | Registers Insomnia's limit |
| `gameplay/powers/cardplay/CardPlayTriggers.scala` | Modify | Registers Saddle Makers |
| `gameplay/powers/wake/HornedMask.scala` | Modify | Doc: the limit names Insomnia |

Tests, under `src/test/scala/oathdigital/`:

| File | Change |
|---|---|
| `gameplay/powers/rest/InsomniaSuite.scala` | Create (Task 1) |
| `gameplay/RestSuite.scala` | Modify (Task 1) |
| `gameplay/powers/wake/QuartermasterSuite.scala` | Create (Task 2) |
| `gameplay/powers/cardplay/SaddleMakersSuite.scala` | Create (Task 3) |
| `gameplay/OperationRestrictionsSuite.scala`, `serialization/GameEventWireSuite.scala` | Modify (Task 4): one new test each |
| `gameplay/ChallengeProcedureSuite.scala`, `gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala` | Modify (Task 4) |
| `gameplay/powers/targeting/LostTongueSuite.scala` | Create (Task 5) |
| `gameplay/powers/targeting/CircletOfCommandSuite.scala`, `ForgottenVaultSuite.scala` | Modify (Task 6) |

Docs: `docs/ROADMAP.md` and `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md` (Task 7).

---

### Task 1: P4 and Insomnia

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/rest/HolderAdviserLimit.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/rest/Insomnia.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala:76-80`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala:26-27`
- Modify: `src/test/scala/oathdigital/gameplay/RestSuite.scala:256-284`
- Test: `src/test/scala/oathdigital/gameplay/powers/rest/InsomniaSuite.scala`

**Interfaces:**
- Consumes: `CardPlayProcedure.PlacementTree` (`card: WorldCardId`, `adjust(children)(change)`), `PlacementRules.limitAdvisers(limit)`, `PlacementRules.limitFaceupAdvisers(limit)`, `NoteSupport.gainNote`, `NoteSupport.gainedKey`, `NoteSupport.secrets`.
- Produces: `HolderAdviserLimit(cardId: DenizenId, limit: Int, name: String)` with `limitFor(ready, player): Option[Int]` and `contribution: Contribution`; `Insomnia.id`, `Insomnia.forCatalog(catalog): Option[Insomnia]`, `Insomnia.HolderLimit` (2), `Insomnia#limitFor`. `SilverTongue.limitFor` and `SilverTongue.HolderLimit` keep their signatures.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/rest/InsomniaSuite.scala`:

```scala
package oathdigital.gameplay.powers.rest

import oathdigital.gameplay.{OathRules, PlacementFixture}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{AdviserLimit, NoteText, PhasePowerCatalog,
  SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.banner.BannerFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class InsomniaSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  /** The automatic production powers, as a command with no modifier walks. */
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)
  private val insomnia = CatalogNames.denizen("Insomnia")
  private val tongue = CatalogNames.denizen("Silver Tongue")
  private val use = ActionRef.UsePower(Insomnia.id)
  private val source = DecisionOptionRef.Denizen(insomnia)
  private val plain = SearchFixture.denizensOf(Suit.Hearth)

  /** p1's Rest, holding Insomnia as an adviser, faceup unless `facedown`. */
  private def resting(facedown: Boolean = false): ReadyGame =
    Table.start.turn(p1, Phase.Rest).adviser(p1, insomnia, facedown = facedown)
      .ready

  private def secrets(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)

  /** Plays `card` from p1's hand and answers its placement with `button`. */
  private def placed(ready: ReadyGame, card: DenizenId, button: String)
      : (Operation, WalkerOutcome.Parked) =
    val tree = PlacementFixture.build(ready, p1, card)
    val parked = PlacementFixture.park(ready, tree, powers)
    (tree, PlacementFixture.answer(ready, tree, parked, powers,
      PlacementFixture.decisionId(card, "place"),
      DecisionOptionRef.Button(button), p1).asInstanceOf[WalkerOutcome.Parked])

  test("REST: its holder gains 1 secret, once per turn"):
    val ready = resting()
    val used = rules.startWalker(Ready(ready), use, p1, Vector.empty,
      Vector(source)).toOption.get
    assertEquals(secrets(used.state.asInstanceOf[Ready].value),
      secrets(ready) + 1)
    val ref = PowerUseRef(PowerTiming.Rest, PowerSourceRef.Card(insomnia),
      Insomnia.id)
    assertEquals(rules.startWalker(used.state, use, p1, Vector.empty,
      Vector(source)).left.toOption, Some(OathViolation.PowerAlreadyUsed(ref)))

  test("its line restates the gain, covering the generic one"):
    val used = rules.startWalker(Ready(resting()), use, p1, Vector.empty,
      Vector(source)).toOption.get
    assertEquals(NoteText.said(Insomnia.forCatalog(catalog).get, used.events),
      Vector(NoteText.Said(NoteKey.Used, s"${p1.value} gained 1 secret.",
        covers = true)))

  test("a facedown Insomnia offers no REST power and sets no limit"):
    val ready = resting(facedown = true)
    assert(!PhasePowerProcedure.usable(catalog, ready, p1,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty)
      .exists(_.power.id == Insomnia.id))
    assertEquals(AdviserLimit.of(catalog, ready, p1), AdviserLimit.Default)

  test("its holder may have only two advisers: a third faceup adviser needs " +
      "a discard, and the locked Insomnia cannot go"):
    val ready = Table.start.hand(p1, plain(0)).adviser(p1, insomnia)
      .adviser(p1, plain(1), facedown = true).ready
    val (tree, faceup) = placed(ready, plain(0), "adviser-faceup")
    assertEquals(PlacementFixture.options(ready, tree, faceup.tree, powers).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(plain(1))))
    assertEquals(AdviserLimit.of(catalog, ready, p1), Insomnia.HolderLimit)

  test("playing Insomnia faceup needs room under its own limit"):
    // Two advisers and a third would fit the default limit of three.
    val ready = Table.start.hand(p1, insomnia)
      .adviser(p1, plain(0), facedown = true)
      .adviser(p1, plain(1), facedown = true).ready
    val (tree, faceup) = placed(ready, insomnia, "adviser-faceup")
    assertEquals(PlacementFixture.options(ready, tree, faceup.tree, powers).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(plain(0)),
        DecisionOptionRef.Denizen(plain(1))))

  test("with Silver Tongue as well the limit stays 2, and both limits keep " +
      "the Mob's discard"):
    val ready = BannerFixture.holdingFavor(Table.start.hand(p1, plain(0))
      .denizen(plain(2), at = Table.homeOf(p1))
      .adviser(p1, tongue).adviser(p1, insomnia).ready)
    assertEquals(AdviserLimit.of(catalog, ready, p1), 2)
    val (tree, site) = placed(ready, plain(0), "site")
    assertEquals(PlacementFixture.options(ready, tree, site.tree, powers).head,
      CardPlayProcedure.noReplacement.ref)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.rest.InsomniaSuite"`
Expected: compile FAIL with "Not found: Insomnia".

- [ ] **Step 3: Create `HolderAdviserLimit`**

Create `src/main/scala/oathdigital/gameplay/powers/rest/HolderAdviserLimit.scala`:

```scala
package oathdigital.gameplay.powers.rest

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.model._

/** "You can only have two advisers", printed on an adviser: refactor P4 of
  * catalog batch 3, shared by Silver Tongue and Insomnia.
  *
  * It is a transform at `SearchPlayAdviser`. While `cardId` is faceup in the
  * acting player's advisers, it lowers that player's limit to `limit` in both
  * adviser orientations. While `cardId` is the card being played, it lowers
  * the faceup limit, so playing it faceup needs room under the limit. Either
  * way it checks the resulting area after the play. A limit only lowers, so
  * two such cards compose and the lowest holds.
  */
final case class HolderAdviserLimit(cardId: DenizenId, limit: Int,
    name: String):
  /** `limit`, when `player` holds `cardId` as a faceup adviser. */
  def limitFor(ready: ReadyGame, player: PlayerId): Option[Int] =
    ready.game.current.players.find(_.player == player)
      .filter(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == cardId
        case _ => false
      }).map(_ => limit)

  val contribution: Contribution = Transform((ctx, children) =>
    ctx.operation match {
      case tree: CardPlayProcedure.PlacementTree
          if limitFor(ctx.state, ctx.activePlayer).nonEmpty =>
        tree.adjust(children)(_.limitAdvisers(limit)) :+ guard(ctx.activePlayer)
      case tree: CardPlayProcedure.PlacementTree if tree.card == cardId =>
        tree.adjust(children)(_.limitFaceupAdvisers(limit)) :+
          guard(ctx.activePlayer)
      case _ => children
    })

  private def guard(actor: PlayerId): Operation = BuildOps((state, _) => {
    val count = state.game.current.players.find(
      _.player == actor).fold(0)(_.advisers.size)
    if limitFor(state, actor).forall(count <= _) then Right(Vector.empty)
    else Left(OathViolation.InvalidEventOrder(
      s"${actor.value} holds $name and can have only $limit advisers"))
  })
```

- [ ] **Step 4: Make Silver Tongue use it**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala` with:

```scala
package oathdigital.gameplay.powers.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Silver Tongue (card 92): "You can only have two advisers. REST: Take a
  * favor from a favor bank matching a card at your site."
  *
  * The REST power is a [[oathdigital.gameplay.powerresolver.PhasePower]]. The
  * adviser limit is a [[HolderAdviserLimit]], a registered transform at
  * `SearchPlayAdviser`, which Insomnia shares.
  */
final case class SilverTongue private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends PhasePower with ContributingPower:
  import SilverTongue._

  private val limit = HolderAdviserLimit(cardId, HolderLimit, "Silver Tongue")

  def id: PowerId = SilverTongue.id
  def timing: PowerTiming = PowerTiming.Rest
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = Vector(NoteSupport.took)

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = stocked(ready, player).nonEmpty

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    val choice = choiceDecisionId(ready, player)
    Right(Sequence(Vector(Branch((state, _) => stocked(state, player) match {
      case Vector(only) => Vector(take(player, _ => Right(only)))
      case several => Vector(
        Decide(choice, player, DecisionQuery.ChooseOne(several.map(suit =>
          DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
          heading = Some("Silver Tongue: take a favor from a bank"))),
        take(player, pending => pending.answered.collectFirst {
          case Answered(`choice`, DecisionAnswer.ChooseOneAnswer(
            DecisionOptionRef.FavorBank(suit)), _) => suit
        }.toRight(OathViolation.InvalidEventOrder(
          s"no Silver Tongue bank is recorded for $choice"))))
    }),
      Note(id, NoteSupport.tookNote(source, player), covers = true))))

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.SearchPlayAdviser -> Vector(limit.contribution))

  /** The adviser limit Silver Tongue sets on `player`: its holder, and only
    * while it is faceup.
    */
  def limitFor(ready: ReadyGame, player: PlayerId): Option[Int] =
    limit.limitFor(ready, player)

  /** Suits of faceup denizens and edifices at the player's pawn site whose
    * bank holds favor, in suit order.
    */
  private def stocked(ready: ReadyGame, player: PlayerId): Vector[Suit] =
    val current = ready.game.current
    val cards = current.players.find(_.player == player).flatMap(_.pawnSite)
      .flatMap(current.map.sites.get).toVector.flatMap(_.denizens.collect {
        case DenizenState(card, Orientation.FaceUp, _) => card: CardId
        case EdificeState(card, _, _) => card: CardId
      })
    val suits = cards.flatMap(catalog.suitOf(_)).toSet
    Suit.all.filter(suit => suits(suit) && ready.banks.favor.getOrElse(suit, 0) > 0)

  private def take(player: PlayerId,
      suit: PendingTree => Either[OathViolation, Suit]): Operation =
    BuildOps((_, pending) => suit(pending).map(bank => Vector(Move(
      Piece.Favor(1), PositionedLocation(Location.FavorBank(bank)),
      PositionedLocation(Location.PlayArea(player))))))

object SilverTongue:
  val id: PowerId = PowerId("denizen.silver-tongue")
  /** How many advisers the holder may have, in either orientation. */
  val HolderLimit: Int = 2
  def forCatalog(catalog: ExecutableCatalog): Option[SilverTongue] =
    catalog.denizenWithPower(id)
      .map(d => new SilverTongue(DenizenId(d.id.value), catalog))

  def choiceDecisionId(ready: ReadyGame, player: PlayerId): String =
    s"silver-tongue-${ready.game.current.tracks.round}-${player.value}"
```

- [ ] **Step 5: Create Insomnia**

Create `src/main/scala/oathdigital/gameplay/powers/rest/Insomnia.scala`:

```scala
package oathdigital.gameplay.powers.rest

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver._
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport}
import oathdigital.model._

/** Insomnia (card 97, adviser-only, locked): "You can only have two advisers.
  * REST: Gain [secret]."
  *
  * The limit is a [[HolderAdviserLimit]], exactly as Silver Tongue's is, so
  * playing Insomnia faceup needs room under the limit, and with Silver Tongue
  * as well the limit stays 2. The limit writes no line, as for Silver Tongue.
  *
  * The REST power is an optional
  * [[oathdigital.gameplay.powerresolver.PhasePower]], once per turn, which the
  * engine enforces. Its line, "{Red} gained 1 secret.", restates the gain in
  * place of the generic gain line, as Tutor's does.
  */
final case class Insomnia private (cardId: DenizenId)
    extends PhasePower with ContributingPower:
  private val limit = HolderAdviserLimit(cardId, Insomnia.HolderLimit,
    "Insomnia")

  def id: PowerId = Insomnia.id
  def timing: PowerTiming = PowerTiming.Rest
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = Vector(Insomnia.gained)

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = true

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    Gain.Secrets(player, Insomnia.Secrets),
    Note(id, NoteSupport.gainNote(Insomnia.gained, source, player,
      NoteUnit.Secret, NoteSupport.secrets), covers = true))))

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.SearchPlayAdviser -> Vector(limit.contribution))

  /** The adviser limit Insomnia sets on `player`: its holder, and only while
    * it is faceup.
    */
  def limitFor(ready: ReadyGame, player: PlayerId): Option[Int] =
    limit.limitFor(ready, player)

object Insomnia:
  val id: PowerId = PowerId("denizen.insomnia")
  /** How many advisers the holder may have, in either orientation. */
  val HolderLimit: Int = 2
  val Secrets: Int = 1
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)

  def forCatalog(catalog: ExecutableCatalog): Option[Insomnia] =
    CatalogCards.denizen(catalog, id).map(new Insomnia(_))
```

- [ ] **Step 6: Make `AdviserLimit.of` take the lowest limit**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala` with:

```scala
package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.PlacementRules
import oathdigital.gameplay.powers.rest.{Insomnia, SilverTongue}
import oathdigital.model._

/** How many advisers a player may hold, for a power that adds an adviser
  * outside card play (Horned Mask).
  *
  * Card play gets its limit from [[oathdigital.gameplay.actions.PlacementRules]],
  * which the [[oathdigital.gameplay.powers.rest.HolderAdviserLimit]] of Silver
  * Tongue and of Insomnia narrows. This reads the same facts as a read of
  * state, so the limit is defined once: the default is
  * `PlacementRules.DefaultAdviserLimit`, and the lowest limit a held card sets
  * wins.
  */
object AdviserLimit:
  val Default: Int = PlacementRules.DefaultAdviserLimit

  def of(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId): Int =
    (SilverTongue.forCatalog(catalog).flatMap(_.limitFor(ready, player))
      .toVector ++ Insomnia.forCatalog(catalog)
      .flatMap(_.limitFor(ready, player)).toVector)
      .minOption.getOrElse(Default)
```

In `src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala`, replace:

```scala
  * "Full" is the player's adviser limit, [[AdviserLimit.of]]: 3, or 2 for a
  * Silver Tongue holder.
```

with:

```scala
  * "Full" is the player's adviser limit, [[AdviserLimit.of]]: 3, or 2 for a
  * holder of Silver Tongue or Insomnia.
```

- [ ] **Step 7: Make `PlacementTree.adjust` keep appended nodes**

In `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala`, replace:

```scala
    def adjust(current: Vector[Operation])(
        change: PlacementRules => PlacementRules): Vector[Operation] =
      current match
        case Vector(body: PlacementBody) => Vector(body.adjust(change))
        case _ => Vector(new PlacementBody(change(PlacementRules.default),
          childrenAt))
```

with:

```scala
    def adjust(current: Vector[Operation])(
        change: PlacementRules => PlacementRules): Vector[Operation] =
      // An earlier contributor may have appended nodes after the body (an
      // adviser limit's check): change the body in place and keep them.
      if current.exists(_.isInstanceOf[PlacementBody]) then current.map {
        case body: PlacementBody => body.adjust(change)
        case other => other
      }
      else Vector(new PlacementBody(change(PlacementRules.default), childrenAt))
```

In the same file's doc comment of `PlacementTree`, replace:

```scala
    * the change it wants. Every contributor's change is applied to the same
    * rules, so contributors compose in any order, and the result is the play
    * planned under all of them.
```

with:

```scala
    * the change it wants. Every contributor's change is applied to the same
    * rules, so contributors compose in any order, and the result is the play
    * planned under all of them. A node a contributor appends after the body
    * is kept by the later ones.
```

- [ ] **Step 8: Register Insomnia**

In `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`:

- Change `import oathdigital.gameplay.powers.rest.SilverTongue` to `import oathdigital.gameplay.powers.rest.{Insomnia, SilverTongue}`.
- Change `PhasePowers(SilverTongue.forCatalog(catalog).toVector ++` to:

```scala
    PhasePowers(SilverTongue.forCatalog(catalog).toVector ++
      Insomnia.forCatalog(catalog).toVector ++
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`:

- Change `import oathdigital.gameplay.powers.rest.{LeagueTreatyContribution, SilverTongue}` to `import oathdigital.gameplay.powers.rest.{Insomnia, LeagueTreatyContribution, SilverTongue}`.
- Add `Insomnia.forCatalog(catalog) ++` on the line after `SilverTongue.forCatalog(catalog) ++`.
- In the doc comment, replace:

```scala
  * Silver Tongue's restriction is inert until Search walks
  * `SearchPlayAdviser`.
```

with:

```scala
  * Silver Tongue's and Insomnia's adviser limits are inert until Search walks
  * `SearchPlayAdviser`.
```

- [ ] **Step 9: Update `RestSuite`'s diagnostic test**

Insomnia's REST now runs as a phase power, so the legacy Rest records no ignored-rule diagnostic for it (ruling 2). In `src/test/scala/oathdigital/gameplay/RestSuite.scala`, in the test "each relevant Rest handler records fallback diagnostics without blocking", replace:

```scala
    val relevant = Set("denizen.vow-of-poverty", "denizen.naysayers",
      "denizen.insomnia")
```

with:

```scala
    // Insomnia's REST runs as a phase power, so it records no diagnostic.
    val relevant = Set("denizen.vow-of-poverty", "denizen.naysayers")
```

and replace:

```scala
    val handler = "denizen.insomnia"
```

with:

```scala
    val handler = "denizen.naysayers"
```

- [ ] **Step 10: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.rest.* oathdigital.gameplay.powers.AdviserLimitSuite oathdigital.gameplay.PlacementRulesSuite oathdigital.gameplay.RestSuite oathdigital.gameplay.powers.banner.* oathdigital.gameplay.powers.wake.HornedMaskSuite"`
Expected: PASS. 6 new tests in `InsomniaSuite`. `SilverTongueSuite`, `AdviserLimitSuite`, `PlacementRulesSuite`, `PeoplesFavorMobSuite` and `HornedMaskSuite` pass unchanged, and `RestSuite` passes with the edit above.

The last `InsomniaSuite` test fails without Step 7: the Mob's discard is not asked. If it fails with Step 7 in place, report it.

- [ ] **Step 11: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/rest/HolderAdviserLimit.scala src/main/scala/oathdigital/gameplay/powers/rest/Insomnia.scala src/main/scala/oathdigital/gameplay/powers/rest/SilverTongue.scala src/main/scala/oathdigital/gameplay/powers/AdviserLimit.scala src/main/scala/oathdigital/gameplay/powers/wake/HornedMask.scala src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/test/scala/oathdigital/gameplay/powers/rest/InsomniaSuite.scala src/test/scala/oathdigital/gameplay/RestSuite.scala
git commit -m "feat(powers): add Insomnia and share the adviser limit with Silver Tongue"
```

---

### Task 2: Quartermaster

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/wake/Quartermaster.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/wake/QuartermasterSuite.scala`

**Interfaces:**
- Consumes: `PhasePower`, `PowerAccess.ruledSites(ready, player): Set[SiteId]`, `NoteSupport.gainNote`, `NoteSupport.supply`.
- Produces: `Quartermaster` (a `case object`), `Quartermaster.id`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/wake/QuartermasterSuite.scala`:

```scala
package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

class QuartermasterSuite extends munit.FunSuite:
  private val quartermaster = CatalogNames.denizen("Quartermaster")
  private val source = DecisionOptionRef.Denizen(quartermaster)
  private val far = CatalogNames.site("Deep Woods")
  private val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  /** p1's Wake with `supply` Supply and Quartermaster at Deep Woods. p1 rules
    * Deep Woods with one warband unless `ruled` is false. p1's pawn stays at
    * Ancient City unless `pawnThere`. */
  private def staged(ruled: Boolean = true, pawnThere: Boolean = false,
      supply: Int = 3): ReadyGame =
    val table = Table.start.turn(p1, Phase.Wake).supply(p1, supply)
      .denizen(quartermaster, at = far)
    val held = if ruled then table.warbandsAt(far, p1, 1) else table
    (if pawnThere then held.pawn(p1, far) else held).ready

  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(Quartermaster.id), p1, Vector.empty, Vector(source))

  private def offered(ready: ReadyGame): Boolean = PhasePowerProcedure.usable(
    catalog, ready, p1, PhasePowerCatalog.default(catalog), WalkerPowers.empty)
    .exists(_.power.id == Quartermaster.id)

  test("its site's ruler gains 1 Supply, wherever their pawn is"):
    val ready = staged()
    assert(offered(ready))
    val used = use(ready).toOption.get.state.asInstanceOf[Ready].value
    assertEquals(Look(used).supply(p1), 4)

  test("it is once per turn"):
    val first = use(staged()).toOption.get.state.asInstanceOf[Ready].value
    val ref = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(quartermaster),
      Quartermaster.id)
    assert(first.game.current.turn.usedPowers.contains(ref))
    // The use may end Wake when it was Wake's only option: put the turn back
    // in Wake, its uses kept, to try again.
    val again = first.updateCurrent(c => c.copy(turn =
      c.turn.copy(phase = Phase.Wake)))
    assertEquals(use(again).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(ref)))

  test("a player who does not rule its site cannot use it, even with the " +
      "pawn there"):
    val ready = staged(ruled = false, pawnThere = true)
    assert(!offered(ready))
    assert(use(ready).isLeft)

  test("its line names the Supply gained"):
    assertEquals(NoteText.said(Quartermaster, use(staged()).toOption.get.events),
      Vector(NoteText.Said(NoteKey.Used, s"${p1.value} gained 1 Supply.",
        covers = false)))

  test("on a full Supply track it gains nothing and writes no line"):
    val done = use(staged(supply = 7)).toOption.get
    assertEquals(Look(done.state.asInstanceOf[Ready].value).supply(p1), 7)
    assertEquals(NoteText.said(Quartermaster, done.events), Vector.empty)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.QuartermasterSuite"`
Expected: compile FAIL with "Not found: Quartermaster".

- [ ] **Step 3: Create Quartermaster**

Create `src/main/scala/oathdigital/gameplay/powers/wake/Quartermaster.scala`:

```scala
package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

/** Quartermaster (card 258, site-only), WAKE: "If you rule this card, gain 1
  * Supply."
  *
  * An optional Wake power, once per turn, which the engine enforces, as Marble
  * Fountains is. A card at a site the player rules is in reach wherever their
  * pawn is, so the ruler of Quartermaster's site may use it from anywhere. A
  * player whose pawn stands there without ruling it may not. Its line reads
  * the Supply the track allowed, as Wayside Inn's does: a full track gains
  * nothing and writes nothing.
  */
case object Quartermaster extends PhasePower:
  val id: PowerId = PowerId("denizen.quartermaster")
  def timing: PowerTiming = PowerTiming.Wake
  val Supply: Int = 1
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = source match
    case DecisionOptionRef.Denizen(card) =>
      PowerAccess.ruledSites(ready, player).exists(site =>
        ready.game.current.map.sites.get(site)
          .exists(_.denizens.exists(_.id == card)))
    case _ => false

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    GainSupply(player, Supply),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
      NoteSupport.supply)))))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`:

- Change the wake import to:

```scala
import oathdigital.gameplay.powers.wake.{Hunger, MarbleFountains,
  Quartermaster, RiverSitePower}
```

- Change `Vector[PhasePower](WaysideInn, Elders, MagicWaterskin, MarbleFountains) ++` to:

```scala
      Vector[PhasePower](WaysideInn, Elders, MagicWaterskin, MarbleFountains,
        Quartermaster) ++
```

- [ ] **Step 5: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.wake.*"`
Expected: PASS. 5 new tests in `QuartermasterSuite`, and every existing wake suite still passes.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/wake/Quartermaster.scala src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala src/test/scala/oathdigital/gameplay/powers/wake/QuartermasterSuite.scala
git commit -m "feat(powers): add Quartermaster"
```

---

### Task 3: Saddle Makers

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/cardplay/SaddleMakers.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/cardplay/SaddleMakersSuite.scala`

**Interfaces:**
- Consumes: `ContributingPower`, `CatalogResolution.of`, `NoteSupport.gainedFromKey`, `NoteSupport.gainedFromNote`, `CardPlayedFaceup(card, source)`.
- Produces: `SaddleMakers.id`, `SaddleMakers.forCatalog(catalog): Option[SaddleMakers]`.

A play to a site also gains the player 1 favor from the played card's bank (`CardPlay.sitePlan`). The bank checks below therefore use faceup-adviser plays, which gain nothing of their own.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/cardplay/SaddleMakersSuite.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture,
  TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Table

class SaddleMakersSuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture._

  private val saddle = DenizenId("142")
  private val nomad = denizensOf(Suit.Nomad).take(3)
  private val order = denizensOf(Suit.Order).take(3)
  private val hearth = denizensOf(Suit.Hearth).take(3)
  private val holder = TargetsFixture.others(base).head

  /** `holder` holds Saddle Makers on `orientation`, and the actor Searches a
    * world deck topped by `top`. */
  private def held(top: Vector[WorldCardId],
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    TargetsFixture.giveAdviser(SearchFixture.staged(top), holder, saddle,
      orientation)

  private def favor(ready: ReadyGame, id: PlayerId): Int =
    player(ready, id).board.favor
  private def bank(ready: ReadyGame, suit: Suit): Int =
    ready.banks.favor.getOrElse(suit, 0)
  private def withBank(ready: ReadyGame, suit: Suit, n: Int): ReadyGame =
    ready.copy(banks = ready.banks.copy(favor = ready.banks.favor.updated(suit, n)))

  test("another player's nomad card played as a faceup adviser gains the " +
      "holder 2 favor from the Nomad bank"):
    val ready = held(nomad)
    val done = play(ready, Vector.empty, nomad.head, "adviser-faceup")
    val after = SearchFixture.after(done)
    assertEquals(favor(after, holder), favor(ready, holder) + 2)
    assertEquals(bank(after, Suit.Nomad), bank(ready, Suit.Nomad) - 2)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), after)

  test("an order card played to a site counts too"):
    val ready = held(order)
    val after = SearchFixture.after(play(ready, Vector.empty, order.head, "site"))
    assertEquals(favor(after, holder), favor(ready, holder) + 2)

  test("a card of another suit, a facedown play and a discard gain nothing"):
    val other = SearchFixture.after(play(held(hearth), Vector.empty,
      hearth.head, "adviser-faceup"))
    assertEquals(favor(other, holder), favor(held(hearth), holder))
    Vector("adviser-facedown", "discard").foreach { button =>
      val ready = held(nomad)
      val after = SearchFixture.after(play(ready, Vector.empty, nomad.head,
        button))
      assertEquals(favor(after, holder), favor(ready, holder), button)
    }

  test("a facedown adviser turned faceup by its play counts"):
    val ready = Table.start.adviser(holder, saddle)
      .adviser(actor, nomad.head, facedown = true).ready
    val after = SearchFixture.after(playFacedown(ready, Vector.empty,
      nomad.head, "adviser-faceup"))
    assertEquals(favor(after, holder), favor(ready, holder) + 2)

  test("the holder's own play gains nothing"):
    val ready = asAdviser(SearchFixture.staged(nomad), saddle)
    val after = SearchFixture.after(play(ready, Vector.empty, nomad.head,
      "adviser-faceup"))
    assertEquals(favor(after, actor), favor(ready, actor))
    assertEquals(bank(after, Suit.Nomad), bank(ready, Suit.Nomad))

  test("a facedown Saddle Makers is not active"):
    val ready = held(nomad, Orientation.FaceDown)
    val after = SearchFixture.after(play(ready, Vector.empty, nomad.head,
      "adviser-faceup"))
    assertEquals(favor(after, holder), favor(ready, holder))

  // ---- Lines ----

  private val power = SaddleMakers.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the holder's gain is Saddle Makers' line"):
    val done = play(held(nomad), Vector.empty, nomad.head, "adviser-faceup")
    assertEquals(said(done.events), Vector(NoteText.Said("gained",
      s"${holder.value} gained 2 favor from the Nomad bank.", covers = true)))

  test("a bank holding 1 favor gives 1, and an empty bank gives nothing and " +
      "writes no line"):
    val low = withBank(held(nomad), Suit.Nomad, 1)
    val one = play(low, Vector.empty, nomad.head, "adviser-faceup")
    assertEquals(favor(SearchFixture.after(one), holder), favor(low, holder) + 1)
    assertEquals(said(one.events), Vector(NoteText.Said("gained",
      s"${holder.value} gained 1 favor from the Nomad bank.", covers = true)))
    val empty = withBank(held(nomad), Suit.Nomad, 0)
    val none = play(empty, Vector.empty, nomad.head, "adviser-faceup")
    assertEquals(favor(SearchFixture.after(none), holder), favor(empty, holder))
    assertEquals(said(none.events), Vector.empty)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.SaddleMakersSuite"`
Expected: compile FAIL with "Not found: SaddleMakers".

- [ ] **Step 3: Create Saddle Makers**

Create `src/main/scala/oathdigital/gameplay/powers/cardplay/SaddleMakers.scala`:

```scala
package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution, NoteSupport}
import oathdigital.model._

/** Saddle Makers (card 142, adviser-only), a persistent rule of a faceup
  * adviser: "After another player plays a [nomad] or [order] card, you gain
  * [favor] [favor] from the matching favor bank."
  *
  * A `Transform` on the faceup card-play hook, as Gossip's is on the facedown
  * one. A card played faceup, to a site or as a faceup adviser, has its suit.
  * A facedown play has none and does not count, and neither does a Vision. A
  * facedown adviser turned faceup by the card-play procedure is played
  * faceup, as for Book Binders. A swap or a take is not a play. The gain is
  * best-effort, so a bank holding one favor gives one.
  *
  * The rule appends the same two nodes whatever the banks hold, so a refold
  * never shifts a parked sibling (Gossip, Book Binders). Its line, "{Blue}
  * gained 2 favor from the Nomad bank.", covers the generic gain line and
  * reads the gain's step, so an empty bank writes nothing.
  */
final case class SaddleMakers private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower:
  def id: PowerId = SaddleMakers.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = Vector(SaddleMakers.gained)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      reward(ctx).fold(children)(children ++ _))))

  override def applicable(ctx: PowerCtx): Boolean = reward(ctx).nonEmpty

  /** The holder's gain and its line, when another player plays a nomad or
    * order denizen faceup. */
  private def reward(ctx: PowerCtx): Option[Vector[Operation]] =
    ctx.operation match
      case CardPlayedFaceup(card: DenizenId, _) => for
        suit <- catalog.suitOf(card).filter(SaddleMakers.Suits)
        holder <- holderOf(ctx.state).filter(_ != ctx.activePlayer)
      yield Vector[Operation](Gain.Favor(holder, suit, SaddleMakers.Favor),
        Note(id, NoteSupport.gainedFromNote(SaddleMakers.gained,
          PowerSourceRef.Card(cardId), holder), covers = true))
      case _ => None

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

object SaddleMakers:
  val id: PowerId = PowerId("denizen.saddle-makers")
  val Favor: Int = 2
  val Suits: Set[Suit] = Set(Suit.Nomad, Suit.Order)
  val gained: NoteKey = NoteSupport.gainedFromKey("gained")

  def forCatalog(catalog: ExecutableCatalog): Option[SaddleMakers] =
    CatalogCards.denizen(catalog, id).map(new SaddleMakers(_, catalog))
```

- [ ] **Step 4: Register it**

In `src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala`, add `SaddleMakers.forCatalog(catalog).toVector ++` on the line after `BookBinders.forCatalog(catalog).toVector ++`.

- [ ] **Step 5: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.cardplay.*"`
Expected: PASS. 8 new tests in `SaddleMakersSuite`, and every existing card-play suite still passes.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/cardplay/SaddleMakers.scala src/main/scala/oathdigital/gameplay/powers/cardplay/CardPlayTriggers.scala src/test/scala/oathdigital/gameplay/powers/cardplay/SaddleMakersSuite.scala
git commit -m "feat(powers): add Saddle Makers"
```

---

### Task 4: A banner's leaving operations become children of its `Take`

**Files:**
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala:434-447`
- Modify: `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala:151-155,375-381`
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignRaid.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala:128-130`
- Modify: `src/test/scala/oathdigital/gameplay/ChallengeProcedureSuite.scala:239`
- Modify: `src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala`
- Test: `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`

**Interfaces:**
- Consumes: `OperationPipeline.run(ready, operations, policy, restrictions)(update): Either[OathViolation, OperationRun]`, `OperationRun(state, executed, skipped)`, `Operation.flatten(operation): Vector[Operation]`.
- Produces: `Take(piece, player, from, to, sourcePosition = StackPosition.Unspecified, required = false, leaving: Vector[CoreOperation] = Vector.empty)`, whose `children` are `leaving :+ move`. A positional pattern on `Take` now has seven fields.

- [ ] **Step 1: Write the failing tests**

In `src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala`, change the operations import to:

```scala
import oathdigital.gameplay.operations.{GrandScepter, LockedCards,
  OperationPipeline, OperationPolicy, OperationRestrictions}
```

and add this test at the end of the class:

```scala
  test("a refused Take skips its leaving operations with it, so the banner " +
      "stays whole"):
    val ready = Table.start.peoplesFavor(Some(p2), favor = 2).ready
    val take = Take(Piece.Banner(Banner.PeoplesFavor), p1,
      Location.PlayArea(p2), Location.PlayArea(p1), leaving = Vector(Move(
        Piece.Favor(2), PositionedLocation(Location.OnBanner(Banner.PeoplesFavor)),
        PositionedLocation(Location.FavorBank(Suit.Order)))))
    val noTake = new OperationRestriction:
      def reason(ready: ReadyGame, operation: CoreOperation)
          : Option[OperationReason] = operation match
        case _: Take => Some(OperationReason("test.no-take", "no take",
          OperationReasonKind.Impossible))
        case _ => None
    def run(restrictions: Vector[OperationRestriction]) = OperationPipeline.run(
      ready, Vector(take), OperationPolicy.Permissive, restrictions)(Right(_))
      .toOption.get
    val order = (state: ReadyGame) => state.banks.favor.getOrElse(Suit.Order, 0)
    val refused = run(Vector(noTake))
    assertEquals(refused.executed, Vector.empty)
    assertEquals(refused.state.game.current.banners.peoplesFavor.holder, Some(p2))
    assertEquals(refused.state.game.current.banners.peoplesFavor.favor, 2)
    assertEquals(order(refused.state), order(ready))
    val taken = run(Vector.empty)
    assertEquals(taken.state.game.current.banners.peoplesFavor.holder, Some(p1))
    assertEquals(taken.state.game.current.banners.peoplesFavor.favor, 0)
    assertEquals(order(taken.state), order(ready) + 2)
```

In `src/test/scala/oathdigital/serialization/GameEventWireSuite.scala`, add this test directly after the test "every Piece variant round-trips through the walker codec":

```scala
  test("a Take's leaving operations round trip, and a Take without them " +
      "writes none"):
    val player = PlayerId("red")
    val holder = PlayerId("blue")
    val leaving = Vector[CoreOperation](
      Move(Piece.Favor(1),
        PositionedLocation(Location.OnBanner(Banner.PeoplesFavor)),
        PositionedLocation(Location.FavorBank(Suit.Order))),
      Burn.secrets(2, PositionedLocation(Location.OnBanner(Banner.DarkestSecret))))
    val takes = Vector(
      Take(Piece.Banner(Banner.PeoplesFavor), player, Location.PlayArea(holder),
        Location.PlayArea(player), leaving = leaving),
      Take(Piece.Banner(Banner.DarkestSecret), player, Location.PlayArea(holder),
        Location.PlayArea(player)))
    val events = takes.map(take => WalkerStepRecorded("0",
      WalkerStepPayload.DeltaRecorded(DeltaMeaning.OperationApplied("take")),
      Vector(take), Vector.empty): OathEvent)
    val encoded = GameEventWire.encodeStream("take-leaving", catalogRef,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event)
      }).toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      events)
    val ops = ujson.read(encoded).arr.map(_("payload")("ops")(0))
    assertEquals(ops(0)("leaving").arr.size, 2)
    assert(!ops(1).obj.contains("leaving"))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite oathdigital.serialization.GameEventWireSuite"`
Expected: compile FAIL with "method apply in object Take does not take ... leaving" or a similar error naming `leaving`.

- [ ] **Step 3: Add `leaving` to `Take`**

In `src/main/scala/oathdigital/model/CoreOperations.scala`, replace:

```scala
/** Moves a piece into the prompted player's custody. A required `Take`
  * rejects its batch, or fails the search's path, when a restriction refuses
  * it.
  */
final case class Take(piece: Piece, player: PlayerId,
    from: Location, to: Location,
    sourcePosition: StackPosition = StackPosition.Unspecified,
    override val required: Boolean = false)
    extends CoreOperation:
  require(Location.ownedBy(to, player),
    "take destination must belong to the taking player")
  val move: Move = Move(piece, PositionedLocation(from, sourcePosition),
    PositionedLocation(to))
  override val children: Vector[Operation] = Vector(move)
```

with:

```scala
/** Moves a piece into the prompted player's custody. A required `Take`
  * rejects its batch, or fails the search's path, when a restriction refuses
  * it.
  *
  * `leaving` clears what does not travel with the piece before it moves: a
  * banner's favor returned to the banks, its secrets burnt. Those operations
  * are the `Take`'s first children, so a restriction that refuses the `Take`
  * refuses them with it, and the banner stays whole.
  */
final case class Take(piece: Piece, player: PlayerId,
    from: Location, to: Location,
    sourcePosition: StackPosition = StackPosition.Unspecified,
    override val required: Boolean = false,
    leaving: Vector[CoreOperation] = Vector.empty)
    extends CoreOperation:
  require(Location.ownedBy(to, player),
    "take destination must belong to the taking player")
  val move: Move = Move(piece, PositionedLocation(from, sourcePosition),
    PositionedLocation(to))
  override val children: Vector[Operation] = leaving :+ move
```

- [ ] **Step 4: Encode and decode `leaving`**

In `src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala`, replace the encode arm:

```scala
      case Take(piece, player, from, to, sourcePosition, _) => ujson.Obj(
        "kind" -> "take", "piece" -> encodePiece(piece),
        "playerId" -> player.value, "from" -> encodeLocation(from),
        "to" -> encodeLocation(to),
        "sourcePosition" -> encodeStackPosition(sourcePosition))
```

with:

```scala
      case Take(piece, player, from, to, sourcePosition, _, leaving) =>
        // Written only when present, so a Take without it encodes as before.
        val optional: Vector[(String, ujson.Value)] =
          if leaving.isEmpty then Vector.empty
          else Vector("leaving" -> ujson.Arr.from(leaving.map(encodeOperation)))
        ujson.Obj.from(Vector[(String, ujson.Value)](
          "kind" -> "take", "piece" -> encodePiece(piece),
          "playerId" -> player.value, "from" -> encodeLocation(from),
          "to" -> encodeLocation(to),
          "sourcePosition" -> encodeStackPosition(sourcePosition)) ++ optional)
```

and replace the decode arm:

```scala
    case "take" => for
      piece <- decodePiece(value("piece"), s"$path.piece")
      from <- decodeLocation(value("from"), s"$path.from")
      to <- decodeLocation(value("to"), s"$path.to")
      sourcePosition <- decodeStackPosition(value("sourcePosition").str,
        s"$path.sourcePosition")
    yield Take(piece, PlayerId(value("playerId").str), from, to,
      sourcePosition)
```

with:

```scala
    case "take" => for
      piece <- decodePiece(value("piece"), s"$path.piece")
      from <- decodeLocation(value("from"), s"$path.from")
      to <- decodeLocation(value("to"), s"$path.to")
      sourcePosition <- decodeStackPosition(value("sourcePosition").str,
        s"$path.sourcePosition")
      leaving <- value.obj.get("leaving").fold[Either[WireError,
          Vector[CoreOperation]]](Right(Vector.empty))(raw =>
        traverse(raw.arr.zipWithIndex.toVector)({ case (operation, index) =>
          decodeOperation(operation, s"$path.leaving[$index]") }))
    yield Take(piece, PlayerId(value("playerId").str), from, to,
      sourcePosition, leaving = leaving)
```

- [ ] **Step 5: Fix the positional `Take` patterns**

In `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala`, replace:

```scala
          case OpStep(Take(Piece.Card(id), _, _, _, _, _), before, after) =>
```

with:

```scala
          case OpStep(Take(Piece.Card(id), _, _, _, _, _, _), before, after) =>
```

and replace:

```scala
          case OpStep(Take(Piece.Banner(banner), _, _, _, _, _), _, _) =>
```

with:

```scala
          case OpStep(Take(Piece.Banner(banner), _, _, _, _, _, _), _, _) =>
```

In `src/test/scala/oathdigital/gameplay/ChallengeProcedureSuite.scala`, replace:

```scala
        case Take(Piece.Banner(_), _, _, _, _, _) => Some(OperationReason(
```

with:

```scala
        case Take(Piece.Banner(_), _, _, _, _, _, _) => Some(OperationReason(
```

- [ ] **Step 6: Move the Raid's leaving operations into its `Take`**

In `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignRaid.scala`, replace:

```scala
      val bannerOps: Vector[CoreOperation] = banners.flatMap { banner =>
```

with:

```scala
      // A banner's favor and secrets leave as its Take's children, so a
      // refused Take leaves the banner whole.
      val bannerOps: Vector[CoreOperation] = banners.map { banner =>
```

and replace:

```scala
        leaving :+ Take(Piece.Banner(banner), attacker,
          Location.PlayArea(defenderId), Location.PlayArea(attacker))
```

with:

```scala
        Take(Piece.Banner(banner), attacker, Location.PlayArea(defenderId),
          Location.PlayArea(attacker), leaving = leaving)
```

- [ ] **Step 7: Move Conspiracy's leaving operations into its `Take`**

In `src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala`, replace:

```scala
    def banner(held: Banner, owner: PlayerId): CoreOperation = Take(
      Piece.Banner(held), actor, Location.PlayArea(owner),
      Location.PlayArea(actor))
```

with:

```scala
    def banner(held: Banner, owner: PlayerId,
        leaving: Vector[CoreOperation]): CoreOperation = Take(
      Piece.Banner(held), actor, Location.PlayArea(owner),
      Location.PlayArea(actor), leaving = leaving)
```

replace:

```scala
          leaving :+ banner(held, owner)
```

with:

```scala
          Vector(banner(held, owner, leaving))
```

and in the class doc comment, replace:

```scala
  * The banner moves by a `Take`, so a restriction on taking sees it; a relic
  * moves by a `Give`. Both are optional.
```

with:

```scala
  * The banner moves by a `Take`, so a restriction on taking sees it; a relic
  * moves by a `Give`. Both are optional. The banner's favor and secrets leave
  * as the `Take`'s children, so a refused `Take` leaves the banner whole.
```

- [ ] **Step 8: Update the Conspiracy suite's reads of the banner's leaving operations**

The recorded batch now holds the banner `Take` with its leaving operations inside it. In `src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala`:

- In the test that takes the People's Favor, replace:

```scala
    val returned = recorded(done).collect:
```

with:

```scala
    // The favor returns as the banner Take's children.
    val returned = recorded(done).flatMap(Operation.flatten).collect:
```

- In the test "Conspiracy taking the Darkest Secret burns every secret and takes the banner", replace:

```scala
    assert(recorded(done).contains(Take(Piece.Banner(Banner.DarkestSecret),
      f.actor, Location.PlayArea(f.enemy), Location.PlayArea(f.actor))),
      recorded(done).toString)
```

with:

```scala
    assert(recorded(done).contains(Take(Piece.Banner(Banner.DarkestSecret),
      f.actor, Location.PlayArea(f.enemy), Location.PlayArea(f.actor),
      leaving = Vector(Burn.secrets(3,
        PositionedLocation(Location.OnBanner(Banner.DarkestSecret)))))),
      recorded(done).toString)
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.OperationRestrictionsSuite oathdigital.serialization.* oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.CampaignRaidSuite oathdigital.gameplay.powers.whenplayed.* oathdigital.gameplay.powers.targeting.* oathdigital.application.gamelog.*"`
Expected: PASS. 2 new tests (1 in `OperationRestrictionsSuite`, 1 in `GameEventWireSuite`), and every other test in those suites passes. `CampaignRaidSuite` asserts the Raid by state, so it passes unchanged.

If a Game Log suite fails because a line read a banner's leaving operation from the top of a recorded batch, stop and report it: ruling 4 found no such line.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/model/CoreOperations.scala src/main/scala/oathdigital/serialization/WalkerOperationCodec.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignRaid.scala src/main/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayed.scala src/main/scala/oathdigital/application/gamelog/CampaignLines.scala src/test/scala/oathdigital/gameplay/ChallengeProcedureSuite.scala src/test/scala/oathdigital/gameplay/powers/whenplayed/ConspiracyWhenPlayedSuite.scala src/test/scala/oathdigital/gameplay/OperationRestrictionsSuite.scala src/test/scala/oathdigital/serialization/GameEventWireSuite.scala
git commit -m "feat(operations): carry a banner's leaving operations inside its Take"
```

---

### Task 5: Lost Tongue

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/targeting/LostTongue.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/TargetProtections.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/ConspiracyTargets.scala:5-6`
- Test: `src/test/scala/oathdigital/gameplay/powers/targeting/LostTongueSuite.scala`

**Interfaces:**
- Consumes: `Take` with seven fields (Task 4), `RuledCards.of(catalog, ready, ruler, suit): Vector[CardId]`, `ConspiracyTargets.narrowed(power, operations, protects, note)`, `OptionRestriction(fn, note)`, `BannerRules.holder`.
- Produces: `LostTongue.id`, `LostTongue.forCatalog(catalog): Option[LostTongue]`, `LostTongue.shielded: NoteKey`, and `operationRestrictions` with one restriction, code `lost-tongue`.

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/targeting/LostTongueSuite.scala`:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.CampaignFixture.raidBoard
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.{CardStaging, NoteText, PowerFixture,
  SearchFixture, TargetsFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.whenplayed.ConspiracyWhenPlayed
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

class LostTongueSuite extends munit.FunSuite:
  import TargetingFixture._

  private val tongue = CatalogNames.denizen("Lost Tongue")
  /** A plain Nomad card: holding it faceup, a player rules a nomad card. */
  private val nomadCard = SearchFixture.denizensOf(Suit.Nomad).head
  private val power = LostTongue.forCatalog(catalog).get
  private val raid = ChooseOneAnswer(DecisionOptionRef.Button("raid"))
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  private def notes(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)
  private def shielded(holder: PlayerId) = NoteText.Said("shielded",
    s"${holder.value}'s banners and relics cannot be targeted.", covers = false)

  private def adviser(state: ReadyGame, player: PlayerId, card: DenizenId,
      side: Orientation = Orientation.FaceUp): ReadyGame =
    TargetsFixture.giveAdviser(state, player, card, side)

  // ---- Raid ----

  /** The Raid board, the defender holding Lost Tongue on `side` and the
    * attacker a faceup nomad adviser when `nomad`, the Raid chosen. Returns
    * the defender, their faceup relic and the transition of the kind answer.
    */
  private def raidKind(side: Orientation, nomad: Boolean)
      : (PlayerId, RelicId, OathTransition) =
    val (b, relic) = raidBoard()
    val held = adviser(b.ready, b.other, tongue, side)
    val ready = if nomad then adviser(held, b.actor, nomadCard) else held
    val started = start(ready, ActionRef.Campaign, b.actor).toOption.get
    (b.other, relic, rules.resolveWalker(started.state, b.actor,
      CampaignIds.kind, raid).toOption.get)

  private def allTargets(relic: RelicId): Set[DecisionOptionRef] = Set(
    DecisionOptionRef.Relic(relic), DecisionOptionRef.Banner(Banner.PeoplesFavor),
    DecisionOptionRef.Banner(Banner.DarkestSecret))

  test("a Raid may not target the holder's relics or banners when the " +
      "attacker rules no nomad card"):
    val (defender, _, kind) = raidKind(Orientation.FaceUp, nomad = false)
    // Every optional target is hidden, so the targets are not asked.
    assertEquals(parked.parkedDecision(kind.state).map(_.decision),
      Some(CampaignIds.force))
    val said = notes(kind.events)
    assertEquals(said.size, 3)
    assertEquals(said.distinct, Vector(shielded(defender)))

  test("an attacker who rules a nomad card may target them all"):
    val (_, relic, kind) = raidKind(Orientation.FaceUp, nomad = true)
    assertEquals(optionsAt(kind, ActionRef.Campaign).toSet, allTargets(relic))
    assertEquals(notes(kind.events), Vector.empty)

  test("a facedown Lost Tongue protects nothing"):
    val (_, relic, kind) = raidKind(Orientation.FaceDown, nomad = false)
    assertEquals(optionsAt(kind, ActionRef.Campaign).toSet, allTargets(relic))

  // ---- Take ----

  test("its restriction refuses a Take of the holder's relic or banner by a " +
      "player who rules no nomad card, and nothing else"):
    val relic = RelicId("R10")
    val board = Table.start.adviser(p2, tongue).relic(p2, relic)
      .peoplesFavor(Some(p2), favor = 2)
    val ready = board.ready
    val withNomad = board.adviser(p1, nomadCard).ready
    val facedown = Table.start.adviser(p2, tongue, facedown = true)
      .relic(p2, relic).peoplesFavor(Some(p2), favor = 2).ready
    def refused(state: ReadyGame, operation: CoreOperation): Boolean =
      power.operationRestrictions.exists(_.reason(state, operation).nonEmpty)
    val mine = Location.PlayArea(p1)
    val theirs = Location.PlayArea(p2)
    val takeRelic = Take(Piece.Card(relic), p1, theirs, mine)
    val takeBanner = Take(Piece.Banner(Banner.PeoplesFavor), p1, theirs, mine)
    assert(refused(ready, takeRelic))
    assert(refused(ready, takeBanner))
    assert(!refused(withNomad, takeRelic))
    assert(!refused(withNomad, takeBanner))
    assert(!refused(facedown, takeRelic))
    assert(!refused(ready, Give(Piece.Card(relic), p2, theirs, mine)),
      "a Give is not a Take")
    assert(!refused(ready, Take(Piece.Favor(1), p1,
      Location.OnBanner(Banner.PeoplesFavor), mine)),
      "a banner's contents are not the banner")

  // ---- Conspiracy ----

  private val conspiracy = VisionRules.Conspiracy
  private val relic = RelicId("R10")
  private val hook = CardPlayedFaceup(conspiracy,
    RuleSourceRef.Adviser(PowerFixture.actor, conspiracy))
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)

  /** The actor plays Conspiracy at a site the enemy shares. The enemy holds
    * Lost Tongue faceup, the People's Favor with 2 favor, and relic R10 when
    * `withRelic`. The actor holds a faceup nomad adviser when `nomad`.
    * Returns the state, the actor and the enemy.
    */
  private def conspiracyAt(withRelic: Boolean = true, nomad: Boolean = false)
      : (ReadyGame, PlayerId, PlayerId) =
    val base = PowerFixture.base
    val actor = PowerFixture.actor
    val enemy = TargetsFixture.others(base).head
    val site = PowerFixture.player(base).pawnSite
    val staged = CardStaging.without(CardStaging.without(base, conspiracy), relic)
      .updateCurrent(c => c.copy(
        players = c.players.map(p => if p.player == enemy then
          p.copy(pawnSite = site) else p),
        banners = c.banners.copy(
          peoplesFavor = c.banners.peoplesFavor.copy(holder = Some(enemy),
            favor = 2),
          darkestSecret = c.banners.darkestSecret.copy(holder = None)),
        temporaryHands = c.temporaryHands.updated(actor, Vector(conspiracy))))
    val held = adviser(if withRelic then holds(staged, enemy, relic) else staged,
      enemy, tongue)
    (if nomad then adviser(held, actor, nomadCard) else held, actor, enemy)

  /** The target options and the pending tree, both empty when nothing was
    * asked, and the walk's events. */
  private def targets(ready: ReadyGame)
      : (Vector[DecisionOptionRef], Option[PendingTree], Vector[OathEvent]) =
    ProcedureWalker.advance(ready, hook, None, powers).toOption.get match
      case WalkerOutcome.Parked(pending, events) =>
        (ProcedureWalker.parkedDecide(ready, hook, pending, powers).get.query
          .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref),
          Some(pending), events)
      case WalkerOutcome.Finished(_, events) => (Vector.empty, None, events)

  /** Answers the target decision with `ref` and returns the state after. */
  private def take(ready: ReadyGame, actor: PlayerId, ref: DecisionOptionRef)
      : ReadyGame = ProcedureWalker.resolve(ready, hook, targets(ready)._2.get,
    Answered(ConspiracyWhenPlayed.decisionId, ChooseOneAnswer(ref), actor),
    powers).toOption.get.asInstanceOf[WalkerOutcome.Finished].treeless

  test("a Conspiracy is not offered the holder's banner, and writes no line"):
    val (ready, _, enemy) = conspiracyAt()
    val (options, _, events) = targets(ready)
    assertEquals(options, Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 0)))
    assertEquals(notes(events), Vector.empty)

  test("a Conspiracy may still take the holder's relic, which it gives"):
    val (ready, actor, enemy) = conspiracyAt()
    val after = take(ready, actor, DecisionOptionRef.RelicSlot(enemy, 0))
    assert(PowerFixture.player(after, actor).relics.exists(_.id == relic))

  test("an actor who rules a nomad card takes the banner, its favor " +
      "returning to the banks"):
    val (ready, actor, _) = conspiracyAt(nomad = true)
    assert(targets(ready)._1.contains(DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    val after = take(ready, actor, DecisionOptionRef.Banner(Banner.PeoplesFavor))
    assertEquals(after.game.current.banners.peoplesFavor.holder, Some(actor))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 0)
    assertEquals(after.banks.favor.values.sum, ready.banks.favor.values.sum + 2)

  test("a Conspiracy left with only the holder's banner asks nothing"):
    val (ready, _, _) = conspiracyAt(withRelic = false)
    val (options, pending, _) = targets(ready)
    assertEquals(options, Vector.empty)
    assertEquals(pending, None)
```

- [ ] **Step 2: Run the suite to verify it fails**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.LostTongueSuite"`
Expected: compile FAIL with "Not found: LostTongue".

- [ ] **Step 3: Create Lost Tongue**

Create `src/main/scala/oathdigital/gameplay/powers/targeting/LostTongue.scala`:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution, RuledCards}
import oathdigital.model._

/** Lost Tongue (card 157, adviser-only), a persistent rule of a faceup
  * adviser: "Other players cannot target or take your relics or banners in
  * any way unless they rule a [nomad] card."
  *
  * Each part reads, when it applies, whether the acting player rules a nomad
  * card ([[oathdigital.gameplay.powers.RuledCards]]):
  *
  *  - **Target.** "Target" is a Campaign's target selection only. A Raid's
  *    optional targets (`CampaignTargetSelection`) hide the holder's relics
  *    and banners, as the Circlet of Command hides them. Each hidden option
  *    writes "{Blue}'s banners and relics cannot be targeted.", naming the
  *    holder.
  *  - **Take.** A registered operation restriction refuses a `Take` of a
  *    relic or a banner from the holder's board. It refuses the `Take`
  *    operation only: a `Give`, such as a Conspiracy's relic or an agreed
  *    Negotiation transfer, is allowed, and so is taking a banner's contents.
  *    A banner's leaving operations are children of its `Take`, so a refused
  *    `Take` leaves the banner whole. A played Conspiracy, which takes a
  *    banner by a `Take`, is not offered the holder's banners
  *    ([[ConspiracyTargets]]). A refused operation is never offered, so the
  *    `Take` part writes no line.
  *
  * Bandits hold nothing and take nothing, so they are never refused.
  */
final case class LostTongue private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower:
  def id: PowerId = LostTongue.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = Vector(LostTongue.shielded)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection -> Vector(OptionRestriction(guard,
      (ctx, _) => protectedHolder(ctx.state, ctx.activePlayer).map(owner =>
        LostTongue.shielded(PowerSourceRef.Card(cardId),
          NoteArg.Player(owner))))),
    PowerWindow.ConspiracyTargetSelection -> Vector(Transform((ctx, operations) =>
      ConspiracyTargets.narrowed(id, operations, bannerShielded(ctx, _), None))))

  override def operationRestrictions: Vector[OperationRestriction] =
    Vector(takes)

  /** Refuses a `Take` of the holder's relic or banner by a player who rules
    * no nomad card. */
  private object takes extends OperationRestriction:
    def reason(ready: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] = operation match
      case Take(piece, taker, Location.PlayArea(owner), _, _, _, _)
          if relicOrBanner(piece) &&
            protectedHolder(ready, taker).contains(owner) =>
        Some(OperationReason("lost-tongue", s"${owner.value}'s Lost Tongue " +
          s"keeps ${taker.value} from taking their relics and banners",
          OperationReasonKind.Impossible))
      case _ => None

  private def relicOrBanner(piece: Piece): Boolean = piece match
    case Piece.Card(_: RelicId) | Piece.Banner(_) => true
    case _ => false

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = Option.when(shields(ctx, ref))(
    OathViolation.InvalidEventOrder(
      "Lost Tongue protects its holder's relics and banners"))

  /** Whether `ref` names a relic or banner of the protected holder. */
  private def shields(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    val current = ctx.state.game.current
    protectedHolder(ctx.state, ctx.activePlayer).exists(owner => ref match
      case DecisionOptionRef.Banner(banner) =>
        BannerRules.holder(current, banner).contains(owner)
      case DecisionOptionRef.Relic(relic) => current.players.exists(p =>
        p.player == owner && p.relics.exists(_.id == relic))
      case DecisionOptionRef.RelicSlot(slotOwner, _) => slotOwner == owner
      case _ => false)

  /** Whether `ref` names a banner of the protected holder. */
  private def bannerShielded(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    ref match
      case _: DecisionOptionRef.Banner => shields(ctx, ref)
      case _ => false

  /** The holder of this card faceup, when `actor` is another player who rules
    * no nomad card. */
  private def protectedHolder(ready: ReadyGame, actor: PlayerId)
      : Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player).filter(owner => owner != actor &&
      RuledCards.of(catalog, ready, SiteRuler.Player(actor), Suit.Nomad).isEmpty)

object LostTongue:
  val id: PowerId = PowerId("denizen.lost-tongue")

  /** "{Blue}'s banners and relics cannot be targeted." */
  val shielded: NoteKey = NoteKey("shielded", Vector(NotePart.Arg(0),
    NotePart.Text("'s banners and relics cannot be targeted.")))

  def forCatalog(catalog: ExecutableCatalog): Option[LostTongue] =
    CatalogCards.denizen(catalog, id).map(new LostTongue(_, catalog))
```

- [ ] **Step 4: Register it and update `ConspiracyTargets`' doc**

In `src/main/scala/oathdigital/gameplay/powers/targeting/TargetProtections.scala`:

- Add `LostTongue.forCatalog(catalog).toVector ++` on the line after `ForgottenVault.forCatalog(catalog).toVector ++`.
- Replace the doc comment with:

```scala
/** The persistent rules that keep a player, or what they hold, from being
  * targeted by a Raid, a Challenge or a Conspiracy, or taken, registered
  * together. A power whose card is absent from `catalog` is omitted.
  */
```

In `src/main/scala/oathdigital/gameplay/powers/targeting/ConspiracyTargets.scala`, replace:

```scala
/** Narrows a played Conspiracy's target decision for a rule that protects
  * some of its targets (the Circlet of Command, the Forgotten Vault).
```

with:

```scala
/** Narrows a played Conspiracy's target decision for a rule that protects
  * some of its targets (the Circlet of Command, the Forgotten Vault, Lost
  * Tongue).
```

- [ ] **Step 5: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.* oathdigital.gameplay.powers.whenplayed.* oathdigital.gameplay.CampaignRaidSuite oathdigital.gameplay.ChallengeProcedureSuite"`
Expected: PASS. 8 new tests in `LostTongueSuite`, and every existing test in those suites still passes.

If the first Raid test finds the walk parked at `CampaignIds.targets` with no option rather than at `CampaignIds.force`, the walker kept an empty choose-many. Stop and report it: a Raid against a protected holder must still be playable.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/targeting/LostTongue.scala src/main/scala/oathdigital/gameplay/powers/targeting/TargetProtections.scala src/main/scala/oathdigital/gameplay/powers/targeting/ConspiracyTargets.scala src/test/scala/oathdigital/gameplay/powers/targeting/LostTongueSuite.scala
git commit -m "feat(powers): add Lost Tongue"
```

---

### Task 6: The Circlet of Command and the Forgotten Vault protect Campaign targets only

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/CircletOfCommand.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/ForgottenVault.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/targeting/ConspiracyTargets.scala:5-7`
- Modify: `src/test/scala/oathdigital/gameplay/powers/targeting/CircletOfCommandSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/targeting/ForgottenVaultSuite.scala`

**Interfaces:**
- Consumes: nothing new.
- Produces: `CircletOfCommand` and `ForgottenVault` contribute at `PowerWindow.CampaignTargetSelection` only.

The rulings ("Target protections corrected") say "target" on a card means a Campaign's target selection only. Write the suite changes first, then the code.

- [ ] **Step 1: Turn the Circlet suite's Challenge and Conspiracy tests around**

In `src/test/scala/oathdigital/gameplay/powers/targeting/CircletOfCommandSuite.scala`:

Replace the test "a Challenge may not name a banner its holder's Circlet protects", the whole test, with:

```scala
  test("a Challenge may name any banner: the Circlet protects only a " +
      "Campaign's targets"):
    val banner = (b: Banner) => DecisionOptionRef.Banner(b): DecisionOptionRef
    assertEquals(challengeBanners(Some(Orientation.FaceUp)).toSet,
      Set(banner(Banner.PeoplesFavor), banner(Banner.DarkestSecret)))
```

Replace the test "Conspiracy may take the Circlet, but not the holder's other relic or banner", the whole test, with:

```scala
  test("Conspiracy may take any of the holder's relics and banners"):
    val (ready, enemy, options, _) = conspiracyTargets(Orientation.FaceUp)
    assertEquals(PowerFixture.player(ready, enemy).relics.map(_.id),
      Vector(other, circlet))
    assertEquals(options.toSet, Set[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 0), DecisionOptionRef.RelicSlot(enemy, 1),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))
```

Replace the test "the banner the Circlet hides from a Challenge names its holder", the whole test, with:

```scala
  test("a Challenge writes no Circlet line"):
    assertEquals(hidden(challenge(Some(Orientation.FaceUp))._2.events),
      Vector.empty)
```

Replace the test "a Conspiracy whose targets the Circlet narrows names the holder", the whole test, with:

```scala
  test("a Conspiracy writes no Circlet line"):
    assertEquals(hidden(conspiracyTargets(Orientation.FaceUp)._4), Vector.empty)
```

- [ ] **Step 2: Turn the Vault suite's Conspiracy tests around**

In `src/test/scala/oathdigital/gameplay/powers/targeting/ForgottenVaultSuite.scala`, replace the three tests "Conspiracy may take the ruler's banner, but not their relic", "Conspiracy may not take the ruler's facedown relic either" and "a Conspiracy left with no target asks nothing and still says why", all three whole, with:

```scala
  test("Conspiracy may take the ruler's relic as well as their banner"):
    val (enemy, options, events) = conspiracyTargets(banner = true)
    assertEquals(options.toSet, Set[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 0),
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    assertEquals(notes(events), Vector.empty)

  test("Conspiracy may take the ruler's facedown relic too"):
    val (enemy, options, events) = conspiracyTargets(banner = true,
      Orientation.FaceDown)
    assert(options.contains(DecisionOptionRef.RelicSlot(enemy, 0)),
      options.toString)
    assertEquals(notes(events), Vector.empty)

  test("a Conspiracy against the ruler's relic alone asks for it and writes " +
      "nothing"):
    val (enemy, options, events) = conspiracyTargets(banner = false)
    assertEquals(options, Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 0)))
    assertEquals(notes(events), Vector.empty)
```

- [ ] **Step 3: Run the suites to verify the new tests fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.CircletOfCommandSuite oathdigital.gameplay.powers.targeting.ForgottenVaultSuite"`
Expected: FAIL. The Circlet's Challenge, Conspiracy and line tests, and the Vault's three Conspiracy tests, fail because both cards still narrow those decisions.

- [ ] **Step 4: Narrow the Circlet to Campaign targets**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/targeting/CircletOfCommand.scala` with:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, OptionRestriction, PowerCtx}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** The Circlet of Command (relic R15), a persistent rule of a faceup relic:
  * players other than the holder cannot target the holder's banners, or the
  * holder's relics other than the Circlet itself. A facedown Circlet does
  * nothing.
  *
  * "Target" means a Campaign's target selection only (catalog batch 3
  * rulings, "Target protections corrected"). So it hides options at a Raid's
  * optional targets (`CampaignTargetSelection`), which list the defender's
  * faceup relics and banners. A Challenge's banner choice and a played
  * Conspiracy's target are not targets. A Raid's mandatory target, the
  * defender's pawn, is not a banner or a relic and stays a target.
  *
  * Each option it hides writes "{Blue}'s banners and relics cannot be
  * targeted.", naming the holder (power log lines design, "Removed and
  * hidden options").
  */
final case class CircletOfCommand private (cardId: RelicId,
    catalog: ExecutableCatalog) extends ContributingPower:
  def id: PowerId = CircletOfCommand.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = Vector(CircletOfCommand.shielded)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(guard, (ctx, _) => note(ctx))))

  /** The line naming the holder whose things it protects. */
  private def note(ctx: PowerCtx): Option[PowerNote] =
    holder(ctx.state).map(owner => CircletOfCommand.shielded(
      PowerSourceRef.Card(cardId), NoteArg.Player(owner)))

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = Option.when(shields(ctx, ref))(
    OathViolation.InvalidEventOrder(
      "the Circlet of Command protects its holder's banners and relics"))

  /** The player holding this Circlet faceup, if any. */
  private def holder(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.relics.exists {
      case RelicState(`cardId`, Orientation.FaceUp, _) => true
      case _ => false
    }).map(_.player)

  /** Whether `ref` names a banner or a relic of the holder that the acting
    * player may not target.
    */
  private def shields(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    val current = ctx.state.game.current
    val faceupHolder = holder(ctx.state)
    def held(owner: PlayerId): Boolean = faceupHolder.contains(owner) &&
      owner != ctx.activePlayer
    ref match
      case DecisionOptionRef.Banner(banner) =>
        BannerRules.holder(current, banner).exists(held)
      case DecisionOptionRef.Relic(relic) => relic != cardId &&
        current.players.find(_.relics.exists(_.id == relic)).exists(p =>
          held(p.player))
      case DecisionOptionRef.RelicSlot(owner, slot) => held(owner) &&
        !current.players.find(_.player == owner).flatMap(_.relics.lift(slot))
          .exists(_.id == cardId)
      case _ => false

object CircletOfCommand:
  val id: PowerId = PowerId("relic.circlet-of-command")

  /** "{Blue}'s banners and relics cannot be targeted." */
  val shielded: NoteKey = NoteKey("shielded", Vector(NotePart.Arg(0),
    NotePart.Text("'s banners and relics cannot be targeted.")))

  def forCatalog(catalog: ExecutableCatalog): Option[CircletOfCommand] =
    CatalogCards.relic(catalog, id).map(new CircletOfCommand(_, catalog))
```

- [ ] **Step 5: Narrow the Vault to Campaign targets**

Replace the whole of `src/main/scala/oathdigital/gameplay/powers/targeting/ForgottenVault.scala` with:

```scala
package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, OptionRestriction, PowerCtx}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Forgotten Vault (card 75, site-only), a persistent rule: enemies of the
  * Vault's ruler cannot target relics that ruler holds, as the Circlet of
  * Command protects its holder's relics. The ruler is the ruler of the Vault's
  * site. Ruled by bandits or unruled, it protects nothing, because bandits hold
  * no relics. Empire rulers are not supported.
  *
  * "Target" means a Campaign's target selection only (catalog batch 3
  * rulings, "Target protections corrected"). So it restricts a Raid's
  * optional targets (`CampaignTargetSelection`), through an
  * `OptionRestriction` whose hide hook notes each relic it hides. A played
  * Conspiracy may take the ruler's relic.
  *
  * The Game Log posts identical notes once per action.
  */
final case class ForgottenVault private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower:
  def id: PowerId = ForgottenVault.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = Vector(ForgottenVault.shielded)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(guard, (ctx, _) => protectedRuler(ctx).map(said))))

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = Option.when(shields(ctx, ref))(
    OathViolation.InvalidEventOrder(
      "the Forgotten Vault protects its ruler's relics"))

  private def said(ruler: PlayerId): PowerNote =
    ForgottenVault.shielded(PowerSourceRef.Card(cardId), NoteArg.Player(ruler))

  /** The Vault's ruler, when the acting player is one of their enemies. */
  private def protectedRuler(ctx: PowerCtx): Option[PlayerId] =
    SiteRulers.rulerOfCard(ctx.state, cardId).collect {
      case ruler @ SiteRuler.Player(owner)
          if SiteRule.enemies(ruler, SiteRuler.Player(ctx.activePlayer)) => owner
    }

  /** Whether `ref` names a relic the protected ruler holds. */
  private def shields(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    protectedRuler(ctx).exists(owner => ref match
      case DecisionOptionRef.Relic(relic) =>
        ctx.state.game.current.players.exists(p =>
          p.player == owner && p.relics.exists(_.id == relic))
      case DecisionOptionRef.RelicSlot(slotOwner, _) => slotOwner == owner
      case _ => false)

object ForgottenVault:
  val id: PowerId = PowerId("denizen.forgotten-vault")

  /** "{ruler}'s relics cannot be targeted." */
  val shielded: NoteKey = NoteKey("shielded", Vector(NotePart.Arg(0),
    NotePart.Text("'s relics cannot be targeted.")))

  def forCatalog(catalog: ExecutableCatalog): Option[ForgottenVault] =
    CatalogCards.denizen(catalog, id).map(new ForgottenVault(_, catalog))
```

In `src/main/scala/oathdigital/gameplay/powers/targeting/ConspiracyTargets.scala`, replace:

```scala
/** Narrows a played Conspiracy's target decision for a rule that protects
  * some of its targets (the Circlet of Command, the Forgotten Vault, Lost
  * Tongue).
```

with:

```scala
/** Narrows a played Conspiracy's target decision for a rule that protects
  * some of its targets (Lost Tongue).
```

- [ ] **Step 6: Run the suites to verify they pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.targeting.* oathdigital.gameplay.ChallengeProcedureSuite oathdigital.gameplay.powers.whenplayed.*"`
Expected: PASS. No new tests: the Circlet suite and the Vault suite keep their counts, with the turned-around tests passing.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/targeting/CircletOfCommand.scala src/main/scala/oathdigital/gameplay/powers/targeting/ForgottenVault.scala src/main/scala/oathdigital/gameplay/powers/targeting/ConspiracyTargets.scala src/test/scala/oathdigital/gameplay/powers/targeting/CircletOfCommandSuite.scala src/test/scala/oathdigital/gameplay/powers/targeting/ForgottenVaultSuite.scala
git commit -m "fix(powers): limit the Circlet of Command and the Forgotten Vault to Campaign targets"
```

---

### Task 7: Docs and gates

**Files:**
- Modify: `docs/ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md:244-250`

- [ ] **Step 1: Record the slice in `docs/ROADMAP.md`**

In the "Phase - Catalog batch 3" section, replace:

```markdown
Idiot, Downtrodden, The Old Oak, Disciples and Crop Rotation, and the Muster
rule Initiation Rite. Slices 2b to 4 remain.
```

with:

```markdown
Idiot, Downtrodden, The Old Oak, Disciples and Crop Rotation, and the Muster
rule Initiation Rite. Slice 2b is done: Insomnia, Quartermaster, Saddle
Makers and Lost Tongue, and the Circlet of Command and the Forgotten Vault now
protect Campaign targets only. Slices 3a to 4 remain.
```

- [ ] **Step 2: Close the open point in the global restrictions design**

In `docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md`, replace:

```markdown
optional, like the relic `Give` beside it. Whether a refused Conspiracy
target is hidden is decided with Lost Tongue, the first restriction on `Take`.
That decision must also cover the order inside the batch: the banner's leaving
operations (the Darkest Secret burn, the People's Favor return) run before the
`Take`, so a skipped `Take` would leave the banner emptied and not taken.
Lost Tongue can probably handle this by making the leaving operations children
of the `Take` itself, so a refused `Take` skips them with it.
```

with:

```markdown
optional, like the relic `Give` beside it. Lost Tongue, the first restriction
on `Take`, settled the rest in catalog batch 3, slice 2b. It hides the
banners it protects from a Conspiracy's targets, without a line. A banner's
leaving operations (the Darkest Secret burn, the People's Favor return) are
children of its `Take`, so a refused `Take` skips them with it and the banner
stays whole.
```

- [ ] **Step 3: Run the gates**

Run: `./sbtw "test" "frontend/test"`
Expected: every server and frontend test passes. The server count is the baseline plus 29 (6 in Task 1, 5 in Task 2, 8 in Task 3, 2 in Task 4, 8 in Task 5, none in Task 6). From a baseline of 2298, that is 2327. The frontend count is unchanged at 466.

Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both pass.

- [ ] **Step 4: Commit**

```bash
git add docs/ROADMAP.md docs/superpowers/specs/2026-09-30-global-operation-restrictions-design.md
git commit -m "docs: record catalog batch 3 slice 2b"
```
