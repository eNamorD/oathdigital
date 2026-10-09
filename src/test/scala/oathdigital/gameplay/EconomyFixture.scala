package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.model._

/** Test powers the Muster and Trade suites share. The boards they act on
  * are built with `Table`.
  */
object EconomyFixture:
  /** Stub of ContributingPower for the Muster source: adds `card` to the
    * source decision, after the options it offers, as Golem Legions adds
    * itself. */
  final case class AddSource(id: PowerId, card: DenizenId)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.MusterSourceSelection -> Vector(Transform((_, operations) =>
        operations.map {
          case decide: Decide => decide.query match {
            case DecisionQuery.ChooseOne(options, heading) =>
              decide.copy(query = DecisionQuery.ChooseOne(options :+
                DecisionOption.Denizen(DecisionOptionRef.Denizen(card)),
                heading)): Operation
            case _ => decide
          }
          case other => other
        })))

  /** Stub of ContributingPower for the Muster gain window: asks a question
    * before the gain, so a walk parks after the cost has placed its favor
    * on the source. */
  final case class AskAtGain(id: PowerId) extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
        Decide(AskAtGain.decisionId, ctx.activePlayer,
          DecisionQuery.ChooseOne(Vector(
            DecisionOption.Button(AskAtGain.go, "Go")))) +: operations)))

  object AskAtGain:
    val decisionId: String = "test.ask-at-gain"
    val go: DecisionOptionRef.Button = DecisionOptionRef.Button("go")

  /** Stub of ContributingPower for the Muster cost window: removes the
    * payment, keeping the Supply. */
  final case class FreePayment(id: PowerId) extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.MusterCost -> Vector(Transform((_, operations) =>
        operations.filterNot(_.isInstanceOf[PayCost]))))
