package oathdigital.frontend

import Dom.{all, click, firstMatch, press}
import org.scalajs.dom

/** The overlay is the only place a card's full rules appear after the hover
  * popover was retired. It renders at document level, above `.game-pane`,
  * which is what lets that pane keep `overflow: hidden`.
  * Runs under jsdom (`Test / jsEnv` in `build.sbt`).
  */
class CardInspectionOverlaySuite extends munit.FunSuite:
  private val card = CardDetails("d1", "denizen", "Old Oak",
    suit = Some("order"), restrictions = Some("site-only"),
    rulesText = Some("**ACTION:** Gain [favor].\n\n[secret] Persistent."),
    orientation = Some("face-up"), relicValue = None, favor = 1)

  private val hidden = CardDetails("hidden", "relic", "Facedown relic",
    orientation = Some("face-down"), hidden = true)

  /** A div on the body and the overlay built on it. */
  private final case class MountedOverlay(root: dom.Element,
      overlay: CardInspectionOverlay)

  private var mounted = Vector.empty[MountedOverlay]

  /** Mounts an overlay on a fresh div; `afterEach` disposes it. */
  private def mountedOverlay(): MountedOverlay =
    val root = dom.document.createElement("div")
    dom.document.body.appendChild(root)
    val mount = MountedOverlay(root, new CardInspectionOverlay(root))
    mounted :+= mount
    mount

  override def afterEach(context: AfterEach): Unit =
    mounted.foreach { mount =>
      mount.overlay.dispose()
      mount.root.remove()
    }
    mounted = Vector.empty

  /** A focusable button on the body, as the card or control that opens the
    * overlay.
    */
  private def focusableOpener(): dom.html.Element =
    val node = dom.document.createElement("button").asInstanceOf[dom.html.Element]
    node.setAttribute("tabindex", "0")
    dom.document.body.appendChild(node)
    node

  test("the overlay is hidden until a card opens it, and shows that card in full"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    assert(root.querySelector(".card-overlay").hasAttribute("hidden"))
    overlay.show(card, focusableOpener())
    assert(overlay.isOpen)
    val node = root.querySelector(".card-overlay")
    assert(!node.hasAttribute("hidden"))
    assertEquals(node.getAttribute("role"), "dialog")
    assertEquals(firstMatch(node, ".card-name").map(_.textContent),
      Some("Old Oak"))
    // Field names come back in the overlay, unlike on the face.
    assert(node.textContent.contains("Suit"), node.textContent)
    assert(node.textContent.contains("Restrictions"), node.textContent)

  /** Inspection is for reading a card, and a card you are allowed to read has
    * no reason to show you its back.
    */
  test("a card the viewer knows is inspected face-up, whichever way it lies"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    overlay.show(card.copy(orientation = Some("face-down")), focusableOpener())
    val node = root.querySelector(".card-overlay")
    assertEquals(all(node, ".card-back-letter"), Vector.empty)
    assertEquals(firstMatch(node, ".card-name").map(_.textContent),
      Some("Old Oak"))
    assert(!node.querySelector(".card-face").classList.contains("card-face-down"))

  test("the details lead with the card name, above the suit"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    overlay.show(card, focusableOpener())
    val details = root.querySelector(".card-overlay-details")
    assertEquals(details.firstChild.asInstanceOf[dom.Element].getAttribute("class"),
      "card-overlay-name")
    assertEquals(details.firstChild.textContent, "Old Oak")
    assertEquals(firstMatch(details, ".card-overlay-property dt")
      .map(_.textContent), Some("Suit"))

  test("an unrestricted card gets no Restrictions row"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    overlay.show(card.copy(restrictions = Some("unrestricted")),
      focusableOpener())
    val node = root.querySelector(".card-overlay")
    assert(!node.textContent.contains("Restrictions"), node.textContent)
    assert(node.textContent.contains("Suit"), node.textContent)

  test("multi-power rules render one block per power with glyphs"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    // Two paragraphs, so two blocks; [favor] and [secret] are the glyphs.
    val twoPowers = card.copy(rulesText = Some(
      "**ACTION:** Gain [favor].\n\n[secret] Persistent."))
    overlay.show(twoPowers, focusableOpener())
    val node = root.querySelector(".card-overlay")
    assertEquals(all(node, ".rules-power").size, 2)
    assertEquals(all(node, ".card-overlay-rules .token-glyph")
      .map(_.getAttribute("aria-label")), Vector("favor", "secret"))
    assertEquals(all(node, ".card-overlay-rules strong").map(_.textContent),
      Vector("ACTION:"))

  test("a card the viewer cannot identify renders as face-down, not as a card"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    overlay.show(hidden, focusableOpener())
    val node = root.querySelector(".card-overlay")
    assertEquals(all(node, ".card-overlay-rules"), Vector.empty)
    assertEquals(firstMatch(node, ".card-back-letter").map(_.textContent),
      Some("R"))
    assert(!node.textContent.contains("Suit"), node.textContent)

  test("Escape, the Close button and a click on the overlay each dismiss it"):
    Vector[(dom.Element, CardInspectionOverlay) => Unit](
      (root, _) => press(root.querySelector(".card-overlay"), "Escape"),
      (root, _) => click(root.querySelector(".card-overlay-close")),
      (root, _) => click(root.querySelector(".card-overlay"))
    ).foreach { dismiss =>
      val MountedOverlay(root, overlay) = mountedOverlay()
      overlay.show(card, focusableOpener())
      assert(overlay.isOpen)
      dismiss(root, overlay)
      assert(!overlay.isOpen)
      assert(root.querySelector(".card-overlay").hasAttribute("hidden"))
    }

  test("a click on the card itself does not dismiss the overlay"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    overlay.show(card, focusableOpener())
    click(root.querySelector(".card-overlay-body"))
    assert(overlay.isOpen)

  test("focus returns to the card that opened the overlay"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    val opener = focusableOpener()
    overlay.show(card, opener)
    assertEquals(dom.document.activeElement,
      root.querySelector(".card-overlay-close"))
    overlay.hide()
    assertEquals(dom.document.activeElement, opener)
    opener.remove()

  /** A pane as `GameTableShell` builds one: a kept heading and a content
    * box that a poll rebuilds.
    */
  private final case class GamePane(section: dom.Element,
      heading: dom.html.Element, content: dom.Element)

  /** Mounts a pane on the body; the test removes its section. */
  private def pane(): GamePane =
    val section = dom.document.createElement("section")
    section.setAttribute("class", "game-pane")
    val heading = dom.document.createElement("h2").asInstanceOf[dom.html.Element]
    heading.setAttribute("class", "pane-heading")
    heading.tabIndex = -1
    val content = dom.document.createElement("div")
    section.appendChild(heading)
    section.appendChild(content)
    dom.document.body.appendChild(section)
    GamePane(section, heading, content)

  private def faces(cards: CardDetails*): dom.Element =
    val row = dom.document.createElement("div")
    cards.foreach(c => row.appendChild(CardFace.render(c)))
    row

  test("focus return survives the opener being rebuilt out from under it"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    val GamePane(section, heading, content) = pane()
    // Two backs that look alike: focus must go to the second one's twin.
    content.appendChild(faces(hidden, hidden))
    val opener = all(content, ".card-face")(1).asInstanceOf[dom.html.Element]
    overlay.show(hidden, opener)
    PanelContent.replace(content, faces(hidden, hidden), heading)
    overlay.hide()
    assert(!dom.document.contains(opener))
    assertEquals(dom.document.activeElement, all(content, ".card-face")(1))
    section.remove()

  test("focus falls back to the pane heading when the rebuild dropped the opener"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    val GamePane(section, heading, content) = pane()
    content.appendChild(faces(card))
    overlay.show(card, content.querySelector(".card-face")
      .asInstanceOf[dom.html.Element])
    PanelContent.replace(content, faces(hidden), heading)
    overlay.hide()
    assertEquals(dom.document.activeElement, heading)
    section.remove()

  test("an opener gone from outside any pane leaves focus off the hidden close button"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    val opener = focusableOpener()
    overlay.show(card, opener)
    opener.remove()
    overlay.hide()
    assertNotEquals(dom.document.activeElement,
      root.querySelector(".card-overlay-close"))
    assertEquals(dom.document.activeElement, dom.document.body)

  test("a projection update while the overlay is open leaves it open and unchanged"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    overlay.show(card, focusableOpener())
    val before = root.querySelector(".card-overlay-body").innerHTML
    // What a poll does to the panes: PanelContent.replace tears down and
    // rebuilds pane DOM. The overlay is not inside a pane.
    val pane = dom.document.createElement("div")
    root.appendChild(pane)
    PanelContent.replace(pane, dom.document.createElement("p").asInstanceOf[dom.Element],
      dom.document.createElement("h2").asInstanceOf[dom.html.Element])
    assert(overlay.isOpen)
    assertEquals(root.querySelector(".card-overlay-body").innerHTML, before)

  test("clicking a card face asks the installed handler to open it"):
    var opened = Vector.empty[String]
    CardInspection.onOpen:
      case CardInspection.Request.Card(c, _) => opened = opened :+ c.cardId
      case _ => ()
    val face = CardFace.render(card)
    dom.document.body.appendChild(face)
    click(face)
    assertEquals(opened, Vector("d1"))
    CardInspection.clear()
    click(face)
    assertEquals(opened, Vector("d1"))
    face.remove()

  test("a face-down card is clickable too"):
    var opened = Vector.empty[String]
    CardInspection.onOpen:
      case CardInspection.Request.Card(c, _) => opened = opened :+ c.cardKind
      case _ => ()
    val face = CardFace.render(hidden)
    dom.document.body.appendChild(face)
    click(face)
    assertEquals(opened, Vector("relic"))
    CardInspection.clear()
    face.remove()

  test("text mode renders a title and its lines and draws no card"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    val opener = focusableOpener()
    overlay.showText("Oathkeeper of Supremacy", Vector(
      "Rules the most sites",
      "Successor to the Chancellor: Holds more relics"), opener)
    assert(overlay.isOpen)
    assertEquals(firstMatch(root, ".card-overlay-name").map(_.textContent),
      Some("Oathkeeper of Supremacy"))
    assertEquals(all(root, ".rules-power").map(_.textContent), Vector(
      "Rules the most sites",
      "Successor to the Chancellor: Holds more relics"))
    assertEquals(all(root, ".card-face"), Vector.empty)
    assertEquals(root.querySelector(".card-overlay").getAttribute("aria-label"),
      "Oathkeeper of Supremacy")

  test("text mode closes and returns focus like card mode"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    val opener = focusableOpener()
    overlay.showText("Oathkeeper of Devotion",
      Vector("Holds the Darkest Secret"), opener)
    press(root.querySelector(".card-overlay"), "Escape")
    assert(!overlay.isOpen)
    assertEquals(dom.document.activeElement, opener)

  test("a card list shows every card in order, each its face or its back"):
    val MountedOverlay(root, overlay) = mountedOverlay()
    val opener = focusableOpener()
    overlay.showCards("2 cards", Vector(card, hidden), opener)
    assert(overlay.isOpen)
    val node = root.querySelector(".card-overlay")
    assertEquals(node.getAttribute("aria-label"), "2 cards")
    val listedFaces = all(node, ".card-list-item .card-face")
    assertEquals(listedFaces.map(_.getAttribute("data-card-id")),
      Vector("d1", "hidden"))
    assert(listedFaces(1).classList.contains("card-face-down"))
    overlay.hide()
    assertEquals(dom.document.activeElement, opener)
    opener.remove()
