package oathdigital.model

/** A pile a Search draws from, as the board option that names it (search
  * source design, ruling 2). */
class SearchSourceSuite extends munit.FunSuite:
  private val piles: Vector[SearchSource] =
    SearchSource.WorldDeck +: Region.all.map(SearchSource.RegionalDiscard(_))

  test("each pile is a board option that names it back"):
    piles.foreach(pile =>
      assertEquals(SearchSource.fromRef(SearchSource.ref(pile)), Some(pile)))
    assertEquals(piles.map(SearchSource.option(_).ref),
      piles.map(SearchSource.ref))
    val world = SearchSource.ref(SearchSource.WorldDeck)
    assertEquals(world.kind -> world.wireId, "deck" -> "world")
    val cradle = SearchSource.ref(SearchSource.RegionalDiscard(Region.Cradle))
    assertEquals(cradle.kind -> cradle.wireId, "discard-pile" -> "cradle")

  test("another deck or any other ref names no pile"):
    assertEquals(SearchSource.fromRef(DecisionOptionRef.Deck(CardDeck.Relic)),
      None)
    assertEquals(SearchSource.fromRef(DecisionOptionRef.Button("world-deck")),
      None)
