package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.{SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, EconomyActions, Look, Outcomes,
  Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Muster asks for its modifiers inside the walk (modifier walker design,
  * slice 3). */
class MusterModifierChoiceSuite extends munit.FunSuite:
  import EconomyActions.parkedDecision
  import SearchFixture.rules

  private val playmates = CatalogNames.denizen("Animal Playmates")
  /** A plain Beast denizen to muster on: it has no Muster or Trade power. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val home = "Ancient City"
  private val modifierDecisionId =
    ModifierPicks.decisionId(PowerWindow.MusterModifierSelection)
  private val startMuster: DecisionOption =
    DecisionOption.Button(ModifierPicks.start, "Start Muster")

  /** p1 at Ancient City beside Errand Boy, with 4 favor and `supply`
    * Supply, holding Animal Playmates faceup when `withPlaymates`. */
  private def besideErrandBoy(supply: Int, withPlaymates: Boolean = true)
      : ReadyGame =
    val table = Table.start.pawn(p1, at = home).denizen(errandBoy, at = home)
      .favor(p1, 4).supply(p1, supply)
    (if withPlaymates then table.adviser(p1, playmates) else table).ready

  private def started(ready: ReadyGame): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Muster, p1))

  private def options(state: OathState)(using munit.Location)
      : Vector[DecisionOption] = parkedDecision(state).query match
    case DecisionQuery.ChooseOne(listed, _) => listed
    case other => fail(s"expected a choose-one, got $other")

  test("a Muster starts on the modifier decision when a modifier can be " +
      "picked"):
    val begun = started(besideErrandBoy(supply = 7))
    assertEquals(parkedDecision(begun.state).decisionId, modifierDecisionId)
    assertEquals(options(begun.state), Vector(startMuster,
      DecisionOption.Denizen(DecisionOptionRef.Denizen(playmates))))

  test("with no modifier to pick, a Muster starts on its source"):
    val begun = started(besideErrandBoy(supply = 7, withPlaymates = false))
    assertEquals(parkedDecision(begun.state).decisionId,
      MusterProcedure.decisionId)

  test("Start says why the Muster cannot go on, until a pick waives the " +
      "Supply"):
    val begun = started(besideErrandBoy(supply = 0))
    assertEquals(options(begun.state).head, DecisionOption.Unavailable(
      startMuster, OptionReason.Written("Needs 1 Supply; you have 0")))
    val picked = accepted(rules.resolveWalker(begun.state, p1,
      modifierDecisionId, ModifierPlay.pick(AnimalPlaymates.id)))
    assertEquals(parkedDecision(picked.state).decisionId,
      MusterProcedure.decisionId)
    val done = accepted(rules.resolveWalker(picked.state, p1,
      MusterProcedure.decisionId, DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Denizen(errandBoy))))
    assertEquals(Look(Outcomes.ready(done.state)).tokensOn(errandBoy),
      Tokens(1, 0))

  test("the start control previews the sources, not the modifier " +
      "decision"):
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector.empty)
    assertEquals(MusterProcedure.startOptions(catalog,
      besideErrandBoy(supply = 7), p1, powers).map(_.option.ref),
      Vector[DecisionOptionRef](DecisionOptionRef.Denizen(errandBoy)))
