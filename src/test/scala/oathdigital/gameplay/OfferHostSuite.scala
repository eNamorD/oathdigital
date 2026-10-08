package oathdigital.gameplay

import scala.collection.mutable

import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, Offer, OfferHost, PowerCtx, Transform}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Outcomes.{finished, parked}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, as}

/** The `Offer` contribution and the node that hosts it: what the walker hands a
  * host, in which order, and how a host is told the walk is resuming inside it.
  */
class OfferHostSuite extends munit.FunSuite:
  /** The start with p1's Supply down to 1, so each gain shows. */
  private val supplyOfOne = Table.start.supply(p1, 1).ready
  private val window = PowerWindow.CampaignAttackerBattlePlans

  private def offer(name: String): CampaignPlanOffer = CampaignPlanOffer(
    CampaignPlanSource.Adviser(p1, DenizenId(name)), name, Vector.empty,
    Vector.empty)

  /** Stub of ContributingPower: offers what `plan` says, whatever the
    * window's context. */
  private final case class Offering(name: String,
      plan: PowerCtx => Option[CampaignPlanOffer]) extends ContributingPower:
    def id: PowerId = PowerId(s"test.$name")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(window -> Vector(Offer(plan)))

  /** What a host was handed on one fold. */
  private final case class Fold(offers: Vector[OfferedPlan], resuming: Boolean,
      answered: Vector[Answered])

  /** Spy of OfferHost: records each fold in `folds` and expands to a
    * `GainSupply` per offer. */
  private final class Host(gain: Int = 1) extends OfferHost:
    val folds: mutable.Buffer[Fold] = mutable.Buffer.empty
    override val window: Option[PowerWindow] = Some(OfferHostSuite.this.window)
    override val children: Vector[Operation] = Vector.empty
    def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
        : Vector[Operation] =
      folds += Fold(offers, pass.resuming, pass.answered)
      offers.map(_ => GainSupply(p1, gain))

  test("a host is handed the offers of the powers hooked at its window, in " +
      "power order"):
    val host = new Host
    val powers = WalkerPowers(Vector(Offering("b", _ => Some(offer("b"))),
      Offering("a", _ => Some(offer("a"))), Offering("none", _ => None)))
    val outcome = ProcedureWalker.advance(supplyOfOne, host, None, powers)
    accepted(outcome)
    assertEquals(host.folds.toVector.map(_.offers.map(_.power.value)), Vector(
      Vector("test.a", "test.b")))
    assertEquals(host.folds.head.resuming, false)
    assertEquals(host.folds.head.answered, Vector.empty[Answered])

  test("a host with nothing offered expands to nothing, and a power's other " +
      "window is not gathered"):
    val host = new Host
    /** Stub of ContributingPower: one Offer, at the defender's plan window
      * only. */
    val elsewhere = new ContributingPower:
      def id: PowerId = PowerId("test.elsewhere")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.CampaignDefenderBattlePlans -> Vector(Offer(_ =>
          Some(offer("elsewhere")))))
    ProcedureWalker.advance(supplyOfOne, host, None,
      WalkerPowers(Vector(elsewhere)))
    assertEquals(host.folds.head.offers, Vector.empty[OfferedPlan])

  test("a transform at the host's window runs before the expansion"):
    val host = new Host(gain = 2)
    /** Stub of ContributingPower: a Transform that adds a GainSupply, and an
      * Offer, at the host's window. */
    val first = new ContributingPower:
      def id: PowerId = PowerId("test.first")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(window ->
        Vector(Transform((_, children) => children :+ GainSupply(p1, 1)),
          Offer(_ => Some(offer("x")))))
    val done = finished(ProcedureWalker.advance(supplyOfOne, host, None,
      WalkerPowers(Vector(first))))
    // 1 at the start, 1 from the transform and 2 from the expansion.
    assertEquals(Look(done.treeless).supply(p1), 4)
    // The transform's gain is recorded before the expansion's.
    assertEquals(done.events.map(as[WalkerStepRecorded](_).ops),
      Vector(Vector[CoreOperation](GainSupply(p1, 1)),
        Vector[CoreOperation](GainSupply(p1, 2))))

  test("a host is told the walk is resuming inside it, and sees the answers " +
      "recorded so far"):
    val question = Decide("test.question", p1, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes"))))
    val seen: mutable.Buffer[(Boolean, Vector[Answered])] = mutable.Buffer.empty
    /** Spy of OfferHost: records (resuming, answered) in `seen` on each
      * fold and expands to `question`. */
    val hosted = new OfferHost:
      override val window: Option[PowerWindow] = Some(OfferHostSuite.this.window)
      override val children: Vector[Operation] = Vector.empty
      def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
          : Vector[Operation] =
        seen += ((pass.resuming, pass.answered))
        Vector(question)
    val tree = Sequence(Vector[Operation](hosted))
    val pending = parked(ProcedureWalker.advance(supplyOfOne, tree, None,
      WalkerPowers.empty)).tree
    // The first fold is the walk's own. The park's search then walks on from
    // the question, which folds the host again, inside it.
    assertEquals(seen.toVector, Vector((false, Vector.empty[Answered]),
      (true, Vector.empty[Answered])))
    val answer = Answered("test.question", DecisionAnswer.ChooseOneAnswer(
      DecisionOptionRef.Button("yes")), p1)
    accepted(ProcedureWalker.resolve(supplyOfOne, tree, pending, answer,
      WalkerPowers.empty))
    // The resume folds the host again, inside it.
    assertEquals(seen.toVector.last, (true, Vector.empty[Answered]))
    // The parked decision is found through the same fold.
    assertEquals(ProcedureWalker.parkedDecide(supplyOfOne, tree, pending,
      WalkerPowers.empty).map(_.decisionId), Some("test.question"))

  test("an offer reads the decisions answered before its window in its " +
      "context"):
    val question = Decide("test.question", p1, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes"))))
    val host = new Host
    val reading = Offering("reader", ctx => Option.when(ctx.answered.exists(
      _.decisionId == "test.question"))(offer("answered")))
    val tree = Sequence(Vector[Operation](question, host))
    val powers = WalkerPowers(Vector(reading))
    val pending =
      parked(ProcedureWalker.advance(supplyOfOne, tree, None, powers)).tree
    // Only the park's search reaches the host, and it does so with the
    // hypothetical answer in the context.
    assertEquals(host.folds.toVector.map(_.offers.map(_.offer.label)), Vector(
      Vector("answered")))
    ProcedureWalker.resolve(supplyOfOne, tree, pending,
      Answered("test.question",
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Button("yes")), p1),
      powers)
    // The answer check reuses the park's verdict, so only the walk that goes
    // on folds the host again.
    assertEquals(host.folds.toVector.map(_.offers.map(_.offer.label)), Vector(
      Vector("answered"), Vector("answered")))

  test("a host can ask whether an operation would run, and what it would " +
      "record"):
    val results: mutable.Buffer[Either[OathViolation, Vector[CoreOperation]]] =
      mutable.Buffer.empty
    /** Spy of OfferHost: records what `pass.applies` answers in `results`;
      * expands to nothing. */
    val host = new OfferHost:
      override val window: Option[PowerWindow] = Some(OfferHostSuite.this.window)
      override val children: Vector[Operation] = Vector.empty
      def expand(offers: Vector[OfferedPlan], pass: OfferHost.Pass)
          : Vector[Operation] =
        results += pass.applies(GainSupply(p1, 1))
        results += pass.applies(SpendSupply(p1, 6))
        results += pass.applies(Sequence(Vector[Operation](GainSupply(p1, 1),
          Decide("test.question", p1, DecisionQuery.ChooseOne(Vector(
            DecisionOption.Button(DecisionOptionRef.Button("yes"), "Yes")))))))
        Vector.empty
    ProcedureWalker.advance(supplyOfOne, host, None, WalkerPowers.empty)
    assertEquals(results(0), Right(Vector[CoreOperation](GainSupply(p1, 1))))
    assertEquals(results(1), Left(OathViolation.CoreOperationRejected(
      "insufficient-supply", "a supply spend of 6 exceeds the 1 available")))
    assertEquals(results(2), Right(Vector[CoreOperation](GainSupply(p1, 1))))
