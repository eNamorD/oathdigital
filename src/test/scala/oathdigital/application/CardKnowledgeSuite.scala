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
  private val (site, relic) =
    (CatalogNames.site("Dunes"), CatalogNames.relic("Sticky Fire"))
  private val (owner, other, third) = (Table.p1, Table.p2, Table.p3)

  /** `op` executed on `ready`; fails with the refusal. */
  private def executed(ready: ReadyGame, op: CoreOperation)(
      using munit.Location): ReadyGame =
    executor.execute(ready, op).fold(error => fail(s"$op: $error"), identity)

  private def run(ops: CoreOperation*)(using munit.Location): ReadyGame =
    ops.foldLeft(board)(executed)

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
    val back = executed(buried, Move(Piece.Card(relic),
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Bottom),
      PositionedLocation(Location.PlayArea(owner)), Some(Orientation.FaceDown)))
    assert(!knows(back, other, relic))

  test("a face-down adviser shuffled into the world deck is forgotten"):
    val adviser = CatalogNames.denizen("Wrestlers")
    val peeked = run(Peek(other, adviser, Location.PlayArea(owner)))
    assert(knows(peeked, other, adviser))
    val gone = executed(peeked, Move(Piece.Card(adviser),
      PositionedLocation(Location.PlayArea(owner)),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Bottom)))
    assert(!gone.knowledge.advisers.values.exists(_.contains(adviser)))

  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val cradleDiscard = PositionedLocation(
    Location.RegionalDiscard(Region.Cradle), StackPosition.Top)

  /** Wrestlers, peeked by `other`, moved from `owner`'s play area to the
    * Cradle discard pile. */
  private lazy val discardedAdviser = run(
    Peek(other, wrestlers, Location.PlayArea(owner)),
    Move(Piece.Card(wrestlers), PositionedLocation(Location.PlayArea(owner)),
      cradleDiscard))

  /** The Cradle discard pile's card names, top first, as `viewer` sees
    * them. */
  private def cradleNames(ready: ReadyGame, viewer: PlayerId) =
    presentation.readyWorld(ready, Some(viewer)).find(_.regionId == "cradle")
      .getOrElse(fail("no cradle")).discardCards.map(_.name)

  /** A discard pile keeps what players knew: they saw the card go in. */
  test("a face-down adviser discarded stays known to its holder and its peeker"):
    Vector(owner, other).foreach { player =>
      assert(knows(discardedAdviser, player, wrestlers), player)
      assertEquals(cradleNames(discardedAdviser, player).headOption,
        Some("Wrestlers"), player)
    }
    assert(!knows(discardedAdviser, third, wrestlers))
    assertEquals(cradleNames(discardedAdviser, third).headOption,
      Some("Facedown denizen"))

  test("a discarded card moved to another discard pile stays known"):
    val moved = executed(discardedAdviser, Move(Piece.Card(wrestlers),
      cradleDiscard, PositionedLocation(
        Location.RegionalDiscard(Region.Provinces), StackPosition.Top)))
    assert(knows(moved, owner, wrestlers))
    assert(knows(moved, other, wrestlers))
    assert(!knows(moved, third, wrestlers))

  test("a discarded card put into the world deck is forgotten by everyone"):
    val gone = executed(discardedAdviser, Move(Piece.Card(wrestlers),
      cradleDiscard,
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Bottom)))
    assert(!gone.knowledge.advisers.values.exists(_.contains(wrestlers)),
      gone.knowledge.advisers)

  test("a card discarded from a Search hand is known to the searcher alone"):
    val drawn = CatalogNames.denizen("Fae Merchant")
    val discarded = executed(Table.from(board).hand(owner, drawn).ready,
      Move(Piece.Card(drawn), PositionedLocation(Location.Hand(owner)),
        cradleDiscard))
    assert(knows(discarded, owner, drawn))
    assert(!knows(discarded, other, drawn))

  /** The dispossessed pile still forgets, as a deck does. */
  test("a face-down adviser dispossessed is forgotten by everyone"):
    val gone = run(Peek(other, wrestlers, Location.PlayArea(owner)),
      Move(Piece.Card(wrestlers), PositionedLocation(Location.PlayArea(owner)),
        PositionedLocation(Location.Dispossessed)))
    assert(!gone.knowledge.advisers.values.exists(_.contains(wrestlers)),
      gone.knowledge.advisers)

  /** Scryer and Oracular Pig peek into a pile, so a player can come to
    * know a card that was already in one. */
  test("a card peeked in the world deck or a discard pile is named to its peeker alone"):
    // Threatening Roar tops the world deck; Fae Merchant lies in the Cradle
    // discard pile.
    val top = CatalogNames.denizen("Threatening Roar")
    val next = CatalogNames.denizen("Fae Merchant")
    val staged = Table.from(board).worldDeckTop(top)
      .discarded(Region.Cradle, next).unchecked
    val peeked = Vector(Peek(owner, top, Location.Deck(CardDeck.World)),
      Peek(owner, next, Location.RegionalDiscard(Region.Cradle)))
      .foldLeft(staged)(executed)
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
