package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, RuledCards}
import oathdigital.model._

object LostTongueCard extends Denizen(DenizenId("157"), "Lost Tongue", Suit.Nomad) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.lost-tongue"),
    persistent = true, cost = Cost.free,
    text = "Other players **cannot** target or take your relics or " +
      "banners in any way unless they rule a [suit-nomad] card.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Lost Tongue (card 157, adviser-only), a persistent rule of a faceup
  * adviser: "Other players cannot target or take your relics or banners in
  * any way unless they rule a [nomad] card."
  *
  * Each part reads, when it applies, whether the acting player rules a nomad
  * card ([[oathdigital.gameplay.powers.RuledCards]]):
  *
  *  - **Target.** "Target" is a Campaign's target selection only. A Raid's
  *    optional targets (`CampaignTargetSelection`) hide the holder's relics
  *    and banners, as the Circlet of Command hides them. Each hidden option
  *    writes "{Blue}'s banners and relics cannot be targeted.", naming the
  *    holder.
  *  - **Take.** A registered operation restriction refuses a `Take` of a
  *    relic or a banner from the holder's board. It refuses the `Take`
  *    operation only: a `Give`, such as a Conspiracy's relic or an agreed
  *    Negotiation transfer, is allowed, and so is taking a banner's contents.
  *    A banner's leaving operations are children of its `Take`, so a refused
  *    `Take` leaves the banner whole. A played Conspiracy, which takes a
  *    banner by a `Take`, is not offered the holder's banners
  *    ([[ConspiracyTargets]]). A refused operation is never offered, so the
  *    `Take` part writes no line.
  *
  * Bandits hold nothing and take nothing, so they are never refused.
  */
final case class LostTongue private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = LostTongueCard.id
  def id: PowerId = LostTongue.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = Vector(LostTongue.shielded)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection -> Vector(OptionRestriction(guard,
      (ctx, _) => protectedHolder(ctx.state, ctx.activePlayer).map(owner =>
        LostTongue.shielded(PowerSourceRef.Card(cardId),
          NoteArg.Player(owner))))),
    PowerWindow.ConspiracyTargetSelection -> Vector(Transform((ctx, operations) =>
      ConspiracyTargets.narrowed(id, operations, bannerShielded(ctx, _), None))))

  override def operationRestrictions: Vector[OperationRestriction] =
    Vector(takes)

  /** Refuses a `Take` of the holder's relic or banner by a player who rules
    * no nomad card. */
  private object takes extends OperationRestriction:
    def reason(ready: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] = operation match
      case Take(piece, taker, Location.PlayArea(owner), _, _, _, _)
          if relicOrBanner(piece) &&
            protectedHolder(ready, taker).contains(owner) =>
        Some(OperationReason("lost-tongue", s"${owner.value}'s Lost Tongue " +
          s"keeps ${taker.value} from taking their relics and banners",
          OperationReasonKind.Impossible))
      case _ => None

  private def relicOrBanner(piece: Piece): Boolean = piece match
    case Piece.Card(_: RelicId) | Piece.Banner(_) => true
    case _ => false

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = Option.when(shields(ctx, ref))(
    OathViolation.InvalidEventOrder(
      "Lost Tongue protects its holder's relics and banners"))

  /** Whether `ref` names a relic or banner of the protected holder. */
  private def shields(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    val current = ctx.state.game.current
    protectedHolder(ctx.state, ctx.activePlayer).exists(owner => ref match
      case DecisionOptionRef.Banner(banner) =>
        BannerRules.holder(current, banner).contains(owner)
      case DecisionOptionRef.Relic(relic) => current.players.exists(p =>
        p.player == owner && p.relics.exists(_.id == relic))
      case DecisionOptionRef.RelicSlot(slotOwner, _) => slotOwner == owner
      case _ => false)

  /** Whether `ref` names a banner of the protected holder. */
  private def bannerShielded(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    ref match
      case _: DecisionOptionRef.Banner => shields(ctx, ref)
      case _ => false

  /** The holder of this card faceup, when `actor` is another player who rules
    * no nomad card. */
  private def protectedHolder(ready: ReadyGame, actor: PlayerId)
      : Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player).filter(owner => owner != actor &&
      RuledCards.of(catalog, ready, SiteRuler.Player(actor), Suit.Nomad).isEmpty)

object LostTongue:
  val id: PowerId = LostTongueCard.power.id

  /** "{Blue}'s banners and relics cannot be targeted." */
  val shielded: NoteKey = NoteKey("shielded", Vector(NotePart.Arg(0),
    NotePart.Text("'s banners and relics cannot be targeted.")))

  def forCatalog(catalog: ExecutableCatalog): LostTongue =
    new LostTongue(catalog)
