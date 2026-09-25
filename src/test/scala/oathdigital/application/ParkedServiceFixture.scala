package oathdigital.application

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.rest.{LeagueTreatyContribution, SilverTongue}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerParked}
import oathdigital.model._
import oathdigital.testkit.{Situation, SituationDriver, Step}

/** Real games parked on a walker through the application service.
  *
  * A park that needs a specific card in play puts that card on top of the
  * world deck through the Chronicle's own world deck, then journals one
  * arranging `WalkerStepRecorded` delta that moves it into place: the same
  * replay path production uses, as the off-turn Oathkeeper reload test does.
  */
object ParkedServiceFixture:
  val treatyCard: DenizenId = DenizenId("237")
  val silverTongueCard: DenizenId = DenizenId("92")

  /** The parked decision, as these fixtures rebuild it: the same catalog and
    * power catalogs `GameApplicationService` builds its rules with
    * (`GameApplicationService.scala:87-90`).
    */
  private val parkedAssertions = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))

  /** Setup driven through `service` as `gameId`, the n-th pawn placed at
    * `placementSites(n)`: the accepted position the first player's Wake
    * starts from, holding every event Setup journaled. */
  def setUp(service: GameApplicationService, gameId: String,
      placementSites: Vector[SiteId] = sites,
      setupChronicle: Chronicle = chronicle,
      setupOrders: SetupOrders = orders): GameAccepted =
    accepted(woken(Situation.journaled(service, catalog, gameId),
      placementSites, setupChronicle, setupOrders))

  private def woken(driver: SituationDriver,
      placementSites: Vector[SiteId] = sites,
      setupChronicle: Chronicle = chronicle,
      setupOrders: SetupOrders = orders): Situation =
    Situation.wake(driver.withAnswers(Situation.pawnsAt(placementSites)),
      setupChronicle, setupOrders)

  /** The situation as the service accepted its last command: `events` are
    * that command's alone, as `GameApplicationService.handle` reports them. */
  private def accepted(situation: Situation, before: Situation): GameAccepted =
    GameAccepted(situation.state, situation.events.drop(before.events.size),
      situation.nextSequence)

  private def accepted(situation: Situation): GameAccepted =
    GameAccepted(situation.state, situation.events, situation.nextSequence)

  /** The step that arranges `gameId`'s fixture board. */
  private def arrange(gameId: String, ops: Vector[CoreOperation]): Step =
    Step.Arrange(ops, s"arrange the $gameId fixture")

  /** `cards`, in order, become the top of the world deck. A card already
    * dealt by setup swaps places with the card it displaces, and the world
    * deck order is rebuilt with `ChronicleFirstGamePlan`'s own interleaving
    * of visions -- the same production splice, not a duplicate of it.
    */
  def withWorldDeckTop(baseChronicle: Chronicle, baseOrders: SetupOrders,
      cards: Vector[DenizenId]): (Chronicle, SetupOrders) =
    val dealt = 6 + baseOrders.participants.size * 3
    val targets = (dealt until dealt + cards.size).toSet
    val worldDeck = cards.zipWithIndex.foldLeft(baseChronicle.worldDeck):
      case (current, (card, offset)) =>
        val target = dealt + offset
        val existing = current.indexOf(card)
        val at = if existing >= 0 then existing else
          val suit = catalog.denizens.find(_.id.value == card.value).get.suit
          current.indices.find(index => !targets(index) &&
            !cards.contains(current(index)) && catalog.denizens.exists(denizen =>
              denizen.id.value == current(index).value &&
                denizen.suit == suit)).get
        current.updated(target, card).updated(at, current(target))
    val newChronicle = baseChronicle.copy(worldDeck = worldDeck)
    val config = FirstGameBootstrapConfig(baseOrders.participants,
      baseOrders.firstPlayer)
    (newChronicle, ChronicleFirstGamePlan.dealOrder(newChronicle, config))

  def topOfWorldDeck(card: DenizenId, to: Location,
      orientation: Orientation = Orientation.FaceUp): Move =
    Move(Piece.Card(card), PositionedLocation(Location.Deck(CardDeck.World),
      StackPosition.Top), PositionedLocation(to), Some(orientation))

  def cleared(ready: oathdigital.model.ReadyGame, site: SiteId)
      : Vector[CoreOperation] = ready.game.current.map.sites(site).forces match
    case SiteForces.Occupied(kind, count) => Vector(Kill(Piece.Warbands(kind,
      count), PositionedLocation(Location.Site(site))))
    case SiteForces.Empty => Vector.empty

  /** League Treaty on the first cradle site, ruled by an off-turn player's
    * warband and holding 2 favor of its own suit. The active player ends
    * Wake and begins Rest, which finishes Rest and parks on the ruler's
    * destination decision.
    */
  def leagueTreatyPark(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, gameId: String)
      : (GameAccepted, PlayerId, PlayerId) =
    val (seededChronicle, seededOrders) = withWorldDeckTop(chronicle, orders,
      Vector(treatyCard))
    val setup = woken(Situation.journaled(service, catalog, repository,
      gameId), setupChronicle = seededChronicle, setupOrders = seededOrders)
    val base = setup.ready
    val current = base.game.current
    assert(current.commonCards.worldDeck.headOption.contains(treatyCard),
      "League Treaty must top the world deck")
    val active = current.turn.activePlayer
    val ruler = current.players.map(_.player).find(_ != active).get
    val lineage = current.players.find(_.player == ruler).get.lineage
    val site = current.map.cradle.head
    val suit = catalog.suitOf(treatyCard).get
    val act = setup.after(arrange(gameId, cleared(base, site) ++ Vector(
      topOfWorldDeck(treatyCard, Location.Site(site)),
      Move(Piece.Warbands(ForceKind.Exile(lineage), 1),
        PositionedLocation(Location.PlayArea(ruler)),
        PositionedLocation(Location.Site(site))),
      Move(Piece.Favor(2), PositionedLocation(Location.FavorBank(suit)),
        PositionedLocation(Location.OnCard(treatyCard))))),
      GameCommand.EndWake(active))
    val parked = act.parkedAfter(GameCommand.BeginRest(active))
    parkedAssertions.assertParked(parked.state, PhaseTransitionRef.FinishRest,
      LeagueTreatyContribution.destinationDecisionId(base, active, site,
        treatyCard), ruler)
    (accepted(parked, act), active, ruler)

  /** Defense dice that always come up blank. A Recover started with these
    * fails its roll, so it parks on the continue-or-stop choice instead of
    * landing wherever a random port would send it.
    */
  val failingDice: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.HollowSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.Blank)

  /** A Recover parked in Act on its continue-or-stop choice, at the first
    * catalog site Recover can target. `service` must roll `failingDice`, or
    * the walk lands somewhere else.
    */
  def recoverChoicePark(service: GameApplicationService, gameId: String)
      : (GameAccepted, PlayerId, Vector[PlayerId]) =
    val recoverSite = catalog.sites.find(site =>
      site.recoverDifficulty.exists(d => d > 0 && d <= 4) &&
        site.relicSlots > 0 &&
        !site.handlers.exists(_.contains(".homeland-"))).get.id
    val recoverChronicle = chronicle.copy(atlasBox =
      chronicle.atlasBox.find(_.site == recoverSite).get +:
        chronicle.atlasBox.filterNot(_.site == recoverSite))
    val recoverSites = recoverChronicle.atlasBox.take(8).map(_.site)
    val actor = orders.firstPlayer
    val act = woken(Situation.journaled(service, catalog, gameId),
      recoverSites, recoverChronicle, orders).after(GameCommand.EndWake(actor))
    val parked = act.parkedAfter(
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor)))
    parkedAssertions.assertParked(parked.state, ActionRef.Recover,
      RecoverProcedure.choiceDecisionId, actor)
    (accepted(parked, act), actor, orders.participants.map(_.playerId))

  /** The off-turn Oathkeeper tie from the service suite: the holder must
    * pick between two tied leaders after the active player's Travel.
    */
  def oathkeeperTiePark(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, gameId: String)
      : (GameAccepted, PlayerId, PlayerId, PlayerId) =
    val setup = woken(Situation.journaled(service, catalog, repository,
      gameId))
    val base = setup.ready
    val active = base.game.current.turn.activePlayer
    val players = base.game.current.players.map(_.player)
    val holder = players.find(_ != active).get
    val leaders = players.filterNot(_ == holder)
    val lineageOf = base.game.current.players.map(p => p.player -> p.lineage).toMap
    val siteA = base.game.current.map.inPlay(0)
    val siteB = base.game.current.map.inPlay(1)
    val act = setup.after(arrange(gameId,
      cleared(base, siteA) ++ cleared(base, siteB) ++ Vector(
        SetOathkeeper(Some(holder)),
        Move(Piece.Warbands(ForceKind.Exile(lineageOf(leaders(0))), 1),
          PositionedLocation(Location.PlayArea(leaders(0))),
          PositionedLocation(Location.Site(siteA))),
        Move(Piece.Warbands(ForceKind.Exile(lineageOf(leaders(1))), 1),
          PositionedLocation(Location.PlayArea(leaders(1))),
          PositionedLocation(Location.Site(siteB))))),
      GameCommand.EndWake(active))
    val inAct = act.ready
    val activePlayer = inAct.game.current.players.find(_.player == active).get
    val destination = inAct.game.current.map.inPlay.find(id =>
      !activePlayer.pawnSite.contains(id) && id != siteA && id != siteB).get
    val parked = act.parkedAfter(
      GameCommand.StartWalker(ActionRef.Travel, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Site(destination)))))
    assert(parked.events.last match {
      case WalkerParked(TriggeredProcedureRef.Oathkeeper, _, _, _, _) => true
      case _ => false
    }, s"expected a triggered Oathkeeper park, got ${parked.events.last}")
    parkedAssertions.assertParked(parked.state, TriggeredProcedureRef.Oathkeeper,
      OathkeeperProcedure.recipientDecisionId, holder)
    (accepted(parked, act), active, holder, leaders(1))

  /** Silver Tongue as the active player's faceup adviser, with two faceup
    * denizens of different suits at their pawn site. Every favor bank starts
    * with at least 3 favor, so Begin Rest stops at the Rest action, and
    * using Silver Tongue parks on its bank choice. The returned suit is
    * the first site card's, which that choice offers.
    */
  def silverTonguePark(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, gameId: String)
      : (GameAccepted, PlayerId, Suit) =
    val bySuit = catalog.denizens.map(d => DenizenId(d.id.value) -> d.suit)
      .filterNot { case (id, _) => id == silverTongueCard || id == treatyCard }
    val first = bySuit.head
    val second = bySuit.find(_._2 != first._2).get
    val cards = Vector(silverTongueCard, first._1, second._1)
    val (seededChronicle, seededOrders) = withWorldDeckTop(chronicle, orders, cards)
    val setup = woken(Situation.journaled(service, catalog, repository,
      gameId), setupChronicle = seededChronicle, setupOrders = seededOrders)
    val current = setup.ready.game.current
    assert(current.commonCards.worldDeck.take(3) == cards,
      "Silver Tongue and the two site cards must top the world deck")
    val active = current.turn.activePlayer
    val pawn = current.players.find(_.player == active).get.pawnSite.get
    val resting = setup.after(arrange(gameId, Vector(
      topOfWorldDeck(silverTongueCard, Location.PlayArea(active)),
      topOfWorldDeck(first._1, Location.Site(pawn)),
      topOfWorldDeck(second._1, Location.Site(pawn)))),
      GameCommand.EndWake(active), GameCommand.BeginRest(active))
    parkedAssertions.assertResumed(resting.state, Phase.Rest, active)
    val parked = resting.parkedAfter(GameCommand.UsePower(active,
      SilverTongue.id, DecisionOptionRef.Denizen(silverTongueCard)))
    val restingReady = resting.ready
    parkedAssertions.assertParked(parked.state, ActionRef.UsePower(SilverTongue.id),
      SilverTongue.choiceDecisionId(restingReady, active), active)
    (accepted(parked, resting), active, first._2)
