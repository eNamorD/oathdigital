package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class WaysideInnSuite extends munit.FunSuite:
  import PowerFixture._

  private val inn = DenizenId("47")
  private val source = DecisionOptionRef.Denizen(inn)
  private val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private def staged(favor: Int, supply: Int) = inPhase(withBoard(
    atHome(base, inn))(_.copy(favor = favor, supply = SupplyTrack(supply))),
    Phase.Act)
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(WaysideInn.id), actor, Vector.empty, Vector(source))
  private def after(state: OathState) = state.asInstanceOf[Ready].value
  private def innCard(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == inn => d }.get

  test("it places 1 favor on its card and gains 2 Supply"):
    val used = after(use(staged(favor = 3, supply = 2)).toOption.get.state)
    assertEquals(player(used).board.favor, 2)
    assertEquals(player(used).board.supply, SupplyTrack(4))
    assertEquals(innCard(used).tokens, Tokens(1, 0))
    assertEquals(used.game.current.turn.usedPowers, Set.empty[PowerUseRef])

  test("the gain is clamped at the track maximum"):
    val used = after(use(staged(favor = 1, supply = 6)).toOption.get.state)
    assertEquals(player(used).board.supply, SupplyTrack(7))

  test("it is unusable without favor to place"):
    val broke = staged(favor = 0, supply = 2)
    assertEquals(PhasePowerProcedure.usable(catalog, broke, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    assert(use(broke).isLeft)

  test("it is unusable again while its card holds the favor"):
    val used = after(use(staged(favor = 3, supply = 2)).toOption.get.state)
    assertEquals(PhasePowerProcedure.usable(catalog, used, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    assert(use(used).isLeft)

  test("it is usable in the Act phase only"):
    val wake = inPhase(staged(favor = 3, supply = 2), Phase.Wake)
    assertEquals(PhasePowerProcedure.usable(catalog, wake, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)

  test("it writes the Supply it gained as its own line"):
    val done = use(staged(favor = 3, supply = 2)).toOption.get
    assertEquals(NoteText.said(WaysideInn, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 2 Supply.", covers = false)))

  test("a gain the track caps is written as what it gained, and a full track writes nothing"):
    assertEquals(NoteText.said(WaysideInn,
      use(staged(favor = 1, supply = 6)).toOption.get.events).map(_.text),
      Vector(s"${actor.value} gained 1 Supply."))
    assertEquals(NoteText.said(WaysideInn,
      use(staged(favor = 1, supply = 7)).toOption.get.events), Vector.empty)
