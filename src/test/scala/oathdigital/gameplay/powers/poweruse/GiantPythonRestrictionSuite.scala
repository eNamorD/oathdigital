package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.gameplay.powers.action.RovingTerror
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Giant Python's restriction (card 186): "Other cards at this site cannot
  * be used for non-persistent powers or placing [favor] / [secret]."
  * (catalog batch 8, slice 10, part 3). It binds every player and nobody
  * may waive it. */
class GiantPythonRestrictionSuite extends munit.FunSuite:
  private val python = CatalogNames.denizen("Giant Python")
  private val home = CatalogNames.site("Ancient City")
  private val woods = CatalogNames.site("Deep Woods")
  private val terror = CatalogNames.denizen("Roving Terror")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val source = PowerSourceRef.Card(python)

  /** p1's Act at Ancient City, ruled by p1, with Giant Python and Roving
    * Terror there and Wrestlers at Deep Woods. */
  private def pythonAtRuledHome: ReadyGame =
    Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .warbandsAt(home, p1, 1).denizen(python, at = home)
      .denizen(terror, at = home).denizen(wrestlers, at = woods).ready

  private def nonPersistentUseOf(card: DenizenId, by: PlayerId = p1)
      : PowerUse = PowerUse(by, PowerId("test.power"), Some(card),
    persistent = false, costsSecret = false)

  test("it refuses every player, its ruler included, a non-persistent " +
      "power of another card at its site, and nobody may waive it"):
    val refusal = GiantPythonRestriction.refusal(pythonAtRuledHome,
      nonPersistentUseOf(terror))
    assertEquals(refusal.map(_.note),
      Some(GiantPythonRestriction.usePlain(source)))
    assertEquals(refusal.flatMap(_.reason.waiver), None)
    assert(GiantPythonRestriction.refusal(pythonAtRuledHome,
      nonPersistentUseOf(terror, by = p2)).nonEmpty)

  test("Giant Python itself, and a card at another site, are free"):
    assertEquals(GiantPythonRestriction.refusal(pythonAtRuledHome,
      nonPersistentUseOf(python)), None)
    assertEquals(GiantPythonRestriction.refusal(pythonAtRuledHome,
      nonPersistentUseOf(wrestlers)), None)

  test("it refuses a payment onto a card at its site"):
    val ban = GiantPythonRestriction.operationRestrictions.head
    val pay = PayCost(p1, Location.OnCard(terror), Cost(favor = 1))
    assert(ban.reason(pythonAtRuledHome, pay).exists(_.waiver.isEmpty))
    assertEquals(ban.reason(pythonAtRuledHome,
      pay.copy(placedAt = Location.OnCard(python))), None)

  test("the phase menu shows a card at its site with its reason"):
    val option = MenuPlay.menuOptionFor(catalog, pythonAtRuledHome,
      ActionRef.UsePower(RovingTerror.id),
      Vector(DecisionOptionRef.Denizen(terror)))
    assertEquals(option, DecisionOption.Unavailable(
      DecisionOption.Denizen(DecisionOptionRef.Denizen(terror)),
      OptionReason.Noted(GiantPythonRestriction.id,
        GiantPythonRestriction.usePlain(source))))

  test("its reach moves with it"):
    val moved = Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(python, at = woods).denizen(terror, at = home)
      .denizen(wrestlers, at = woods).ready
    assertEquals(GiantPythonRestriction.refusal(moved,
      nonPersistentUseOf(terror)), None)
    assert(GiantPythonRestriction.refusal(moved,
      nonPersistentUseOf(wrestlers)).nonEmpty)
