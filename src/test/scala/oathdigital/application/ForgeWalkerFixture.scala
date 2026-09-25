package oathdigital.application

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.testkit.Situation

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
object ForgeWalkerFixture extends munit.Assertions:

  /** Every Forge in this fixture is preceded by a conquest, and a conquest
    * needs dice. These always come up the same way so the board the Forge
    * starts from is the same board every run.
    */
  val blankCampaignDice: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] = Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] = Vector.fill(count)(DefenseDieFace.Blank)

  /** The shipped catalog with the one non-homeland forgeable site's printed
    * cost rewritten to name both resources, so a Forge there parks.
    *
    * The shipped catalog prints three favor at that site. Under the
    * forced-decision rule a query whose section demands every option has
    * exactly one legal answer and must not be asked, so `ForgeProcedure`
    * declares no decision node there at all. This override is what gives
    * the PARKED path a real board to be tested against.
    */
  lazy val mixedForgeCostCatalog: oathdigital.catalog.ExecutableCatalog =
    val siteId = catalog.sites.find(site => site.forgeRequirements.nonEmpty &&
      !site.handlers.exists(_.contains(".homeland-"))).get.id
    catalog.copy(sites = catalog.sites.map(site =>
      if site.id != siteId then site
      else site.copy(forgeRequirements = Some(Tokens(2, 1)))))

  /** Drives a real, journalled game to the point where the first player can
    * start a Forge: a ruled site with a printed Forge cost, exactly three
    * empty faceup denizens on it, supply in hand and a non-empty relic deck.
    *
    * Returns the accepted position to start from, the actor, and the site.
    */
  def forgeReadyGame(service: GameApplicationService, gameId: String,
      cat: oathdigital.catalog.ExecutableCatalog = catalog)
      : (GameAccepted, PlayerId, SiteId) =
    val (ready, actor, forgeSite) = forgeReady(service, gameId, cat)
    (GameAccepted(ready.state, ready.events, ready.nextSequence), actor,
      forgeSite)

  private def forgeReady(service: GameApplicationService, gameId: String,
      cat: oathdigital.catalog.ExecutableCatalog)
      : (Situation, PlayerId, SiteId) =
    // Every homeland site restricts which denizens may be played there, and
    // this fixture plays three in, so the site has to be a non-homeland one.
    val forgeSite = cat.sites.find(site => site.forgeRequirements.nonEmpty &&
      !site.handlers.exists(_.contains(".homeland-"))).get.id
    val sitePlayable = orders.worldDeckOrder.collect { case id: DenizenId
        if cat.denizens.find(_.id.value == id.value).exists(definition =>
          definition.restrictions == oathdigital.catalog.CardRestrictions.Unrestricted ||
          definition.restrictions == oathdigital.catalog.CardRestrictions.SiteOnly) => id
    }.take(6)
    val forgeChronicle = chronicle.copy(atlasBox =
      chronicle.atlasBox.find(_.site == forgeSite).get +:
        chronicle.atlasBox.filterNot(_.site == forgeSite))
    val forgeOrders = orders.copy(
      worldDeckOrder = sitePlayable ++ orders.worldDeckOrder.filterNot(sitePlayable.contains))
    // The first player's pawn goes to the Forge site.
    val orderedSites = forgeSite +: forgeChronicle.atlasBox.take(8)
      .map(_.site).filterNot(_ == forgeSite)
    val actor = forgeOrders.firstPlayer
    val woken = Situation.wake(Situation.journaled(service, cat, gameId)
      .withAnswers(Situation.pawnsAt(orderedSites)), forgeChronicle,
      forgeOrders)
    // A conquest of the Forge site: the other bandit-ruled sites are
    // optional targets, and none is taken.
    val campaigned = woken.withAnswers {
      case park if park.decisionId == CampaignIds.targets =>
        ChooseManyAnswer(Vector.empty)
      case park if park.decisionId == CampaignIds.force => ChooseAmountAnswer(3)
      case park if park.decisionId == CampaignIds.sacrifice =>
        ChooseAmountAnswer(2)
      case park if park.decisionId == CampaignIds.placement =>
        ChooseAmountAnswer(1)
    }.after(GameCommand.EndWake(actor),
      GameCommand.StartWalker(ActionRef.Campaign, StartPayload(actor)))

    // Each Search keeps the first drawn card playable at the site and plays
    // it there.
    val searching = campaigned.withAnswers {
      case park if park.decisionId == SearchProcedure.cardDecisionId =>
        val drawn = park.ready.game.current.temporaryHands(actor)
        val kept = drawn.find(card => CardPlay.plannedOperations(cat,
          park.ready, actor, card, SearchPlacement.Site(None),
          CardPlay.Origin.TemporaryHand).isRight)
          .getOrElse(fail(s"no site-playable card in prepared draw $drawn"))
        PartitionAnswer(drawn.map(card => DecisionPlacement(card match {
          case id: DenizenId => DecisionOptionRef.Denizen(id)
          case id: VisionId => DecisionOptionRef.Vision(id)
        }, if card == kept then "keep" else "discard")))
      case park if park.decisionId.startsWith("cardplay.place.") =>
        ChooseOneAnswer(DecisionOptionRef.Button("site"))
    }
    val search = GameCommand.StartWalker(ActionRef.Search, StartPayload(actor,
      Vector.empty, Vector(DecisionOptionRef.Button("search:world"))))
    // Two Searches, then a round of the others' turns back to the actor, and
    // a third Search.
    @annotation.tailrec
    def roundTo(situation: Situation): Situation =
      val active = situation.ready.game.current.turn.activePlayer
      if active == actor then situation
      else roundTo(situation.after(GameCommand.EndWake(active),
        GameCommand.BeginRest(active)))
    val ready = roundTo(searching.after(search, search,
      GameCommand.BeginRest(actor))).after(GameCommand.EndWake(actor), search)
    (ready, actor, forgeSite)

  /** The position a mixed-cost Forge PARKS from: the fixture board above,
    * reached under [[mixedForgeCostCatalog]], plus the `StartWalker` that
    * spends Supply and stops at the assignment decision.
    *
    * Returns the catalog it ran under (the assertions need its printed
    * cost), the parked position, and the actor.
    */
  def parkedForge(gameId: String)
      : (oathdigital.catalog.ExecutableCatalog, GameAccepted, PlayerId,
        SiteId) =
    val forgeCatalog = mixedForgeCostCatalog
    val service = new GameApplicationService(forgeCatalog,
      new InMemoryEventStreamRepository, campaignDicePort = blankCampaignDice)
    val (ready, actor, forgeSite) = forgeReady(service, gameId, forgeCatalog)
    val started = ready.parkedAfter(
      GameCommand.StartWalker(ActionRef.Forge, StartPayload(actor)))
    (forgeCatalog, GameAccepted(started.state,
      started.events.drop(ready.events.size), started.nextSequence), actor,
      forgeSite)
