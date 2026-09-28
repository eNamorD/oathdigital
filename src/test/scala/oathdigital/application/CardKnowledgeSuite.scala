package oathdigital.application

import oathdigital.gameplay.operations.OperationExecutor
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}

/** Knowledge follows the card (spec, "Knowledge follows the card"). The
  * board and the log read knowledge through the same `identifiesAt`, so
  * these checks hold for both. */
class CardKnowledgeSuite extends munit.FunSuite:
  private val executor = new OperationExecutor
  private val presentation = new GamePresentationProjector(catalog)
  /** Sticky Fire lies at Dunes, where no pawn stands, so the pawn-at-site
    * rule never identifies it for p1, p2 or p3. p1 holds a facedown
    * Wrestlers. */
  private val board: ReadyGame = Table.start
    .relicAt("Sticky Fire", at = "Dunes")
    .adviser(Table.p1, "Wrestlers", facedown = true)
    .ready
  private val current = board.game.current
  private val (site, relic) =
    (CatalogNames.site("Dunes"), CatalogNames.relic("Sticky Fire"))
  private val (owner, other, third) = (Table.p1, Table.p2, Table.p3)

  private def run(ops: CoreOperation*)(using munit.Location): ReadyGame =
    ops.foldLeft(board) { (ready, op) =>
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

  /** Every card pile is face down at the table: no one tracks a card once it
    * enters one, so its knowledge is cleared as for a deck. */
  test("a face-down adviser discarded or dispossessed is forgotten by everyone"):
    val adviser = current.players.find(_.player == owner).get.advisers
      .collectFirst { case DenizenState(id, Orientation.FaceDown, _) => id }.get
    val peeked = run(Peek(other, adviser, Location.PlayArea(owner)))
    Vector(
      PositionedLocation(Location.RegionalDiscard(Region.Cradle),
        StackPosition.Top),
      PositionedLocation(Location.Dispossessed)).foreach { pile =>
      val gone = executor.execute(peeked, Move(Piece.Card(adviser),
        PositionedLocation(Location.PlayArea(owner)), pile))
        .fold(error => fail(s"$pile: $error"), identity)
      assert(!gone.knowledge.advisers.values.exists(_.contains(adviser)),
        s"$pile: ${gone.knowledge.advisers}")
    }

  /** Scryer and Oracular Pig peek into a pile. A card entering a pile is
    * forgotten, so a record of a card still in one comes from such a peek. */
  test("a card peeked in the world deck or a discard pile is named to its peeker alone"):
    val Vector(top, next) = current.commonCards.worldDeck.take(2): @unchecked
    val staged = board.updateCurrent(c => c.copy(commonCards =
      c.commonCards.copy(worldDeck = c.commonCards.worldDeck.filterNot(_ == next),
        regionalDiscards = c.commonCards.regionalDiscards.updated(Region.Cradle,
          c.commonCards.discard(Region.Cradle) :+ next))))
    val peeked = Vector(Peek(owner, top, Location.Deck(CardDeck.World)),
      Peek(owner, next, Location.RegionalDiscard(Region.Cradle)))
      .foldLeft(staged)((ready, op) =>
        executor.execute(ready, op).fold(error => fail(s"$op: $error"), identity))
    Vector[CardId](top, next).foreach { card =>
      assert(!knows(staged, owner, card), s"$card before the peek")
      assert(knows(peeked, owner, card), s"$card to its peeker")
      assert(!knows(peeked, other, card), s"$card to another player")
    }

  test("a relic set aside is forgotten by everyone, its holder included"):
    val held = run(Peek(other, relic, Location.Site(site)), taken)
    assert(knows(held, other, relic))
    val aside = executor.execute(held, Move(Piece.Card(relic),
      PositionedLocation(Location.PlayArea(owner)),
      PositionedLocation(Location.SetAsideRelics)))
      .fold(error => fail(s"set aside: $error"), identity)
    assert(!aside.knowledge.heldRelics.values.exists(_.contains(relic)),
      aside.knowledge.heldRelics)
