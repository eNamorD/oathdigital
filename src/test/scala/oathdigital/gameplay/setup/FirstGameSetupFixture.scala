package oathdigital.gameplay.setup

import oathdigital.catalog._
import oathdigital.gameplay.cards.NewFoundations
import oathdigital.gameplay.operations.TheGrandScepterCard
import oathdigital.model._
import oathdigital.model.OathState._
import oathdigital.gameplay.walker.WalkerCompleted
import oathdigital.testkit.{Outcomes, Situation}

/** Shared fixture for the whole gameplay test suite: a real catalog, three
  * Exile participants, and a Chronicle-shaped input for `GameStartRules`
  * (2026-09-21 Chronicle design, slice 2). `setupThroughWake()` drives a game
  * from `OathRules.beginGame` through every player's pawn placement and
  * adviser choice, landing on the same `Phase.Wake` starting position the
  * deleted `FirstGameSetupRules` used to produce -- this is what every
  * unrelated procedure suite (Recover, Travel, Negotiation, Forge, ...)
  * builds its own fixture on top of.
  */
object FirstGameSetupFixture:
  val catalog: ExecutableCatalog = NewFoundations.catalog
  val participants = Vector(
    FirstGameParticipant(
      PlayerId("p1"),
      LineageId("l1"),
      PlayerColor.Red
    ),
    FirstGameParticipant(
      PlayerId("p2"),
      LineageId("l2"),
      PlayerColor.Blue
    ),
    FirstGameParticipant(
      PlayerId("p3"),
      LineageId("l3"),
      PlayerColor.Yellow
    )
  )
  val sites: Vector[SiteId] = catalog.sites.take(8).map(_.id)
  /** The ~30 implemented denizens repeated across suits, standing in for
    * the full 60-card world deck this slice's "cheat" policy calls for
    * (2026-09-21 Chronicle design, "Since only ~30 denizens are
    * implemented right now"). */
  val denizens: Vector[DenizenId] = oathdigital.model.Suit.all.sortBy(_.key).flatMap:
    suit =>
      catalog.denizens.filter(_.suit == suit).take(10)
        .map(d => DenizenId(d.id.value))
  val relics: Vector[RelicId] = catalog.relics
    .filter(_.id != TheGrandScepterCard.id)
    .map(r => RelicId(r.id.value))
  val homelandEdifices: Vector[(SiteId, EdificeId)] = sites.flatMap { siteId =>
    catalog.sites.find(_.id == siteId).get.homeland.map: suit =>
      val edifice = catalog.edifices.find(_.suit == suit).get
      siteId -> EdificeId(edifice.id.value)
  }
  private val edificesBySite = homelandEdifices.toMap

  /** The between-game record `GameStartRules` reads: exactly the 8 sites in
    * play (no long-term storage beyond them, in this fixture), each with its
    * Homeland's edifice if it has one; the implemented denizens as the
    * world deck; the ordinary relics as the relic deck.
    */
  val chronicle: Chronicle = Chronicle(
    atlasBox = sites.map(site => StoredSite(site,
      edificesBySite.get(site).toVector)),
    worldDeck = denizens,
    relicDeck = relics
  )

  /** The first game's first player. `Table.start` overrides it to p1; a
    * test that runs from `orders` itself starts with p2. */
  val firstPlayer: PlayerId = PlayerId("p2")

  val orders: SetupOrders = SetupOrders(participants, firstPlayer,
    ChronicleFixtureDealOrder.dealOrder(denizens, participants.size), relics)

  /** A fresh `Ready` game in `Phase.Setup`: nothing placed, nothing chosen.
    * What `GameStartRulesSuite` and `SetupProcedureSuite` exercise directly.
    */
  def freshReady: ReadyGame =
    GameStartRules.evolve(catalog, chronicle, orders).toOption.get

  /** Setup driven by `OathRules` from this first-game input, the n-th pawn
    * placed at `placementSites(n)` and every adviser choice keeping the
    * first card in hand: [[oathdigital.testkit.Situation.wake]] under the
    * fixture's rules, ready to be driven further.
    */
  def initialSituation(placementSites: Vector[SiteId] = sites): Situation =
    Situation.wake(Situation.rules(catalog)
      .withAnswers(Situation.pawnsAt(placementSites)), chronicle, orders)

  /** The state Setup reached and the events that reached it. */
  final case class SetupResult(state: OathState, events: Vector[OathEvent])

  /** [[initialSituation]]'s state and real event history as the first
    * player's Wake begins, in `Phase.Wake`. Real play ends a Wake with
    * nothing to decide in the command that begins it, so this stops before
    * that End Wake: the rule suites built on it start from the Wake and add
    * whatever option they test. */
  def setupThroughWake(placementSites: Vector[SiteId] = sites)
      : SetupResult =
    val woken = initialSituation(placementSites)
    val setupEnd = woken.events.indexWhere {
      case WalkerCompleted(TriggeredProcedureRef.Setup) => true
      case _ => false
    }
    val events = woken.events.take(setupEnd + 1)
    val rules = new oathdigital.gameplay.OathRules(catalog)
    val state = events.foldLeft[Either[OathViolation, OathState]](
      Right(NoGame))((state, event) => state.flatMap(rules.evolve(_, event)))
    SetupResult(state.fold(violation =>
      throw AssertionError(s"the setup events do not replay: $violation"),
      identity), events)

  /** The game `setupThroughWake()` sets up. Immutable, so suites share one. */
  lazy val initialReady: ReadyGame =
    Outcomes.ready(setupThroughWake().state)

/** Splices the five fixed Vision identities into the fixture's implemented
  * denizens the same way `ChronicleFirstGamePlan.dealOrder` (Task 6) splices
  * them into a generated Chronicle's world deck -- kept local to the test
  * fixture so it has no production dependency of its own.
  */
private object ChronicleFixtureDealOrder:
  def dealOrder(denizens: Vector[DenizenId], participantCount: Int)
      : Vector[WorldCardId] =
    // Set aside first: 3 cards for each participant's hand and 6 for the
    // seeded regional discards.
    val remaining = denizens.drop(6 + participantCount * 3)
    // Then the 10+2 and 15+3 packets: 10 cards and 2 Visions, 15 cards and the
    // other 3 Visions, and the rest of the deck below them.
    remaining.take(10) ++ FirstGameRulesData.visions.take(2) ++
      remaining.slice(10, 25) ++ FirstGameRulesData.visions.drop(2) ++
      remaining.drop(25)
