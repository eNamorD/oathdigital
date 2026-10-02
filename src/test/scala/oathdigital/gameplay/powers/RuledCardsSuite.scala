package oathdigital.gameplay.powers

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** The cards a ruler rules (catalog batch 3 rulings, "Cards a player
  * rules"). */
class RuledCardsSuite extends munit.FunSuite:
  // Two Nomad denizens, an Order one, and a Nomad edifice (Great Forge, on
  // its ruined face).
  private val convoys = CatalogNames.denizen("Convoys")
  private val vowOfKinship = CatalogNames.denizen("Vow of Kinship")
  private val order = CatalogNames.denizen("Martial Culture")
  private val nomadEdifice = CatalogNames.edifice("Great Forge")

  private def nomadsOf(b: Board, ruler: SiteRuler): Set[CardId] =
    RuledCards.of(catalog, b.ready, ruler, Suit.Nomad).toSet

  test("a player rules their faceup advisers, not their facedown ones"):
    val base = board()
    val b = on(base)(_.adviser(base.actor, convoys)
      .adviser(base.actor, vowOfKinship, facedown = true))
    assertEquals(nomadsOf(b, SiteRuler.Player(b.actor)), Set[CardId](convoys))

  test("a player rules the faceup denizens and the edifices, on either " +
      "face, at the sites they rule"):
    val base = board()
    val site = CatalogNames.site("Broken Peaks")
    // A facedown denizen has no suit, and an order card is not a nomad card.
    val b = on(actorRules(base, site))(_
      .denizen(convoys, at = site)
        .denizen(vowOfKinship, at = site, facedown = true)
        .denizen(order, at = site)
      .edifice(nomadEdifice, EdificeSide.Ruined, at = site))
    assertEquals(nomadsOf(b, SiteRuler.Player(b.actor)),
      Set[CardId](convoys, nomadEdifice))

  test("bandits rule the cards at every site they rule, and nobody else " +
      "does"):
    // Two Bandits rule Ancient City, p1's site, and Broken Peaks; p1 rules
    // nothing.
    val base = board(extras = 1)
    val b = on(base)(_.denizen(convoys, at = "Ancient City")
      .denizen(vowOfKinship, at = "Broken Peaks"))
    assertEquals(nomadsOf(b, SiteRuler.Bandits),
      Set[CardId](convoys, vowOfKinship))
    assertEquals(nomadsOf(b, SiteRuler.Player(b.actor)), Set.empty[CardId])
