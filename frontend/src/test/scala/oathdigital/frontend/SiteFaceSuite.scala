package oathdigital.frontend

import org.scalajs.dom

/** The site box, which carries its own numbers in its corners the way a
  * printed Oath site does. Runs under jsdom (`Test / jsEnv` in `build.sbt`).
  */
class SiteFaceSuite extends munit.FunSuite {
  private def all(node: dom.Element, selector: String): Vector[dom.Element] =
    node.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  private def one(node: dom.Element, selector: String): Option[dom.Element] =
    all(node, selector).headOption

  private def glyphs(node: dom.Element, selector: String): Vector[String] =
    all(node, s"$selector .token-glyph").map(_.getAttribute("aria-label"))

  private def denizen(id: String, name: String, suit: String): GameSiteCard =
    GameSiteCard(id, name, Some(CardDetails(id, "denizen", name,
      suit = Some(suit), orientation = Some("face-up"))))

  private val woods = GameSite("site:woods", "Deep Woods",
    looseFavor = 2, looseSecrets = 1, denizenCapacity = 3, relicCapacity = 0,
    denizens = Vector(denizen("denizen:fox", "Fox", "beast"),
      denizen("denizen:owl", "Owl", "arcane")),
    relics = GameSiteRelics(0), defense = 2,
    powers = Vector(SitePower("coast", "Coast", Some("Travel along the Coast route."))))

  private val mine = GameSite("site:mine", "Salt Flats",
    looseFavor = 0, looseSecrets = 0, denizenCapacity = 1, relicCapacity = 2,
    denizens = Vector(denizen("denizen:dune", "Dune", "nomad")),
    relics = GameSiteRelics(2), defense = 0,
    recoverDifficulty = Some(4))

  test("a site's denizens and relics share one row, with no headings") {
    val node = ServerUiSupport.siteDetails(mine)
    val row = one(node, ".site-cards").getOrElse(fail("no site card row"))
    assertEquals(all(row, ".card-face").size, 3)
    assertEquals(all(node, ".site-denizens"), Vector.empty)
    assertEquals(all(node, ".site-relics"), Vector.empty)
    assert(!node.textContent.contains("Denizens"), node.textContent)
    assert(!node.textContent.contains("Relics"), node.textContent)
    assert(!node.textContent.contains("None"), node.textContent)
  }

  test("empty denizen slots fill the row up to capacity, ahead of the relics") {
    val row = one(ServerUiSupport.siteDetails(woods.copy(denizenCapacity = 2,
      relicCapacity = 1, denizens = Vector(denizen("denizen:fox", "Fox", "beast")),
      relics = GameSiteRelics(1))), ".site-cards")
      .getOrElse(fail("no site card row"))
    assertEquals(all(row, ".card-face").map(_.getAttribute("class")), Vector(
      "card-face card-face-denizen",
      "card-face card-face-denizen card-slot-empty",
      "card-face card-face-relic card-face-down"))
  }

  test("loose favor and secrets sit in the site's upper left as glyphs") {
    val heading = ServerUiSupport.siteHeading(woods)
    assertEquals(glyphs(heading, ".site-tokens"), Vector("favor", "secret"))
    assertEquals(all(heading, ".site-tokens .site-token-count")
      .map(_.textContent), Vector("2", "1"))
    assertEquals(one(heading, ".site-name").map(_.textContent), Some("Deep Woods"))
  }

  test("a token the site does not hold is not drawn at all") {
    val heading = ServerUiSupport.siteHeading(
      woods.copy(looseFavor = 0, looseSecrets = 3))
    assertEquals(glyphs(heading, ".site-tokens"), Vector("secret"))
    assertEquals(all(heading, ".site-tokens .site-token-count")
      .map(_.textContent), Vector("3"))
  }

  test("defense is a die in the upper right, drawn once per point") {
    val heading = ServerUiSupport.siteHeading(woods)
    assertEquals(glyphs(heading, ".site-defense"),
      Vector("defense die", "defense die"))
    assertEquals(one(heading, ".site-defense").map(_.getAttribute("aria-label")),
      Some("Defense 2"))
  }

  /** A site anyone can walk into still says so, rather than leaving the
    * reader to decide whether a missing die means zero or means unknown.
    */
  test("an undefended site says zero instead of drawing no die") {
    val heading = ServerUiSupport.siteHeading(mine)
    assertEquals(glyphs(heading, ".site-defense"), Vector.empty)
    assertEquals(one(heading, ".site-defense").map(_.textContent), Some("0"))
    assertEquals(one(heading, ".site-defense").map(_.getAttribute("aria-label")),
      Some("Defense 0"))
  }

  test("site powers sit in the lower left, the requirement in the lower right") {
    val node = ServerUiSupport.siteDetails(woods.copy(recoverDifficulty = Some(5)))
    val footer = one(node, ".site-footer").getOrElse(fail("no site footer"))
    assertEquals(footer.firstChild.asInstanceOf[dom.Element].getAttribute("class"),
      "site-powers")
    assertEquals(all(footer, ".site-power").map(_.textContent),
      Vector("Coast: Travel along the Coast route."))
    assertEquals(footer.lastChild.asInstanceOf[dom.Element].getAttribute("class"),
      "site-requirement")
  }

  test("forge and recover are one corner, never both") {
    val recover = one(ServerUiSupport.siteDetails(mine), ".site-requirement")
      .getOrElse(fail("no requirement"))
    assertEquals(recover.textContent, "Recover 4")
    assertEquals(glyphs(recover, ""), Vector.empty)

    val forged = one(ServerUiSupport.siteDetails(mine.copy(
      forgeCost = Some(ForgeCost(2, 1)))), ".site-requirement")
      .getOrElse(fail("no requirement"))
    assert(forged.textContent.startsWith("Forge"), forged.textContent)
    assert(!forged.textContent.contains("Recover"), forged.textContent)
    assertEquals(glyphs(forged, ""), Vector("favor", "favor", "secret"))
  }

  test("a site with neither requirement leaves the corner out") {
    assertEquals(all(ServerUiSupport.siteDetails(woods), ".site-requirement"),
      Vector.empty)
  }
}
