package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class DemonTailSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val tail = RelicId("R46")
  private val source = DecisionOptionRef.Relic(tail)

  private def staged(supply: Int = 2, faceUp: Int = 3): ReadyGame =
    act(withBoard(withRelic(base, tail))(_.copy(supply = SupplyTrack(supply),
      faceUpSecrets = faceUp, faceDownSecrets = 0)))

  test("Demon Tail is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(DemonTail.id).isDefined)

  test("it burns three secrets and gains 2 Supply"):
    val ready0 = staged()
    val done = use(rules(), ready0, DemonTail.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(secrets(end), 0)
    // Burnt secrets go to the shared bank, not onto the relic.
    assertEquals(tokensOn(end, tail), Tokens.empty)
    assertEquals(player(end).board.supply, SupplyTrack(4))
    assertEquals(replayed(rules(), ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("the gain is clamped at the track maximum, and the line says so"):
    val done = use(rules(), staged(supply = SupplyTrack.Maximum - 1),
      DemonTail.id, source).toOption.get
    assertEquals(player(ready(done.state)).board.supply, SupplyTrack.full)
    assertEquals(NoteText.said(DemonTail, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 1 Supply.", covers = false)))

  test("two secrets are not enough"):
    val broke = staged(faceUp = 2)
    assert(!usableIds(broke).contains(DemonTail.id))
    assert(use(rules(), broke, DemonTail.id, source).isLeft)

  test("its line reads the Supply gained"):
    val done = use(rules(), staged(), DemonTail.id, source).toOption.get
    assertEquals(NoteText.said(DemonTail, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"${actor.value} gained 2 Supply.", covers = false)))
