package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.economy.MusterSource
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx, Transform}
import oathdigital.model._

object VowOfBeastkinCard extends Denizen(DenizenId("196"), "Vow of Beastkin", Suit.Beast) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-beastkin"),
    persistent = false, cost = Cost.free,
    text = "You **must** muster on a card matching any of your " +
      "advisers, but you gain one more warband.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Vow of Beastkin (card 196, adviser-only, locked), an automatic rule of a
  * faceup adviser: its holder may Muster only on a card of the same suit as
  * one of their faceup advisers, and every Muster gains one more warband.
  *
  * The source rule is an `OptionRestriction` on the Muster's source
  * decision, so another card is not offered and an answer naming one is
  * refused. The Vow is itself a faceup Beast adviser, so a Beast card always
  * qualifies. The hidden sources write no line.
  *
  * The extra warband joins the Muster's own gain, and the line follows that
  * gain at once ([[ExtraWarbands]]): "Gained {n} more warband." An empty
  * warband bank writes nothing. The catalog marks the power
  * `persistent: false`, but it is a must, so it keeps the default automatic
  * resolution.
  */
final case class VowOfBeastkin private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = VowOfBeastkinCard.id
  def id: PowerId = VowOfBeastkin.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  override def noteKeys: Vector[NoteKey] = Vector(VowOfBeastkin.gained)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterSourceSelection -> Vector(OptionRestriction(refused)),
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      ExtraWarbands.added(ctx, id, cardId, VowOfBeastkin.Warbands,
        operations))))

  override def applicable(ctx: PowerCtx): Boolean =
    holds(ctx.state, ctx.activePlayer)

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })

  /** Refuses a source whose suit matches none of the holder's faceup
    * advisers. */
  private def refused(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = for
    card <- cardOf(ref)
    suit <- catalog.suitOf(card)
    if MusterSource.matching(catalog, ctx.state, ctx.activePlayer, suit) == 0
    site <- PowerAccess.pawnSite(ctx.state, ctx.activePlayer)
  yield OathViolation.EconomyCardUnavailable(site, card)

  private def cardOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(card) => Some(card)
    case DecisionOptionRef.Edifice(card) => Some(card)
    case _ => None

object VowOfBeastkin:
  val id: PowerId = VowOfBeastkinCard.power.id
  val Warbands: Int = 1
  /** "Gained {n} more warband." */
  val gained: NoteKey = ExtraWarbands.gained

  def forCatalog(catalog: ExecutableCatalog): VowOfBeastkin =
    new VowOfBeastkin(catalog)
