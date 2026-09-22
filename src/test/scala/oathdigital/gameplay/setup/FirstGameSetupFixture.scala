package oathdigital.gameplay.setup

import java.nio.file.Paths

import oathdigital.catalog._
import oathdigital.gameplay.OathRules
import oathdigital.model._
import oathdigital.model.OathState._

/** Shared fixture for the whole gameplay test suite: a real catalog, three
  * Exile participants, and a Chronicle-shaped input for `GameStartRules`
  * (2026-09-21 Chronicle design, slice 2). `execute()` drives a full game
  * from `OathRules.beginGame` through every player's pawn placement and
  * adviser choice, landing on the same `Phase.Wake` starting position the
  * deleted `FirstGameSetupRules` used to produce -- this is what every
  * unrelated procedure suite (Recover, Travel, Negotiation, Forge, ...)
  * builds its own fixture on top of.
  */
object FirstGameSetupFixture {
  val catalogRef =
    CatalogRef("oath-new-foundations", "2026.08.29-pre5")
  val catalog: ExecutableCatalog =
    CatalogLoader
      .load(
        Paths.get("docs/catalog/new-foundations-component-catalog.json"),
        CatalogLoadRequest(expectedCatalog = Some(catalogRef))
      )
      .toOption
      .get
  val participants = Vector(
    FirstGameParticipant(
      PlayerId("p1"),
      LineageId("l1"),
      PlayerColor("red")
    ),
    FirstGameParticipant(
      PlayerId("p2"),
      LineageId("l2"),
      PlayerColor("blue")
    ),
    FirstGameParticipant(
      PlayerId("p3"),
      LineageId("l3"),
      PlayerColor("yellow")
    )
  )
  val sites: Vector[SiteId] = catalog.sites.take(8).map(_.id)
  /** The ~30 implemented denizens repeated across suits, standing in for
    * the full 60-card world deck this slice's "cheat" policy calls for
    * (2026-09-21 Chronicle design, "Since only ~30 denizens are
    * implemented right now"). */
  val denizens: Vector[DenizenId] = oathdigital.model.Suit.all.sortBy(_.key).flatMap {
    suit =>
      catalog.denizens.filter(_.suit == suit).take(10)
        .map(d => DenizenId(d.id.value))
  }
  val relics: Vector[RelicId] = catalog.relics
    .filter(_.role == RelicRole.Ordinary)
    .map(r => RelicId(r.id.value))
  val homelandEdifices: Vector[(SiteId, EdificeId)] = sites.flatMap { siteId =>
    catalog.sites.find(_.id == siteId).get.handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        val suit = Suit.fromKey(
          handler.substring(handler.indexOf(".homeland-") + 10)).get
        val edifice = catalog.edifices.find(_.suit == suit).get
        siteId -> EdificeId(edifice.id.value)
    }
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

  /** Drives `OathRules.beginGame` through every player's two Setup
    * decisions, in turn order starting at `orders.firstPlayer`, answering
    * each with the same choice the deleted `FirstGameSetupRules`-based
    * fixture made: the next in-play site, then the first card in hand.
    * Lands on `Phase.Wake` with the returned real event history.
    */
  def execute(placementSites: Vector[SiteId] = sites)
      : (OathState, Vector[OathEvent]) = {
    val rules = new OathRules(catalog)
    val order = Vector(PlayerId("p2"), PlayerId("p3"), PlayerId("p1"))
    var transition = rules.beginGame(OathState.NoGame, chronicle, orders).toOption.get
    var state = transition.state
    var events = transition.events
    order.zipWithIndex.foreach { case (playerId, index) =>
      val pawnDecision = SetupProcedure.pawnDecisionId(playerId)
      transition = rules.resolveWalker(state, playerId, pawnDecision,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(placementSites(index))))
        .toOption.get
      state = transition.state
      events = events ++ transition.events
      val hand = state match {
        case Ready(ready) =>
          ready.game.current.temporaryHands.getOrElse(playerId, Vector.empty)
        case _ => Vector.empty
      }
      val adviser = hand.collectFirst { case id: DenizenId => id }.get
      val adviserDecision = SetupProcedure.adviserDecisionId(playerId)
      transition = rules.resolveWalker(state, playerId, adviserDecision,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Denizen(adviser)))
        .toOption.get
      state = transition.state
      events = events ++ transition.events
    }
    (state, events)
  }

  /** The game `execute()` sets up. Immutable, so suites share one. */
  lazy val initialReady: ReadyGame = {
    val Ready(value) = execute()._1: @unchecked
    value
  }
}

/** Splices the five fixed Vision identities into the fixture's implemented
  * denizens the same way `ChronicleFirstGamePlan.dealOrder` (Task 6) splices
  * them into a generated Chronicle's world deck -- kept local to the test
  * fixture so it has no production dependency of its own.
  */
private object ChronicleFixtureDealOrder {
  def dealOrder(denizens: Vector[DenizenId], participantCount: Int): Vector[WorldCardId] = {
    val remaining = denizens.drop(6 + participantCount * 3)
    remaining.take(10) ++ FirstGameRulesData.visions.take(2) ++
      remaining.slice(10, 25) ++ FirstGameRulesData.visions.drop(2) ++
      remaining.drop(25)
  }
}
