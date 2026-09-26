package oathdigital.application

import oathdigital.gameplay.operations.OperationExecutor
import oathdigital.gameplay.setup.FirstGameSetupFixture.{catalog, initialReady}
import oathdigital.model._

/** Knowledge follows the card (spec, "Knowledge follows the card"). The
  * board and the log read knowledge through the same `identifiesAt`, so
  * these checks hold for both. */
class CardKnowledgeSuite extends munit.FunSuite:
  private val executor = new OperationExecutor
  private val presentation = new GamePresentationProjector(catalog)
  private val current = initialReady.game.current

  /** A site with a relic, and three players whose pawns stand elsewhere, so
    * the pawn-at-site rule never identifies the relic for them. */
  private val (site, relic) = current.map.sites.collectFirst {
    case (id, state) if state.relics.nonEmpty => id -> state.relics.head.id
  }.get
  private val away = current.players.filterNot(_.pawnSite.contains(site))
    .map(_.player)
  private val Vector(owner, other, third) = away.take(3): @unchecked

  private def run(ops: CoreOperation*)(using munit.Location): ReadyGame =
    ops.foldLeft(initialReady) { (ready, op) =>
      executor.execute(ready, op).fold(error => fail(s"$op: $error"), identity) }

  private def knows(ready: ReadyGame, player: PlayerId, id: CardId) =
    presentation.identifiesAt(ready, Some(player), id)

  private val taken = Move(Piece.Card(relic),
    PositionedLocation(Location.Site(site)),
    PositionedLocation(Location.PlayArea(owner)), Some(Orientation.FaceDown))

  test("a player who gives a face-down relic away still knows it"):
    val handedOver = run(taken, Give(Piece.Card(relic), owner,
      Location.PlayArea(owner), Location.PlayArea(other)))
    assert(knows(handedOver, owner, relic))
    assert(knows(handedOver, other, relic))
    assert(!knows(handedOver, third, relic))

  test("a site peeker still knows a relic after another player takes it"):
    val peeked = run(Peek(other, relic, Location.Site(site)), taken)
    assert(knows(peeked, other, relic))
    assert(!knows(peeked, third, relic))
    assertEquals(peeked.knowledge.siteRelics.getOrElse(other, Map.empty)
      .getOrElse(site, Vector.empty), Vector.empty[RelicId])

  test("a relic buried into its deck is known by nobody when it comes back"):
    val buried = run(Peek(other, relic, Location.Site(site)),
      Bury(BuryableCard.Relic(relic), PositionedLocation(Location.Site(site))))
    Vector(buried.knowledge.heldRelics, buried.knowledge.advisers)
      .foreach(known => assert(!known.values.exists(_.contains(relic)), known))
    assert(!buried.knowledge.siteRelics.values.exists(_.values
      .exists(_.contains(relic))))
    val back = executor.execute(buried, Move(Piece.Card(relic),
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Bottom),
      PositionedLocation(Location.PlayArea(owner)), Some(Orientation.FaceDown)))
      .toOption.get
    assert(!knows(back, other, relic))

  test("a face-down adviser shuffled into the world deck is forgotten"):
    val adviser = current.players.find(_.player == owner).get.advisers
      .collectFirst { case DenizenState(id, Orientation.FaceDown, _) => id }.get
    val peeked = run(Peek(other, adviser, Location.PlayArea(owner)))
    assert(knows(peeked, other, adviser))
    val gone = executor.execute(peeked, Move(Piece.Card(adviser),
      PositionedLocation(Location.PlayArea(owner)),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Bottom)))
      .toOption.get
    assert(!gone.knowledge.advisers.values.exists(_.contains(adviser)))
