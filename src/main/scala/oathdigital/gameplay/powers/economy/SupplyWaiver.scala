package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** The shape of the Muster and Trade modifiers that waive the Supply payment
  * by the suit of the card the action uses (the Cup of Plenty, Animal
  * Playmates, Birdsong).
  *
  * The card is the answer to the action's source decision, which the cost node
  * cannot see, so the actor's Supply payment is replaced by a node that reads
  * the answer when it runs and pays unless `free` holds for the card's suit.
  * The suit is read off the answered card, not resolved as a source: this node
  * runs after the payment that placed a token on the card, which a source must
  * not yet hold. A card whose suit cannot be read pays.
  */
private[economy] object SupplyWaiver:
  def effects(window: PowerWindow, decisionId: String,
      catalog: ExecutableCatalog)(free: (ReadyGame, PlayerId, Suit) => Boolean)
      : Map[PowerWindow, Vector[Contribution]] = Map(
    window -> Vector(Transform((ctx, operations) =>
      operations.map {
        case pay @ SpendSupply(player, _, _) if player == ctx.activePlayer =>
          unlessFree(pay, decisionId, catalog, free)
        case other => other
      })))

  private def unlessFree(pay: SpendSupply, decisionId: String,
      catalog: ExecutableCatalog, free: (ReadyGame, PlayerId, Suit) => Boolean)
      : Operation = BuildOps((ready, pending) => Right(
    if suitOf(pending, decisionId, catalog).exists(free(ready, pay.player, _))
    then Vector.empty
    else Vector[CoreOperation](pay)))

  private def suitOf(pending: PendingTree, decisionId: String,
      catalog: ExecutableCatalog): Option[Suit] =
    PowerAnswers.one(pending, decisionId).flatMap:
      case DecisionOptionRef.Denizen(id) => catalog.suitOf(id)
      case DecisionOptionRef.Edifice(id) => catalog.suitOf(id)
      case _ => None
