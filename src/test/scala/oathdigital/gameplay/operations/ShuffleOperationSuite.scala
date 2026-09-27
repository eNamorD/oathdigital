package oathdigital.gameplay.operations

import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.model._

class ShuffleOperationSuite extends munit.FunSuite:
  private val executor = new OperationExecutor()
  private val cradle = SearchSource.RegionalDiscard(Region.Cradle)

  /** The Cradle discard pile holds the world deck's top three cards. */
  private val discarded = initialReady.updateCurrent(c => c.copy(
    commonCards = c.commonCards.copy(
      worldDeck = c.commonCards.worldDeck.drop(3),
      regionalDiscards = c.commonCards.regionalDiscards.updated(Region.Cradle,
        c.commonCards.worldDeck.take(3)))))
  private def zones(ready: ReadyGame): CardZones = ready.game.current.commonCards
  private val pile = zones(discarded).discard(Region.Cradle)
  private val deck = zones(discarded).worldDeck

  test("a shuffle writes the world deck's new order"):
    val done = executor.execute(discarded,
      Shuffle(SearchSource.WorldDeck, Some(deck.reverse)))
    assertEquals(done.map(zones(_).worldDeck), Right(deck.reverse))

  test("a shuffle writes a discard pile's new order and leaves the rest"):
    val done = executor.execute(discarded, Shuffle(cradle, Some(pile.reverse)))
      .toOption.get
    assertEquals(zones(done).discard(Region.Cradle), pile.reverse)
    assertEquals(zones(done).worldDeck, deck)

  test("an order that drops, adds or swaps a card is refused"):
    Vector(pile.tail, pile :+ deck.last, pile.tail :+ deck.last).foreach(
      order => assert(executor.execute(discarded,
        Shuffle(cradle, Some(order))).isLeft, order.toString))

  test("a shuffle the walker never ordered is refused"):
    assert(executor.execute(discarded, Shuffle(SearchSource.WorldDeck)).isLeft)

  test("a pile is named for a log line or a label"):
    assertEquals(SearchSource.name(SearchSource.WorldDeck), "world deck")
    assertEquals(SearchSource.name(cradle), "Cradle discard pile")

  test("a shuffle forgets its pile's cards, since no one knows which is where"):
    val peeker = discarded.game.current.players.head.player
    val peeked = pile.foldLeft(discarded)((ready, card) => executor.execute(
      ready, Peek(peeker, card, Location.RegionalDiscard(Region.Cradle)))
      .toOption.get)
    assert(pile.forall(peeked.knowledge.advisers(peeker).contains))
    val shuffled = executor.execute(peeked, Shuffle(cradle, Some(pile.reverse)))
      .toOption.get
    assert(shuffled.knowledge.advisers.values.forall(known =>
      pile.forall(!known.contains(_))), shuffled.knowledge.advisers.toString)
