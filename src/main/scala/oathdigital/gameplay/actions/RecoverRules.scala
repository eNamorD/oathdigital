package oathdigital.gameplay.actions

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, OathState, PowerRuntime, ReadyGame}
import oathdigital.gameplay.OathViolation
import oathdigital.gameplay.OathViolation._
import oathdigital.model._

/** Recover eligibility and scoring, shared by the walker's declared procedure
  * ([[oathdigital.gameplay.actions.recover.RecoverProcedure]]), the reviewed
  * Recover powers, and the application-layer projectors. Carries no walker or
  * legacy-command knowledge of its own -- just the rules every caller needs
  * to agree on.
  */
object RecoverRules {
  def difficulty(catalog: ExecutableCatalog, site: SiteId): Option[Int] =
    catalog.sites.find(_.id == site).flatMap(_.recoverDifficulty)

  def score(faces: Vector[DefenseDieFace]): Int = DefenseDieFace.score(faces)

  def validateAction(catalog: ExecutableCatalog, state: OathState,
      actor: PlayerId, siteId: SiteId): Either[OathViolation, ReadyGame] = for {
    ready <- OathLifecycle.validateAct(state, actor)
    player <- ready.game.current.players.find(_.player == actor)
      .toRight(WrongPlayer(ready.game.current.turn.activePlayer, actor))
    _ <- Either.cond(player.pawnSite.contains(siteId), (),
      RecoverUnavailable("pawn is not at the Recover site"))
    _ <- validatePotential(catalog, ready, player, siteId)
  } yield ready

  def validate(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerState,
      siteId: SiteId): Either[OathViolation, Unit] =
    validatePotential(catalog, ready, player, siteId).flatMap { _ =>
      val site = ready.game.current.map.sites.get(siteId)
      Either.cond(site.exists(_.relics.nonEmpty), (),
        OathViolation.RecoverUnavailable("site has no facedown relic"))
    }

  /** Base procedure eligibility without requiring a relic already at the site.
    * Recover powers may satisfy that final condition before the first roll.
    */
  def validatePotential(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerState, siteId: SiteId): Either[OathViolation, Unit] = {
    val game = ready.game
    val reason =
      if (game.campaign.lineages.values.exists(_.role != Role.Exile)) Some("Recover is limited to the exile-only first game")
      else if (game.campaign.foundations.values.exists(f => f.face != FoundationFace.Normal || f.alterationSources.nonEmpty)) Some("altered Foundations are not supported for Recover")
      else None
    reason.map(OathViolation.UnsupportedRecoverState).toLeft(()).flatMap(_ =>
      PowerRuntime.requireAudited(catalog)).flatMap { _ =>
      if (difficulty(catalog, siteId).isEmpty) Left(OathViolation.RecoverUnavailable("site has no Recover Difficulty"))
      else if (player.board.supply.supply < 1) Left(OathViolation.InsufficientSupply(1, player.board.supply.supply))
      else Right(())
    }
  }
}
