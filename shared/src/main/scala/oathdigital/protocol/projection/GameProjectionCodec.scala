package oathdigital.protocol.projection

import scala.util.control.NonFatal

import oathdigital.protocol.ProtocolDecodeFailure
import oathdigital.protocol.ProtocolDecodeFailure.MalformedJson
import ProjectionCodecSupport._
import WorldProjectionCodec._
import ActionProjectionCodec._

object GameProjectionCodec:
  private val Fields = Set(
    "gameId", "nextSequence", "phase", "activeParticipantId", "players", "world",
    "pawnLocations", "ready", "completed", "activePlayerResources",
    "currentSiteResources",
    "worldDeckCount", "worldDeckTopCardKind", "playerBoards",
    "oathkeeper", "banners",
    "favorBanks", "tracks",
    "relicDeckCount", "temporaryHandPreview",
    "walkerDecision", "walkerWaiting", "viewerPlayerId",
    "supplyMaximum", "supplyBands", "undo", "redo", "logRevision",
    "consentWaiting", "consentPrompt", "consentPreferences")

  def encode(value: GameProjection): String = ujson.write(encodeValue(value))
  def decode(json: String): Either[ProtocolDecodeFailure, GameProjection] =
    try decodeValue(ujson.read(json), "$")
    catch { case NonFatal(error) => Left(MalformedJson.of(error)) }

  private[projection] def encodeValue(value: GameProjection): ujson.Value =
    val result = ujson.Obj(
    "gameId" -> value.gameId, "nextSequence" -> ujson.Num(value.nextSequence.toDouble),
    "phase" -> value.phase, "activeParticipantId" -> stringOption(value.activeParticipantId),
    "players" -> encoded(value.players)(encodePlayer),
    "world" -> encoded(value.world)(encodeRegion),
    "pawnLocations" -> encoded(value.pawnLocations)(p => ujson.Obj(
      "playerId" -> p.playerId, "siteId" -> p.siteId)),
    "ready" -> value.ready, "completed" -> value.completed,
    "activePlayerResources" -> option(value.activePlayerResources)(r => ujson.Obj(
      "favor" -> r.favor, "faceUpSecrets" -> r.faceUpSecrets,
      "faceDownSecrets" -> r.faceDownSecrets,
      "committedSecrets" -> r.committedSecrets,
      "totalSecrets" -> r.totalSecrets, "supply" -> r.supply)),
    "currentSiteResources" -> option(value.currentSiteResources)(r => ujson.Obj(
      "siteId" -> r.siteId, "favor" -> r.favor, "secrets" -> r.secrets)),
    "worldDeckCount" -> value.worldDeckCount,
    "worldDeckTopCardKind" -> stringOption(value.worldDeckTopCardKind),
    "playerBoards" -> encoded(value.playerBoards)(encodeBoard),
    "oathkeeper" -> option(value.oathkeeper)(o => ujson.Obj(
      "goal" -> o.goal, "holderPlayerId" -> stringOption(o.holderPlayerId),
      "side" -> o.side, "usurperLimited" -> o.usurperLimited,
      "winnerPlayerId" -> stringOption(o.winnerPlayerId),
      "winnerVictoryKind" -> stringOption(o.winnerVictoryKind))),
    "banners" -> encoded(value.banners)(b => ujson.Obj(
      "banner" -> b.key, "face" -> b.face,
      "holderPlayerId" -> stringOption(b.holderPlayerId), "resources" -> b.resources)),
    "favorBanks" -> encoded(value.favorBanks)(b => ujson.Obj(
      "suit" -> b.suit, "count" -> b.count)),
    "tracks" -> option(value.tracks)(t => ujson.Obj(
      "round" -> t.round, "visionsDrawn" -> t.visionsDrawn,
      "usurperLimited" -> t.usurperLimited, "limiterRound" -> t.limiterRound,
      "firstPlayerId" -> t.firstPlayerId)),
    "relicDeckCount" -> value.relicDeckCount,
    "temporaryHandPreview" -> encoded(value.temporaryHandPreview)(encodeCard),
    "walkerDecision" -> option(value.walkerDecision)(encodeWalkerDecision),
    "walkerWaiting" -> option(value.walkerWaiting)(encodeWalkerWaiting),
    "supplyMaximum" -> value.supplyMaximum,
    "supplyBands" -> encoded(value.supplyBands)(b => ujson.Obj(
      "from" -> b.from, "to" -> intOption(b.to), "supply" -> b.supply)),
    "undo" -> option(value.undo)(offer => ujson.Obj("note" -> offer.note)),
    "redo" -> option(value.redo)(offer => ujson.Obj("note" -> offer.note)),
    "logRevision" -> option(value.logRevision)(revision =>
      ujson.Num(revision.toDouble)),
    "consentWaiting" -> option(value.consentWaiting)(
      ConsentProjectionCodec.encodeWaiting),
    "consentPrompt" -> option(value.consentPrompt)(
      ConsentProjectionCodec.encodePrompt),
    "consentPreferences" -> encoded(value.consentPreferences)(
      ConsentProjectionCodec.encodePreferences))
    value.viewerPlayerId.foreach(player => result("viewerPlayerId") = ujson.Str(player))
    result

  private[projection] def decodeValue(raw: ujson.Value, path: String): Result[GameProjection] = for
    value <- obj(raw, path); _ <- exact(value, Fields, path)
    game <- string(value, "gameId", path); sequence <- long(value, "nextSequence", path)
    phase <- string(value, "phase", path); active <- optionalString(value, "activeParticipantId", path)
    playerRaws <- array(value, "players", path); players <- traverse(playerRaws, s"$path.players")(decodePlayer)
    regionRaws <- array(value, "world", path); world <- traverse(regionRaws, s"$path.world")(decodeRegion)
    pawnRaws <- array(value, "pawnLocations", path)
    pawns <- traverse(pawnRaws, s"$path.pawnLocations") { (raw, child) => for
      row <- obj(raw, child); _ <- exact(row, Set("playerId", "siteId"), child)
      player <- string(row, "playerId", child); site <- string(row, "siteId", child)
    yield PawnLocationProjection(player, site) }
    ready <- bool(value, "ready", path)
    completed <- bool(value, "completed", path)
    resources <- optionalAbsent(value, "activePlayerResources", path)(decodeResources)
    siteResources <- optionalAbsent(value, "currentSiteResources", path)(decodeSiteResources)
    deckCount <- intOr(value, "worldDeckCount", path, 0)
    deckTop <- optionalAbsent(value, "worldDeckTopCardKind", path)(string)
    boardRaws <- default(value, "playerBoards", path, Vector.empty[ujson.Value])(array)
    boards <- traverse(boardRaws, s"$path.playerBoards")(decodeBoard)
    oathkeeper <- optionalAbsent(value, "oathkeeper", path)(decodeOathkeeper)
    bannerRaws <- default(value, "banners", path, Vector.empty[ujson.Value])(array)
    banners <- traverse(bannerRaws, s"$path.banners")(decodeBanner)
    bankRaws <- default(value, "favorBanks", path, Vector.empty[ujson.Value])(array)
    banks <- traverse(bankRaws, s"$path.favorBanks") { (raw, child) => for
      row <- obj(raw, child); _ <- exact(row, Set("suit", "count"), child)
      suit <- string(row, "suit", child); count <- int(row, "count", child)
    yield FavorBankProjection(suit, count) }
    tracks <- optionalAbsent(value, "tracks", path) { (raw, child) => for
      row <- obj(raw, child); _ <- exact(row, Set("round", "visionsDrawn",
        "usurperLimited", "limiterRound", "firstPlayerId"), child)
      round <- int(row, "round", child); visions <- int(row, "visionsDrawn", child)
      limited <- bool(row, "usurperLimited", child); limiter <- int(row, "limiterRound", child)
      first <- string(row, "firstPlayerId", child)
    yield GameTracksProjection(round, visions, limited, limiter, first) }
    relicDeck <- intOr(value, "relicDeckCount", path, 0)
    previewRaws <- default(value, "temporaryHandPreview", path,
      Vector.empty[ujson.Value])(array)
    preview <- traverse(previewRaws, s"$path.temporaryHandPreview")(decodeCard)
    walkerDecision <- optionalAbsent(value, "walkerDecision", path)(decodeWalkerDecision)
    walkerWaiting <- optionalAbsent(value, "walkerWaiting", path)(decodeWalkerWaiting)
    viewer <- optionalAbsent(value, "viewerPlayerId", path)(string)
    supplyMaximum <- intOr(value, "supplyMaximum", path, 0)
    bandRaws <- default(value, "supplyBands", path, Vector.empty[ujson.Value])(array)
    bands <- traverse(bandRaws, s"$path.supplyBands") { (raw, child) => for
      row <- obj(raw, child); _ <- exact(row, Set("from", "to", "supply"), child)
      from <- int(row, "from", child); to <- optionalAbsent(row, "to", child)(int)
      supply <- int(row, "supply", child)
    yield SupplyBandProjection(from, to, supply) }
    undo <- optionalAbsent(value, "undo", path)(decodeStepOffer)
    redo <- optionalAbsent(value, "redo", path)(decodeStepOffer)
    logRevision <- value.value.get("logRevision") match
      case None | Some(ujson.Null) => Right(None)
      case Some(_) => long(value, "logRevision", path).map(Some(_))
    consentWaiting <- optionalAbsent(value, "consentWaiting", path)(
      ConsentProjectionCodec.decodeWaiting)
    consentPrompt <- optionalAbsent(value, "consentPrompt", path)(
      ConsentProjectionCodec.decodePrompt)
    preferenceRaws <- default(value, "consentPreferences", path,
      Vector.empty[ujson.Value])(array)
    consentPreferences <- traverse(preferenceRaws,
      s"$path.consentPreferences")(ConsentProjectionCodec.decodePreferences)
  yield GameProjection(game, sequence, phase, active, players, world, pawns,
    ready, completed, resources, siteResources,
    deckCount, deckTop, boards, oathkeeper, banners,
    banks, tracks, relicDeck, preview,
    walkerDecision, walkerWaiting, viewer,
    supplyMaximum, bands, undo, redo, logRevision,
    consentWaiting, consentPrompt, consentPreferences)

  private def decodeStepOffer(raw: ujson.Value, path: String)
      : Result[StepOfferProjection] = for
    row <- obj(raw, path); _ <- exact(row, Set("note"), path)
    note <- string(row, "note", path)
  yield StepOfferProjection(note)

  private def decodeResources(raw: ujson.Value, path: String): Result[ActivePlayerResourcesProjection] = for
    v <- obj(raw, path); _ <- exact(v, Set("favor", "faceUpSecrets", "faceDownSecrets",
      "committedSecrets", "totalSecrets", "supply"), path)
    favor <- int(v, "favor", path); up <- int(v, "faceUpSecrets", path)
    down <- int(v, "faceDownSecrets", path)
    committed <- int(v, "committedSecrets", path)
    total <- int(v, "totalSecrets", path); supply <- int(v, "supply", path)
  yield ActivePlayerResourcesProjection(favor, up, down, committed, total, supply)
  private def decodeSiteResources(raw: ujson.Value, path: String): Result[CurrentSiteResourcesProjection] = for
    v <- obj(raw, path); _ <- exact(v, Set("siteId", "favor", "secrets"), path)
    site <- string(v, "siteId", path); favor <- int(v, "favor", path); secrets <- int(v, "secrets", path)
  yield CurrentSiteResourcesProjection(site, favor, secrets)
  private def decodeOathkeeper(raw: ujson.Value, path: String): Result[OathkeeperProjection] = for
    v <- obj(raw, path); _ <- exact(v, Set("goal", "holderPlayerId", "side", "usurperLimited", "winnerPlayerId", "winnerVictoryKind"), path)
    goal <- string(v, "goal", path); holder <- optionalString(v, "holderPlayerId", path)
    side <- string(v, "side", path); limited <- bool(v, "usurperLimited", path)
    winner <- optionalString(v, "winnerPlayerId", path); kind <- optionalString(v, "winnerVictoryKind", path)
  yield OathkeeperProjection(goal, holder, side, limited, winner, kind)
  private def decodeBanner(raw: ujson.Value, path: String): Result[BannerProjection] = for
    v <- obj(raw, path); _ <- exact(v, Set("banner", "face", "holderPlayerId", "resources"), path)
    key <- string(v, "banner", path); face <- string(v, "face", path)
    holder <- optionalString(v, "holderPlayerId", path); resources <- int(v, "resources", path)
  yield BannerProjection(key, face, holder, resources)
