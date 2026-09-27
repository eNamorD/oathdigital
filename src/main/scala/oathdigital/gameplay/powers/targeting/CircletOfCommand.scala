package oathdigital.gameplay.powers.targeting

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, OptionRestriction, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** The Circlet of Command (relic R15), a persistent rule of a faceup relic:
  * players other than the holder cannot target the holder's banners, or the
  * holder's relics other than the Circlet itself. A facedown Circlet does
  * nothing.
  *
  * It restricts the three decisions that name a banner or a relic of another
  * player:
  *
  *  - a Raid's optional targets (`CampaignTargetSelection`), which lists the
  *    defender's faceup relics and banners;
  *  - a Challenge's banner choice (`ChallengeBannerSelection`);
  *  - a played Conspiracy's target (`ConspiracyTargetSelection`). A decision
  *    left with no option is dropped there, so a Conspiracy with no target
  *    left plays and takes nothing.
  *
  * A Raid's mandatory target, the defender's pawn, is not a banner or a relic
  * and stays a target.
  *
  * Each option it hides writes "{Blue}'s banners and relics cannot be
  * targeted.", naming the holder (power log lines design, "Removed and
  * hidden options"). At a Conspiracy, whose decision it narrows with a
  * transform, the same line is a note put before the decision whenever it
  * drops an option, and in its place when it drops them all.
  */
final case class CircletOfCommand private (cardId: RelicId,
    catalog: ExecutableCatalog) extends ContributingPower:
  def id: PowerId = CircletOfCommand.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = Vector(CircletOfCommand.shielded)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection ->
      Vector(OptionRestriction(guard, (ctx, _) => note(ctx))),
    PowerWindow.ChallengeBannerSelection ->
      Vector(OptionRestriction(guard, (ctx, _) => note(ctx))),
    PowerWindow.ConspiracyTargetSelection -> Vector(Transform(dropShielded)))

  /** The line naming the holder whose things it protects. */
  private def note(ctx: PowerCtx): Option[PowerNote] =
    holder(ctx.state).map(owner => CircletOfCommand.shielded(
      PowerSourceRef.Card(cardId), NoteArg.Player(owner)))

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = Option.when(shields(ctx, ref))(
    OathViolation.InvalidEventOrder(
      "the Circlet of Command protects its holder's banners and relics"))

  /** The Conspiracy's decision without the options it protects, after the
    * note saying so when it dropped any. */
  private def dropShielded(ctx: PowerCtx, operations: Vector[Operation])
      : Vector[Operation] =
    val kept = operations.flatMap:
      case decide: Decide => decide.query match
        case one: DecisionQuery.ChooseOne =>
          val options = one.options.filterNot(option => shields(ctx, option.ref))
          if options.isEmpty then Vector.empty
          else Vector(decide.copy(query = one.copy(options = options)))
        case _ => Vector(decide)
      case other => Vector(other)
    if kept == operations then operations
    else note(ctx).map(said => Note(id, _ => Some(said))).toVector ++ kept

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
  val id: PowerId = PowerId("relic.circlet-of-command")

  /** "{Blue}'s banners and relics cannot be targeted." */
  val shielded: NoteKey = NoteKey("shielded", Vector(NotePart.Arg(0),
    NotePart.Text("'s banners and relics cannot be targeted.")))

  def forCatalog(catalog: ExecutableCatalog): Option[CircletOfCommand] =
    CatalogCards.relic(catalog, id).map(new CircletOfCommand(_, catalog))
