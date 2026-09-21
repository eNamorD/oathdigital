package oathdigital.gameplay

import scala.collection.mutable

import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, Offer, OfferHost, PowerCtx, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** The `Offer` contribution and the node that hosts it: what the walker hands a
  * host, in which order, and how a host is told the walk is resuming inside it.
  */
class OfferHostSuite extends munit.FunSuite {
  private val ready = initialReady
  private val actor = ready.game.current.turn.activePlayer
  private val window = PowerWindow.CampaignAttackerBattlePlans

  private def offer(name: String): CampaignPlanOffer = CampaignPlanOffer(
    CampaignPlanSource.Adviser(actor, DenizenId(name)), name, Vector.empty,
    Vector.empty)

  /** A power that offers what `plan` says, whatever the window's context. */
  private final case class Offering(name: String,
      plan: PowerCtx => Option[CampaignPlanOffer]) extends ContributingPower {
    def id: PowerId = PowerId(s"test.$name")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(window -> Vector(Offer(plan)))
  }

  /** A host that records each fold and expands to a `GainSupply` per offer. */
  private final class Host(gain: Int = 1) extends OfferHost {
    val folds: mutable.Buffer[(Vector[OfferedPlan], Boolean, Vector[Answered])] =
      mutable.Buffer.empty
    override val window: Option[PowerWindow] = Some(OfferHostSuite.this.window)
    override val children: Vector[Operation] = Vector.empty
    def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
        : Vector[Operation] = {
      folds += ((offers, pass.resuming, pass.answered))
      offers.map(_ => GainSupply(actor, gain))
    }
  }

  private def low(state: ReadyGame): ReadyGame = state.updateCurrent(current =>
    current.copy(players = current.players.map(p =>
      if (p.player == actor) p.copy(board = p.board.copy(supply = SupplyTrack(1)))
      else p)))

  test("a host is handed the offers of the powers hooked at its window, in power order") {
    val host = new Host
    val powers = WalkerPowers(Vector(Offering("b", _ => Some(offer("b"))),
      Offering("a", _ => Some(offer("a"))), Offering("none", _ => None)))
    val outcome = ProcedureWalker.advance(low(ready), host, None, powers)
    assert(outcome.isRight)
    assertEquals(host.folds.toVector.map(_._1.map(_.power.value)), Vector(
      Vector("test.a", "test.b")))
    assertEquals(host.folds.head._2, false)
    assertEquals(host.folds.head._3, Vector.empty[Answered])
  }

  test("a host with nothing offered expands to nothing, and a power's other window is not gathered") {
    val host = new Host
    val elsewhere = new ContributingPower {
      def id: PowerId = PowerId("test.elsewhere")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.CampaignDefenderBattlePlans -> Vector(Offer(_ =>
          Some(offer("elsewhere")))))
    }
    ProcedureWalker.advance(low(ready), host, None, WalkerPowers(Vector(elsewhere)))
    assertEquals(host.folds.head._1, Vector.empty[OfferedPlan])
  }

  test("a transform at the host's window runs before the expansion") {
    val host = new Host(gain = 2)
    val first = new ContributingPower {
      def id: PowerId = PowerId("test.first")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(window ->
        Vector(Transform((_, children) => children :+ GainSupply(actor, 1)),
          Offer(_ => Some(offer("x")))))
    }
    val Right(WalkerOutcome.Finished(after, events)) = ProcedureWalker.advance(
      low(ready), host, None, WalkerPowers(Vector(first))): @unchecked
    assertEquals(after.game.current.players.find(_.player == actor).get
      .board.supply.supply, 4)
    assertEquals(events.size, 2)
  }

  test("a host is told the walk is resuming inside it, and sees the answers recorded so far") {
    val question = Decide("test.question", actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes"))))
    val hosted = new OfferHost {
      val seen: mutable.Buffer[(Boolean, Vector[Answered])] = mutable.Buffer.empty
      override val window: Option[PowerWindow] = Some(OfferHostSuite.this.window)
      override val children: Vector[Operation] = Vector.empty
      def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
          : Vector[Operation] = {
        seen += ((pass.resuming, pass.answered))
        Vector(question)
      }
    }
    val tree = Sequence(Vector[Operation](hosted))
    val state = low(ready)
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(state,
      tree, None, WalkerPowers.empty): @unchecked
    assertEquals(hosted.seen.toVector, Vector((false, Vector.empty[Answered])))
    val answer = Answered("test.question", DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Button("yes")), actor)
    assert(ProcedureWalker.resolve(state, tree, pending, answer,
      WalkerPowers.empty).isRight)
    // The resume folds the host again, inside it.
    assertEquals(hosted.seen.toVector.last, (true, Vector.empty[Answered]))
    // The parked decision is found through the same fold.
    assertEquals(ProcedureWalker.parkedDecide(state, tree, pending,
      WalkerPowers.empty).map(_.decisionId), Some("test.question"))
  }

  test("an offer reads the decisions answered before its window in its context") {
    val question = Decide("test.question", actor, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes"))))
    val host = new Host
    val reading = Offering("reader", ctx => Option.when(ctx.answered.exists(
      _.decisionId == "test.question"))(offer("answered")))
    val tree = Sequence(Vector[Operation](question, host))
    val powers = WalkerPowers(Vector(reading))
    val state = low(ready)
    val Right(WalkerOutcome.Parked(pending, _)) = ProcedureWalker.advance(state,
      tree, None, powers): @unchecked
    assertEquals(host.folds.size, 0)
    ProcedureWalker.resolve(state, tree, pending, Answered("test.question",
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("yes")), actor),
      powers)
    assertEquals(host.folds.toVector.map(_._1.map(_.offer.label)), Vector(
      Vector("answered")))
  }

  test("a host can ask whether an operation would run, and what it would record") {
    val results: mutable.Buffer[Either[OathViolation, Vector[CoreOperation]]] =
      mutable.Buffer.empty
    val host = new OfferHost {
      override val window: Option[PowerWindow] = Some(OfferHostSuite.this.window)
      override val children: Vector[Operation] = Vector.empty
      def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
          : Vector[Operation] = {
        results += pass.applies(GainSupply(actor, 1))
        results += pass.applies(SpendSupply(actor, 6))
        results += pass.applies(Sequence(Vector[Operation](GainSupply(actor, 1),
          Decide("test.question", actor, DecisionQuery.ChooseOne(Vector(
            DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes")))))))
        Vector.empty
      }
    }
    val state = low(ready)
    ProcedureWalker.advance(state, host, None, WalkerPowers.empty)
    assertEquals(results(0), Right(Vector[CoreOperation](GainSupply(actor, 1))))
    assert(results(1).isLeft)
    assertEquals(results(2), Right(Vector[CoreOperation](GainSupply(actor, 1))))
    // Asking changed nothing.
    assertEquals(state.game.current.players.find(_.player == actor).get
      .board.supply.supply, 1)
  }
}
