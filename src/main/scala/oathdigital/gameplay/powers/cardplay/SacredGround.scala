package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.{Edifice, EdificeFace, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.gameplay.powers.setup.EdificeSetupSupport
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

object SacredGroundCard extends Edifice(EdificeId("E08"), Suit.Nomad):
  object intact extends EdificeFace("Sacred Ground") with Locked:
    val power = PrintedPower(PowerId("edifice.e08.intact"),
      persistent = true, cost = Cost.free,
      text = "Players **cannot** play Visions, except the Conspiracy, " +
        "faceup unless their pawn is at this site.")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Desecrated Ground"):
    val power = PrintedPower(PowerId("edifice.e08.ruined"),
      persistent = true, cost = Cost.free,
      text = "If an Exile reveals a Vision while their pawn is at this " +
        "site, they do not discard their current revealed Vision. " +
        "_They can have any number!_")
    val powers: Vector[PrintedPower] = Vector(power)

/** Sacred Ground (E08's intact face), a persistent rule: "Players cannot play
  * Visions, except the Conspiracy, faceup unless their pawn is at this site."
  *
  * It binds every player whose pawn is elsewhere, the site's own ruler
  * included. Its ruined face, Desecrated Ground, is not implemented; E08
  * never appears in a generated game.
  * When the look-ahead hides a faceup placement because of it, it writes
  * "{Red} cannot play a Vision faceup."
  */
final case class SacredGround private (edifice: EdificeId)
    extends ContributingPower:
  def id: PowerId = SacredGround.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  override def noteKeys: Vector[NoteKey] = Vector(VisionPlay.noFaceup)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup ->
      Vector(Restriction((ctx, _) => blocked(ctx), VisionPlay.note(edifice))))

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
