package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.{PowerAccess, SiteRulers}
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

object SecretPoliceCard extends Denizen(DenizenId("113"), "Secret Police", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.secret-police"),
    persistent = true, cost = Cost.free,
    text = "Enemies **cannot** play Visions faceup while their pawn is " +
      "at any site ruled by Secret Police's ruler.")
  val powers: Vector[PrintedPower] = Vector(power)

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
  * Its ruler may consent to a faceup play (consent design, slice 3); bandits
  * and the Empire never do.
  */
case object SecretPolice extends ContributingPower:
  val cardId: DenizenId = SecretPoliceCard.id
  val id: PowerId = SecretPoliceCard.power.id

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  private val notes = NoteSet(id)
  /** "Cannot play a Vision faceup without ruler's consent.": the reason of
    * a faceup play the Police's ruler may be asked about. */
  val noFaceupConsent: NoteKey = notes.key("no-faceup-consent", Vector(
    NotePart.Text("Cannot play a Vision faceup without ruler's consent.")))
  notes.include(VisionPlay.notes)

  override def noteKeys: Vector[NoteKey] = notes.declared

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup ->
      Vector(Restriction((ctx, _) => blocked(ctx), VisionPlay.note(cardId),
        Some(waiver))))

  /** "Enemies cannot" (consent design, ruling 1): the player who rules the
    * Police's site may waive it. */
  private def waiver: Waiver = Waiver(
    ready => SiteRulers.rulerOfCard(ready, cardId).collect {
      case SiteRuler.Player(player) => player },
    PowerSourceRef.Card(cardId),
    (_, _) => noFaceupConsent(PowerSourceRef.Card(cardId)))

  private def blocked(ctx: PowerCtx): Option[OathViolation] = for
    _ <- VisionPlay.pending(ctx)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(ctx.actor))
    pawn <- PowerAccess.pawnSite(ctx.state, ctx.actor)
    if SiteRulers.rulerOf(ctx.state, pawn).contains(ruler)
  yield VisionPlay.forbidden("Secret Police")
