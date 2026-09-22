package oathdigital.frontend

import org.scalajs.dom

/** `CardFace` is the one place a projected `CardDetails` becomes DOM, so
  * every rule about card shape, redaction and knowability is pinned here.
  * Runs under jsdom (`Test / jsEnv` in `build.sbt`).
  */
class CardFaceSuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private def one(node: dom.Element, selector: String): Option[dom.Element] =
    all(node, selector).headOption

  private val faceUp = CardDetails("d1", "denizen", "Old Oak",
    suit = Some("order"), restrictions = Some("site-only"),
    rulesText = Some("**ACTION:** Gain [favor]."),
    orientation = Some("face-up"), favor = 2, secrets = 1)

  private val hidden = CardDetails("hidden", "vision", "Facedown vision",
    orientation = Some("face-down"), hidden = true)

  private val knowable = CardDetails("r7", "relic", "Ancient Crown",
    rulesText = Some("Rule"), orientation = Some("face-down"),
    relicValue = Some(3), defense = Some(1))

  test("a denizen and a relic each get their type's box, whichever way they face") {
    assert(CardFace.render(faceUp).classList.contains("card-face-denizen"))
    assert(CardFace.render(faceUp.copy(orientation = Some("face-down"),
      hidden = true)).classList.contains("card-face-denizen"))
    assert(CardFace.render(knowable).classList.contains("card-face-relic"))
    assert(CardFace.render(knowable.copy(orientation = Some("face-up")))
      .classList.contains("card-face-relic"))
    assertEquals(CardFace.boxClass("edifice"), "card-face-denizen")
    assertEquals(CardFace.boxClass("vision"), "card-face-denizen")
  }

  test("a face-up card shows its summary with no field names") {
    val node = CardFace.render(faceUp)
    assertEquals(one(node, ".card-name").map(_.textContent), Some("Old Oak"))
    assertEquals(one(node, ".card-suit").map(_.textContent), Some("order"))
    assertEquals(one(node, ".card-suit").map(_.getAttribute("class")),
      Some("card-suit suit-order"))
    assertEquals(one(node, ".card-restriction").map(_.textContent),
      Some("site-only"))
    assertEquals(all(node, ".card-tokens .token-glyph")
      .map(_.getAttribute("aria-label")), Vector("favor", "secret"))
    assertEquals(all(node, ".card-token-count").map(_.textContent),
      Vector("2", "1"))
    assert(!node.textContent.contains("Suit:"), node.textContent)
    assert(!node.textContent.contains("Favor:"), node.textContent)
  }

  test("rules text never reaches the face") {
    assert(!CardFace.render(faceUp).textContent.contains("ACTION"))
    assert(!CardFace.render(knowable).textContent.contains("Rule"))
  }

  test("a relic face carries its value and defense, a denizen face does not") {
    val relic = CardFace.render(knowable.copy(orientation = Some("face-up")))
    assertEquals(all(relic, ".card-stat").map(_.textContent), Vector("3", "1"))
    assertEquals(all(CardFace.render(faceUp), ".card-stat"), Vector.empty)
  }

  test("an unidentifiable card exposes no name, suit or rules and shows its letter") {
    val node = CardFace.render(hidden)
    assert(node.classList.contains("card-face-down"))
    assert(!node.classList.contains("card-face-knowable"))
    assertEquals(one(node, ".card-back-letter").map(_.textContent), Some("V"))
    assertEquals(node.getAttribute("aria-label"), "Facedown vision")
    assertEquals(all(node, ".card-name"), Vector.empty)
    assertEquals(all(node, ".card-suit"), Vector.empty)
    assertEquals(node.textContent, "V")
  }

  test("each hidden kind gets its own letter") {
    assertEquals(CardFace.backLetter("denizen"), "D")
    assertEquals(CardFace.backLetter("edifice"), "D")
    assertEquals(CardFace.backLetter("vision"), "V")
    assertEquals(CardFace.backLetter("relic"), "R")
  }

  /** A denizen back and a vision back differ physically, so which one a
    * face-down adviser is remains public even when its identity is not.
    * `GamePresentationProjector` projects the real kind (commit `7ae54c3`);
    * the face must therefore never collapse the two into one letter.
    */
  test("an unidentifiable adviser still says whether it is a denizen or a vision") {
    val denizenBack = CardFace.render(CardDetails("hidden", "denizen",
      "Facedown denizen", orientation = Some("face-down"), hidden = true))
    val visionBack = CardFace.render(CardDetails("hidden", "vision",
      "Facedown vision", orientation = Some("face-down"), hidden = true))
    assertEquals(one(denizenBack, ".card-back-letter").map(_.textContent), Some("D"))
    assertEquals(one(visionBack, ".card-back-letter").map(_.textContent), Some("V"))
  }

  test("a knowable back carries the pip and a hover-only summary") {
    val node = CardFace.render(knowable)
    assert(node.classList.contains("card-face-down"))
    assert(node.classList.contains("card-face-knowable"))
    assertEquals(one(node, ".card-back-letter").map(_.textContent), Some("R"))
    assertEquals(all(node, ".card-knowable-pip").size, 1)
    assertEquals(one(node, ".card-hover-summary .card-name").map(_.textContent),
      Some("Ancient Crown"))
  }

  test("knowability is read per render, so a later projection can add the pip") {
    val before = CardFace.render(CardDetails("hidden", "relic",
      "Facedown relic", orientation = Some("face-down"), hidden = true))
    val after = CardFace.render(knowable)
    assert(!before.classList.contains("card-face-knowable"))
    assert(after.classList.contains("card-face-knowable"))
  }

  test("the unimplemented marker survives the redesign") {
    val node = CardFace.render(faceUp.copy(implemented = false))
    assert(node.classList.contains("card-face-unimplemented"))
    assertEquals(node.getAttribute("aria-label"), "Old Oak (unimplemented)")
    assertEquals(all(node, ".card-unimplemented-badge").size, 1)
  }

  test("the card id rides the element for focus restoration") {
    assertEquals(CardFace.render(faceUp).getAttribute("data-card-id"), "d1")
  }
}
