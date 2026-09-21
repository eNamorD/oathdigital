package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class ElderSuite extends munit.FunSuite {
  import PowerFixture._

  private val elders = DenizenId("26")
  private val source = DecisionOptionRef.Denizen(elders)
  private val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private def staged(favor: Int) = inPhase(withBoard(atHome(base, elders))(
    _.copy(favor = favor)), Phase.Act)
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(Elders.id), actor, Vector.empty, Vector(source))
  private def secrets(ready: ReadyGame) =
    player(ready).board.faceUpSecrets + player(ready).board.faceDownSecrets

  test("Elders is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(Elders.id).isDefined)
  }

  test("it places 2 favor on its card and gains a secret") {
    val ready = staged(favor = 3)
    val used = use(ready).toOption.get.state.asInstanceOf[Ready].value
    assertEquals(player(used).board.favor, 1)
    assertEquals(secrets(used), secrets(ready) + 1)
    assertEquals(used.game.current.map.sites(home(used)).denizens.collectFirst {
      case d: DenizenState if d.id == elders => d.tokens }.get, Tokens(2, 0))
  }

  test("one favor is not enough") {
    val broke = staged(favor = 1)
    assertEquals(PhasePowerProcedure.usable(catalog, broke, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    assert(use(broke).isLeft)
  }
}
