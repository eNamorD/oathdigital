package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.gameplay.powers.setup.EdificeSetupSupport
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Sacred Ground (E08's intact face), a persistent rule: "Players cannot play
  * Visions, except the Conspiracy, faceup unless their pawn is at this site."
  *
  * It binds every player whose pawn is elsewhere, the site's own ruler
  * included. Its ruined face, Desecrated Ground, is not implemented; E08
  * never appears in a generated game.
  */
final case class SacredGround private (edifice: EdificeId)
    extends ContributingPower:
  def id: PowerId = SacredGround.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup ->
      Vector(Restriction((ctx, _) => blocked(ctx))))

  private def blocked(ctx: PowerCtx): Option[OathViolation] = for
    vision <- VisionPlay.pending(ctx)
    if vision != VisionRules.Conspiracy
    site <- EdificeSetupSupport.siteOf(ctx.state, edifice, EdificeSide.Intact)
    if ctx.state.game.current.map.inPlay.contains(site)
    if !PowerAccess.pawnSite(ctx.state, ctx.activePlayer).contains(site)
  yield VisionPlay.forbidden("Sacred Ground")

object SacredGround:
  val id: PowerId = PowerId("edifice.e08.intact")

  def forCatalog(catalog: ExecutableCatalog): Option[SacredGround] =
    CatalogCards.edifice(catalog, id).map(new SacredGround(_))
