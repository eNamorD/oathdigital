package oathdigital.gameplay.powers.setup

import oathdigital.gameplay.powers.{NoteText, WalkerPowerCatalog}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure, SetupWalkDriver}
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Unwrap.accepted

class GreatMarketRulesSuite extends munit.FunSuite:
  private val catalog = FirstGameSetupFixture.catalog
  private val edifice = CatalogNames.edifice("Great Market")
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)
  private val site = CatalogNames.site("Ancient City")
  private val banditSite = CatalogNames.site("Broken Peaks")
  private val marketSuit = catalog.suitOf(edifice).get

  // The Setup walk is the subject (the edifice's effect runs when the walk
  // reaches it), so these tests start from the fresh Setup, not a Table.
  private def stagedAt(side: EdificeSide): ReadyGame =
    Table.from(FirstGameSetupFixture.freshReady)
      .edifice(edifice, side, at = site).unchecked

  /** Bandits rule exactly `bandited`, one each; every other site is empty.
    * The fresh deal fills every site with bandits to its capacity. */
  private def banditsOnlyAt(ready: ReadyGame, bandited: SiteId*): ReadyGame =
    FirstGameSetupFixture.sites.foldLeft(Table.from(ready))((table, s) =>
      table.bandits(s, if bandited.contains(s) then 1 else 0)).unchecked

  private def finish(ready: ReadyGame): ReadyGame =
    val tree = accepted(SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty))
    SetupWalkDriver.driveToCompletion(ready, tree, TriggeredProcedureRef.Setup,
      powers)

  private def events(ready: ReadyGame): Vector[OathEvent] =
    val tree = accepted(SetupProcedure.build(catalog, ready,
      ready.game.current.turn.activePlayer, Vector.empty))
    SetupWalkDriver.driveWithEvents(ready, tree, TriggeredProcedureRef.Setup,
      powers).events

  private val great = GreatMarket.forCatalog(catalog)

  private val bandit = BanditMarket.forCatalog(catalog)

  test("Great Market places one favor per denizen in its region, itself included"):
    val staged = stagedAt(EdificeSide.Intact)
    val region = Region.Cradle // Ancient City's region
    val expectedCount = staged.game.current.map.inPlay
      .filter(s => staged.game.current.map.regionOf(s).contains(region))
      .flatMap(s => staged.game.current.map.sites(s).denizens)
      .size
    val finished = finish(staged)
    assertEquals(finished.game.current.map.sites(site).tokens.favor, expectedCount)

  test("Bandit Market favors every bandit-ruled site and burns every bank"):
    // One bandit site keeps the arithmetic below unambiguous.
    val withBandits = banditsOnlyAt(stagedAt(EdificeSide.Ruined), banditSite)
    val startingBank = withBandits.banks.favor
    val finished = finish(withBandits)
    assertEquals(finished.game.current.map.sites(banditSite).tokens.favor, 1)
    // Every bank burns 1; the market's own bank also paid the bandit site's 1.
    Suit.all.foreach(suit => assertEquals(finished.banks.favor(suit),
      startingBank(suit) - (if suit == marketSuit then 2 else 1)))

  // ---- Lines ----

  test("Great Market writes the favor it placed, and where"):
    val staged = stagedAt(EdificeSide.Intact)
    val region = Region.Cradle // Ancient City's region
    val count = staged.game.current.map.inPlay
      .filter(s => staged.game.current.map.regionOf(s).contains(region))
      .flatMap(s => staged.game.current.map.sites(s).denizens).size
    assertEquals(NoteText.said(great.id, great.noteKeys, events(staged)),
      Vector(NoteText.Said("placed",
        s"Placed $count favor on ${site.value}.", covers = false)))

  test("Bandit Market writes the favor it placed and the favor it burned"):
    val staged = banditsOnlyAt(stagedAt(EdificeSide.Ruined), banditSite)
    assertEquals(NoteText.said(bandit.id, bandit.noteKeys, events(staged)),
      Vector(NoteText.Said("placed-and-burned",
        s"Placed 1 favor on the bandit site and burned ${Suit.all.size} " +
          "favor from the banks.", covers = false)))

  test("Bandit Market with no bandit site writes only the burn"):
    val staged = banditsOnlyAt(stagedAt(EdificeSide.Ruined))
    assertEquals(NoteText.said(bandit.id, bandit.noteKeys, events(staged)),
      Vector(NoteText.Said("burned",
        s"Burned ${Suit.all.size} favor from the banks.", covers = false)))

  test("Bandit Market that places the last favor in the banks writes only the placing"):
    val staged = banditsOnlyAt(stagedAt(EdificeSide.Ruined), banditSite)
    // One favor in the market's own bank and none elsewhere: the site takes
    // it, and the burn finds nothing left.
    val lean = Suit.all.foldLeft(Table.from(staged))((table, suit) =>
      table.bankFavor(suit, if suit == marketSuit then 1 else 0)).unchecked
    assertEquals(NoteText.said(bandit.id, bandit.noteKeys, events(lean)),
      Vector(NoteText.Said("placed", "Placed 1 favor on the bandit site.",
        covers = false)))
