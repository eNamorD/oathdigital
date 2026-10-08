package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap.accepted

class SpoiledSuppliesSuite extends munit.FunSuite:
  import PowerFixture._
  import PhasePowerHarness._

  private val spoiled = CatalogNames.denizen("Spoiled Supplies")
  private val source = DecisionOptionRef.Denizen(spoiled)
  private val first = Table.p2
  private val second = Table.p3

  private def supplyOf(ready: ReadyGame, id: PlayerId = actor) =
    Look(ready).player(id).board.supply.supply

  /** p1's Act at Ancient City with Spoiled Supplies at the site and `favor`
    * favor. `first` (p2) stands at Ancient City with `supply`; `second` (p3)
    * stands at Buried Giant with 3 Supply, so it is never hit.
    */
  private def staged(supply: Int = 3, favor: Int = 1) =
    Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .denizen(spoiled, at = "Ancient City").favor(actor, favor)
      .pawn(first, "Ancient City").supply(first, supply)
      .pawn(second, "Buried Giant").supply(second, 3).ready

  test("each other player at the actor's site loses 1 Supply, and nobody " +
      "else does"):
    val ready = staged()
    val done = accepted(use(ready, SpoiledSupplies, source))
    parked.assertNotParked(done.state)
    assertEquals(supplyOf(after(done), first), 2)
    assertEquals(supplyOf(after(done), second), 3)
    assertEquals(supplyOf(after(done)), supplyOf(ready))
    assertEquals(Look(after(done)).tokensOn(spoiled), Tokens(1, 0))
    assertEquals(Look(after(done)).player(actor).board.favor, 0)
    assertEquals(replayed(ready, done.events), Right(done.state))
    assertWireRoundTrips(done.events)

  test("it writes the Supply each player lost"):
    val done = accepted(use(staged(), SpoiledSupplies, source))
    assertEquals(NoteText.said(SpoiledSupplies, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${first.value} lost 1 Supply.",
        covers = false)))

  test("two players at the site each lose 1 Supply, each with a line"):
    val ready = Table.from(staged()).pawn(second, "Ancient City").unchecked
    val done = accepted(use(ready, SpoiledSupplies, source))
    assertEquals(supplyOf(after(done), first), 2)
    assertEquals(supplyOf(after(done), second), 2)
    assertEquals(NoteText.said(SpoiledSupplies, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${first.value} lost 1 Supply.",
        covers = false),
      NoteText.Said(NoteKey.Used, s"${second.value} lost 1 Supply.",
        covers = false)))

  test("a player with no Supply loses none, and the line says nobody did"):
    val done = accepted(use(staged(supply = 0), SpoiledSupplies, source))
    assertEquals(supplyOf(after(done), first), 0)
    assertEquals(Look(after(done)).tokensOn(spoiled), Tokens(1, 0))
    assertEquals(NoteText.said(SpoiledSupplies, done.events), Vector(
      NoteText.Said("used.none", "No enemy lost Supply.", covers = false)))

  test("it is unusable without a favor to place"):
    val ready = staged(favor = 0)
    assert(!usableNow(ready).exists(_.power.id == SpoiledSupplies.id))
    assertEquals(use(ready, SpoiledSupplies, source).left.toOption,
      Some(OathViolation.InsufficientFavor(1, 0)))
