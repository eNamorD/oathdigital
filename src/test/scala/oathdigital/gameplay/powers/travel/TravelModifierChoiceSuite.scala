package oathdigital.gameplay.powers.travel

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes}
import oathdigital.testkit.Unwrap.accepted

/** Travel asks for its modifiers inside the walk (modifier walker design,
  * slice 2). */
class TravelModifierChoiceSuite extends munit.FunSuite:
  import PowerFixture._
  import TravelFixture._

  private val tents = CatalogNames.denizen("Tents")

  /** The actor at the Cradle's plains with `supply` Supply and `favor`
    * favor, Tents a faceup adviser. */
  private def withTents(supply: Int, favor: Int): ReadyGame =
    on(board(supply = supply))(_.adviser(actor, tents).favor(actor, favor))

  private def started(ready: ReadyGame): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Travel, actor))

  private def options(state: OathState)(using munit.Location)
      : Vector[DecisionOption] = parkedDecision(state).query match
    case DecisionQuery.ChooseOne(listed, _, _) => listed
    case other => fail(s"expected a choose-one, got $other")

  private val startTravel: DecisionOption =
    DecisionOption.Button(ModifierPicks.start, "Start Travel")

  test("a Travel starts on the modifier decision when a modifier can be " +
      "picked"):
    val begun = started(withTents(supply = 7, favor = 1))
    assertEquals(parkedDecision(begun.state).decisionId, modifierDecisionId)
    assertEquals(options(begun.state), Vector(startTravel,
      DecisionOption.Priced(
        DecisionOption.Denizen(DecisionOptionRef.Denizen(tents)),
        OptionPrice(favor = 1))))

  test("with no modifier to pick, a Travel starts on its destination"):
    assertEquals(startParksOn(board()), TravelProcedure.destinationDecisionId)

  test("Start says why the Travel cannot go on, until a pick makes it free"):
    val begun = started(withTents(supply = 0, favor = 1))
    assertEquals(options(begun.state).head, DecisionOption.Unavailable(
      startTravel, OptionReason.Written("Needs 1 Supply; you have 0")))
    val picked = accepted(rules.resolveWalker(begun.state, actor,
      modifierDecisionId, ModifierPlay.pick(Tents.id)))
    val after = Outcomes.ready(picked.state)
    // The favor is paid at the pick, onto the card.
    assertEquals(Look(after).favor(actor), 0)
    assertEquals(Look(after).tokensOn(tents), Tokens(1, 0))
    assertEquals(parkedDecision(picked.state).decisionId,
      TravelProcedure.destinationDecisionId)

  test("a picked modifier pays once, at the pick"):
    val done = Outcomes.ready(accepted(travel(
      withTents(supply = 7, favor = 2), coast, Vector(Tents.id))).state)
    assertEquals(Look(done).favor(actor), 1)
    assertEquals(Look(done).tokensOn(tents), Tokens(1, 0))
