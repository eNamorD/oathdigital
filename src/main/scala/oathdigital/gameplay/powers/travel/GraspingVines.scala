package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, NoteSupport}
import oathdigital.model._

object GraspingVinesCard extends Denizen(DenizenId("178"), "Grasping Vines", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.grasping-vines"),
    persistent = true, cost = Cost.free,
    text = "Enemies traveling from any site ruled by Grasping Vines' " +
      "ruler **must** kill one warband on their board if able.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Grasping Vines (card 178), a persistent rule of a faceup site card: an enemy
  * traveling from a site ruled by the Vines' ruler kills one warband on their
  * own board if able. The Vines' ruler is the ruler of the site it stands at,
  * and that ruler is exempt.
  *
  * The kill is unconditional and not required: it is a plain `Kill` placed
  * before the pawn's move, which does nothing for a board with no warband, so a
  * traveller with none is not stopped.
  *
  * The kill writes "Killed 1 {Red} warband.", read from its step, so a
  * traveller with no warband reads nothing.
  */
final case class GraspingVines private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = GraspingVinesCard.id
  def id: PowerId = GraspingVines.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = Vector(GraspingVines.killed)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      kill(ctx).fold(operations)(_ ++ operations))))

  override def applicable(ctx: PowerCtx): Boolean = kill(ctx).nonEmpty

  /** The kill this Travel owes and its line, if it owes one. */
  private def kill(ctx: PowerCtx): Option[Vector[Operation]] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(route.player))
    if SiteRulers.rulerOf(ctx.state, route.source).contains(ruler)
    warband <- TravelPayments.ownWarband(ctx.state, route.player,
      GraspingVines.Warbands)
  yield Vector(Kill(warband, PositionedLocation(Location.PlayArea(route.player))),
    Note(id, NoteSupport.killedNote(GraspingVines.killed,
      PowerSourceRef.Card(cardId), route.player)))

object GraspingVines:
  val id: PowerId = GraspingVinesCard.power.id
  val Warbands: Int = 1
  val killed: NoteKey = NoteSupport.killedKey("killed")

  def forCatalog(catalog: ExecutableCatalog): GraspingVines =
    new GraspingVines(catalog)
