package oathdigital.protocol.projection

import ProjectionCodecSupport._

private[projection] object CampaignResultProjectionCodec {
  private val Fields = Set("attackerPlayerId", "kind", "defenderPlayerId",
    "targetSiteIds", "raidTargets", "force", "attackDice", "attackScore",
    "skullLosses", "sacrificed", "defenseDice", "defenseScore", "attackerWins")

  def encode(value: CampaignResultProjection): ujson.Value = ujson.Obj(
    "attackerPlayerId" -> value.attackerPlayerId, "kind" -> value.kind,
    "defenderPlayerId" -> stringOption(value.defenderPlayerId),
    "targetSiteIds" -> encoded(value.targetSiteIds)(ujson.Str(_)),
    "raidTargets" -> encoded(value.raidTargets)(ujson.Str(_)),
    "force" -> value.force,
    "attackDice" -> encoded(value.attackDice)(ujson.Str(_)),
    "attackScore" -> value.attackScore, "skullLosses" -> value.skullLosses,
    "sacrificed" -> value.sacrificed,
    "defenseDice" -> encoded(value.defenseDice)(ujson.Str(_)),
    "defenseScore" -> value.defenseScore, "attackerWins" -> value.attackerWins)

  def decode(raw: ujson.Value, path: String): Result[CampaignResultProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Fields, path)
    attacker <- string(value, "attackerPlayerId", path)
    kind <- string(value, "kind", path)
    defender <- optionalString(value, "defenderPlayerId", path)
    sites <- strings(value, "targetSiteIds", path)
    raid <- strings(value, "raidTargets", path)
    force <- int(value, "force", path)
    attackDice <- strings(value, "attackDice", path)
    attackScore <- int(value, "attackScore", path)
    skulls <- int(value, "skullLosses", path)
    sacrificed <- int(value, "sacrificed", path)
    defenseDice <- strings(value, "defenseDice", path)
    defenseScore <- int(value, "defenseScore", path)
    attackerWins <- bool(value, "attackerWins", path)
  } yield CampaignResultProjection(attacker, kind, defender, sites, raid, force,
    attackDice, attackScore, skulls, sacrificed, defenseDice, defenseScore,
    attackerWins)
}
