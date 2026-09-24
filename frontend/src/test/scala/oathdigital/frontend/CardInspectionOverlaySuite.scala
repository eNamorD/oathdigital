package oathdigital.frontend

import org.scalajs.dom
import scala.scalajs.js

/** The overlay is the only place a card's full rules appear after the hover
  * popover was retired. It renders at document level, above `.game-pane`,
  * which is what lets that pane keep `overflow: hidden`.
  * Runs under jsdom (`Test / jsEnv` in `build.sbt`).
  */
class CardInspectionOverlaySuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private def one(node: dom.Element, selector: String): Option[dom.Element] =
    all(node, selector).headOption

  private def press(node: dom.Element, key: String): Unit =
    node.dispatchEvent(new dom.KeyboardEvent("keydown",
      js.Dynamic.literal(key = key, bubbles = true)
        .asInstanceOf[dom.KeyboardEventInit]))

  private def click(node: dom.Element): Unit =
    node.dispatchEvent(new dom.MouseEvent("click",
      new dom.MouseEventInit { bubbles = true }))

  private val card = CardDetails("d1", "denizen", "Old Oak",
    suit = Some("order"), restrictions = Some("site-only"),
    rulesText = Some("**ACTION:** Gain [favor].\n\n[secret] Persistent."),
    orientation = Some("face-up"), relicValue = None, favor = 1)

  private val hidden = CardDetails("hidden", "relic", "Facedown relic",
    orientation = Some("face-down"), hidden = true)

  private def fixture(): (dom.Element, CardInspectionOverlay) = {
    val root = dom.document.createElement("div")
    dom.document.body.appendChild(root)
    (root, new CardInspectionOverlay(root))
  }

  private def origin(): dom.html.Element = {
    val node = dom.document.createElement("button").asInstanceOf[dom.html.Element]
    node.setAttribute("tabindex", "0")
    dom.document.body.appendChild(node)
    node
  }

  test("the overlay is hidden until a card opens it, and shows that card in full") {
    val (root, overlay) = fixture()
    assert(root.querySelector(".card-overlay").hasAttribute("hidden"))
    overlay.show(card, origin())
    assert(overlay.isOpen)
    val node = root.querySelector(".card-overlay")
    assert(!node.hasAttribute("hidden"))
    assertEquals(node.getAttribute("role"), "dialog")
    assertEquals(one(node, ".card-name").map(_.textContent), Some("Old Oak"))
    // Field names come back in the overlay, unlike on the face.
    assert(node.textContent.contains("Suit"), node.textContent)
    assert(node.textContent.contains("Restrictions"), node.textContent)
    overlay.dispose(); root.remove()
  }

  /** Inspection is for reading a card, and a card you are allowed to read has
    * no reason to show you its back.
    */
  test("a card the viewer knows is inspected face-up, whichever way it lies") {
    val (root, overlay) = fixture()
    overlay.show(card.copy(orientation = Some("face-down")), origin())
    val node = root.querySelector(".card-overlay")
    assertEquals(all(node, ".card-back-letter"), Vector.empty)
    assertEquals(one(node, ".card-name").map(_.textContent), Some("Old Oak"))
    assert(!node.querySelector(".card-face").classList.contains("card-face-down"))
    overlay.dispose(); root.remove()
  }

  test("the details lead with the card name, above the suit") {
    val (root, overlay) = fixture()
    overlay.show(card, origin())
    val details = root.querySelector(".card-overlay-details")
    assertEquals(details.firstChild.asInstanceOf[dom.Element].getAttribute("class"),
      "card-overlay-name")
    assertEquals(details.firstChild.textContent, "Old Oak")
    assertEquals(one(details, ".card-overlay-property dt").map(_.textContent),
      Some("Suit"))
    overlay.dispose(); root.remove()
  }

  test("an unrestricted card gets no Restrictions row") {
    val (root, overlay) = fixture()
    overlay.show(card.copy(restrictions = Some("unrestricted")), origin())
    val node = root.querySelector(".card-overlay")
    assert(!node.textContent.contains("Restrictions"), node.textContent)
    assert(node.textContent.contains("Suit"), node.textContent)
    overlay.dispose(); root.remove()
  }

  test("multi-power rules render one block per power with glyphs") {
    val (root, overlay) = fixture()
    overlay.show(card, origin())
    val node = root.querySelector(".card-overlay")
    assertEquals(all(node, ".rules-power").size, 2)
    assertEquals(all(node, ".card-overlay-rules .token-glyph")
      .map(_.getAttribute("aria-label")), Vector("favor", "secret"))
    assertEquals(all(node, ".card-overlay-rules strong").map(_.textContent),
      Vector("ACTION:"))
    overlay.dispose(); root.remove()
  }

  test("a card the viewer cannot identify renders as face-down, not as a card") {
    val (root, overlay) = fixture()
    overlay.show(hidden, origin())
    val node = root.querySelector(".card-overlay")
    assertEquals(all(node, ".card-overlay-rules"), Vector.empty)
    assertEquals(one(node, ".card-back-letter").map(_.textContent), Some("R"))
    assert(!node.textContent.contains("Suit"), node.textContent)
    overlay.dispose(); root.remove()
  }

  test("Escape, the Close button and a click on the overlay each dismiss it") {
    Vector[(dom.Element, CardInspectionOverlay) => Unit](
      (root, _) => press(root.querySelector(".card-overlay"), "Escape"),
      (root, _) => click(root.querySelector(".card-overlay-close")),
      (root, _) => click(root.querySelector(".card-overlay"))
    ).foreach { dismiss =>
      val (root, overlay) = fixture()
      overlay.show(card, origin())
      assert(overlay.isOpen)
      dismiss(root, overlay)
      assert(!overlay.isOpen)
      assert(root.querySelector(".card-overlay").hasAttribute("hidden"))
      overlay.dispose(); root.remove()
    }
  }

  test("a click on the card itself does not dismiss the overlay") {
    val (root, overlay) = fixture()
    overlay.show(card, origin())
    click(root.querySelector(".card-overlay-body"))
    assert(overlay.isOpen)
    overlay.dispose(); root.remove()
  }

  test("focus returns to the card that opened the overlay") {
    val (root, overlay) = fixture()
    val opener = origin()
    overlay.show(card, opener)
    assertEquals(dom.document.activeElement,
      root.querySelector(".card-overlay-close"))
    overlay.hide()
    assertEquals(dom.document.activeElement, opener)
    overlay.dispose(); root.remove(); opener.remove()
  }

  test("focus return survives the opener being rebuilt out from under it") {
    val (root, overlay) = fixture()
    val opener = origin()
    overlay.show(card, opener)
    opener.remove()
    overlay.hide()
    assert(!overlay.isOpen)
    overlay.dispose(); root.remove()
  }

  test("a projection update while the overlay is open leaves it open and unchanged") {
    val (root, overlay) = fixture()
    overlay.show(card, origin())
    val before = root.querySelector(".card-overlay-body").innerHTML
    // What a poll does to the panes: PanelContent.replace tears down and
    // rebuilds pane DOM. The overlay is not inside a pane.
    val pane = dom.document.createElement("div")
    root.appendChild(pane)
    PanelContent.replace(pane, dom.document.createElement("p").asInstanceOf[dom.Element],
      dom.document.createElement("h2").asInstanceOf[dom.html.Element])
    assert(overlay.isOpen)
    assertEquals(root.querySelector(".card-overlay-body").innerHTML, before)
    overlay.dispose(); root.remove()
  }

  test("clicking a card face asks the installed handler to open it") {
    var opened = Vector.empty[String]
    CardInspection.onOpen {
      case CardInspection.Request.Card(c, _) => opened = opened :+ c.cardId
      case _ => ()
    }
    val face = CardFace.render(card)
    dom.document.body.appendChild(face)
    click(face)
    assertEquals(opened, Vector("d1"))
    CardInspection.clear()
    click(face)
    assertEquals(opened, Vector("d1"))
    face.remove()
  }

  test("a face-down card is clickable too") {
    var opened = Vector.empty[String]
    CardInspection.onOpen {
      case CardInspection.Request.Card(c, _) => opened = opened :+ c.cardKind
      case _ => ()
    }
    val face = CardFace.render(hidden)
    dom.document.body.appendChild(face)
    click(face)
    assertEquals(opened, Vector("relic"))
    CardInspection.clear()
    face.remove()
  }

  test("text mode renders a title and its lines and draws no card") {
    val (root, overlay) = fixture()
    val opener = origin()
    overlay.showText("Oathkeeper of Supremacy", Vector(
      "Rules the most sites",
      "Successor to the Chancellor: Holds more relics"), opener)
    assert(overlay.isOpen)
    assertEquals(one(root, ".card-overlay-name").map(_.textContent),
      Some("Oathkeeper of Supremacy"))
    assertEquals(all(root, ".rules-power").map(_.textContent), Vector(
      "Rules the most sites",
      "Successor to the Chancellor: Holds more relics"))
    assertEquals(all(root, ".card-face"), Vector.empty)
    assertEquals(root.querySelector(".card-overlay").getAttribute("aria-label"),
      "Oathkeeper of Supremacy")
  }

  test("text mode closes and returns focus like card mode") {
    val (root, overlay) = fixture()
    val opener = origin()
    overlay.showText("Oathkeeper of Devotion",
      Vector("Holds the Darkest Secret"), opener)
    press(root.querySelector(".card-overlay"), "Escape")
    assert(!overlay.isOpen)
    assertEquals(dom.document.activeElement, opener)
  }
}
