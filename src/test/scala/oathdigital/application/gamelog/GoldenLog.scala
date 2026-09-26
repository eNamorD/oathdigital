package oathdigital.application.gamelog

/** An exact, readable rendering of log entries for golden files: one line
  * per entry, `sequence.ordinal kind depth` then every span, a typed span in
  * brackets with its identity. Nothing a `LogEntry` holds is lost. */
object GoldenLog:
  def render(entries: Vector[LogEntry]): String =
    entries.map(entry => s"${entry.sequence}.${entry.ordinal} " +
      s"${entry.kind.key} ${entry.depth} | " + entry.spans.map(span).mkString)
      .mkString("", "\n", "\n")

  private def span(value: LogSpan): String = value match
    case LogSpan.Text(text) => text
    case LogSpan.Player(id, name) => s"[player:$id|$name]"
    case LogSpan.Card(id, name) => s"[card:$id|$name]"
    case LogSpan.Site(id, name) => s"[site:$id|$name]"
    case LogSpan.Amount(count, unit) => s"[amount:$count $unit]"
    case LogSpan.Cost(count, unit) => s"[cost:$count $unit]"
    case dice: LogSpan.Dice =>
      s"[dice:${dice.die} ${dice.faces.mkString(" ")}|${dice.text}]"
