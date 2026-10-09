package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{AdviserArrival, AdviserArrivals,
  ArrivalFollowUp, ContributingPower, Contribution}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}

/** The adviser arrival pass (catalog batch 5, "The arrival pass"), driven by
  * stub rules over one leaf. */
class AdviserArrivalPassSuite extends munit.FunSuite:
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val elders = CatalogNames.denizen("Elders")
  private val rainBoots = CatalogNames.denizen("Rain Boots")
  /** "{cards}": the arrivals a stub heard. */
  private val heard = NoteKey("heard", Vector(NotePart.Arg(0)))

  /** Stub of ContributingPower: hooks no window. Whenever advisers arrive,
    * it follows up with `follow(arrived)` and notes the arrivals it heard.
    */
  private final case class HearsArrivals(name: String,
      follow: AdviserArrivals => Vector[CoreOperation])
      extends ContributingPower:
    def id: PowerId = PowerId(name)
    def source: RuleSourceRef = RuleSourceRef.GameRule(name)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def onArrival(arrived: AdviserArrivals)
        : Option[ArrivalFollowUp] =
      val cards = arrived.arrivals.map(_.card)
      Some(ArrivalFollowUp(follow(arrived), Note(id, _ => Some(heard(
        PowerSourceRef.Card(wrestlers), NoteArg.Cards(cards))))))

  /** Hears arrivals and does nothing more. */
  private val hears = HearsArrivals("test.hears", _ => Vector.empty)

  /** Buries each denizen that arrived in p1's advisers. */
  private val buriesP1Arrivals = HearsArrivals("test.bury-p1", arrived =>
    arrived.arrivals.collect {
      case AdviserArrival(`p1`, card: DenizenId) => Bury(
        BuryableCard.Denizen(card), PositionedLocation(Location.PlayArea(p1)))
    })

  /** Each time it is asked, gives p1 the first adviser p2 still holds. */
  private val passesP2AdviserOn = HearsArrivals("test.pass-on", arrived =>
    arrived.after.game.current.players.find(_.player == p2).toVector
      .flatMap(_.advisers.headOption).map(next => Give(Piece.Card(next.id),
        p2, Location.PlayArea(p2), Location.PlayArea(p1), required = true)))

  private def givesToP1(card: DenizenId): CoreOperation = Give(
    Piece.Card(card), p2, Location.PlayArea(p2), Location.PlayArea(p1),
    required = true)

  /** `ops` walked as the one leaf of a sequence, under `power`. */
  private def walked(ready: ReadyGame, power: ContributingPower,
      ops: Vector[CoreOperation]): WalkerOutcome.Finished =
    Outcomes.finished(ProcedureWalker.advance(ready,
      Sequence(Vector(BuildOps((_, _) => Right(ops), required = true))),
      None, WalkerPowers(Vector(power))))

  /** The arguments of every line `power` journaled in `events`. */
  private def heardBy(power: ContributingPower, events: Vector[OathEvent])
      : Vector[NoteArg] = events.collect {
    case PowerNoted(id, note, _) if id == power.id => note.args }.flatten

  test("an adviser new to a player is an arrival; the rule's follow-up runs " +
      "as its own step at the leaf's node, credited to it, with its line " +
      "after it, and replays"):
    val ready = Table.start.adviser(p2, wrestlers).ready
    val done = walked(ready, buriesP1Arrivals, Vector(givesToP1(wrestlers)))
    assertEquals(Look(done.treeless).advisers(p1), Vector.empty)
    assertEquals(done.treeless.game.current.commonCards.worldDeck.lastOption,
      Some(wrestlers))
    val steps = done.events.collect { case step: WalkerStepRecorded => step }
    assertEquals(steps.map(_.nodeId), Vector("0", "0"))
    assertEquals(steps.map(_.contributions),
      Vector(Vector.empty, Vector(buriesP1Arrivals.id)))
    assert(done.events.lastOption.exists(_.isInstanceOf[PowerNoted]),
      done.events.toString)
    assertEquals(heardBy(buriesP1Arrivals, done.events),
      Vector(NoteArg.Cards(Vector(wrestlers))))
    val replayed = done.events.collect { case event: WalkerEvent => event }
      .foldLeft[Either[OathViolation, OathState]](
        Right(OathState.Ready(ready)))((state, event) =>
        state.flatMap(ProcedureWalker.applyRecorded(_, event)))
    assertEquals(replayed.map {
      case OathState.Ready(game) => game.game.current
      case other => fail(s"expected a ready game, got $other")
    }, Right(done.treeless.game.current))

  test("an adviser turned faceup is an arrival; one that stays faceup is " +
      "not"):
    val ready = Table.start.adviser(p1, wrestlers, facedown = true)
      .adviser(p1, elders).ready
    // Card play turns a facedown adviser faceup with this `Play`.
    val turned = Play(wrestlers, PositionedLocation(Location.PlayArea(p1)),
      Location.PlayArea(p1), Orientation.FaceUp, required = true)
    val done = walked(ready, hears, Vector(turned))
    assertEquals(heardBy(hears, done.events),
      Vector(NoteArg.Cards(Vector(wrestlers))))

  test("a leaf in which no adviser arrives asks no rule"):
    val ready = Table.start.adviser(p1, wrestlers).supply(p1, 2).ready
    val done = walked(ready, hears, Vector(GainSupply(p1, 1)))
    assertEquals(heardBy(hears, done.events), Vector.empty)
    assertEquals(done.events.count(_.isInstanceOf[WalkerStepRecorded]), 1)

  test("a follow-up's own arrivals start no second pass"):
    val ready = Table.start.adviser(p2, wrestlers).adviser(p2, elders)
      .adviser(p2, rainBoots).ready
    val done = walked(ready, passesP2AdviserOn, Vector(givesToP1(wrestlers)))
    // The leaf gave Wrestlers; the one follow-up gave Elders. A second pass
    // would have given Rain Boots too.
    assertEquals(Look(done.treeless).advisers(p1),
      Vector[CardId](wrestlers, elders))
    assertEquals(Look(done.treeless).advisers(p2), Vector[CardId](rainBoots))
    assertEquals(heardBy(passesP2AdviserOn, done.events),
      Vector(NoteArg.Cards(Vector(wrestlers))))
