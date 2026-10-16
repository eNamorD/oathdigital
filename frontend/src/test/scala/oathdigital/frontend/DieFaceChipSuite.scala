package oathdigital.frontend

import Dom.all
import oathdigital.protocol.projection.{Die, DieFace}
import org.scalajs.dom

/** The seven faces the engine projects for both dice, drawn as the symbols
  * printed on them. One renderer, so a face looks the same wherever it is
  * rolled.
  */
class DieFaceChipSuite extends munit.FunSuite:
  private def glyphs(node: dom.Element): Vector[String] =
    all(node, ".token-glyph").map(_.getAttribute("aria-label"))

  test("a face is drawn as the symbols printed on it"):
    assertEquals(glyphs(DieFaceChip.face("one-sword")), Vector("sword"))
    assertEquals(glyphs(DieFaceChip.face("two-swords-skull")),
      Vector("sword", "sword", "skull"))
    assertEquals(glyphs(DieFaceChip.face("hollow-sword")), Vector("hollow sword"))
    assertEquals(glyphs(DieFaceChip.face("one-shield")), Vector("shield"))
    assertEquals(glyphs(DieFaceChip.face("two-shields")), Vector("shield", "shield"))

  test("a face rolled on a known die carries that die; any other stays neutral"):
    assertEquals(DieFaceChip.face("one-sword", Some(Die.Attack))
      .getAttribute("class"),
      "die-face die-face-one-sword die-face-attack")
    assertEquals(DieFaceChip.face("blank", Some(Die.Defense))
      .getAttribute("class"),
      "die-face die-face-blank die-face-defense")
    assertEquals(DieFaceChip.face("one-shield").getAttribute("class"),
      "die-face die-face-one-shield")

  test("a roll gives every face its die"):
    val row = DieFaceChip.roll(Vector(DieFace.OneSword, DieFace.HollowSword))
    assertEquals(all(row, ".die-face-attack").size, 2)

  /** Neither has a symbol in the sprite: a blank face is blank, and the
    * doubler multiplies rather than adding, so it says so.
    */
  test("the blank and the doubler carry no glyph"):
    assertEquals(glyphs(DieFaceChip.face("blank")), Vector.empty)
    assertEquals(DieFaceChip.face("blank").textContent, "")
    assertEquals(glyphs(DieFaceChip.face("doubler")), Vector.empty)
    assertEquals(DieFaceChip.face("doubler").textContent, "×2")

  test("each face's accessible name is its printed words"):
    Vector("one-sword" -> "sword", "hollow-sword" -> "hollow sword",
      "two-swords-skull" -> "two swords and a skull",
      "one-shield" -> "shield", "two-shields" -> "two shields",
      "blank" -> "blank", "doubler" -> "doubler").foreach {
      (face, printedWords) =>
        assertEquals(DieFaceChip.face(face).getAttribute("aria-label"),
          printedWords)
    }

  test("a face is an img"):
    assertEquals(DieFaceChip.face("one-shield").getAttribute("role"), "img")

  /** A face the catalog grows later is shown as itself rather than dropped,
    * the same way an unknown rules token stays literal.
    */
  test("a face this client does not know is printed as its own name"):
    val unknown = DieFaceChip.face("three-moons")
    assertEquals(glyphs(unknown), Vector.empty)
    assertEquals(unknown.textContent, "three-moons")
    assertEquals(unknown.getAttribute("aria-label"), "three-moons")

  test("a roll is a row of faces, and an empty roll draws nothing"):
    val row = DieFaceChip.rollKeys(
      Vector("one-shield", "blank", "doubler"), None)
    assertEquals(all(row, ".die-face").size, 3)
    assertEquals(row.getAttribute("aria-label"), "shield, blank, doubler")
    assertEquals(all(DieFaceChip.rollKeys(Vector.empty, None), ".die-face"),
      Vector.empty)
