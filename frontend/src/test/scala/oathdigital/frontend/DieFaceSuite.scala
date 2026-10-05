package oathdigital.frontend

import Dom.all
import org.scalajs.dom

/** The seven faces the engine projects for both dice, drawn as the symbols
  * printed on them. One renderer, so a face looks the same wherever it is
  * rolled.
  */
class DieFaceSuite extends munit.FunSuite:
  private def glyphs(node: dom.Element): Vector[String] =
    all(node, ".token-glyph").map(_.getAttribute("aria-label"))

  test("a face is drawn as the symbols printed on it"):
    assertEquals(glyphs(DieFace.face("one-sword")), Vector("sword"))
    assertEquals(glyphs(DieFace.face("two-swords-skull")),
      Vector("sword", "sword", "skull"))
    assertEquals(glyphs(DieFace.face("hollow-sword")), Vector("hollow sword"))
    assertEquals(glyphs(DieFace.face("one-shield")), Vector("shield"))
    assertEquals(glyphs(DieFace.face("two-shields")), Vector("shield", "shield"))

  test("a face rolled on a known die carries that die; any other stays neutral"):
    assertEquals(DieFace.face("one-sword", Some("attack")).getAttribute("class"),
      "die-face die-face-one-sword die-face-attack")
    assertEquals(DieFace.face("blank", Some("defense")).getAttribute("class"),
      "die-face die-face-blank die-face-defense")
    assertEquals(DieFace.face("one-shield").getAttribute("class"),
      "die-face die-face-one-shield")
    // A die this client does not know is drawn neutral, not guessed at.
    assertEquals(DieFace.face("one-shield", Some("round")).getAttribute("class"),
      "die-face die-face-one-shield")

  test("a roll gives every face its die"):
    val row = DieFace.roll(Vector("one-sword", "blank"), Some("attack"))
    assertEquals(all(row, ".die-face-attack").size, 2)

  /** Neither has a symbol in the sprite: a blank face is blank, and the
    * doubler multiplies rather than adding, so it says so.
    */
  test("the blank and the doubler carry no glyph"):
    assertEquals(glyphs(DieFace.face("blank")), Vector.empty)
    assertEquals(DieFace.face("blank").textContent, "")
    assertEquals(glyphs(DieFace.face("doubler")), Vector.empty)
    assertEquals(DieFace.face("doubler").textContent, "×2")

  test("each face's accessible name is its printed words"):
    Vector("one-sword" -> "one sword", "hollow-sword" -> "hollow sword",
      "two-swords-skull" -> "two swords and a skull",
      "one-shield" -> "one shield", "two-shields" -> "two shields",
      "blank" -> "blank", "doubler" -> "doubler").foreach {
      (face, printedWords) =>
        assertEquals(DieFace.face(face).getAttribute("aria-label"),
          printedWords)
    }

  test("a face is an img"):
    assertEquals(DieFace.face("one-shield").getAttribute("role"), "img")

  /** A face the catalog grows later is shown as itself rather than dropped,
    * the same way an unknown rules token stays literal.
    */
  test("a face this client does not know is printed as its own name"):
    val unknown = DieFace.face("three-moons")
    assertEquals(glyphs(unknown), Vector.empty)
    assertEquals(unknown.textContent, "three-moons")
    assertEquals(unknown.getAttribute("aria-label"), "three-moons")

  test("a roll is a row of faces, and an empty roll draws nothing"):
    val row = DieFace.roll(Vector("one-shield", "blank", "doubler"))
    assertEquals(all(row, ".die-face").size, 3)
    assertEquals(row.getAttribute("aria-label"),
      "one shield, blank, doubler")
    assertEquals(all(DieFace.roll(Vector.empty), ".die-face"), Vector.empty)
