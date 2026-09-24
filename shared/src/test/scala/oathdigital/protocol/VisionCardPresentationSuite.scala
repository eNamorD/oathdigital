package oathdigital.protocol

import oathdigital.protocol.projection.VisionCardPresentation

/** The five Visions as they are printed. The text is card text: it is asserted
  * verbatim here so a reword has to be a deliberate edit to this suite.
  */
class VisionCardPresentationSuite extends munit.FunSuite {
  private val gate =
    "and at least three visions have been drawn from the world deck."

  test("each Vision carries its printed name and text") {
    assertEquals(VisionCardPresentation.byId("vision:vision-of-conquest"),
      VisionCardPresentation("Vision of Conquest",
        s"Wake: You win if you hold the **most sites** $gate"))
    assertEquals(VisionCardPresentation.byId("vision:vision-of-sanctuary"),
      VisionCardPresentation("Vision of Sanctuary",
        s"Wake: You win if you hold the **most relics** $gate"))
    assertEquals(VisionCardPresentation.byId("vision:vision-of-rebellion"),
      VisionCardPresentation("Vision of Rebellion",
        s"Wake: You win if you hold the **People's Favor** $gate"))
    assertEquals(VisionCardPresentation.byId("vision:vision-of-faith"),
      VisionCardPresentation("Vision of Faith",
        s"Wake: You win if you hold the **Darkest Secret** $gate"))
  }

  test("Conspiracy is two paragraphs and names both banner resources") {
    val card = VisionCardPresentation.byId("vision:conspiracy")
    assertEquals(card.name, "Conspiracy")
    val paragraphs = card.rulesText.split("\n\n").toVector
    assertEquals(paragraphs.size, 2)
    assertEquals(paragraphs.head,
      "If this card is discarded in a raid campaign, return it to the box.")
    assert(paragraphs(1).startsWith("WHEN PLAYED: Take a relic or banner"))
    assert(paragraphs(1).contains("[favor]/[secret]"))
    assert(paragraphs(1).endsWith("Return the Conspiracy to the box."))
  }

  test("no Vision claims a unique leader -- the card says the most") {
    assert(!VisionCardPresentation.byId.values.exists(
      _.rulesText.contains("uniquely")))
  }
}
