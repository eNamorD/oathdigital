package oathdigital.frontend

import oathdigital.frontend.Dom.{all, firstMatch}
import oathdigital.frontend.TestBoards.*
import oathdigital.model.PlayerColor
import oathdigital.testkit.Unwrap.{as, present}

import org.scalajs.dom

/** The players pane is the one pane that must never scroll, so what a board
  * spends its lines on is pinned here.
  */
class PlayerBoardSuite extends munit.FunSuite:
  private val board = quietBoard("red")
  private val oak = CardDetails("a1", "denizen", "Old Oak",
    suit = Some("order"), orientation = Some("face-up"))

  private val redExile = GamePlayer("red", "Red", "Exile", PlayerColor.Red)

  /** The players pane of a game with red alone, whose pawn stands at
    * "site:woods", holding `playerBoard`. `supplyMaximum` is the Supply
    * track's ceiling.
    */
  private def render(playerBoard: PlayerBoard, supplyMaximum: Int)
      : dom.Element =
    WorldBoardRenderer.playerBoards(
      TestProjections.actingProjection(players = Vector(redExile))
        .copy(pawnLocations = Vector(GamePawn("red", "site:woods")),
          playerBoards = Vector(playerBoard), supplyMaximum = supplyMaximum),
      "red")

  /** A Supply-at-Rest inspection the pane asked the overlay to open. */
  private final case class Opened(title: String, lines: Vector[String])

  test("name, role and resources share one line, and the location is gone"):
    val node = render(board.standingAt("site:woods"), supplyMaximum = 7)
    val identity = firstMatch(node, ".player-identity")
      .getOrElse(fail("no identity"))
    assert(identity.textContent.contains("Red"), identity.textContent)
    assertEquals(firstMatch(identity, ".player-role").map(_.textContent),
      Some("Exile"))
    assertEquals(all(identity, ".resources").size, 1)
    assertEquals(all(node, ".player-location"), Vector.empty)

  /** The track has a ceiling, so Supply reads against it: "7/7" says the
    * Act cannot buy any more of it, where "7" alone says nothing.
    */
  test("favor and secrets are glyphs, warbands and supply stay words"):
    // Red has 3 warbands, 2 favor and 1 of 2 secrets faceup, and 7 Supply
    // against a track that holds 7.
    val tracked = board.withWarbands(3).withFavor(2)
      .withSecrets(faceUp = 1, faceDown = 1).withSupply(7)
    val resources = firstMatch(render(tracked, supplyMaximum = 7),
      ".resources").getOrElse(fail("no resources"))
    assertEquals(all(resources, ".token-glyph").map(_.getAttribute("aria-label")),
      Vector("favor", "secret"))
    assertEquals(all(resources, ".resource").map(_.textContent),
      Vector("Warbands 3", "2", "1/2", "Supply 7/7"))

  /** Two labelled rows cost more height than the pane has, and the box shape
    * already says which card is a relic, exactly as it does at a site. A
    * revealed Vision is a player state, not a card in the hand, so it sits
    * in the slots row instead (see the slots-row tests below).
    */
  test("advisers and relics share one unlabelled row; a revealed vision does not"):
    val node = WorldBoardRenderer.playerBoards(
      TestProjections.actingProjection(players = Vector(redExile))
        .copy(playerBoards = Vector(board.withAdvisers(oak)
          .withRelics(CardDetails("r1", "relic", "Crown",
            orientation = Some("face-up")))
          .copy(revealedVision = Some(CardDetails("v1", "vision", "Vision",
            orientation = Some("face-up")))))),
      "red")
    assertEquals(all(node, ".board-cards").size, 1)
    assertEquals(all(node, ".board-cards .card-face").map(_.getAttribute("class")),
      Vector("card-face card-face-denizen",
        "card-face card-face-denizen card-slot-empty",
        "card-face card-face-denizen card-slot-empty",
        "card-face card-face-relic"))
    assertEquals(all(node, ".board-cards strong"), Vector.empty)
    assertEquals(firstMatch(node, ".board-cards")
      .map(_.getAttribute("aria-label")), Some("Cards in play"))

  /** The strip is where a player looks for who holds what, so the title sits
    * on its holder's line and nowhere else.
    */
  private def titled(side: String): dom.Element =
    WorldBoardRenderer.playerBoards(
      TestProjections.actingProjection(players = Vector(redExile,
        GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)))
        .copy(playerBoards = Vector(board, board.copy(playerId = "blue")),
          oathkeeper = Some(OathkeeperStatus("supremacy", Some("blue"), side,
            usurperLimited = true, winnerPlayerId = None))),
      "red")

  test("the Oathkeeper badge sits on its holder's line only"):
    val node = titled("oathkeeper")
    val badges = all(node, ".player-title")
    assertEquals(badges.map(_.textContent), Vector("Oathkeeper"))
    assertEquals(badges.map(_.closest(".player-board").getAttribute(
      "data-player-id")), Vector("blue"))
    assertEquals(badges.map(_.getAttribute("data-tip")),
      Vector("Oathkeeper of Supremacy"))

  test("the badge reads Usurper once the title flips"):
    assertEquals(all(titled("usurper"), ".player-title")
      .map(badge => badge.textContent -> badge.classList.contains("title-usurper")),
      Vector("Usurper" -> true))

  /** The seating vector is already the cyclic turn order, so anchoring it on
    * the viewer gives turn order from their own seat -- and it stays put as
    * turns pass, unlike an active-player anchor.
    */
  test("the viewer's seat leads, and the rest follow in turn order"):
    val seats = Vector("red", "blue", "white", "black")
      .map(id => GamePlayer(id, id.capitalize, "Exile", PlayerColor.Red))
    assertEquals(WorldBoardRenderer.seatOrder(seats, Some("white"))
      .map(_.playerId), Vector("white", "black", "red", "blue"))
    assertEquals(WorldBoardRenderer.seatOrder(seats, Some("red"))
      .map(_.playerId), Vector("red", "blue", "white", "black"))

  test("a viewer who holds no seat leaves the order alone"):
    val seats = Vector("red", "blue")
      .map(id => GamePlayer(id, id.capitalize, "Exile", PlayerColor.Red))
    assertEquals(WorldBoardRenderer.seatOrder(seats, None).map(_.playerId),
      Vector("red", "blue"))
    assertEquals(WorldBoardRenderer.seatOrder(seats, Some("ghost"))
      .map(_.playerId), Vector("red", "blue"))

  /** The seat the client holds decides, in both modes: a trusted session
    * adopts its seat from the projection's viewer before rendering and will
    * not display a projection that disagrees with it, so the projected
    * viewer is never the more correct answer -- only sometimes present.
    */
  test("the rendered strip leads with the seat the client holds"):
    val seats = Vector("red", "blue", "white")
      .map(id => GamePlayer(id, id.capitalize, "Exile", PlayerColor.Red))
    def strip(viewer: Option[String], seat: String): Vector[String] =
      all(WorldBoardRenderer.playerBoards(
        TestProjections.actingProjection(players = seats)
          .copy(viewerPlayerId = viewer),
        seat), ".player-board")
        .map(_.getAttribute("data-player-id"))
    assertEquals(strip(Some("white"), "white"), Vector("white", "red", "blue"))
    // A development session holds a seat but is named by no viewer.
    assertEquals(strip(None, "blue"), Vector("blue", "white", "red"))

  /** The count alone cannot say which secrets are spendable, so the sentence
    * the old line carried stays as the accessible name.
    */
  test("the secrets breakdown survives as the accessible name"):
    // Red owns 2 secrets: 1 faceup and 1 facedown, none committed.
    val owned = board.withSecrets(faceUp = 1, faceDown = 1, committed = 0)
    val secrets = present(all(render(owned, supplyMaximum = 7),
      ".resource").find(resource => all(resource, ".token-glyph")
        .exists(_.getAttribute("aria-label") == "secret")),
      "the secrets resource")
    assertEquals(secrets.getAttribute("aria-label"),
      "Secrets 1/2. 1 available of 2 owned; 1 facedown and 0 committed")

  /** A title and a Vision are states of the player, not cards in a hand, so
    * they get a row of their own between the identity line and the cards.
    */
  private def renderWith(playerBoard: PlayerBoard,
      oathkeeper: Option[OathkeeperStatus]): dom.Element =
    WorldBoardRenderer.playerBoards(
      TestProjections.actingProjection(players = Vector(redExile))
        .copy(playerBoards = Vector(playerBoard), oathkeeper = oathkeeper),
      "red")

  test("the title and the Vision sit in their own row, not among the cards"):
    val vision = CardDetails("vision:vision-of-faith", "vision",
      "Vision of Faith", orientation = Some("face-up"),
      rulesText = Some("Wake: You win if you hold the **Darkest Secret**."))
    val node = renderWith(board.copy(revealedVision = Some(vision)),
      Some(OathkeeperStatus("devotion", Some("red"), "oathkeeper",
        usurperLimited = false, None)))
    assertEquals(all(node, ".player-identity .player-title"), Vector.empty)
    val slots = firstMatch(node, ".player-slots")
      .getOrElse(fail("no slots row"))
    assertEquals(all(slots, ".player-title").size, 1)
    assertEquals(firstMatch(slots, ".player-slot-vision")
      .map(_.textContent), Some("Vision of Faith"))
    assertEquals(all(node, ".board-cards .card-face")
      .map(_.getAttribute("data-card-id"))
      .contains("vision:vision-of-faith"), false)

  test("a player with neither a title nor a Vision has no slots row"):
    assertEquals(all(render(board, supplyMaximum = 7), ".player-slots"),
      Vector.empty)

  /** Red's board with a revealed Vision of Faith and the Oathkeeper title of
    * Devotion, and the titles the inspector is asked to open while `act`
    * clicks inside it.
    */
  private def opensOnClick(act: dom.Element => Unit): Vector[String] =
    var opened = Vector.empty[String]
    CardInspection.onOpen:
      case CardInspection.Request.Text(title, _, _) => opened = opened :+ title
      case CardInspection.Request.Card(card, _) => opened = opened :+ card.name
      case CardInspection.Request.Cards(title, _, _) => opened = opened :+ title
      case CardInspection.Request.Confirm(question, _, _) =>
        opened = opened :+ question
      case CardInspection.Request.Choices(question, _, _) =>
        opened = opened :+ question
      case CardInspection.Request.Power(title, _, _, _) =>
        opened = opened :+ title
      case CardInspection.Request.RuledCard(card, _, _) =>
        opened = opened :+ card.name
    val vision = CardDetails("vision:vision-of-faith", "vision",
      "Vision of Faith", orientation = Some("face-up"))
    val node = renderWith(board.copy(revealedVision = Some(vision)),
      Some(OathkeeperStatus("devotion", Some("red"), "oathkeeper",
        usurperLimited = false, None)))
    act(node)
    CardInspection.clear()
    opened

  test("the title pill opens the Oath text"):
    assertEquals(opensOnClick(node =>
      as[dom.html.Button](firstMatch(node, ".player-slots .player-title")
        .getOrElse(fail("no title pill"))).click()),
      Vector("Oathkeeper of Devotion"))

  test("the Vision pill opens its card"):
    assertEquals(opensOnClick(node =>
      as[dom.html.Button](firstMatch(node, ".player-slot-vision")
        .getOrElse(fail("no vision pill"))).click()),
      Vector("Vision of Faith"))

  test("Supply is a button that opens Supply at Rest for that board"):
    var opened = Vector.empty[Opened]
    CardInspection.onOpen:
      case CardInspection.Request.Text(title, lines, _) =>
        opened :+= Opened(title, lines)
      case _ => ()
    // Red has 7 Supply against a track that holds 7.
    val sevenOfSeven = board.withSupply(7)
    val supply = as[dom.html.Button](firstMatch(
      render(sevenOfSeven, supplyMaximum = 7), ".resources .supply-open")
      .getOrElse(fail("no Supply button")))
    assertEquals(supply.textContent, "Supply 7/7")
    supply.click()
    CardInspection.clear()
    assertEquals(opened.map(_.title), Vector("Supply at Rest"))
    assert(opened.head.lines.head.startsWith("Warbands in bag: "),
      opened.head.lines.head)

  test("a held banner names what it holds: favor or secrets, not resources"):
    val banners = Vector(
      oathdigital.protocol.projection.BannerProjection("peoples-favor",
        "peoples-favor", Some("red"), 3),
      oathdigital.protocol.projection.BannerProjection("darkest-secret",
        "darkest-secret", Some("red"), 1))
    val rendered = render(board.copy(banners = banners), supplyMaximum = 7)
    val lines = all(rendered, ".player-banner").map(_.textContent)
    assertEquals(lines.map(_.split(" · ").last), Vector("3 favor", "1 secret"))
    assert(lines.forall(!_.contains("resources")), lines)

  test("empty adviser slots are drawn up to the player's limit"):
    def placeholders(limit: Int) = all(render(
      board.withAdvisers(oak).copy(adviserLimit = limit), supplyMaximum = 7), ".board-cards .card-slot-empty")
      .map(_.getAttribute("data-slot-index"))
    assertEquals(placeholders(3), Vector("1", "2"))
    assertEquals(placeholders(2), Vector("1"))
