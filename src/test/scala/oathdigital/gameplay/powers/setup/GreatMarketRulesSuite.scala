package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.{CardStaging, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure, SetupWalkDriver}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

class GreatMarketRulesSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val edifice = EdificeId("E02")
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)
  private val site = FirstGameSetupFixture.sites.head

  private def stagedAt(side: EdificeSide): ReadyGame =
    CardStaging.without(FirstGameSetupFixture.freshReady, edifice).updateCurrent(c =>
      c.copy(map = c.map.copy(sites = c.map.sites.updated(site,
        c.map.sites(site).copy(denizens = c.map.sites(site).denizens :+
          EdificeState(edifice, side, Tokens.empty))))))

  private def finish(ready: ReadyGame): ReadyGame = {
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveToCompletion(ready, tree, powers)
  }

  test("Great Market places one favor per denizen in its region, itself included") {
    val staged = stagedAt(EdificeSide.Intact)
    val region = staged.game.current.map.regionOf(site).get
    val expectedCount = staged.game.current.map.inPlay
      .filter(s => staged.game.current.map.regionOf(s).contains(region))
      .flatMap(s => staged.game.current.map.sites(s).denizens)
      .size
    val finished = finish(staged)
    assertEquals(finished.game.current.map.sites(site).tokens.favor, expectedCount)
  }

  test("Bandit Market favors every bandit-ruled site and burns every bank") {
    val staged = stagedAt(EdificeSide.Ruined)
    val banditSite = FirstGameSetupFixture.sites(1)
    // The fixture's own board already occupies every site's capacity with
    // bandits by default -- clear all but one site so exactly one site is
    // bandit-ruled, keeping the arithmetic below unambiguous.
    val withBandits = staged.updateCurrent(c => c.copy(map = c.map.copy(
      sites = c.map.sites.map { case (s, state) =>
        s -> (if (s == banditSite) state.copy(forces = SiteForces.Occupied(ForceKind.Bandit, 1))
              else state.copy(forces = SiteForces.Empty))
      })))
    val marketSuit = FirstGameSetupFixture.catalog.suitOf(edifice).get
    val startingBank = withBandits.banks.favor
    val finished = finish(withBandits)
    assertEquals(finished.game.current.map.sites(banditSite).tokens.favor, 1)
    Suit.all.foreach(suit => assertEquals(finished.banks.favor(suit),
      startingBank(suit) - (if (suit == marketSuit) 2 else 1)))
  }
}
