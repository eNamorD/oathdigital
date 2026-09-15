package oathdigital.application

import oathdigital.gameplay.OathContinue
import oathdigital.gameplay.OathState.Ready
import oathdigital.gameplay.operations.{CoreOperation, Kill, Location, Move,
  Piece, PositionedLocation, StackPosition}
import oathdigital.gameplay.setup.{FirstGameRulesData, FirstGameSetupPlan}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.DeltaMeaning.OperationApplied
import oathdigital.gameplay.walker.WalkerStepPayload.DeltaRecorded
import oathdigital.gameplay.walker.WalkerStepRecorded
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
          val suit = catalog.denizens.find(_.id.value == card.value).get.suit.value
          current.indices.find(index => !targets(index) &&
            !cards.contains(current(index)) && catalog.denizens.exists(denizen =>
              denizen.id.value == current(index).value &&
                denizen.suit.value == suit)).get
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

  def cleared(ready: oathdigital.gameplay.ReadyGame, site: SiteId)
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
    val suit = Suit.all.find(s => catalog.denizens.exists(d =>
      d.id.value == treatyCard.value && d.suit.value == s.key)).get
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
}
