package oathdigital.gameplay

import oathdigital.model.OathState.Ready
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powers.PhasePowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerCompleted, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, Look, MenuPlay, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted
import oathdigital.testkit.PhasePowerHarness

/** A Wake with nothing left to decide ends by itself, in the command that
  * left it so: at the turn boundary, and after the last Wake option. */
class WakeAutoEndSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)

  /** The quiet table: p1's Act, no site holds favor or secrets, and each
    * pawn stands alone at a site: p1 at Ancient City, p2 at Broken Peaks and
    * p3 at Buried Giant. Ancient City is the only River in play, so its River
    * offers nothing. No Wake option is open unless a test adds one. */
  private val quiet: ReadyGame = Table.start
    .pawn(p1, at = "Ancient City").pawn(p2, at = "Broken Peaks")
    .pawn(p3, at = "Buried Giant").ready
  private val actor = p1

  private def completed(events: Vector[OathEvent]) =
    events.collect { case WalkerCompleted(procedure) => procedure }
  /** One favor at p2's and p3's sites, so whoever wakes next can take it. */
  private def withFavor(ready: ReadyGame): ReadyGame =
    Table.from(ready).siteTokens("Broken Peaks", favor = 1)
      .siteTokens("Buried Giant", favor = 1).unchecked
  private def replayed(from: ReadyGame, events: Vector[OathEvent]) =
    Ready(ActPowerHarness.replayed(rules, from, events))

  test("the next player's Wake with nothing to decide ends in the same command"):
    val rested = accepted(
      rules.startWalker(Ready(quiet), PhaseTransitionRef.BeginRest, actor))
    val restedReady = Outcomes.ready(rested.state)
    val next = restedReady.game.current.turn
    assertNotEquals(next.activePlayer, actor)
    val inWake = Table.from(restedReady).backInPhase(Phase.Wake).unchecked
    assertEquals(PhasePowerHarness.menuPowers(catalog, inWake,
        next.activePlayer, PhasePowerCatalog.default(catalog),
        WalkerPowers.empty), Vector.empty)
    assertEquals(completed(rested.events), Vector(PhaseTransitionRef.BeginRest,
      PhaseTransitionRef.FinishRest, PhaseTransitionRef.EndWake))
    assertEquals(next.phase, Phase.Act)
    assertEquals(replayed(quiet, rested.events), rested.state)

  test("a Wake with wealth to take waits for the player"):
    val rested = accepted(rules.startWalker(Ready(withFavor(quiet)),
      PhaseTransitionRef.BeginRest, actor))
    assertEquals(completed(rested.events), Vector(PhaseTransitionRef.BeginRest,
      PhaseTransitionRef.FinishRest))
    assertEquals(Look(rested.state).phase, Phase.Wake)
    assertEquals(MenuPlay.parked(rested.state), Some(PhaseMenu.Menu.Wake))

  test("taking the last wealth ends Wake"):
    val waking = accepted(rules.startWalker(Ready(withFavor(quiet)),
      PhaseTransitionRef.BeginRest, actor))
    val next = Look(waking.state).active
    val took = accepted(MenuPlay.picked(rules, waking.state, next,
      PhaseMenu.takeWealth(WakeResource.Favor)))
    assertEquals(completed(took.events), Vector(TriggeredProcedureRef.WakeMenu,
      ActionRef.TakeWealth, PhaseTransitionRef.EndWake))
    assertEquals(Look(took.state).phase, Phase.Act)
    assertEquals(replayed(Outcomes.ready(waking.state), took.events),
      took.state)
