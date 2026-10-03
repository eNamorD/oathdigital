package oathdigital.gameplay

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  Restriction}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerPowers, WalkerStepRecorded}
import oathdigital.model.OathState.Ready
import oathdigital.model._
import oathdigital.testkit.{CatacombsGame, Look, Outcomes, Table}
import oathdigital.testkit.Unwrap.accepted

/** Catacombs as the first real walker contribution, exercised through
  * REAL commands (`OathRules.startWalker` / `rollWalkerPrepared`) rather than
  * by calling the contribution directly -- the point is that a
  * power reaches the walker through the generic seam, so a test that invoked
  * `CatacombsContribution.contributions` by hand would prove nothing about the
  * wiring.
  *
  * Catacombs' legacy behaviour (`powers/RecoverPowers.scala`): it makes a
  * relic-less site eligible for Recover, and on use places the relic deck's
  * top card facedown at the site for a printed 1-secret cost placed on the
  * Catacombs card itself.
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
    accepted(rules.startWalker(Ready(fixture.ready), ActionRef.Recover,
      fixture.actor, modifiers))

  test("Catacombs makes a relic-less site recoverable, placing the relic and " +
      "paying its secret in one recorded step attributed to the power"):
    val fixture = reliclessSite(secrets = 2)
    val transition = started(fixture, Vector(catacombsId))

    // The transform prepended ONE node to the root sequence's children, so
    // the power's whole effect is one step, at child index 0, carrying both
    // operations and naming Catacombs as its contributor.
    val first = steps(transition.events).head
    assertEquals(first.nodeId, "0")
    assertEquals(first.contributions, Vector(catacombsId))
    assertEquals(first.ops, Vector[CoreOperation](
      Move(Piece.Card(fixture.topRelic),
        PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
        PositionedLocation(Location.Site(fixture.site)),
        resultingOrientation = Some(Orientation.FaceDown)),
      PayCost(fixture.actor, Location.OnCard(catacombsCard),
        Cost(secret = 1), matchingBank = catalog.suitOf(catacombsCard))))

    val after = Outcomes.ready(transition.state)
    val site = after.game.current.map.sites(fixture.site)
    assertEquals(site.relics.map(relic => relic.id -> relic.orientation),
      Vector(fixture.topRelic -> Orientation.FaceDown))
    assertEquals(site.denizens.head.tokens.secrets, 1)
    // p1's 2 faceup secrets, less the 1 paid onto Catacombs.
    assertEquals(Look(after).player(fixture.actor).board.faceUpSecrets, 2 - 1)
    assertEquals(after.game.current.commonCards.relicDeck,
      fixture.ready.game.current.commonCards.relicDeck.tail)

    // The inserted node shifts every later index by one: the choice the
    // failed first roll parks on (the Repeat, its first round, the round's
    // choice Branch, its Decide) sits under "2" instead of the bare tree's
    // "1".
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("2", "0", "2", "0")))
    assertEquals(after.game.current.walkerModifiers, Vector(catacombsId))

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
    // The bare tree parks the failed first roll's choice at the Repeat (1),
    // its first round (0), the round's choice Branch (2) and its Decide (0).
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("1", "0", "2", "0")))
    assertEquals(after.game.current.map.sites(fixture.site).relics,
      Vector.empty)

  test("a site that already holds a facedown relic starts with no modifier " +
      "and records no placement operations"):
    val fixture = relicSite()
    val transition = started(fixture, Vector.empty)
    val recorded = steps(transition.events)

    // The dice-pool bonus, then the first round's Supply spend and its roll.
    assertEquals(recorded.map(_.nodeId), Vector("0.0", "1.0.0", "1.0.1"))
    assertEquals(recorded.flatMap(_.contributions), Vector.empty[PowerId])
    // The roll itself records no operations: a Roll leaf writes its outcome
    // as state (`ProcedureWalker.recordRoll`), not as an ops batch.
    assertEquals(recorded.flatMap(_.ops), Vector[CoreOperation](
      ModifyDicePool(RecoverProcedure.recoverPool, 2,
        window = Some(PowerWindow.RecoverBeforeFirstRoll)),
      SpendSupply(fixture.actor, 1)))

    val after = Outcomes.ready(transition.state)
    assertEquals(after.game.current.commonCards.relicDeck,
      fixture.ready.game.current.commonCards.relicDeck)
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("1", "0", "2", "0")))

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
    assertEquals(parkedAt, Some(Vector("2", "0", "2", "0")))

    val resumed = accepted(rules.resolveWalker(transition.state, fixture.actor,
      RecoverProcedure.choiceDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("continue"))))
    // The invariant under test: the resumed fold must address the SAME leaf
    // the walk parked at, not a re-derived tree shifted by a dropped/moved
    // Catacombs node. Pin the resumed answer's step to the exact parked
    // path, and its ops (answering a Decide records none).
    val choiceStep = steps(resumed.events).head
    assertEquals(choiceStep.nodeId, parkedAt.get.mkString("."))
    assertEquals(choiceStep.ops, Vector.empty[CoreOperation])

  test("a Restriction-only power does not make relic-less Recover illegal"):
    val fixture = reliclessSite(secrets = 2)
    val restrictionOnly = new ContributingPower:
      def id: PowerId = PowerId("test.restriction-only")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] =
        Map(PowerWindow.RecoverActionEligibility -> Vector(
          Restriction((_, _) => None)))
    val restrictedRules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers(Vector(restrictionOnly)),
      walkerDice = WalkerDiceFixture.blanks)
    val unrestricted = new OathRules(catalog, walkerPowerCatalog =
      WalkerPowers.empty, walkerDice = WalkerDiceFixture.blanks)
    // A restriction that forbids nothing leaves the start exactly as it was.
    assertEquals(restrictedRules.startWalker(Ready(fixture.ready),
      ActionRef.Recover, fixture.actor).map(_.state),
      unrestricted.startWalker(Ready(fixture.ready), ActionRef.Recover,
        fixture.actor).map(_.state))

  test("Catacombs is rejected when the site's relic slots are full"):
    val fixture = fullRelicSite()
    rules.startWalker(Ready(fixture.ready), ActionRef.Recover, fixture.actor,
        Vector(catacombsId)) match
      case Left(violation: OathViolation.RecoverUnavailable) =>
        assert(violation.detail.contains("no empty relic slot"),
          s"violation detail '${violation.detail}' should mention the " +
            "full relic slot")
      case other =>
        fail(s"expected the capacity-full start to be rejected, got $other")
    // A `Left` carries no transition at all: nothing is appended and the
    // fixture's own state (never mutated -- it is immutable data) is the
    // only state that exists for this command.

  test("Catacombs at a site the actor rules places the relic at its own site"):
    val fixture = ruledElsewhere()
    val transition = started(fixture, Vector(catacombsId))
    val after = Outcomes.ready(transition.state)
    val there = after.game.current.map.sites(fixture.site)
    assertEquals(there.relics.map(_.id), Vector(fixture.topRelic))
    assertEquals(there.denizens.head.tokens.secrets, 1)

  test("Catacombs at a site the actor neither rules nor stands on is not " +
      "applicable"):
    val fixture = ruledElsewhere(ruled = false)
    rules.startWalker(Ready(fixture.ready), ActionRef.Recover, fixture.actor,
      Vector(catacombsId)) match
      case Left(OathViolation.InvalidEventOrder(detail)) =>
        assert(detail.contains("not applicable"), detail)
      case other => fail(s"expected the modifier to be refused, got $other")
