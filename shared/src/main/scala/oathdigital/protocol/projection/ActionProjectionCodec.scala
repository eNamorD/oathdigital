package oathdigital.protocol.projection

import ProjectionCodecSupport._
import WorldProjectionCodec.{decodeCard, encodeCard}

private[projection] object ActionProjectionCodec {
  def encodeTarget(value: BoardTargetRefProjection): ujson.Value = value match {
    case BoardTargetRefProjection.Site(id) => ujson.Obj("kind" -> "site", "siteId" -> id)
  }
  def decodeTarget(raw: ujson.Value, path: String): Result[BoardTargetRefProjection] = for {
    value <- obj(raw, path); kind <- string(value, "kind", path)
    target <- kind match {
      case "site" => exact(value, Set("kind", "siteId"), path).flatMap(_ =>
        string(value, "siteId", path).map(BoardTargetRefProjection.Site))
      case other => Left(oathdigital.protocol.ProtocolDecodeFailure.InvalidValue(
        s"$path.kind", s"unsupported board target '$other'"))
    }
  } yield target

  def encodeAction(value: BoardTargetActionProjection): ujson.Value = ujson.Obj(
    "actionKind" -> value.actionKind, "decisionId" -> stringOption(value.decisionId),
    "prompt" -> value.prompt, "minimum" -> value.minimum, "maximum" -> value.maximum,
    "autoActivate" -> value.autoActivate, "explicitConfirm" -> value.explicitConfirm,
    "candidates" -> encoded(value.candidates)(candidate => ujson.Obj(
      "target" -> encodeTarget(candidate.target), "label" -> candidate.label,
      "details" -> encoded(candidate.details)(ujson.Str(_)))))
  def decodeAction(raw: ujson.Value, path: String): Result[BoardTargetActionProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("actionKind", "decisionId", "prompt", "minimum", "maximum",
      "autoActivate", "explicitConfirm", "candidates"), path)
    kind <- string(value, "actionKind", path)
    decision <- optionalAbsent(value, "decisionId", path)(string)
    prompt <- string(value, "prompt", path); minimum <- int(value, "minimum", path)
    maximum <- int(value, "maximum", path); auto <- bool(value, "autoActivate", path)
    explicit <- bool(value, "explicitConfirm", path)
    candidateRaws <- array(value, "candidates", path)
    candidates <- traverse(candidateRaws, s"$path.candidates") { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row, Set("target", "label", "details"), child)
      targetRaw <- field(row, "target", child); target <- decodeTarget(targetRaw, s"$child.target")
      label <- string(row, "label", child); details <- strings(row, "details", child)
    } yield BoardTargetCandidateProjection(target, label, details) }
  } yield BoardTargetActionProjection(kind, prompt, minimum, maximum, auto, candidates,
    decision, explicit)

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

  def encodeRollOutcome(value: WalkerRollOutcomeProjection): ujson.Value = ujson.Obj(
    "faces" -> encoded(value.faces)(ujson.Str(_)), "score" -> value.score,
    "difficulty" -> value.difficulty)
  def decodeRollOutcome(raw: ujson.Value, path: String)
      : Result[WalkerRollOutcomeProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("faces", "score", "difficulty"), path)
    faces <- strings(value, "faces", path)
    score <- int(value, "score", path); difficulty <- int(value, "difficulty", path)
  } yield WalkerRollOutcomeProjection(faces, score, difficulty)

  def encodeWalkerDecision(value: WalkerDecisionProjection): ujson.Value = ujson.Obj(
    "action" -> value.action, "decisionId" -> value.decisionId, "kind" -> value.kind,
    "pool" -> stringOption(value.pool), "count" -> intOption(value.count),
    "query" -> option(value.query)(encodeDecisionQuery),
    "rollOutcome" -> option(value.rollOutcome)(encodeRollOutcome))
  def decodeWalkerDecision(raw: ujson.Value, path: String): Result[WalkerDecisionProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("action", "decisionId", "kind", "pool", "count",
      "query", "rollOutcome"), path)
    action <- string(value, "action", path); decision <- string(value, "decisionId", path)
    kind <- string(value, "kind", path); pool <- optionalString(value, "pool", path)
    count <- optionalInt(value, "count", path)
    query <- optionalAbsent(value, "query", path)(decodeDecisionQuery)
    rollOutcome <- optionalAbsent(value, "rollOutcome", path)(decodeRollOutcome)
  } yield WalkerDecisionProjection(action, decision, kind, pool, count, query,
    rollOutcome)

  def encodeWalkerWaiting(value: WalkerWaitingProjection): ujson.Value = ujson.Obj(
    "playerId" -> value.playerId, "heading" -> stringOption(value.heading),
    "coOwnerPlayerIds" -> encoded(value.coOwnerPlayerIds)(ujson.Str(_)),
    "deal" -> option(value.deal)(encodeDeal))
  def decodeWalkerWaiting(raw: ujson.Value, path: String): Result[WalkerWaitingProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("playerId", "heading", "coOwnerPlayerIds", "deal"), path)
    playerId <- string(value, "playerId", path)
    heading <- optionalString(value, "heading", path)
    coOwners <- strings(value, "coOwnerPlayerIds", path)
    deal <- optionalAbsent(value, "deal", path)(decodeDeal)
  } yield WalkerWaitingProjection(playerId, heading, coOwners, deal)

  /** A projected decision query: one `form` string, direct options, the
    * sections a partition declares, and the slots plus total a distribute
    * query declares. An option's `kind`/`id` pair, including an option
    * embedded in a distribute slot, is the same spelling a submitted and a
    * journalled answer use, so this codec writes no table of its own -- it
    * copies the two strings through.
    */
  def encodeDecisionQuery(value: DecisionQueryProjection): ujson.Value = ujson.Obj(
    "form" -> value.form,
    "options" -> encoded(value.options)(encodeOptionRow),
    "sections" -> encoded(value.sections)(section => ujson.Obj(
      "key" -> section.key, "label" -> section.label,
      "minRequired" -> section.minRequired,
      "maxAllowed" -> intOption(section.maxAllowed))),
    "heading" -> stringOption(value.heading),
    "confirmLabel" -> stringOption(value.confirmLabel),
    "slots" -> encoded(value.slots)(slot => ujson.Obj(
      "option" -> encodeOptionRow(slot.option), "minimum" -> slot.minimum,
      "maximum" -> slot.maximum, "suggested" -> intOption(slot.suggested))),
    "minTotal" -> intOption(value.minTotal),
    "maxTotal" -> intOption(value.maxTotal),
    "minimum" -> intOption(value.minimum),
    "maximum" -> intOption(value.maximum),
    "deal" -> option(value.deal)(encodeDeal))
  def decodeDecisionQuery(raw: ujson.Value, path: String)
      : Result[DecisionQueryProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("form", "options", "sections", "heading",
      "confirmLabel", "slots", "minTotal", "maxTotal", "minimum", "maximum", "deal"), path)
    form <- string(value, "form", path)
    optionRaws <- array(value, "options", path)
    options <- traverse(optionRaws, s"$path.options")(decodeOptionRow)
    sectionRaws <- array(value, "sections", path)
    sections <- traverse(sectionRaws, s"$path.sections") { (raw, child) => for {
      row <- obj(raw, child)
      _ <- exact(row, Set("key", "label", "minRequired", "maxAllowed"), child)
      key <- string(row, "key", child); label <- string(row, "label", child)
      minimum <- int(row, "minRequired", child)
      maximum <- optionalInt(row, "maxAllowed", child)
    } yield DecisionSectionProjection(key, label, minimum, maximum) }
    heading <- optionalString(value, "heading", path)
    confirmLabel <- optionalString(value, "confirmLabel", path)
    slotRaws <- array(value, "slots", path)
    slots <- traverse(slotRaws, s"$path.slots") { (raw, child) => for {
      row <- obj(raw, child)
      _ <- exact(row, Set("option", "minimum", "maximum", "suggested"), child)
      option <- field(row, "option", child).flatMap(
        decodeOptionRow(_, s"$child.option"))
      minimum <- int(row, "minimum", child)
      maximum <- int(row, "maximum", child)
      suggested <- optionalInt(row, "suggested", child)
    } yield DecisionSlotProjection(option, minimum, maximum, suggested) }
    minTotal <- optionalInt(value, "minTotal", path)
    maxTotal <- optionalInt(value, "maxTotal", path)
    minimum <- optionalInt(value, "minimum", path)
    maximum <- optionalInt(value, "maximum", path)
    deal <- optionalAbsent(value, "deal", path)(decodeDeal)
  } yield DecisionQueryProjection(form, options, sections, heading,
    confirmLabel, slots, minTotal, maxTotal, minimum, maximum, deal)

  private def encodeOptionRow(row: DecisionOptionProjection): ujson.Value =
    ujson.Obj("kind" -> row.kind, "id" -> row.id, "label" -> row.label,
      "card" -> option(row.card)(encodeCard),
      "details" -> encoded(row.details)(ujson.Str(_)))

  private[projection] def decodeOptionRow(raw: ujson.Value, child: String)
      : Result[DecisionOptionProjection] = for {
    row <- obj(raw, child)
    _ <- exact(row, Set("kind", "id", "label", "card", "details"), child)
    kind <- string(row, "kind", child); id <- string(row, "id", child)
    label <- string(row, "label", child)
    card <- optionalAbsent(row, "card", child)(decodeCard)
    details <- stringsOrEmpty(row, "details", child)
  } yield DecisionOptionProjection(kind, id, label, card, details)

  def encodePhasePower(value: PhasePowerProjection): ujson.Value = ujson.Obj(
    "powerId" -> value.powerId, "source" -> encodeOptionRow(value.source),
    "name" -> value.name, "rulesText" -> value.rulesText)

  def decodePhasePower(raw: ujson.Value, path: String)
      : Result[PhasePowerProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("powerId", "source", "name", "rulesText"), path)
    powerId <- string(value, "powerId", path)
    source <- field(value, "source", path).flatMap(
      decodeOptionRow(_, s"$path.source"))
    name <- string(value, "name", path)
    rulesText <- string(value, "rulesText", path)
  } yield PhasePowerProjection(powerId, source, name, rulesText)

  private def encodeTransfer(row: NegotiationTransferProjection): ujson.Value =
    ujson.Obj("authorPlayerId" -> row.authorPlayerId,
      "recipientPlayerId" -> row.recipientPlayerId, "favor" -> row.favor,
      "relicCount" -> row.relicCount, "relics" -> encoded(row.relics)(encodeCard))
  private def encodeDisclosure(row: NegotiationDisclosureProjection): ujson.Value =
    ujson.Obj("authorPlayerId" -> row.authorPlayerId,
      "recipientPlayerId" -> row.recipientPlayerId, "kind" -> row.kind,
      "card" -> option(row.card)(encodeCard))
  private def encodeSiteRelic(row: NegotiationSiteRelicProjection): ujson.Value =
    ujson.Obj("siteId" -> row.siteId, "card" -> encodeCard(row.card))

  private def decodeTransfer(raw: ujson.Value, child: String)
      : Result[NegotiationTransferProjection] = for {
    row <- obj(raw, child)
    _ <- exact(row, Set("authorPlayerId", "recipientPlayerId", "favor",
      "relicCount", "relics"), child)
    author <- string(row, "authorPlayerId", child)
    recipient <- string(row, "recipientPlayerId", child)
    favor <- int(row, "favor", child); count <- int(row, "relicCount", child)
    raws <- array(row, "relics", child)
    relics <- traverse(raws, s"$child.relics")(decodeCard)
  } yield NegotiationTransferProjection(author, recipient, favor, count, relics)
  private def decodeDisclosure(raw: ujson.Value, child: String)
      : Result[NegotiationDisclosureProjection] = for {
    row <- obj(raw, child)
    _ <- exact(row, Set("authorPlayerId", "recipientPlayerId", "kind", "card"), child)
    author <- string(row, "authorPlayerId", child)
    recipient <- string(row, "recipientPlayerId", child)
    kind <- string(row, "kind", child)
    card <- optional(row, "card", child)(decodeCard)
  } yield NegotiationDisclosureProjection(author, recipient, kind, card)
  private def decodeSiteRelic(raw: ujson.Value, child: String)
      : Result[NegotiationSiteRelicProjection] = for {
    row <- obj(raw, child)
    _ <- exact(row, Set("siteId", "card"), child)
    site <- string(row, "siteId", child)
    cardRaw <- field(row, "card", child)
    card <- decodeCard(cardRaw, s"$child.card")
  } yield NegotiationSiteRelicProjection(site, card)

  def encodeDeal(value: NegotiationDealProjection): ujson.Value = ujson.Obj(
    "participantPlayerIds" -> encoded(value.participantPlayerIds)(ujson.Str(_)),
    "acceptedPlayerIds" -> encoded(value.acceptedPlayerIds)(ujson.Str(_)),
    "transfers" -> encoded(value.transfers)(encodeTransfer),
    "disclosures" -> encoded(value.disclosures)(encodeDisclosure),
    "editing" -> option(value.editing)(editing => ujson.Obj(
      "editableFavor" -> editing.editableFavor,
      "editableRelics" -> encoded(editing.editableRelics)(encodeCard),
      "editableAdvisers" -> encoded(editing.editableAdvisers)(encodeCard),
      "editableSiteRelics" -> encoded(editing.editableSiteRelics)(encodeSiteRelic),
      "canAccept" -> editing.canAccept)))

  def decodeDeal(raw: ujson.Value, path: String): Result[NegotiationDealProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("participantPlayerIds", "acceptedPlayerIds", "transfers",
      "disclosures", "editing"), path)
    participants <- strings(value, "participantPlayerIds", path)
    accepted <- strings(value, "acceptedPlayerIds", path)
    transferRaws <- array(value, "transfers", path)
    transfers <- traverse(transferRaws, s"$path.transfers")(decodeTransfer)
    disclosureRaws <- array(value, "disclosures", path)
    disclosures <- traverse(disclosureRaws, s"$path.disclosures")(decodeDisclosure)
    editing <- optionalAbsent(value, "editing", path) { (rawEditing, child) => for {
      row <- obj(rawEditing, child)
      _ <- exact(row, Set("editableFavor", "editableRelics", "editableAdvisers",
        "editableSiteRelics", "canAccept"), child)
      favor <- int(row, "editableFavor", child)
      relicRaws <- array(row, "editableRelics", child)
      relics <- traverse(relicRaws, s"$child.editableRelics")(decodeCard)
      adviserRaws <- array(row, "editableAdvisers", child)
      advisers <- traverse(adviserRaws, s"$child.editableAdvisers")(decodeCard)
      siteRaws <- array(row, "editableSiteRelics", child)
      sites <- traverse(siteRaws, s"$child.editableSiteRelics")(decodeSiteRelic)
      canAccept <- bool(row, "canAccept", child)
    } yield NegotiationEditingProjection(favor, relics, advisers, sites, canAccept) }
  } yield NegotiationDealProjection(participants, accepted, transfers, disclosures, editing)
}
