package oathdigital.frontend

import org.scalajs.dom

/** The players pane is the one pane that must never scroll, so what a board
  * spends its lines on is pinned here.
  */
class PlayerBoardSuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private def one(node: dom.Element, selector: String): Option[dom.Element] =
    all(node, selector).headOption

  private val board = PlayerBoard("red", warbands = 3, favor = 2,
    faceUpSecrets = 1, faceDownSecrets = 0, committedSecrets = 0,
    totalSecrets = 2, supply = 7, pawnSiteId = Some("site:woods"),
    advisers = Vector(CardDetails("a1", "denizen", "Old Oak",
      suit = Some("order"), orientation = Some("face-up"))),
    relics = Vector.empty, revealedVision = None)

  private def render(): dom.Element = WorldBoardRenderer.playerBoards(
    GameProjection("game", 1L, "act", Some("red"),
      Vector(GamePlayer("red", "Red", "Exile", PlayerColorToken.Red)),
      Vector.empty,
      Vector(GamePawn("red", "site:woods")), Vector.empty, ready = true,
      completed = false, playerBoards = Vector(board)),
    new RecordingView("game", "red"))

  test("name, role and resources share one line, and the location is gone") {
    val node = render()
    val identity = one(node, ".player-identity").getOrElse(fail("no identity"))
    assert(identity.textContent.contains("Red"), identity.textContent)
    assertEquals(one(identity, ".player-role").map(_.textContent), Some("Exile"))
    assertEquals(all(identity, ".resources").size, 1)
    assertEquals(all(node, ".player-location"), Vector.empty)
  }

  test("favor and secrets are glyphs, warbands and supply stay words") {
    val resources = one(render(), ".resources").getOrElse(fail("no resources"))
    assertEquals(all(resources, ".token-glyph").map(_.getAttribute("aria-label")),
      Vector("favor", "secret"))
    assertEquals(all(resources, ".resource").map(_.textContent),
      Vector("Warbands 3", "2", "1/2", "Supply 7"))
  }

  /** Two labelled rows cost more height than the pane has, and the box shape
    * already says which card is a relic, exactly as it does at a site.
    */
  test("advisers, relics and a revealed vision share one unlabelled row") {
    val node = WorldBoardRenderer.playerBoards(
      GameProjection("game", 1L, "act", Some("red"),
        Vector(GamePlayer("red", "Red", "Exile", PlayerColorToken.Red)),
        Vector.empty, Vector.empty, Vector.empty, ready = true,
        completed = false, playerBoards = Vector(board.copy(
          relics = Vector(CardDetails("r1", "relic", "Crown",
            orientation = Some("face-up"))),
          revealedVision = Some(CardDetails("v1", "vision", "Vision",
            orientation = Some("face-up")))))),
      new RecordingView("game", "red"))
    assertEquals(all(node, ".board-cards").size, 1)
    assertEquals(all(node, ".board-cards .card-face").map(_.getAttribute("class")),
      Vector("card-face card-face-denizen", "card-face card-face-relic",
        "card-face card-face-denizen"))
    assertEquals(all(node, ".board-cards strong"), Vector.empty)
    assertEquals(one(node, ".board-cards").map(_.getAttribute("aria-label")),
      Some("Cards in play"))
  }

  /** The seating vector is already the cyclic turn order, so anchoring it on
    * the viewer gives turn order from their own seat -- and it stays put as
    * turns pass, unlike an active-player anchor.
    */
  test("the viewer's seat leads, and the rest follow in turn order") {
    val seats = Vector("red", "blue", "white", "black")
      .map(id => GamePlayer(id, id.capitalize, "Exile", PlayerColorToken.Red))
    assertEquals(WorldBoardRenderer.seatOrder(seats, Some("white"))
      .map(_.playerId), Vector("white", "black", "red", "blue"))
    assertEquals(WorldBoardRenderer.seatOrder(seats, Some("red"))
      .map(_.playerId), Vector("red", "blue", "white", "black"))
  }

  test("a viewer who holds no seat leaves the order alone") {
    val seats = Vector("red", "blue")
      .map(id => GamePlayer(id, id.capitalize, "Exile", PlayerColorToken.Red))
    assertEquals(WorldBoardRenderer.seatOrder(seats, None).map(_.playerId),
      Vector("red", "blue"))
    assertEquals(WorldBoardRenderer.seatOrder(seats, Some("ghost"))
      .map(_.playerId), Vector("red", "blue"))
  }

  test("the rendered strip leads with the viewer's own board") {
    val seats = Vector("red", "blue", "white")
      .map(id => GamePlayer(id, id.capitalize, "Exile", PlayerColorToken.Red))
    val node = WorldBoardRenderer.playerBoards(
      GameProjection("game", 1L, "act", Some("red"), seats, Vector.empty,
        Vector.empty, Vector.empty, ready = true, completed = false,
        viewerPlayerId = Some("white")),
      new RecordingView("game", "white"))
    assertEquals(all(node, ".player-board").map(_.getAttribute("data-player-id")),
      Vector("white", "red", "blue"))
  }

  /** A development session is not a seat, so the projection names no viewer;
    * the seat the client is acting as says the same thing.
    */
  test("with no viewer named, the strip leads with the seat the client holds") {
    val seats = Vector("red", "blue", "white")
      .map(id => GamePlayer(id, id.capitalize, "Exile", PlayerColorToken.Red))
    val node = WorldBoardRenderer.playerBoards(
      GameProjection("game", 1L, "act", Some("red"), seats, Vector.empty,
        Vector.empty, Vector.empty, ready = true, completed = false),
      new RecordingView("game", "blue"))
    assertEquals(all(node, ".player-board").map(_.getAttribute("data-player-id")),
      Vector("blue", "white", "red"))
  }

  /** The count alone cannot say which secrets are spendable, so the sentence
    * the old line carried stays as the accessible name.
    */
  test("the secrets breakdown survives as the accessible name") {
    val secrets = all(render(), ".resource").apply(2)
    assertEquals(secrets.getAttribute("aria-label"),
      "Secrets 1/2. 1 available of 2 owned; 0 facedown and 0 committed")
  }
}
