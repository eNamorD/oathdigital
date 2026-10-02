package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, OptionRestriction, PowerCtx}
import oathdigital.gameplay.powers.CatalogResolution
import oathdigital.model._

object ForgottenVaultCard extends Denizen(DenizenId("75"), "Forgotten Vault", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.forgotten-vault"),
    persistent = true, cost = Cost.free,
    text = "Enemies of Forgotten Vault's ruler **cannot** target relics " +
      "held by its ruler. If ruled by Empire, all Imperials have " +
      "this power.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Forgotten Vault (card 75, site-only), a persistent rule: enemies of the
  * Vault's ruler cannot target relics that ruler holds, as the Circlet of
  * Command protects its holder's relics. The ruler is the ruler of the Vault's
  * site. Ruled by bandits or unruled, it protects nothing, because bandits hold
  * no relics. Empire rulers are not supported.
  *
  * "Target" means a Campaign's target selection only (catalog batch 3
  * rulings, "Target protections corrected"). So it restricts a Raid's
  * optional targets (`CampaignTargetSelection`), through an
  * `OptionRestriction` whose hide hook notes each relic it hides. A played
  * Conspiracy may take the ruler's relic.
  *
  * The Game Log posts identical notes once per action.
  */
final case class ForgottenVault private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = ForgottenVaultCard.id
  def id: PowerId = ForgottenVault.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = Vector(ForgottenVault.shielded)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(guard, (ctx, _) => protectedRuler(ctx).map(said))))

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = Option.when(shields(ctx, ref))(
    OathViolation.InvalidEventOrder(
      "the Forgotten Vault protects its ruler's relics"))

  private def said(ruler: PlayerId): PowerNote =
    ForgottenVault.shielded(PowerSourceRef.Card(cardId), NoteArg.Player(ruler))

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
  val id: PowerId = ForgottenVaultCard.power.id

  /** "{ruler}'s relics cannot be targeted." */
  val shielded: NoteKey = NoteKey("shielded", Vector(NotePart.Arg(0),
    NotePart.Text("'s relics cannot be targeted.")))

  def forCatalog(catalog: ExecutableCatalog): ForgottenVault =
    new ForgottenVault(catalog)
