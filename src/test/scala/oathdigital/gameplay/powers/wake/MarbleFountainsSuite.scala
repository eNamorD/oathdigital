package oathdigital.gameplay.powers.wake

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{PhasePowerCatalog, PlayerFacts, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready

class MarbleFountainsSuite extends munit.FunSuite {
  import PowerFixture._

  private val fountains = EdificeId("E15")
  private val source = DecisionOptionRef.Edifice(fountains)
  private val rules = new OathRules(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val kind = PlayerFacts.forceKind(base, actor).toOption.get

  /** The edifice is at the pawn's site, or at a far site the actor rules, so
    * that in the second case only the pawn condition fails.
    */
  private def staged(side: EdificeSide = EdificeSide.Intact,
      pawnAtEdifice: Boolean = true) = {
    val site = home(base)
    val far = base.game.current.map.inPlay.toVector.sortBy(_.value)
      .find(_ != site).get
    val placed = withEdifice(base, fountains, side,
      if (pawnAtEdifice) site else far)
    val ruled = if (pawnAtEdifice) placed else placed.updateCurrent(c =>
      c.copy(map = c.map.copy(sites = c.map.sites.updated(far,
        c.map.sites(far).copy(forces = SiteForces.Occupied(kind, 1))))))
    inPhase(withBoard(ruled)(_.copy(supply = SupplyTrack(1))), Phase.Wake)
  }
  private def use(ready: ReadyGame) = rules.startWalker(Ready(ready),
    ActionRef.UsePower(MarbleFountains.id), actor, Vector.empty, Vector(source))

  test("Marble Fountains is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(MarbleFountains.id).isDefined)
  }

  test("it refreshes Supply to the maximum when the pawn is at the site") {
    val used = use(staged()).toOption.get.state.asInstanceOf[Ready].value
    assertEquals(player(used).board.supply, SupplyTrack(7))
  }

  test("it is once per turn") {
    val first = use(staged()).toOption.get.state
    val used = PowerUseRef(PowerTiming.Wake, PowerSourceRef.Card(fountains),
      MarbleFountains.id)
    assert(first.asInstanceOf[Ready].value.game.current.turn.usedPowers
      .contains(used))
    assertEquals(use(first.asInstanceOf[Ready].value).left.toOption,
      Some(OathViolation.PowerAlreadyUsed(used)))
  }

  test("it is unusable when the pawn is at another site") {
    val away = staged(pawnAtEdifice = false)
    assert(PowerAccess.locate(away, actor, fountains).isDefined,
      "the edifice must be reachable, or this test proves nothing")
    assertEquals(PhasePowerProcedure.usable(catalog, away, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
    assert(use(away).isLeft)
  }

  test("a ruined Marble Fountains offers nothing") {
    val ruined = staged(EdificeSide.Ruined)
    assertEquals(PhasePowerProcedure.usable(catalog, ruined, actor,
      PhasePowerCatalog.default(catalog)), Vector.empty)
  }
}
