package oathdigital.application.gamelog

import oathdigital.application.{GameHistory, GamePresentationProjector}
import oathdigital.catalog.ExecutableCatalog
import oathdigital.model.PlayerId
import oathdigital.protocol.projection.{LogEntryWire, LogPageWire, LogSpanWire}

/** The log page one seat reads: the whole prefix formatted for that seat,
  * then every entry at or after `after` (spec, "Routes" and "Cost"). */
private[application] final class GameLogProjector(catalog: ExecutableCatalog,
    presentation: GamePresentationProjector):
  private val formatter = new GameLogFormatter(catalog, presentation)

  def page(gameId: String, history: GameHistory, after: Long,
      viewer: PlayerId): Option[LogPageWire] =
    Option.when(after >= 0 && after <= history.nextSequence)(LogPageWire(
      gameId, after, history.nextSequence,
      formatter.format(history.steps, Some(viewer))
        .filter(_.sequence >= after).map(GameLogProjector.wire)))

private[application] object GameLogProjector:
  def wire(entry: LogEntry): LogEntryWire = LogEntryWire(entry.sequence,
    entry.ordinal, entry.kind.key, entry.depth, entry.spans.map(span))

  def span(value: LogSpan): LogSpanWire = value match
    case LogSpan.Text(text) => LogSpanWire("text", text)
    case LogSpan.Player(id, name) => LogSpanWire("player", name, id = Some(id))
    case LogSpan.Card(id, name) => LogSpanWire("card", name, id = Some(id))
    case LogSpan.Site(id, name) => LogSpanWire("site", name, id = Some(id))
    case amount @ LogSpan.Amount(count, unit) =>
      LogSpanWire("amount", amount.text, value = Some(count), unit = Some(unit))
    case cost @ LogSpan.Cost(count, unit) =>
      LogSpanWire("cost", cost.text, value = Some(count), unit = Some(unit))
