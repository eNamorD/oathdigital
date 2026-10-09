package oathdigital.gameplay.powers

import oathdigital.gameplay.actions.{AdviserFace, VisionRules}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.Orientation
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class AdviserLimitSuite extends munit.FunSuite:
  private val tongue = CatalogNames.denizen("Silver Tongue")

  // The default limit is three advisers; a faceup Silver Tongue lowers its
  // holder's to two.
  test("a faceup Silver Tongue lowers its holder's limit to two, and no one " +
      "else's"):
    val ready = Table.start.adviser(p1, tongue).ready
    assertEquals(AdviserLimit.of(catalog, ready, p1), 2)
    Vector(p2, p3).foreach(other =>
      assertEquals(AdviserLimit.of(catalog, ready, other), 3))

  test("a facedown Silver Tongue changes nothing"):
    val ready = Table.start.adviser(p1, tongue, facedown = true).ready
    assertEquals(AdviserLimit.of(catalog, ready, p1), 3)

  test("a Lost Tapestry holder's facedown Visions do not count toward the " +
      "limit, and only while it is faceup"):
    val faith = VisionRules.Faith
    def held(facedownTapestry: Boolean) = Table.start
      .relic(p1, "Lost Tapestry", facedown = facedownTapestry)
      .adviser(p1, faith, facedown = true).adviser(p1, "Wrestlers").ready
    assertEquals(AdviserLimit.ignored(held(false), p1),
      Vector(AdviserFace(faith, Orientation.FaceDown)))
    assertEquals(AdviserLimit.towardLimit(held(false), p1), 1)
    assertEquals(AdviserLimit.towardLimit(held(true), p1), 2)
