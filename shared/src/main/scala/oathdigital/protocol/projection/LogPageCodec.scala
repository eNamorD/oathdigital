package oathdigital.protocol.projection

import scala.util.control.NonFatal

import oathdigital.protocol.ProtocolDecodeFailure
import oathdigital.protocol.ProtocolDecodeFailure.MalformedJson
import ProjectionCodecSupport._

/** The log page's wire form. Kinds are not checked against a list: a client
  * shows an unknown span kind as its text, so a newer server's span does
  * not break an older page. */
object LogPageCodec:
  private val PageFields = Set("gameId", "after", "nextSequence", "entries")
  private val EntryFields = Set("sequence", "ordinal", "kind", "depth", "spans")
  private val SpanFields = Set("kind", "text", "id", "value", "unit")

  def encode(value: LogPageWire): String = ujson.write(ujson.Obj(
    "gameId" -> value.gameId,
    "after" -> ujson.Num(value.after.toDouble),
    "nextSequence" -> ujson.Num(value.nextSequence.toDouble),
    "entries" -> encoded(value.entries)(encodeEntry)))

  def decode(json: String): Either[ProtocolDecodeFailure, LogPageWire] =
    try decodePage(ujson.read(json), "$")
    catch { case NonFatal(error) => Left(MalformedJson("$",
      Option(error.getMessage).getOrElse("malformed JSON"))) }

  private def encodeEntry(entry: LogEntryWire): ujson.Value = ujson.Obj(
    "sequence" -> ujson.Num(entry.sequence.toDouble),
    "ordinal" -> entry.ordinal, "kind" -> entry.kind, "depth" -> entry.depth,
    "spans" -> encoded(entry.spans)(encodeSpan))

  private def encodeSpan(span: LogSpanWire): ujson.Value = ujson.Obj.from(
    Vector[(String, ujson.Value)]("kind" -> ujson.Str(span.kind),
      "text" -> ujson.Str(span.text)) ++
      span.id.map(id => "id" -> (ujson.Str(id): ujson.Value)) ++
      span.value.map(amount => "value" -> (ujson.Num(amount): ujson.Value)) ++
      span.unit.map(unit => "unit" -> (ujson.Str(unit): ujson.Value)))

  private def decodePage(raw: ujson.Value, path: String): Result[LogPageWire] =
    for
      value <- obj(raw, path)
      _ <- exact(value, PageFields, path)
      gameId <- string(value, "gameId", path)
      after <- long(value, "after", path)
      next <- long(value, "nextSequence", path)
      rawEntries <- array(value, "entries", path)
      entries <- traverse(rawEntries, s"$path.entries")(decodeEntry)
    yield LogPageWire(gameId, after, next, entries)

  private def decodeEntry(raw: ujson.Value, path: String): Result[LogEntryWire] =
    for
      value <- obj(raw, path)
      _ <- exact(value, EntryFields, path)
      sequence <- long(value, "sequence", path)
      ordinal <- int(value, "ordinal", path)
      kind <- string(value, "kind", path)
      depth <- int(value, "depth", path)
      rawSpans <- array(value, "spans", path)
      spans <- traverse(rawSpans, s"$path.spans")(decodeSpan)
    yield LogEntryWire(sequence, ordinal, kind, depth, spans)

  private def decodeSpan(raw: ujson.Value, path: String): Result[LogSpanWire] =
    for
      value <- obj(raw, path)
      _ <- exact(value, SpanFields, path)
      kind <- string(value, "kind", path)
      text <- string(value, "text", path)
      id <- optionalAbsent(value, "id", path)(string)
      amount <- optionalAbsent(value, "value", path)(int)
      unit <- optionalAbsent(value, "unit", path)(string)
    yield LogSpanWire(kind, text, id, amount, unit)
