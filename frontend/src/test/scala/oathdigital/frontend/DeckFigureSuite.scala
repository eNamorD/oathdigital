package oathdigital.frontend

import oathdigital.frontend.Dom.{all, one}
import oathdigital.frontend.TestBoardOptions.recording
import oathdigital.frontend.TestSites.quietSite
import oathdigital.model.PlayerColor
import org.scalajs.dom

/** The world deck and each region's discard are drawn as a card with its
  * count on the face, and a parked decision rings the card and its cost
  * badge only: the caption naming the pile sits under the ring.
  */
class DeckFigureSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val tracks = oathdigital.protocol.projection.GameTracksProjection(
    round = 1, visionsDrawn = 0, usurperLimited = false, limiterRound = 4,
    firstPlayerId = "red")
  private val facedown = CardDetails("hidden", "denizen", "Facedown denizen",
    orientation = Some("face-down"), hidden = true)
  private val table = TestProjections.actingProjection(players = Vector(red),
    world = Vector(
      GameRegion("cradle", Vector(quietSite("woods", "Deep Woods"))),
      GameRegion("hinterland", Vector(quietSite("shore", "Green Shore")),
        2, Some("denizen"), Vector(facedown, facedown)))).copy(
    worldDeckCount = 53, worldDeckTopCardKind = Some("denizen"),
    tracks = Some(tracks))

  private def offered(kind: String, id: String): OptionView =
    OptionView(DecisionOptionState(kind, id, "Pile",
      badge = Some("2 Supply")), OptionState.Available, None)

  private def region(root: dom.Element, label: String): dom.Element =
    one(root, s"""section.region[aria-label="$label"]""")

  test("a region's discard sits in its header, after the region's name"):
    val header = one(region(WorldBoardRenderer.world(table, BoardOptions.none),
      "Hinterland"), ":scope > .region-header")
    assertEquals(all(header, ":scope > *").map(_.getAttribute("class")),
      Vector("region-label", "deck-figure"))
    assertEquals(one(header, ".deck-caption").textContent, "Discard")

  test("the world deck sits beside the round tracker, in the Cradle"):
    val world = WorldBoardRenderer.world(table, BoardOptions.none)
    val row = one(region(world, "Cradle"), ".deck-row")
    assertEquals(all(row, ":scope > *").map(_.getAttribute("class")),
      Vector("round-tracker", "deck-figure"))
    assertEquals(one(row, ".deck-caption").textContent, "World deck")
    assertEquals(all(world, ".world > .deck-figure, .world > .pile-display"),
      Vector.empty)

  test("the count is drawn on the card"):
    val world = WorldBoardRenderer.world(table, BoardOptions.none)
    assertEquals(one(region(world, "Cradle"), ".deck-row .deck-card .pile-count")
      .textContent, "×53")
    assertEquals(one(region(world, "Hinterland"), ".deck-card .pile-count")
      .textContent, "×2")

  test("an offered pile rings the card and its badge, not the caption"):
    val world = WorldBoardRenderer.world(table, recording(
      offered("deck", "world"), offered("discard-pile", "hinterland")).options)
    Vector("deck:world", "discard-pile:hinterland").foreach { ref =>
      val ring = one(world, s"""[data-option-ref="$ref"]""")
      assert(ring.classList.contains("board-option"), ref)
      assertEquals(all(ring, ".deck-card").size, 1, ref)
      assertEquals(one(ring, ".target-detail-badge").textContent, "2 Supply")
      assertEquals(all(ring, ".deck-caption"), Vector.empty, ref)
    }
