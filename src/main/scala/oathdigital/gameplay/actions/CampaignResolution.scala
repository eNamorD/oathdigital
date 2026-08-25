package oathdigital.gameplay.actions

import oathdigital.model._
import oathdigital.gameplay.{OathViolation, ReadyGame}
import oathdigital.gameplay.OathViolation._

trait CampaignLosingForceResolver {
  def id: String
  def resolve(ready: ReadyGame, campaign: PendingProcedure.Campaign)
      : Either[OathViolation, Vector[CampaignLosingForceEffect]]
  def resolveAttackerDefeat(ready: ReadyGame,
      campaign: PendingProcedure.Campaign, surviving: Int)
      : Either[OathViolation, Vector[CampaignLosingForceEffect]] =
    Left(CampaignOutcomeMismatch(
      s"losing-force policy '$id' does not resolve attacker defeat"))
  def resolveRaidDefenderDefeat(ready: ReadyGame,
      campaign: PendingProcedure.Campaign): Either[OathViolation, CampaignRaidBoardLoss] =
    campaign.defender match {
      case CampaignDefender.Player(player) =>
        val total = ready.game.current.players.find(_.player == player).get.board.warbands
        Right(CampaignRaidBoardLoss(player, total / 2, total - total / 2))
      case _ => Left(CampaignOutcomeMismatch("a Raid requires a player defender"))
    }
}
object CampaignLosingForceResolver {
  val default: CampaignLosingForceResolver =
    new CampaignLosingForceResolver {
      val id = "campaign.loss.default-defeated-force"
      def resolve(ready: ReadyGame, campaign: PendingProcedure.Campaign) = {
        val removed = campaign.targetSites.foldLeft[
          Either[OathViolation, Vector[CampaignLosingForceEffect]]](
          Right(Vector.empty)) { (result, siteId) => result.flatMap { effects =>
            ready.game.current.map.sites.get(siteId).toRight(
              SiteNotInPlay(siteId)).flatMap(_.forces match {
                case SiteForces.Occupied(force, count) if count > 0 =>
                  Right(effects :+ CampaignLosingForceEffect.Remove(
                    siteId, force, count))
                case other => Left(CampaignOutcomeMismatch(
                  s"target '${siteId.value}' has unsupported losing force $other"))
              })
          }}
        removed.flatMap { effects => campaign.defender match {
          case CampaignDefender.Bandits => Right(effects)
          case CampaignDefender.Player(player) =>
            val total = effects.collect {
              case CampaignLosingForceEffect.Remove(_, _, count) => count
            }.sum
            val returned = total - total / 2
            if (returned == 0) Right(effects)
            else for {
              site <- CampaignRules.campaignOrigin(ready, campaign)
              force <- effects.collectFirst {
                case CampaignLosingForceEffect.Remove(_, kind, _) => kind
              }.toRight(CampaignOutcomeMismatch(
                "player-defender Campaign loss has no removed force"))
            } yield effects :+ CampaignLosingForceEffect.ReturnToBoard(
              site, player, force, returned)
        }}
      }
      override def resolveAttackerDefeat(ready: ReadyGame,
          campaign: PendingProcedure.Campaign, surviving: Int) = {
        val owner = ready.game.current.players.find(
          _.player == campaign.actor).get
        val force = ForceKind.Exile(owner.lineage)
        val killed = surviving / 2
        val returned = surviving - killed
        CampaignRules.campaignOrigin(ready, campaign).map { site => Vector(
          Option.when(killed > 0)(CampaignLosingForceEffect.KillCommitted(
            site, campaign.actor, force, killed)),
          Option.when(returned > 0)(CampaignLosingForceEffect.ReturnToBoard(
            site, campaign.actor, force, returned))
        ).flatten }
      }
    }

  val removeAllBandits: CampaignLosingForceResolver = default
}

final case class CampaignLosingForceRegistry(
    selected: CampaignLosingForceResolver,
    resolvers: Vector[CampaignLosingForceResolver]
) {
  require(resolvers.map(_.id).distinct.size == resolvers.size,
    "Campaign losing-force policy IDs must be unique")
  require(resolvers.exists(_.id == selected.id),
    "selected Campaign losing-force policy must be registered")
  def byId(id: String): Option[CampaignLosingForceResolver] =
    resolvers.find(_.id == id)
}
object CampaignLosingForceRegistry {
  val default: CampaignLosingForceRegistry = CampaignLosingForceRegistry(
    CampaignLosingForceResolver.default,
    Vector(CampaignLosingForceResolver.default))
}
