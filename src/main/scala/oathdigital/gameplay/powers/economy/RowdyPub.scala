package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{PlayerFacts, PowerAnswers, SelectedModifier}
import oathdigital.model._

object RowdyPubCard extends Denizen(DenizenId("144"), "Rowdy Pub", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.rowdy-pub"),
    persistent = false, cost = Cost.free,
    text = "Gain one more warband if mustering from Rowdy Pub.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Rowdy Pub (card 144), a selected Muster modifier: mustering from Rowdy Pub
  * gains one more warband, on top of the bonus for matching advisers.
  *
  * The card mustered from is the answer to the Muster's source decision, read
  * when the gain node runs, so the extra warband is added only when Rowdy Pub is
  * the source. The gain is best-effort like the base gain.
  */
final case class RowdyPub private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = RowdyPubCard.id
  def id: PowerId = RowdyPub.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      operations :+ bonus(ctx.activePlayer))))

  private def bonus(actor: PlayerId): Operation = BuildOps((ready, pending) =>
    if PowerAnswers.one(pending, MusterProcedure.decisionId)
        .contains(DecisionOptionRef.Denizen(cardId)) then
      PlayerFacts.forceKind(ready, actor).map(kind =>
        Vector[CoreOperation](Gain.Warbands(actor, kind, RowdyPub.Warbands)))
    else Right(Vector.empty))

object RowdyPub:
  val id: PowerId = RowdyPubCard.power.id
  val Warbands: Int = 1

  def forCatalog(catalog: ExecutableCatalog): RowdyPub =
    new RowdyPub(catalog)
