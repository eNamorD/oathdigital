package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.ready
import oathdigital.testkit.Unwrap.accepted
import oathdigital.testkit.PhasePowerHarness

class WaysideInnSuite extends munit.FunSuite:
  import PowerFixture._

  private val inn = CatalogNames.denizen("Wayside Inn")
  private val source = DecisionOptionRef.Denizen(inn)
  private val rules = ActPowerHarness.rules()
  /** p1's Act at Ancient City with Wayside Inn at the site, `favor` favor
    * and `supply` Supply. */
  private def staged(favor: Int, supply: Int) =
    Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .denizen(inn, at = "Ancient City").favor(actor, favor)
      .supply(actor, supply).ready
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(WaysideInn.id), actor, Vector(source))

  test("it places 1 favor on its card and gains 2 Supply"):
    val used = ready(accepted(use(staged(favor = 3, supply = 2))).state)
    assertEquals(Look(used).player(actor).board.favor, 2)
    assertEquals(Look(used).player(actor).board.supply, SupplyTrack(4))
    assertEquals(Look(used).tokensOn(inn), Tokens(1, 0))
    assertEquals(used.game.current.turn.usedPowers, Set.empty[PowerUseRef])

  test("the gain is clamped at the track maximum"):
    val used = ready(accepted(use(staged(favor = 1, supply = 6))).state)
    assertEquals(Look(used).player(actor).board.supply, SupplyTrack(7))

  test("it is unusable without favor to place"):
    val broke = staged(favor = 0, supply = 2)
    assertEquals(PhasePowerHarness.menuPowers(catalog, broke, actor,
        PhasePowerCatalog.default(catalog), WalkerPowers.empty), Vector.empty)
    assertEquals(use(broke).left.toOption,
      Some(OathViolation.InsufficientFavor(1, 0)))

  test("it is usable in the Act phase only"):
    val wake = inPhase(staged(favor = 3, supply = 2), Phase.Wake)
    assertEquals(PhasePowerHarness.menuPowers(catalog, wake, actor,
        PhasePowerCatalog.default(catalog), WalkerPowers.empty), Vector.empty)

  test("it writes the Supply it gained as its own line"):
    val done = accepted(use(staged(favor = 3, supply = 2)))
    assertEquals(NoteText.said(WaysideInn, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 2 Supply.", covers = false)))

  test("a gain the track caps is written as what it gained, and a full track writes nothing"):
    assertEquals(NoteText.said(WaysideInn,
      accepted(use(staged(favor = 1, supply = 6))).events).map(_.text),
      Vector(s"${actor.value} gained 1 Supply."))
    assertEquals(NoteText.said(WaysideInn,
      accepted(use(staged(favor = 1, supply = 7))).events), Vector.empty)
