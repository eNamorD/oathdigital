package oathdigital.protocol.projection

import ProjectionCodecSupport._
import WorldProjectionCodec.{decodeCard, encodeCard}

private[projection] object ActionProjectionCodec {
  def encodeTarget(value: BoardTargetRefProjection): ujson.Value = value match {
    case BoardTargetRefProjection.Player(id) => ujson.Obj("kind" -> "player", "playerId" -> id)
    case BoardTargetRefProjection.Site(id) => ujson.Obj("kind" -> "site", "siteId" -> id)
    case BoardTargetRefProjection.SiteCard(site, kind, id) => ujson.Obj(
      "kind" -> "site-card", "siteId" -> site, "cardKind" -> kind, "cardId" -> id)
    case BoardTargetRefProjection.PlayerAdviser(player, card) => ujson.Obj(
      "kind" -> "player-adviser", "playerId" -> player, "cardId" -> card)
    case BoardTargetRefProjection.PlayerRelic(player, relic) => ujson.Obj(
      "kind" -> "player-relic", "playerId" -> player, "relicId" -> relic)
    case BoardTargetRefProjection.PlayerPawn(player) => ujson.Obj(
      "kind" -> "player-pawn", "playerId" -> player)
    case BoardTargetRefProjection.PlayerBanner(player, banner) => ujson.Obj(
      "kind" -> "player-banner", "playerId" -> player, "banner" -> banner)
  }
  def decodeTarget(raw: ujson.Value, path: String): Result[BoardTargetRefProjection] = for {
    value <- obj(raw, path); kind <- string(value, "kind", path)
    target <- kind match {
      case "player" => exact(value, Set("kind", "playerId"), path).flatMap(_ =>
        string(value, "playerId", path).map(BoardTargetRefProjection.Player))
      case "site" => exact(value, Set("kind", "siteId"), path).flatMap(_ =>
        string(value, "siteId", path).map(BoardTargetRefProjection.Site))
      case "site-card" => for {
        _ <- exact(value, Set("kind", "siteId", "cardKind", "cardId"), path)
        site <- string(value, "siteId", path); cardKind <- string(value, "cardKind", path)
        _ <- Either.cond(Set("denizen", "edifice").contains(cardKind), (),
          oathdigital.protocol.ProtocolDecodeFailure.InvalidValue(s"$path.cardKind",
            "expected denizen or edifice"))
        card <- string(value, "cardId", path)
      } yield BoardTargetRefProjection.SiteCard(site, cardKind, card)
      case "player-adviser" => for {
        _ <- exact(value, Set("kind", "playerId", "cardId"), path)
        player <- string(value, "playerId", path); card <- string(value, "cardId", path)
      } yield BoardTargetRefProjection.PlayerAdviser(player, card)
      case "player-relic" => for {
        _ <- exact(value, Set("kind", "playerId", "relicId"), path)
        player <- string(value, "playerId", path); relic <- string(value, "relicId", path)
      } yield BoardTargetRefProjection.PlayerRelic(player, relic)
      case "player-pawn" => exact(value, Set("kind", "playerId"), path).flatMap(_ =>
        string(value, "playerId", path).map(BoardTargetRefProjection.PlayerPawn))
      case "player-banner" => for {
        _ <- exact(value, Set("kind", "playerId", "banner"), path)
        player <- string(value, "playerId", path); banner <- string(value, "banner", path)
        _ <- Either.cond(Set("peoples-favor", "darkest-secret").contains(banner), (),
          oathdigital.protocol.ProtocolDecodeFailure.InvalidValue(s"$path.banner",
            "expected peoples-favor or darkest-secret"))
      } yield BoardTargetRefProjection.PlayerBanner(player, banner)
      case other => Left(oathdigital.protocol.ProtocolDecodeFailure.InvalidValue(
        s"$path.kind", s"unsupported board target '$other'"))
    }
  } yield target

  def encodeAction(value: BoardTargetActionProjection): ujson.Value = ujson.Obj(
    "actionKind" -> value.actionKind, "decisionId" -> stringOption(value.decisionId),
    "prompt" -> value.prompt, "minimum" -> value.minimum, "maximum" -> value.maximum,
    "autoActivate" -> value.autoActivate, "explicitConfirm" -> value.explicitConfirm,
    "requiredTargets" -> encoded(value.requiredTargets)(encodeTarget),
    "formation" -> option(value.formation)(f => ujson.Obj(
      "minimumForce" -> f.minimumForce, "maximumForce" -> f.maximumForce,
      "availableWarbands" -> f.availableWarbands, "supplyCost" -> f.supplyCost)),
    "candidates" -> encoded(value.candidates)(candidate => ujson.Obj(
      "target" -> encodeTarget(candidate.target), "label" -> candidate.label,
      "details" -> encoded(candidate.details)(ujson.Str(_)))))
  def decodeAction(raw: ujson.Value, path: String): Result[BoardTargetActionProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("actionKind", "decisionId", "prompt", "minimum", "maximum",
      "autoActivate", "explicitConfirm", "requiredTargets", "formation", "candidates"), path)
    kind <- string(value, "actionKind", path)
    decision <- optionalAbsent(value, "decisionId", path)(string)
    prompt <- string(value, "prompt", path); minimum <- int(value, "minimum", path)
    maximum <- int(value, "maximum", path); auto <- bool(value, "autoActivate", path)
    explicit <- bool(value, "explicitConfirm", path)
    requiredRaws <- array(value, "requiredTargets", path)
    required <- traverse(requiredRaws, s"$path.requiredTargets")(decodeTarget)
    formation <- optionalAbsent(value, "formation", path) { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row, Set("minimumForce", "maximumForce",
        "availableWarbands", "supplyCost"), child)
      min <- int(row, "minimumForce", child); max <- int(row, "maximumForce", child)
      available <- int(row, "availableWarbands", child); cost <- int(row, "supplyCost", child)
    } yield BoardTargetFormationProjection(min, max, available, cost) }
    candidateRaws <- array(value, "candidates", path)
    candidates <- traverse(candidateRaws, s"$path.candidates") { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row, Set("target", "label", "details"), child)
      targetRaw <- field(row, "target", child); target <- decodeTarget(targetRaw, s"$child.target")
      label <- string(row, "label", child); details <- strings(row, "details", child)
    } yield BoardTargetCandidateProjection(target, label, details) }
  } yield BoardTargetActionProjection(kind, prompt, minimum, maximum, auto, candidates,
    formation, required, decision, explicit)

  def encodeResolution(value: CardResolutionProjection): ujson.Value = ujson.Obj(
    "kind" -> value.kind, "orientation" -> stringOption(value.orientation),
    "replacementRequired" -> value.replacementRequired,
    "replacementTargets" -> encoded(value.replacementTargets)(encodeCard))
  def decodeResolution(raw: ujson.Value, path: String): Result[CardResolutionProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("kind", "orientation", "replacementRequired", "replacementTargets"), path)
    kind <- string(value, "kind", path); orientation <- optionalString(value, "orientation", path)
    required <- bool(value, "replacementRequired", path)
    raws <- array(value, "replacementTargets", path)
    targets <- traverse(raws, s"$path.replacementTargets")(decodeCard)
  } yield CardResolutionProjection(kind, orientation, required, targets)

  def encodePending(value: PendingCardDecisionProjection): ujson.Value = ujson.Obj(
    "decisionId" -> value.decisionId, "kind" -> value.kind,
    "actorPlayerId" -> value.actorPlayerId, "prompt" -> value.prompt,
    "instructions" -> encoded(value.instructions)(ujson.Str(_)),
    "cards" -> encoded(value.cards)(encodeCard), "keepMinimum" -> value.keepMinimum,
    "keepMaximum" -> value.keepMaximum, "orderingRequired" -> value.orderingRequired,
    "resolutionsByCard" -> ujson.Obj.from(value.resolutionsByCard.toVector.sortBy(_._1).map {
      case (id, resolutions) => id -> encoded(resolutions)(encodeResolution)
    }))
  def decodePending(raw: ujson.Value, path: String): Result[PendingCardDecisionProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("decisionId", "kind", "actorPlayerId", "prompt", "instructions",
      "cards", "keepMinimum", "keepMaximum", "orderingRequired", "resolutionsByCard"), path)
    decision <- string(value, "decisionId", path); kind <- string(value, "kind", path)
    actor <- string(value, "actorPlayerId", path); prompt <- string(value, "prompt", path)
    instructions <- strings(value, "instructions", path)
    cardRaws <- array(value, "cards", path); cards <- traverse(cardRaws, s"$path.cards")(decodeCard)
    minimum <- int(value, "keepMinimum", path); maximum <- int(value, "keepMaximum", path)
    ordering <- bool(value, "orderingRequired", path)
    resolutionRaw <- field(value, "resolutionsByCard", path)
    resolutionObject <- obj(resolutionRaw, s"$path.resolutionsByCard")
    _ <- resolutionObject.value.keys.find(key => !cards.exists(_.cardId == key))
      .map(key => Left(oathdigital.protocol.ProtocolDecodeFailure.UnexpectedField(
        s"$path.resolutionsByCard.$key"))).getOrElse(Right(()))
    resolutions <- cards.foldLeft[Result[Map[String, Vector[CardResolutionProjection]]]](
      Right(Map.empty)) { case (Right(done), card) =>
        resolutionObject.value.get(card.cardId).toRight(
          oathdigital.protocol.ProtocolDecodeFailure.MissingField(
            s"$path.resolutionsByCard.${card.cardId}")).flatMap(array(_,
            s"$path.resolutionsByCard.${card.cardId}")).flatMap(traverse(_,
            s"$path.resolutionsByCard.${card.cardId}")(decodeResolution))
          .map(values => done.updated(card.cardId, values))
      case (failure @ Left(_), _) => failure }
  } yield PendingCardDecisionProjection(decision, kind, actor, prompt, instructions,
    cards, minimum, maximum, ordering, resolutions)

  def encodeMinor(value: MinorActionsProjection): ujson.Value = ujson.Obj(
    "advisers" -> encoded(value.advisers)(entry => ujson.Obj(
      "card" -> encodeCard(entry.card),
      "placements" -> encoded(entry.placements)(encodeResolution))),
    "canPeekSiteRelics" -> value.canPeekSiteRelics,
    "facedownRelics" -> encoded(value.facedownRelics)(encodeCard),
    "siteId" -> stringOption(value.siteId), "maxBoardToSite" -> value.maxBoardToSite,
    "maxSiteToBoard" -> value.maxSiteToBoard)
  def decodeMinor(raw: ujson.Value, path: String): Result[MinorActionsProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("advisers", "canPeekSiteRelics", "facedownRelics",
      "siteId", "maxBoardToSite", "maxSiteToBoard"), path)
    adviserRaws <- array(value, "advisers", path)
    advisers <- traverse(adviserRaws, s"$path.advisers") { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row, Set("card", "placements"), child)
      cardRaw <- field(row, "card", child); card <- decodeCard(cardRaw, s"$child.card")
      placementRaws <- array(row, "placements", child)
      placements <- traverse(placementRaws, s"$child.placements")(decodeResolution)
    } yield MinorAdviserProjection(card, placements) }
    peek <- bool(value, "canPeekSiteRelics", path)
    relicRaws <- array(value, "facedownRelics", path)
    relics <- traverse(relicRaws, s"$path.facedownRelics")(decodeCard)
    site <- optionalString(value, "siteId", path); toSite <- int(value, "maxBoardToSite", path)
    toBoard <- int(value, "maxSiteToBoard", path)
  } yield MinorActionsProjection(advisers, peek, relics, site, toSite, toBoard)

  def encodeNegotiation(value: NegotiationProjection): ujson.Value = ujson.Obj(
    "decisionId" -> value.decisionId, "actorPlayerId" -> value.actorPlayerId,
    "siteId" -> value.siteId,
    "participantPlayerIds" -> encoded(value.participantPlayerIds)(ujson.Str(_)),
    "acceptedPlayerIds" -> encoded(value.acceptedPlayerIds)(ujson.Str(_)),
    "transfers" -> encoded(value.transfers)(row => ujson.Obj(
      "authorPlayerId" -> row.authorPlayerId, "recipientPlayerId" -> row.recipientPlayerId,
      "favor" -> row.favor, "relicCount" -> row.relicCount,
      "relics" -> encoded(row.relics)(encodeCard))),
    "disclosures" -> encoded(value.disclosures)(row => ujson.Obj(
      "authorPlayerId" -> row.authorPlayerId, "recipientPlayerId" -> row.recipientPlayerId,
      "kind" -> row.kind, "card" -> option(row.card)(encodeCard))),
    "editableFavor" -> value.editableFavor,
    "editableRelics" -> encoded(value.editableRelics)(encodeCard),
    "editableAdvisers" -> encoded(value.editableAdvisers)(encodeCard),
    "editableSiteRelics" -> encoded(value.editableSiteRelics)(row => ujson.Obj(
      "siteId" -> row.siteId, "card" -> encodeCard(row.card))))
  def decodeNegotiation(raw: ujson.Value, path: String): Result[NegotiationProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("decisionId", "actorPlayerId", "siteId", "participantPlayerIds",
      "acceptedPlayerIds", "transfers", "disclosures", "editableFavor", "editableRelics",
      "editableAdvisers", "editableSiteRelics"), path)
    decision <- string(value, "decisionId", path); actor <- string(value, "actorPlayerId", path)
    site <- string(value, "siteId", path); participants <- strings(value, "participantPlayerIds", path)
    accepted <- strings(value, "acceptedPlayerIds", path)
    transferRaws <- array(value, "transfers", path)
    transfers <- traverse(transferRaws, s"$path.transfers") { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row, Set("authorPlayerId", "recipientPlayerId",
        "favor", "relicCount", "relics"), child)
      author <- string(row, "authorPlayerId", child); recipient <- string(row, "recipientPlayerId", child)
      favor <- int(row, "favor", child); count <- int(row, "relicCount", child)
      raws <- array(row, "relics", child); relics <- traverse(raws, s"$child.relics")(decodeCard)
    } yield NegotiationTransferProjection(author, recipient, favor, count, relics) }
    disclosureRaws <- array(value, "disclosures", path)
    disclosures <- traverse(disclosureRaws, s"$path.disclosures") { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row, Set("authorPlayerId", "recipientPlayerId", "kind", "card"), child)
      author <- string(row, "authorPlayerId", child); recipient <- string(row, "recipientPlayerId", child)
      kind <- string(row, "kind", child); card <- optional(row, "card", child)(decodeCard)
    } yield NegotiationDisclosureProjection(author, recipient, kind, card) }
    favor <- int(value, "editableFavor", path)
    relicRaws <- array(value, "editableRelics", path); relics <- traverse(relicRaws, s"$path.editableRelics")(decodeCard)
    adviserRaws <- array(value, "editableAdvisers", path); advisers <- traverse(adviserRaws, s"$path.editableAdvisers")(decodeCard)
    siteRelicRaws <- array(value, "editableSiteRelics", path)
    siteRelics <- traverse(siteRelicRaws, s"$path.editableSiteRelics") { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row, Set("siteId", "card"), child)
      site <- string(row, "siteId", child); cardRaw <- field(row, "card", child)
      card <- decodeCard(cardRaw, s"$child.card")
    } yield NegotiationSiteRelicProjection(site, card) }
  } yield NegotiationProjection(decision, actor, site, participants, accepted, transfers,
    disclosures, favor, relics, advisers, siteRelics)
}
