package oathdigital.application

import oathdigital.model.Suit

class SitePowerTextSuite extends munit.FunSuite:
  test("each Homeland is named for its suit and shares the Homeland text"):
    val texts = Suit.all.map { suit =>
      val printed = SitePowerText.of(s"homeland-${suit.key}").get
      assertEquals(printed.label, s"${suit.key.capitalize} Homeland")
      printed.text
    }
    assertEquals(texts.distinct.size, 1)
    assert(texts.head.startsWith("There is a Homeland of each suit."), texts.head)
