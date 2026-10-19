package oathdigital.gameplay.walker

import oathdigital.gameplay.powerresolver.{BurnTake, Burnt,
  ContributingPower, Contribution}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class BurnTakesSuite extends munit.FunSuite:
  /** A test power: claims for p1 every burn another player makes. Its line
    * names the burner and the count. */
  private object TakesOthersBurnsForP1 extends ContributingPower:
    val id: PowerId = PowerId("test.takes-others-burns")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def takesBurn(ready: ReadyGame, burnt: Burnt)
        : Option[BurnTake] =
      Option.when(burnt.by != p1)(BurnTake(p1, (burner, amount, _) =>
        line(burner, amount)))

  private def line(burner: PlayerId, amount: Int): LogNote =
    LogNote(Some(PowerSourceRef.Site(SiteId("test"))), "took",
      Vector(NoteArg.Player(burner), NoteArg.Number(amount)))

  private val powers = WalkerPowers(Vector(TakesOthersBurnsForP1))
  /** p1 with no favor; p2 with 3 favor, 1 faceup and 2 facedown secrets. */
  private val start: ReadyGame = Table.start.favor(p1, 0).favor(p2, 3)
    .secrets(p2, faceUp = 1, faceDown = 2).ready
  private val p2Board = PositionedLocation(Location.PlayArea(p2))

  /** The end state and the events of walking `tree` from `from`. */
  private def walked(tree: Operation, from: ReadyGame = start)
      (using munit.Location): (ReadyGame, Vector[OathEvent]) =
    accepted(ProcedureWalker.advance(from, tree, None, powers)) match
      case WalkerOutcome.Finished(end, events) => (end, events)
      case other => fail(s"the walk did not finish: $other")

  /** Each recorded step's operations. */
  private def steps(events: Vector[OathEvent]): Vector[Vector[CoreOperation]] =
    events.collect { case WalkerStepRecorded(_, DeltaPayload(ops), _) => ops }

  /** The test power's lines. */
  private def lines(events: Vector[OathEvent]): Vector[LogNote] =
    events.collect { case LogNoted(TakesOthersBurnsForP1.id, note, _) =>
      note }

  test("a batch's burn another player makes becomes a Take to the taker, " +
      "cut to what is there, with the taker's line"):
    val (end, events) = walked(BuildOps((_, _) => Right(Vector[CoreOperation](
      Burn.favor(5, p2Board, Some(p2))))))
    assertEquals(Look(end).favor(p2), 0)
    assertEquals(Look(end).favor(p1), 3)
    assertEquals(steps(events), Vector(Vector[CoreOperation](Take(
      Piece.Favor(3), p1, Location.PlayArea(p2), Location.PlayArea(p1)))))
    assertEquals(lines(events), Vector(line(p2, 3)))

  test("a cost or a burn in the tree runs whole: the cost's take is " +
      "paid in full, and facedown secrets stay facedown"):
    val (paid, paidEvents) = walked(Sequence(Vector[Operation](
      PayCost(p2, Location.SharedBank, Cost(favorBurnt = 2)))))
    assertEquals(Look(paid).favor(p1), 2)
    assertEquals(steps(paidEvents), Vector(Vector[CoreOperation](
      PayCost(p2, Location.SharedBank, Cost.free),
      Take(Piece.Favor(2), p1, Location.PlayArea(p2), Location.PlayArea(p1)))))
    // The take is paid in full: p2 holds 3 favor, so a cost of 5 is refused.
    assert(ProcedureWalker.advance(start, Sequence(Vector[Operation](
      PayCost(p2, Location.SharedBank, Cost(favorBurnt = 5)))), None,
      powers).isLeft)
    val faceDownBefore = Look(start).faceDownSecrets(p1)
    val (taken, takenEvents) = walked(Sequence(Vector[Operation](
      Burn.secrets(2, PositionedLocation(Location.PlayArea(p2),
        side = Some(SecretSide.FaceDown)), Some(p2)))))
    assertEquals(Look(taken).faceDownSecrets(p1), faceDownBefore + 2)
    assertEquals(Look(taken).faceDownSecrets(p2), 0)
    assertEquals(Look(taken).faceUpSecrets(p2), 1)
    assertEquals(lines(takenEvents), Vector(line(p2, 2)))

  test("a burn of the taker's own board leaves it there, and a player's " +
      "own burn goes to the bank"):
    val keep = Table.from(start).favor(p1, 2).unchecked
    val (kept, keptEvents) = walked(BuildOps((_, _) => Right(
      Vector[CoreOperation](Burn.favor(2,
        PositionedLocation(Location.PlayArea(p1)), Some(p2))))), keep)
    assertEquals(Look(kept).favor(p1), 2)
    assertEquals(steps(keptEvents), Vector.empty)
    assertEquals(lines(keptEvents), Vector(line(p2, 2)))
    val (own, ownEvents) = walked(BuildOps((_, _) => Right(
      Vector[CoreOperation](Burn.favor(2, p2Board, Some(p1))))))
    assertEquals(Look(own).favor(p2), 1)
    assertEquals(Look(own).favor(p1), 0)
    assertEquals(lines(ownEvents), Vector.empty)
