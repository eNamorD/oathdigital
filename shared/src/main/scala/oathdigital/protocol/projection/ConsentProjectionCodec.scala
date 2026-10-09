package oathdigital.protocol.projection

import ProjectionCodecSupport._

private[projection] object ConsentProjectionCodec:
  def encodeAsk(value: ConsentAskProjection): ujson.Value = ujson.Obj(
    "granterPlayerIds" -> encoded(value.granterPlayerIds)(ujson.Str(_)),
    "powers" -> encoded(value.powers)(ujson.Str(_)))

  def decodeAsk(raw: ujson.Value, path: String)
      : Result[ConsentAskProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("granterPlayerIds", "powers"), path)
    granters <- strings(row, "granterPlayerIds", path)
    powers <- strings(row, "powers", path)
  yield ConsentAskProjection(granters, powers)

  def encodeWaiting(value: ConsentWaitingProjection): ujson.Value = ujson.Obj(
    "granterPlayerIds" -> encoded(value.granterPlayerIds)(ujson.Str(_)))

  def decodeWaiting(raw: ujson.Value, path: String)
      : Result[ConsentWaitingProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("granterPlayerIds"), path)
    granters <- strings(row, "granterPlayerIds", path)
  yield ConsentWaitingProjection(granters)

  def encodePrompt(value: ConsentPromptProjection): ujson.Value = ujson.Obj(
    "requesterPlayerId" -> value.requesterPlayerId,
    "powers" -> encoded(value.powers)(ujson.Str(_)),
    "target" -> value.target)

  def decodePrompt(raw: ujson.Value, path: String)
      : Result[ConsentPromptProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("requesterPlayerId", "powers", "target"), path)
    requester <- string(row, "requesterPlayerId", path)
    powers <- strings(row, "powers", path)
    target <- string(row, "target", path)
  yield ConsentPromptProjection(requester, powers, target)

  def encodePreferences(value: ConsentPreferencesProjection): ujson.Value =
    ujson.Obj("powerId" -> value.powerId, "power" -> value.power,
      "rows" -> encoded(value.rows)(row => ujson.Obj(
        "playerId" -> row.playerId, "setting" -> row.setting)))

  def decodePreferences(raw: ujson.Value, path: String)
      : Result[ConsentPreferencesProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("powerId", "power", "rows"), path)
    powerId <- string(row, "powerId", path)
    power <- string(row, "power", path)
    rowRaws <- array(row, "rows", path)
    rows <- traverse(rowRaws, s"$path.rows") { (entryRaw, child) => for
      entry <- obj(entryRaw, child)
      _ <- exact(entry, Set("playerId", "setting"), child)
      player <- string(entry, "playerId", child)
      setting <- string(entry, "setting", child)
    yield ConsentPreferenceRowProjection(player, setting) }
  yield ConsentPreferencesProjection(powerId, power, rows)
