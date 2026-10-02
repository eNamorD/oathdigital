package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.{CardStaging, NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure, SetupWalkDriver}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

class GreatForgeRulesSuite extends munit.FunSuite:
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

  private def finish(ready: ReadyGame): ReadyGame =
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveToCompletion(ready, tree, powers)

  test("Great Forge gives the placing player the top relic facedown"):
    val staged = stagedAt(EdificeSide.Intact)
    val topRelic = staged.game.current.commonCards.relicDeck.head
    val finished = finish(staged)
    val player = finished.game.current.players.find(_.player == firstPlayer).get
    assert(player.relics.exists(r => r.id == topRelic &&
      r.orientation == Orientation.FaceDown))

  test("Broken Forge discards every relic in its region to setAsideRelics"):
    val staged = stagedAt(EdificeSide.Ruined)
    val region = staged.game.current.map.regionOf(site).get
    val relicSite = staged.game.current.map.inPlay
      .find(s => staged.game.current.map.regionOf(s).contains(region) &&
        staged.game.current.map.sites(s).relics.nonEmpty).get
    val relicsBefore = staged.game.current.map.sites(relicSite).relics.map(_.id).toSet
    val finished = finish(staged)
    assert(relicsBefore.nonEmpty)
    assert(finished.game.current.map.sites(relicSite).relics.isEmpty)
    assert(relicsBefore.subsetOf(finished.game.current.setAsideRelics.toSet),
      "the discarded relics are set aside")

  // ---- Lines ----

  private def events(ready: ReadyGame): Vector[OathEvent] =
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveWithEvents(ready, tree, powers)._2

  private val great = GreatForge.forCatalog(catalog)
  private val broken = BrokenForge.forCatalog(catalog)

  test("Great Forge writes the relic its player drew"):
    val staged = stagedAt(EdificeSide.Intact)
    val topRelic = staged.game.current.commonCards.relicDeck.head
    val said = NoteText.said(great.id, great.noteKeys, events(staged))
    assertEquals(said.head, NoteText.Said(NoteKey.Used,
      s"${firstPlayer.value} drew ${topRelic.value} facedown.", covers = false))
    // The driver places every pawn at the first site, so each player draws.
    assertEquals(said.size, staged.game.current.players.size)

  test("Great Forge with an empty relic deck writes nothing"):
    val staged = stagedAt(EdificeSide.Intact).updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(relicDeck = Vector.empty)))
    assertEquals(NoteText.said(great.id, great.noteKeys, events(staged)),
      Vector.empty)

  test("Broken Forge writes the relics it discarded"):
    val staged = stagedAt(EdificeSide.Ruined)
    val current = staged.game.current
    val region = current.map.regionOf(site).get
    val relics = current.map.inPlay
      .filter(s => current.map.regionOf(s).contains(region))
      .flatMap(s => current.map.sites(s).relics.map(_.id.value))
    assert(relics.nonEmpty)
    assertEquals(NoteText.said(broken.id, broken.noteKeys, events(staged)),
      Vector(NoteText.Said("discarded",
        s"Discarded ${relics.mkString(", ")}.", covers = false)))
