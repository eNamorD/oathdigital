package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.{MusterSource, TradeProcedure}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PowerAnswers, SelectedModifier}
import oathdigital.model._

/** The Cup of Plenty (relic R07), a selected Trade modifier: trading with a
  * card whose suit differs from every faceup adviser the player holds costs no
  * Supply. A facedown adviser does not count, and a player with no faceup
  * adviser trades free.
  *
  * The card traded with is the answer to the Trade's source decision, which the
  * cost node cannot see, so the Supply payment is replaced by a node that reads
  * the answer when it runs and pays only when the suits match.
  */
final case class CupOfPlenty private (cardId: RelicId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = CupOfPlenty.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeCost -> Vector(Transform((ctx, operations) =>
      operations.map {
        case pay @ SpendSupply(player, _, _) if player == ctx.activePlayer =>
          unlessFree(ctx.activePlayer, pay)
        case other => other
      })))

  private def unlessFree(actor: PlayerId, pay: SpendSupply): Operation =
    BuildOps((ready, pending) => Right(
      if (differs(ready, actor, pending)) Vector.empty
      else Vector[CoreOperation](pay)))

  /** Whether the card traded with matches none of the faceup advisers. The suit
    * is read off the answered card, not resolved as a source: this node runs
    * after the payment that placed a secret on the card, which a source must
    * not yet hold.
    */
  private def differs(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Boolean = (for {
    ref <- PowerAnswers.one(pending, TradeProcedure.decisionId)
    suit <- ref match {
      case DecisionOptionRef.Denizen(id) => catalog.suitOf(id)
      case DecisionOptionRef.Edifice(id) => catalog.suitOf(id)
      case _ => None
    }
  } yield MusterSource.matching(catalog, ready, actor, suit) == 0)
    .getOrElse(false)
}

object CupOfPlenty {
  val id: PowerId = PowerId("relic.cup-of-plenty")

  def forCatalog(catalog: ExecutableCatalog): Option[CupOfPlenty] =
    CatalogCards.relic(catalog, id).map(new CupOfPlenty(_, catalog))
}
