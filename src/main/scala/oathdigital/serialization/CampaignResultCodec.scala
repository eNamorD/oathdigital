package oathdigital.serialization

import oathdigital.model._
import scala.util.control.NonFatal

/** Wire spelling of [[CampaignResult]], for the `record-campaign-result`
  * operation. Split out of `WalkerOperationCodec` for headroom.
  */
private[serialization] trait CampaignResultCodec {
    this: GameEventJsonSupport =>
  import WireError._

  protected final def encodeCampaignResult(result: CampaignResult): ujson.Value =
    ujson.Obj(
      "attackerPlayerId" -> result.attacker.value,
      "campaignKind" -> result.kind.key,
      "defender" -> (result.defender match {
        case CampaignDefender.Bandits => ujson.Obj("kind" -> "bandits")
        case CampaignDefender.Player(id) =>
          ujson.Obj("kind" -> "player", "playerId" -> id.value)
      }),
      "targetSiteIds" -> ujson.Arr.from(result.targetSites.map(site =>
        ujson.Str(site.value))),
      "raidTargets" -> ujson.Arr.from(result.raidTargets.map(
        encodeCampaignRaidTarget)),
      "force" -> result.force,
      "attackFaces" -> ujson.Arr.from(result.attackFaces.map(face =>
        ujson.Str(encodeAttackFace(face)))),
      "attackScore" -> result.attackScore,
      "skullLosses" -> result.skullLosses,
      "sacrificed" -> result.sacrificed,
      "defenseFaces" -> ujson.Arr.from(result.defenseFaces.map(face =>
        ujson.Str(encodeDefenseFace(face)))),
      "defenseScore" -> result.defenseScore,
      "victorious" -> result.victorious)

  protected final def decodeCampaignResult(value: ujson.Value, path: String)
      : Either[WireError, CampaignResult] = try {
    for {
      kind <- decodeCampaignKind(value("campaignKind"), s"$path.campaignKind")
      defender <- value("defender")("kind").str match {
        case "bandits" => Right(CampaignDefender.Bandits)
        case "player" => Right(CampaignDefender.Player(
          PlayerId(value("defender")("playerId").str)))
        case other => Left(InvalidValue(s"$path.defender.kind",
          s"unknown Campaign defender '$other'"))
      }
      raidTargets <- traverse(value("raidTargets").arr.toVector.zipWithIndex) {
        case (entry, index) =>
          decodeCampaignRaidTarget(entry, s"$path.raidTargets[$index]")
      }
      attackFaces <- traverse(value("attackFaces").arr.toVector.zipWithIndex) {
        case (entry, index) =>
          decodeAttackFace(entry.str, s"$path.attackFaces[$index]")
      }
      defenseFaces <- traverse(value("defenseFaces").arr.toVector.zipWithIndex) {
        case (entry, index) =>
          decodeDefenseFace(entry.str, s"$path.defenseFaces[$index]")
      }
    } yield CampaignResult(PlayerId(value("attackerPlayerId").str), kind,
      defender, value("targetSiteIds").arr.toVector.map(v => SiteId(v.str)),
      raidTargets, value("force").num.toInt, attackFaces,
      value("attackScore").num.toInt, value("skullLosses").num.toInt,
      value("sacrificed").num.toInt, defenseFaces,
      value("defenseScore").num.toInt, value("victorious").bool)
  } catch {
    case NonFatal(error) => Left(InvalidValue(path,
      Option(error.getMessage).getOrElse("invalid Campaign result")))
  }
}
