package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{Look, Table}

class ElderSuite extends munit.FunSuite:
  import PowerFixture._

  private val elders = DenizenId("26")
  private val source = DecisionOptionRef.Denizen(elders)
  private val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private def staged(favor: Int) = inPhase(Table.from(atActorsSite(quietTable,
    elders)).favor(actor, favor).unchecked, Phase.Act)
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(Elders.id), actor, Vector.empty, Vector(source))
  private def secrets(ready: ReadyGame) =
    Look(ready).player(actor).board.faceUpSecrets + Look(ready).player(actor)
      .board.faceDownSecrets

  test("it places 2 favor on its card and gains a secret"):
    val ready = staged(favor = 3)
    val used = use(ready).toOption.get.state.asInstanceOf[Ready].value
    assertEquals(Look(used).player(actor).board.favor, 1)
    assertEquals(secrets(used), secrets(ready) + 1)
    assertEquals(used.game.current.map.sites(actorsSite(used))
      .denizens.collectFirst {
      case d: DenizenState if d.id == elders => d.tokens }.get, Tokens(2, 0))

  test("one favor is not enough"):
    val broke = staged(favor = 1)
    assertEquals(PhasePowerProcedure.usable(catalog, broke, actor,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty), Vector.empty)
    assert(use(broke).isLeft)

  test("it writes its gain as its own line, covering the generic one"):
    val done = use(staged(favor = 3)).toOption.get
    assertEquals(NoteText.said(Elders, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 secret.", covers = true)))
