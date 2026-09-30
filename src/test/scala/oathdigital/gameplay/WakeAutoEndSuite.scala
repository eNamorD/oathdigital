package oathdigital.gameplay

import oathdigital.model.OathState.Ready
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.PhasePowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{WalkerCompleted, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.Table

/** A Wake with nothing left to decide ends by itself, in the command that
  * left it so: at the turn boundary, and after the last Wake option. */
class WakeAutoEndSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)

  /** Every site empty of favor and secrets, and each pawn alone at a site
    * with no River, so no Wake option is open unless a test adds one. */
  private val quiet: ReadyGame =
    val initial = Table.start.ready
    val current = initial.game.current
    val plain = current.map.inPlay.filter(site => !catalog.sites
      .find(_.id.value == site.value).exists(_.handlers.exists(_.contains("river"))))
    assert(plain.size >= current.players.size, plain.toString)
    initial.updateCurrent(_.copy(
      map = current.map.copy(sites = current.map.sites.view
        .mapValues(_.copy(tokens = Tokens.empty)).toMap),
      players = current.players.zip(plain).map((player, site) =>
        player.copy(pawnSite = Some(site))),
      turn = current.turn.copy(phase = Phase.Act)))
  private val actor = quiet.game.current.turn.activePlayer

  private def ready(state: OathState) = state.asInstanceOf[Ready].value
  private def completed(events: Vector[OathEvent]) =
    events.collect { case WalkerCompleted(procedure) => procedure }
  /** One favor at every other player's site, so whoever wakes next can
    * take it. */
  private def withFavor(ready: ReadyGame): ReadyGame =
    val current = ready.game.current
    val sites = current.players.filter(_.player != actor).flatMap(_.pawnSite)
    ready.updateCurrent(_.copy(map = current.map.copy(sites =
      sites.foldLeft(current.map.sites)((all, site) =>
        all.updated(site, all(site).copy(tokens = Tokens(1, 0)))))))
  private def replayed(from: ReadyGame, events: Vector[OathEvent]) =
    events.foldLeft[Either[OathViolation, OathState]](Right(Ready(from)))(
      (state, event) => state.flatMap(rules.evolve(_, event)))

  test("the next player's Wake with nothing to decide ends in the same command"):
    val rested = rules.startWalker(Ready(quiet), PhaseTransitionRef.BeginRest,
      actor).toOption.get
    val next = ready(rested.state).game.current.turn
    assertNotEquals(next.activePlayer, actor)
    assertEquals(PhasePowerProcedure.usable(catalog, ready(rested.state)
      .updateCurrent(c => c.copy(turn = c.turn.copy(phase = Phase.Wake))),
      next.activePlayer, PhasePowerCatalog.default(catalog), WalkerPowers.empty), Vector.empty)
    assertEquals(completed(rested.events), Vector(PhaseTransitionRef.BeginRest,
      PhaseTransitionRef.FinishRest, PhaseTransitionRef.EndWake))
    assertEquals(next.phase, Phase.Act)
    assertEquals(replayed(quiet, rested.events), Right(rested.state))

  test("a Wake with wealth to take waits for the player"):
    val rested = rules.startWalker(Ready(withFavor(quiet)),
      PhaseTransitionRef.BeginRest, actor).toOption.get
    assertEquals(completed(rested.events), Vector(PhaseTransitionRef.BeginRest,
      PhaseTransitionRef.FinishRest))
    assertEquals(ready(rested.state).game.current.turn.phase, Phase.Wake)

  test("taking the last wealth ends Wake"):
    val waking = rules.startWalker(Ready(withFavor(quiet)),
      PhaseTransitionRef.BeginRest, actor).toOption.get
    val next = ready(waking.state).game.current.turn.activePlayer
    val took = rules.startWalker(waking.state, ActionRef.TakeWealth, next,
      startArgs = Vector(DecisionOptionRef.Button("favor"))).toOption.get
    assertEquals(completed(took.events), Vector(ActionRef.TakeWealth,
      PhaseTransitionRef.EndWake))
    assertEquals(ready(took.state).game.current.turn.phase, Phase.Act)
    assertEquals(replayed(ready(waking.state), took.events), Right(took.state))
