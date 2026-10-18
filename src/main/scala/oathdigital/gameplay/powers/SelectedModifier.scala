package oathdigital.gameplay.powers

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  ModifierPicks, PowerCtx, PowerUse}
import oathdigital.model._

/** A power the player picks for a major action in the walk's modifier
  * decision (modifier walker design), and that then applies to that action
  * for free unless it names a cost.
  *
  * A modifier that names a `cost` pays it when it is picked, onto its card
  * (`selectionPayments`), whatever the modifier then does: the player owns
  * the choice to pick it, and the next pass of the decision sees what it
  * paid.
  *
  * `applicable` answers two questions, told apart by the window:
  *
  *  - At a `*ModifierSelection` window it asks "may the player select this
  *    now?": the power belongs to that action, its card is one the player may
  *    use (`PowerAccess`), and its `cost` is payable on its own, including the
  *    empty-card rule. A power already picked in `ctx.answered` always
  *    applies there.
  *  - At every other window it asks `appliesAt`, which reads only the node the
  *    power is hooked on. It must not read state the action itself changes,
  *    because the walker folds every window again on each resume and a fold
  *    that differs from the first one moves the parked position.
  *
  * The selection window is checked by action, so a Travel modifier is never
  * offered for a Search, whichever window a later power hooks.
  *
  * A power states what it does inside the walk as `effects`, by window. Its
  * resolution is read from the catalog (`persistent: false` is selected).
  */
trait SelectedModifier extends ContributingPower:
  def catalog: ExecutableCatalog
  /** The card the power is printed on. */
  def cardId: CardId
  /** The major actions the power may be selected for. */
  def actions: Set[MajorActionType]
  /** What selecting the power costs, placed onto its card. */
  def cost: Cost = Cost.free
  /** What the power does inside the walk, by window. */
  def effects: Map[PowerWindow, Vector[Contribution]]

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  final override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  final override lazy val contributions
      : Map[PowerWindow, Vector[Contribution]] = effects

  /** Whether the power applies at the node it is hooked on. */
  def appliesAt(ctx: PowerCtx): Boolean = true

  override def selectionCard: Option[CardId] = Some(cardId)

  /** Its use at a modifier-selection window: by the actor, from its card,
    * costing a secret when its printed cost or its `cost` has one. Inside
    * a Campaign nobody may be asked (consent design, ruling 2). */
  override def use(ctx: PowerCtx): Option[PowerUse] =
    for
      _ <- SelectedModifier.selectionAction(ctx.window)
      printed <- catalog.printedPower(id)
    yield PowerUse.of(ctx.activePlayer, printed, cardId,
      secret = cost.secret > 0 || cost.secretBurnt > 0,
      consent = ctx.window != PowerWindow.CampaignModifierSelection,
      places = cost.favor > 0 || cost.secret > 0)
  override def selectionWindows: Set[PowerWindow] =
    actions.map(SelectedModifier.selectionWindow)

  /** At its selection window: a picked power always applies there, so its
    * option restrictions reach the options left; an unpicked one applies
    * when the player may select it now. */
  final override def applicable(ctx: PowerCtx): Boolean =
    SelectedModifier.selectionAction(ctx.window) match
      case Some(action) => actions(action) &&
        (ModifierPicks.isPicked(ctx.answered, ctx.window, cardId) ||
          selectable(ctx.state, ctx.activePlayer))
      case None => appliesAt(ctx)

  /** The player may use the card, from where they act
    * ([[PowerAccess.locate]]), and can pay for it. */
  def selectable(ready: ReadyGame, actor: PlayerId): Boolean =
    PowerAccess.locate(ready, actor, cardId).isDefined &&
      Costs.affordable(ready, actor, Location.OnCard(cardId), cost)

  /** The pick's payment: the `cost`, onto the card. A modifier whose price
    * depends on the state, such as Spiteful Mirror's once per turn,
    * overrides it and `selectable`. */
  override def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] =
    if cost == Cost.free then Vector.empty else Vector(payment(actor))

  /** The payment, required, placed onto the card as `PayCost` places it. */
  protected final def payment(actor: PlayerId): CoreOperation =
    Costs.onCard(actor, cardId, cost, catalog)

object SelectedModifier:
  private val selection: Map[PowerWindow, MajorActionType] = Map(
    PowerWindow.SearchModifierSelection -> MajorActionType.Search,
    PowerWindow.TravelModifierSelection -> MajorActionType.Travel,
    PowerWindow.CampaignModifierSelection -> MajorActionType.Campaign,
    PowerWindow.MusterModifierSelection -> MajorActionType.Muster,
    PowerWindow.TradeModifierSelection -> MajorActionType.Trade,
    PowerWindow.ForgeModifierSelection -> MajorActionType.Forge,
    PowerWindow.RecoverModifierSelection -> MajorActionType.Recover,
    PowerWindow.ChallengeModifierSelection -> MajorActionType.Challenge)

  /** The action a modifier-selection window belongs to, or `None` for any
    * other window.
    */
  def selectionAction(window: PowerWindow): Option[MajorActionType] =
    selection.get(window)

  private val windowOf: Map[MajorActionType, PowerWindow] =
    selection.map(_.swap)

  /** The modifier-selection window of `action`. */
  def selectionWindow(action: MajorActionType): PowerWindow = windowOf(action)
