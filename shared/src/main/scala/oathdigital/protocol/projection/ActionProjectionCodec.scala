package oathdigital.protocol.projection

import ProjectionCodecSupport._
import WorldProjectionCodec.{decodeCard, encodeCard}

private[projection] object ActionProjectionCodec:
  def encodeRollOutcome(value: WalkerRollOutcomeProjection): ujson.Value = ujson.Obj(
    "pool" -> value.pool,
    "faces" -> encoded(value.faces)(face => ujson.Str(face.key)),
    "score" -> value.score, "target" -> intOption(value.target),
    "detail" -> encoded(value.detail)(ujson.Str(_)))
  def decodeRollOutcome(raw: ujson.Value, path: String)
      : Result[WalkerRollOutcomeProjection] = for
    value <- obj(raw, path)
    _ <- exact(value, Set("pool", "faces", "score", "target", "detail"), path)
    pool <- string(value, "pool", path)
    keys <- strings(value, "faces", path)
    faces <- traverse(keys, s"$path.faces")((key, child) =>
      DieFace.byKey.get(key).toRight(oathdigital.protocol
        .ProtocolDecodeFailure.UnknownVariant(child,
          s"unknown die face '$key'")))
    score <- int(value, "score", path)
    target <- optionalInt(value, "target", path)
    detail <- stringsOrEmpty(value, "detail", path)
  yield WalkerRollOutcomeProjection(pool, faces, score, target, detail)

  def encodeWalkerDecision(value: WalkerDecisionProjection): ujson.Value = ujson.Obj(
    "action" -> value.action, "decisionId" -> value.decisionId,
    "query" -> option(value.query)(encodeDecisionQuery),
    "rollOutcome" -> option(value.rollOutcome)(encodeRollOutcome),
    "answeredOptions" -> encoded(value.answeredOptions)(encodeOptionRow),
    "presentation" -> value.presentation)
  def decodeWalkerDecision(raw: ujson.Value, path: String): Result[WalkerDecisionProjection] = for
    value <- obj(raw, path)
    _ <- exact(value, Set("action", "decisionId", "query", "rollOutcome",
      "answeredOptions", "presentation"), path)
    action <- string(value, "action", path); decision <- string(value, "decisionId", path)
    query <- optionalAbsent(value, "query", path)(decodeDecisionQuery)
    rollOutcome <- optionalAbsent(value, "rollOutcome", path)(decodeRollOutcome)
    answeredRaws <- array(value, "answeredOptions", path)
    answered <- traverse(answeredRaws, s"$path.answeredOptions")(decodeOptionRow)
    presentation <- default(value, "presentation", path, "panel")(string)
  yield WalkerDecisionProjection(action, decision, query, rollOutcome,
    answered, presentation)

  def encodeWalkerWaiting(value: WalkerWaitingProjection): ujson.Value = ujson.Obj(
    "playerId" -> value.playerId, "heading" -> stringOption(value.heading),
    "coOwnerPlayerIds" -> encoded(value.coOwnerPlayerIds)(ujson.Str(_)),
    "deal" -> option(value.deal)(encodeDeal))
  def decodeWalkerWaiting(raw: ujson.Value, path: String): Result[WalkerWaitingProjection] = for
    value <- obj(raw, path)
    _ <- exact(value, Set("playerId", "heading", "coOwnerPlayerIds", "deal"), path)
    playerId <- string(value, "playerId", path)
    heading <- optionalString(value, "heading", path)
    coOwners <- strings(value, "coOwnerPlayerIds", path)
    deal <- optionalAbsent(value, "deal", path)(decodeDeal)
  yield WalkerWaitingProjection(playerId, heading, coOwners, deal)

  /** A projected decision query: the `form` discriminator and, per form,
    * exactly the fields that form declares. A payload carrying a field its
    * own form does not declare is rejected rather than ignored, which is what
    * the per-case `exact` sets are for.
    *
    * The discriminator key is `form`, not the `kind` its two neighbours in
    * this file use. Deliberate: **form** is the word `CONTEXT.md` gives the
    * concept, and the project has already paid to have it mean one thing.
    *
    * An option's `kind`/`id` pair, including one embedded in a slot, is the
    * same spelling a submitted and a journalled answer use, so this codec
    * writes no table of its own -- it copies the two strings through. The seven
    * form spellings are this wire's own and are deliberately not shared with
    * `DecisionAnswerCodec`'s journal tags; see
    * `docs/superpowers/specs/2026-09-25-decision-form-tag-separation-decision.md`.
    */
  def encodeDecisionQuery(value: DecisionQueryProjection): ujson.Value =
    value match
      case DecisionQueryProjection.ChooseOne(options, heading,
          confirmVerb) => ujson.Obj(
        "form" -> "choose-one",
        "options" -> encoded(options)(encodeOptionRow),
        "heading" -> stringOption(heading),
        "confirmVerb" -> stringOption(confirmVerb))
      case DecisionQueryProjection.ChooseMany(options, minOptions, maxOptions,
          heading, tallies, summary, confirm) => ujson.Obj(
        "form" -> "choose-many",
        "options" -> encoded(options)(encodeOptionRow),
        "minOptions" -> minOptions, "maxOptions" -> maxOptions,
        "heading" -> stringOption(heading),
        "tallies" -> encoded(tallies)(encodeTally),
        "summary" -> option(summary)(encodeSummary),
        "confirm" -> option(confirm)(encodeConfirm))
      case DecisionQueryProjection.ChooseAmount(minAmount, maxAmount,
          suggested, confirmLabel, heading) => ujson.Obj(
        "form" -> "choose-amount",
        "minAmount" -> minAmount, "maxAmount" -> maxAmount,
        "suggested" -> intOption(suggested),
        "confirmLabel" -> confirmLabel,
        "heading" -> stringOption(heading))
      case DecisionQueryProjection.Partition(sections, options, confirmLabel,
          heading) => ujson.Obj(
        "form" -> "partition",
        "sections" -> encoded(sections)(encodeSection),
        "options" -> encoded(options)(encodeOptionRow),
        "confirmLabel" -> stringOption(confirmLabel),
        "heading" -> stringOption(heading))
      case DecisionQueryProjection.Distribute(slots, minTotal, maxTotal,
          confirmLabel, heading) => ujson.Obj(
        "form" -> "distribute",
        "slots" -> encoded(slots)(encodeSlot),
        "minTotal" -> minTotal, "maxTotal" -> maxTotal,
        "confirmLabel" -> confirmLabel,
        "heading" -> stringOption(heading))
      case DecisionQueryProjection.Negotiate(deal, heading) => ujson.Obj(
        "form" -> "negotiate",
        "deal" -> encodeDeal(deal),
        "heading" -> stringOption(heading))
      case DecisionQueryProjection.Inspect(cards, done, heading) => ujson.Obj(
        "form" -> "inspect",
        "cards" -> encoded(cards)(encodeCard),
        "done" -> encodeOptionRow(done),
        "heading" -> stringOption(heading))

  /** Reads the discriminator, then hands the object to the one decoder that
    * knows that form's fields. One decoder per case rather than one `for`
    * with every field optional: each carries its own `exact` set, so a field
    * another form declares is an unexpected field here rather than an ignored
    * one.
    */
  def decodeDecisionQuery(raw: ujson.Value, path: String)
      : Result[DecisionQueryProjection] = for
    value <- obj(raw, path)
    form <- string(value, "form", path)
    query <- form match
      case "choose-one" => decodeChooseOne(value, path)
      case "choose-many" => decodeChooseMany(value, path)
      case "choose-amount" => decodeChooseAmount(value, path)
      case "partition" => decodePartition(value, path)
      case "distribute" => decodeDistribute(value, path)
      case "negotiate" => decodeNegotiate(value, path)
      case "inspect" => decodeInspect(value, path)
      case other => Left(oathdigital.protocol.ProtocolDecodeFailure
        .UnknownVariant(s"$path.form", s"unknown decision form '$other'"))
  yield query

  private def decodeChooseOne(value: ujson.Obj, path: String)
      : Result[DecisionQueryProjection] = for
    _ <- exact(value, Set("form", "options", "heading", "confirmVerb"), path)
    options <- optionRows(value, path)
    heading <- optionalString(value, "heading", path)
    confirmVerb <- optionalAbsent(value, "confirmVerb", path)(string)
  yield DecisionQueryProjection.ChooseOne(options, heading, confirmVerb)

  private def decodeChooseMany(value: ujson.Obj, path: String)
      : Result[DecisionQueryProjection] = for
    _ <- exact(value, Set("form", "options", "minOptions", "maxOptions",
      "heading", "tallies", "summary", "confirm"), path)
    options <- optionRows(value, path)
    minOptions <- int(value, "minOptions", path)
    maxOptions <- int(value, "maxOptions", path)
    heading <- optionalString(value, "heading", path)
    tallies <- default(value, "tallies", path,
      Vector.empty[DecisionTallyProjection])((raw, child) =>
        array(raw, child).flatMap(traverse(_, child)(decodeTally)))
    summary <- optionalAbsent(value, "summary", path)(decodeSummary)
    confirm <- optionalAbsent(value, "confirm", path)(decodeConfirm)
  yield DecisionQueryProjection.ChooseMany(options, minOptions, maxOptions,
    heading, tallies, summary, confirm)

  private def encodeTally(tally: DecisionTallyProjection): ujson.Value =
    ujson.Obj("label" -> tally.label, "unit" -> tally.unit,
      "values" -> encoded(tally.values)(value => ujson.Obj(
        "kind" -> value.kind, "id" -> value.id, "value" -> value.value)),
      "limit" -> intOption(tally.limit))

  private def decodeTally(raw: ujson.Value, path: String)
      : Result[DecisionTallyProjection] = for
    value <- obj(raw, path)
    _ <- exact(value, Set("label", "unit", "values", "limit"), path)
    label <- string(value, "label", path)
    unit <- string(value, "unit", path)
    rows <- array(value, "values", path)
    values <- traverse(rows, s"$path.values")((row, child) => for
      entry <- obj(row, child)
      _ <- exact(entry, Set("kind", "id", "value"), child)
      kind <- string(entry, "kind", child)
      id <- string(entry, "id", child)
      amount <- int(entry, "value", child)
    yield DecisionTallyValueProjection(kind, id, amount))
    limit <- optionalInt(value, "limit", path)
  yield DecisionTallyProjection(label, unit, values, limit)

  private def encodeSummary(summary: DecisionSummaryProjection): ujson.Value =
    ujson.Obj("verb" -> summary.verb, "one" -> summary.one,
      "many" -> summary.many)

  private def decodeSummary(raw: ujson.Value, path: String)
      : Result[DecisionSummaryProjection] = for
    value <- obj(raw, path)
    _ <- exact(value, Set("verb", "one", "many"), path)
    verb <- string(value, "verb", path)
    one <- string(value, "one", path)
    many <- string(value, "many", path)
  yield DecisionSummaryProjection(verb, one, many)

  private def encodeConfirm(confirm: CountedConfirmProjection): ujson.Value =
    ujson.Obj("verb" -> confirm.verb, "one" -> confirm.one,
      "many" -> confirm.many, "none" -> stringOption(confirm.none),
      "countsLocked" -> confirm.countsLocked)

  private def decodeConfirm(raw: ujson.Value, path: String)
      : Result[CountedConfirmProjection] = for
    value <- obj(raw, path)
    _ <- exact(value, Set("verb", "one", "many", "none", "countsLocked"), path)
    verb <- string(value, "verb", path)
    one <- string(value, "one", path)
    many <- string(value, "many", path)
    none <- optionalAbsent(value, "none", path)(string)
    countsLocked <- boolOr(value, "countsLocked", path, false)
  yield CountedConfirmProjection(verb, one, many, none, countsLocked)

  private def decodeChooseAmount(value: ujson.Obj, path: String)
      : Result[DecisionQueryProjection] = for
    _ <- exact(value, Set("form", "minAmount", "maxAmount", "suggested",
      "confirmLabel", "heading"), path)
    minAmount <- int(value, "minAmount", path)
    maxAmount <- int(value, "maxAmount", path)
    suggested <- optionalInt(value, "suggested", path)
    confirmLabel <- string(value, "confirmLabel", path)
    heading <- optionalString(value, "heading", path)
  yield DecisionQueryProjection.ChooseAmount(minAmount, maxAmount, suggested,
    confirmLabel, heading)

  private def decodePartition(value: ujson.Obj, path: String)
      : Result[DecisionQueryProjection] = for
    _ <- exact(value, Set("form", "sections", "options", "confirmLabel",
      "heading"), path)
    sectionRaws <- array(value, "sections", path)
    sections <- traverse(sectionRaws, s"$path.sections")(decodeSection)
    options <- optionRows(value, path)
    confirmLabel <- optionalString(value, "confirmLabel", path)
    heading <- optionalString(value, "heading", path)
  yield DecisionQueryProjection.Partition(sections, options, confirmLabel,
    heading)

  private def decodeDistribute(value: ujson.Obj, path: String)
      : Result[DecisionQueryProjection] = for
    _ <- exact(value, Set("form", "slots", "minTotal", "maxTotal",
      "confirmLabel", "heading"), path)
    slotRaws <- array(value, "slots", path)
    slots <- traverse(slotRaws, s"$path.slots")(decodeSlot)
    minTotal <- int(value, "minTotal", path)
    maxTotal <- int(value, "maxTotal", path)
    confirmLabel <- string(value, "confirmLabel", path)
    heading <- optionalString(value, "heading", path)
  yield DecisionQueryProjection.Distribute(slots, minTotal, maxTotal,
    confirmLabel, heading)

  private def decodeNegotiate(value: ujson.Obj, path: String)
      : Result[DecisionQueryProjection] = for
    _ <- exact(value, Set("form", "deal", "heading"), path)
    dealRaw <- field(value, "deal", path)
    deal <- decodeDeal(dealRaw, s"$path.deal")
    heading <- optionalString(value, "heading", path)
  yield DecisionQueryProjection.Negotiate(deal, heading)

  private def decodeInspect(value: ujson.Obj, path: String)
      : Result[DecisionQueryProjection] = for
    _ <- exact(value, Set("form", "cards", "done", "heading"), path)
    cardRaws <- array(value, "cards", path)
    cards <- traverse(cardRaws, s"$path.cards")(decodeCard)
    done <- field(value, "done", path).flatMap(
      decodeOptionRow(_, s"$path.done"))
    heading <- optionalString(value, "heading", path)
  yield DecisionQueryProjection.Inspect(cards, done, heading)

  private def optionRows(value: ujson.Obj, path: String)
      : Result[Vector[DecisionOptionProjection]] =
    array(value, "options", path)
      .flatMap(traverse(_, s"$path.options")(decodeOptionRow))

  private def encodeSection(section: DecisionSectionProjection): ujson.Value =
    ujson.Obj("key" -> section.key, "label" -> section.label,
      "minRequired" -> section.minRequired,
      "maxAllowed" -> intOption(section.maxAllowed))

  private def decodeSection(raw: ujson.Value, path: String)
      : Result[DecisionSectionProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("key", "label", "minRequired", "maxAllowed"), path)
    key <- string(row, "key", path); label <- string(row, "label", path)
    minimum <- int(row, "minRequired", path)
    maximum <- optionalInt(row, "maxAllowed", path)
  yield DecisionSectionProjection(key, label, minimum, maximum)

  private def encodeSlot(slot: DecisionSlotProjection): ujson.Value =
    ujson.Obj("option" -> encodeOptionRow(slot.option),
      "minimum" -> slot.minimum, "maximum" -> slot.maximum,
      "suggested" -> intOption(slot.suggested))

  private def decodeSlot(raw: ujson.Value, path: String)
      : Result[DecisionSlotProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("option", "minimum", "maximum", "suggested"), path)
    option <- field(row, "option", path).flatMap(
      decodeOptionRow(_, s"$path.option"))
    minimum <- int(row, "minimum", path)
    maximum <- int(row, "maximum", path)
    suggested <- optionalInt(row, "suggested", path)
  yield DecisionSlotProjection(option, minimum, maximum, suggested)

  private def encodeOptionRow(row: DecisionOptionProjection): ujson.Value =
    ujson.Obj("kind" -> row.kind, "id" -> row.id, "label" -> row.label,
      "card" -> option(row.card)(encodeCard),
      "details" -> encoded(row.details)(ujson.Str(_)),
      "badge" -> stringOption(row.badge), "state" -> row.state,
      "reason" -> stringOption(row.reason),
      "replaces" -> option(row.replaces)(encodeCard),
      "buries" -> ujson.Bool(row.buries),
      "variant" -> stringOption(row.variant),
      "group" -> stringOption(row.group),
      "consent" -> option(row.consent)(ConsentProjectionCodec.encodeAsk))

  private[projection] def decodeOptionRow(raw: ujson.Value, child: String)
      : Result[DecisionOptionProjection] = for
    row <- obj(raw, child)
    _ <- exact(row, Set("kind", "id", "label", "card", "details", "badge",
      "state", "reason", "replaces", "buries", "variant", "group",
      "consent"), child)
    kind <- string(row, "kind", child); id <- string(row, "id", child)
    label <- string(row, "label", child)
    card <- optionalAbsent(row, "card", child)(decodeCard)
    details <- stringsOrEmpty(row, "details", child)
    badge <- optionalString(row, "badge", child)
    state <- default(row, "state", child, "available")(string)
    reason <- optionalAbsent(row, "reason", child)(string)
    replaces <- optionalAbsent(row, "replaces", child)(decodeCard)
    buries <- boolOr(row, "buries", child, false)
    variant <- optionalAbsent(row, "variant", child)(string)
    group <- optionalAbsent(row, "group", child)(string)
    consent <- optionalAbsent(row, "consent", child)(
      ConsentProjectionCodec.decodeAsk)
  yield DecisionOptionProjection(kind, id, label, card, details, badge, state,
    reason, replaces, buries, variant, group, consent)

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
      : Result[NegotiationTransferProjection] = for
    row <- obj(raw, child)
    _ <- exact(row, Set("authorPlayerId", "recipientPlayerId", "favor",
      "relicCount", "relics"), child)
    author <- string(row, "authorPlayerId", child)
    recipient <- string(row, "recipientPlayerId", child)
    favor <- int(row, "favor", child); count <- int(row, "relicCount", child)
    raws <- array(row, "relics", child)
    relics <- traverse(raws, s"$child.relics")(decodeCard)
  yield NegotiationTransferProjection(author, recipient, favor, count, relics)
  private def decodeDisclosure(raw: ujson.Value, child: String)
      : Result[NegotiationDisclosureProjection] = for
    row <- obj(raw, child)
    _ <- exact(row, Set("authorPlayerId", "recipientPlayerId", "kind", "card"), child)
    author <- string(row, "authorPlayerId", child)
    recipient <- string(row, "recipientPlayerId", child)
    kind <- string(row, "kind", child)
    card <- optional(row, "card", child)(decodeCard)
  yield NegotiationDisclosureProjection(author, recipient, kind, card)
  private def decodeSiteRelic(raw: ujson.Value, child: String)
      : Result[NegotiationSiteRelicProjection] = for
    row <- obj(raw, child)
    _ <- exact(row, Set("siteId", "card"), child)
    site <- string(row, "siteId", child)
    cardRaw <- field(row, "card", child)
    card <- decodeCard(cardRaw, s"$child.card")
  yield NegotiationSiteRelicProjection(site, card)

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

  def decodeDeal(raw: ujson.Value, path: String): Result[NegotiationDealProjection] = for
    value <- obj(raw, path)
    _ <- exact(value, Set("participantPlayerIds", "acceptedPlayerIds", "transfers",
      "disclosures", "editing"), path)
    participants <- strings(value, "participantPlayerIds", path)
    accepted <- strings(value, "acceptedPlayerIds", path)
    transferRaws <- array(value, "transfers", path)
    transfers <- traverse(transferRaws, s"$path.transfers")(decodeTransfer)
    disclosureRaws <- array(value, "disclosures", path)
    disclosures <- traverse(disclosureRaws, s"$path.disclosures")(decodeDisclosure)
    editing <- optionalAbsent(value, "editing", path) { (rawEditing, child) => for
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
    yield NegotiationEditingProjection(favor, relics, advisers, sites, canAccept) }
  yield NegotiationDealProjection(participants, accepted, transfers, disclosures, editing)
