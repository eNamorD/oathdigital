package oathdigital.gameplay.powers

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.powers.campaign.PlanDriver.inert
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

/** The cards a ruler rules (catalog batch 3 rulings, "Cards a player
  * rules"). */
class RuledCardsSuite extends munit.FunSuite:
  private val nomads = inert(Suit.Nomad, 2).map(DenizenId(_))
  private val order = DenizenId(inert(Suit.Order, 1).head)
  private val nomadEdifice =
    EdificeId(catalog.edifices.find(_.suit == Suit.Nomad).get.id.value)

  private def nomadsOf(b: Board, ruler: SiteRuler): Set[CardId] =
    RuledCards.of(catalog, b.ready, ruler, Suit.Nomad).toSet

  test("a player rules their faceup advisers, not their facedown ones"):
    val base = board()
    val b = withAdviserFor(withAdviser(base, nomads(0).value,
      Orientation.FaceUp), base.actor, nomads(1).value, Orientation.FaceDown)
    assertEquals(nomadsOf(b, SiteRuler.Player(b.actor)), Set[CardId](nomads(0)))

  test("a player rules the faceup denizens and the edifices, on either " +
      "face, at the sites they rule"):
    val base = board()
    val site = base.ready.game.current.map.inPlay.find(_ != base.origin).get
    // A facedown denizen has no suit, and an order card is not a nomad card.
    val b = on(actorRules(base, site))(_
      .denizen(nomads(0), at = site)
      .denizen(nomads(1), at = site, facedown = true)
      .denizen(order, at = site)
      .edifice(nomadEdifice, EdificeSide.Ruined, at = site))
    assertEquals(nomadsOf(b, SiteRuler.Player(b.actor)),
      Set[CardId](nomads(0), nomadEdifice))

  test("bandits rule the cards at every site they rule, and nobody else " +
      "does"):
    val base = board(extras = 1)
    val b = withSiteCard(withSiteCard(base, base.origin, nomads(0).value),
      base.extras.head, nomads(1).value)
    assertEquals(nomadsOf(b, SiteRuler.Bandits),
      Set[CardId](nomads(0), nomads(1)))
    assertEquals(nomadsOf(b, SiteRuler.Player(b.actor)), Set.empty[CardId])
