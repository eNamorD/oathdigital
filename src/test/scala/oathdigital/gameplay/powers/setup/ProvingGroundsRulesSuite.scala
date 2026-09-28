package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.{CardStaging, NoteText, PlayerFacts, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure, SetupWalkDriver}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

class ProvingGroundsRulesSuite extends munit.FunSuite:
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

  private def finish(ready: ReadyGame): ReadyGame =
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveToCompletion(ready, tree, powers)

  test("Proving Grounds gives the placing player three warbands"):
    val staged = stagedAt(EdificeSide.Intact)
    PlayerFacts.forceKind(staged, firstPlayer).toOption.get
    val before = staged.game.current.players.find(_.player == firstPlayer).get
      .board.warbands
    val finished = finish(staged)
    val after = finished.game.current.players.find(_.player == firstPlayer).get
      .board.warbands
    assertEquals(after, before + 3)

  /** Empty Grounds' site with two denizens beside it. The fixture's region
    * holds no other card, so without them there is nothing to discard. */
  private def withOtherDenizens: ReadyGame =
    val extra = FirstGameSetupFixture.freshReady.game.current.commonCards
      .worldDeck.collect { case id: DenizenId => id }.take(2)
    extra.foldLeft(stagedAt(EdificeSide.Ruined))((ready, id) =>
      CardStaging.without(ready, id).updateCurrent(c => c.copy(map = c.map.copy(
        sites = c.map.sites.updated(site, c.map.sites(site).copy(denizens =
          c.map.sites(site).denizens :+
            DenizenState(id, Orientation.FaceUp, Tokens.empty)))))))

  test("Empty Grounds discards every other denizen in its region"):
    val staged = withOtherDenizens
    val region = staged.game.current.map.regionOf(site).get
    val finished = finish(staged)
    val remaining = finished.game.current.map.inPlay.filter(s =>
      finished.game.current.map.regionOf(s).contains(region))
      .flatMap(s => finished.game.current.map.sites(s).denizens)
    // Empty Grounds discards all OTHER denizens, not itself -- it stays on
    // the board as the sole survivor of its own region.
    assertEquals(remaining, Vector[SiteDenizenState](EdificeState(edifice,
      EdificeSide.Ruined, Tokens.empty)))

  // ---- Lines ----

  private def events(ready: ReadyGame): Vector[OathEvent] =
    val tree = SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty).toOption.get
    SetupWalkDriver.driveWithEvents(ready, tree, powers)._2

  private val proving = ProvingGrounds.forCatalog(catalog).get
  private val empty = EmptyGrounds.forCatalog(catalog).get

  test("Proving Grounds writes the warbands its player gained"):
    val staged = stagedAt(EdificeSide.Intact)
    val said = NoteText.said(proving.id, proving.noteKeys, events(staged))
    assertEquals(said.head, NoteText.Said("gained",
      s"${firstPlayer.value} gained 3 warbands.", covers = false))
    // The driver places every pawn at the first site, so each player gains.
    assertEquals(said.size, staged.game.current.players.size)

  test("Empty Grounds writes the cards it discarded, in place of the Discard line"):
    val staged = withOtherDenizens
    val current = staged.game.current
    val region = current.map.regionOf(site).get
    val others = current.map.inPlay
      .filter(s => current.map.regionOf(s).contains(region))
      .flatMap(s => current.map.sites(s).denizens.map(_.id.value))
      .filterNot(_ == edifice.value)
    assert(others.nonEmpty)
    assertEquals(NoteText.said(empty.id, empty.noteKeys, events(staged)),
      Vector(NoteText.Said("discarded",
        s"Discarded ${others.mkString(", ")}.", covers = true)))
