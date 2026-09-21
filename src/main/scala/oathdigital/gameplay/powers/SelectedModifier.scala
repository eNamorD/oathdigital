package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Transform}
import oathdigital.model._

/** A power the player selects in a command's `modifiers` at the start of a
  * major action, and that then applies to that action for free unless it names
  * a cost.
  *
  * A modifier that names a `cost` pays it at the very start of its action,
  * whatever the modifier then does: the kit prepends the payment to the root of
  * the action's tree (the action's eligibility window). The player owns the
  * choice to select it, so the payment is made whether or not the modifier then
  * has an effect. The same payment is `selectionPayments`, so a command that
  * selects several modifiers dry-runs all of their payments together and refuses
  * a combination the player cannot pay, before anything happens.
  *
  * `applicable` answers three questions, told apart by the window:
  *
  *  - At a `*ModifierSelection` window it asks "may the player select this
  *    now?": the power belongs to that action, its card is one the player may
  *    use (`PowerAccess`), and its `cost` is payable on its own, including the
  *    empty-card rule.
  *  - At the action's eligibility window (the root, where the payment goes) a
  *    selected modifier always applies.
  *  - At every other window it asks `appliesAt`, which reads only the node the
  *    power is hooked on. It must not read state the action itself changes,
  *    because the walker folds every window again on each resume and a fold
  *    that differs from the first one moves the parked position.
  *
  * The selection window is checked by action, so a Travel modifier is never
  * offered for a Search, whichever window a later power hooks.
  *
  * A power states what it does inside the walk as `effects`, by window, and the
  * kit adds the payment to them. Its resolution is read from the catalog
  * (`persistent: false` is selected).
  */
trait SelectedModifier extends ContributingPower {
  def catalog: ExecutableCatalog
  /** The card the power is printed on. */
  def cardId: CardId
  /** The major actions at whose start the power may be selected. */
  def actions: Set[MajorActionType]
  /** What selecting the power costs, placed onto its card. */
  def cost: Cost = Cost.free
  /** What the power does inside the walk, by window. */
  def effects: Map[PowerWindow, Vector[Contribution]]

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  final override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  final override lazy val contributions: Map[PowerWindow, Vector[Contribution]] = {
    val payments: Map[PowerWindow, Vector[Contribution]] =
      if (cost == Cost.free) Map.empty
      else actions.map(action => SelectedModifier.eligibility(action) ->
        Vector[Contribution](Transform((ctx, operations) =>
          payment(ctx.activePlayer) +: operations))).toMap
    (payments.keySet ++ effects.keySet).map(window => window ->
      (payments.getOrElse(window, Vector.empty) ++
        effects.getOrElse(window, Vector.empty))).toMap
  }

  /** Whether the power applies at the node it is hooked on. */
  def appliesAt(ctx: PowerCtx): Boolean = true

  final override def applicable(ctx: PowerCtx): Boolean =
    SelectedModifier.selectionAction(ctx.window) match {
      case Some(action) => actions(action) &&
        selectable(ctx.state, ctx.activePlayer)
      case None => SelectedModifier.isEligibility(ctx.window) ||
        appliesAt(ctx)
    }

  /** The player may use the card, and can pay for it. */
  def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    PowerAccess.locate(ready, actor, cardId).isDefined &&
      Costs.affordable(ready, actor, Location.OnCard(cardId), cost)

  final override def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] =
    if (cost == Cost.free) Vector.empty else Vector(payment(actor))

  /** The payment, required, placed onto the card as `PayCost` places it. */
  protected final def payment(actor: PlayerId): CoreOperation =
    Costs.onCard(actor, cardId, cost, catalog)
}

object SelectedModifier {
  private val selection: Map[PowerWindow, MajorActionType] = Map(
    PowerWindow.SearchModifierSelection -> MajorActionType.Search,
    PowerWindow.TravelModifierSelection -> MajorActionType.Travel,
    PowerWindow.CampaignModifierSelection -> MajorActionType.Campaign,
    PowerWindow.MusterModifierSelection -> MajorActionType.Muster,
    PowerWindow.TradeModifierSelection -> MajorActionType.Trade,
    PowerWindow.ForgeModifierSelection -> MajorActionType.Forge,
    PowerWindow.RecoverModifierSelection -> MajorActionType.Recover,
    PowerWindow.ChallengeModifierSelection -> MajorActionType.Challenge)

  private val eligibilityWindows: Map[MajorActionType, PowerWindow] = Map(
    MajorActionType.Search -> PowerWindow.SearchActionEligibility,
    MajorActionType.Travel -> PowerWindow.TravelActionEligibility,
    MajorActionType.Campaign -> PowerWindow.CampaignActionEligibility,
    MajorActionType.Muster -> PowerWindow.MusterActionEligibility,
    MajorActionType.Trade -> PowerWindow.TradeActionEligibility,
    MajorActionType.Forge -> PowerWindow.ForgeActionEligibility,
    MajorActionType.Recover -> PowerWindow.RecoverActionEligibility,
    MajorActionType.Challenge -> PowerWindow.ChallengeActionEligibility)

  /** The action a modifier-selection window belongs to, or `None` for any
    * other window.
    */
  def selectionAction(window: PowerWindow): Option[MajorActionType] =
    selection.get(window)

  /** The window at the root of `action`'s tree, where a payment goes. */
  def eligibility(action: MajorActionType): PowerWindow =
    eligibilityWindows(action)

  def isEligibility(window: PowerWindow): Boolean =
    eligibilityWindows.valuesIterator.contains(window)
}
