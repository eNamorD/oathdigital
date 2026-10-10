package oathdigital.gameplay

import oathdigital.gameplay.walker.{PowerNoted, ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.p1
import oathdigital.testkit.PowerStubs.{ActionForbiddingStub, OptionHidingStub, hideSource, hidNote}
import oathdigital.testkit.Outcomes.{finished, parked}

class PowerNoteHideSuite extends munit.FunSuite:
  private val window = PowerWindow.ChallengeAmountSelection
  /** p1's Act on the quiet table: the walks below ask p1 to decide. */
  private val actor = p1
  private val ready = Table.start.turn(p1, Phase.Act).ready
  private def site(id: String) = DecisionOptionRef.Site(SiteId(id))
  private def pick(ready: ReadyGame) = Sequence(Vector[Operation](Decide("pick",
    actor, DecisionQuery.ChooseOne(
      Vector("a", "b", "c").map(id => DecisionOption.Site(site(id)))),
    window = Some(window))))
  private def notes(events: Vector[OathEvent]): Vector[OathEvent] =
    events.filter(_.isInstanceOf[PowerNoted])

  private val hider = PowerId("test.hider")

  private def offered(tree: Operation, pending: PendingTree,
      powers: WalkerPowers): Vector[DecisionOption] =
    ProcedureWalker.parkedDecide(ready, tree, pending, powers)
      .map(_.query).collect {
        case DecisionQuery.ChooseOne(options, _, _) => options
      }.getOrElse(Vector.empty)

  test("each option a power hides keeps its note as its reason, journaled " +
      "nowhere"):
    val powers = WalkerPowers(Vector(OptionHidingStub(hider, window,
      Set(site("b"), site("c")))))
    val tree = pick(ready)
    val WalkerOutcome.Parked(pending, events) =
      parked(ProcedureWalker.advance(ready, tree, None, powers))
    assertEquals(notes(events), Vector.empty)
    assertEquals(offered(tree, pending, powers), Vector[DecisionOption](
      DecisionOption.Site(site("a")),
      DecisionOption.Unavailable(DecisionOption.Site(site("b")),
        OptionReason.Noted(hider, hidNote(site("b")))),
      DecisionOption.Unavailable(DecisionOption.Site(site("c")),
        OptionReason.Noted(hider, hidNote(site("c"))))))

  test("answering the decision does not note the hidden options again"):
    val powers = WalkerPowers(
      Vector(OptionHidingStub(hider, window, Set(site("b")))))
    val tree = pick(ready)
    val pending =
      parked(ProcedureWalker.advance(ready, tree, None, powers)).tree
    val events = finished(ProcedureWalker.resolve(ready, tree, pending,
      Answered("pick", DecisionAnswer.ChooseOneAnswer(site("a")), actor),
      powers)).events
    assertEquals(notes(events), Vector.empty)

  test("a power that says nothing hides silently"):
    val powers = WalkerPowers(Vector(OptionHidingStub(hider, window, Set(site("b")),
      says = false)))
    val events =
      parked(ProcedureWalker.advance(ready, pick(ready), None, powers)).events
    assertEquals(notes(events), Vector.empty)

  test("an option the look-ahead hides keeps the note of the restriction it " +
      "would break as its reason"):
    val nested = PowerWindow.CampaignActionEligibility
    val forbidder = PowerId("test.forbidder")
    val powers = WalkerPowers(Vector(ActionForbiddingStub(forbidder, nested)))
    def button(key: String) =
      DecisionOption.Button(DecisionOptionRef.Button(key), key)
    val tree = Sequence(Vector[Operation](Decide("ask", actor,
        DecisionQuery.ChooseOne(Vector(button("yes"), button("no")))),
      Branch((_, pending) => if pending.answered.exists {
        case Answered("ask", DecisionAnswer.ChooseOneAnswer(ref), _) =>
          ref == DecisionOptionRef.Button("yes")
        case _ => false
      } then Vector(Sequence(Vector.empty, Some(nested))) else Vector.empty)))
    val WalkerOutcome.Parked(pending, events) =
      parked(ProcedureWalker.advance(ready, tree, None, powers))
    assertEquals(notes(events), Vector.empty)
    assertEquals(offered(tree, pending, powers), Vector[DecisionOption](
      DecisionOption.Unavailable(button("yes"), OptionReason.Noted(forbidder,
        PowerNote(hideSource, "forbade", Vector.empty))),
      button("no")))
    val WalkerOutcome.Finished(_, answered) = finished(ProcedureWalker.resolve(
      ready, tree, pending, Answered("ask", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("no")), actor), powers))
    assertEquals(notes(answered), Vector.empty)

  test("a restriction on a tree with no decision to narrow writes no note"):
    // Rejecting a whole action is OathRules' check before the walk, which
    // journals nothing; the walker notes only options it hid.
    val powers = WalkerPowers(Vector(ActionForbiddingStub(PowerId("test.forbidder"),
      PowerWindow.CampaignActionEligibility)))
    val tree = Sequence(Vector[Operation](ModifyDicePool(PoolKey("p"), 1)),
      Some(PowerWindow.CampaignActionEligibility))
    val events =
      finished(ProcedureWalker.advance(ready, tree, None, powers)).events
    assertEquals(notes(events), Vector.empty)
