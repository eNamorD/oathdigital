package oathdigital.serialization

import scala.util.control.NonFatal

import oathdigital.gameplay.walker.{ActionBoundaryRan, ChoicePayload,
  FollowUpSkipped, PowerNoted, ProcedureDequeued, RollPayload,
  WalkerCompleted, DeltaMeaning, WalkerParked, WalkerStepPayload,
  WalkerStepRecorded}
import oathdigital.gameplay.walker.DeltaMeaning.{DicePoolModified,
  OperationApplied, RelicAcquired, SupplySpent}
import oathdigital.model._

/** Wire vocabulary for generic walker journal facts. */
private[serialization] trait WalkerEventCodec extends WalkerOperationCodec:
  this: GameEventJsonSupport =>
  import GameEventWire._
  import WireError._

  protected final val walkerDiscriminator: PartialFunction[OathEvent, String] =
    case _: WalkerStepRecorded => WalkerStepRecordedType
    case _: WalkerParked => WalkerParkedType
    case _: WalkerCompleted => WalkerCompletedType
    case _: PowerNoted => PowerNotedType
    case _: ProcedureDequeued => ProcedureDequeuedType
    case _: FollowUpSkipped => FollowUpSkippedType
    case ActionBoundaryRan => ActionBoundaryRanType

  protected final val walkerEncoder: PartialFunction[OathEvent, ujson.Value] =
    case WalkerStepRecorded(nodeId, payload, ops, contributions) =>
      ujson.Obj(
        "nodeId" -> nodeId,
        "step" -> encodeStepPayload(payload),
        "ops" -> ujson.Arr.from(ops.map(encodeOperation)),
        "contributions" -> stringArray(contributions.map(_.value)))
    case WalkerParked(procedure, at, answered, startArgs, startFacts) =>
      ujson.Obj(
        "procedure" -> encodeProcedure(procedure),
        "at" -> stringArray(at),
        "answered" -> ujson.Arr.from(answered.map(encodeAnswered)),
        // Always written, empty for an action that selects nothing. A park
        // written before batch-1 Task 5 has no such key at all, and `decodeParked` reads a missing key as empty.
        "startArgs" -> ujson.Arr.from(startArgs.map(
          DecisionAnswerCodec.encodeRef)),
        // Read the same way: a park written before start facts has none.
        "startFacts" -> ujson.Arr.from(startFacts.map(
          DecisionAnswerCodec.encodeRef)))
    case WalkerCompleted(procedure) => ujson.Obj(
      "procedure" -> encodeProcedure(procedure))
    case PowerNoted(power, note, covers) => ujson.Obj(
      "powerId" -> power.value, "covers" -> ujson.Bool(covers),
      "note" -> encodeNote(note))
    case ProcedureDequeued(entry) =>
      ujson.Obj("entry" -> encodeQueued(entry))
    case FollowUpSkipped(entry) =>
      ujson.Obj("entry" -> encodeQueued(entry))
    case ActionBoundaryRan => ujson.Obj()

  protected final def walkerDecode(eventType: String, payload: ujson.Value,
      path: String)
      : Option[Either[WireError, OathEvent]] =
    val decoder: PartialFunction[String, Either[WireError, OathEvent]] =
      case WalkerStepRecordedType => decodeStepRecorded(payload, path)
      case WalkerParkedType => decodeParked(payload, path)
      case WalkerCompletedType => for
        procedure <- decodeProcedure(payload("procedure"),
          s"$path.procedure")
      yield WalkerCompleted(procedure)
      case PowerNotedType => decodePowerNoted(payload, path)
      case ProcedureDequeuedType =>
        decodeQueued(payload("entry"), s"$path.entry")
          .map(ProcedureDequeued(_))
      case FollowUpSkippedType =>
        decodeQueued(payload("entry"), s"$path.entry")
          .map(FollowUpSkipped(_))
      case ActionBoundaryRanType => Right(ActionBoundaryRan)
    decoder.lift(eventType)

  private def encodeAnswered(answered: Answered): ujson.Value = ujson.Obj(
    "decisionId" -> answered.decisionId,
    "payload" -> DecisionAnswerCodec.encode(answered.answer),
    "byPlayerId" -> answered.by.value)

  private def decodeAnswered(value: ujson.Value,
      path: String): Either[WireError, Answered] = for
    answer <- DecisionAnswerCodec.decode(value("payload"), s"$path.payload")
  yield Answered(value("decisionId").str, answer,
    PlayerId(value("byPlayerId").str))

  private def encodeStepPayload(payload: WalkerStepPayload): ujson.Value =
    payload match
      case WalkerStepPayload.DeltaRecorded(meaning) =>
        ujson.Obj("kind" -> "delta", "meaning" -> encodeDeltaMeaning(meaning))
      case ChoicePayload(decisionId, answer, by) => ujson.Obj(
        "kind" -> "choice", "decisionId" -> decisionId,
        "payload" -> DecisionAnswerCodec.encode(answer),
        "byPlayerId" -> by.value)
      case RollPayload(pool, faces, automatic) =>
        val encoded = ujson.Obj(
          "kind" -> "roll", "pool" -> pool.value,
          "faces" -> ujson.Arr.from(faces.map(encodeDieFace)))
        if automatic then encoded("automatic") = ujson.True
        encoded
      case other => throw new IllegalArgumentException(
        s"unsupported walker step payload $other")

  private def decodeStepPayload(value: ujson.Value,
      path: String): Either[WireError, WalkerStepPayload] =
    value("kind").str match
      case "delta" => decodeDeltaMeaning(value("meaning"), s"$path.meaning")
        .map(WalkerStepPayload.DeltaRecorded.apply)
      case "choice" => DecisionAnswerCodec.decode(value("payload"), s"$path.payload")
        .map(ChoicePayload(value("decisionId").str, _,
          PlayerId(value("byPlayerId").str)))
      case "roll" => traverse(value("faces").arr.toVector)(face =>
        decodeDieFace(face.str, s"$path.faces"))
        .map(faces => RollPayload(PoolKey(value("pool").str), faces,
          value.obj.get("automatic").exists(_.bool)))
      case other => Left(InvalidValue(s"$path.kind",
        s"unknown walker step payload '$other'"))

  /** A procedure's note has no source and writes no `source` field. */
  private def encodeNote(note: PowerNote): ujson.Value =
    val fields = ujson.Obj("key" -> note.key,
      "args" -> ujson.Arr.from(note.args.map(encodeNoteArg)))
    note.source.foreach(source =>
      fields("source") = encodePowerSource(source))
    fields

  private def encodeNoteArg(arg: NoteArg): ujson.Value = arg match
    case NoteArg.Player(id) => ujson.Obj("kind" -> "player", "playerId" -> id.value)
    case NoteArg.Card(id) => ujson.Obj("kind" -> "card", "card" -> encodeCardId(id))
    case NoteArg.Site(id) => ujson.Obj("kind" -> "site", "siteId" -> id.value)
    case NoteArg.Amount(value, unit) => ujson.Obj("kind" -> "amount",
      "value" -> value, "unit" -> unit.key)
    case NoteArg.Number(value) => ujson.Obj("kind" -> "number", "value" -> value)
    case NoteArg.Bank(suit) => ujson.Obj("kind" -> "bank", "suit" -> suit.key)
    case NoteArg.Cards(ids) => ujson.Obj("kind" -> "cards",
      "cards" -> ujson.Arr.from(ids.map(encodeCardId)))
    case NoteArg.Banner(banner) => ujson.Obj("kind" -> "banner",
      "bannerKey" -> banner.key)
    case NoteArg.Pile(pile) => ujson.Obj("kind" -> "pile",
      "pile" -> encodeSearchSource(pile))
    case NoteArg.RegionName(region) => ujson.Obj("kind" -> "region",
      "region" -> region.key)
    case NoteArg.SuitName(suit) => ujson.Obj("kind" -> "suit",
      "suit" -> suit.key)
    case NoteArg.Goal(goal) => ujson.Obj("kind" -> "goal", "goal" -> goal.key)
    case NoteArg.Dice(faces) => ujson.Obj("kind" -> "dice",
      "faces" -> ujson.Arr.from(faces.map(encodeDieFace)))
    case NoteArg.Players(ids) => ujson.Obj("kind" -> "players",
      "playerIds" -> ujson.Arr.from(ids.map(_.value)))
    case NoteArg.Clauses(parts) => ujson.Obj("kind" -> "clauses",
      "clauses" -> ujson.Arr.from(parts.map(encodeNoteClause)))
    case NoteArg.Placements(entries) => ujson.Obj("kind" -> "placements",
      "placements" -> ujson.Arr.from(entries.map((count, site) =>
        ujson.Obj("count" -> count, "siteId" -> site.value))))
    case NoteArg.Things(items) => ujson.Obj("kind" -> "things",
      "things" -> ujson.Arr.from(items.map(encodeNoteThing)))

  private def encodeNoteThing(thing: NoteThing): ujson.Value = thing match
    case NoteThing.Site(id) => ujson.Obj("kind" -> "site",
      "siteId" -> id.value)
    case NoteThing.Pawn(player) => ujson.Obj("kind" -> "pawn",
      "playerId" -> player.value)
    case NoteThing.Card(id) => ujson.Obj("kind" -> "card",
      "card" -> encodeCardId(id))
    case NoteThing.Banner(banner) => ujson.Obj("kind" -> "banner",
      "bannerKey" -> banner.key)

  private def encodeNoteClause(clause: NoteClause): ujson.Value = clause match
    case NoteClause.BurnedFavor(count) =>
      ujson.Obj("kind" -> "burned-favor", "count" -> count)
    case NoteClause.Discarded(ids) => ujson.Obj("kind" -> "discarded",
      "cards" -> ujson.Arr.from(ids.map(encodeCardId)))
    case NoteClause.SetAside(ids) => ujson.Obj("kind" -> "set-aside",
      "cards" -> ujson.Arr.from(ids.map(encodeCardId)))

  private def decodePowerNoted(value: ujson.Value,
      path: String): Either[WireError, OathEvent] = try for
    note <- decodeNote(value("note"), s"$path.note")
  yield PowerNoted(PowerId(value("powerId").str), note, value("covers").bool)
  catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid power note"))) }

  private def decodeNote(value: ujson.Value,
      path: String): Either[WireError, PowerNote] = for
    source <- value.obj.get("source") match
      case None => Right(None)
      case Some(field) =>
        decodePowerSource(field, s"$path.source").map(Some.apply)
    args <- traverse(value("args").arr.zipWithIndex.toVector):
      case (arg, index) => decodeNoteArg(arg, s"$path.args[$index]")
  yield PowerNote(source, value("key").str, args)

  private def decodeNoteArg(value: ujson.Value,
      path: String): Either[WireError, NoteArg] = value("kind").str match
    case "player" => Right(NoteArg.Player(PlayerId(value("playerId").str)))
    case "card" => decodeCardId(value("card"), s"$path.card")
      .map(NoteArg.Card.apply)
    case "site" => Right(NoteArg.Site(SiteId(value("siteId").str)))
    case "amount" =>
      for
        amount <- decodeCount(value("value"), s"$path.value")
        unit <- NoteUnit.fromKey(value("unit").str).toRight(
          InvalidValue(s"$path.unit", "unknown note unit"))
      yield NoteArg.Amount(amount, unit)
    case "number" => decodeCount(value("value"), s"$path.value")
      .map(NoteArg.Number.apply)
    case "bank" => decodeSuit(value("suit").str, s"$path.suit")
      .map(NoteArg.Bank.apply)
    case "dice" => traverse(value("faces").arr.toVector)(face =>
      decodeDieFace(face.str, s"$path.faces")).map(NoteArg.Dice.apply)
    case "cards" => traverse(value("cards").arr.zipWithIndex.toVector)({
      case (card, index) => decodeCardId(card, s"$path.cards[$index]") })
      .map(NoteArg.Cards.apply)
    case "banner" => decodeBanner(value("bannerKey").str, s"$path.bannerKey")
      .map(NoteArg.Banner.apply)
    case "pile" => decodeSearchSource(value("pile"), s"$path.pile")
      .map(NoteArg.Pile.apply)
    case "region" => decodeRegion(value("region").str, s"$path.region")
      .map(NoteArg.RegionName.apply)
    case "suit" => decodeSuit(value("suit").str, s"$path.suit")
      .map(NoteArg.SuitName.apply)
    case "goal" => OathkeeperGoal.all.find(_.key == value("goal").str)
      .toRight(InvalidValue(s"$path.goal", "unknown Oathkeeper goal"))
      .map(NoteArg.Goal.apply)
    case "players" => Right(NoteArg.Players(
      value("playerIds").arr.toVector.map(id => PlayerId(id.str))))
    case "clauses" => traverse(value("clauses").arr.zipWithIndex.toVector)({
      case (clause, index) =>
        decodeNoteClause(clause, s"$path.clauses[$index]") })
      .map(NoteArg.Clauses.apply)
    case "placements" =>
      traverse(value("placements").arr.zipWithIndex.toVector)({
        case (entry, index) =>
          decodeCount(entry("count"), s"$path.placements[$index].count")
            .map(_ -> SiteId(entry("siteId").str)) })
        .map(NoteArg.Placements.apply)
    case "things" => traverse(value("things").arr.zipWithIndex.toVector)({
      case (thing, index) =>
        decodeNoteThing(thing, s"$path.things[$index]") })
      .map(NoteArg.Things.apply)
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown note argument '$other'"))

  private def decodeNoteThing(value: ujson.Value, path: String)
      : Either[WireError, NoteThing] = value("kind").str match
    case "site" => Right(NoteThing.Site(SiteId(value("siteId").str)))
    case "pawn" => Right(NoteThing.Pawn(PlayerId(value("playerId").str)))
    case "card" => decodeCardId(value("card"), s"$path.card")
      .map(NoteThing.Card.apply)
    case "banner" => decodeBanner(value("bannerKey").str, s"$path.bannerKey")
      .map(NoteThing.Banner.apply)
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown note thing '$other'"))

  private def decodeNoteClause(value: ujson.Value, path: String)
      : Either[WireError, NoteClause] = value("kind").str match
    case "burned-favor" => decodeCount(value("count"), s"$path.count")
      .map(NoteClause.BurnedFavor.apply)
    case "discarded" => traverse(value("cards").arr.zipWithIndex.toVector)({
      case (card, index) => decodeCardId(card, s"$path.cards[$index]") })
      .map(NoteClause.Discarded.apply)
    case "set-aside" => traverse(value("cards").arr.zipWithIndex.toVector)({
      case (card, index) => decodeCardId(card, s"$path.cards[$index]") })
      .map(NoteClause.SetAside.apply)
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown note clause '$other'"))

  private def decodeCount(value: ujson.Value, path: String)
      : Either[WireError, Int] = decodeSignedInt(value, path).flatMap(count =>
    Either.cond(count >= 0, count, InvalidValue(path, "must not be negative")))

  private def encodeDeltaMeaning(meaning: DeltaMeaning): ujson.Value =
    meaning match
      case DicePoolModified(pool, delta) => ujson.Obj(
        "kind" -> "dice-pool-modified", "pool" -> pool.value,
        "delta" -> delta)
      case SupplySpent(player, amount) => ujson.Obj(
        "kind" -> "supply-spent", "playerId" -> player.value,
        "amount" -> amount)
      case RelicAcquired(player, relic, site) => ujson.Obj(
        "kind" -> "relic-acquired", "playerId" -> player.value,
        "relicId" -> relic.value, "siteId" -> site.value)
      case OperationApplied(label) => ujson.Obj(
        "kind" -> "operation-applied", "label" -> label)

  private def decodeDeltaMeaning(value: ujson.Value,
      path: String): Either[WireError, DeltaMeaning] = value("kind").str match
    case "dice-pool-modified" =>
      decodeSignedInt(value("delta"), s"$path.delta").map(delta =>
        DicePoolModified(PoolKey(value("pool").str), delta))
    case "supply-spent" =>
      decodeSignedInt(value("amount"), s"$path.amount").flatMap { amount =>
        if amount > 0 then Right(SupplySpent(
          PlayerId(value("playerId").str), amount))
        else Left(InvalidValue(s"$path.amount", "must be positive"))
      }
    case "relic-acquired" => Right(RelicAcquired(
      PlayerId(value("playerId").str), RelicId(value("relicId").str),
      SiteId(value("siteId").str)))
    case "operation-applied" =>
      Right(OperationApplied(value("label").str))
    case other => Left(InvalidValue(s"$path.kind",
      s"unknown walker delta meaning '$other'"))

  private def decodeStepRecorded(value: ujson.Value,
      path: String): Either[WireError, OathEvent] = try for
    step <- decodeStepPayload(value("step"), s"$path.step")
    ops <- traverse(value("ops").arr.zipWithIndex.toVector):
      case (operation, index) => decodeOperation(operation, s"$path.ops[$index]")
    contributions = value("contributions").arr.toVector.map(id =>
      PowerId(id.str))
  yield WalkerStepRecorded(
    value("nodeId").str, step, ops, contributions)
  catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid walker step"))) }

  private def decodeParked(value: ujson.Value,
      path: String): Either[WireError, OathEvent] = try for
    procedure <- decodeProcedure(value("procedure"), s"$path.procedure")
    answered <- traverse(value("answered").arr.zipWithIndex.toVector):
      case (answer, index) => decodeAnswered(answer,
        s"$path.answered[$index]")
    startArgs <- decodeRefs(value, "startArgs", s"$path.startArgs")
    startFacts <- decodeRefs(value, "startFacts", s"$path.startFacts")
  yield WalkerParked(procedure,
    value("at").arr.toVector.map(_.str), answered, startArgs, startFacts)
  catch { case NonFatal(error) => Left(InvalidValue(path,
    Option(error.getMessage).getOrElse("invalid walker park"))) }

  /** Start selections and start facts are `DecisionOptionRef`s and nothing
    * else, so this names no action and needs no arm per action -- a future
    * action that selects a site, a card or a player already round-trips here
    * unchanged.
    */
  private def decodeRefs(value: ujson.Value, key: String, path: String)
      : Either[WireError, Vector[DecisionOptionRef]] =
    value.obj.get(key) match
      case None => Right(Vector.empty)
      case Some(args) => traverse(args.arr.zipWithIndex.toVector):
        case (ref, index) =>
          DecisionAnswerCodec.decodeRef(ref, s"$path[$index]")
