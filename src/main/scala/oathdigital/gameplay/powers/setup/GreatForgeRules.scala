package oathdigital.gameplay.powers.setup

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, RelicDraws}
import oathdigital.model._

/** E06, both faces (2026-09-21 Chronicle design, "Setup powers"). See
  * `GreatMarketRules` for the `WhenExplored`/window-sharing rationale.
  */
sealed abstract class ForgeRule extends ContributingPower:
  def catalog: ExecutableCatalog
  def edifice: EdificeId
  protected def side: EdificeSide

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  private def at(ctx: PowerCtx): Option[(PlayerId, SiteId)] =
    for
      edificeSite <- EdificeSetupSupport.siteOf(ctx.state, edifice, side)
      placement <- EdificeSetupSupport.pawnPlacement(ctx)
      if placement._2 == edificeSite
    yield placement

  override def applicable(ctx: PowerCtx): Boolean = at(ctx).isDefined

  protected def build(ready: ReadyGame, actor: PlayerId, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]]

  /** The line this face writes after its effect. */
  protected def note(actor: PlayerId, at: SiteId): Note

  final def contributions: Map[PowerWindow, Vector[Contribution]] =
    val effect = Vector(Transform((ctx, ops) => at(ctx) match {
      case Some((actor, site)) => ops :+ BuildOps((ready, _) =>
        build(ready, actor, site)) :+ note(actor, site)
      case None => ops
    }))
    Map(PowerWindow.SetupPawnPlaced -> effect, PowerWindow.WhenExplored -> effect)

final case class GreatForge private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends ForgeRule:
  def id: PowerId = GreatForge.id
  protected def side: EdificeSide = EdificeSide.Intact

  override def noteKeys: Vector[NoteKey] = Vector(RelicDraws.drew)

  protected def note(actor: PlayerId, at: SiteId): Note =
    Note(id, RelicDraws.drewNote(PowerSourceRef.Card(edifice), actor))

  protected def build(ready: ReadyGame, actor: PlayerId, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] =
    Right(RelicDraws.takeTop(ready, actor))
object GreatForge:
  val id: PowerId = PowerId("edifice.e06.intact")
  def forCatalog(catalog: ExecutableCatalog): Option[GreatForge] =
    CatalogCards.edifice(catalog, id).map(new GreatForge(_, catalog))

final case class BrokenForge private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends ForgeRule:
  def id: PowerId = BrokenForge.id
  protected def side: EdificeSide = EdificeSide.Ruined

  override def noteKeys: Vector[NoteKey] = Vector(EdificeSetupSupport.discarded)

  /** The relics at sites before the step that no site holds after it. */
  protected def note(actor: PlayerId, at: SiteId): Note = Note(id, states => for
    step <- states.previous
    gone = BrokenForge.siteRelics(step._1)
      .filterNot(BrokenForge.siteRelics(step._2).contains)
    if gone.nonEmpty
  yield EdificeSetupSupport.discarded(PowerSourceRef.Card(edifice),
    NoteArg.Cards(gone)))

  protected def build(ready: ReadyGame, actor: PlayerId, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] =
    ready.game.current.map.regionOf(at).toRight(
      OathViolation.InvalidEventOrder(s"${at.value} is not in play")).map { region =>
      val current = ready.game.current
      current.map.inPlay.filter(s => current.map.regionOf(s).contains(region))
        .flatMap(s => current.map.sites(s).relics.map(relic => Discard.Relic(
          relic.id, PositionedLocation(Location.Site(s)), relic.tokens.secrets, actor)))
    }
object BrokenForge:
  val id: PowerId = PowerId("edifice.e06.ruined")

  /** Every relic at a site in play, in map order. */
  private def siteRelics(ready: ReadyGame): Vector[CardId] =
    val current = ready.game.current
    current.map.inPlay.flatMap(site => current.map.sites(site).relics.map(_.id))
  def forCatalog(catalog: ExecutableCatalog): Option[BrokenForge] =
    CatalogCards.edifice(catalog, id).map(new BrokenForge(_, catalog))
