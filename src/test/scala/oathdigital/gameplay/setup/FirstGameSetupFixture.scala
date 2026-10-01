package oathdigital.gameplay.setup

import oathdigital.catalog._
import oathdigital.catalog.holding.TheGrandScepterCard
import oathdigital.gameplay.cards.NewFoundations
import oathdigital.model._
import oathdigital.model.OathState._
import oathdigital.gameplay.walker.WalkerCompleted
import oathdigital.testkit.Situation

/** Shared fixture for the whole gameplay test suite: a real catalog, three
  * Exile participants, and a Chronicle-shaped input for `GameStartRules`
  * (2026-09-21 Chronicle design, slice 2). `execute()` drives a full game
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
    catalog.sites.find(_.id == siteId).get.handlers.collectFirst:
      case handler if handler.contains(".homeland-") =>
        val suit = Suit.fromKey(
          handler.substring(handler.indexOf(".homeland-") + 10)).get
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

  val orders: SetupOrders = SetupOrders(
    participants, PlayerId("p2"), ChronicleFixtureDealOrder.dealOrder(denizens, participants.size),
    relics)

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

  /** [[initialSituation]]'s state and real event history as the first
    * player's Wake begins, in `Phase.Wake`. Real play ends a Wake with
    * nothing to decide in the command that begins it, so this stops before
    * that End Wake: the rule suites built on it start from the Wake and add
    * whatever option they test. */
  def execute(placementSites: Vector[SiteId] = sites)
      : (OathState, Vector[OathEvent]) =
    val woken = initialSituation(placementSites)
    val setupEnd = woken.events.indexWhere {
      case WalkerCompleted(TriggeredProcedureRef.Setup) => true
      case _ => false
    }
    val events = woken.events.take(setupEnd + 1)
    val rules = new oathdigital.gameplay.OathRules(catalog)
    val state = events.foldLeft[Either[OathViolation, OathState]](
      Right(NoGame))((state, event) => state.flatMap(rules.evolve(_, event)))
    (state.toOption.get, events)

  /** The game `execute()` sets up. Immutable, so suites share one. */
  lazy val initialReady: ReadyGame =
    val Ready(value) = execute()._1: @unchecked
    value

/** Splices the five fixed Vision identities into the fixture's implemented
  * denizens the same way `ChronicleFirstGamePlan.dealOrder` (Task 6) splices
  * them into a generated Chronicle's world deck -- kept local to the test
  * fixture so it has no production dependency of its own.
  */
private object ChronicleFixtureDealOrder:
  def dealOrder(denizens: Vector[DenizenId], participantCount: Int): Vector[WorldCardId] =
    val remaining = denizens.drop(6 + participantCount * 3)
    remaining.take(10) ++ FirstGameRulesData.visions.take(2) ++
      remaining.slice(10, 25) ++ FirstGameRulesData.visions.drop(2) ++
      remaining.drop(25)
