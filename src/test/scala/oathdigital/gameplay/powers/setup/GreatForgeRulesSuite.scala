package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.{CardStaging, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure, SetupWalkDriver}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

class GreatForgeRulesSuite extends munit.FunSuite {
  private val catalog = FirstGameSetupFixture.catalog
  private val edifice = EdificeId("E06")
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)
  // The generic driver always answers a Decide with its first offered
  // option, and `siteOptions` is `map.inPlay` order -- staging at
  // `sites.head` puts the edifice under the FIRST participant's (setup's
  // first player) pawn-placement choice.
  private val site = FirstGameSetupFixture.sites.head
  private val firstPlayer = PlayerId("p2")

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

  test("Great Forge gives the placing player the top relic facedown") {
    val staged = stagedAt(EdificeSide.Intact)
    val topRelic = staged.game.current.commonCards.relicDeck.head
    val finished = finish(staged)
    val player = finished.game.current.players.find(_.player == firstPlayer).get
    assert(player.relics.exists(r => r.id == topRelic &&
      r.orientation == Orientation.FaceDown))
  }

  test("Broken Forge discards every relic in its region to setAsideRelics") {
    val staged = stagedAt(EdificeSide.Ruined)
    val region = staged.game.current.map.regionOf(site).get
    val relicSite = staged.game.current.map.inPlay
      .find(s => staged.game.current.map.regionOf(s).contains(region) &&
        staged.game.current.map.sites(s).relics.nonEmpty).get
    val relicsBefore = staged.game.current.map.sites(relicSite).relics.map(_.id).toSet
    val finished = finish(staged)
    assert(finished.game.current.map.sites(relicSite).relics.isEmpty)
    assert(relicsBefore.nonEmpty)
  }
}
