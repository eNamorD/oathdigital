package oathdigital.application

import oathdigital.model._
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.OathState.Ready

/** The one real board a walker Forge can be driven to, shared by every
  * suite that needs one.
  *
  * It was `GameApplicationServiceSuite`'s private fixture until Task 5b
  * needed a parked Forge in [[WalkerDecisionProjectionSuite]] too. Extracted
  * rather than rebuilt, because a second hand-built board would only
  * approximate this one: the position below is reached by real commands
  * (a conquest, two Searches, a Rest round), and it is that provenance --
  * not the shape of the state -- that makes an assertion about a parked
  * Forge worth anything.
  */
object ForgeWalkerFixture extends munit.Assertions {

  /** Every Forge in this fixture is preceded by a conquest, and a conquest
    * needs dice. These always come up the same way so the board the Forge
    * starts from is the same board every run.
    */
  val blankCampaignDice: CampaignDicePort = new CampaignDicePort {
    def rollAttack(count: Int) = Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int) = Vector.fill(count)(DefenseDieFace.Blank)
  }

  /** The shipped catalog with the one non-homeland forgeable site's printed
    * cost rewritten to name both resources, so a Forge there parks.
    *
    * The shipped catalog prints three favor at that site. Under the
    * forced-decision rule a query whose section demands every option has
    * exactly one legal answer and must not be asked, so `ForgeProcedure`
    * declares no decision node there at all. This override is what gives
    * the PARKED path a real board to be tested against.
    */
  lazy val mixedForgeCostCatalog: oathdigital.catalog.ExecutableCatalog = {
    val siteId = catalog.sites.find(site => site.forgeRequirements.nonEmpty &&
      !site.handlers.exists(_.contains(".homeland-"))).get.id
    catalog.copy(sites = catalog.sites.map(site =>
      if (site.id != siteId) site
      else site.copy(forgeRequirements = Some(Tokens(2, 1)))))
  }

  /** Drives a real, journalled game to the point where `p2` can start a
    * Forge: a ruled site with a printed Forge cost, exactly three empty
    * faceup denizens on it, supply in hand and a non-empty relic deck.
    *
    * Returns the accepted position to start from, the actor, and the site.
    */
  def forgeReadyGame(service: GameApplicationService, gameId: String,
      cat: oathdigital.catalog.ExecutableCatalog = catalog)
      : (GameAccepted, PlayerId, SiteId) = {
    // Every homeland site restricts which denizens may be played there, and
    // this fixture plays three in, so the site has to be a non-homeland one.
    val forgeSite = cat.sites.find(site => site.forgeRequirements.nonEmpty &&
      !site.handlers.exists(_.contains(".homeland-"))).get.id
    val sitePlayable = plan.worldDeckOrder.collect { case id: DenizenId
        if cat.denizens.find(_.id.value == id.value).exists(definition =>
          definition.restrictions == oathdigital.catalog.CardRestrictions.Unrestricted ||
          definition.restrictions == oathdigital.catalog.CardRestrictions.SiteOnly) => id
    }.take(6)
    val forgePlan = plan.copy(orderedSites = forgeSite +:
      plan.orderedSites.filterNot(_ == forgeSite),
      worldDeckOrder = sitePlayable ++ plan.worldDeckOrder.filterNot(sitePlayable.contains))
    var accepted = service.handle(gameId, 0L, GameCommand.Begin(forgePlan)).toOption.get
    val order = Vector(PlayerId("p2"), PlayerId("p3"), PlayerId("p1"))
    order.zipWithIndex.foreach { case (playerId, index) =>
      val destination = if (index == 0) forgeSite else forgePlan.orderedSites(index)
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.PlacePawn(playerId, destination)).toOption.get
      val participantIndex = forgePlan.participants.indexWhere(_.playerId == playerId)
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.ChooseAdviser(playerId,
          forgePlan.denizenOrder(6 + participantIndex * 3))).toOption.get
    }
    val actor = PlayerId("p2")
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val campaignDecision = DecisionId(s"campaign-${accepted.nextSequence}")
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.BeginCampaignConquest(actor, forgeSite, 3)).toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.FinishCampaignPlans(actor, campaignDecision)).toOption.get
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.ChooseCampaignSacrifice(actor, campaignDecision, 2)).toOption.get
    val Ready(won) = accepted.state: @unchecked
    won.game.current.pending.collect { case c: PendingProcedure.Campaign => c }
      .foreach { campaign =>
        accepted = service.handle(gameId, accepted.nextSequence,
          GameCommand.PlaceCampaignForce(actor, campaignDecision,
            Vector(CampaignForceAllocation(forgeSite,
              campaign.force - campaign.skullLosses -
                campaign.sacrificed.getOrElse(0))))).toOption.get
      }

    def searchOne(): Unit = {
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.StartWalker(ActionRef.Search, StartPayload(actor,
          Vector.empty, Vector(DecisionOptionRef.Button("search:world")))))
        .fold(error => fail(s"Search fixture rejected: $error"), identity)
      val Ready(pendingReady) = accepted.state: @unchecked
      val drawn = pendingReady.game.current.temporaryHands(actor)
      val kept = drawn.find(card => CardPlay.plannedOperations(cat,
        pendingReady, actor, card, SearchPlacement.Site(None),
        CardPlay.Origin.TemporaryHand).isRight)
        .getOrElse(fail(s"no site-playable card in prepared draw $drawn"))
      if (drawn.size > 1) {
        val choices = drawn.map(card => DecisionPlacement(card match {
          case id: DenizenId => DecisionOptionRef.Denizen(id)
          case id: VisionId => DecisionOptionRef.Vision(id)
        }, if (card == kept) "keep" else "discard"))
        accepted = service.handle(gameId, accepted.nextSequence,
          GameCommand.ResolveWalker(actor, TreeDecision("search.cards",
            DecisionAnswer.PartitionAnswer(choices)))).toOption.get
      }
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision(
          s"cardplay.place.${kept.kind}.${kept.value}",
          DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("site")))))
        .toOption.get
    }
    searchOne(); searchOne()
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.BeginRest(actor)).toOption.get
    Vector(PlayerId("p3"), PlayerId("p1")).foreach { player =>
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.EndWake(player)).toOption.get
      accepted = service.handle(gameId, accepted.nextSequence,
        GameCommand.BeginRest(player)).toOption.get
    }
    accepted = service.handle(gameId, accepted.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    searchOne()
    (accepted, actor, forgeSite)
  }

  /** The position a mixed-cost Forge PARKS from: the fixture board above,
    * reached under [[mixedForgeCostCatalog]], plus the `StartWalker` that
    * spends Supply and stops at the assignment decision.
    *
    * Returns the catalog it ran under (the assertions need its printed
    * cost), the parked position, and the actor.
    */
  def parkedForge(gameId: String)
      : (oathdigital.catalog.ExecutableCatalog, GameAccepted, PlayerId,
        SiteId) = {
    val forgeCatalog = mixedForgeCostCatalog
    val service = new GameApplicationService(forgeCatalog,
      new InMemoryEventStreamRepository, campaignDicePort = blankCampaignDice)
    val (ready, actor, forgeSite) = forgeReadyGame(service, gameId,
      forgeCatalog)
    val started = service.handle(gameId, ready.nextSequence,
      GameCommand.StartWalker(ActionRef.Forge, StartPayload(actor)))
      .fold(error => fail(s"walker Forge start rejected: $error"), identity)
    (forgeCatalog, started, actor, forgeSite)
  }
}
