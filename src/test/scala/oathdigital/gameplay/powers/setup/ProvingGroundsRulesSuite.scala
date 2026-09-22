package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.{CardStaging, PlayerFacts, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure, SetupWalkDriver}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

class ProvingGroundsRulesSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val edifice = EdificeId("E22")
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)
  private val site = FirstGameSetupFixture.sites.head
  private val firstPlayer = PlayerId("p2")

  private def stagedAt(side: EdificeSide, at: SiteId = site): ReadyGame =
    CardStaging.without(FirstGameSetupFixture.freshReady, edifice).updateCurrent(c =>
      c.copy(map = c.map.copy(sites = c.map.sites.updated(at,
        c.map.sites(at).copy(denizens = c.map.sites(at).denizens :+
          EdificeState(edifice, side, Tokens.empty))))))

  private def finish(ready: ReadyGame): ReadyGame = {
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveToCompletion(ready, tree, powers)
  }

  test("Proving Grounds gives the placing player three warbands") {
    val staged = stagedAt(EdificeSide.Intact)
    PlayerFacts.forceKind(staged, firstPlayer).toOption.get
    val before = staged.game.current.players.find(_.player == firstPlayer).get
      .board.warbands
    val finished = finish(staged)
    val after = finished.game.current.players.find(_.player == firstPlayer).get
      .board.warbands
    assertEquals(after, before + 3)
  }

  test("Empty Grounds discards every other denizen in its region") {
    val staged = stagedAt(EdificeSide.Ruined)
    val region = staged.game.current.map.regionOf(site).get
    val remainingOtherDenizens = { (state: ReadyGame) =>
      state.game.current.map.inPlay.filter(s =>
        state.game.current.map.regionOf(s).contains(region))
        .flatMap(s => state.game.current.map.sites(s).denizens)
        .exists {
          case e: EdificeState => e.id == edifice
          case _ => true
        }
    }
    assert(remainingOtherDenizens(staged), "fixture must have other cards in the region")
    val finished = finish(staged)
    val remaining = finished.game.current.map.inPlay.filter(s =>
      finished.game.current.map.regionOf(s).contains(region))
      .flatMap(s => finished.game.current.map.sites(s).denizens)
    // Empty Grounds discards all OTHER denizens, not itself -- it stays on
    // the board as the sole survivor of its own region.
    assertEquals(remaining, Vector[SiteDenizenState](EdificeState(edifice,
      EdificeSide.Ruined, Tokens.empty)))
  }
}
