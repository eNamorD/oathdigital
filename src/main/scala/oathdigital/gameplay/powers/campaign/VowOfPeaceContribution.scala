package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Vow of Peace: "You cannot campaign. Attackers cannot sacrifice warbands to
  * increase their attack against you." A faceup copy held as an adviser does
  * both. A facedown copy is not active, and the card is adviser-only, so no
  * other location is considered.
  *
  * The first sentence is a `Restriction` at the Campaign's root, the same
  * shape as Narrow Pass blocking a Travel. The second is a `Transform` at
  * `CampaignSacrificeSelection` that removes the attacker's sacrifice decision
  * when the defender holds the Vow, so the battle reads a sacrifice of zero.
  * In a Conquest the defender is the ruler of the targets. The defender's own
  * sacrifices, such as Wrestlers', are not the attacker's and stay.
  */
final case class VowOfPeaceContribution private (cardId: DenizenId)
    extends ContributingPower:
  def id: PowerId = VowOfPeaceContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignActionEligibility ->
      Vector(Restriction((ctx, _) => blocked(ctx))),
    PowerWindow.CampaignSacrificeSelection ->
      Vector(Transform((ctx, children) =>
        if defenderHolds(ctx) then Vector.empty else children)))

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case card: DenizenState =>
        card.id == cardId && card.orientation == Orientation.FaceUp
      case _ => false
    })

  private def blocked(ctx: PowerCtx): Option[OathViolation] =
    Option.when(holds(ctx.state, ctx.activePlayer))(
      OathViolation.CampaignUnavailable(
        "Vow of Peace prevents its ruler from campaigning"))

  /** Whether this Campaign's defender is a player holding the Vow faceup. */
  private def defenderHolds(ctx: PowerCtx): Boolean =
    CampaignSetup.setup(ctx.state, ctx.activePlayer,
      PendingTree(ctx.nodePath, ctx.answered)).exists(_.defender match {
        case CampaignDefender.Player(player) => holds(ctx.state, player)
        case CampaignDefender.Bandits => false
      })

object VowOfPeaceContribution:
  val id: PowerId = PowerId("denizen.vow-of-peace")

  /** `None` when the catalog has no such card, for example a test stub. */
  def forCatalog(catalog: ExecutableCatalog): Option[VowOfPeaceContribution] =
    catalog.denizenWithPower(id)
      .map(card => new VowOfPeaceContribution(DenizenId(card.id.value)))
