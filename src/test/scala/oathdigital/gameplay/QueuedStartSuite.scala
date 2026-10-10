package oathdigital.gameplay

import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{QueuedStart, WalkerPowers}
import oathdigital.gameplay.walker.QueuedStart.StartCost
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

/** What a start costs, or why it would be refused, through the production
  * registry (walker-driven game loop design, slice 4, plan-time rulings 2
  * and 3). */
class QueuedStartSuite extends munit.FunSuite:
  private val walkerPowers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)
  private val phasePowers = PhasePowerCatalog.default(catalog)
  // Fair Isle has a Recover difficulty.
  private val fairIsle = CatalogNames.site("Fair Isle")
  private val relic = CatalogNames.relic("Dowsing Sticks")

  /** p1's Act at Fair Isle, with `supply` Supply and a world deck to search,
    * and Dowsing Sticks facedown there when `lying` says so. */
  private def acting(supply: Int, lying: Boolean = true): ReadyGame =
    val seated = Table.start.pawn(p1, at = fairIsle)
      .pawn(p2, at = "Broken Peaks").pawn(p3, at = "Buried Giant")
      .supply(p1, supply).worldDeckTop("Threatening Roar", "Fae Merchant",
        "Second Chance")
    (if lying then seated.relicAt(relic, at = fairIsle) else seated).ready

  private def entry(procedure: StartableRef): QueuedProcedure =
    QueuedProcedure(p1, procedure, Vector.empty, None, QueueKind.Run)

  private def priced(procedure: StartableRef, supply: Int = 7) =
    QueuedStart.priced(catalog, acting(supply), entry(procedure),
      walkerPowers, phasePowers)

  test("Search costs its one source's Supply"):
    // Every discard pile is empty, so the world deck is the only source.
    assertEquals(priced(ActionRef.Search), Right(StartCost(2, 0)))
    assertEquals(priced(ActionRef.Search).map(_.words), Right(Some(
      "2 Supply")))

  test("Recover is priced from what its first walk spends"):
    assertEquals(priced(ActionRef.Recover).map(_.words), Right(Some(
      "1 Supply")))

  test("Travel's cost depends on the destination"):
    val words = priced(ActionRef.Travel).toOption.flatMap(_.words)
    assert(words.exists(_.matches("\\d+\\+ Supply")),
      priced(ActionRef.Travel).toString)

  test("a free start has no words"):
    assertEquals(priced(ActionRef.PeekSiteRelics).map(_.words), Right(None))

  test("a Supply shortfall is worded with the amounts"):
    assert(priced(ActionRef.Search, supply = 1).isLeft)
    assertEquals(QueuedStart.needs(catalog, acting(1), entry(
      ActionRef.Search), walkerPowers, phasePowers),
      Some("Needs 2 Supply; you have 1"))

  test("a refusal not about Supply has no shortfall"):
    val bare = acting(7, lying = false)
    // The precondition: nothing lies at Fair Isle.
    assertEquals(Look(bare).relicsAt(fairIsle), Vector.empty)
    assertEquals(QueuedStart.needs(catalog, bare, entry(
      ActionRef.PeekSiteRelics), walkerPowers, phasePowers), None)
