package oathdigital.application

import oathdigital.model._
import oathdigital.protocol.projection.CampaignResultProjection

/** The last Campaign's result. Everything in it is public, so it takes no
  * viewer.
  */
private[application] object CampaignResultProjector {
  def project(ready: ReadyGame): Option[CampaignResultProjection] =
    ready.game.current.lastCampaignResult.map { result =>
      CampaignResultProjection(result.attacker.value, result.kind.key,
        result.defender match {
          case CampaignDefender.Player(player) => Some(player.value)
          case CampaignDefender.Bandits => None
        }, result.targetSites.map(_.value), result.raidTargets.map(_.stableKey),
        result.force, result.attackFaces.map(attackFace), result.attackScore,
        result.skullLosses, result.sacrificed,
        result.defenseFaces.map(defenseFace), result.defenseScore,
        result.attackerWins)
    }

  // The wire spellings, duplicated because the application layer may not
  // import the serialization layer (see `defenseFaceName` in
  // `WalkerDecisionProjector`).
  private def attackFace(face: AttackDieFace): String = face match {
    case AttackDieFace.HollowSword => "hollow-sword"
    case AttackDieFace.OneSword => "one-sword"
    case AttackDieFace.TwoSwordsSkull => "two-swords-skull"
  }

  private def defenseFace(face: DefenseDieFace): String = face match {
    case DefenseDieFace.Blank => "blank"
    case DefenseDieFace.OneShield => "one-shield"
    case DefenseDieFace.TwoShields => "two-shields"
    case DefenseDieFace.Doubler => "doubler"
  }
}
