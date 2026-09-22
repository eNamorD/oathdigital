package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._
import oathdigital.gameplay._
import oathdigital.model.OathViolation._
import oathdigital.gameplay.operations.Costs

object ForgeRules {
  /** The complete pre-release component corpus was audited against CR p.25 / NF
    * p.14 and contains no handler that changes the base Forge procedure. The
    * exact handler vocabulary is pinned: an added/changed handler makes active
    * component powers conservative blockers until explicitly re-audited.
    */
  def validate(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerState,
      siteId: SiteId): Either[OathViolation, (Vector[SiteDenizenTarget], Tokens)] = {
    val game = ready.game
    val definition = catalog.sites.find(_.id == siteId)
    val site = game.current.map.sites.get(siteId)
    PowerRuntime.requireAudited(catalog).flatMap { _ =>
      for {
        s <- site.toRight(SiteNotInPlay(siteId))
        ruled <- SiteRule.ruledBy(s.forces, game.current.players, player.player)
          .left.map(x => ForgeUnavailable(s"cannot derive site ruler: $x"))
        _ <- Either.cond(ruled, (), ForgeUnavailable("actor does not rule their pawn site"))
        cost <- definition.flatMap(_.forgeRequirements)
          .toRight(ForgeUnavailable("site has no printed Forge cost"))
        empty = s.denizens.collect { case d: DenizenState if d.orientation == Orientation.FaceUp && d.tokens == Tokens.empty =>
          SiteDenizenTarget(siteId, d.id) }
        _ <- Either.cond(empty.size == 3, (), ForgeUnavailable("site must contain exactly three empty denizens"))
        _ <- Either.cond(cost.favor + cost.secrets == 3, (), ForgeUnavailable("printed Forge cost must contain three resources"))
        _ <- Either.cond(player.board.supply.supply >= 1, (), InsufficientSupply(1, player.board.supply.supply))
        _ <- Either.cond(game.current.commonCards.relicDeck.nonEmpty, (), ForgeUnavailable("relic deck is empty"))
        // The actor funds the printed cost from their own play area, so this
        // gate is what stops a Forge that spends Supply, walks to its last
        // node and fails there with nothing recoverable. It plans the whole
        // cost onto one of the three empty denizens: the resources are split
        // across all three at execution time, but affordability is a total
        // over the actor's own favor and secrets either way.
        _ <- Costs.plan(ready, player.player,
          Location.OnCard(empty.head.denizenId),
          Cost(favor = cost.favor, secret = cost.secrets))
      } yield empty -> cost
    }
  }
}
