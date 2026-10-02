package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, OptionRestriction, PowerCtx}
import oathdigital.gameplay.powers.CatalogResolution
import oathdigital.model._

object CircletOfCommandCard extends Relic(RelicId("R15"), "Circlet of Command", value = 12, defense = 0):
  val power = PrintedPower(PowerId("relic.circlet-of-command"),
    persistent = true, cost = Cost.free,
    text = "Players **cannot** target your banners or your other relics.")
  val powers: Vector[PrintedPower] = Vector(power)

/** The Circlet of Command (relic R15), a persistent rule of a faceup relic:
  * players other than the holder cannot target the holder's banners, or the
  * holder's relics other than the Circlet itself. A facedown Circlet does
  * nothing.
  *
  * "Target" means a Campaign's target selection only (catalog batch 3
  * rulings, "Target protections corrected"). So it hides options at a Raid's
  * optional targets (`CampaignTargetSelection`), which list the defender's
  * faceup relics and banners. A Challenge's banner choice and a played
  * Conspiracy's target are not targets. A Raid's mandatory target, the
  * defender's pawn, is not a banner or a relic and stays a target.
  *
  * Each option it hides writes "{Blue}'s banners and relics cannot be
  * targeted.", naming the holder (power log lines design, "Removed and
  * hidden options").
  */
final case class CircletOfCommand private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: RelicId = CircletOfCommandCard.id
  def id: PowerId = CircletOfCommand.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = Vector(CircletOfCommand.shielded)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(guard, (ctx, _) => note(ctx))))

  /** The line naming the holder whose things it protects. */
  private def note(ctx: PowerCtx): Option[PowerNote] =
    holder(ctx.state).map(owner => CircletOfCommand.shielded(
      PowerSourceRef.Card(cardId), NoteArg.Player(owner)))

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = Option.when(shields(ctx, ref))(
    OathViolation.InvalidEventOrder(
      "the Circlet of Command protects its holder's banners and relics"))

  /** The player holding this Circlet faceup, if any. */
  private def holder(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.relics.exists {
      case RelicState(`cardId`, Orientation.FaceUp, _) => true
      case _ => false
    }).map(_.player)

  /** Whether `ref` names a banner or a relic of the holder that the acting
    * player may not target.
    */
  private def shields(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    val current = ctx.state.game.current
    val faceupHolder = holder(ctx.state)
    def held(owner: PlayerId): Boolean = faceupHolder.contains(owner) &&
      owner != ctx.activePlayer
    ref match
      case DecisionOptionRef.Banner(banner) =>
        BannerRules.holder(current, banner).exists(held)
      case DecisionOptionRef.Relic(relic) => relic != cardId &&
        current.players.find(_.relics.exists(_.id == relic)).exists(p =>
          held(p.player))
      case DecisionOptionRef.RelicSlot(owner, slot) => held(owner) &&
        !current.players.find(_.player == owner).flatMap(_.relics.lift(slot))
          .exists(_.id == cardId)
      case _ => false

object CircletOfCommand:
  val id: PowerId = CircletOfCommandCard.power.id

  /** "{Blue}'s banners and relics cannot be targeted." */
  val shielded: NoteKey = NoteKey("shielded", Vector(NotePart.Arg(0),
    NotePart.Text("'s banners and relics cannot be targeted.")))

  def forCatalog(catalog: ExecutableCatalog): CircletOfCommand =
    new CircletOfCommand(catalog)
