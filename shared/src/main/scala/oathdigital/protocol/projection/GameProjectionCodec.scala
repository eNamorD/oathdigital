package oathdigital.protocol.projection

import scala.util.control.NonFatal

import oathdigital.protocol.ProtocolDecodeFailure
import oathdigital.protocol.ProtocolDecodeFailure.MalformedJson
import ProjectionCodecSupport._
import WorldProjectionCodec._
import ActionProjectionCodec._

object GameProjectionCodec {
  private val Fields = Set(
    "gameId", "nextSequence", "phase", "activeParticipantId", "players", "world",
    "pawnLocations", "legalControls", "ready", "completed", "activePlayerResources",
    "currentSiteResources", "actionSelectionOpen", "actionFamilies",
    "legalTravelDestinations", "legalSearchSources",
    "boardTargetActions", "pendingCardDecision", "campaign",
    "campaignRaidRelocation", "worldDeckCount", "worldDeckTopCardKind", "playerBoards",
    "oathkeeper", "banners", "minorActions",
    "favorBanks", "tracks",
    "relicDeckCount", "privateAdviserPreview",
    "walkerDecision", "walkerWaiting", "phasePowers", "lastCampaign")

  def encode(value: GameProjection): String = ujson.write(encodeValue(value))
  def decode(json: String): Either[ProtocolDecodeFailure, GameProjection] =
    try decodeValue(ujson.read(json), "$")
    catch { case NonFatal(error) => Left(MalformedJson("$",
      Option(error.getMessage).getOrElse("malformed JSON"))) }

  private[projection] def encodeValue(value: GameProjection): ujson.Value = ujson.Obj(
    "gameId" -> value.gameId, "nextSequence" -> ujson.Num(value.nextSequence.toDouble),
    "phase" -> value.phase, "activeParticipantId" -> stringOption(value.activeParticipantId),
    "players" -> encoded(value.players)(encodePlayer),
    "world" -> encoded(value.world)(encodeRegion),
    "pawnLocations" -> encoded(value.pawnLocations)(p => ujson.Obj(
      "playerId" -> p.playerId, "siteId" -> p.siteId)),
    "legalControls" -> encoded(value.legalControls)(ujson.Str(_)),
    "ready" -> value.ready, "completed" -> value.completed,
    "activePlayerResources" -> option(value.activePlayerResources)(r => ujson.Obj(
      "favor" -> r.favor, "faceUpSecrets" -> r.faceUpSecrets,
      "faceDownSecrets" -> r.faceDownSecrets,
      "committedSecrets" -> r.committedSecrets,
      "totalSecrets" -> r.totalSecrets, "supply" -> r.supply)),
    "currentSiteResources" -> option(value.currentSiteResources)(r => ujson.Obj(
      "siteId" -> r.siteId, "favor" -> r.favor, "secrets" -> r.secrets)),
    "actionSelectionOpen" -> value.actionSelectionOpen,
    "actionFamilies" -> encoded(value.actionFamilies)(ujson.Str(_)),
    "legalTravelDestinations" -> encoded(value.legalTravelDestinations)(d =>
      ujson.Obj("siteId" -> d.siteId, "supplyCost" -> d.supplyCost)),
    "legalSearchSources" -> encoded(value.legalSearchSources)(s => ujson.Obj(
      "kind" -> s.kind, "region" -> stringOption(s.region), "supplyCost" -> s.supplyCost)),
    "boardTargetActions" -> encoded(value.boardTargetActions)(encodeAction),
    "pendingCardDecision" -> option(value.pendingCardDecision)(encodePending),
    "campaign" -> option(value.campaign)(CampaignProjectionCodec.encode),
    "campaignRaidRelocation" -> option(value.campaignRaidRelocation)(r => ujson.Obj(
      "decisionId" -> r.decisionId, "actorPlayerId" -> r.actorPlayerId,
      "defenderPlayerId" -> r.defenderPlayerId, "originSiteId" -> r.originSiteId,
      "legalSiteIds" -> encoded(r.legalSiteIds)(ujson.Str(_)))),
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
    "minorActions" -> option(value.minorActions)(encodeMinor),
    "favorBanks" -> encoded(value.favorBanks)(b => ujson.Obj(
      "suit" -> b.suit, "count" -> b.count)),
    "tracks" -> option(value.tracks)(t => ujson.Obj(
      "round" -> t.round, "visionsDrawn" -> t.visionsDrawn,
      "usurperLimited" -> t.usurperLimited, "limiterRound" -> t.limiterRound,
      "firstPlayerId" -> t.firstPlayerId)),
    "relicDeckCount" -> value.relicDeckCount,
    "privateAdviserPreview" -> encoded(value.privateAdviserPreview)(encodeCard),
    "walkerDecision" -> option(value.walkerDecision)(encodeWalkerDecision),
    "walkerWaiting" -> option(value.walkerWaiting)(encodeWalkerWaiting),
    "phasePowers" -> encoded(value.phasePowers)(encodePhasePower),
    "lastCampaign" -> option(value.lastCampaign)(CampaignResultProjectionCodec.encode))

  private[projection] def decodeValue(raw: ujson.Value, path: String): Result[GameProjection] = for {
    value <- obj(raw, path); _ <- exact(value, Fields, path)
    game <- string(value, "gameId", path); sequence <- long(value, "nextSequence", path)
    phase <- string(value, "phase", path); active <- optionalString(value, "activeParticipantId", path)
    playerRaws <- array(value, "players", path); players <- traverse(playerRaws, s"$path.players")(decodePlayer)
    regionRaws <- array(value, "world", path); world <- traverse(regionRaws, s"$path.world")(decodeRegion)
    pawnRaws <- array(value, "pawnLocations", path)
    pawns <- traverse(pawnRaws, s"$path.pawnLocations") { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row, Set("playerId", "siteId"), child)
      player <- string(row, "playerId", child); site <- string(row, "siteId", child)
    } yield PawnLocationProjection(player, site) }
    controls <- strings(value, "legalControls", path); ready <- bool(value, "ready", path)
    completed <- bool(value, "completed", path)
    resources <- optionalAbsent(value, "activePlayerResources", path)(decodeResources)
    siteResources <- optionalAbsent(value, "currentSiteResources", path)(decodeSiteResources)
    actionOpen <- boolOr(value, "actionSelectionOpen", path, false)
    families <- stringsOrEmpty(value, "actionFamilies", path)
    destinationRaws <- default(value, "legalTravelDestinations", path,
      Vector.empty[ujson.Value])(array)
    destinations <- traverse(destinationRaws, s"$path.legalTravelDestinations")(decodeDestination)
    sourceRaws <- default(value, "legalSearchSources", path, Vector.empty[ujson.Value])(array)
    sources <- traverse(sourceRaws, s"$path.legalSearchSources")(decodeSearchSource)
    actionRaws <- default(value, "boardTargetActions", path, Vector.empty[ujson.Value])(array)
    actions <- traverse(actionRaws, s"$path.boardTargetActions")(decodeAction)
    pending <- optionalAbsent(value, "pendingCardDecision", path)(decodePending)
    campaign <- optionalAbsent(value, "campaign", path)(CampaignProjectionCodec.decode)
    relocation <- optionalAbsent(value, "campaignRaidRelocation", path)(decodeRelocation)
    deckCount <- intOr(value, "worldDeckCount", path, 0)
    deckTop <- optionalAbsent(value, "worldDeckTopCardKind", path)(string)
    boardRaws <- default(value, "playerBoards", path, Vector.empty[ujson.Value])(array)
    boards <- traverse(boardRaws, s"$path.playerBoards")(decodeBoard)
    oathkeeper <- optionalAbsent(value, "oathkeeper", path)(decodeOathkeeper)
    bannerRaws <- default(value, "banners", path, Vector.empty[ujson.Value])(array)
    banners <- traverse(bannerRaws, s"$path.banners")(decodeBanner)
    minor <- optionalAbsent(value, "minorActions", path)(decodeMinor)
    bankRaws <- default(value, "favorBanks", path, Vector.empty[ujson.Value])(array)
    banks <- traverse(bankRaws, s"$path.favorBanks") { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row, Set("suit", "count"), child)
      suit <- string(row, "suit", child); count <- int(row, "count", child)
    } yield FavorBankProjection(suit, count) }
    tracks <- optionalAbsent(value, "tracks", path) { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row, Set("round", "visionsDrawn",
        "usurperLimited", "limiterRound", "firstPlayerId"), child)
      round <- int(row, "round", child); visions <- int(row, "visionsDrawn", child)
      limited <- bool(row, "usurperLimited", child); limiter <- int(row, "limiterRound", child)
      first <- string(row, "firstPlayerId", child)
    } yield GameTracksProjection(round, visions, limited, limiter, first) }
    relicDeck <- intOr(value, "relicDeckCount", path, 0)
    previewRaws <- default(value, "privateAdviserPreview", path,
      Vector.empty[ujson.Value])(array)
    preview <- traverse(previewRaws, s"$path.privateAdviserPreview")(decodeCard)
    walkerDecision <- optionalAbsent(value, "walkerDecision", path)(decodeWalkerDecision)
    walkerWaiting <- optionalAbsent(value, "walkerWaiting", path)(decodeWalkerWaiting)
    powerRaws <- default(value, "phasePowers", path, Vector.empty[ujson.Value])(array)
    phasePowers <- traverse(powerRaws, s"$path.phasePowers") { (raw, child) =>
      decodePhasePower(raw, child) }
    lastCampaign <- optionalAbsent(value, "lastCampaign", path)(CampaignResultProjectionCodec.decode)
  } yield GameProjection(game, sequence, phase, active, players, world, pawns, controls,
    ready, completed, resources, siteResources, actionOpen, families, destinations,
    sources, actions, pending, campaign, relocation,
    deckCount, deckTop, boards, oathkeeper, banners, minor,
    banks, tracks, relicDeck, preview,
    walkerDecision, walkerWaiting, phasePowers, lastCampaign)

  private def decodeResources(raw: ujson.Value, path: String): Result[ActivePlayerResourcesProjection] = for {
    v <- obj(raw, path); _ <- exact(v, Set("favor", "faceUpSecrets", "faceDownSecrets",
      "committedSecrets", "totalSecrets", "supply"), path)
    favor <- int(v, "favor", path); up <- int(v, "faceUpSecrets", path)
    down <- int(v, "faceDownSecrets", path)
    committed <- int(v, "committedSecrets", path)
    total <- int(v, "totalSecrets", path); supply <- int(v, "supply", path)
  } yield ActivePlayerResourcesProjection(favor, up, down, committed, total, supply)
  private def decodeSiteResources(raw: ujson.Value, path: String): Result[CurrentSiteResourcesProjection] = for {
    v <- obj(raw, path); _ <- exact(v, Set("siteId", "favor", "secrets"), path)
    site <- string(v, "siteId", path); favor <- int(v, "favor", path); secrets <- int(v, "secrets", path)
  } yield CurrentSiteResourcesProjection(site, favor, secrets)
  private def decodeDestination(raw: ujson.Value, path: String): Result[LegalTravelDestinationProjection] = for {
    v <- obj(raw, path); _ <- exact(v, Set("siteId", "supplyCost"), path)
    site <- string(v, "siteId", path); cost <- int(v, "supplyCost", path)
  } yield LegalTravelDestinationProjection(site, cost)
  private def decodeSearchSource(raw: ujson.Value, path: String): Result[LegalSearchSourceProjection] = for {
    v <- obj(raw, path); _ <- exact(v, Set("kind", "region", "supplyCost"), path)
    kind <- string(v, "kind", path); region <- optionalString(v, "region", path)
    cost <- int(v, "supplyCost", path)
  } yield LegalSearchSourceProjection(kind, region, cost)
  private def decodeRelocation(raw: ujson.Value, path: String): Result[CampaignRaidRelocationProjection] = for {
    v <- obj(raw, path); _ <- exact(v, Set("decisionId", "actorPlayerId", "defenderPlayerId", "originSiteId", "legalSiteIds"), path)
    decision <- string(v, "decisionId", path); actor <- string(v, "actorPlayerId", path)
    defender <- string(v, "defenderPlayerId", path); origin <- string(v, "originSiteId", path)
    sites <- strings(v, "legalSiteIds", path)
  } yield CampaignRaidRelocationProjection(decision, actor, defender, origin, sites)
  private def decodeOathkeeper(raw: ujson.Value, path: String): Result[OathkeeperProjection] = for {
    v <- obj(raw, path); _ <- exact(v, Set("goal", "holderPlayerId", "side", "usurperLimited", "winnerPlayerId", "winnerVictoryKind"), path)
    goal <- string(v, "goal", path); holder <- optionalString(v, "holderPlayerId", path)
    side <- string(v, "side", path); limited <- bool(v, "usurperLimited", path)
    winner <- optionalString(v, "winnerPlayerId", path); kind <- optionalString(v, "winnerVictoryKind", path)
  } yield OathkeeperProjection(goal, holder, side, limited, winner, kind)
  private def decodeBanner(raw: ujson.Value, path: String): Result[BannerProjection] = for {
    v <- obj(raw, path); _ <- exact(v, Set("banner", "face", "holderPlayerId", "resources"), path)
    key <- string(v, "banner", path); face <- string(v, "face", path)
    holder <- optionalString(v, "holderPlayerId", path); resources <- int(v, "resources", path)
  } yield BannerProjection(key, face, holder, resources)
}
