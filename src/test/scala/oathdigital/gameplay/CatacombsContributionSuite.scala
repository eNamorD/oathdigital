package oathdigital.gameplay

import oathdigital.gameplay.actions.RecoverRules
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Restriction}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup._
import oathdigital.gameplay.walker.{WalkerPowers, WalkerStepRecorded}
import oathdigital.model.OathState.Ready
import oathdigital.model._

/** Task 5: Catacombs as the first real walker contribution, exercised through
  * REAL commands (`OathRules.startWalker` / `rollWalkerPrepared`) rather than
  * by calling the contribution directly -- the point of the task is that a
  * power reaches the walker through the generic seam, so a test that invoked
  * `CatacombsContribution.contributions` by hand would prove nothing about the
  * wiring.
  *
  * Catacombs' legacy behaviour (`powers/RecoverPowers.scala`): it makes a
  * relic-less site eligible for Recover, and on use places the relic deck's
  * top card facedown at the site for a printed 1-secret cost placed on the
  * Catacombs card itself.
  */
class CatacombsContributionSuite extends munit.FunSuite {
  import CatacombsContributionSuite._

  private val setup = new FirstGameSetupRules(catalog)

  /** The production rules instance shape: the real contribution catalog. */
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog))

  private def steps(events: Vector[OathEvent]): Vector[WalkerStepRecorded] =
    events.collect { case step: WalkerStepRecorded => step }

  private def started(fixture: CatacombsContributionSuite.Fixture,
      modifiers: Vector[PowerId]): OathTransition =
    rules.startWalker(Ready(fixture.ready), ActionRef.Recover, fixture.actor,
      modifiers) match {
      case Right(transition) => transition
      case other => fail(s"expected the walker start to run, got $other")
    }

  test("Catacombs makes a relic-less site recoverable, placing the relic and " +
      "paying its secret in one recorded step attributed to the power") {
    val fixture = reliclessSite(setup)
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

    val Ready(after) = transition.state: @unchecked
    val site = after.game.current.map.sites(fixture.site)
    assertEquals(site.relics.map(relic => relic.id -> relic.orientation),
      Vector(fixture.topRelic -> Orientation.FaceDown))
    assertEquals(site.denizens.head.tokens.secrets, 1)
    assertEquals(after.game.current.players.find(_.player == fixture.actor)
      .get.board.faceUpSecrets, 1)
    assertEquals(after.game.current.commonCards.relicDeck,
      fixture.ready.game.current.commonCards.relicDeck.tail)

    // The inserted node shifts every later index by one: the first Roll parks
    // at "2.0.0" instead of the bare tree's "1.0.0".
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("2", "0", "1")))
    assertEquals(after.game.current.walkerModifiers, Vector(catacombsId))
  }

  test("without the Catacombs modifier the same relic-less start remains legal") {
    val fixture = reliclessSite(setup)
    val transition = started(fixture, Vector.empty)
    val Ready(after) = transition.state: @unchecked
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("1", "0", "1")))
    assertEquals(after.game.current.map.sites(fixture.site).relics,
      Vector.empty)
  }

  test("a site that already holds a facedown relic starts with no modifier " +
      "and records no placement operations") {
    val fixture = relicSite(setup)
    val transition = started(fixture, Vector.empty)
    val recorded = steps(transition.events)

    assertEquals(recorded.map(_.nodeId), Vector("0.0", "1.0.0"))
    assertEquals(recorded.flatMap(_.contributions), Vector.empty[PowerId])
    assertEquals(recorded.flatMap(_.ops), Vector[CoreOperation](
      ModifyDicePool(RecoverProcedure.recoverPool, 2,
        window = Some(PowerWindow.RecoverBeforeFirstRoll)),
      SpendSupply(fixture.actor, 1)))

    val Ready(after) = transition.state: @unchecked
    assertEquals(after.game.current.commonCards.relicDeck,
      fixture.ready.game.current.commonCards.relicDeck)
    assertEquals(after.game.current.walkerPending.map(_.at),
      Some(Vector("1", "0", "1")))
  }

  test("the Catacombs fold survives its own effect: a resume after the last " +
      "secret is spent still addresses the leaf the walk parked at") {
    // The contribution's applicability must be STABLE across the commands of
    // one action: the walker re-derives and re-folds the tree every command,
    // so an `applicable` that consulted the resources the power itself spends
    // would drop the inserted node on resume and shift every parked index.
    val fixture = reliclessSite(setup, secrets = 1)
    val transition = started(fixture, Vector(catacombsId))
    val Ready(parked) = transition.state: @unchecked
    assertEquals(parked.game.current.players.find(_.player == fixture.actor)
      .get.board.faceUpSecrets, 0)
    val parkedAt = parked.game.current.walkerPending.map(_.at)
    assertEquals(parkedAt, Some(Vector("2", "0", "1")))

    val rolled = rules.rollWalkerPrepared(transition.state, fixture.actor,
      RecoverProcedure.recoverPool)(count => Right(
        Vector.fill(count)(DefenseDieFace.Blank))) match {
      case Right(next) => next
      case other => fail(s"expected the resumed roll to run, got $other")
    }
    // The invariant under test: the resumed fold must address the SAME leaf
    // the walk parked at, not a re-derived tree shifted by a dropped/moved
    // Catacombs node. Pin the resumed Roll step's node id to the exact
    // parked path, and its ops (a Roll leaf records outcome as state, not as
    // an ops batch -- see `ProcedureWalker.recordRoll`).
    val rollStep = steps(rolled.events).head
    assertEquals(rollStep.nodeId, parkedAt.get.mkString("."))
    assertEquals(rollStep.ops, Vector.empty[CoreOperation])
  }

  test("a Restriction-only power does not make relic-less Recover illegal") {
    val fixture = reliclessSite(setup)
    val restrictionOnly = new ContributingPower {
      def id: PowerId = PowerId("test.restriction-only")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] =
        Map(PowerWindow.RecoverActionEligibility -> Vector(
          Restriction((_, _) => None)))
    }
    val restrictedRules = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowers(Vector(restrictionOnly)))
    assert(restrictedRules.startWalker(Ready(fixture.ready), ActionRef.Recover,
      fixture.actor).isRight)
  }

  test("Catacombs is rejected when the site's relic slot is already full " +
      "(finding I1: capacity is enforced, not just the empty-deck case)") {
    val fixture = fullRelicSite(setup)
    rules.startWalker(Ready(fixture.ready), ActionRef.Recover, fixture.actor,
        Vector(catacombsId)) match {
      case Left(violation: OathViolation.RecoverUnavailable) =>
        assert(violation.detail.contains("no empty relic slot"),
          s"violation detail '${violation.detail}' should mention the " +
            "full relic slot")
      case other =>
        fail(s"expected the capacity-full start to be rejected, got $other")
    }
    // A `Left` carries no transition at all: nothing is appended and the
    // fixture's own state (never mutated -- it is immutable data) is the
    // only state that exists for this command.
  }

  test("Catacombs at a site the actor rules places the relic at its own site") {
    val fixture = ruledElsewhere(setup)
    val transition = started(fixture, Vector(catacombsId))
    val Ready(after) = transition.state: @unchecked
    val there = after.game.current.map.sites(fixture.site)
    assertEquals(there.relics.map(_.id), Vector(fixture.topRelic))
    assertEquals(there.denizens.head match {
      case d: DenizenState => d.tokens.secrets
      case _ => -1 }, 1)
  }

  test("Catacombs at a site the actor neither rules nor stands on is not applicable") {
    val fixture = ruledElsewhere(setup, ruled = false)
    rules.startWalker(Ready(fixture.ready), ActionRef.Recover, fixture.actor,
      Vector(catacombsId)) match {
      case Left(OathViolation.InvalidEventOrder(detail)) =>
        assert(detail.contains("not applicable"), detail)
      case other => fail(s"expected the modifier to be refused, got $other")
    }
  }
}

/** Shared fixture: the application-level projection suite drives the same
  * Catacombs game to pin the projector against the walker's own fold.
  */
object CatacombsContributionSuite {
  val catacombsId: PowerId = PowerId("denizen.catacombs")

  /** Read from the catalog so the fixture and the contribution have to agree
    * on the printed card rather than both guessing.
    */
  val catacombsCard: DenizenId = DenizenId(catalog.denizens.find(
    _.powers.exists(_.id.value == catacombsId.value)).get.id.value)

  final case class Fixture(ready: ReadyGame, actor: PlayerId, site: SiteId,
      topRelic: RelicId)

  /** Act phase; the active player's pawn stands on an in-play Recover site
    * holding a faceup Catacombs denizen and NO relic.
    */
  def reliclessSite(setup: FirstGameSetupRules, secrets: Int = 2): Fixture =
    fixture(setup, secrets, placeRelic = false)

  /** The same site with one facedown relic already there. */
  def relicSite(setup: FirstGameSetupRules): Fixture =
    fixture(setup, secrets = 2, placeRelic = true)

  /** Site filled to relic-slot capacity; Catacombs must reject placement. */
  def fullRelicSite(setup: FirstGameSetupRules): Fixture =
    fixture(setup, secrets = 2, placeRelic = false, fillCapacity = true)

  private def fixture(setup: FirstGameSetupRules, secrets: Int,
      placeRelic: Boolean, fillCapacity: Boolean = false): Fixture = {
    val Ready(base) = FirstGameSetupFixture.execute(setup)._1: @unchecked
    val current = base.game.current
    val active = current.players.find(
      _.player == current.turn.activePlayer).get
    val candidates = current.map.inPlay.filter(siteId =>
      RecoverRules.difficulty(catalog, siteId).exists(d => d > 0 && d <= 4) &&
        catalog.sites.find(_.id == siteId).exists(_.relicSlots > 0))
    assert(candidates.nonEmpty,
      "fixture needs an in-play Recover site with a relic slot")
    val siteId = candidates.minBy(siteId =>
      RecoverRules.difficulty(catalog, siteId).get)
    val slots = catalog.sites.find(_.id == siteId).get.relicSlots
    val deck = current.commonCards.relicDeck
    assert(deck.size > slots,
      "fixture needs enough relics to fill the site and still draw one")
    val relics =
      if (placeRelic) Vector(RelicState(deck.head, Orientation.FaceDown,
        Tokens.empty))
      else if (fillCapacity) deck.take(slots).map(id =>
        RelicState(id, Orientation.FaceUp, Tokens.empty))
      else Vector.empty
    val remainingDeck =
      if (placeRelic) deck.tail
      else if (fillCapacity) deck.drop(slots)
      else deck
    val site = current.map.sites(siteId).copy(relics = relics,
      denizens = Vector(DenizenState(catacombsCard, Orientation.FaceUp,
        Tokens.empty)))
    val player = active.copy(pawnSite = Some(siteId),
      board = active.board.copy(faceUpSecrets = secrets))
    val ready = base.updateCurrent(_.copy(
      turn = current.turn.copy(phase = Phase.Act),
      players = current.players.map(other =>
        if (other.player == player.player) player else other),
      commonCards = current.commonCards.copy(
        relicDeck = remainingDeck,
        worldDeck = current.commonCards.worldDeck.filterNot(
          _ == catacombsCard),
        regionalDiscards = current.commonCards.regionalDiscards.map {
          case (region, cards) =>
            region -> cards.filterNot(_ == catacombsCard)
        }),
      map = current.map.copy(sites =
        current.map.sites.updated(siteId, site))))
    Fixture(ready, player.player, siteId, remainingDeck.head)
  }

  /** The pawn stands on a Recover site that already holds a facedown relic.
    * The Catacombs card sits at a different in-play site with a free relic
    * slot, which the actor rules. `site` is the Catacombs site.
    */
  def ruledElsewhere(setup: FirstGameSetupRules,
      ruled: Boolean = true): Fixture = {
    val home = relicSite(setup)
    val current = home.ready.game.current
    val lineage = current.players.find(_.player == home.actor).get.lineage
    // Setup fills every relic slot, so the far site's relics go to the bottom
    // of the relic deck to free a slot. The top of the deck stays the same.
    val far = current.map.inPlay.find(id => id != home.site &&
      catalog.sites.find(_.id == id).exists(_.relicSlots > 0)).get
    val freed = current.map.sites(far).relics.map(_.id)
    val forces =
      if (ruled) SiteForces.Occupied(ForceKind.Exile(lineage), 1)
      else SiteForces.Occupied(ForceKind.Bandit, 1)
    val moved = home.ready.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(relicDeck =
        c.commonCards.relicDeck ++ freed),
      map = c.map.copy(sites = c.map.sites
        .updated(home.site, c.map.sites(home.site).copy(denizens = Vector.empty))
        .updated(far, c.map.sites(far).copy(forces = forces, relics =
          Vector.empty, denizens = Vector(DenizenState(catacombsCard,
            Orientation.FaceUp, Tokens.empty)))))))
    home.copy(ready = moved, site = far)
  }
}
