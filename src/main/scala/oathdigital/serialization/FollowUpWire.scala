package oathdigital.serialization

import oathdigital.model._

/** Wire spellings shared by the walker's operations and events: a power's
  * source, a procedure reference, and a queued follow-up entry, which holds
  * both (follow-up actions design). */
private[serialization] trait FollowUpWire:
  this: GameEventJsonSupport =>
  import WireError._

  protected final def encodePowerSource(source: PowerSourceRef): ujson.Value =
    source match
      case PowerSourceRef.Site(site) =>
        ujson.Obj("kind" -> "site", "siteId" -> site.value)
      case PowerSourceRef.Card(card) =>
        ujson.Obj("kind" -> "card", "card" -> encodeCardId(card))
      case PowerSourceRef.Banner(banner) =>
        ujson.Obj("kind" -> "banner", "bannerKey" -> banner.key)

  protected final def decodePowerSource(value: ujson.Value,
      path: String): Either[WireError, PowerSourceRef] =
    value("kind").str match
      case "site" => Right(PowerSourceRef.Site(SiteId(value("siteId").str)))
      case "card" => decodeCardId(value("card"), s"$path.card")
        .map(PowerSourceRef.Card.apply)
      case "banner" => decodeBanner(value("bannerKey").str,
        s"$path.bannerKey").map(PowerSourceRef.Banner.apply)
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown note source '$other'"))

  /** Wire spelling of a procedure reference: a family tag plus its
    * key, so a decoder can reject a reference read back under the wrong
    * family (an End Wake key spelled `"action"`) or an unknown family,
    * rather than resolving on the key alone.
    */
  protected final def encodeProcedure(procedure: ProcedureRef): ujson.Value =
    ujson.Obj("family" -> procedure.family, "key" -> procedure.key)

  protected final def decodeProcedure(value: ujson.Value,
      path: String): Either[WireError, ProcedureRef] =
    val family = value("family").str
    val key = value("key").str
    ProcedureRef.fromFamilyKey(family, key).toRight(InvalidValue(path,
      s"unknown walker procedure '$family/$key'"))

  protected final def encodeFollowUp(followUp: FollowUp): ujson.Value =
    ujson.Obj(
      "playerId" -> followUp.player.value,
      "action" -> encodeProcedure(followUp.action),
      "source" -> encodePowerSource(followUp.source))

  protected final def decodeFollowUp(value: ujson.Value,
      path: String): Either[WireError, FollowUp] = for
    procedure <- decodeProcedure(value("action"), s"$path.action")
    action <- procedure match
      case action: ActionRef => Right(action)
      case other => Left(InvalidValue(s"$path.action",
        s"a follow-up is an action, not ${other.key}"))
    source <- decodePowerSource(value("source"), s"$path.source")
  yield FollowUp(PlayerId(value("playerId").str), action, source)
