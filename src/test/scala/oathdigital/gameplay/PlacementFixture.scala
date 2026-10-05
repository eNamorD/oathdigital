package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlayRules, DiscardPermission}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.Outcomes
import oathdigital.testkit.Unwrap.{accepted, as, present}

/** Driving the card-play walk, shared by the placement suites. The powers
  * built here are stubs of ContributingPower: they change `CardPlayRules`
  * and nothing else.
  */
object PlacementFixture:
  /** Stub of ContributingPower: changes the rules every play is planned
    * under (`CardPlayRules`), nothing else. */
  def rulePower(name: String)(
      change: CardPlayRules => CardPlayRules): ContributingPower =
    new ContributingPower:
      def id: PowerId = PowerId(name)
      def source: RuleSourceRef = RuleSourceRef.GameRule(name)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, ops) =>
          ctx.operation match {
            case tree: CardPlayProcedure.PlacementTree =>
              tree.adjust(ops)(change)
            case _ => ops
          })))

  /** A plain site discard permission, of any card, whose line says nothing. */
  val testPermission: DiscardPermission = DiscardPermission(
    PowerId("test.discard-first"),
    Note(PowerId("test.discard-first"), _ => None))

  /** Stub of ContributingPower: adds `testPermission`. */
  val discardFirst: ContributingPower =
    rulePower("test.discard-first")(_.permitSiteDiscard(testPermission))
  /** Stub of ContributingPower: lowers the adviser limit to two. */
  val limitTwo: ContributingPower =
    rulePower("test.limit-two")(_.limitAdvisers(2))

  /** The id of a card play's `kind` decision ("place" or "replace") for
    * `card`. */
  def cardPlayDecisionId(card: WorldCardId, kind: String): String =
    s"cardplay.$kind.${card.kind}.${card.value}"

  def build(ready: ReadyGame, actor: PlayerId, card: WorldCardId)(
      using munit.Location): Operation =
    accepted(CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.TemporaryHand))

  def park(ready: ReadyGame, tree: Operation, powers: WalkerPowers)(
      using munit.Location): PendingTree =
    Outcomes.parked(ProcedureWalker.advance(ready, tree, None, powers)).tree

  def answer(ready: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, id: String, ref: DecisionOptionRef,
      actor: PlayerId)(using munit.Location): WalkerOutcome =
    accepted(ProcedureWalker.resolve(ready, tree, pending,
      Answered(id, DecisionAnswer.ChooseOneAnswer(ref), actor), powers))

  /** The options of the decision the walk is parked on. */
  def options(ready: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers)(using munit.Location): Vector[DecisionOptionRef] =
    val decide = present(
      ProcedureWalker.parkedDecide(ready, tree, pending, powers),
      "the decision the walk is parked on")
    as[DecisionQuery.ChooseOne](decide.query).options.map(_.ref)
