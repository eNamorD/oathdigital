package oathdigital.gameplay.powers.setup

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.operations.DiscardRestrictions
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, PlayerFacts}
import oathdigital.model._

/** E22, both faces (2026-09-21 Chronicle design, "Setup powers"). See
  * `GreatMarketRules` for the `WhenExplored`/window-sharing rationale.
  */
final case class ProvingGrounds private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends ContributingPower {
  def id: PowerId = ProvingGrounds.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  private def at(ctx: PowerCtx): Option[(PlayerId, SiteId)] =
    for {
      edificeSite <- EdificeSetupSupport.siteOf(ctx.state, edifice, EdificeSide.Intact)
      placement <- EdificeSetupSupport.pawnPlacement(ctx)
      if placement._2 == edificeSite
    } yield placement

  override def applicable(ctx: PowerCtx): Boolean = at(ctx).isDefined

  private def build(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector(Gain.Warbands(actor, kind, 3)))

  def contributions: Map[PowerWindow, Vector[Contribution]] = {
    val effect = Vector(Transform((ctx, ops) => at(ctx) match {
      case Some((actor, _)) => ops :+ BuildOps((ready, _) => build(ready, actor))
      case None => ops
    }))
    Map(PowerWindow.SetupPawnPlaced -> effect, PowerWindow.WhenExplored -> effect)
  }
}
object ProvingGrounds {
  val id: PowerId = PowerId("edifice.e22.intact")
  def forCatalog(catalog: ExecutableCatalog): Option[ProvingGrounds] =
    CatalogCards.edifice(catalog, id).map(new ProvingGrounds(_, catalog))
}

/** Discards all OTHER denizens in this region -- edifices count as denizens
  * for this clause specifically (design spec table), and a discarded ruined
  * edifice returns to the edifice deck via `Discard.RuinedEdifice`; an
  * intact edifice is locked and stays, mirroring `Dazzle`. The actor is the
  * first player, per the spec's "the actor for powers with no 'you' is the
  * first player" (SetupEnd has no single "you").
  */
final case class EmptyGrounds private (edifice: EdificeId, catalog: ExecutableCatalog)
    extends ContributingPower {
  def id: PowerId = EmptyGrounds.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  private def at(ready: ReadyGame): Option[SiteId] =
    EdificeSetupSupport.siteOf(ready, edifice, EdificeSide.Ruined)

  override def applicable(ctx: PowerCtx): Boolean = at(ctx.state).isDefined

  private def build(ready: ReadyGame, site: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] = {
    val current = ready.game.current
    val actor = ready.setup.firstPlayer
    current.map.regionOf(site).toRight(OathViolation.InvalidEventOrder(
      s"${site.value} is not in play")).flatMap { region =>
      val destination = CardPlay.nextRegion(region)
      val candidates = current.map.inPlay.filter(s =>
        current.map.regionOf(s).contains(region)).flatMap(s =>
        current.map.sites(s).denizens.map(s -> _))
        .filterNot { case (s, card) => s == site && card.id.value == edifice.value }
      candidates.foldLeft[Either[OathViolation, Vector[CoreOperation]]](Right(Vector.empty)) {
        case (acc, (siteId, card)) => for {
          operations <- acc
          suit <- catalog.suitOf(card.id).toRight(card match {
            case denizen: DenizenState => OathViolation.UnknownWorldCard(denizen.id)
            case edifice: EdificeState => OathViolation.UnknownEdifice(edifice.id)
          })
        } yield card match {
          case denizen: DenizenState => operations :+ Discard.Denizen(denizen.id,
            PositionedLocation(Location.Site(siteId)), destination, suit,
            denizen.tokens.favor, denizen.tokens.secrets, actor)
          case edifice: EdificeState if edifice.side == EdificeSide.Ruined =>
            operations :+ Discard.RuinedEdifice(edifice.id,
              PositionedLocation(Location.Site(siteId)), suit,
              edifice.tokens.favor, edifice.tokens.secrets, actor)
          case _ => operations
        }
      }
    }
  }

  def contributions: Map[PowerWindow, Vector[Contribution]] = {
    val effect = Vector(Transform((ctx, ops) => at(ctx.state) match {
      case Some(site) => ops :+ BuildOps((ready, _) => build(ready, site),
        restrictions = (ready, _) =>
          Vector(new DiscardRestrictions(catalog, ready.setup.firstPlayer)))
      case None => ops
    }))
    Map(PowerWindow.SetupEnd -> effect, PowerWindow.WhenExplored -> effect)
  }
}
object EmptyGrounds {
  val id: PowerId = PowerId("edifice.e22.ruined")
  def forCatalog(catalog: ExecutableCatalog): Option[EmptyGrounds] =
    CatalogCards.edifice(catalog, id).map(new EmptyGrounds(_, catalog))
}
