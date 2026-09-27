package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{PowerAccess, SiteRulers}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Secret Police (card 113), a persistent rule of a site card: "Enemies cannot
  * play Visions faceup while their pawn is at any site ruled by Secret
  * Police's ruler."
  *
  * The Police's ruler is the ruler of the site it stands at. A player ruler
  * binds every other player whose pawn is at a site that player rules. A
  * Bandit ruler binds every player at any Bandit-ruled site, the Police site
  * included, since bandits are every player's enemy. An Empire ruler binds no
  * one until the Empire phase decides otherwise. The rule binds players who
  * cannot use the card, so the card is found on the map, as Toll Roads is,
  * and not through `PowerAccess`.
  * When the look-ahead hides a faceup placement because of it, it writes
  * "{Red} cannot play a Vision faceup."
  */
final case class SecretPolice private (cardId: DenizenId)
    extends ContributingPower:
  def id: PowerId = SecretPolice.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  override def noteKeys: Vector[NoteKey] = Vector(VisionPlay.noFaceup)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup ->
      Vector(Restriction((ctx, _) => blocked(ctx), VisionPlay.note(cardId))))

  private def blocked(ctx: PowerCtx): Option[OathViolation] = for
    _ <- VisionPlay.pending(ctx)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(ctx.activePlayer))
    pawn <- PowerAccess.pawnSite(ctx.state, ctx.activePlayer)
    if SiteRulers.rulerOf(ctx.state, pawn).contains(ruler)
  yield VisionPlay.forbidden("Secret Police")

object SecretPolice:
  val id: PowerId = PowerId("denizen.secret-police")

  def forCatalog(catalog: ExecutableCatalog): Option[SecretPolice] =
    CatalogCards.denizen(catalog, id).map(new SecretPolice(_))
