package oathdigital.serialization

import oathdigital.model._

/** Wire spellings shared by the walker's operations and events: a power's
  * source, a procedure reference, and a queued entry, which holds both
  * (walker-driven game loop design, "The queue"). */
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

  protected final def encodeQueued(entry: QueuedProcedure): ujson.Value =
    val fields = Vector[(String, ujson.Value)](
      "actorId" -> entry.actor.value,
      "procedure" -> encodeProcedure(entry.procedure),
      "startArgs" -> ujson.Arr.from(entry.startArgs.map(
        DecisionAnswerCodec.encodeRef)),
      "kind" -> (entry.kind match
        case QueueKind.Run => "run"
        case QueueKind.Offer => "offer")) ++
      entry.source.map(source => "source" -> encodePowerSource(source))
    ujson.Obj.from(fields)

  protected final def decodeQueued(value: ujson.Value,
      path: String): Either[WireError, QueuedProcedure] = for
    procedure <- decodeProcedure(value("procedure"), s"$path.procedure")
    startable <- procedure match
      case startable: StartableRef => Right(startable)
      case other => Left(InvalidValue(s"$path.procedure",
        s"a queued procedure is startable, not ${other.key}"))
    startArgs <- traverse(value("startArgs").arr.zipWithIndex.toVector):
      case (ref, index) =>
        DecisionAnswerCodec.decodeRef(ref, s"$path.startArgs[$index]")
    source <- value.obj.get("source") match
      case None => Right(None)
      case Some(source) =>
        decodePowerSource(source, s"$path.source").map(Some(_))
    kind <- value("kind").str match
      case "run" => Right(QueueKind.Run)
      case "offer" => Right(QueueKind.Offer)
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown queue kind '$other'"))
  yield QueuedProcedure(PlayerId(value("actorId").str), startable,
    startArgs, source, kind)
