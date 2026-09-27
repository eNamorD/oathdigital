package oathdigital.gameplay.powers.action

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PowerFixture,
  TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerDice}
import oathdigital.model._

class ClayRattleSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val parked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val rattle = RelicId("R47")
  private val source = DecisionOptionRef.Relic(rattle)
  private val cradle = SearchSource.RegionalDiscard(Region.Cradle)

  /** Reverses every pile it shuffles, and never rolls. */
  private val reversing: WalkerDice = new WalkerDice:
    def roll(kind: DiceKind, count: Int)
        : Either[OathViolation, Vector[DieFace]] =
      Left(OathViolation.InvalidEventOrder("Clay Rattle never rolls"))
    override def shuffle(count: Int): Either[OathViolation, Vector[Int]] =
      Right((0 until count).reverse.toVector)
  private val shuffling = rules(reversing)

  /** The actor holds Clay Rattle and `faceUp` faceup secrets. The Cradle
    * discard pile holds the world deck's top three cards, and the
    * Hinterland's is empty.
    */
  private def staged(faceUp: Int = 2): ReadyGame =
    act(withBoard(withRelic(base, rattle))(_.copy(faceUpSecrets = faceUp)))
      .updateCurrent(c => c.copy(commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck.drop(3),
        regionalDiscards = c.commonCards.regionalDiscards
          .updated(Region.Cradle, c.commonCards.worldDeck.take(3))
          .updated(Region.Hinterland, Vector.empty))))
  private def zones(ready: ReadyGame): CardZones = ready.game.current.commonCards

  /** Clay Rattle is used under `under` and `pile` is chosen. */
  private def shuffled(pile: SearchSource, under: OathRules = shuffling) =
    val ready0 = staged()
    val started = use(under, ready0, ClayRattle.id, source).toOption.get
    (ready0, started, answer(under, started.state, ClayRattle.decisionId,
      ClayRattle.ref(pile)))

  test("Clay Rattle is a registered phase power"):
    assert(PhasePowerCatalog.default(catalog).find(ClayRattle.id).isDefined)

  test("it places two secrets and asks which pile to shuffle"):
    val started = use(shuffling, staged(), ClayRattle.id, source).toOption.get
    assertEquals(parked.parkedDecision(started.state).map(_.decision),
      Some(ClayRattle.decisionId))
    assertEquals(tokensOn(ready(started.state), rattle), Tokens(0, 2))
    assertEquals(TargetsFixture.offered(started, actor),
      Some(ClayRattle.piles.map(ClayRattle.ref).map(r => r.kind -> r.wireId)))
    assertEquals(ClayRattle.piles.size, 4)

  test("the world deck takes the source's order, and replay applies it"):
    val (ready0, started, answered) = shuffled(SearchSource.WorldDeck)
    val done = answered.toOption.get
    val end = ready(done.state)
    assertEquals(zones(end).worldDeck, zones(ready0).worldDeck.reverse)
    assertEquals(zones(end).discard(Region.Cradle),
      zones(ready0).discard(Region.Cradle))
    parked.assertResumed(done.state, Phase.Act, actor)
    // Replay has no shuffle source: it applies the recorded order.
    assertEquals(replayed(rules(), ready0, started.events ++ done.events), end)
    assert(wireRoundTrips(started.events ++ done.events))

  test("a region's discard pile is shuffled, and its line names it"):
    val (ready0, started, answered) = shuffled(cradle)
    val done = answered.toOption.get
    assertEquals(zones(ready(done.state)).discard(Region.Cradle),
      zones(ready0).discard(Region.Cradle).reverse)
    assertEquals(zones(ready(done.state)).worldDeck, zones(ready0).worldDeck)
    assertEquals(NoteText.said(ClayRattle, started.events ++ done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} shuffled the Cradle discard pile.", covers = false)))

  test("an empty pile is shuffled without asking the source"):
    val hinterland = SearchSource.RegionalDiscard(Region.Hinterland)
    val (_, started, answered) = shuffled(hinterland, under = rules())
    val done = answered.toOption.get
    assertEquals(zones(ready(done.state)).discard(Region.Hinterland),
      Vector.empty)
    assertEquals(NoteText.said(ClayRattle, started.events ++ done.events),
      Vector(NoteText.Said(NoteKey.Used,
        s"${actor.value} shuffled the Hinterland discard pile.",
        covers = false)))

  test("a walker with no shuffle source refuses a pile of two or more"):
    val (_, _, answered) = shuffled(SearchSource.WorldDeck, under = rules())
    assert(answered.isLeft)

  test("a source whose order is not a permutation of the pile is refused"):
    val repeating: WalkerDice = new WalkerDice:
      def roll(kind: DiceKind, count: Int)
          : Either[OathViolation, Vector[DieFace]] =
        Left(OathViolation.InvalidEventOrder("Clay Rattle never rolls"))
      override def shuffle(count: Int): Either[OathViolation, Vector[Int]] =
        Right(Vector.fill(count)(0))
    val (_, _, answered) = shuffled(cradle, under = rules(repeating))
    assert(answered.isLeft)

  test("one faceup secret is not enough"):
    val broke = staged(faceUp = 1)
    assert(!usableIds(broke).contains(ClayRattle.id))
    assert(use(shuffling, broke, ClayRattle.id, source).isLeft)
