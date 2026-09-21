package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class MagicWaterskinSuite extends munit.FunSuite {
  import PowerFixture._

  private val skin = RelicId("R45")
  private val source = DecisionOptionRef.Relic(skin)
  private val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private def staged(orientation: Orientation = Orientation.FaceUp,
      supply: Int = 1) = inPhase(withBoard(withRelic(base, skin, orientation))(
    _.copy(supply = SupplyTrack(supply))), Phase.Act)
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(MagicWaterskin.id), actor, Vector.empty, Vector(source))

  test("Magic Waterskin is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(MagicWaterskin.id).isDefined)
  }

  test("it buries itself at the bottom of the relic deck and gains 4 Supply") {
    val used = use(staged()).toOption.get.state.asInstanceOf[Ready].value
    assert(!player(used).relics.exists(_.id == skin))
    assertEquals(used.game.current.commonCards.relicDeck.last, skin)
    assertEquals(player(used).board.supply, SupplyTrack(5))
  }

  test("a secret on the relic returns to its holder facedown") {
    val ready = staged().updateCurrent(c => c.copy(players = c.players.map(p =>
      if (p.player != actor) p else p.copy(relics = p.relics.map(r =>
        r.copy(tokens = Tokens(0, 1)))))))
    val used = use(ready).toOption.get.state.asInstanceOf[Ready].value
    assertEquals(player(used).board.faceDownSecrets,
      player(ready).board.faceDownSecrets + 1)
    assertEquals(used.game.current.commonCards.relicDeck.last, skin)
  }

  test("the gain is clamped at the track maximum") {
    val used = use(staged(supply = 5)).toOption.get.state
      .asInstanceOf[Ready].value
    assertEquals(player(used).board.supply, SupplyTrack(7))
  }

  test("a facedown relic cannot be used") {
    val facedown = staged(Orientation.FaceDown)
    assertEquals(PhasePowerProcedure.usable(catalog, facedown, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    assert(use(facedown).isLeft)
  }
}
