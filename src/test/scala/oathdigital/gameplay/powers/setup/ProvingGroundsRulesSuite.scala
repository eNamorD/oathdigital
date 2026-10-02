package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.{NoteText, PlayerFacts, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure, SetupWalkDriver}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Unwrap.accepted

class ProvingGroundsRulesSuite extends munit.FunSuite:
  private val catalog = FirstGameSetupFixture.catalog
  private val edifice = CatalogNames.edifice("Proving Grounds")
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)
  private val site = CatalogNames.site("Ancient City")
  // p2 is the first player of the fixture's first game.
  private val firstPlayer = Table.p2
  // The fresh deal's first two world-deck denizens.
  private val otherDenizens = Vector("Threatening Roar", "Fae Merchant")
    .map(CatalogNames.denizen(_))

  private def stagedAt(side: EdificeSide, at: SiteId = site): ReadyGame =
    Table.from(FirstGameSetupFixture.freshReady).edifice(edifice, side, at = at)
      .unchecked

  private def finish(ready: ReadyGame): ReadyGame =
    val tree = accepted(SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty))
    SetupWalkDriver.driveToCompletion(ready, tree, TriggeredProcedureRef.Setup,
      powers)

  test("Proving Grounds gives the placing player three warbands"):
    val staged = stagedAt(EdificeSide.Intact)
    assert(PlayerFacts.forceKind(staged, firstPlayer).isRight,
      "the first player has a warband kind to take")
    val before = Look(staged).boardWarbands(firstPlayer)
    val finished = finish(staged)
    assertEquals(Look(finished).boardWarbands(firstPlayer), before + 3)

  /** Empty Grounds' site with two denizens beside it. The fixture's region
    * holds no other card, so without them there is nothing to discard. */
  private def withOtherDenizens: ReadyGame =
    otherDenizens.foldLeft(Table.from(stagedAt(EdificeSide.Ruined)))(
      _.denizen(_, at = site)).unchecked

  test("Empty Grounds discards every other denizen in its region"):
    val staged = withOtherDenizens
    val region = Region.Cradle // Ancient City's region
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
    val tree = accepted(SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty))
    SetupWalkDriver.driveWithEvents(ready, tree, TriggeredProcedureRef.Setup,
      powers).events

  private val proving = ProvingGrounds.forCatalog(catalog)
  private val empty = EmptyGrounds.forCatalog(catalog)

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
    val region = Region.Cradle // Ancient City's region
    val others = current.map.inPlay
      .filter(s => current.map.regionOf(s).contains(region))
      .flatMap(s => current.map.sites(s).denizens.map(_.id.value))
      .filterNot(_ == edifice.value)
    assert(others.nonEmpty)
    assertEquals(NoteText.said(empty.id, empty.noteKeys, events(staged)),
      Vector(NoteText.Said("discarded",
        s"Discarded ${others.mkString(", ")}.", covers = true)))
