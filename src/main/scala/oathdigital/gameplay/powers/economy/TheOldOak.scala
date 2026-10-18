package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Deferred,
  OperationRewrite, Transform}
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
  * faceup beast adviser, and a Trade for favor holds none. The power records
  * the player's secret gain as it runs (operation rewrite design, "record"),
  * and adds a step after the gain that gains one more secret when a gain was
  * recorded and the source decision was answered with The Old Oak. The step
  * is added whatever the Trade, so the window's node count never depends on
  * the answer. The generic gain line tells the secret.
  */
final case class TheOldOak private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = TheOldOakCard.id
  def id: PowerId = TheOldOak.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeGain -> Vector(Transform((ctx, operations) =>
      OperationRewrite.ops(ctx, operations)((_, _) => {
        // The record comes first: a leaf after the gain would stand
        // between it and the Trade's own note, which restates the leaf
        // before it (`TradeProcedure.tradedNote`).
        case gain @ Gain.Secrets(player, _) if player == ctx.actor =>
          Vector(Defer(TheOldOak.deferred, Vector(gain)), gain)
      }) :+ bonus(ctx.actor))))

  /** One more secret, when the Trade's gain was recorded and its source was
    * The Old Oak. */
  private def bonus(actor: PlayerId): Operation =
    Deferred.resolve(TheOldOak.deferred)((_, pending, _) =>
      if PowerAnswers.one(pending, TradeProcedure.decisionId)
          .contains(DecisionOptionRef.Denizen(cardId)) then
        Vector(Gain.Secrets(actor, TheOldOak.Secrets))
      else Vector.empty)

object TheOldOak:
  val id: PowerId = TheOldOakCard.power.id
  val Secrets: Int = 1
  /** The Trade's secret gain, recorded as it runs. */
  val deferred: DeferralKey = DeferralKey("the-old-oak")

  def forCatalog(catalog: ExecutableCatalog): TheOldOak =
    new TheOldOak(catalog)
