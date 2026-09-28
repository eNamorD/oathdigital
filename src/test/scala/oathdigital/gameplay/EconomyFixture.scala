package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.model._

/** Test powers the Muster and Trade suites share. The boards they act on
  * are built with `Table`.
  */
object EconomyFixture:
  /** Adds the actor's adviser `adviser` to the Muster source decision, which is
    * exactly what Golem Legions will do; only the acceptance rule stops it
    * today.
    */
  final case class AddAdviserSource(id: PowerId, adviser: DenizenId)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.MusterSourceSelection -> Vector(Transform((_, operations) =>
        operations.map {
          case decide: Decide => decide.query match {
            case DecisionQuery.ChooseOne(options, heading) =>
              decide.copy(query = DecisionQuery.ChooseOne(options :+
                DecisionOption.Denizen(DecisionOptionRef.Denizen(adviser)),
                heading)): Operation
            case _ => decide
          }
          case other => other
        })))

  /** Removes the payment from a Muster's cost window, keeping the Supply. */
  final case class FreePayment(id: PowerId) extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(PowerWindow.MusterCost -> Vector(Transform((_, operations) =>
        operations.filterNot(_.isInstanceOf[PayCost]))))
