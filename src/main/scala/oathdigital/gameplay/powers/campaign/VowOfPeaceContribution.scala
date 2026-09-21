package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Vow of Peace: "You cannot campaign." A faceup copy held as an adviser blocks
  * its holder's whole Campaign, a `Restriction` at the action's root, the same
  * shape as Narrow Pass blocking a Travel. A facedown copy is not active, and
  * the card is adviser-only, so no other location is considered. The second
  * printed sentence, that attackers cannot sacrifice against a holder, is not
  * modelled (the legacy Campaign never modelled it either).
  */
final case class VowOfPeaceContribution private (cardId: DenizenId)
    extends ContributingPower {
  def id: PowerId = VowOfPeaceContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.CampaignActionEligibility ->
      Vector(Restriction((ctx, _) => blocked(ctx))))

  private def blocked(ctx: PowerCtx): Option[OathViolation] =
    Option.when(ctx.state.game.current.players
      .find(_.player == ctx.activePlayer).exists(_.advisers.exists {
        case card: DenizenState =>
          card.id == cardId && card.orientation == Orientation.FaceUp
        case _ => false
      }))(OathViolation.CampaignUnavailable(
        "Vow of Peace prevents its ruler from campaigning"))
}

object VowOfPeaceContribution {
  val id: PowerId = PowerId("denizen.vow-of-peace")

  /** `None` when the catalog has no such card, for example a test stub. */
  def forCatalog(catalog: ExecutableCatalog): Option[VowOfPeaceContribution] =
    catalog.denizens.find(_.handlers.contains(id.value))
      .map(card => new VowOfPeaceContribution(DenizenId(card.id.value)))
}
