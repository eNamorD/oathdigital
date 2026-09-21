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
  */
final case class CircletOfCommand private (cardId: RelicId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = CircletOfCommand.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignTargetSelection -> Vector(OptionRestriction(guard)),
    PowerWindow.ChallengeBannerSelection -> Vector(OptionRestriction(guard)),
    PowerWindow.ConspiracyTargetSelection -> Vector(Transform(dropShielded)))

  private def guard(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[OathViolation] = Option.when(shields(ctx, ref))(
    OathViolation.InvalidEventOrder(
      "the Circlet of Command protects its holder's banners and relics"))

  private def dropShielded(ctx: PowerCtx, operations: Vector[Operation])
      : Vector[Operation] = operations.flatMap {
    case decide: Decide => decide.query match {
      case one: DecisionQuery.ChooseOne =>
        val options = one.options.filterNot(option => shields(ctx, option.ref))
        if (options.isEmpty) Vector.empty
        else Vector(decide.copy(query = one.copy(options = options)))
      case _ => Vector(decide)
    }
    case other => Vector(other)
  }

  /** Whether `ref` names a banner or a relic of the holder that the acting
    * player may not target.
    */
  private def shields(ctx: PowerCtx, ref: DecisionOptionRef): Boolean = {
    val current = ctx.state.game.current
    val holder = current.players.find(_.relics.exists {
      case RelicState(`cardId`, Orientation.FaceUp, _) => true
      case _ => false
    }).map(_.player)
    def held(owner: PlayerId): Boolean = holder.contains(owner) &&
      owner != ctx.activePlayer
    ref match {
      case DecisionOptionRef.Banner(banner) =>
        BannerRules.holder(current, banner).exists(held)
      case DecisionOptionRef.Relic(relic) => relic != cardId &&
        current.players.find(_.relics.exists(_.id == relic)).exists(p =>
          held(p.player))
      case DecisionOptionRef.RelicSlot(owner, slot) => held(owner) &&
        !current.players.find(_.player == owner).flatMap(_.relics.lift(slot))
          .exists(_.id == cardId)
      case _ => false
    }
  }
}

object CircletOfCommand {
  val id: PowerId = PowerId("relic.circlet-of-command")

  def forCatalog(catalog: ExecutableCatalog): Option[CircletOfCommand] =
    CatalogCards.relic(catalog, id).map(new CircletOfCommand(_, catalog))
}
