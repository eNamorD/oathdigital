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
  // Any two denizens will do: Empty Grounds must discard them.
  private val otherDenizens = Vector("Threatening Roar", "Fae Merchant")
    .map(CatalogNames.denizen(_))

  // The Setup walk is the subject (the edifice's effect runs when the walk
  // reaches it), so these tests start from the fresh Setup, not a Table.
  private def stagedAt(side: EdificeSide, at: SiteId = site): ReadyGame =
    Table.from(FirstGameSetupFixture.freshReady).edifice(edifice, side, at = at)
      .unchecked

  private def finish(ready: ReadyGame): ReadyGame =
    val tree = accepted(SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty))
    SetupWalkDriver.driveToCompletion(ready, tree, TriggeredProcedureRef.Setup,
      powers)

  /** Empty Grounds' site with two denizens beside it. The fixture's region
    * holds no other card, so without them there is nothing to discard. */
  private def withOtherDenizens: ReadyGame =
    otherDenizens.foldLeft(Table.from(stagedAt(EdificeSide.Ruined)))(
      _.denizen(_, at = site)).unchecked

  private def events(ready: ReadyGame): Vector[OathEvent] =
    val tree = accepted(SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty))
    SetupWalkDriver.driveWithEvents(ready, tree, TriggeredProcedureRef.Setup,
      powers).events

  private val proving = ProvingGrounds.forCatalog(catalog)

  private val empty = EmptyGrounds.forCatalog(catalog)

  test("Proving Grounds gives the placing player three warbands"):
    val staged = stagedAt(EdificeSide.Intact)
    assert(PlayerFacts.forceKind(staged, firstPlayer).isRight,
      "the first player has a warband kind to take")
    val before = Look(staged).boardWarbands(firstPlayer)
    val finished = finish(staged)
    assertEquals(Look(finished).boardWarbands(firstPlayer), before + 3)

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

  /** Every card at a site in the fixture's region, in map order. */
  private def cradleCards(ready: ReadyGame): Vector[CardId] =
    val map = ready.game.current.map
    map.inPlay.filter(map.regionOf(_).contains(Region.Cradle))
      .flatMap(map.sites(_).denizens.map(_.id))

  test("Empty Grounds discards denizens at a site an Ancient Bloodline " +
      "holder rules, since nobody moves them"):
    // p1, not the first player, rules Ancient City and holds Bloodline.
    val staged = Table.from(withOtherDenizens).warbandsAt(site, Table.p1, 1)
      .adviser(Table.p1, CatalogNames.denizen("Ancient Bloodline"))
      .unchecked
    assertEquals(cradleCards(finish(staged)), Vector[CardId](edifice))

  test("Empty Grounds discards denizens at a site the Hall of Ministers' " +
      "ruler rules, since nobody discards them"):
    val hall = CatalogNames.edifice("Hall of Ministers")
    val staged = Table.from(withOtherDenizens).warbandsAt(site, Table.p1, 1)
      .edifice(hall, EdificeSide.Intact, at = site).unchecked
    assertEquals(cradleCards(finish(staged)), Vector[CardId](edifice, hall))

  // ---- Lines ----

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
