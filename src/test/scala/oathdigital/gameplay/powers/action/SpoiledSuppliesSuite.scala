package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class SpoiledSuppliesSuite extends munit.FunSuite:
  import PowerFixture._
  import TargetsFixture._

  private val spoiled = DenizenId("228")
  private val source = DecisionOptionRef.Denizen(spoiled)
  private val first = others(base)(0)
  private val second = others(base)(1)

  private def elsewhere(ready: ReadyGame): SiteId =
    ready.game.current.map.inPlay.find(_ != home(ready)).get
  private def withSupply(ready: ReadyGame, id: PlayerId, amount: Int) =
    updatePlayer(ready, id)(p => p.copy(board =
      p.board.copy(supply = SupplyTrack(amount))))
  private def supplyOf(ready: ReadyGame, id: PlayerId = actor) =
    player(ready, id).board.supply.supply
  private def cardOf(ready: ReadyGame) = ready.game.current.map
    .sites(home(ready)).denizens.collectFirst {
      case d: DenizenState if d.id == spoiled => d }.get

  /** Spoiled Supplies at the actor's site, with `favor` on the actor's
    * board. `first` stands at the actor's site with `supply`; `second`
    * stands elsewhere with 3 Supply, so it is never hit.
    */
  private def staged(supply: Int = 3, favor: Int = 1) =
    val actorReady = inPhase(withBoard(atHome(base, spoiled))(
      _.copy(favor = favor)), Phase.Act)
    val placed = withPawn(withPawn(actorReady, first, home(actorReady)),
      second, elsewhere(actorReady))
    withSupply(withSupply(placed, first, supply), second, 3)

  test("Spoiled Supplies is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(SpoiledSupplies.id).isDefined)

  test("each other player at the actor's site loses 1 Supply, and nobody " +
      "else does"):
    val ready = staged()
    val done = use(ready, SpoiledSupplies, source).toOption.get
    parked.assertNotParked(done.state)
    assertEquals(supplyOf(after(done), first), 2)
    assertEquals(supplyOf(after(done), second), 3)
    assertEquals(supplyOf(after(done)), supplyOf(ready))
    assertEquals(cardOf(after(done)).tokens, Tokens(1, 0))
    assertEquals(player(after(done)).board.favor, 0)
    assertEquals(replayed(ready, done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(done.events))

  test("it writes the Supply each player lost"):
    val done = use(staged(), SpoiledSupplies, source).toOption.get
    assertEquals(NoteText.said(SpoiledSupplies, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${first.value} lost 1 Supply.",
        covers = false)))

  test("two players at the site each lose 1 Supply, each with a line"):
    val ready = withPawn(staged(), second, home(staged()))
    val done = use(ready, SpoiledSupplies, source).toOption.get
    assertEquals(supplyOf(after(done), first), 2)
    assertEquals(supplyOf(after(done), second), 2)
    assertEquals(NoteText.said(SpoiledSupplies, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${first.value} lost 1 Supply.",
        covers = false),
      NoteText.Said(NoteKey.Used, s"${second.value} lost 1 Supply.",
        covers = false)))

  test("a player with no Supply loses none, and the line says nobody did"):
    val done = use(staged(supply = 0), SpoiledSupplies, source).toOption.get
    assertEquals(supplyOf(after(done), first), 0)
    assertEquals(cardOf(after(done)).tokens, Tokens(1, 0))
    assertEquals(NoteText.said(SpoiledSupplies, done.events), Vector(
      NoteText.Said("used.none", "No enemy lost Supply.", covers = false)))

  test("it is unusable without a favor to place"):
    val ready = staged(favor = 0)
    assert(!usableNow(ready).exists(_.power.id == SpoiledSupplies.id))
    assert(use(ready, SpoiledSupplies, source).isLeft)
