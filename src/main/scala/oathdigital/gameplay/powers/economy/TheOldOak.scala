package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.{PowerAnswers, SelectedModifier}
import oathdigital.model._

object TheOldOakCard extends Denizen(DenizenId("42"), "The Old Oak", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.the-old-oak"),
    persistent = false, cost = Cost.free,
    text = "If trading with The Old Oak for [secret], gain one more " +
      "[secret] if you have any [suit-beast] advisers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** The Old Oak (card 42, site-only), a selected Trade modifier with no cost:
  * "If trading with The Old Oak for [secret], gain one more [secret] if you
  * have any beast advisers."
  *
  * A Trade for secrets gains one secret per faceup adviser matching the
  * card's suit, and The Old Oak is a beast card. So with The Old Oak as the
  * card, the gain node holds a secret gain exactly when the player has a
  * faceup beast adviser, and a Trade for favor holds none. The power adds a
  * node after the gain that gains one more secret when the gain node holds a
  * secret gain for the player and the source decision was answered with The
  * Old Oak. The node is added whatever the Trade, so the window's node count
  * never depends on the answer. The generic gain line tells the secret.
  */
final case class TheOldOak private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = TheOldOakCard.id
  def id: PowerId = TheOldOak.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeGain -> Vector(Transform((ctx, operations) =>
      operations :+ bonus(ctx.activePlayer, operations.exists {
        case Gain.Secrets(player, _) => player == ctx.activePlayer
        case _ => false
      }))))

  private def bonus(actor: PlayerId, secrets: Boolean): Operation =
    BuildOps((_, pending) => Right(
      if secrets && PowerAnswers.one(pending, TradeProcedure.decisionId)
          .contains(DecisionOptionRef.Denizen(cardId)) then
        Vector[CoreOperation](Gain.Secrets(actor, TheOldOak.Secrets))
      else Vector.empty))

object TheOldOak:
  val id: PowerId = TheOldOakCard.power.id
  val Secrets: Int = 1

  def forCatalog(catalog: ExecutableCatalog): TheOldOak =
    new TheOldOak(catalog)
