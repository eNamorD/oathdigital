package oathdigital.gameplay.powers.action

import oathdigital.gameplay.{OathRules, WalkerDiceFixture}
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerDice}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.accepted

class ClayRattleSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val parked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val rattle = CatalogNames.relic("Clay Rattle")
  private val source = DecisionOptionRef.Relic(rattle)
  private val cradle = SearchSource.RegionalDiscard(Region.Cradle)

  /** Stub of WalkerDice: shuffle reverses every pile, roll fails. */
  private val reversing: WalkerDice =
    WalkerDiceFixture.shufflingBy(count => (0 until count).reverse.toVector)
  private val shuffling = rules(reversing)

  /** p1's Act holding Clay Rattle and `faceUp` faceup secrets. The Cradle
    * discard pile holds three named cards, Second Chance on top; the
    * Hinterland's is empty, as on the quiet table.
    */
  private def staged(faceUp: Int = 2): ReadyGame =
    Table.start.turn(actor, Phase.Act).relic(actor, rattle)
      .secrets(actor, faceUp)
      .discarded(Region.Cradle, "Threatening Roar", "Fae Merchant",
        "Second Chance").ready
  private def zones(ready: ReadyGame): CardZones = ready.game.current.commonCards

  /** The state Clay Rattle was used from, the walk it started, and the
    * rules' answer once `pile` was chosen. */
  private case class Shuffled(before: ReadyGame, started: OathTransition,
      answered: Either[OathViolation, OathTransition])

  /** Clay Rattle is used under `under` and `pile` is chosen. */
  private def shuffled(pile: SearchSource, under: OathRules = shuffling)
      : Shuffled =
    val before = staged()
    val started = accepted(use(under, before, ClayRattle.id, source))
    Shuffled(before, started, answer(under, started.state,
      ClayRattle.decisionId, ClayRattle.ref(pile)))

  test("it places two secrets and asks which pile to shuffle"):
    val started = accepted(use(shuffling, staged(), ClayRattle.id, source))
    assertEquals(parked.parkedDecision(started.state).map(_.decision),
      Some(ClayRattle.decisionId))
    assertEquals(Look(ready(started.state)).tokensOn(rattle), Tokens(0, 2))
    assertEquals(PhasePowerHarness.offered(started, actor),
      Some(ClayRattle.piles.map(ClayRattle.ref).map(r => r.kind -> r.wireId)))
    assertEquals(ClayRattle.piles.size, 4)

  test("the world deck takes the source's order, and replay applies it"):
    val Shuffled(ready0, started, answered) = shuffled(SearchSource.WorldDeck)
    val done = accepted(answered)
    val end = ready(done.state)
    assertEquals(zones(end).worldDeck, zones(ready0).worldDeck.reverse)
    assertEquals(zones(end).discard(Region.Cradle),
      zones(ready0).discard(Region.Cradle))
    parked.assertResumed(done.state, Phase.Act, actor)
    // Replay has no shuffle source: it applies the recorded order.
    assertEquals(replayed(rules(), ready0, started.events ++ done.events), end)
    assertWireRoundTrips(started.events ++ done.events)
    assertEquals(NoteText.said(ClayRattle, started.events ++ done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} shuffled the world deck.", covers = false)))

  test("a region's discard pile is shuffled, and its line names it"):
    val Shuffled(ready0, started, answered) = shuffled(cradle)
    val done = accepted(answered)
    assertEquals(zones(ready(done.state)).discard(Region.Cradle),
      zones(ready0).discard(Region.Cradle).reverse)
    assertEquals(zones(ready(done.state)).worldDeck, zones(ready0).worldDeck)
    assertEquals(NoteText.said(ClayRattle, started.events ++ done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} shuffled the Cradle discard pile.", covers = false)))

  test("an empty pile is shuffled without asking the source"):
    val hinterland = SearchSource.RegionalDiscard(Region.Hinterland)
    val Shuffled(_, started, answered) = shuffled(hinterland, under = rules())
    val done = accepted(answered)
    assertEquals(zones(ready(done.state)).discard(Region.Hinterland),
      Vector.empty)
    assertEquals(NoteText.said(ClayRattle, started.events ++ done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} shuffled the Hinterland discard pile.",
        covers = false)))

  test("a walker with no shuffle source refuses a pile of two or more"):
    val answered = shuffled(SearchSource.WorldDeck, under = rules()).answered
    assert(answered.isLeft)

  test("a source whose order is not a permutation of the pile is refused"):
    // Stub of WalkerDice: shuffle returns a non-permutation (all zeros).
    val repeating: WalkerDice =
      WalkerDiceFixture.shufflingBy(count => Vector.fill(count)(0))
    val answered = shuffled(cradle, under = rules(repeating)).answered
    assert(answered.isLeft)

  test("one faceup secret is not enough"):
    val broke = staged(faceUp = 1)
    assert(!usableIds(broke).contains(ClayRattle.id))
    assert(use(shuffling, broke, ClayRattle.id, source).isLeft)
