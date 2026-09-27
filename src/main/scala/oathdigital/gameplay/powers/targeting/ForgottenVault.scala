package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, OptionRestriction, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Forgotten Vault (card 75, site-only), a persistent rule: enemies of the
  * Vault's ruler cannot target relics that ruler holds, as the Circlet of
  * Command protects its holder's relics. The ruler is the ruler of the Vault's
  * site. Ruled by bandits or unruled, it protects nothing, because bandits hold
  * no relics. Empire rulers are not supported.
  *
  * It restricts the two decisions that name another player's relic:
  *
  *  - a Raid's optional targets (`CampaignTargetSelection`), through an
  *    `OptionRestriction` whose hide hook notes each relic it hides;
  *  - a played Conspiracy's target (`ConspiracyTargetSelection`), through a
  *    `Transform`, as the Circlet's is, because a decision left with no option
  *    is dropped there. The same note goes before the narrowed decision, or
  *    in its place.
  *
  * The Game Log posts identical notes once per action.
  */
final case class ForgottenVault private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower:
  def id: PowerId = ForgottenVault.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = Vector(ForgottenVault.shielded)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(guard, (ctx, _) => protectedRuler(ctx).map(said))),
    PowerWindow.ConspiracyTargetSelection -> Vector(Transform(dropShielded)))

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = Option.when(shields(ctx, ref))(
    OathViolation.InvalidEventOrder(
      "the Forgotten Vault protects its ruler's relics"))

  private def said(ruler: PlayerId): PowerNote =
    ForgottenVault.shielded(PowerSourceRef.Card(cardId), NoteArg.Player(ruler))

  private def dropShielded(ctx: PowerCtx, operations: Vector[Operation])
      : Vector[Operation] = operations.flatMap:
    case decide: Decide => decide.query match
      case one: DecisionQuery.ChooseOne =>
        val options = one.options.filterNot(option => shields(ctx, option.ref))
        if options.size == one.options.size then Vector(decide)
        else
          val note = protectedRuler(ctx).toVector.map(ruler =>
            Note(id, _ => Some(said(ruler))))
          if options.isEmpty then note
          else note :+ decide.copy(query = one.copy(options = options))
      case _ => Vector(decide)
    case other => Vector(other)

  /** The Vault's ruler, when the acting player is one of their enemies. */
  private def protectedRuler(ctx: PowerCtx): Option[PlayerId] =
    SiteRulers.rulerOfCard(ctx.state, cardId).collect {
      case ruler @ SiteRuler.Player(owner)
          if SiteRule.enemies(ruler, SiteRuler.Player(ctx.activePlayer)) => owner
    }

  /** Whether `ref` names a relic the protected ruler holds. */
  private def shields(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    protectedRuler(ctx).exists(owner => ref match
      case DecisionOptionRef.Relic(relic) =>
        ctx.state.game.current.players.exists(p =>
          p.player == owner && p.relics.exists(_.id == relic))
      case DecisionOptionRef.RelicSlot(slotOwner, _) => slotOwner == owner
      case _ => false)

object ForgottenVault:
  val id: PowerId = PowerId("denizen.forgotten-vault")

  /** "{ruler}'s relics cannot be targeted." */
  val shielded: NoteKey = NoteKey("shielded", Vector(NotePart.Arg(0),
    NotePart.Text("'s relics cannot be targeted.")))

  def forCatalog(catalog: ExecutableCatalog): Option[ForgottenVault] =
    CatalogCards.denizen(catalog, id).map(new ForgottenVault(_, catalog))
