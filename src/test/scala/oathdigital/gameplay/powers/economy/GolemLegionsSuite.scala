package oathdigital.gameplay.powers.economy

import oathdigital.application.{GameProjector, LoadedGame}
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PhasePowerHarness, PowerRules, Refusals, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class GolemLegionsSuite extends munit.FunSuite:
  private val golem = CatalogNames.denizen("Golem Legions")
  private val selected = Vector(GolemLegions.id)
  private val home = CatalogNames.site("Ancient City")
  /** An Arcane adviser besides Golem Legions. */
  private val magiciansCode = CatalogNames.denizen("Magician's Code")
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val vowOfBeastkin = CatalogNames.denizen("Vow of Beastkin")
  private val rules = PowerRules.withWalkerPowers
  private val projector = new GameProjector(catalog)

  /** p1 at Ancient City, where no card stands, holding Golem Legions faceup,
    * with the start's 1 favor, 7 Supply and 3 warbands on the board. */
  private def holdingGolem: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, golem)

  private def controls(ready: ReadyGame): Vector[String] =
    projector.project("golem", LoadedGame(Ready(ready), 4), p1).legalControls

  private def musterStarted(ready: ReadyGame): OathTransition =
    accepted(rules.startWalker(Ready(ready), ActionRef.Muster, p1, selected))

  private def musteredOnGolem(ready: ReadyGame): EconomyActions.Completed =
    EconomyActions.muster(rules, ready, p1, selected,
      DecisionOptionRef.Denizen(golem))

  test("selected, it offers Golem Legions as a Muster source, so a Muster " +
      "with no card at the pawn site can start"):
    val ready = holdingGolem.ready
    assertEquals(rules.startWalker(Ready(ready), ActionRef.Muster, p1)
      .left.toOption, Some(OathViolation.NoPlayableOption(ActionRef.Muster.key)))
    assert(controls(ready).contains("beginMuster"), controls(ready))
    assertEquals(PhasePowerHarness.offered(musterStarted(ready), p1),
      Some(Vector("denizen" -> golem.value)))

  test("mustering on it places the favor on Golem Legions, which is its " +
      "own matching adviser"):
    val ready = holdingGolem.ready
    val done = musteredOnGolem(ready)
    val after = Look(done.result)
    assertEquals(after.tokensOn(golem), Tokens(1, 0))
    assertEquals(after.favor(p1), Look(ready).favor(p1) - 1)
    // 1, plus 1 for Golem Legions, a faceup Arcane adviser.
    assertEquals(after.boardWarbands(p1), Look(ready).boardWarbands(p1) + 2)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("it counts itself once, beside the actor's other Arcane advisers"):
    val ready = holdingGolem.adviser(p1, magiciansCode).ready
    // 1, plus Golem Legions and Magician's Code.
    assertEquals(Look(musteredOnGolem(ready).result).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 3)

  test("while it holds favor it is shown unavailable and refused, so it " +
      "musters once between Rests"):
    val ready = holdingGolem.denizen(errandBoy, at = home)
      .tokens(golem, favor = 1).ready
    val started = musterStarted(ready)
    assertEquals(PhasePowerHarness.queryOf(started, p1).map(_.offeredOptions
      .map(option => (option.id, option.state, option.reason))), Some(Vector(
        (errandBoy.value, "available", None),
        (golem.value, "unavailable", Some("Already holds favor or secrets")))))
    assertEquals(rules.resolveWalker(started.state, p1,
      MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(golem))).left.toOption,
      Some(Refusals.notOffered(MusterProcedure.decisionId)))

  test("Vow of Beastkin still narrows the widened sources"):
    // Golem Legions is Arcane and matches itself. Errand Boy is Beast and
    // matches the Vow. Wrestlers is Order and matches nothing, so the Vow
    // hides it.
    val ready = holdingGolem.adviser(p1, vowOfBeastkin)
      .denizen(errandBoy, at = home).denizen(wrestlers, at = home).ready
    assertEquals(PhasePowerHarness.offered(musterStarted(ready), p1),
      Some(Vector("denizen" -> errandBoy.value, "denizen" -> golem.value)))
