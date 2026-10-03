package oathdigital.application

import oathdigital.model._
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.testkit.{CatalogNames, Table, TestCards}
import oathdigital.testkit.Table.p1

/** The board a walker Forge is driven from, shared by every suite that needs
  * one.
  *
  * It was `GameApplicationServiceSuite`'s private fixture until
  * [[WalkerDecisionProjectionSuite]] needed a parked Forge too. It was
  * once reached by real commands (a conquest, Searches, a Rest round); it is
  * now stated as a [[Table]], so a change to Setup or to those actions no
  * longer moves the Forge's board.
  */
object ForgeWalkerFixture:

  /** Ancient City: the one non-homeland site with a printed Forge cost
    * (three favor). Every homeland site restricts which denizens may be
    * played there, and the Forge needs three. */
  lazy val forgeSite: SiteId =
    val ancientCity = CatalogNames.site("Ancient City")
    require(catalog.sites.filter(site => site.forgeRequirements.nonEmpty &&
      site.homeland.isEmpty).map(_.id) == Vector(ancientCity),
      "Ancient City must be the one non-homeland forgeable site")
    ancientCity

  /** p1 stands at the Forge site and rules it with one warband (the other
    * two stay on the board). The site holds three faceup denizens with no
    * tokens, which are the Forge's targets. p1 has 4 favor, enough for the
    * printed cost of three favor, and 1 secret, enough for the mixed cost
    * of two favor and one secret. Dowsing Sticks tops the relic deck, so the
    * forged relic is known. Supply is the printed 7 and needs one. p1
    * already holds the Oathkeeper title, and bandits fill the empty sites,
    * so neither the title nor the refill moves in the Forge's command. */
  lazy val forgeTable: Table = Table.start
    .pawn(p1, forgeSite)
    .warbandsAt(forgeSite, p1, 1).boardWarbands(p1, 2)
    .denizen("Threatening Roar", forgeSite)
    .denizen("Mushrooms", forgeSite)
    .denizen("Mercenaries", forgeSite)
    .favor(p1, 4)
    .relicDeckTop("Dowsing Sticks")
    .oathkeeper(Some(p1))
    .banditsRefilled

  /** The shipped catalog with the one non-homeland forgeable site's printed
    * cost rewritten to name both resources, so a Forge there parks.
    *
    * The shipped catalog prints three favor at that site. Under the
    * forced-decision rule a query whose section demands every option has
    * exactly one legal answer and must not be asked, so `ForgeProcedure`
    * declares no decision node there at all. This override is what gives
    * the PARKED path a real board to be tested against.
    */
  lazy val mixedForgeCostCatalog: oathdigital.catalog.ExecutableCatalog =
    catalog.copy(sites = catalog.sites.map(site =>
      if site.id != forgeSite then site
      else TestCards.siteLike(site)(forgeRequirements = Some(Tokens(2, 1)))))

  /** A fresh service begun at [[forgeTable]] under `cat`, and its
    * repository. The stream is empty, so the Forge starts at sequence 0. */
  def forgeService(cat: oathdigital.catalog.ExecutableCatalog = catalog)
      : (GameApplicationService, InMemoryEventStreamRepository) =
    forgeTable.service(catalog = cat)

  /** A parked Forge: the catalog it ran under (the assertions need its
    * printed cost) and the parked position. p1 forges at [[forgeSite]]. */
  final case class ParkedForge(catalog: oathdigital.catalog.ExecutableCatalog,
      started: GameAccepted)

  /** The position a mixed-cost Forge PARKS from: the fixture board above,
    * under [[mixedForgeCostCatalog]], after the `StartWalker` that spends
    * Supply and stops at the assignment decision.
    */
  def parkedForge(gameId: String): ParkedForge =
    val forgeCatalog = mixedForgeCostCatalog
    val (service, _) = forgeService(forgeCatalog)
    val started = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Forge, StartPayload(p1)))
      .fold(error => throw AssertionError(s"Forge refused: $error"), identity)
    ParkedForge(forgeCatalog, started)
