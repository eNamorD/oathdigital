package oathdigital.frontend

import oathdigital.model.PlayerColor
import Dom.all
import TestBoardOptions.{available, recording}
import TestSites.quietSite
import TestProjections.actingProjection
import org.scalajs.dom

/** A region's discard pile opens as a card list, top first. */
class DiscardPileSuite extends munit.FunSuite:
  private val site = quietSite("site:woods", "Deep Woods")

  private val top = CardDetails("hidden", "denizen", "Facedown denizen",
    orientation = Some("face-down"), hidden = true)
  private val known = CardDetails("denizen:scryer", "denizen", "Scryer",
    suit = Some("arcane"))

  private def projection(pile: Vector[CardDetails]): GameProjection =
    actingProjection(
      players = Vector(GamePlayer("red", "Red", "Exile", PlayerColor.Red)),
      world = Vector(GameRegion("cradle", Vector(site), pile.size,
        pile.headOption.map(_.cardKind), pile)))

  private def world(pile: Vector[CardDetails]): dom.Element =
    WorldBoardRenderer.world(projection(pile), BoardOptions.none)

  private def discard(root: dom.Element): dom.html.Button =
    all(root, ".region .pile-open").headOption
      .getOrElse(fail("no discard pile")).asInstanceOf[dom.html.Button]

  override def afterEach(context: AfterEach): Unit = CardInspection.clear()

  test("the pile is a button that opens its cards top first"):
    var opened = Vector.empty[(String, Vector[String])]
    CardInspection.onOpen:
      case CardInspection.Request.Cards(title, cards, _) =>
        opened :+= (title -> cards.map(_.name))
      case _ => ()
    val pile = discard(world(Vector(top, known)))
    assertEquals(pile.tagName.toLowerCase, "button")
    assertEquals(pile.getAttribute("aria-label"), "Cradle discard pile, 2 cards")
    pile.click()
    assertEquals(opened, Vector("Cradle discard pile (2)" ->
      Vector("Facedown denizen", "Scryer")))

  test("an empty pile keeps its look and opens nothing"):
    val pile = discard(world(Vector.empty))
    assert(pile.disabled)
    assertEquals(all(pile, ".pile-empty").size, 1)
    // The pile shows its count as ×<n>.
    assertEquals(all(pile, ".pile-count").map(_.textContent), Vector("×0"))

  test("a pile that is an option opens its cards from the inspect button " +
      "only"):
    var opened = Vector.empty[String]
    CardInspection.onOpen:
      case CardInspection.Request.Cards(title, _, _) => opened :+= title
      case _ => ()
    val pile = DecisionOptionState("discard-pile", "cradle",
      "Discard to the Cradle discard")
    val options = recording(available(pile))
    val root = WorldBoardRenderer.world(projection(Vector(top, known)),
      options.options)
    discard(root).click()
    assertEquals((options.picks, opened), (Vector(pile), Vector.empty))
    val inspect = all(root, ".region .board-option-inspect").headOption
      .getOrElse(fail("no inspect button"))
    assertEquals(inspect.getAttribute("aria-label"),
      "Inspect Cradle discard pile")
    inspect.asInstanceOf[dom.html.Button].click()
    assertEquals(opened, Vector("Cradle discard pile (2)"))
