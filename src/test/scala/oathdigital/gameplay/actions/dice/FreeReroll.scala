package oathdigital.gameplay.actions.dice

import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  OfferReroll}
import oathdigital.model._
import oathdigital.testkit.CatalogNames

/** A power of the tests' own: a free reroll of every die of a throw, for a
  * roller who holds Bracken faceup. Bracken's own power is not
  * implemented, so the card offers nothing else. */
object FreeReroll extends ContributingPower:
  val bracken: DenizenId = CatalogNames.denizen("Bracken")
  def id: PowerId = PowerId("test.free-reroll")
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.Reroll -> Vector(OfferReroll(ctx =>
      RerollChoice.throwOf(ctx).filter(rolled =>
        ctx.state.game.current.players.find(_.player == rolled.roller)
          .exists(_.advisers.exists {
            case DenizenState(`bracken`, Orientation.FaceUp, _) => true
            case _ => false
          })).map(rolled => RerollOffer(bracken, None,
        Vector(Reroll(rolled.pool, rolled.indices)))))))
