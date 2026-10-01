package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts, PowerAnswers,
  SelectedModifier}
import oathdigital.model._

/** Downtrodden (card 81), a selected Muster modifier with no cost: "Gain two
  * more warbands if mustering on a card whose favor bank has the least favor
  * (not tied)."
  *
  * The card's suit is read off the answer to the Muster's source decision,
  * and the banks when the gain node runs. The card's bank must hold strictly
  * less favor than each of the other five, so a tie for least, empty banks
  * included, gives nothing. The Muster's own favor comes from the player's
  * board, so it never changes a bank before the read. The gain is best-effort
  * like the base gain, and the generic gain lines tell it.
  */
final case class Downtrodden private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = Downtrodden.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Muster)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      operations :+ bonus(ctx.activePlayer))))

  private def bonus(actor: PlayerId): Operation = BuildOps((ready, pending) =>
    if suitOf(pending).exists(Downtrodden.least(ready, _)) then
      PlayerFacts.forceKind(ready, actor).map(kind => Vector[CoreOperation](
        Gain.Warbands(actor, kind, Downtrodden.Warbands)))
    else Right(Vector.empty))

  private def suitOf(pending: PendingTree): Option[Suit] =
    PowerAnswers.one(pending, MusterProcedure.decisionId).flatMap:
      case DecisionOptionRef.Denizen(card) => catalog.suitOf(card)
      case DecisionOptionRef.Edifice(card) => catalog.suitOf(card)
      case _ => None

object Downtrodden:
  val id: PowerId = PowerId("denizen.downtrodden")
  val Warbands: Int = 2

  /** Whether `suit`'s bank holds strictly less favor than every other bank. */
  def least(ready: ReadyGame, suit: Suit): Boolean =
    val favor = (bank: Suit) => ready.banks.favor.getOrElse(bank, 0)
    Suit.all.filter(_ != suit).forall(other => favor(suit) < favor(other))

  def forCatalog(catalog: ExecutableCatalog): Option[Downtrodden] =
    CatalogCards.denizen(catalog, id).map(new Downtrodden(_, catalog))
