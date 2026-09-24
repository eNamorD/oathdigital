package oathdigital.frontend

import org.scalajs.dom

/** The seven faces the engine projects for both dice, drawn as the symbols
  * printed on them. One renderer, so a face looks the same wherever it is
  * rolled.
  */
class DieFaceSuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private def glyphs(node: dom.Element): Vector[String] =
    all(node, ".token-glyph").map(_.getAttribute("aria-label"))

  test("a face is drawn as the symbols printed on it") {
    assertEquals(glyphs(DieFace.face("one-sword")), Vector("sword"))
    assertEquals(glyphs(DieFace.face("two-swords-skull")),
      Vector("sword", "sword", "skull"))
    assertEquals(glyphs(DieFace.face("hollow-sword")), Vector("hollow sword"))
    assertEquals(glyphs(DieFace.face("one-shield")), Vector("shield"))
    assertEquals(glyphs(DieFace.face("two-shields")), Vector("shield", "shield"))
  }

  /** Neither has a symbol in the sprite: a blank face is blank, and the
    * doubler multiplies rather than adding, so it says so.
    */
  test("the blank and the doubler carry no glyph") {
    assertEquals(glyphs(DieFace.face("blank")), Vector.empty)
    assertEquals(DieFace.face("blank").textContent, "")
    assertEquals(glyphs(DieFace.face("doubler")), Vector.empty)
    assertEquals(DieFace.face("doubler").textContent, "×2")
  }

  test("every face keeps the words it used to be printed as") {
    assertEquals(DieFace.face("two-swords-skull").getAttribute("aria-label"),
      "two swords and a skull")
    assertEquals(DieFace.face("blank").getAttribute("aria-label"), "blank")
    assertEquals(DieFace.face("doubler").getAttribute("aria-label"), "doubler")
    assertEquals(DieFace.face("one-shield").getAttribute("role"), "img")
  }

  /** A face the catalog grows later is shown as itself rather than dropped,
    * the same way an unknown rules token stays literal.
    */
  test("a face this client does not know is printed as its own name") {
    val unknown = DieFace.face("three-moons")
    assertEquals(glyphs(unknown), Vector.empty)
    assertEquals(unknown.textContent, "three-moons")
    assertEquals(unknown.getAttribute("aria-label"), "three-moons")
  }

  test("a roll is a row of faces, and an empty roll draws nothing") {
    val row = DieFace.roll(Vector("one-shield", "blank", "doubler"))
    assertEquals(all(row, ".die-face").size, 3)
    assertEquals(row.getAttribute("aria-label"),
      "one shield, blank, doubler")
    assertEquals(all(DieFace.roll(Vector.empty), ".die-face"), Vector.empty)
  }
}
