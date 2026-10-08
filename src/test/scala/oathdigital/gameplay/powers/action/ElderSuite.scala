package oathdigital.gameplay.powers.action

import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Outcomes,
  Table}
import oathdigital.testkit.Unwrap.accepted

class ElderSuite extends munit.FunSuite:
  import PowerFixture._

  private val elders = CatalogNames.denizen("Elders")
  private val source = DecisionOptionRef.Denizen(elders)
  private val rules = ActPowerHarness.rules()
  /** p1's Act at Ancient City with Elders at the site and `favor` favor. */
  private def staged(favor: Int) = Table.start.turn(actor, Phase.Act)
    .pawn(actor, "Ancient City").denizen(elders, at = "Ancient City")
    .favor(actor, favor).ready
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(Elders.id), actor, Vector.empty, Vector(source))
  private def secrets(ready: ReadyGame) =
    Look(ready).player(actor).board.faceUpSecrets + Look(ready).player(actor)
      .board.faceDownSecrets

  test("it places 2 favor on its card and gains a secret"):
    val ready = staged(favor = 3)
    val used = Outcomes.ready(accepted(use(ready)).state)
    assertEquals(Look(used).player(actor).board.favor, 1)
    assertEquals(secrets(used), secrets(ready) + 1)
    assertEquals(Look(used).tokensOn(elders), Tokens(2, 0))

  test("one favor is not enough"):
    val broke = staged(favor = 1)
    assertEquals(PhasePowerProcedure.usable(catalog, broke, actor,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty), Vector.empty)
    assertEquals(use(broke).left.toOption,
      Some(OathViolation.InsufficientFavor(2, 1)))

  test("it writes its gain as its own line, covering the generic one"):
    val done = accepted(use(staged(favor = 3)))
    assertEquals(NoteText.said(Elders, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 secret.", covers = true)))
