package oathdigital.protocol.projection

import ProjectionCodecSupport._

private[protocol] object WorldProjectionCodec {
  def encodePlayer(value: SetupPlayerProjection): ujson.Value = ujson.Obj(
    "playerId" -> value.playerId, "displayName" -> value.displayName,
    "role" -> value.role, "colorToken" -> value.color.key)
  def decodePlayer(raw: ujson.Value, path: String): Result[SetupPlayerProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("playerId", "displayName", "role", "colorToken"), path)
    playerId <- string(value, "playerId", path)
    displayName <- string(value, "displayName", path)
    role <- string(value, "role", path)
    color <- playerColor(value, "colorToken", path)
  } yield SetupPlayerProjection(playerId, displayName, role, color)

  def encodeCard(value: CardDetailsProjection): ujson.Value = ujson.Obj(
    "cardId" -> value.cardId, "cardKind" -> value.cardKind, "name" -> value.name,
    "suit" -> stringOption(value.suit), "restrictions" -> stringOption(value.restrictions),
    "rulesText" -> stringOption(value.rulesText), "orientation" -> stringOption(value.orientation),
    "side" -> stringOption(value.side), "favor" -> value.favor, "secrets" -> value.secrets,
    "relicValue" -> intOption(value.relicValue), "defense" -> intOption(value.defense),
    "hidden" -> value.hidden, "implemented" -> value.implemented)
  def decodeCard(raw: ujson.Value, path: String): Result[CardDetailsProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("cardId", "cardKind", "name", "suit", "restrictions",
      "rulesText", "orientation", "side", "favor", "secrets", "relicValue",
      "defense", "hidden", "implemented"), path)
    cardId <- string(value, "cardId", path)
    kind <- string(value, "cardKind", path)
    name <- string(value, "name", path)
    suit <- optionalAbsent(value, "suit", path)(string)
    restrictions <- optionalAbsent(value, "restrictions", path)(string)
    rulesText <- optionalAbsent(value, "rulesText", path)(string)
    orientation <- optionalAbsent(value, "orientation", path)(string)
    side <- optionalAbsent(value, "side", path)(string)
    favor <- intOr(value, "favor", path, 0)
    secrets <- intOr(value, "secrets", path, 0)
    relicValue <- optionalAbsent(value, "relicValue", path)(int)
    defense <- optionalAbsent(value, "defense", path)(int)
    hidden <- bool(value, "hidden", path)
    implemented <- boolOr(value, "implemented", path, true)
  } yield CardDetailsProjection(cardId, kind, name, suit, restrictions, rulesText,
    orientation, side, favor, secrets, relicValue, defense, hidden, implemented)

  private def encodePower(value: SitePowerProjection): ujson.Value = ujson.Obj(
    "kind" -> value.kind, "label" -> value.label,
    "description" -> stringOption(value.description))
  private def decodePower(raw: ujson.Value, path: String): Result[SitePowerProjection] = for {
    value <- obj(raw, path); _ <- exact(value, Set("kind", "label", "description"), path)
    kind <- string(value, "kind", path); label <- string(value, "label", path)
    description <- optionalString(value, "description", path)
  } yield SitePowerProjection(kind, label, description)

  private def encodeSiteCard(value: SiteCardProjection): ujson.Value = ujson.Obj(
    "denizenId" -> value.cardId, "label" -> value.label,
    "details" -> option(value.details)(encodeCard))
  private def decodeSiteCard(raw: ujson.Value, path: String): Result[SiteCardProjection] = for {
    value <- obj(raw, path); _ <- exact(value, Set("denizenId", "label", "details"), path)
    id <- string(value, "denizenId", path); label <- string(value, "label", path)
    details <- optionalAbsent(value, "details", path)(decodeCard)
  } yield SiteCardProjection(id, label, details)

  private def encodeRelics(value: SiteRelicsProjection): ujson.Value = ujson.Obj(
    "facedownCount" -> value.facedownCount,
    "knownRelics" -> encoded(value.knownRelics)(encodeCard))
  private def decodeRelics(raw: ujson.Value, path: String): Result[SiteRelicsProjection] = for {
    value <- obj(raw, path); _ <- exact(value, Set("facedownCount", "knownRelics"), path)
    count <- int(value, "facedownCount", path)
    raws <- default(value, "knownRelics", path, Vector.empty[ujson.Value])(array)
    cards <- traverse(raws, s"$path.knownRelics")(decodeCard)
  } yield SiteRelicsProjection(count, cards)

  /** The wire keeps the flat shape older clients read: kind, ruler and colour
    * are each spelled out, and the decoder checks they name the same force.
    */
  private def encodeForces(value: SiteForcesProjection): ujson.Value = {
    val (kind, ruler, player, color) = value match {
      case SiteForcesProjection.Exile(_, playerId, color, _) =>
        ("exile", "player", Some(playerId), color.key)
      case _: SiteForcesProjection.Imperial => ("imperial", "empire", None, "empire")
      case _: SiteForcesProjection.Bandit => ("bandit", "bandit", None, "bandit")
    }
    ujson.Obj("forceKind" -> kind, "count" -> value.count, "rulerKind" -> ruler,
      "rulerPlayerId" -> stringOption(player), "label" -> value.label,
      "colorToken" -> color)
  }
  private def decodeForces(raw: ujson.Value, path: String): Result[SiteForcesProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("forceKind", "count", "rulerKind", "rulerPlayerId",
      "label", "colorToken"), path)
    kind <- string(value, "forceKind", path); count <- int(value, "count", path)
    ruler <- string(value, "rulerKind", path)
    player <- optionalString(value, "rulerPlayerId", path)
    label <- string(value, "label", path); color <- string(value, "colorToken", path)
    _ <- Either.cond(count > 0, (), oathdigital.protocol.ProtocolDecodeFailure.InvalidValue(
      s"$path.count", "expected positive integer"))
    forces <- ((kind, ruler, player, color) match {
      case ("exile", "player", Some(playerId), _) =>
        playerColor(value, "colorToken", path).map(
          SiteForcesProjection.Exile(count, playerId, _, label))
      case ("imperial", "empire", None, "empire") =>
        Right(SiteForcesProjection.Imperial(count, label))
      case ("bandit", "bandit", None, "bandit") =>
        Right(SiteForcesProjection.Bandit(count, label))
      case _ => Left(oathdigital.protocol.ProtocolDecodeFailure.InvalidValue(path,
        "force, ruler, and color tokens do not agree"))
    }): Result[SiteForcesProjection]
  } yield forces

  private def encodeSite(value: SetupSiteProjection): ujson.Value = ujson.Obj(
    "siteId" -> value.siteId, "label" -> value.label,
    "looseFavor" -> value.looseFavor, "looseSecrets" -> value.looseSecrets,
    "denizenCapacity" -> value.denizenCapacity, "relicCapacity" -> value.relicCapacity,
    "defense" -> value.defense, "recoverDifficulty" -> intOption(value.recoverDifficulty),
    "forgeCost" -> option(value.forgeCost)(cost => ujson.Obj(
      "favor" -> cost.favor, "secrets" -> cost.secrets)),
    "powers" -> encoded(value.powers)(encodePower),
    "forces" -> option(value.forces)(encodeForces),
    "denizens" -> encoded(value.denizens)(encodeSiteCard), "relics" -> encodeRelics(value.relics))
  private def decodeSite(raw: ujson.Value, path: String): Result[SetupSiteProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("siteId", "label", "looseFavor", "looseSecrets",
      "denizenCapacity", "relicCapacity", "defense", "recoverDifficulty", "forgeCost",
      "powers", "forces", "denizens", "relics"), path)
    siteId <- string(value, "siteId", path); label <- string(value, "label", path)
    favor <- int(value, "looseFavor", path); secrets <- int(value, "looseSecrets", path)
    denizenCapacity <- int(value, "denizenCapacity", path)
    relicCapacity <- int(value, "relicCapacity", path); defense <- intOr(value, "defense", path, 0)
    recover <- optionalAbsent(value, "recoverDifficulty", path)(int)
    forge <- optionalAbsent(value, "forgeCost", path) { (raw, child) => for {
      cost <- obj(raw, child); _ <- exact(cost, Set("favor", "secrets"), child)
      favor <- int(cost, "favor", child); secrets <- int(cost, "secrets", child)
    } yield ForgeCostProjection(favor, secrets) }
    powerRaws <- default(value, "powers", path, Vector.empty[ujson.Value])(array)
    powers <- traverse(powerRaws, s"$path.powers")(decodePower)
    forces <- optionalAbsent(value, "forces", path)(decodeForces)
    denizenRaws <- array(value, "denizens", path)
    denizens <- traverse(denizenRaws, s"$path.denizens")(decodeSiteCard)
    relicRaw <- field(value, "relics", path); relics <- decodeRelics(relicRaw, s"$path.relics")
  } yield SetupSiteProjection(siteId, label, favor, secrets, denizenCapacity,
    relicCapacity, denizens, relics, defense, recover, forge, powers, forces)

  def encodeRegion(value: SetupRegionProjection): ujson.Value = ujson.Obj(
    "regionId" -> value.regionId, "discardCount" -> value.discardCount,
    "discardTopCardKind" -> stringOption(value.discardTopCardKind),
    "sites" -> encoded(value.sites)(encodeSite))
  def decodeRegion(raw: ujson.Value, path: String): Result[SetupRegionProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("regionId", "discardCount", "discardTopCardKind", "sites"), path)
    region <- string(value, "regionId", path); count <- intOr(value, "discardCount", path, 0)
    top <- optionalAbsent(value, "discardTopCardKind", path)(string)
    raws <- array(value, "sites", path); sites <- traverse(raws, s"$path.sites")(decodeSite)
  } yield SetupRegionProjection(region, sites, count, top)

  def encodeBoard(value: PlayerBoardProjection): ujson.Value = ujson.Obj(
    "playerId" -> value.playerId, "warbands" -> value.warbands, "favor" -> value.favor,
    "faceUpSecrets" -> value.faceUpSecrets, "faceDownSecrets" -> value.faceDownSecrets,
    "committedSecrets" -> value.committedSecrets, "totalSecrets" -> value.totalSecrets,
    "supply" -> value.supply, "pawnSiteId" -> stringOption(value.pawnSiteId),
    "advisers" -> encoded(value.advisers)(encodeCard),
    "relics" -> encoded(value.relics)(encodeCard),
    "revealedVision" -> option(value.revealedVision)(encodeCard),
    "banners" -> encoded(value.banners)(b => ujson.Obj(
      "banner" -> b.key, "face" -> b.face,
      "holderPlayerId" -> stringOption(b.holderPlayerId),
      "resources" -> b.resources)))
  def decodeBoard(raw: ujson.Value, path: String): Result[PlayerBoardProjection] = for {
    value <- obj(raw, path)
    _ <- exact(value, Set("playerId", "warbands", "favor", "faceUpSecrets",
      "faceDownSecrets", "committedSecrets", "totalSecrets", "supply", "pawnSiteId", "advisers", "relics",
      "revealedVision", "banners"), path)
    player <- string(value, "playerId", path); warbands <- int(value, "warbands", path)
    favor <- int(value, "favor", path); up <- int(value, "faceUpSecrets", path)
    down <- int(value, "faceDownSecrets", path)
    committed <- int(value, "committedSecrets", path)
    total <- int(value, "totalSecrets", path); supply <- int(value, "supply", path)
    pawn <- optionalString(value, "pawnSiteId", path)
    adviserRaws <- array(value, "advisers", path)
    advisers <- traverse(adviserRaws, s"$path.advisers")(decodeCard)
    relicRaws <- array(value, "relics", path); relics <- traverse(relicRaws, s"$path.relics")(decodeCard)
    vision <- optional(value, "revealedVision", path)(decodeCard)
    bannerRaws <- default(value, "banners", path, Vector.empty[ujson.Value])(array)
    banners <- traverse(bannerRaws, s"$path.banners") { (raw, child) => for {
      row <- obj(raw, child); _ <- exact(row,
        Set("banner", "face", "holderPlayerId", "resources"), child)
      key <- string(row, "banner", child); face <- string(row, "face", child)
      holder <- optionalString(row, "holderPlayerId", child)
      resources <- int(row, "resources", child)
    } yield BannerProjection(key, face, holder, resources) }
  } yield PlayerBoardProjection(player, warbands, favor, up, down, committed, total, supply, pawn,
    advisers, relics, vision, banners)
}
