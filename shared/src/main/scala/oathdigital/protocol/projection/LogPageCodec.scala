package oathdigital.protocol.projection

import scala.util.control.NonFatal

import oathdigital.protocol.ProtocolDecodeFailure
import oathdigital.protocol.ProtocolDecodeFailure.{InvalidValue, MalformedJson,
  UnknownVariant}
import ProjectionCodecSupport._

/** The log page's wire form. One encoder and one decoder arm per span case,
  * each with its own `exact` set, so a field another case declares is an
  * unexpected field here rather than an ignored one.
  *
  * A kind this build does not know is an `UnknownVariant`, not a span
  * rendered as its text. The frontend ships inside the server's own
  * resources (`build.sbt` copies `fullLinkJS`'s output into
  * `resourceManaged`), so "a newer server's span reaching an older page" is
  * not a state this system reaches; what remains is a browser holding a
  * stale cached bundle, which the client reports and offers to reload.
  * Tolerating the kind instead would cost an `Unknown` case that every
  * downstream match carries forever
  * (`docs/superpowers/specs/2026-10-15-typed-log-span-wire-design.md`).
  */
object LogPageCodec:
  private val PageFields = Set("gameId", "after", "nextSequence", "entries")
  private val EntryFields = Set("sequence", "ordinal", "kind", "spans",
    "section", "takenBack")
  private val SectionFields = Set("id", "action")
  private val TakenBackFields = Set("group", "header")

  def encode(value: LogPage): String = ujson.write(ujson.Obj(
    "gameId" -> value.gameId,
    "after" -> ujson.Num(value.after.toDouble),
    "nextSequence" -> ujson.Num(value.nextSequence.toDouble),
    "entries" -> encoded(value.entries)(encodeEntry)))

  def decode(json: String): Either[ProtocolDecodeFailure, LogPage] =
    try decodePage(ujson.read(json), "$")
    catch { case NonFatal(error) => Left(MalformedJson.of(error)) }

  private def encodeEntry(entry: LogEntry): ujson.Value = ujson.Obj(
    "sequence" -> ujson.Num(entry.sequence.toDouble),
    "ordinal" -> entry.ordinal, "kind" -> entry.kind.key,
    "spans" -> encoded(entry.spans)(encodeSpan),
    "section" -> option(entry.section)(section => ujson.Obj(
      "id" -> ujson.Num(section.id.toDouble), "action" -> section.action)),
    "takenBack" -> option(entry.takenBack)(group => ujson.Obj(
      "group" -> ujson.Num(group.group.toDouble),
      "header" -> encoded(group.header)(encodeSpan))))

  /** No `text`: every case computes it, on both sides of the wire. */
  private def encodeSpan(span: LogSpan): ujson.Value = span match
    case LogSpan.Text(value) => ujson.Obj("kind" -> "text", "text" -> value)
    case LogSpan.Player(id, name) =>
      ujson.Obj("kind" -> "player", "id" -> id, "name" -> name)
    case LogSpan.Card(id, name) =>
      ujson.Obj("kind" -> "card", "id" -> id, "name" -> name)
    case LogSpan.Site(id, name) =>
      ujson.Obj("kind" -> "site", "id" -> id, "name" -> name)
    case LogSpan.Amount(value, unit) =>
      ujson.Obj("kind" -> "amount", "value" -> value, "unit" -> unit)
    case LogSpan.Cost(value) => ujson.Obj("kind" -> "cost", "value" -> value)
    case LogSpan.Dice(die, faces, names) => ujson.Obj("kind" -> "dice",
      "die" -> die, "faces" -> encoded(faces)(ujson.Str(_)),
      "names" -> encoded(names)(ujson.Str(_)))
    case LogSpan.Cards(cards) => ujson.Obj("kind" -> "cards",
      "cards" -> encoded(cards)(WorldProjectionCodec.encodeCard))

  private def decodePage(raw: ujson.Value, path: String): Result[LogPage] =
    for
      value <- obj(raw, path)
      _ <- exact(value, PageFields, path)
      gameId <- string(value, "gameId", path)
      after <- long(value, "after", path)
      next <- long(value, "nextSequence", path)
      rawEntries <- array(value, "entries", path)
      entries <- traverse(rawEntries, s"$path.entries")(decodeEntry)
    yield LogPage(gameId, after, next, entries)

  private def decodeEntry(raw: ujson.Value, path: String): Result[LogEntry] =
    for
      value <- obj(raw, path)
      _ <- exact(value, EntryFields, path)
      sequence <- long(value, "sequence", path)
      ordinal <- int(value, "ordinal", path)
      kind <- decodeKind(value, path)
      rawSpans <- array(value, "spans", path)
      spans <- traverse(rawSpans, s"$path.spans")(decodeSpan)
      section <- optionalAbsent(value, "section", path)(decodeSection)
      _ <- Either.cond(section.isEmpty || kind.depth == 1, (),
        InvalidValue(s"$path.section", "only a line can carry a section"))
      takenBack <- optionalAbsent(value, "takenBack", path)(decodeTakenBack)
    yield LogEntry(sequence, ordinal, kind, spans, section, takenBack)

  private def decodeKind(value: ujson.Obj, path: String): Result[LogKind] =
    string(value, "kind", path).flatMap(key =>
      LogKind.values.find(_.key == key).toRight(UnknownVariant(
        s"$path.kind", s"unknown log entry kind '$key'")))

  private def decodeTakenBack(raw: ujson.Value, path: String)
      : Result[LogTakenBack] =
    for
      value <- obj(raw, path)
      _ <- exact(value, TakenBackFields, path)
      group <- long(value, "group", path)
      rawHeader <- array(value, "header", path)
      header <- traverse(rawHeader, s"$path.header")(decodeSpan)
    yield LogTakenBack(group, header)

  private def decodeSection(raw: ujson.Value, path: String)
      : Result[LogSection] =
    for
      value <- obj(raw, path)
      _ <- exact(value, SectionFields, path)
      id <- long(value, "id", path)
      action <- string(value, "action", path)
    yield LogSection(id, action)

  /** Reads the discriminator, then hands the object to the one decoder that
    * knows that case's fields. */
  private def decodeSpan(raw: ujson.Value, path: String): Result[LogSpan] =
    for
      value <- obj(raw, path)
      kind <- string(value, "kind", path)
      span <- kind match
        case "text" => decodeText(value, path)
        case "player" => decodeNamed(value, path, LogSpan.Player.apply)
        case "card" => decodeNamed(value, path, LogSpan.Card.apply)
        case "site" => decodeNamed(value, path, LogSpan.Site.apply)
        case "amount" => decodeAmount(value, path)
        case "cost" => decodeCost(value, path)
        case "dice" => decodeDice(value, path)
        case "cards" => decodeCards(value, path)
        case other => Left(UnknownVariant(s"$path.kind",
          s"unknown log span kind '$other'"))
    yield span

  private def decodeText(value: ujson.Obj, path: String): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "text"), path)
      text <- string(value, "text", path)
    yield LogSpan.Text(text)

  private def decodeNamed(value: ujson.Obj, path: String,
      make: (String, String) => LogSpan): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "id", "name"), path)
      id <- string(value, "id", path)
      name <- string(value, "name", path)
    yield make(id, name)

  private def decodeAmount(value: ujson.Obj, path: String): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "value", "unit"), path)
      amount <- int(value, "value", path)
      unit <- string(value, "unit", path)
    yield LogSpan.Amount(amount, unit)

  private def decodeCost(value: ujson.Obj, path: String): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "value"), path)
      amount <- int(value, "value", path)
    yield LogSpan.Cost(amount)

  private def decodeDice(value: ujson.Obj, path: String): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "die", "faces", "names"), path)
      die <- string(value, "die", path)
      faces <- strings(value, "faces", path)
      names <- strings(value, "names", path)
    yield LogSpan.Dice(die, faces, names)

  /** `LogWords.listed` builds a `Cards` span only past the inline threshold
    * and writes a `Text` span when the viewer may identify none of them, so
    * an empty list is not a span this server can send. */
  private def decodeCards(value: ujson.Obj, path: String): Result[LogSpan] =
    for
      _ <- exact(value, Set("kind", "cards"), path)
      raw <- array(value, "cards", path)
      cards <- traverse(raw, s"$path.cards")(WorldProjectionCodec.decodeCard)
      _ <- Either.cond(cards.nonEmpty, (),
        InvalidValue(s"$path.cards", "expected at least one card"))
    yield LogSpan.Cards(cards)
