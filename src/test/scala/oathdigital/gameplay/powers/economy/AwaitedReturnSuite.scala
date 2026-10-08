package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class AwaitedReturnSuite extends munit.FunSuite:
  private val awaited = CatalogNames.denizen("Awaited Return")
  private val power = AwaitedReturn.forCatalog(catalog)
  private val modifiers = Vector(AwaitedReturn.id)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Order denizen to Trade with. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City beside Wrestlers, holding Awaited Return faceup,
    * `supply` Supply and the start's 3 warbands, 1 favor and 1 faceup secret.
    * The Order bank holds 3 favor. */
  private def holdingAwaitedReturn(supply: Int = 7): Table = Table.start
    .denizen(wrestlers, at = home).adviser(p1, awaited).supply(p1, supply)
    .bankFavor(Suit.Order, 3)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("selected, the Trade spends no Supply and sacrifices one warband " +
      "from the board"):
    val ready = holdingAwaitedReturn().ready
    val done = EconomyActions.trade(rules, ready, p1, modifiers, "favor",
      DecisionOptionRef.Denizen(wrestlers))
    val end = Look(done.result)
    assertEquals(end.supply(p1), 7)
    assertEquals(end.boardWarbands(p1), Look(ready).boardWarbands(p1) - 1)
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 1)
    assertEquals(said(done.journal.events), Vector(NoteText.Said("sacrificed",
      "Spent no Supply after sacrificing 1 warband.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("it lets a player with no Supply trade"):
    val noSupply = holdingAwaitedReturn(supply = 0).ready
    val wrestlersCard = DecisionOptionRef.Denizen(wrestlers)
    assertEquals(EconomyActions.attemptTrade(rules, noSupply, p1, Vector.empty,
      "favor", wrestlersCard).left.toOption,
      Some(OathViolation.CoreOperationRejected("insufficient-supply",
        "a supply spend of 1 exceeds the 0 available")))
    val done = EconomyActions.trade(rules, noSupply, p1, modifiers, "favor",
      wrestlersCard)
    assertEquals(Look(done.result).supply(p1), 0)

  test("it is not offered with no warband on the board"):
    val offered = (ready: ReadyGame) =>
      accepted(rules.offerableWalkerPowers(ready, p1, ActionRef.Trade))
        .map(_.id)
    assert(offered(holdingAwaitedReturn().ready).contains(AwaitedReturn.id))
    assert(!offered(holdingAwaitedReturn().boardWarbands(p1, 0).ready)
      .contains(AwaitedReturn.id))
