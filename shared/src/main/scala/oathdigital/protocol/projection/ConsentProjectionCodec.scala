package oathdigital.protocol.projection

import ProjectionCodecSupport._

private[projection] object ConsentProjectionCodec:
  def encodeAsk(value: ConsentAskProjection): ujson.Value =
    val row = ujson.Obj(
      "granterPlayerIds" -> encoded(value.granterPlayerIds)(ujson.Str(_)),
      "powers" -> encoded(value.powers)(ujson.Str(_)))
    // Written only when set (catalog batch 8, slice 15).
    if value.alternatives.nonEmpty then
      row("alternatives") = encoded(value.alternatives)(alternative =>
        ujson.Obj("granterPlayerId" -> alternative.granterPlayerId,
          "powers" -> encoded(alternative.powers)(ujson.Str(_)),
          "paying" -> alternative.paying))
    row

  def decodeAsk(raw: ujson.Value, path: String)
      : Result[ConsentAskProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("granterPlayerIds", "powers", "alternatives"), path)
    granters <- strings(row, "granterPlayerIds", path)
    powers <- strings(row, "powers", path)
    alternativeRaws <- default(row, "alternatives", path,
      Vector.empty[ujson.Value])(array)
    alternatives <- traverse(alternativeRaws, s"$path.alternatives") {
      (entryRaw, child) => for
        entry <- obj(entryRaw, child)
        _ <- exact(entry, Set("granterPlayerId", "powers", "paying"), child)
        granter <- string(entry, "granterPlayerId", child)
        powers <- strings(entry, "powers", child)
        paying <- bool(entry, "paying", child)
      yield ConsentAlternativeProjection(granter, powers, paying) }
  yield ConsentAskProjection(granters, powers, alternatives)

  def encodeWaiting(value: ConsentWaitingProjection): ujson.Value =
    val row = ujson.Obj(
      "granterPlayerIds" -> encoded(value.granterPlayerIds)(ujson.Str(_)))
    if value.either then row("either") = true
    row

  def decodeWaiting(raw: ujson.Value, path: String)
      : Result[ConsentWaitingProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("granterPlayerIds", "either"), path)
    granters <- strings(row, "granterPlayerIds", path)
    either <- boolOr(row, "either", path, false)
  yield ConsentWaitingProjection(granters, either)

  def encodePrompt(value: ConsentPromptProjection): ujson.Value =
    val row = ujson.Obj(
      "requesterPlayerId" -> value.requesterPlayerId,
      "powers" -> encoded(value.powers)(ujson.Str(_)),
      "target" -> value.target)
    if value.paying then row("paying") = true
    row

  def decodePrompt(raw: ujson.Value, path: String)
      : Result[ConsentPromptProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("requesterPlayerId", "powers", "target", "paying"),
      path)
    requester <- string(row, "requesterPlayerId", path)
    powers <- strings(row, "powers", path)
    target <- string(row, "target", path)
    paying <- boolOr(row, "paying", path, false)
  yield ConsentPromptProjection(requester, powers, target, paying)

  def encodePreferences(value: ConsentPreferencesProjection): ujson.Value =
    ujson.Obj("powerId" -> value.powerId, "power" -> value.power,
      "rows" -> encoded(value.rows)(row => ujson.Obj(
        "playerId" -> row.playerId, "setting" -> row.setting)),
      "cardId" -> stringOption(value.cardId))

  def decodePreferences(raw: ujson.Value, path: String)
      : Result[ConsentPreferencesProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("powerId", "power", "rows", "cardId"), path)
    powerId <- string(row, "powerId", path)
    power <- string(row, "power", path)
    rowRaws <- array(row, "rows", path)
    rows <- traverse(rowRaws, s"$path.rows") { (entryRaw, child) => for
      entry <- obj(entryRaw, child)
      _ <- exact(entry, Set("playerId", "setting"), child)
      player <- string(entry, "playerId", child)
      setting <- string(entry, "setting", child)
    yield ConsentPreferenceRowProjection(player, setting) }
    cardId <- optionalAbsent(row, "cardId", path)(string)
  yield ConsentPreferencesProjection(powerId, power, rows, cardId)
