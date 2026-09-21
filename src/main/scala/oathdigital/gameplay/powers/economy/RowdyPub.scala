package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts, PowerAnswers, SelectedModifier}
import oathdigital.model._

/** Rowdy Pub (card 144), a selected Muster modifier: mustering from Rowdy Pub
  * gains one more warband, on top of the bonus for matching advisers.
  *
  * The card mustered from is the answer to the Muster's source decision, read
  * when the gain node runs, so the extra warband is added only when Rowdy Pub is
  * the source. The gain is best-effort like the base gain.
  */
final case class RowdyPub private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier {
  def id: PowerId = RowdyPub.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      operations :+ bonus(ctx.activePlayer))))

  private def bonus(actor: PlayerId): Operation = BuildOps((ready, pending) =>
    if (PowerAnswers.one(pending, MusterProcedure.decisionId)
        .contains(DecisionOptionRef.Denizen(cardId)))
      PlayerFacts.forceKind(ready, actor).map(kind =>
        Vector[CoreOperation](Gain.Warbands(actor, kind, RowdyPub.Warbands)))
    else Right(Vector.empty))
}

object RowdyPub {
  val id: PowerId = PowerId("denizen.rowdy-pub")
  val Warbands: Int = 1

  def forCatalog(catalog: ExecutableCatalog): Option[RowdyPub] =
    CatalogCards.denizen(catalog, id).map(new RowdyPub(_, catalog))
}
