# Game Log, Slice 2 (Knowledge, Details and Goldens) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the game log tell the whole story. Knowledge follows a card when it moves, and every detail line is added: decisions, rolls drawn as dice, resource and card changes, minor actions and state-based triggers. Setup gets its lines, a Negotiation its settlement, and a Campaign its full account. The campaign result panel leaves the Actions pane, and an exact golden log pins every script.

**Architecture:** The knowledge fix lives in the one function every card move passes through, `CardMovementOperations.applyCardMoves`, so the board, the decision options and the log all see it. The formatter gains focused line builders beside Slice 1's `ActionLines`:

- `ChoiceWords` names a chosen option; it rebuilds the parked decision to read a button's label.
- `DetailLines` covers decisions, rolls and deltas inside a walker run.
- `EventLines` covers the five standalone events.
- `SetupLines`, `NegotiationLines` and `CampaignLines` cover their procedures.

Detail lines skip every operation or answer that an action line already narrates. The exclusions are an explicit, tested list. Rolls travel as a new `dice` span that the pane draws with the existing die-face chips. Golden files under `src/test/resources/gamelog/` hold the exact entries for every script, for the acting seat and for one other seat.

**Tech Stack:** Scala 3.9.0, Scala.js, ujson with hand-written `exact` codecs, munit (jsdom for the frontend). Build through `./sbtw`. Impeccable (`~/.claude/skills/impeccable`) for the copy review and the pane polish.

**Spec:** `docs/superpowers/specs/2026-09-25-game-log-design.md` (approved at `e050d4c4`). Before starting, read these sections: Knowledge follows the card, Detail lines, Setup lines, Campaign, Negotiation, Visibility, Wire contract and Testing. Slice 1's plan (`docs/superpowers/plans/2026-09-26-game-log-slice1.md`) explains the formatter this plan extends. Slice 3 (overlay, divider, New chip, sticky headline) is a separate plan.

## Global Constraints

- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` on both projects. An unused import or private member fails the build.
- Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- `shared/src/main` may import only `oathdigital.protocol`. `application` must not import `persistence`, `serialization` or `server`. `gameplay` must not import `application` or `protocol`.
- Never touch the live database `var/oathdigital`. Browser checks use a scratch copy only.
- Other sessions commit to `main` while you work. Stage explicit paths only; never `git add -A`. Never commit `node_modules`, `.tooling` or `target`. Work in a git worktree, and symlink the main checkout's `.tooling` into it before the first `./sbtw`.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- Voice: past-tense verbs, and the subject is left out when the actor of the run does the thing. Another player's deed names that player. The formatter never emits an operation's or event's class name, and never a raw decision key.
- Card backs are public. A card the viewer may not identify reads "a Denizen", "a Vision" or "a Relic". A face-down card in a player's row that a Negotiation line must tell apart reads "facedown adviser (slot n)" or "facedown relic (slot n)". Neither form is ever a `Card` span, and no wire field ever carries a hidden card's id.
- Entry order equals `(sequence, ordinal)` order, and nothing already posted ever changes: the client only appends.
- Record the baseline test counts from the first full run and use them as expected counts later. Do not invent counts.
- Gates, in order, before the final commit: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`, then `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`.
- UI work follows Impeccable. Run `~/.claude/skills/impeccable/scripts/impeccable context` once per session with cwd at the worktree root. Read `reference/craft-floor.md` immediately before any CSS or pane edit. Verify in one batched pass plus at most one confirming pass. DESIGN.md forbids `transition`, `animation` and resting shadows, and keeps saturated color for pieces only.

### Decisions this plan makes that the spec left open

These were settled while planning (2026-09-26). The user chose the first one; the rest are plan-level wording and anchoring choices. Task 12 records each one in the spec.

1. **Dice are drawn as dice.** A roll line carries a `dice` span: `kind = "dice"`, `unit` names the die (`attack` or `defense`), `id` holds the face wire names separated by single spaces, and `text` gives the rulebook names joined by ", ". The pane draws it with `DieFace.roll`, the chip the removed campaign panel used. This is a seventh span kind beside the spec's six.
2. **Roll wording.** "Rolled {dice}" with " for the attack" or " for the defense" on the two Campaign pools, and nothing added for any other pool (Recover).
3. **Battle plans.** Each plan gets its own line at its choice: "Activated {plan}", or "Blue activated {plan}" for the defender. A face-down plan card that is flipped posts "Blue revealed {card}" at the flip, so it follows its activation line instead of preceding it. The spec lists them as one "activated {battle plans}" line after "revealed". Where the attacker's loop ends is not always recorded in the segment that ends it, so one line per plan is the rule that never posts thin. Bandit plans are named the same way, "The bandits activated {plan}". Bandits apply every free plan with no decision, so their line posts at the record each application leaves (`CampaignPlans.appliedMarker`), read back to the plan's source by a new inverse, `CampaignPlans.markedRef`. (The user asked on 2026-09-26 for bandit plans to be named, so that every plan activation reads the same.)
4. **Campaign results.** "Targets: {targets}" and "Attack Pool: {n}, Defense Pool: {n}" post at the always-asked force answer. They read `CampaignSetup.setup` and `CampaignBattle.printedDefense` on the state after that answer, exactly as the procedure reads them. "Attack: {score}" (with " with {n} skull(s)" when skulls were paid) and "Defense: {score}" post at each pool's `ModifyRollOutcome`. "Sacrificed {n} warband(s)" posts at a non-zero sacrifice answer. The winner's gains and the loser's losses post as two lines at the run's completion, when every outcome operation is known:
   - Gains: "Took {relics and banners} from Blue" for a Raid, or "Placed {n} warbands on {site}, …" for a Conquest.
   - Losses: "Blue lost {n} warbands, burned {n} favor, discarded {cards}, set aside {relics} and was sent to {site}", keeping only the parts that happened. The loser is the defender when the attacker wins and the attacker otherwise; "The bandits lost …" names the bandits.
5. **Decision lines.** "Chose {options}" (or "Blue chose …") for a `ChooseOneAnswer` or a non-empty `ChooseManyAnswer`. They skip every decision in the narrated list (Task 2). A button reads the label the player was shown, found by rebuilding the parked decision on the state before the answer.
6. **Negotiation settlement.** "Blue gave 1 favor to Yellow" and "Blue gave {relic} to Yellow" come from `Give`. "White showed Blue {card}" comes from a `Peek` of a card in a player's area. "Blue was shown {relic} at {site}" comes from a `Peek` at a site, which names no shower. The lines keep the settlement batch's order: disclosures first, then transfers.
7. **Search placement.** Search plays its kept card through the same Card Play placement as Play Facedown Adviser, so it posts the same "Played … / Discarded …" action line. With a single drawn card, its "Drew … and kept …" line now posts at the placement answer, so it still precedes the placement line.
8. **Minor actions and triggers.** The spec wording applies: "Peeked at {relics} at {site}", "Revealed {relic}", "Moved {n} warbands to {site}" (or "from {site}"), "Bandits returned to {sites}" and "Blue became the Usurper".
9. **The Trigger "{card}: {effect}" row.** Only Use Power runs and Take Wealth record `RecordPowerUse`. Use Power already posts "Used {card}", and that power's effect follows as detail lines, so no separate trigger line is added.
10. **Arranged pieces.** A test script's `Step.Arrange` of favor from a bank into a player's area now posts "Gained … favor from the … bank" inside the End Wake run it joins. The line is truthful to the journal, and the goldens keep it.

### Facts this plan relies on (verified against the code on 2026-09-26)

- **Knowledge.** `CardKnowledge(siteRelics: Map[PlayerId, Map[SiteId, Vector[RelicId]]], advisers: Map[PlayerId, Vector[WorldCardId]], heldRelics: Map[PlayerId, Vector[RelicId]])` sits on `ReadyGame.knowledge` (`model/GameStateProtocol.scala:24-36`). Only `CardFaceOperations.peek` writes it, and nothing removes or moves an entry. `GamePresentationProjector.identifiesCard` (`:271-330`) decides visibility per location:
  - Site relics: face up, or `siteRelics`, or the viewer's pawn at the site.
  - A player's area: face up, the owner, or `heldRelics` (relics) or `advisers` (world cards).
  - A hand: its owner only.
  - Everything else: nobody.
  The board's `playerBoards` and `siteProjection` read the same knowledge.
- **The move choke point.** `CardMovementOperations.applyCardMoves` (`gameplay/operations/CardMovementOperations.scala:197-217`) receives every card relocation: `Move`, `Give`, `Take`, `Play`, `Draw`, `Discard`, `Bury`, `Swap` and `Exchange` all flatten to card `Move` or `Bury` leaves. It indexes each card's `LocatedCard` (old container) before removing and inserting. `CardTransfer.to.location` is the destination.
- **Walker events.** `WalkerStepRecorded(nodeId, payload, ops, contributions)`. `ChoicePayload(decisionId, answer, by)`. `RollPayload(pool, faces, automatic)` with empty `ops`. `WalkerParked(procedure, at, answered, modifiers, startArgs)`. `WalkerCompleted(procedure)`. `DeltaMeaning.OperationApplied(label)` is the plain delta payload.
- **Parked state.** The state before a `ChoicePayload` event is the parked state: `current.walkerPending`, `walkerProcedure`, `walkerStartArgs` and `walkerModifiers` are set. `ProcedureWalker.parkedDecide(ready, tree, pending, powers)` returns the parked `Decide`. `WalkerDecisionProjector` already rebuilds the tree through `rebuild(ready, procedure, activePlayer, args)` and selects powers through `WalkerPowers.selected(walkerPowerCatalog, modifiers)`.
- **Options.** `DecisionOption.Button(ref, label)`, plus `Priced(option, price)` and `Badged(option, badge)`, which wrap another option. `DecisionOptionRef` has eleven cases: `Button(key)`, `Player(id)`, `Site(id)`, `Denizen(id)`, `Relic(id)`, `Vision(id)`, `Edifice(id)`, `RelicSlot(owner, slot)`, `Banner(banner)`, `Deck(id: CardDeck)` and `FavorBank(suit)`. `DecisionQuery.ChooseOne(options, heading)`, `ChooseMany(min, max, options, heading)`, `ChooseAmount(min, max, heading, confirmLabel, suggested)`.
- **Setup.** One `TriggeredProcedureRef.Setup` run for every player. Each player answers `setup.pawn-placement.{player}` and `setup.adviser-choice.{player}` themselves. After each answer, a `BuildOps` step records one of two things:
  - `Move(Piece.Pawn(p), PlayArea(p) -> Site(s))`.
  - `Move(Card(kept), Hand(p) -> PlayArea(p), Some(FaceDown))` for the kept card, plus a `Move` of each rejected card to a regional discard.
- **Negotiation settlement.** One batch of `Peek(recipient, card, PlayArea(owner) | Site(site))` disclosures, then `Give(Piece.Favor(n) | Piece.Card(relic), giver, PlayArea(giver), PlayArea(recipient))` transfers. A deal gives only favor and relics. Participants propose their own terms in `ProposeTerms(NegotiationTerms(transfers, disclosures))`; `NegotiationDisclosure(recipient, NegotiationDisclosureRef.Adviser(owner, card))` discloses a face-down adviser.
- **Detail operations.** Composites recorded by a `BuildOps` keep their composite shape. The same composite placed directly in a tree is recorded as its `Move` leaves.
  - Composites: `Gain.Favor(player, suit, amount)`, `Gain.Secrets(player, amount)`, `Draw(player, cards, source, destination)`, `Discard.Denizen(card, from, to: Region, suit, favor, secrets, actingPlayer, required)`, `Discard.Vision(card, from, to: Region, required)`, `Reveal(card, at)`, `Take(piece, player, from, to, sourcePosition)`.
  - Primitives: `Bury(card: BuryableCard, from, required)`, `Peek(viewer, card, at)`, `Move(piece, from, to, resultingOrientation)`.
  - Other shapes: `Kill(warbands, from)` flattens to a `Move` to the warband bank. `Burn` is a sealed trait with `resource` and `from`.
  - Card Play's own placement discard is a `Discard.Denizen` or `Discard.Vision` (`actions/CardPlay.scala:283-323`).
- **Standalone events.** These are plain `OathEvent`s outside any walker run: `SiteRelicsPeeked(playerId, siteId, relics)`, `OwnedRelicRevealed(playerId, relicId)`, `WarbandsMoved(playerId, siteId, toSite, amount, priorBoardWarbands, priorSiteWarbands)`, `BanditsRefilled(sites: Vector[(SiteId, Int)])` and `UsurperFlipped(playerId)`. Commands `PeekSiteRelics`, `RevealOwnedRelic` and `MoveWarbands` produce the first three in Act.
- **Campaign.** `CampaignIds` (`actions/campaign/CampaignSetup.scala:7-32`) holds the ids: `kind`, `defender`, `targets`, `force` (always asked), `attackerPlan`, `defenderPlan`, `sacrifice` (asked only when a force warband survives the skulls), `placement`, `relocation` and `planSacrifice`. It also holds `attackPool = PoolKey("campaign.attack")`, `defensePool = PoolKey("campaign.defense")`, and `finish = Button("finish")`, the answer that ends a plan loop.
  - `CampaignSetup.setup(ready, actor, pending): Option[CampaignSetup(actor, kind, origin, defender, targetSites, raidTargets, force)]`. `CampaignBattle.printedDefense(catalog, setup)`.
  - Results are written as `ModifyRollOutcome(attackPool, Some(skulls), Some(score))` and `ModifyRollOutcome(defensePool, None, Some(score))`, then `RecordCampaignResult(result)`.
  - The outcome: `Kill`s; on a Conquest win, `Move` of warbands back to a player defender and `Move(Warbands, PlayArea(attacker) -> Site(s))` placements. On a Raid win, one transfer batch of `Take` relics and banners, `Move` of face-down advisers to a regional discard, `Move` of face-down relics to `SetAsideRelics` and a `Burn` of favor, then `Move(Pawn(defender), Site -> Site)`.
  - A face-down defending plan card is flipped by `Move(Card(id), PlayArea(p) -> PlayArea(p), Some(FaceUp))`.
  - A bandit defender applies every free, applicable plan with no decision (`CampaignPlanChoice.banditPlans`). Each application's last child records `ModifyDicePool(CampaignPlans.appliedMarker(CampaignPlans.refOf(source)), 1)`, where `appliedMarker(ref) = PoolKey(s"campaign.plan-applied.${ref.kind}.${ref.wireId}")`. The pool is never rolled. A kind holds no dot. `DecisionOptionRef.fromWire(kind, wireId)` parses a kind and wire id back into a ref.
  - `AttackDieFace { HollowSword, OneSword, TwoSwordsSkull }`, `DefenseDieFace { Blank, OneShield, TwoShields, Doubler }`. Their wire names (`hollow-sword`, `one-sword`, `two-swords-skull`, `blank`, `one-shield`, `two-shields`, `doubler`) are the ones the frontend's `DieFace` draws.
- **The campaign result panel** is fed by one chain, and nothing else reads it. Every file in the chain:
  - Frontend: `CampaignResultPanel.scala`, called at `ActionDecisionRenderer.scala:285`, and the `CampaignResultState` alias in `frontend.scala:4-5`.
  - Shared: `CampaignResultProjection` (`ActionProjectionDtos.scala:323-340`), `CampaignResultProjectionCodec.scala`, `GameProjection.lastCampaign` (`GameProjectionDto.scala:36`) and its codec lines (`GameProjectionCodec.scala:22,79,139,148`).
  - Server: `CampaignResultProjector.scala`, called at `GameProjection.scala:113`.
  - Tests: `CampaignResultPanelSuite`, `CampaignResultProjectionSuite`, and `ProjectionProtocolSuite`'s "a Campaign result round-trips" test.
  `DieFace` stays, because the Recover panel uses it. `RecordCampaignResult`, its serialization codec, and `lastCampaignResult` on game state stay: the rules read them.
- **A selectable modifier.** Augury (`DenizenId("56")`, `Augury.id = PowerId("denizen.augury")`) is a free Search modifier the player selects. `ParkedServiceFixture.withWorldDeckTop(chronicle, orders, cards)` puts a card on top of the world deck after setup's deal. `ParkedServiceFixture.topOfWorldDeck(card, to)` is the arranging `Move`.
- **Scripts.** `LogScripts` (`src/test/scala/oathdigital/application/gamelog/LogScripts.scala`) builds every journal through `GameApplicationService`. A `Situation`'s `Answers` receive a `Park(decide, ready, awaiting, procedure)`. `Situation.defaultAnswer` answers each decision type as follows:
  - ChooseOne: the first option.
  - Partition: keep the first option and put the rest in another section.
  - ChooseMany: the first `min` options.
  - ChooseAmount: `min`.
  sbt runs tests in-process from the project root, so relative paths such as `src/test/resources/gamelog` resolve from the root.

---

## File Structure

**Server, new:**

- `src/main/scala/oathdigital/gameplay/operations/CardKnowledgeMoves.scala`: knowledge follows every card move.
- `src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala`: a chosen option as spans.
- `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`: decisions, rolls and deltas inside a run, and the narrated list.
- `src/main/scala/oathdigital/application/gamelog/EventLines.scala`: the five standalone events.
- `src/main/scala/oathdigital/application/gamelog/SetupLines.scala`: pawn placements and kept advisers.
- `src/main/scala/oathdigital/application/gamelog/NegotiationLines.scala`: Negotiation's lines, moved out of `ActionLines`, plus the settlement.
- `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala`: every Campaign line after the start line.

**Server, modified:** `gameplay/operations/CardMovementOperations.scala`, `application/WalkerDecisionProjector.scala`, `application/gamelog/{LogEntry,LogWords,ActionLines,GameLogFormatter,GameLogProjector}.scala`, `application/GameProjection.scala`.

**Server, deleted:** `application/CampaignResultProjector.scala`.

**Shared, modified:** `protocol/projection/{ActionProjectionDtos,GameProjectionDto,GameProjectionCodec,LogProjectionDtos}.scala`. **Deleted:** `protocol/projection/CampaignResultProjectionCodec.scala`.

**Frontend, modified:** `GameLogPane.scala`, `ActionDecisionRenderer.scala`, `frontend.scala`, `frontend/styles.css` (the Task 11 polish only). **Deleted:** `CampaignResultPanel.scala`.

**Tests, new:**

- `src/test/scala/oathdigital/application/CardKnowledgeSuite.scala`.
- `src/test/scala/oathdigital/application/gamelog/` suites: `GameLogDecisionSuite`, `GameLogDetailSuite`, `GameLogEventSuite`, `GameLogSetupSuite`, `GameLogCampaignSuite`, `GameLogGoldenSuite`, and the renderer `GoldenLog`.
- The golden files `src/test/resources/gamelog/*.log`.

**Tests, modified:** `LogScripts.scala`, `GameLogExchangeSuite.scala`, `GameLogActionLineSuite.scala`, `frontend/.../GameLogPaneSuite.scala`, `shared/.../ProjectionProtocolSuite.scala`, `shared/.../LogPageCodecSuite.scala`.

**Tests, deleted:** `frontend/.../CampaignResultPanelSuite.scala`, `src/test/.../application/CampaignResultProjectionSuite.scala`.

**Docs, modified:** the spec (Task 12), `docs/ROADMAP.md`, `DESIGN.md` (the Task 11 polish).

---

### Task 1: Knowledge follows the card

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/operations/CardKnowledgeMoves.scala`
- Modify: `src/main/scala/oathdigital/gameplay/operations/CardMovementOperations.scala:197-217` (`applyCardMoves`)
- Test: `src/test/scala/oathdigital/application/CardKnowledgeSuite.scala`

**Interfaces:**
- Produces: `CardKnowledgeMoves.follow(ready: ReadyGame, moved: Vector[(Location, LocatedCard)]): ReadyGame` (private to `operations`). Every card move now updates `ready.knowledge`; nothing else changes shape.

- [ ] **Step 1: Write the failing test**

Create `src/test/scala/oathdigital/application/CardKnowledgeSuite.scala`:

```scala
package oathdigital.application

import oathdigital.gameplay.operations.OperationExecutor
import oathdigital.gameplay.setup.FirstGameSetupFixture.{catalog, initialReady}
import oathdigital.model._

/** Knowledge follows the card (spec, "Knowledge follows the card"). The
  * board and the log read knowledge through the same `identifiesAt`, so
  * these checks hold for both. */
class CardKnowledgeSuite extends munit.FunSuite:
  private val executor = new OperationExecutor
  private val presentation = new GamePresentationProjector(catalog)
  private val current = initialReady.game.current

  /** A site with a relic, and three players whose pawns stand elsewhere, so
    * the pawn-at-site rule never identifies the relic for them. */
  private val (site, relic) = current.map.sites.collectFirst {
    case (id, state) if state.relics.nonEmpty => id -> state.relics.head.id
  }.get
  private val away = current.players.filterNot(_.pawnSite.contains(site))
    .map(_.player)
  private val Vector(owner, other, third) = away.take(3): @unchecked

  private def run(ops: CoreOperation*)(using munit.Location): ReadyGame =
    ops.foldLeft(initialReady) { (ready, op) =>
      executor.execute(ready, op).fold(error => fail(s"$op: $error"), identity) }

  private def knows(ready: ReadyGame, player: PlayerId, id: CardId) =
    presentation.identifiesAt(ready, Some(player), id)

  private val taken = Move(Piece.Card(relic),
    PositionedLocation(Location.Site(site)),
    PositionedLocation(Location.PlayArea(owner)), Some(Orientation.FaceDown))

  test("a player who gives a face-down relic away still knows it"):
    val given = run(taken, Give(Piece.Card(relic), owner,
      Location.PlayArea(owner), Location.PlayArea(other)))
    assert(knows(given, owner, relic))
    assert(knows(given, other, relic))
    assert(!knows(given, third, relic))

  test("a site peeker still knows a relic after another player takes it"):
    val peeked = run(Peek(other, relic, Location.Site(site)), taken)
    assert(knows(peeked, other, relic))
    assert(!knows(peeked, third, relic))
    assertEquals(peeked.knowledge.siteRelics.getOrElse(other, Map.empty)
      .getOrElse(site, Vector.empty), Vector.empty[RelicId])

  test("a relic buried into its deck is known by nobody when it comes back"):
    val buried = run(Peek(other, relic, Location.Site(site)),
      Bury(BuryableCard.Relic(relic), PositionedLocation(Location.Site(site))))
    Vector(buried.knowledge.heldRelics, buried.knowledge.advisers)
      .foreach(known => assert(!known.values.exists(_.contains(relic)), known))
    assert(!buried.knowledge.siteRelics.values.exists(_.values
      .exists(_.contains(relic))))
    val back = executor.execute(buried, Move(Piece.Card(relic),
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Bottom),
      PositionedLocation(Location.PlayArea(owner)), Some(Orientation.FaceDown)))
      .toOption.get
    assert(!knows(back, other, relic))

  test("a face-down adviser shuffled into the world deck is forgotten"):
    val adviser = current.players.find(_.player == owner).get.advisers
      .collectFirst { case DenizenState(id, Orientation.FaceDown, _) => id }.get
    val peeked = run(Peek(other, adviser, Location.PlayArea(owner)))
    assert(knows(peeked, other, adviser))
    val gone = executor.execute(peeked, Move(Piece.Card(adviser),
      PositionedLocation(Location.PlayArea(owner)),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Bottom)))
      .toOption.get
    assert(!gone.knowledge.advisers.values.exists(_.contains(adviser)))
```

If the first game's starting adviser is a Vision rather than a Denizen for `owner`, pick the first player whose starting adviser is a `DenizenState`. The test is about the move, not the card kind.

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.CardKnowledgeSuite"`
Expected: the tests fail on the assertions, not in compilation:
- "a player who gives a face-down relic away still knows it": `knows(given, owner, relic)` is false.
- "a site peeker still knows …": the peeker is not identified after the take.
- "… buried …" and "… shuffled …": the knowledge still holds the card.

- [ ] **Step 3: Implement knowledge following the card**

Create `src/main/scala/oathdigital/gameplay/operations/CardKnowledgeMoves.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.model._

/** Knowledge follows the card (Game Log design, "Knowledge follows the
  * card"). A player who knew a card keeps knowing it when it moves; a card
  * that goes into a deck or the reliquary is known by nobody.
  *
  * `CardKnowledge` is derived during replay and never journaled, so a game
  * loaded after this change re-derives the fuller knowledge.
  */
private[operations] object CardKnowledgeMoves:
  /** `moved` pairs each card's destination with where it lay before the
    * batch. */
  def follow(ready: ReadyGame, moved: Vector[(Location, LocatedCard)])
      : ReadyGame =
    ready.copy(knowledge = moved.foldLeft(ready.knowledge) {
      case (knowledge, (to, located)) => after(knowledge, located, to) })

  private def after(knowledge: CardKnowledge, located: LocatedCard,
      to: Location): CardKnowledge =
    if hidden(to) then forget(knowledge, located.id)
    else located.location.container match
      case CardContainer.Player(owner, _) =>
        remember(knowledge, owner, located.id)
      case CardContainer.Site(site, SiteCardArea.Relics) => located.id match
        case relic: RelicId => carry(knowledge, site, relic)
        case _ => knowledge
      case _ => knowledge

  private def hidden(to: Location): Boolean = to match
    case Location.Deck(_) | Location.Reliquary => true
    case _ => false

  private def remember(knowledge: CardKnowledge, player: PlayerId,
      id: CardId): CardKnowledge = id match
    case relic: RelicId => knowledge.copy(heldRelics = knowledge.heldRelics
      .updated(player, (knowledge.heldRelics.getOrElse(player, Vector.empty)
        :+ relic).distinct))
    case card: WorldCardId => knowledge.copy(advisers = knowledge.advisers
      .updated(player, (knowledge.advisers.getOrElse(player, Vector.empty)
        :+ card).distinct))
    case _ => knowledge

  /** Every player who peeked `relic` at `site` knows it wherever it goes. */
  private def carry(knowledge: CardKnowledge, site: SiteId, relic: RelicId)
      : CardKnowledge =
    val peekers = knowledge.siteRelics.collect {
      case (player, sites)
          if sites.getOrElse(site, Vector.empty).contains(relic) => player
    }
    peekers.foldLeft(knowledge) { (known, player) =>
      val sites = known.siteRelics(player)
      remember(known.copy(siteRelics = known.siteRelics.updated(player,
        sites.updated(site, sites(site).filterNot(_ == relic)))), player, relic)
    }

  private def forget(knowledge: CardKnowledge, id: CardId): CardKnowledge =
    CardKnowledge(
      siteRelics = knowledge.siteRelics.view.mapValues(_.view
        .mapValues(_.filterNot(_ == id)).toMap).toMap,
      advisers = knowledge.advisers.view.mapValues(_.filterNot(_ == id)).toMap,
      heldRelics = knowledge.heldRelics.view
        .mapValues(_.filterNot(_ == id)).toMap)
```

In `CardMovementOperations.applyCardMoves`, change the final `yield inserted` to:

```scala
    yield CardKnowledgeMoves.follow(inserted, removedCards.map {
      case (transfer, located) => transfer.to.location -> located })
```

- [ ] **Step 4: Run the suite, then the whole server suite**

Run: `./sbtw "testOnly oathdigital.application.CardKnowledgeSuite"`
Expected: 4 tests pass.
Run: `./sbtw test`
Expected: everything passes. Record the server count as this plan's baseline plus 4. If a board-redaction test now fails because a giver or a site peeker sees a card, read it: that test pinned the bug this task fixes. Change its expectation, and name the spec section in a comment.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/operations/CardKnowledgeMoves.scala src/main/scala/oathdigital/gameplay/operations/CardMovementOperations.scala src/test/scala/oathdigital/application/CardKnowledgeSuite.scala
git commit -m "fix(rules): knowledge of a card follows it when it moves"
```

---

### Task 2: Choice words and decision lines

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala`
- Create: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` (add `parkedDecide`)
- Modify: `src/main/scala/oathdigital/application/gamelog/LogWords.scala` (add `subject`, `label`)
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogDecisionSuite.scala`

**Interfaces:**
- Produces: `WalkerDecisionProjector.parkedDecide(ready: ReadyGame): Option[Decide]`.
- Produces: `LogWords.subject(player: PlayerId, actor: PlayerId, verb: String): Vector[LogSpan]` gives "Verb " for the actor, "Blue verb " for anyone else. `LogWords.label(key: String): String` is the presentation projector's `safeLabel`.
- Produces: `ChoiceWords.option(ref, before, after, viewer): Vector[LogSpan]` and `ChoiceWords.options(refs, before, after, viewer): Vector[LogSpan]`.
- Produces: `DetailLines(words, choices)` with `lines(journal, run, at, viewer): Vector[Posted]`, and `DetailLines.narrated(decisionId): Boolean`. Tasks 3 and 4 extend `lines`.

- [ ] **Step 1: Write the failing test**

Create `src/test/scala/oathdigital/application/gamelog/GameLogDecisionSuite.scala`:

```scala
package oathdigital.application.gamelog

import LogScripts._

class GameLogDecisionSuite extends munit.FunSuite:
  private def lines(script: Script): Vector[String] =
    texts(format(script, None).filter(_.depth == 1))

  test("a power's own decision posts one Chose line naming what was shown"):
    val all = lines(usePower)
    val chose = all.filter(_.startsWith("Chose "))
    assertEquals(chose.size, 1, all)
    assert(!chose.head.contains("Button"), chose.head)
    // The line follows the power's own "Used" line.
    assert(all.indexWhere(_.startsWith("Used ")) < all.indexOf(chose.head), all)

  test("decisions an action line already tells post no Chose line"):
    Vector(search, facedownAdviser, muster, trade, recoverFailed,
      recoverSucceeded, negotiationDeclined, negotiationAgreed,
      oathkeeper, woken).foreach { script =>
      val all = lines(script)
      assert(!all.exists(_.startsWith("Chose ")), s"${script.name}: $all")
    }

  test("the narrated list names setup, card play, campaign and negotiation"):
    Vector("setup.pawn-placement.p1", "cardplay.place.denizen.12",
      "cardplay.replace.denizen.12", "campaign.force", "negotiation.deal",
      "search.cards", "recover.choice", "muster.source", "oathkeeper.recipient")
      .foreach(id => assert(DetailLines.narrated(id), id))
    assert(!DetailLines.narrated("power.whistle.target"))
    assert(!DetailLines.narrated("challenge.ribbon-site"))
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogDecisionSuite"`
Expected: compilation failure, `Not found: DetailLines`.

- [ ] **Step 3: Add `parkedDecide`, the words helpers, and `ChoiceWords`**

In `WalkerDecisionProjector` (class body, beside `project`):

```scala
  /** The `Decide` the walker is parked on in `ready`, after every power's
    * transform: the options its owner was shown. The game log reads a
    * button's label from it. */
  def parkedDecide(ready: ReadyGame): Option[Decide] =
    val current = ready.game.current
    for
      pending <- current.walkerPending
      procedure <- current.walkerProcedure
      tree <- rebuild(ready, procedure, current.turn.activePlayer,
        current.walkerStartArgs).toOption
      decide <- ProcedureWalker.parkedDecide(ready, tree, pending,
        WalkerPowers.selected(walkerPowerCatalog, current.walkerModifiers))
    yield decide
```

In `LogWords` (class body):

```scala
  /** "Verb " when `player` is the run's actor, else "Blue verb ". */
  def subject(player: PlayerId, actor: PlayerId, verb: String)
      : Vector[LogSpan] =
    if player == actor then Vector(LogSpan.Text(s"${verb.capitalize} "))
    else Vector(this.player(player), LogSpan.Text(s" $verb "))

  def label(key: String): String = presentation.safeLabel(key)
```

Create `src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.application.WalkerDecisionProjector
import oathdigital.model._
import LogSpan.Text

/** A chosen option as the log names it: a player, site, card or banner as
  * its typed reference (a card following the visibility rule), a button as
  * the label its chooser was shown. */
private[gamelog] final class ChoiceWords(words: LogWords,
    decisions: WalkerDecisionProjector):
  def options(refs: Vector[DecisionOptionRef], before: ReadyGame,
      after: ReadyGame, viewer: Option[PlayerId]): Vector[LogSpan] =
    LogWords.join(refs.map(option(_, before, after, viewer)))

  def option(ref: DecisionOptionRef, before: ReadyGame, after: ReadyGame,
      viewer: Option[PlayerId]): Vector[LogSpan] = ref match
    case DecisionOptionRef.Button(key) =>
      Vector(Text(shown(before, ref).getOrElse(words.label(key))))
    case DecisionOptionRef.Player(id) => Vector(words.player(id))
    case DecisionOptionRef.Site(id) => Vector(words.site(id))
    case DecisionOptionRef.Denizen(id) => card(id, before, after, viewer)
    case DecisionOptionRef.Relic(id) => card(id, before, after, viewer)
    case DecisionOptionRef.Vision(id) => card(id, before, after, viewer)
    case DecisionOptionRef.Edifice(id) => card(id, before, after, viewer)
    case DecisionOptionRef.RelicSlot(owner, slot) =>
      Vector(words.player(owner), Text(s"'s facedown relic (slot ${slot + 1})"))
    case DecisionOptionRef.Banner(banner) => Vector(words.banner(banner))
    case DecisionOptionRef.Deck(deck) => Vector(Text(words.label(deck.key)))
    case DecisionOptionRef.FavorBank(suit) => Vector(Text(s"the $suit bank"))

  private def card(id: CardId, before: ReadyGame, after: ReadyGame,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    words.one(words.card(id, before, after, viewer))

  /** The label the parked decision gave `ref`. */
  private def shown(before: ReadyGame, ref: DecisionOptionRef)
      : Option[String] =
    decisions.parkedDecide(before).toVector.flatMap(_.query match
      case DecisionQuery.ChooseOne(options, _) => options
      case DecisionQuery.ChooseMany(_, _, options, _) => options
      case _ => Vector.empty).flatMap(labelled(ref)).headOption

  private def labelled(ref: DecisionOptionRef)(option: DecisionOption)
      : Option[String] = option match
    case DecisionOption.Button(button, label) if button == ref => Some(label)
    case DecisionOption.Priced(inner, _) => labelled(ref)(inner)
    case DecisionOption.Badged(inner, _) => labelled(ref)(inner)
    case _ => None
```

- [ ] **Step 4: Create `DetailLines` with decision lines and wire it in**

Create `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.actions.challenge.{ChallengeProcedure,
  PlaceBannerResourceProcedure}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.walker.{ChoicePayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseOneAnswer}

/** The detail lines of a walker run (spec, "Detail lines"): decisions, rolls
  * and deltas, between a start line and the action line that closes the
  * action. A rule here stays silent wherever an action line, a start line or
  * another procedure's own lines already say the same thing.
  */
private[gamelog] final class DetailLines(words: LogWords,
    choices: ChoiceWords):
  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    decision(journal, run, at, viewer)

  private def decision(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] = journal.event(at) match
    case WalkerStepRecorded(_, ChoicePayload(id, answer, by), _, _)
        if !DetailLines.narrated(id) =>
      val refs = answer match
        case ChooseOneAnswer(ref) => Vector(ref)
        case ChooseManyAnswer(selected) => selected
        case _ => Vector.empty
      (for
        before <- journal.readyBefore(at)
        after <- journal.readyAfter(at)
        if refs.nonEmpty
      yield Posted.line(LogKind.Decision, words.subject(by, run.actor, "chose")
        ++ choices.options(refs, before, after, viewer))).toVector
    case _ => Vector.empty

private[gamelog] object DetailLines:
  /** Decisions other lines tell: Setup's own lines, Card Play's placement
    * line and the discard line of a replaced adviser, every Campaign and
    * Negotiation line, and each action line that names its choice. */
  private val NarratedPrefixes = Vector("setup.", ActionLines.PlacePrefix,
    "cardplay.replace.", "campaign.", "negotiation.")
  private val NarratedIds = Set(SearchProcedure.cardDecisionId,
    RecoverProcedure.choiceDecisionId, RecoverProcedure.relicDecisionId,
    MusterProcedure.decisionId, TradeProcedure.decisionId,
    ForgeProcedure.assignmentDecisionId, ChallengeProcedure.bannerDecisionId,
    ChallengeProcedure.amountDecisionId,
    PlaceBannerResourceProcedure.bannerDecisionId,
    PlaceBannerResourceProcedure.amountDecisionId,
    OathkeeperProcedure.recipientDecisionId)

  def narrated(decisionId: String): Boolean =
    NarratedIds(decisionId) || NarratedPrefixes.exists(decisionId.startsWith)
```

If an object named here is in a different package, find it with `grep -rn "object ChallengeProcedure" src/main/scala` and fix the import. The ids are verified; only the packages were inferred from paths.

In `GameLogFormatter`, add these fields beside the others:

```scala
  private val choices = new ChoiceWords(words,
    new WalkerDecisionProjector(catalog, presentation))
  private val details = new DetailLines(words, choices)
```

Also add `import oathdigital.application.WalkerDecisionProjector`.

A power that asks its own question parks before it records any effect. Slice 1 anchors "Used {card}" at the first effect step, so that power's "Chose …" line would come before its "Used …" line. The source card is known from the start, so a decision answer also anchors "Used". In `ActionLines.effect`, add this case before `case _ => false`:

```scala
      // A power's own question: its source is already known, so "Used"
      // comes before the "Chose" line its answer posts.
      case WalkerStepRecorded(_, _: ChoicePayload, _, _) => true
```

Action lines precede detail lines within one event, so "Used" posts first. In `walker`, post details after the action lines:

```scala
        val posted = start.toVector ++
          starts.continued(journal, begun, at).toVector ++
          actions.lines(journal, begun, at, viewer) ++
          details.lines(journal, begun, at, viewer) ++
          turnHeadlines(journal, at)
```

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: `GameLogDecisionSuite` passes (3 tests), and every Slice 1 log suite still passes, the properties suite included.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/main/scala/oathdigital/application/gamelog/ChoiceWords.scala src/main/scala/oathdigital/application/gamelog/DetailLines.scala src/main/scala/oathdigital/application/gamelog/LogWords.scala src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala src/test/scala/oathdigital/application/gamelog/GameLogDecisionSuite.scala
git commit -m "feat(log): decisions post Chose lines naming what the player was shown"
```

---

### Task 3: Rolls drawn as dice

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/LogEntry.scala` (the `Dice` span)
- Modify: `src/main/scala/oathdigital/application/gamelog/LogWords.scala` (face names)
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala` (roll lines)
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala` (wire)
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala` (doc comment on `LogSpanWire`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala`
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogDetailSuite.scala` (new), `shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala`, `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala`

**Interfaces:**
- Produces: `LogSpan.Dice(die: String, faces: Vector[String], names: Vector[String])`. `die` is `attack` or `defense`, `faces` are the wire names, and `text` is `names.mkString(", ")`.
- Produces: `LogWords.dice(faces: Vector[DieFace]): Option[LogSpan.Dice]`. It returns `None` for an empty roll or for faces of neither known die.
- Produces the wire span `LogSpanWire("dice", text, id = Some(faces.mkString(" ")), unit = Some(die))`.

- [ ] **Step 1: Write the failing server test**

Create `src/test/scala/oathdigital/application/gamelog/GameLogDetailSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.model._
import LogScripts._

class GameLogDetailSuite extends munit.FunSuite:
  private def lines(script: Script, viewer: Option[PlayerId] = None)
      : Vector[String] = texts(format(script, viewer).filter(_.depth == 1))

  test("each Recover roll posts a Rolled line whose dice travel as a dice span"):
    val entries = format(recoverFailed, None)
    val rolls = entries.filter(_.kind == LogKind.Roll)
    assertEquals(rolls.size, 2, texts(entries))
    rolls.foreach { roll =>
      assertEquals(roll.spans.head, LogSpan.Text("Rolled "))
      val dice = roll.spans.collect { case dice: LogSpan.Dice => dice }
      assertEquals(dice.map(_.die), Vector("defense"))
      assert(dice.head.faces.forall(_ == "blank"), dice)
      assertEquals(dice.head.text, dice.head.faces.map(_ => "blank")
        .mkString(", "))
    }
    // The roll sits between the spend it bought and the next choice.
    val all = lines(recoverFailed)
    assert(all.indexWhere(_.startsWith("Rolled ")) >
      all.indexWhere(_.startsWith("Started Recover")), all)
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogDetailSuite"`
Expected: compilation failure, `value Dice is not a member of object LogSpan`.

- [ ] **Step 3: Add the span, the face names and the roll line**

In `LogEntry.scala`, inside `object LogSpan`:

```scala
  /** One roll of one die kind, drawn as dice: `faces` are the wire names the
    * client's die chips know, `names` the rulebook's ("Attack" and "Defend"). */
  final case class Dice(die: String, faces: Vector[String],
      names: Vector[String]) extends LogSpan:
    def text: String = names.mkString(", ")
```

In `object LogWords`:

```scala
  /** A roll as a dice span, or nothing for no dice or an unknown die. */
  def dice(faces: Vector[DieFace]): Option[LogSpan.Dice] =
    val attack = faces.collect { case face: AttackDieFace => face }
    val defense = faces.collect { case face: DefenseDieFace => face }
    if faces.isEmpty then None
    else if attack.size == faces.size then Some(LogSpan.Dice("attack",
      attack.map(attackWire), attack.map(attackName)))
    else if defense.size == faces.size then Some(LogSpan.Dice("defense",
      defense.map(defenseWire), defense.map(defenseName)))
    else None

  private def attackWire(face: AttackDieFace): String = face match
    case AttackDieFace.HollowSword => "hollow-sword"
    case AttackDieFace.OneSword => "one-sword"
    case AttackDieFace.TwoSwordsSkull => "two-swords-skull"
  private def attackName(face: AttackDieFace): String = face match
    case AttackDieFace.HollowSword => "hollow sword"
    case AttackDieFace.OneSword => "sword"
    case AttackDieFace.TwoSwordsSkull => "two swords and a skull"
  private def defenseWire(face: DefenseDieFace): String = face match
    case DefenseDieFace.Blank => "blank"
    case DefenseDieFace.OneShield => "one-shield"
    case DefenseDieFace.TwoShields => "two-shields"
    case DefenseDieFace.Doubler => "doubler"
  private def defenseName(face: DefenseDieFace): String = face match
    case DefenseDieFace.Blank => "blank"
    case DefenseDieFace.OneShield => "shield"
    case DefenseDieFace.TwoShields => "two shields"
    case DefenseDieFace.Doubler => "doubler"
```

In `DetailLines`, make `lines` return `decision(...) ++ roll(journal, at)`. Add `RollPayload` to the walker import and `oathdigital.gameplay.actions.campaign.CampaignIds` to the imports, then add:

```scala
  private def roll(journal: LogJournal, at: Int): Vector[Posted] =
    journal.event(at) match
      case WalkerStepRecorded(_, RollPayload(pool, faces, _), _, _) =>
        LogWords.dice(faces).toVector.map(dice => Posted.line(LogKind.Roll,
          Vector(LogSpan.Text("Rolled "), dice) ++ DetailLines.forPool(pool)))
      case _ => Vector.empty
```

and in `object DetailLines`:

```scala
  def forPool(pool: PoolKey): Vector[LogSpan] =
    if pool == CampaignIds.attackPool then Vector(LogSpan.Text(" for the attack"))
    else if pool == CampaignIds.defensePool then
      Vector(LogSpan.Text(" for the defense"))
    else Vector.empty
```

In `GameLogProjector.span`, add:

```scala
    case dice: LogSpan.Dice => LogSpanWire("dice", dice.text,
      id = Some(dice.faces.mkString(" ")), unit = Some(dice.die))
```

In `LogProjectionDtos.scala`, extend the `LogSpanWire` field comments: `kind` gains `dice`, `id` holds "the face wire names, space-separated, for dice", and `unit` holds "attack | defense for dice". The codec already accepts any kind string, so it needs no change.

- [ ] **Step 4: Run the server test**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogDetailSuite"`
Expected: PASS.

- [ ] **Step 5: Write the failing codec and pane tests**

In `LogPageCodecSuite`, add a round-trip case for a dice span:

```scala
  test("a dice span round-trips with its faces and die"):
    val page = LogPageWire("g", 0, 9, Vector(LogEntryWire(7, 0, "roll", 1,
      Vector(LogSpanWire("text", "Rolled "), LogSpanWire("dice",
        "sword, two swords and a skull", id = Some("one-sword two-swords-skull"),
        unit = Some("attack"))))))
    assertEquals(LogPageCodec.decode(LogPageCodec.encode(page)), Right(page))
```

Match the suite's existing encode and decode calls if they differ from `LogPageCodec.encode(page)` and `LogPageCodec.decode(...)`.

In `GameLogPaneSuite`, add:

```scala
  test("a dice span draws the die-face chips, one per face"):
    val content = box(0, 0, 0)
    val roll = entry(7, "roll", 1, LogSpanWire("text", "Rolled "),
      LogSpanWire("dice", "sword, blank", id = Some("one-sword blank"),
        unit = Some("attack")), LogSpanWire("text", " for the attack"))
    new GameLogPane(content).show("g|red", Vector(roll), Map.empty)
    val faces = content.querySelectorAll(".die-faces .die-face")
    assertEquals(faces.length, 2)
    assertEquals(faces(0).getAttribute("aria-label"), "one sword")
    assertEquals(content.querySelector(".die-faces").getAttribute("class"),
      "die-faces log-dice log-dice-attack")
```

- [ ] **Step 6: Run them to verify they fail**

Run: `./sbtw "sharedJVM/testOnly *LogPageCodecSuite" "frontend/testOnly oathdigital.frontend.GameLogPaneSuite"`. If the shared suites run under the root project, use `"testOnly *LogPageCodecSuite"` instead; see how Slice 1's Task 9 ran it.
Expected: the codec case passes already (kinds are open). The pane case fails: no `.die-faces` element.

- [ ] **Step 7: Draw dice in the pane**

In `GameLogPane.spanNode`, add before the fallback:

```scala
    case "dice" => span.id.fold[dom.Node](dom.document.createTextNode(span.text)) {
      faces =>
        val dice = DieFace.roll(faces.split(" ").toVector.filter(_.nonEmpty))
        dice.setAttribute("class", s"die-faces log-dice log-dice-${span.unit
          .getOrElse("unknown")}")
        dice
    }
```

- [ ] **Step 8: Run both, then commit**

Run: `./sbtw "frontend/testOnly oathdigital.frontend.GameLogPaneSuite"` and the codec suite.
Expected: PASS.

```bash
git add src/main/scala/oathdigital/application/gamelog/LogEntry.scala src/main/scala/oathdigital/application/gamelog/LogWords.scala src/main/scala/oathdigital/application/gamelog/DetailLines.scala src/main/scala/oathdigital/application/gamelog/GameLogProjector.scala shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala src/test/scala/oathdigital/application/gamelog/GameLogDetailSuite.scala shared/src/test/scala/oathdigital/protocol/LogPageCodecSuite.scala frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala
git commit -m "feat(log): rolls post as dice the pane draws as die-face chips"
```

---

### Task 4: Delta lines, and Search's placement

**Files:**
- Modify: `src/main/scala/oathdigital/application/gamelog/DetailLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` (Search)
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogDetailSuite.scala`

**Interfaces:**
- Consumes: `LogWords.subject`, `LogWords.card`, `LogWords.cards`, `ActionLines.PlacePrefix`, `ActionLines.subjectCard`, `ActionLines.plural`.
- Produces: `DetailLines.parts(operation: CoreOperation): Vector[CoreOperation]`, the recorded operation broken down only as far as the detail rules match.

- [ ] **Step 1: Write the failing tests**

Add to `GameLogDetailSuite`:

```scala
  test("an arranged favor gain reads as a gain from its bank"):
    val all = lines(trade)
    assert(all.exists(line =>
      "^Gained 1 favor from the \\w+ bank$".r.matches(line)), all)

  test("Trade's own gain is its action line, not a second gain line"):
    val all = lines(trade)
    assertEquals(all.count(_.startsWith("Gained ")), 1, all)

  test("Search: the kept card's placement posts after the draw line"):
    val all = lines(search)
    val drew = all.indexWhere(_.startsWith("Drew "))
    val placed = all.indexWhere(line => line.startsWith("Discarded ") ||
      line.startsWith("Played "))
    assert(drew >= 0 && placed > drew, all)

  test("Search: the cards not kept are discarded to a regional discard"):
    val all = lines(search)
    assert(all.exists(line =>
      "^Discarded .+ to the (Cradle|Provinces|Hinterland) discard$".r
        .matches(line)), all)

  test("the placement discard is told once, by the action line"):
    val all = lines(facedownAdviser)
    assertEquals(all.count(_.startsWith("Discarded ")), 1, all)
    assert(!all.exists(_.endsWith(" discard")), all)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogDetailSuite"`
Expected: the gain, Search placement and Search discard tests fail; the other two pass for now.

- [ ] **Step 3: Implement the delta lines**

In `DetailLines`, make `lines` return `decision(...) ++ roll(journal, at) ++ deltas(journal, run, at, viewer)`. Add `Draw`, `Discard`, `Bury`, `Peek`, `Reveal`, `Gain` and `Move` to what `oathdigital.model._` already covers, and `GamePresentationProjector` from `oathdigital.application` for `orientationOf`. Then add:

```scala
  /** Resource and card changes inside a run, in batch order. Consecutive
    * discards to the same pile by the same player read as one line. */
  private def deltas(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val placedDiscard = discardedPlacement(journal, run, at)
    val found = journal.ops(at).flatMap(step =>
      DetailLines.parts(step.operation).flatMap(part =>
        delta(run, OpStep(part, step.before, step.after), placedDiscard,
          viewer)))
    found.foldLeft(Vector.empty[DetailLines.Found]) {
      case (done :+ DetailLines.Discarded(who, region, cards),
          DetailLines.Discarded(next, again, more))
          if who == next && region == again =>
        done :+ DetailLines.Discarded(who, region, cards ++ more)
      case (done, next) => done :+ next
    }.map {
      case DetailLines.Line(posted) => posted
      case DetailLines.Discarded(who, region, cards) =>
        Posted.line(LogKind.Delta, who ++ words.cards(cards) ++ Vector(
          LogSpan.Text(s" to the ${region.key.capitalize} discard")))
    }

  private def delta(run: Run, step: OpStep, placedDiscard: Option[CardId],
      viewer: Option[PlayerId]): Vector[DetailLines.Found] =
    import DetailLines.{Discarded, Line}
    val actor = run.actor
    val OpStep(operation, before, after) = step
    def line(spans: Vector[LogSpan]) =
      Vector(Line(Posted.line(LogKind.Delta, spans)))
    def card(id: CardId) = words.card(id, before, after, viewer)
    def gainedFavor(player: PlayerId, suit: Suit, amount: Int) =
      if run.procedure == ActionRef.Trade && player == actor then Vector.empty
      else line(words.subject(player, actor, "gained") :+
        LogSpan.Text(s"$amount favor from the $suit bank"))
    def gainedSecrets(player: PlayerId, amount: Int) =
      if run.procedure == ActionRef.Trade && player == actor then Vector.empty
      else line(words.subject(player, actor, "gained") :+ LogSpan.Text(
        s"$amount ${ActionLines.plural(amount, "secret", "secrets")}"))
    operation match
      case Gain.Favor(player, suit, amount) => gainedFavor(player, suit, amount)
      case Move(Piece.Favor(amount),
          PositionedLocation(Location.FavorBank(suit), _),
          PositionedLocation(Location.PlayArea(player), _), _) =>
        gainedFavor(player, suit, amount)
      case Gain.Secrets(player, amount) => gainedSecrets(player, amount)
      case Move(Piece.Secrets(amount), PositionedLocation(Location.SharedBank, _),
          PositionedLocation(Location.PlayArea(player), _), _) =>
        gainedSecrets(player, amount)
      case Move(Piece.Warbands(_, amount), _,
          PositionedLocation(Location.Site(site), _), _)
          if run.procedure != ActionRef.Campaign =>
        line(Vector(LogSpan.Text(s"Moved $amount " +
          ActionLines.plural(amount, "warband", "warbands") + " to "),
          words.site(site)))
      case Draw(player, cards, _, _) if run.procedure != ActionRef.Search =>
        line(words.subject(player, actor, "drew") ++
          words.cards(cards.map(card)))
      case discard: Discard.Denizen if !placedDiscard.contains(discard.card) =>
        Vector(Discarded(words.subject(discard.actingPlayer, actor,
          "discarded"), discard.to, Vector(card(discard.card))))
      case discard: Discard.Vision if !placedDiscard.contains(discard.card) =>
        Vector(Discarded(Vector(LogSpan.Text("Discarded ")), discard.to,
          Vector(card(discard.card))))
      case Bury(buried, _, _) =>
        line(LogSpan.Text("Buried ") +: words.one(card(buried.id)))
      case Peek(peeker, id, _) if run.procedure != ActionRef.Negotiation =>
        line(words.subject(peeker, actor, "peeked at") ++ words.one(card(id)))
      case Reveal(id, _) => line(LogSpan.Text("Revealed ") +: words.one(card(id)))
      case Move(Piece.Card(id), PositionedLocation(Location.PlayArea(owner), _),
          PositionedLocation(Location.PlayArea(same), _),
          Some(Orientation.FaceUp)) if owner == same && faceDown(before, id) =>
        line(words.subject(owner, actor, "revealed") ++ words.one(card(id)))
      case _ => Vector.empty

  private def faceDown(ready: ReadyGame, id: CardId): Boolean =
    CardIndex.from(ready.game).toOption.flatMap(_.get(id))
      .flatMap(located => GamePresentationProjector.orientationOf(located.state))
      .contains(Orientation.FaceDown)

  /** The card Card Play's placement answer sent to the discard, which the
    * "Discarded {card}" action line already tells. */
  private def discardedPlacement(journal: LogJournal, run: Run, at: Int)
      : Option[CardId] =
    journal.answers(run, at).reverse.collectFirst {
      case Answered(id, ChooseOneAnswer(DecisionOptionRef.Button("discard")), _)
          if id.startsWith(ActionLines.PlacePrefix) =>
        ActionLines.subjectCard(id.stripPrefix(ActionLines.PlacePrefix))
    }.flatten
```

In `object DetailLines`:

```scala
  private[gamelog] sealed trait Found
  private[gamelog] final case class Line(posted: Posted) extends Found
  private[gamelog] final case class Discarded(who: Vector[LogSpan],
      region: Region, cards: Vector[CardWord]) extends Found

  /** A recorded operation broken down only as far as a detail rule reads:
    * the composites the rules name stay whole; other composites open into
    * their children. */
  def parts(operation: CoreOperation): Vector[CoreOperation] = operation match
    case _: Gain.Favor | _: Gain.Secrets | _: Draw | _: Discard.Denizen |
        _: Discard.Vision | _: Reveal => Vector(operation)
    case _: PrimitiveOperation => Vector(operation)
    case composite => composite.children.collect {
      case child: CoreOperation => child }.flatMap(parts)
```

Check the field names that `Discard.Denizen` and `Discard.Vision` expose, `card` and `to`, against `CoreOperations.scala:169-185`. They are the constructor parameters, so they are public.

- [ ] **Step 4: Post Search's placement line**

In `ActionLines`, change the Search case to post the Card Play line too:

```scala
      case ActionRef.Search =>
        search(journal, run, at, viewer) ++
          playedAdviser(journal, run, at, viewer)
```

In `search`, the single-card rule becomes: post at the placement answer, or at completion only when no placement answer came. Replace the `WalkerCompleted` case with:

```scala
        case WalkerStepRecorded(_, ChoicePayload(id, _, _), _, _)
            if drawn.size == 1 && id.startsWith(PlacePrefix) =>
          Some(drawn.map(_._1))
        case _: WalkerCompleted if drawn.size == 1 && !journal.answers(run, at)
            .exists(_.decisionId.startsWith(PlacePrefix)) =>
          Some(drawn.map(_._1))
```

- [ ] **Step 5: Run the log suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: all pass, the properties suite included.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/DetailLines.scala src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/test/scala/oathdigital/application/gamelog/GameLogDetailSuite.scala
git commit -m "feat(log): gains, draws, discards, buries, peeks, reveals and warbands post as detail lines"
```

---

### Task 5: Minor actions and state-based triggers

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/EventLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala`
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (`revealRelic`)
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogEventSuite.scala`

**Interfaces:**
- Produces: `EventLines(words).lines(journal: LogJournal, at: Int, viewer: Option[PlayerId]): Vector[Posted]`.
- Produces: `LogScripts.revealRelic: Script`, a successful Recover followed by revealing the recovered relic.

- [ ] **Step 1: Write the failing tests**

Add to `LogScripts`, after `recoverSucceeded`:

```scala
  /** A successful Recover, then the recovered relic revealed as a minor
    * action. */
  def revealRelic(using munit.Location): Script =
    val (service, act) = recovering("reveal-relic", steadyDice)
    val actor = active(act)
    val recovered = start(act, ActionRef.Recover)
    val relic = recovered.ready.game.current.players.find(_.player == actor)
      .get.relics.collectFirst {
        case RelicState(id, Orientation.FaceDown, _) => id }.get
    recovered.after(GameCommand.RevealOwnedRelic(actor, relic))
    Script("reveal-relic", service, actor)
```

Add `revealRelic` to `all`.

Create `src/test/scala/oathdigital/application/gamelog/GameLogEventSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.engine.{RecordedEvent, ReplayStep}
import oathdigital.gameplay.operations.OperationExecutor
import oathdigital.model._
import LogScripts._

class GameLogEventSuite extends munit.FunSuite:
  private def lines(entries: Vector[LogEntry]) =
    texts(entries.filter(_.depth == 1))

  /** `script`'s journal with one event appended, from its last state to
    * `after`. */
  private def ending(script: Script, event: OathEvent,
      after: OathState => OathState = identity, viewer: Option[PlayerId])
      : Vector[LogEntry] =
    val steps = script.history.steps
    val last = steps.last.after
    formatter.format(steps :+ ReplayStep(RecordedEvent(steps.size.toLong,
      event), last, after(last)), viewer)

  private def ready(state: OathState): ReadyGame = state match
    case OathState.Ready(ready) => ready
    case other => fail(s"expected a ready game, got $other")

  test("a revealed relic is named for everyone"):
    val script = revealRelic
    Vector(Some(script.actor), None).foreach { viewer =>
      val revealed = format(script, viewer).last
      assertEquals(revealed.kind, LogKind.Delta)
      assert(text(revealed).startsWith("Revealed "), text(revealed))
      assert(revealed.spans.exists(_.isInstanceOf[LogSpan.Card]),
        revealed.spans)
    }

  test("a site peek names the relics for the peeker only"):
    val script = woken
    val state = ready(script.history.steps.last.after)
    val (site, relics) = state.game.current.map.sites.collectFirst {
      case (id, site) if site.relics.nonEmpty => id -> site.relics.map(_.id)
    }.get
    val peeker = script.actor
    val peeked = (state: OathState) => OathState.Ready(relics.foldLeft(ready(state))(
      (known, relic) => new OperationExecutor().execute(known,
        Peek(peeker, relic, Location.Site(site))).toOption.get))
    val event = OathEvent.SiteRelicsPeeked(peeker, site, relics)
    val mine = ending(script, event, peeked, Some(peeker)).last
    assert(mine.spans.exists(_.isInstanceOf[LogSpan.Card]), mine.spans)
    assert(text(mine).startsWith("Peeked at "), text(mine))
    // A player whose pawn stands at the site sees its relics anyway.
    val other = state.game.current.players.find(player =>
      player.player != peeker && !player.pawnSite.contains(site)).map(_.player)
    val theirs = ending(script, event, peeked, other).last
    assertEquals(text(theirs), s"${name(peeker)} peeked at " +
      (if relics.size == 1 then "a Relic" else s"${relics.size} Relics") +
      s" at ${presentation.siteLabel(site)}")

  test("warbands moved, bandits returned and a new Usurper each post one line"):
    val script = woken
    val state = ready(script.history.steps.last.after)
    val site = state.game.current.map.inPlay.head
    val other = script.players.find(_ != script.actor).get
    val cases = Vector(
      OathEvent.WarbandsMoved(script.actor, site, true, 2, 3, 1) ->
        s"Moved 2 warbands to ${presentation.siteLabel(site)}",
      OathEvent.WarbandsMoved(other, site, false, 1, 3, 2) ->
        s"${name(other)} moved 1 warband from ${presentation.siteLabel(site)}",
      OathEvent.BanditsRefilled(Vector(site -> 1)) ->
        s"Bandits returned to ${presentation.siteLabel(site)}",
      OathEvent.UsurperFlipped(other) -> s"${name(other)} became the Usurper")
    cases.foreach { case (event, expected) =>
      assertEquals(lines(ending(script, event, viewer = None)).last, expected)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogEventSuite"`
Expected: all three fail, because the formatter posts nothing for these events.

- [ ] **Step 3: Implement `EventLines` and wire it in**

Create `src/main/scala/oathdigital/application/gamelog/EventLines.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.model._
import LogSpan.Text

/** The events that happen outside any walker run (spec, "Detail lines"):
  * three minor actions, and two state-based changes at Wake. The subject is
  * left out when the active player acts. */
private[gamelog] final class EventLines(words: LogWords):
  def lines(journal: LogJournal, at: Int, viewer: Option[PlayerId])
      : Vector[Posted] =
    (for
      before <- journal.readyBefore(at)
      after <- journal.readyAfter(at)
    yield
      val active = before.game.current.turn.activePlayer
      def card(id: CardId) = words.card(id, before, after, viewer)
      journal.event(at) match
        case OathEvent.SiteRelicsPeeked(player, site, relics) =>
          Vector(Posted.line(LogKind.Delta, words.subject(player, active,
            "peeked at") ++ words.cards(relics.map(card)) ++
            Vector(Text(" at "), words.site(site))))
        case OathEvent.OwnedRelicRevealed(player, relic) =>
          Vector(Posted.line(LogKind.Delta, words.subject(player, active,
            "revealed") ++ words.one(card(relic))))
        case OathEvent.WarbandsMoved(player, site, toSite, amount, _, _) =>
          Vector(Posted.line(LogKind.Delta, words.subject(player, active,
            "moved") ++ Vector(Text(s"$amount " +
              ActionLines.plural(amount, "warband", "warbands") +
              (if toSite then " to " else " from ")), words.site(site))))
        case OathEvent.BanditsRefilled(sites) =>
          Vector(Posted.line(LogKind.Trigger, Text("Bandits returned to ") +:
            LogWords.join(sites.map { case (site, _) =>
              Vector(words.site(site)) })))
        case OathEvent.UsurperFlipped(player) =>
          Vector(Posted.line(LogKind.Trigger, Vector(words.player(player),
            Text(" became the Usurper"))))
        case _ => Vector.empty
    ).getOrElse(Vector.empty)
```

In `GameLogFormatter`, add `private val events = new EventLines(words)`. Replace the Slice 1 silent branch for these five events with:

```scala
      case _: OathEvent.SiteRelicsPeeked | _: OathEvent.OwnedRelicRevealed |
          _: OathEvent.WarbandsMoved | _: OathEvent.BanditsRefilled |
          _: OathEvent.UsurperFlipped =>
        (events.lines(journal, at, viewer), run)
```

- [ ] **Step 4: Run the log suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: all pass, the properties suite over `revealRelic` included.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/EventLines.scala src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/GameLogEventSuite.scala
git commit -m "feat(log): minor actions and state-based triggers post lines"
```

---

### Task 6: Setup lines

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/SetupLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala` (the Setup case)
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogSetupSuite.scala`

**Interfaces:**
- Produces: `SetupLines(words).lines(journal, at, viewer): Vector[Posted]`.

- [ ] **Step 1: Write the failing test**

Create `src/test/scala/oathdigital/application/gamelog/GameLogSetupSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.model._
import LogScripts._

class GameLogSetupSuite extends munit.FunSuite:
  private def setupLines(script: Script, viewer: Option[PlayerId])
      : Vector[LogEntry] = format(script, viewer)
    .takeWhile(entry => text(entry) != "Round 1").filter(_.depth == 1)

  test("every player places a pawn and keeps an adviser, in turn order"):
    val script = woken
    val lines = texts(setupLines(script, None))
    assertEquals(lines.count(_.contains(" placed pawn at ")),
      script.players.size, lines)
    assertEquals(lines.count(_.contains(" kept ")), script.players.size, lines)

  test("a kept adviser is named to its owner and read by its back by others"):
    val script = woken
    val mine = setupLines(script, Some(script.actor))
      .filter(entry => text(entry).contains(" kept "))
    val own = mine.find(_.spans.head == LogSpan.Player(script.actor.value,
      name(script.actor))).get
    assert(own.spans.exists(_.isInstanceOf[LogSpan.Card]), own.spans)
    mine.filterNot(_ == own).foreach { entry =>
      assert("^.+ kept a (Denizen|Vision)$".r.matches(text(entry)), text(entry))
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogSetupSuite"`
Expected: both fail, with no setup lines.

- [ ] **Step 3: Implement**

Create `src/main/scala/oathdigital/application/gamelog/SetupLines.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.model._
import LogSpan.Text

/** Setup's lines (spec, "Setup lines"): one per pawn placed and one per
  * adviser kept. Setup has no turn yet, so every line names its player. */
private[gamelog] final class SetupLines(words: LogWords):
  def lines(journal: LogJournal, at: Int, viewer: Option[PlayerId])
      : Vector[Posted] = journal.ops(at).collect {
    case OpStep(Move(Piece.Pawn(player), _,
        PositionedLocation(Location.Site(site), _), _), _, _) =>
      Posted.line(LogKind.Action, Vector(words.player(player),
        Text(" placed pawn at "), words.site(site)))
    case OpStep(Move(Piece.Card(card),
        PositionedLocation(Location.Hand(holder), _),
        PositionedLocation(Location.PlayArea(owner), _), _), before, after)
        if holder == owner =>
      Posted.line(LogKind.Decision, Vector(words.player(owner),
        Text(" kept ")) ++ words.one(words.card(card, before, after, viewer)))
  }
```

In `ActionLines`, add `private val setup = new SetupLines(words)` and replace `case TriggeredProcedureRef.Setup => Vector.empty` with:

```scala
      case TriggeredProcedureRef.Setup => setup.lines(journal, at, viewer)
```

- [ ] **Step 4: Run the log suites and commit**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: all pass.

```bash
git add src/main/scala/oathdigital/application/gamelog/SetupLines.scala src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/test/scala/oathdigital/application/gamelog/GameLogSetupSuite.scala
git commit -m "feat(log): setup posts each pawn placement and each kept adviser"
```

---

### Task 7: Negotiation's settlement

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/NegotiationLines.scala` (moves `negotiation` and `negotiators` out of `ActionLines`)
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/LogWords.scala` (`slotted`)
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (`negotiationDisclosed`)
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogExchangeSuite.scala`

**Interfaces:**
- Produces: `LogWords.slotted(id: CardId, owner: PlayerId, before: ReadyGame, after: ReadyGame, viewer: Option[PlayerId]): Vector[LogSpan]`.
- Produces: `NegotiationLines(words).lines(journal, run, at, viewer): Vector[Posted]`.
- Produces: `LogScripts.negotiationDisclosed: Script`.

- [ ] **Step 1: Write the failing tests**

Add to `LogScripts`, beside the other negotiation scripts, with imports `NegotiationDisclosure`, `NegotiationDisclosureRef` and `NegotiationTerms` from `oathdigital.model` (already covered by `oathdigital.model._`):

```scala
  /** The partner shows the actor its facedown starting adviser. */
  def negotiationDisclosed(using munit.Location): Script =
    val (service, parked, actor, partner) =
      negotiating("negotiation-disclosed")
    val adviser = parked.ready.game.current.players
      .find(_.player == partner).get.advisers.collectFirst {
        case DenizenState(id, Orientation.FaceDown, _) => id: WorldCardId
        case VisionState(id, Orientation.FaceDown) => id: WorldCardId
      }.get
    parked
      .parkedAfter(deal(partner, ProposeTerms(NegotiationTerms(Vector.empty,
        Vector(NegotiationDisclosure(actor,
          NegotiationDisclosureRef.Adviser(partner, adviser)))))))
      .parkedAfter(deal(actor, AcceptDeal))
      .after(deal(partner, AcceptDeal))
    Script("negotiation-disclosed", service, actor)
```

Add `negotiationDisclosed` to `all`. If the deal refuses a proposal from a player other than the actor, read `NegotiationProcedure`'s proposal rule. Then reorder the three answers the way it requires, keeping the partner as the terms' author.

Add to `GameLogExchangeSuite`:

```scala
  test("an agreed deal's favor transfer names giver and recipient"):
    val script = negotiationAgreed
    val partner = script.players.find(player => player != script.actor &&
      lines(script).exists(_.contains(name(player)))).get
    assert(lines(script).contains(
      s"${name(script.actor)} gave 1 favor to ${name(partner)}"),
      lines(script))

  test("a disclosed adviser is named to its owner and recipient, by slot to others"):
    val script = negotiationDisclosed
    val shown = (viewer: Option[PlayerId]) =>
      texts(format(script, viewer)).find(_.contains(" showed ")).get
    val partner = script.players.find(player => player != script.actor &&
      shown(None).startsWith(name(player))).get
    val third = script.players.find(player => player != script.actor &&
      player != partner).get
    assert(!shown(Some(script.actor)).endsWith("(slot 1)"),
      shown(Some(script.actor)))
    assert(!shown(Some(partner)).endsWith("(slot 1)"), shown(Some(partner)))
    assertEquals(shown(Some(third)),
      s"${name(partner)} showed ${name(script.actor)} facedown adviser (slot 1)")
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogExchangeSuite"`
Expected: the two new tests fail, with no settlement lines.

- [ ] **Step 3: Implement `slotted` and `NegotiationLines`**

In `LogWords` (class body):

```scala
  /** A card in `owner`'s adviser or relic row: named when the viewer may
    * identify it, else by its 1-based slot in that row before the operation
    * (spec, "Negotiation"). */
  def slotted(id: CardId, owner: PlayerId, before: ReadyGame,
      after: ReadyGame, viewer: Option[PlayerId]): Vector[LogSpan] =
    card(id, before, after, viewer) match
      case CardWord.Named(span) => Vector(span)
      case back: CardWord.Back =>
        val row = before.game.current.players.find(_.player == owner)
        val (noun, index) = id match
          case _: RelicId =>
            ("relic", row.fold(-1)(_.relics.indexWhere(_.id == id)))
          case _ =>
            ("adviser", row.fold(-1)(_.advisers.indexWhere(_.id == id)))
        if index < 0 then one(back)
        else Vector(LogSpan.Text(s"facedown $noun (slot ${index + 1})"))
```

Create `src/main/scala/oathdigital/application/gamelog/NegotiationLines.scala`. Move `negotiation` and `negotiators` from `ActionLines` verbatim, make `lines` the entry point, and add the settlement:

```scala
package oathdigital.application.gamelog

import oathdigital.gameplay.actions.negotiation.NegotiationDeal
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, DeclineDeal}
import ActionLines.{action, isDelta}
import LogSpan.Text

/** Negotiation's lines (spec, "Negotiation"): who negotiated or who ended
  * it, then the settlement, one line per disclosure and transfer, each with
  * its own subject. Proposals and counter-proposals post nothing. */
private[gamelog] final class NegotiationLines(words: LogWords):
  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    negotiation(journal, run, at) ++ settlement(journal, at, viewer)

  // `negotiation` and `negotiators`: moved from ActionLines unchanged.

  private def settlement(journal: LogJournal, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    def delta(spans: Vector[LogSpan]) = Posted.line(LogKind.Delta, spans)
    journal.ops(at).collect {
      case OpStep(Give(Piece.Favor(amount), giver, _,
          Location.PlayArea(recipient), _), _, _) =>
        delta(Vector(words.player(giver), Text(s" gave $amount favor to "),
          words.player(recipient)))
      case OpStep(Give(Piece.Card(id), giver, _, Location.PlayArea(recipient),
          _), before, after) =>
        delta(Vector(words.player(giver), Text(" gave ")) ++
          words.slotted(id, giver, before, after, viewer) ++
          Vector(Text(" to "), words.player(recipient)))
      case OpStep(Peek(recipient, id, Location.PlayArea(owner)), before,
          after) =>
        delta(Vector(words.player(owner), Text(" showed "),
          words.player(recipient), Text(" ")) ++
          words.slotted(id, owner, before, after, viewer))
      case OpStep(Peek(recipient, id, Location.Site(site)), before, after) =>
        delta(Vector(words.player(recipient), Text(" was shown ")) ++
          words.one(words.card(id, before, after, viewer)) ++
          Vector(Text(" at "), words.site(site)))
    }
```

The moved `negotiation` method calls `action(...)`, `words.player` and `journal.answers`, so keep its body exactly as it is in `ActionLines`. In `ActionLines`, add `private val negotiations = new NegotiationLines(words)`, replace the case with `case ActionRef.Negotiation => negotiations.lines(journal, run, at, viewer)`, and delete the two moved methods and the imports they alone used (`NegotiationDeal`, `ChooseManyAnswer`, `DeclineDeal`).

The settlement batch is one event, so the settlement lines follow "Negotiated with …" within that event, in batch order.

- [ ] **Step 4: Run the log suites and commit**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: all pass. The leak test now also covers `negotiationDisclosed`, and a slot phrase is a `Text` span, so it passes.

```bash
git add src/main/scala/oathdigital/application/gamelog/NegotiationLines.scala src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/main/scala/oathdigital/application/gamelog/LogWords.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/GameLogExchangeSuite.scala
git commit -m "feat(log): a negotiation's settlement posts each transfer and disclosure"
```

---

### Task 8: The rest of Campaign

**Files:**
- Create: `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala` (moves the "{winner} wins!" rule out of `ActionLines`)
- Modify: `src/main/scala/oathdigital/application/gamelog/ActionLines.scala`
- Modify: `src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala` (constructs `ActionLines` with the new collaborators)
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlans.scala` (`markedRef`, the inverse of `appliedMarker`)
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (`raid`)
- Test: `src/test/scala/oathdigital/application/gamelog/GameLogCampaignSuite.scala`, `src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala` (new, unless a suite for `CampaignPlans` already exists; then add to it)

**Interfaces:**
- Consumes: `ChoiceWords.option`, `LogWords.subject`, `CampaignSetup.setup`, `CampaignBattle.printedDefense`, `CampaignIds`.
- Produces: `CampaignPlans.markedRef(pool: PoolKey): Option[DecisionOptionRef]`, where `markedRef(appliedMarker(ref)) == Some(ref)` for every plan source ref, and `None` for any other pool.
- Produces: `CampaignLines(words, choices, catalog).lines(journal, run, at, viewer): Vector[Posted]`. `ActionLines` becomes `ActionLines(words, choices, catalog)`.
- Produces: `LogScripts.raid: Script`, a Raid against the player who shares the actor's site, which the attacker wins.

- [ ] **Step 1: Write the failing tests**

Add to `LogScripts` (import `oathdigital.gameplay.actions.campaign.CampaignIds`, and `ChooseAmountAnswer`, `ChooseOneAnswer` from `DecisionAnswer`):

```scala
  /** Attack dice all swords, defense dice all blank. */
  val raidDice: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.Blank)

  /** A Raid on the player whose pawn shares the actor's site. Every board
    * warband goes into the force and every survivor is sacrificed, so the
    * attack is twice the force against the defender's board warbands and
    * blank dice: the attacker wins. No battle plan is chosen. */
  def raid(using munit.Location): Script =
    val sites = FirstGameSetupFixture.sites
    val (service, _, driver) = journaled("raid", raidDice,
      Vector(sites(0), sites(0)) ++ sites.drop(1))
    val woken = Situation.wake(driver)
    val actor = active(woken)
    woken.withAnswers {
      case park if park.decisionId == CampaignIds.kind =>
        ChooseOneAnswer(DecisionOptionRef.Button("raid"))
      case Park(Decide(CampaignIds.force, _,
          DecisionQuery.ChooseAmount(_, max, _, _, _), _, _), _, _, _) =>
        ChooseAmountAnswer(max)
      case Park(Decide(CampaignIds.sacrifice, _,
          DecisionQuery.ChooseAmount(_, max, _, _, _), _, _), _, _, _) =>
        ChooseAmountAnswer(max)
      case park if park.decisionId == CampaignIds.attackerPlan ||
          park.decisionId == CampaignIds.defenderPlan =>
        ChooseOneAnswer(CampaignIds.finish)
    }.after(GameCommand.EndWake(actor),
      GameCommand.StartWalker(ActionRef.Campaign, StartPayload(actor)))
    Script("raid", service, actor)
```

Add `raid` to `all`, and import `Park` from `oathdigital.testkit`.

Create `src/test/scala/oathdigital/application/gamelog/GameLogCampaignSuite.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.engine.{RecordedEvent, ReplayStep}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.walker.{ChoicePayload, DeltaMeaning,
  WalkerCompleted, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import LogScripts._

class GameLogCampaignSuite extends munit.FunSuite:
  private def lines(script: Script, viewer: Option[PlayerId] = None) =
    texts(format(script, viewer).filter(_.depth == 1))

  test("a Raid tells its targets, pools, dice, totals, winner and losses in order"):
    val script = raid
    val all = lines(script)
    val defender = script.players.find(player => player != script.actor &&
      all.exists(_.startsWith(s"Started Campaign: Raid against ${name(player)}")))
      .get
    val expected = Vector(
      s"Started Campaign: Raid against ${name(defender)}",
      s"Targets: ${name(defender)}'s pawn",
      "Attack Pool: ",
      "Rolled ",
      "Attack: ",
      "Sacrificed ",
      "Rolled ",
      "Defense: ",
      s"${name(script.actor)} wins!",
      s"${name(defender)} ")
    val found = expected.foldLeft((0, Vector.empty[Int])) {
      case ((from, at), prefix) =>
        val index = all.indexWhere(_.startsWith(prefix), from)
        assert(index >= 0, s"no '$prefix' after line $from in $all")
        (index + 1, at :+ index)
    }._2
    assertEquals(found, found.sorted)
    assert(all.exists(_.endsWith(" for the attack")), all)
    assert(all.exists(_.endsWith(" for the defense")), all)
    // Half of a one-warband board is none, so "lost" may be absent; the
    // relocation never is.
    assert(all.last.startsWith(s"${name(defender)} ") &&
      all.last.contains(" was sent to "), all)

  test("a battle plan answer names who activated it; a flipped plan card names itself"):
    val script = woken
    val steps = script.history.steps
    val last = steps.last.after
    val ready = last match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    // Any other player whose starting adviser is a face-down Denizen.
    val (defender, plan) = ready.game.current.players
      .filter(_.player != script.actor).flatMap(player => player.advisers
        .collectFirst { case DenizenState(id, Orientation.FaceDown, _) =>
          player.player -> id }).head
    val tail = Vector[OathEvent](
      WalkerStepRecorded("plan", ChoicePayload(CampaignIds.defenderPlan,
        ChooseOneAnswer(DecisionOptionRef.Denizen(plan)), defender),
        Vector.empty, Vector.empty),
      WalkerStepRecorded("reveal", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("reveal")), Vector(Move(Piece.Card(plan),
          PositionedLocation(Location.PlayArea(defender)),
          PositionedLocation(Location.PlayArea(defender)),
          Some(Orientation.FaceUp))), Vector.empty),
      WalkerCompleted(ActionRef.Campaign))
    val entries = formatter.format(steps ++ tail.zipWithIndex.map {
      case (event, index) => ReplayStep(RecordedEvent(steps.size.toLong + index,
        event), last, last) }, None)
    val shown = texts(entries.filter(_.sequence >= steps.size))
    assert(shown.contains(s"${name(defender)} activated a Denizen"), shown)
    assert(shown.exists(line => line.startsWith(s"${name(defender)} revealed ")
      && line != s"${name(defender)} revealed a Denizen"), shown)

  test("a plan the bandits apply is named like any other activation"):
    val script = woken
    val steps = script.history.steps
    val last = steps.last.after
    val ready = last match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    // A first game deals each homeland site its edifice, which sits among
    // the site's cards; a site card is what a bandit plan's source is.
    val edifice = ready.game.current.map.sites.values.flatMap(_.denizens)
      .collectFirst { case held: EdificeState => held.id }.get
    val tail = Vector[OathEvent](
      WalkerStepRecorded("bandit-plan", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("plan")), Vector(ModifyDicePool(
          CampaignPlans.appliedMarker(DecisionOptionRef.Edifice(edifice)), 1)),
        Vector.empty),
      WalkerCompleted(ActionRef.Campaign))
    val entries = formatter.format(steps ++ tail.zipWithIndex.map {
      case (event, index) => ReplayStep(RecordedEvent(steps.size.toLong + index,
        event), last, last) }, None)
    val shown = texts(entries.filter(_.sequence >= steps.size))
    // A site card is public, so even an observer reads its name.
    assert(shown.exists(line => line.startsWith("The bandits activated ") &&
      !line.endsWith("Edifice") && !line.endsWith("Denizen") &&
      !line.contains("campaign.plan-applied")), shown)
```

Add `CampaignPlans` to the test's `oathdigital.gameplay.actions.campaign` import.

Add the round-trip test for the inverse, in `src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala`:

```scala
package oathdigital.gameplay

import oathdigital.gameplay.actions.campaign.CampaignPlans
import oathdigital.model._

class CampaignPlansSuite extends munit.FunSuite:
  test("a bandit's applied-plan marker reads back to the plan's source"):
    Vector[DecisionOptionRef](DecisionOptionRef.Denizen(DenizenId("56")),
      DecisionOptionRef.Edifice(EdificeId("edifice:homeland-arcane")),
      DecisionOptionRef.Relic(RelicId("relic:circlet")),
      DecisionOptionRef.Button("title")).foreach { ref =>
      assertEquals(CampaignPlans.markedRef(CampaignPlans.appliedMarker(ref)),
        Some(ref))
    }
    assertEquals(CampaignPlans.markedRef(PoolKey("campaign.attack")), None)
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogCampaignSuite"`
Expected: compilation failure, `value markedRef is not a member of object CampaignPlans`. After Step 3's first part adds it, run again:
- The Raid test fails at "Targets:".
- The player plan test fails at "activated". Its reveal line already comes from Task 4's `DetailLines`.
- The bandit plan test fails with no "The bandits activated" line.
If the `raid` script fails before the Campaign starts, the failure names the refusing command. The known risk is that Conquest is the only legal kind at the shared site; in that case the kind decision is not asked and the Campaign starts as a Conquest. Check `CampaignSetup.legalKinds` for the shared site. A Raid needs an enemy pawn at the actor's site, so the shared `sites(0)` spread should allow it.

- [ ] **Step 3: Implement `markedRef`, then `CampaignLines`**

In `CampaignPlans`, beside `appliedMarker`, add its inverse, so the one place that spells the marker also reads it:

```scala
  /** The plan source `appliedMarker` recorded in `pool`, or `None` for any
    * other pool. A ref's kind holds no dot, so the first dot after the
    * prefix ends it. */
  def markedRef(pool: PoolKey): Option[DecisionOptionRef] =
    Option.when(pool.value.startsWith(MarkerPrefix))(
      pool.value.stripPrefix(MarkerPrefix)).flatMap(rest =>
      rest.split("\\.", 2) match
        case Array(kind, wireId) => DecisionOptionRef.fromWire(kind, wireId)
        case _ => None)

  private val MarkerPrefix = "campaign.plan-applied."
```

Change `appliedMarker` to build its key from `MarkerPrefix` too: `PoolKey(s"$MarkerPrefix${ref.kind}.${ref.wireId}")`. Run `./sbtw "testOnly oathdigital.gameplay.CampaignPlansSuite"`; expected PASS.

Create `src/main/scala/oathdigital/application/gamelog/CampaignLines.scala`:

```scala
package oathdigital.application.gamelog

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds,
  CampaignPlans, CampaignSetup}
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseOneAnswer}
import ActionLines.{action, plural}
import LogSpan.Text

/** Every Campaign line after the start line (spec, "Campaign"), each posted
  * where its facts complete. The rolls themselves are detail lines. */
private[gamelog] final class CampaignLines(words: LogWords,
    choices: ChoiceWords, catalog: ExecutableCatalog):
  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    answered(journal, run, at, viewer) ++ recorded(journal, at, viewer) ++
      closing(journal, run, at, viewer)

  /** Targets and pools at the force answer; a battle plan at its choice; a
    * sacrifice at its amount. */
  private def answered(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] = journal.event(at) match
    case WalkerStepRecorded(_, ChoicePayload(CampaignIds.force, _, _), _, _) =>
      (for
        ready <- journal.readyAfter(at)
        setup <- CampaignSetup.setup(ready, run.actor,
          PendingTree(Vector.empty, journal.answers(run, at)))
      yield Vector(
        Posted.line(LogKind.Decision, Text("Targets: ") +:
          targets(setup, ready, viewer)),
        Posted.line(LogKind.Delta, Vector(Text(s"Attack Pool: ${setup.force}, " +
          s"Defense Pool: ${CampaignBattle.printedDefense(catalog, setup)}")))))
        .getOrElse(Vector.empty)
    case WalkerStepRecorded(_, ChoicePayload(id, ChooseOneAnswer(ref), by), _, _)
        if (id == CampaignIds.attackerPlan || id == CampaignIds.defenderPlan) &&
          ref != CampaignIds.finish =>
      (for
        before <- journal.readyBefore(at)
        after <- journal.readyAfter(at)
      yield Posted.line(LogKind.Decision, words.subject(by, run.actor,
        "activated") ++ choices.option(ref, before, after, viewer))).toVector
    case WalkerStepRecorded(_, ChoicePayload(CampaignIds.sacrifice,
        ChooseAmountAnswer(count), _), _, _) if count > 0 =>
      Vector(Posted.line(LogKind.Decision, Vector(Text(
        s"Sacrificed $count ${plural(count, "warband", "warbands")}"))))
    case _ => Vector.empty

  private def targets(setup: CampaignSetup, ready: ReadyGame,
      viewer: Option[PlayerId]): Vector[LogSpan] = setup.kind match
    case CampaignKind.Conquest =>
      LogWords.join(setup.targetSites.map(site => Vector(words.site(site))))
    case CampaignKind.Raid => LogWords.join(setup.raidTargets.map {
      case CampaignRaidTarget.Pawn(player) =>
        Vector(words.player(player), Text("'s pawn"))
      case CampaignRaidTarget.Relic(_, relic) =>
        words.one(words.card(relic, ready, ready, viewer))
      case CampaignRaidTarget.Banner(_, banner) => Vector(words.banner(banner))
    })

  /** The two totals as the result windows write them, each plan the bandits
    * applied, and the winner. */
  private def recorded(journal: LogJournal, at: Int, viewer: Option[PlayerId])
      : Vector[Posted] =
    journal.ops(at).collect {
      case OpStep(ModifyDicePool(pool, _), before, after)
          if CampaignPlans.markedRef(pool).nonEmpty =>
        Posted.line(LogKind.Decision, Text("The bandits activated ") +:
          choices.option(CampaignPlans.markedRef(pool).get, before, after,
            viewer))
      case OpStep(ModifyRollOutcome(CampaignIds.attackPool, skulls,
          Some(score)), _, _) =>
        val paid = skulls.filter(_ > 0).fold("")(count =>
          s" with $count ${plural(count, "skull", "skulls")}")
        Posted.line(LogKind.Roll, Vector(Text(s"Attack: $score$paid")))
      case OpStep(ModifyRollOutcome(CampaignIds.defensePool, _, Some(score)),
          _, _) =>
        Posted.line(LogKind.Roll, Vector(Text(s"Defense: $score")))
      case OpStep(RecordCampaignResult(result), _, _) =>
        action(
          if result.attackerWins then
            Vector(words.player(result.attacker), Text(" wins!"))
          else result.defender match
            case CampaignDefender.Player(player) =>
              Vector(words.player(player), Text(" wins!"))
            case CampaignDefender.Bandits => Vector(Text("The bandits win!")))
    }

  /** At completion: what the winner gained, then what the loser lost that
    * no earlier line says. */
  private def closing(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] = journal.event(at) match
    case _: WalkerCompleted =>
      val ops = journal.runOps(run, at).map(_._2)
      val outcome = ops.dropWhile {
        case OpStep(_: RecordCampaignResult, _, _) => false
        case _ => true
      }
      outcome.headOption.collect {
        case OpStep(RecordCampaignResult(result), _, _) => result
      }.toVector.flatMap(result => gains(result, outcome.tail, viewer) ++
        losses(result, run.actor, outcome.tail, viewer))
    case _ => Vector.empty

  private def gains(result: CampaignResult, ops: Vector[OpStep],
      viewer: Option[PlayerId]): Vector[Posted] =
    if !result.attackerWins then Vector.empty
    else result.kind match
      case CampaignKind.Raid =>
        val taken = ops.collect {
          case OpStep(Take(Piece.Card(id), _, _, _, _), before, after) =>
            words.one(words.card(id, before, after, viewer))
          case OpStep(Take(Piece.Banner(banner), _, _, _, _), _, _) =>
            Vector(words.banner(banner))
        }
        result.defender match
          case CampaignDefender.Player(defender) if taken.nonEmpty =>
            Vector(Posted.line(LogKind.Delta, Text("Took ") +:
              (LogWords.join(taken) ++ Vector(Text(" from "),
                words.player(defender)))))
          case _ => Vector.empty
      case CampaignKind.Conquest =>
        val placed = ops.collect {
          case OpStep(Move(Piece.Warbands(_, count),
              PositionedLocation(Location.PlayArea(player), _),
              PositionedLocation(Location.Site(site), _), _), _, _)
              if player == result.attacker =>
            Vector(Text(s"$count ${plural(count, "warband", "warbands")} on "),
              words.site(site))
        }
        if placed.isEmpty then Vector.empty
        else Vector(Posted.line(LogKind.Delta, Text("Placed ") +:
          LogWords.join(placed)))

  private def losses(result: CampaignResult, actor: PlayerId,
      ops: Vector[OpStep], viewer: Option[PlayerId]): Vector[Posted] =
    val loser =
      if result.attackerWins then result.defender
      else CampaignDefender.Player(result.attacker)
    val owner = loser match
      case CampaignDefender.Player(player) => Some(player)
      case CampaignDefender.Bandits => None
    def from(location: Location) = owner.exists(player =>
      location == Location.PlayArea(player))
    val killed = ops.collect {
      case OpStep(Kill(Piece.Warbands(_, count), PositionedLocation(place, _)),
          _, _) if from(place) || (loser == result.defender &&
            place.isInstanceOf[Location.Site]) => count
    }.sum
    val returned = ops.collect {
      case OpStep(Move(Piece.Warbands(_, count),
          PositionedLocation(Location.WarbandBank(_), _),
          PositionedLocation(place, _), _), _, _) if from(place) => count
    }.sum
    val lost = killed - returned
    val burned = ops.collect {
      case OpStep(burn: Burn, _, _) if from(burn.from.location) =>
        burn.resource match
          case Piece.Favor(count) => count
          case _ => 0
    }.sum
    val discarded = ops.collect {
      case OpStep(Move(Piece.Card(id), PositionedLocation(place, _),
          PositionedLocation(Location.RegionalDiscard(_), _), _), before, after)
          if from(place) => words.card(id, before, after, viewer)
    }
    val setAside = ops.collect {
      case OpStep(Move(Piece.Card(id), PositionedLocation(place, _),
          PositionedLocation(Location.SetAsideRelics, _), _), before, after)
          if from(place) => words.card(id, before, after, viewer)
    }
    val sent = ops.collect {
      case OpStep(Move(Piece.Pawn(player), _,
          PositionedLocation(Location.Site(site), _), _), _, _)
          if owner.contains(player) => site
    }.lastOption
    val parts = Vector(
      Option.when(lost > 0)(Vector[LogSpan](Text(
        s"lost $lost ${plural(lost, "warband", "warbands")}"))),
      Option.when(burned > 0)(Vector[LogSpan](Text(s"burned $burned favor"))),
      Option.when(discarded.nonEmpty)(Text("discarded ") +:
        words.cards(discarded)),
      Option.when(setAside.nonEmpty)(Text("set aside ") +:
        words.cards(setAside)),
      sent.map(site => Vector[LogSpan](Text("was sent to "), words.site(site)))
    ).flatten
    if parts.isEmpty then Vector.empty
    else
      val said = LogWords.join(parts)
      val subject: Vector[LogSpan] = owner match
        case Some(player) if player == actor => Vector.empty
        case Some(player) => Vector(words.player(player), Text(" "))
        case None => Vector(Text("The bandits "))
      val spans = if subject.nonEmpty then subject ++ said else said match
        case Text(first) +: rest => Text(first.capitalize) +: rest
        case other => other
      Vector(Posted.line(LogKind.Delta, spans))
```

In `ActionLines`, change the constructor to `ActionLines(words: LogWords, choices: ChoiceWords, catalog: ExecutableCatalog)`, add `private val campaigns = new CampaignLines(words, choices, catalog)`, and replace the Campaign case with `case ActionRef.Campaign => campaigns.lines(journal, run, at, viewer)`. In `GameLogFormatter`, construct `new ActionLines(words, choices, catalog)`. Declare `choices` before `actions`, since both are vals.

- [ ] **Step 4: Run the log suites**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: all pass, Slice 1's "Campaign: a start line naming kind and defender, and the winner" included. If the Raid test's order check fails, print `all` and compare it with the anchors in "Decisions this plan makes", item 4. A line in the wrong place means its rule reads the wrong event. Fix the rule, not the test.

- [ ] **Step 5: Commit**

```bash
git add src/main/scala/oathdigital/application/gamelog/CampaignLines.scala src/main/scala/oathdigital/application/gamelog/ActionLines.scala src/main/scala/oathdigital/application/gamelog/GameLogFormatter.scala src/main/scala/oathdigital/gameplay/actions/campaign/CampaignPlans.scala src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/GameLogCampaignSuite.scala src/test/scala/oathdigital/gameplay/CampaignPlansSuite.scala
git commit -m "feat(log): a campaign tells its targets, pools, plans (the bandits' too), totals, gains and losses"
```

---

### Task 9: The campaign result panel leaves the Actions pane

**Files:**
- Delete: `frontend/src/main/scala/oathdigital/frontend/CampaignResultPanel.scala`, `frontend/src/test/scala/oathdigital/frontend/CampaignResultPanelSuite.scala`
- Delete: `shared/src/main/scala/oathdigital/protocol/projection/CampaignResultProjectionCodec.scala`
- Delete: `src/main/scala/oathdigital/application/CampaignResultProjector.scala`, `src/test/scala/oathdigital/application/CampaignResultProjectionSuite.scala`
- Modify: `frontend/src/main/scala/oathdigital/frontend/ActionDecisionRenderer.scala:285`, `frontend/src/main/scala/oathdigital/frontend/frontend.scala:4-5`
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala:323-340`, `GameProjectionDto.scala:36`, `GameProjectionCodec.scala:22,79,139,148`
- Modify: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala` (the "a Campaign result round-trips" test)
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala:113`, `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` (the doc comment above `faceName`)

**Interfaces:**
- Removes: `GameProjection.lastCampaign` and `CampaignResultProjection` from the wire. Server and client ship together, so no old client reads a newer projection.

- [ ] **Step 1: Confirm nothing else reads the chain**

Run: `grep -rn "lastCampaign\b\|CampaignResultProjection\|CampaignResultProjector\|CampaignResultPanel\|CampaignResultState" --include=*.scala src shared frontend/src`
Expected: only the files listed above. `lastCampaignResult` on game state is a different name and stays.

- [ ] **Step 2: Remove the chain**

- Delete the five files.
- Remove the `CampaignResultPanel.render(value, panel)` line.
- Remove the two `CampaignResultState` alias lines.
- Remove `CampaignResultProjection` and its doc comment.
- Remove the `lastCampaign` field from `GameProjection`, and its four codec touchpoints: the field name in the key list, the encode pair, the decode line and the constructor argument.
- Remove `lastCampaign = CampaignResultProjector.project(context.ready),` from `GameProjection.scala`.
- Delete the "a Campaign result round-trips" test.
- In `WalkerDecisionProjector`'s comment, replace "and `CampaignResultProjector` follows the same precedent for Campaign's dice" with "and the game log's `LogWords.dice` follows the same precedent".

- [ ] **Step 3: Build and test both projects**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: all pass. The frontend count drops by the deleted panel suite's tests, and the server and shared counts drop by the deleted projection tests. Record the new counts.

- [ ] **Step 4: Commit**

```bash
git add -u frontend/src/main/scala/oathdigital/frontend frontend/src/test/scala/oathdigital/frontend shared/src/main/scala/oathdigital/protocol/projection shared/src/test/scala/oathdigital/protocol src/main/scala/oathdigital/application src/test/scala/oathdigital/application
git status --short
git commit -m "refactor: the campaign result panel leaves the Actions pane for the log"
```

`git add -u` on those directories stages only this task's modifications and deletions. Read `git status --short` before committing, and unstage anything that is not this task's.

---

### Task 10: A chosen modifier, and the golden logs

**Files:**
- Modify: `src/test/scala/oathdigital/application/gamelog/LogScripts.scala` (`augury`, `named`)
- Create: `src/test/scala/oathdigital/application/gamelog/GoldenLog.scala`
- Create: `src/test/scala/oathdigital/application/gamelog/GameLogGoldenSuite.scala`
- Create: `src/test/resources/gamelog/{script}.{actor|other}.log`, two per script
- Modify: `src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala`

**Interfaces:**
- Produces: `LogScripts.augury: Script`. `LogScripts.named: Vector[(String, () => Script)]` lists every script by its stream name. `all` becomes `named.map(_._2())`.
- Produces: `GoldenLog.render(entries: Vector[LogEntry]): String`, one entry per line, lossless over every span kind.

- [ ] **Step 1: Write the modifier script and its failing test**

Add to `LogScripts` (import `oathdigital.gameplay.powers.search.Augury`):

```scala
  /** Augury, a free Search modifier, stands at the actor's site; the Search
    * selects it. */
  def augury(using munit.Location): Script =
    val card = DenizenId("56")
    val (chronicle, orders) = ParkedServiceFixture.withWorldDeckTop(
      FirstGameSetupFixture.chronicle, FirstGameSetupFixture.orders,
      Vector(card))
    val (service, _, driver) = journaled("augury")
    val woken = Situation.wake(driver, chronicle, orders)
    val actor = active(woken)
    woken.after(Step.Arrange(Vector(ParkedServiceFixture.topOfWorldDeck(card,
        Location.Site(pawn(woken, actor))))), GameCommand.EndWake(actor))
      .after(GameCommand.StartWalker(ActionRef.Search, StartPayload(actor,
        Vector(Augury.id), Vector(DecisionOptionRef.Button("search:world")))))
    Script("augury", service, actor)

  /** Every script by its stream name, for the suites that hold for each. */
  val named: Vector[(String, () => Script)] = Vector(
    "woken" -> (() => woken), "round" -> (() => round),
    "oathkeeper" -> (() => oathkeeper), "search" -> (() => search),
    "augury" -> (() => augury),
    "facedown-adviser" -> (() => facedownAdviser),
    "muster" -> (() => muster), "trade" -> (() => trade),
    "take-wealth" -> (() => takeWealth),
    "recover-failed" -> (() => recoverFailed),
    "recover-succeeded" -> (() => recoverSucceeded),
    "reveal-relic" -> (() => revealRelic), "forge" -> (() => forge),
    "banners" -> (() => banners), "raid" -> (() => raid),
    "negotiation-declined" -> (() => negotiationDeclined),
    "negotiation-agreed" -> (() => negotiationAgreed),
    "negotiation-disclosed" -> (() => negotiationDisclosed),
    "use-power" -> (() => usePower))

  def all(using munit.Location): Vector[Script] = named.map(_._2())
```

Each lambda's call to a script needs a `munit.Location`. munit supplies one implicitly through its `Location.generate` macro, so the lambdas compile inside the object.

Add to `GameLogStartLineSuite`:

```scala
  test("a chosen modifier is named on the start line by its card"):
    val script = augury
    val start = texts(format(script, None)).find(_.startsWith("Started Search"))
      .get
    assert(start.startsWith("Started Search with Augury"), start)
    assert(start.endsWith("−2 Supply"), start)
```

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogStartLineSuite"`
Expected: PASS. Slice 1 built the modifier path without a script for it. If the start line reads anything but "Started Search with Augury", read `StartLines.modifiers` and fix it there.

- [ ] **Step 2: Write the golden renderer and suite**

Create `src/test/scala/oathdigital/application/gamelog/GoldenLog.scala`:

```scala
package oathdigital.application.gamelog

/** An exact, readable rendering of log entries for golden files: one line
  * per entry, `sequence.ordinal kind depth` then every span, a typed span in
  * brackets with its identity. Nothing a `LogEntry` holds is lost. */
object GoldenLog:
  def render(entries: Vector[LogEntry]): String =
    entries.map(entry => s"${entry.sequence}.${entry.ordinal} " +
      s"${entry.kind.key} ${entry.depth} | " + entry.spans.map(span).mkString)
      .mkString("", "\n", "\n")

  private def span(value: LogSpan): String = value match
    case LogSpan.Text(text) => text
    case LogSpan.Player(id, name) => s"[player:$id|$name]"
    case LogSpan.Card(id, name) => s"[card:$id|$name]"
    case LogSpan.Site(id, name) => s"[site:$id|$name]"
    case LogSpan.Amount(count, unit) => s"[amount:$count $unit]"
    case LogSpan.Cost(count, unit) => s"[cost:$count $unit]"
    case dice: LogSpan.Dice =>
      s"[dice:${dice.die} ${dice.faces.mkString(" ")}|${dice.text}]"
```

Create `src/test/scala/oathdigital/application/gamelog/GameLogGoldenSuite.scala`:

```scala
package oathdigital.application.gamelog

import java.nio.file.{Files, Path, Paths}
import LogScripts._

/** The exact log of every script, for its actor and for one other seat
  * (spec, "Golden tests"). `GAMELOG_GOLDEN=write` rewrites the files
  * instead of comparing; a rewritten golden is reviewed line by line before
  * it is committed. */
class GameLogGoldenSuite extends munit.FunSuite:
  private val directory: Path = Paths.get("src/test/resources/gamelog")
  private val writing = sys.env.get("GAMELOG_GOLDEN").contains("write")

  named.foreach { case (name, build) =>
    test(s"$name reads exactly as its golden log for the actor and another seat"):
      val script = build()
      val other = script.players.find(_ != script.actor).get
      Vector("actor" -> script.actor, "other" -> other).foreach {
        case (seat, viewer) =>
          val actual = GoldenLog.render(format(script, Some(viewer)))
          val file = directory.resolve(s"$name.$seat.log")
          if writing then
            Files.createDirectories(directory)
            Files.writeString(file, actual)
          else
            assert(Files.exists(file), s"missing golden $file; run with " +
              "GAMELOG_GOLDEN=write and review it")
            assertNoDiff(actual, Files.readString(file), s"$name for $seat")
      }
  }
```

- [ ] **Step 3: Run the suite to verify it fails, then write the goldens**

Run: `./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`
Expected: every test fails with "missing golden".
Run: `GAMELOG_GOLDEN=write ./sbtw "testOnly oathdigital.application.gamelog.GameLogGoldenSuite"`
Expected: 38 files are written under `src/test/resources/gamelog/`.

- [ ] **Step 4: Review every golden against the spec, with Impeccable's clarify pass**

The goldens freeze the log's copy, so review them as UX copy before committing. Load `~/.claude/skills/impeccable/reference/clarify.md` and apply it to the log lines; the review is planning and reading only, with no UI edit. For each `*.actor.log` and its `*.other.log`, check:

1. Every line's wording matches the spec's tables and this plan's Decisions. The voice is past tense, with the subject left out for the actor and named for anyone else.
2. No raw id, class name or decision key appears in any text span.
3. The `other` file holds no `[card:…]` that the actor's file names from the actor's hand or face-down row. A card that is `[card:…]` in one file and "a Denizen" in the other is correct.
4. The order within each action is start line, then details, then the action line. Campaign follows item 4's order, and Negotiation's settlement follows "Negotiated with".
5. Nothing reads twice. A detail line that repeats an action line is a missing narrated rule.

A wrong line is a formatter bug. Fix the rule in `main`, rewrite with `GAMELOG_GOLDEN=write`, and review again; never hand-edit a golden. Record in the commit message any wording that clarify improved over the spec's, so Task 12 carries it into the spec.

- [ ] **Step 5: Run the log suites and commit**

Run: `./sbtw "testOnly oathdigital.application.gamelog.*"`
Expected: all pass, the properties suite included. It now also covers `augury`, `raid`, `revealRelic` and `negotiationDisclosed`.

```bash
git add src/test/scala/oathdigital/application/gamelog/LogScripts.scala src/test/scala/oathdigital/application/gamelog/GoldenLog.scala src/test/scala/oathdigital/application/gamelog/GameLogGoldenSuite.scala src/test/scala/oathdigital/application/gamelog/GameLogStartLineSuite.scala src/test/resources/gamelog
git commit -m "test(log): golden logs for every script, and a chosen modifier's start line"
```

---

### Task 11: Polish the Log pane with Slice 2's entries

**Files:**
- Modify: `frontend/styles.css` (log entry rules only)
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala` (only if the polish finds a structural defect)
- Modify: `DESIGN.md` (the Panes and Dice sections)
- Test: `frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala`

This task follows Impeccable's `polish` command on the Log pane, run in bounded passes.

- [ ] **Step 1: Load the Impeccable playbook**

Run `~/.claude/skills/impeccable/scripts/impeccable context --target frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala` from the worktree root, if this session has not run it. Then read `reference/polish.md`. Immediately before the first CSS edit, read `reference/craft-floor.md`.

- [ ] **Step 2: Serve the worktree's build against a scratch database, and look once**

Copy `var/oathdigital` to a scratch directory and never open the live one. Serve this worktree's build on a free port, the way Slice 1's verification did (port 8093, stopped afterwards). The main checkout's server shows only main's code. Open a seat on the six-player game `manual-1790205747051-112090` and, in one batch, capture:
- the Log pane at the table's normal size: a Campaign with dice, a Negotiation settlement, a Setup block;
- the Actions pane after a Campaign: the result panel must be gone, with no empty gap where it was.

- [ ] **Step 3: Fix what the look shows, in one batch**

Keep within DESIGN.md. Check these and fix only what is actually wrong:
- The `log-dice` chips sit on the 11px line: chip height, vertical alignment and gaps. They follow DESIGN.md's Dice rule and change no line height.
- `log-decision`, `log-roll`, `log-delta` and `log-trigger` lines read as lines (Ink Dim) under the action lines. A Trigger line is not styled like a headline.
- A long Campaign losses line wraps under its own indent.
- No `transition`, `animation` or resting shadow, and no new colors outside the tokens.
Add one `GameLogPaneSuite` assertion for any class this step adds.

- [ ] **Step 4: Confirm once, then update DESIGN.md**

Take one confirming capture of the same views. Update DESIGN.md:
- Panes: the Log pane's entries replace the stripe texture and placeholder description. Headlines use Replay green, turn names use seat color, and lines use Ink Dim with the cost span set apart.
- Dice: "a roll in the log draws the same chips inline, without a totals line; the totals follow as their own log line".
Stop the scratch server.

- [ ] **Step 5: Run the frontend tests and commit**

Run: `./sbtw "frontend/test"`
Expected: all pass.

```bash
git add frontend/styles.css frontend/src/main/scala/oathdigital/frontend/GameLogPane.scala frontend/src/test/scala/oathdigital/frontend/GameLogPaneSuite.scala DESIGN.md
git commit -m "style(log): Slice 2 entries and dice sit cleanly in the Log pane"
```

Stage only the files this step actually changed.

---

### Task 12: Final gates, spec and roadmap

**Files:**
- Modify: `docs/superpowers/specs/2026-09-25-game-log-design.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run every gate**

Run: `./sbtw "test" "frontend/test" "frontend/fastLinkJS"`
Expected: all pass. The counts are the baselines, plus this plan's new tests, minus Task 9's deleted ones.
Run: `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`
Expected: both clean. `ActionLines.scala` must still be at or under 800 lines. Negotiation's and Campaign's rules moved out, so it should have shrunk.

- [ ] **Step 2: Check coverage**

Run: `./sbtw clean coverage test coverageReport frontend/test`
Expected: statement coverage at or above the floor of 86.8. If it drops below, add tests for the uncovered log branches rather than lowering the floor. The likely gaps are a Conquest's gains line and a defeated attacker's losses line. A synthetic tail in `GameLogCampaignSuite`, as in its plan test, reaches both.

- [ ] **Step 3: Amend the spec**

Add a dated note, "Amended 2026-09-26 by the Slice 2 plan", to the status block. Then:
- In "Wire contract", add `dice` to the span kinds and describe its `id` and `unit`.
- In "Detail lines", replace the Roll row with the dice span wording.
- In "Campaign", replace "The exact wording of lines 7, 9, and 11 is fixed … by the second slice's plan" with the wording of Decisions items 3 and 4, and note the per-plan activation lines.
- Record Decisions items 5 to 10.
- Record any clarify-driven wording change from Task 10.

- [ ] **Step 4: Update the roadmap and commit**

In `docs/ROADMAP.md`'s "Player-facing action history", mark Slice 2 done with the date, in the style Slice 1 used. Leave Slice 3 open.

```bash
git add docs/superpowers/specs/2026-09-25-game-log-design.md docs/ROADMAP.md
git commit -m "docs: mark the game log's second slice delivered"
```

---

## Self-review notes

- **Spec coverage (Slice 2):** each item and the task that delivers it.
  - Knowledge follows the card, as the first task: Task 1.
  - Detail lines: Decision (Task 2), Roll (Task 3), and the Delta rows for gains, warbands, draws, discards, buries, peeks and reveals (Task 4).
  - Trigger lines: Usurper and bandits (Task 5); `RecordPowerUse` resolved by Decisions item 9.
  - Standalone `SiteRelicsPeeked`, `OwnedRelicRevealed` and `WarbandsMoved`: Task 5.
  - Setup lines: Task 6.
  - Negotiation settlement, with slot wording: Task 7.
  - Campaign lines 2 to 11: Task 8. The panel's removal: Task 9.
  - Golden tests for the acting seat and another seat: Task 10. Prefix stability, viewer agreement and the leak test now run over the new scripts too: Tasks 5, 7, 8 and 10.
  - The Knowledge tests: Task 1.
  - Coverage, including a declined negotiation and a campaign: the existing scripts plus `raid`.
  - The modifier start line that Slice 1 left unpinned: Task 10.
- **Deliberately not here:** the overlay, divider, New chip and sticky headline are Slice 3. A Raid on the bandits posts no gains line, since nothing is taken.
- **Bandit battle plans** are named like every other activation (Decisions item 3, at the user's request). The name comes from the marker each application records, read back by `CampaignPlans.markedRef`, which has its own round-trip test (Task 8).
- **Impeccable:** Task 10's golden review applies `clarify` to the log's copy, and Task 11 applies `polish` to the Log and Actions panes, with DESIGN.md kept current. Both use bounded passes, the worktree's own build, and a scratch database.
- **Type consistency:**
  - `ChoiceWords.option(ref, before, after, viewer)` is used by `DetailLines` (Task 2) and `CampaignLines` (Task 8).
  - `LogWords.subject(player, actor, verb)` is used in Tasks 2, 4, 5 and 8.
  - `LogSpan.Dice(die, faces, names)` has the same fields in Tasks 3 and 10.
  - `ActionLines(words, choices, catalog)` is constructed only by `GameLogFormatter`.
  - `DetailLines.narrated` is tested in Task 2.
- **Risks called out in their steps:**
  - The disclosure script's answer order depends on the Negotiation proposal rule (Task 7).
  - The Raid script depends on Raid being a legal kind at the shared site (Task 8).
  - An existing board test may have pinned the knowledge bug (Task 1).
  Each step says what to read and what to change.
