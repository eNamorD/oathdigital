package oathdigital.protocol.projection

import ProjectionCodecSupport._

private[projection] object CampaignProjectionCodec {
  private def encodeChoice(value: CampaignPlanChoiceProjection): ujson.Value = ujson.Obj(
    "kind" -> value.kind, "sourceKey" -> stringOption(value.sourceKey),
    "playerId" -> stringOption(value.playerId), "siteId" -> stringOption(value.siteId),
    "cardId" -> stringOption(value.cardId), "label" -> value.label,
    "handlerId" -> stringOption(value.handlerId), "favorCost" -> value.favorCost,
    "secretCost" -> value.secretCost, "mechanicalResult" -> value.mechanicalResult)
  private def decodeChoice(raw: ujson.Value, path: String): Result[CampaignPlanChoiceProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("kind", "sourceKey", "playerId", "siteId", "cardId", "label",
      "handlerId", "favorCost", "secretCost", "mechanicalResult"), path)
    kind <- string(value, "kind", path); source <- optionalString(value, "sourceKey", path)
    player <- optionalString(value, "playerId", path); site <- optionalString(value, "siteId", path)
    card <- optionalString(value, "cardId", path); label <- string(value, "label", path)
    handler <- optionalString(value, "handlerId", path); favor <- int(value, "favorCost", path)
    secret <- int(value, "secretCost", path); result <- string(value, "mechanicalResult", path)
    _ <- Either.cond(kind match {
      case "adviser" | "relic" => source.nonEmpty && player.nonEmpty &&
        site.isEmpty && card.nonEmpty
      case "site-card" => source.nonEmpty && player.isEmpty && site.nonEmpty &&
        card.nonEmpty
      case "title" => source.nonEmpty && player.nonEmpty && site.isEmpty && card.isEmpty
      case _ => false
    }, (), oathdigital.protocol.ProtocolDecodeFailure.InvalidValue(path,
      s"invalid Campaign plan choice shape '$kind'"))
  } yield CampaignPlanChoiceProjection(kind, source, player, site, card, label, handler,
    favor, secret, result)

  def encode(value: CampaignProjection): ujson.Value = ujson.Obj(
    "decisionId" -> value.decisionId, "kind" -> value.kind,
    "raidTargets" -> encoded(value.raidTargets)(ujson.Str(_)),
    "targetSiteIds" -> encoded(value.targetSiteIds)(ujson.Str(_)),
    "defenderKind" -> value.defenderKind,
    "defenderPlayerId" -> stringOption(value.defenderPlayerId),
    "defenderForce" -> value.defenderForce, "defenseDiceCount" -> value.defenseDiceCount,
    "planSide" -> value.planSide,
    "decisionOwnerPlayerId" -> stringOption(value.decisionOwnerPlayerId),
    "force" -> value.force, "plansFinished" -> value.plansFinished,
    "planChoices" -> encoded(value.planChoices)(encodeChoice),
    "selectedPlans" -> encoded(value.selectedPlans)(encodeChoice),
    "attackDice" -> encoded(value.attackDice)(ujson.Str(_)), "attack" -> value.attack,
    "skullLosses" -> value.skullLosses, "maxSacrifice" -> value.maxSacrifice,
    "sacrificed" -> intOption(value.sacrificed),
    "defenseDice" -> encoded(value.defenseDice)(ujson.Str(_)),
    "defense" -> intOption(value.defense), "victorious" -> boolOption(value.victorious),
    "maxPlacement" -> value.maxPlacement,
    "placementTargets" -> encoded(value.placementTargets)(target => ujson.Obj(
      "siteId" -> target.siteId, "label" -> target.label)))

  def decode(raw: ujson.Value, path: String): Result[CampaignProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("decisionId", "kind", "raidTargets", "targetSiteIds",
      "defenderKind", "defenderPlayerId", "defenderForce", "defenseDiceCount",
      "planSide", "decisionOwnerPlayerId", "force", "plansFinished", "planChoices",
      "selectedPlans", "attackDice", "attack", "skullLosses", "maxSacrifice",
      "sacrificed", "defenseDice", "defense", "victorious", "maxPlacement",
      "placementTargets"), path)
    decision <- string(value, "decisionId", path)
    kind <- default(value, "kind", path, "conquest")(string)
    raids <- stringsOrEmpty(value, "raidTargets", path); sites <- strings(value, "targetSiteIds", path)
    defenderKind <- default(value, "defenderKind", path, "bandits")(string)
    defender <- optionalAbsent(value, "defenderPlayerId", path)(string)
    defenderForce <- intOr(value, "defenderForce", path, 0)
    defenseDiceCount <- intOr(value, "defenseDiceCount", path, 0)
    planSide <- default(value, "planSide", path, "attacker")(string)
    owner <- optionalAbsent(value, "decisionOwnerPlayerId", path)(string)
    force <- int(value, "force", path); finished <- bool(value, "plansFinished", path)
    choiceRaws <- array(value, "planChoices", path)
    choices <- traverse(choiceRaws, s"$path.planChoices")(decodeChoice)
    selectedRaws <- array(value, "selectedPlans", path)
    selected <- traverse(selectedRaws, s"$path.selectedPlans")(decodeChoice)
    _ <- Either.cond((choices ++ selected).flatMap(_.sourceKey).distinct.size ==
      choices.size + selected.size, (),
      oathdigital.protocol.ProtocolDecodeFailure.InvalidValue(path,
        "Campaign plan sources must be distinct across available and selected plans"))
    attackDice <- strings(value, "attackDice", path); attack <- int(value, "attack", path)
    skulls <- int(value, "skullLosses", path); maxSacrifice <- int(value, "maxSacrifice", path)
    sacrificed <- optionalInt(value, "sacrificed", path)
    defenseDice <- strings(value, "defenseDice", path); defense <- optionalInt(value, "defense", path)
    victorious <- optionalBool(value, "victorious", path); maxPlacement <- int(value, "maxPlacement", path)
    placementRaws <- array(value, "placementTargets", path)
    placements <- traverse(placementRaws, s"$path.placementTargets") { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row, Set("siteId", "label"), child)
      site <- string(row, "siteId", child); label <- string(row, "label", child)
    } yield CampaignPlacementTargetProjection(site, label) }
    _ <- Either.cond(kind == "raid" ||
      (sites.nonEmpty && sites.distinct.size == sites.size &&
        placements.map(_.siteId) == sites), (),
      oathdigital.protocol.ProtocolDecodeFailure.InvalidValue(
        s"$path.targetSiteIds", "Campaign targets and placements must match"))
  } yield CampaignProjection(decision, sites, force, finished, choices, selected,
    attackDice, attack, skulls, maxSacrifice, sacrificed, defenseDice, defense,
    victorious, maxPlacement, placements, defenderKind, defender, defenderForce,
    defenseDiceCount, planSide, owner, kind, raids)
}
