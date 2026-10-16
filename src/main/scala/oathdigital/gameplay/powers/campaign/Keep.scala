package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, PowerUse, Transform}
import oathdigital.model._

object KeepCard extends Denizen(DenizenId("5"), "Keep", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.keep"),
    persistent = false, cost = Cost.free,
    text = "If defeated in a raid, after the attacker takes what they " +
      "targeted, place your pawn at this site and end the action. " +
      "_Do not burn favor, move pawn, etc._")
  val powers: Vector[PrintedPower] = Vector(power)

/** Keep (card 5, site-only), a rule of a faceup site card: "If defeated in
  * a raid, after the attacker takes what they targeted, place your pawn at
  * this site and end the action. Do not burn favor, move pawn, etc."
  *
  * It applies when the Raid's defeated defender rules Keep's site once the
  * battle's losses are made. The takes stand. The Raid's consequences (the
  * facedown discards, the set-aside relics, the half-favor burn) are
  * dropped, and the relocation question is replaced: the defender's pawn
  * moves to Keep's site, or stays when it is already there. Nothing else in
  * the Raid follows; the effects at the Campaign's end still run.
  *
  * The catalog marks the power `persistent: false`, but it is not a choice,
  * so it keeps the default automatic resolution. Its line, "Ended the Raid
  * with {Blue}'s pawn at {site}.", restates a pawn move and covers it, so
  * the Campaign's closing line does not say the pawn was sent there.
  */
case object KeepRule extends ContributingPower:
  val cardId: DenizenId = KeepCard.id
  val id: PowerId = KeepCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  private val notes = NoteSet(id)
  /** "Ended the Raid with {Blue}'s pawn at {site}." */
  val ended: NoteKey = notes.key("ended", Vector(
    NotePart.Text("Ended the Raid with "), NotePart.Arg(0),
    NotePart.Text("'s pawn at "), NotePart.Arg(1), NotePart.Text(".")))

  override def noteKeys: Vector[NoteKey] = notes.declared

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignRaidConsequences ->
      Vector(Transform((_, _) => Vector.empty)),
    PowerWindow.CampaignRaidRelocation -> Vector(Transform((ctx, _) =>
      kept(ctx.state).toVector.map((defender, site) =>
        placed(defender, site)))))

  override def applicable(ctx: PowerCtx): Boolean = kept(ctx.state).nonEmpty

  /** The defender's use of Keep, which a power-use restriction judges where
    * its window folds: it replaces the Raid's consequences, so it cannot sit
    * in an `AutomaticUse` (catalog batch 8, slice 10). */
  override def use(ctx: PowerCtx): Option[PowerUse] =
    kept(ctx.state).map((defender, _) =>
      PowerUse.automatic(defender, id, cardId))

  /** The Raid's defeated defender and Keep's site, when that defender rules
    * it. Both windows Keep folds come only after a Raid's victory, so the
    * recorded result is this Raid's. */
  def kept(ready: ReadyGame): Option[(PlayerId, SiteId)] = for
    result <- ready.game.current.lastCampaignResult
    defender <- Some(result.defender).collect {
      case CampaignDefender.Player(player) => player }
    site <- SiteRulers.siteOf(ready, cardId)
    if SiteRulers.rulerOf(ready, site).contains(SiteRuler.Player(defender))
  yield (defender, site)

  /** The defender's pawn placed at `site`, unless it stands there, then the
    * line. The line covers the move it restates. */
  private def placed(defender: PlayerId, site: SiteId): Operation =
    Branch((ready, _) => {
      val from = ready.game.current.players.find(_.player == defender)
        .flatMap(_.pawnSite).filter(_ != site)
      from.fold(Vector[Operation](line(defender, site, covers = false)))(
        origin => Vector[Operation](
          BuildOps((_, _) => Right(Vector[CoreOperation](Move(
            Piece.Pawn(defender), PositionedLocation(Location.Site(origin)),
            PositionedLocation(Location.Site(site)))))),
          line(defender, site, covers = true)))
    })

  private def line(defender: PlayerId, site: SiteId, covers: Boolean)
      : Note = Note(id, _ => Some(ended(PowerSourceRef.Card(cardId),
    NoteArg.Player(defender), NoteArg.Site(site))), covers)
