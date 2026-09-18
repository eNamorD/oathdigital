package oathdigital.application

import oathdigital.model.OathState.Ready
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.DeltaMeaning.OperationApplied
import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.gameplay.walker.{WalkerParked, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.serialization.GameEventWire

/** Real games parked on a walker through the application service.
  *
  * A park that needs a specific card in play puts that card on top of the
  * world deck through the setup plan, then journals one arranging
  * `WalkerStepRecorded` delta that moves it into place: the same replay path
  * production uses, as the off-turn Oathkeeper reload test does.
  */
object ParkedServiceFixture {
  val treatyCard: DenizenId = DenizenId("237")
  val silverTongueCard: DenizenId = DenizenId("92")

  def setUp(service: GameApplicationService, gameId: String,
      placementSites: Vector[SiteId] = sites,
      setupPlan: FirstGameSetupPlan = plan): GameAccepted = {
    var accepted = service.handle(gameId, 0L, GameCommand.Begin(setupPlan))
      .fold(error => throw new AssertionError(error.toString), identity)
    Vector(PlayerId("p2"), PlayerId("p3"), PlayerId("p1")).zipWithIndex
      .foreach { case (playerId, index) =>
        accepted = service.handle(gameId, accepted.nextSequence,
          GameCommand.PlacePawn(playerId, placementSites(index))).toOption.get
        val participantIndex =
          setupPlan.participants.indexWhere(_.playerId == playerId)
        accepted = service.handle(gameId, accepted.nextSequence,
          GameCommand.ChooseAdviser(playerId,
            setupPlan.denizenOrder(6 + participantIndex * 3))).toOption.get
      }
    accepted
  }

  /** `cards`, in order, become the top of the world deck. A card already
    * dealt by setup swaps places with the card it displaces, and the world
    * deck is rebuilt with the fixture's own interleaving of visions.
    */
  def withWorldDeckTop(base: FirstGameSetupPlan,
      cards: Vector[DenizenId]): FirstGameSetupPlan = {
    val dealt = 6 + base.participants.size * 3
    val targets = (dealt until dealt + cards.size).toSet
    val order = cards.zipWithIndex.foldLeft(base.denizenOrder) {
      case (current, (card, offset)) =>
        val target = dealt + offset
        val existing = current.indexOf(card)
        val at = if (existing >= 0) existing else {
          val suit = catalog.denizens.find(_.id.value == card.value).get.suit
          current.indices.find(index => !targets(index) &&
            !cards.contains(current(index)) && catalog.denizens.exists(denizen =>
              denizen.id.value == current(index).value &&
                denizen.suit == suit)).get
        }
        current.updated(target, card).updated(at, current(target))
    }
    val remaining = order.drop(dealt)
    base.copy(denizenOrder = order, worldDeckOrder =
      remaining.take(10) ++ FirstGameRulesData.visions.take(2) ++
        remaining.slice(10, 25) ++ FirstGameRulesData.visions.drop(2) ++
        remaining.drop(25))
  }

  /** Journals one arranging delta at `at`, the stream's next sequence. */
  def seed(repository: InMemoryEventStreamRepository, gameId: String, at: Long,
      ops: Vector[CoreOperation]): Unit = {
    val arrange = WalkerStepRecorded("0", DeltaRecorded(OperationApplied(
      s"arrange the $gameId fixture")), ops, Vector.empty)
    val record = ujson.write(GameEventWire.encodeEvent(gameId, catalogRef, at,
      arrange).toOption.get)
    repository.append(gameId, ExpectedStream.AtNextSequence(at), Vector(record))
  }

  def topOfWorldDeck(card: DenizenId, to: Location,
      orientation: Orientation = Orientation.FaceUp): Move =
    Move(Piece.Card(card), PositionedLocation(Location.Deck(CardDeck.World),
      StackPosition.Top), PositionedLocation(to), Some(orientation))

  def cleared(ready: oathdigital.model.ReadyGame, site: SiteId)
      : Vector[CoreOperation] = ready.game.current.map.sites(site).forces match {
    case SiteForces.Occupied(kind, count) => Vector(Kill(Piece.Warbands(kind,
      count), PositionedLocation(Location.Site(site))))
    case SiteForces.Empty => Vector.empty
  }

  /** League Treaty on the first cradle site, ruled by an off-turn player's
    * warband and holding 2 favor of its own suit. The active player ends
    * Wake and begins Rest, which finishes Rest and parks on the ruler's
    * destination decision.
    */
  def leagueTreatyPark(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, gameId: String)
      : (GameAccepted, PlayerId, PlayerId) = {
    val setup = setUp(service, gameId, setupPlan = withWorldDeckTop(plan,
      Vector(treatyCard)))
    val Ready(base) = setup.state: @unchecked
    val current = base.game.current
    assert(current.commonCards.worldDeck.headOption.contains(treatyCard),
      "League Treaty must top the world deck")
    val active = current.turn.activePlayer
    val ruler = current.players.map(_.player).find(_ != active).get
    val lineage = current.players.find(_.player == ruler).get.lineage
    val site = current.map.cradle.head
    val suit = catalog.suitOf(treatyCard).get
    seed(repository, gameId, setup.nextSequence, cleared(base, site) ++ Vector(
      topOfWorldDeck(treatyCard, Location.Site(site)),
      Move(Piece.Warbands(ForceKind.Exile(lineage), 1),
        PositionedLocation(Location.PlayArea(ruler)),
        PositionedLocation(Location.Site(site))),
      Move(Piece.Favor(2), PositionedLocation(Location.FavorBank(suit)),
        PositionedLocation(Location.OnCard(treatyCard)))))
    val seeded = service.load(gameId).toOption.flatten.get
    val act = service.handle(gameId, seeded.nextSequence,
      GameCommand.EndWake(active)).toOption.get
    val parked = service.handle(gameId, act.nextSequence,
      GameCommand.BeginRest(active)).toOption.get
    assert(parked.continue match {
      case OathContinue.AwaitingRestDecision(owner, _) => owner == ruler
      case _ => false
    }, s"expected the ruler's Rest decision, got ${parked.continue}")
    (parked, active, ruler)
  }

  /** A Recover roll parked in Act, at the first catalog site Recover can
    * target.
    */
  def recoverRollPark(service: GameApplicationService, gameId: String)
      : (GameAccepted, PlayerId, Vector[PlayerId]) = {
    val recoverSite = catalog.sites.find(site =>
      site.recoverDifficulty.exists(d => d > 0 && d <= 4) &&
        site.relicSlots > 0 &&
        !site.handlers.exists(_.contains(".homeland-"))).get.id
    val recoverPlan = plan.copy(orderedSites = recoverSite +:
      plan.orderedSites.filterNot(_ == recoverSite))
    val actor = recoverPlan.firstPlayer
    val setup = setUp(service, gameId, recoverPlan.orderedSites, recoverPlan)
    val act = service.handle(gameId, setup.nextSequence,
      GameCommand.EndWake(actor)).toOption.get
    val parked = service.handle(gameId, act.nextSequence,
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(actor))).toOption.get
    assert(parked.continue == OathContinue.AwaitingRecoverRoll(actor,
      DecisionId(RecoverProcedure.rollDecisionId)), parked.continue.toString)
    (parked, actor, recoverPlan.participants.map(_.playerId))
  }

  /** The off-turn Oathkeeper tie from the service suite: the holder must
    * pick between two tied leaders after the active player's Travel.
    */
  def oathkeeperTiePark(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, gameId: String)
      : (GameAccepted, PlayerId, PlayerId, PlayerId) = {
    val setup = setUp(service, gameId)
    val Ready(base) = setup.state: @unchecked
    val active = base.game.current.turn.activePlayer
    val players = base.game.current.players.map(_.player)
    val holder = players.find(_ != active).get
    val leaders = players.filterNot(_ == holder)
    val lineageOf = base.game.current.players.map(p => p.player -> p.lineage).toMap
    val siteA = base.game.current.map.inPlay(0)
    val siteB = base.game.current.map.inPlay(1)
    seed(repository, gameId, setup.nextSequence,
      cleared(base, siteA) ++ cleared(base, siteB) ++ Vector(
        SetOathkeeper(Some(holder)),
        Move(Piece.Warbands(ForceKind.Exile(lineageOf(leaders(0))), 1),
          PositionedLocation(Location.PlayArea(leaders(0))),
          PositionedLocation(Location.Site(siteA))),
        Move(Piece.Warbands(ForceKind.Exile(lineageOf(leaders(1))), 1),
          PositionedLocation(Location.PlayArea(leaders(1))),
          PositionedLocation(Location.Site(siteB)))))
    val act = service.handle(gameId, setup.nextSequence + 1L,
      GameCommand.EndWake(active)).toOption.get
    val Ready(inAct) = act.state: @unchecked
    val activePlayer = inAct.game.current.players.find(_.player == active).get
    val destination = inAct.game.current.map.inPlay.find(id =>
      !activePlayer.pawnSite.contains(id) && id != siteA && id != siteB).get
    val parked = service.handle(gameId, act.nextSequence,
      GameCommand.StartWalker(ActionRef.Travel, StartPayload(active,
        Vector.empty, Vector(DecisionOptionRef.Site(destination))))).toOption.get
    assert(parked.events.last match {
      case WalkerParked(TriggeredProcedureRef.Oathkeeper, _, _, _, _) => true
      case _ => false
    }, s"expected a triggered Oathkeeper park, got ${parked.events.last}")
    assert(parked.continue == OathContinue.AwaitingOathkeeperRecipient(holder,
      DecisionId(OathkeeperProcedure.recipientDecisionId)), parked.continue.toString)
    (parked, active, holder, leaders(1))
  }

  /** Silver Tongue as the active player's faceup adviser, with two faceup
    * denizens of different suits at their pawn site. Every favor bank starts
    * with at least 3 favor, so Begin Rest stops at the Rest action, and
    * using Silver Tongue parks on its bank choice. The returned suit is
    * the first site card's, which that choice offers.
    */
  def silverTonguePark(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, gameId: String)
      : (GameAccepted, PlayerId, Suit) = {
    val bySuit = catalog.denizens.map(d => DenizenId(d.id.value) -> d.suit)
      .filterNot { case (id, _) => id == silverTongueCard || id == treatyCard }
    val first = bySuit.head
    val second = bySuit.find(_._2 != first._2).get
    val cards = Vector(silverTongueCard, first._1, second._1)
    val setup = setUp(service, gameId, setupPlan = withWorldDeckTop(plan, cards))
    val Ready(base) = setup.state: @unchecked
    val current = base.game.current
    assert(current.commonCards.worldDeck.take(3) == cards,
      "Silver Tongue and the two site cards must top the world deck")
    val active = current.turn.activePlayer
    val pawn = current.players.find(_.player == active).get.pawnSite.get
    seed(repository, gameId, setup.nextSequence, Vector(
      topOfWorldDeck(silverTongueCard, Location.PlayArea(active)),
      topOfWorldDeck(first._1, Location.Site(pawn)),
      topOfWorldDeck(second._1, Location.Site(pawn))))
    val act = service.handle(gameId, setup.nextSequence + 1L,
      GameCommand.EndWake(active)).toOption.get
    val resting = service.handle(gameId, act.nextSequence,
      GameCommand.BeginRest(active)).toOption.get
    assert(resting.continue == OathContinue.AwaitingRestAction(active),
      resting.continue.toString)
    val parked = service.handle(gameId, resting.nextSequence,
      GameCommand.UsePower(active, SilverTongue.id,
        DecisionOptionRef.Denizen(silverTongueCard))).toOption.get
    assert(parked.continue.isInstanceOf[OathContinue.AwaitingPowerDecision],
      parked.continue.toString)
    (parked, active, first._2)
  }
}
