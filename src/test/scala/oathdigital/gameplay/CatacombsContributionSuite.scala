package oathdigital.gameplay

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  Restriction}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.recover.CatacombsContribution
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ChoicePayload, RollPayload,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model.OathState.Ready
import oathdigital.model._
import oathdigital.testkit.{CatacombsGame, Look, Outcomes, RecordedOps, Table}
import oathdigital.testkit.Unwrap.accepted

/** Catacombs as the first real walker contribution, exercised through
  * the real rules entry points (`OathRules.startWalker` /
  * `rollWalkerPrepared`) rather than
  * by calling the contribution directly -- the point is that a
  * power reaches the walker through the generic seam, so a test that invoked
  * `CatacombsContribution.contributions` by hand would prove nothing about the
  * wiring.
  *
  * Catacombs' legacy behaviour (`powers/RecoverPowers.scala`): it makes a
  * relic-less site eligible for Recover; when picked, it pays a printed 1-secret
  * cost onto the Catacombs card, and the Recover then places the relic deck's
  * top card facedown at the site.
  */
class CatacombsContributionSuite extends munit.FunSuite:
  import CatacombsGame._

  /** The production rules instance shape: the real contribution catalog,
    * with dice that always fail the roll so every walk here parks on the
    * continue-or-stop choice rather than running on to a relic.
    */
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)

  private def steps(events: Vector[OathEvent]): Vector[WalkerStepRecorded] =
    events.collect { case step: WalkerStepRecorded => step }

  private def started(fixture: Game, modifiers: Vector[PowerId])
      : OathTransition =
    accepted(ModifierPlay.started(rules, Ready(fixture.ready),
      ActionRef.Recover, fixture.actor, PowerWindow.RecoverModifierSelection,
      modifiers))

  test("Catacombs makes a relic-less site recoverable: the pick pays its " +
      "secret, and the Recover places the relic in a step attributed to " +
      "the power"):
    val fixture = reliclessSite(secrets = 2)
    val transition = started(fixture, Vector(catacombsId))
    val acting = steps(transition.events).filter(RecordedOps.of(_).nonEmpty)

    // The pick's payment, onto the card.
    assertEquals(RecordedOps.of(acting.head), Vector[CoreOperation](
      PayCost(fixture.actor, Location.OnCard(catacombsCard),
        Cost(secret = 1), matchingBank = catalog.suitOf(catacombsCard))))
    // The transform prepended ONE node to the Recover's children, so the
    // placement is the Recover's first step, naming Catacombs.
    val placed = acting(1)
    assertEquals(placed.nodeId, "1.0")
    assertEquals(placed.contributions, Vector(catacombsId))
    assertEquals(RecordedOps.of(placed), Vector[CoreOperation](
      Move(Piece.Card(fixture.topRelic),
        PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
        PositionedLocation(Location.Site(fixture.site)),
        resultingOrientation = Some(Orientation.FaceDown))))

    val after = Outcomes.ready(transition.state)
    val site = after.game.current.map.sites(fixture.site)
    assertEquals(site.relics.map(relic => relic.id -> relic.orientation),
      Vector(fixture.topRelic -> Orientation.FaceDown))
    assertEquals(site.denizens.head.tokens.secrets, 1)
    // p1's 2 faceup secrets, less the 1 paid onto Catacombs.
    assertEquals(Look(after).player(fixture.actor).board.faceUpSecrets, 2 - 1)
    assertEquals(after.game.current.commonCards.relicDeck,
      fixture.ready.game.current.commonCards.relicDeck.tail)

    // The Recover is the root's second child, after the modifier decision,
    // and the inserted node shifts each later index by one: the choice the
    // failed first roll parks on (the Repeat, its first round, the round's
    // choice Branch, its Decide) sits under "2" instead of the bare
    // Recover's "1".
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("1", "2", "0", "2", "0")))

  private def offered(ready: ReadyGame, action: ActionRef): Vector[PowerId] =
    accepted(rules.offerableWalkerPowers(ready, Table.p1, action)).map(_.id)

  test("Catacombs is offered for a Recover and for no other action"):
    val ready = reliclessSite(secrets = 2).ready
    assert(offered(ready, ActionRef.Recover).contains(catacombsId))
    Vector(ActionRef.Search, ActionRef.Travel, ActionRef.Muster,
      ActionRef.Trade, ActionRef.Forge, ActionRef.Challenge,
      ActionRef.Campaign).foreach(action =>
        assert(!offered(ready, action).contains(catacombsId), action))

  test("Catacombs is not offered when its secret cannot be paid"):
    assert(!offered(reliclessSite(secrets = 0).ready, ActionRef.Recover)
      .contains(catacombsId))

  test("without the Catacombs modifier the same relic-less start remains " +
      "legal"):
    val fixture = reliclessSite(secrets = 2)
    val transition = started(fixture, Vector.empty)
    val after = Outcomes.ready(transition.state)
    // The Recover, after the modifier decision (1), parks the failed first
    // roll's choice at the Repeat (1), its first round (0), the round's
    // choice Branch (2) and its Decide (0).
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("1", "1", "0", "2", "0")))
    assertEquals(after.game.current.map.sites(fixture.site).relics,
      Vector.empty)

  test("a site that already holds a facedown relic starts with no modifier " +
      "and records no placement operations"):
    val fixture = relicSite()
    val transition = started(fixture, Vector.empty)
    val recorded = steps(transition.events)

    // The dice-pool bonus, then the first round's Supply spend and its roll,
    // which heads its roll step. The roll carries `RecoverRoll`, so it sits
    // one level deeper than its step.
    assertEquals(recorded.map(_.nodeId),
      Vector("1.0.0", "1.1.0.0.0", "1.1.0.1.0.0"))
    assertEquals(recorded.flatMap(_.contributions), Vector.empty[PowerId])
    // The roll itself records no operations: a Roll leaf writes its outcome
    // as state (`ProcedureWalker.recordRoll`), not an operation batch. Read
    // through `RecordedOps` that is true of any roll, so the payload is what
    // carries the fact.
    assert(recorded(2).payload.isInstanceOf[RollPayload], recorded(2).payload)
    assertEquals(recorded.flatMap(RecordedOps.of(_)), Vector[CoreOperation](
      ModifyDicePool(RecoverProcedure.recoverPool, 2,
        window = Some(PowerWindow.RecoverBeforeFirstRoll)),
      SpendSupply(fixture.actor, 1)))

    val after = Outcomes.ready(transition.state)
    assertEquals(after.game.current.commonCards.relicDeck,
      fixture.ready.game.current.commonCards.relicDeck)
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("1", "1", "0", "2", "0")))

  test("the Catacombs fold survives its own effect: a resume after the last " +
      "secret is spent still addresses the leaf the walk parked at"):
    // The contribution's applicability must be STABLE across the commands of
    // one action: the walker re-derives and re-folds the tree every command,
    // so an `applicable` that consulted the resources the power itself spends
    // would drop the inserted node on resume and shift every parked index.
    val fixture = reliclessSite(secrets = 1)
    val transition = started(fixture, Vector(catacombsId))
    val parked = Outcomes.ready(transition.state)
    assertEquals(Look(parked).player(fixture.actor).board.faceUpSecrets, 0)
    val parkedAt = parked.game.current.walkerPending.map(_.at)
    assertEquals(parkedAt, Some(Vector("1", "2", "0", "2", "0")))

    val resumed = accepted(rules.resolveWalker(transition.state, fixture.actor,
      RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("continue"))))
    // The invariant under test: the resumed fold must address the SAME leaf
    // the walk parked at, not a re-derived tree shifted by a dropped/moved
    // Catacombs node. Pin the resumed answer's step to the exact parked
    // path.
    val choiceStep = steps(resumed.events).head
    assert(choiceStep.payload.isInstanceOf[ChoicePayload], choiceStep.payload)
    assertEquals(choiceStep.nodeId, parkedAt.get.mkString("."))

  test("a Restriction-only power does not make relic-less Recover illegal"):
    val fixture = reliclessSite(secrets = 2)
    // Stub of ContributingPower: forbids nothing.
    val stubRestriction = new ContributingPower:
      def id: PowerId = PowerId("test.restriction-only")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] =
        Map(PowerWindow.RecoverActionEligibility -> Vector(
          Restriction((_, _) => None)))
    val restrictedRules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers(Vector(stubRestriction)),
      walkerDice = WalkerDiceFixture.blanks)
    val unrestricted = new OathRules(catalog, walkerPowerCatalog =
      WalkerPowers.empty, walkerDice = WalkerDiceFixture.blanks)
    // A restriction that forbids nothing leaves the start exactly as it was.
    assertEquals(restrictedRules.startWalker(Ready(fixture.ready),
      ActionRef.Recover, fixture.actor).map(_.state),
      unrestricted.startWalker(Ready(fixture.ready), ActionRef.Recover,
        fixture.actor).map(_.state))

  test("Catacombs is listed unavailable at a full relic site and with an " +
      "empty relic deck"):
    // Relic Worship can be picked, so the decision asks and lists Catacombs.
    def withWorship(game: Game): ReadyGame =
      Table.from(game.ready).adviser(Table.p1, "Relic Worship").ready
    def catacombsOption(ready: ReadyGame)(using munit.Location)
        : DecisionOption =
      val begun = accepted(rules.startWalker(Ready(ready), ActionRef.Recover,
        Table.p1))
      modifierOption(begun.state, catacombsCard)
    def unavailable(reason: String): DecisionOption =
      DecisionOption.Unavailable(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(catacombsCard)),
        OptionReason.Written(reason))
    assertEquals(catacombsOption(withWorship(fullRelicSite())),
      unavailable(CatacombsContribution.noEmptySlot))
    val noRelics = withWorship(reliclessSite(secrets = 2)).updateCurrent(
      current => current.copy(commonCards =
        current.commonCards.copy(relicDeck = Vector.empty)))
    assertEquals(catacombsOption(noRelics),
      unavailable(CatacombsContribution.emptyRelicDeck))

  test("Catacombs at a site the actor rules places the relic at its own site"):
    val fixture = ruledElsewhere()
    val transition = started(fixture, Vector(catacombsId))
    val after = Outcomes.ready(transition.state)
    val there = after.game.current.map.sites(fixture.site)
    assertEquals(there.relics.map(_.id), Vector(fixture.topRelic))
    assertEquals(there.denizens.head.tokens.secrets, 1)

  test("Catacombs at a site the actor neither rules nor stands on is not " +
      "listed"):
    val fixture = ruledElsewhere(ruled = false)
    val begun = accepted(rules.startWalker(Ready(fixture.ready),
      ActionRef.Recover, fixture.actor))
    val after = Outcomes.ready(begun.state)
    // Nothing to pick, so nothing is asked: the Recover rolls at once.
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("1", "1", "0", "2", "0")))
    assertEquals(after.game.current.map.sites(fixture.site).relics,
      fixture.ready.game.current.map.sites(fixture.site).relics)

  test("Catacombs pays its secret onto its card, as its selection payment"):
    val fixture = reliclessSite(secrets = 1)
    val power = CatacombsContribution.forCatalog(catalog)
    assertEquals(power.selectionPayments(fixture.ready, fixture.actor),
      Vector(Costs.onCard(fixture.actor, power.cardId, Cost(secret = 1),
        catalog)))
