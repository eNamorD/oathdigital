package oathdigital.gameplay

import oathdigital.gameplay.operations.PayCostSettlement
import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.RuledCards
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** `EffectiveSuit`: a card's suit as the rules see it. A faceup Marriage
  * counts as two advisers of its suit (catalog batch 5 rulings, "Counts as
  * applies everywhere"), and Pledge to Discord makes its holder's faceup
  * advisers Discord (catalog batch 8 rulings, slice 8). */
class EffectiveSuitSuite extends munit.FunSuite:
  private val marriage = CatalogNames.denizen("Marriage")
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val pledge = CatalogNames.denizen("Pledge to Discord")
  private val mirror = CatalogNames.relic("Spiteful Mirror")

  test("a faceup Marriage counts as two Hearth advisers, and facedown as " +
      "none"):
    val faceup = Table.start.adviser(p1, "Marriage")
      .adviser(p1, "Homesteaders").ready
    assertEquals(EffectiveSuit.count(catalog, faceup, p1, Suit.Hearth), 3)
    assertEquals(EffectiveSuit.count(catalog, faceup, p1, Suit.Beast), 0)
    val facedown = Table.start.adviser(p1, "Marriage", facedown = true)
      .adviser(p1, "Homesteaders").ready
    assertEquals(EffectiveSuit.count(catalog, facedown, p1, Suit.Hearth), 1)

  test("a ruler's count weighs Marriage, while its list names it once"):
    val ready = Table.start.adviser(p1, "Marriage").ready
    val ruler = SiteRuler.Player(p1)
    val listed = RuledCards.of(catalog, ready, ruler, Suit.Hearth)
    assertEquals(listed.count(_ == marriage), 1)
    assertEquals(RuledCards.count(catalog, ready, ruler, Suit.Hearth),
      listed.size + 1)

  test("with Pledge to Discord faceup, its holder's faceup advisers are " +
      "Discord, Marriage as two, while other cards keep their suit"):
    val ready = Table.start.adviser(p1, pledge).adviser(p1, marriage)
      .adviser(p1, wrestlers).adviser(p2, homesteaders)
      .denizen("Alchemist", at = "Ancient City").ready
    // Pledge 1, Marriage 2, Wrestlers 1.
    assertEquals(EffectiveSuit.count(catalog, ready, p1, Suit.Discord), 4)
    assertEquals(EffectiveSuit.count(catalog, ready, p1, Suit.Hearth), 0)
    assertEquals(EffectiveSuit.of(catalog, ready, wrestlers),
      Some(Suit.Discord))
    assertEquals(EffectiveSuit.of(catalog, ready, homesteaders),
      Some(Suit.Hearth))
    assertEquals(EffectiveSuit.of(catalog, ready,
      CatalogNames.denizen("Alchemist")), Some(Suit.Arcane))

  test("a facedown Pledge changes nothing, and a facedown adviser beside " +
      "a faceup Pledge counts for no suit"):
    val facedownPledge = Table.start.adviser(p1, pledge, facedown = true)
      .adviser(p1, wrestlers).ready
    assertEquals(EffectiveSuit.of(catalog, facedownPledge, wrestlers),
      Some(Suit.Order))
    val facedownAdviser = Table.start.adviser(p1, pledge)
      .adviser(p1, wrestlers, facedown = true).ready
    assertEquals(EffectiveSuit.count(catalog, facedownAdviser, p1,
      Suit.Discord), 1)

  test("a Pledge holder rules its advisers as Discord"):
    val ready = Table.start.adviser(p1, pledge).adviser(p1, wrestlers).ready
    val ruler = SiteRuler.Player(p1)
    assertEquals(RuledCards.of(catalog, ready, ruler, Suit.Discord),
      Vector[CardId](pledge, wrestlers))
    assertEquals(RuledCards.of(catalog, ready, ruler, Suit.Order),
      Vector.empty[CardId])

  test("the traded suit is Discord once Spiteful Mirror is picked"):
    val ready = Table.start.denizen(wrestlers, at = "Ancient City").ready
    val source = DecisionOptionRef.Denizen(wrestlers)
    val picked = Vector(Answered(
      ModifierPicks.decisionId(PowerWindow.TradeModifierSelection),
      ChooseOneAnswer(DecisionOptionRef.Relic(mirror)), p1))
    assertEquals(EffectiveSuit.traded(catalog, ready, Vector.empty, source),
      Some(Suit.Order))
    assertEquals(EffectiveSuit.traded(catalog, ready, picked, source),
      Some(Suit.Discord))

  test("an off-turn payment onto a Pledge holder's adviser settles to the " +
      "Discord bank"):
    // p1 is the active player on the starting table; p2 pays off turn.
    val ready = Table.start.adviser(p2, pledge).adviser(p2, wrestlers).ready
    val pay = PayCost(p2, Location.OnCard(wrestlers), Cost(favor = 1),
      intoOccupied = true, matchingBank = Some(Suit.Order))
    assertEquals(PayCostSettlement.prepare(ready, pay),
      Right(pay.copy(matchingBank = Some(Suit.Discord), offTurn = true)))

  test("Rest returns the favor on a Pledge holder's adviser to the Discord " +
      "bank"):
    val ready = Table.start.adviser(p1, pledge).adviser(p1, wrestlers)
      .tokens(wrestlers, favor = 2).ready
    assertEquals(RestCleanupPlan.derive(catalog, ready, p1)
      .map(_.returnedFavor), Right(Map(Suit.Discord -> 2)))
