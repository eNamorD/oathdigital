package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.economy.{MusterSource, TradeProcedure}
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object OldSongsCard extends Denizen(DenizenId("229"), "Old Songs", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.old-songs"),
    persistent = false, cost = Cost.free,
    text = "You may trade with cards that have exactly one [secret] on " +
      "them.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Old Songs (card 229), a selected Trade modifier with no cost: the Trade
  * may also draw on a card that holds exactly one secret and no favor. A
  * card with one secret and any favor does not qualify, and a token-free
  * card qualifies as usual.
  *
  * It folds after the powers that add sources
  * ([[EconomySources.ExemptionPriority]]), so it also admits such a card
  * that Map Library or Skilled Merchants adds. The Trade's payment onto the
  * answered source is marked `intoOccupied`. It does not apply to a Muster.
  * It writes no line: the start line names it.
  */
final case class OldSongs private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = OldSongsCard.id
  def id: PowerId = OldSongs.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def priority: Int = EconomySources.ExemptionPriority

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeSourceSelection -> Vector(Transform((ctx, operations) =>
      EconomySources.exempted(ctx, operations, ref =>
        MusterSource.cardOf(ctx.state, ctx.actor, ref)
          .exists(_.tokens == OldSongs.OneSecret)))),
    PowerWindow.TradeCost -> Vector(Transform((ctx, operations) =>
      EconomySources.intoOccupied(ctx, operations,
        TradeProcedure.decisionId))))

object OldSongs:
  val id: PowerId = OldSongsCard.power.id
  /** What a card must hold for Old Songs to admit it. */
  val OneSecret: Tokens = Tokens(favor = 0, secrets = 1)

  def forCatalog(catalog: ExecutableCatalog): OldSongs =
    new OldSongs(catalog)
