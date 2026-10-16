package oathdigital.application.gamelog

import oathdigital.protocol.projection.{LogKind, LogSection, LogSpan}

/** An entry before it has a key: what one event posts, in order. */
private[gamelog] final case class Posted(kind: LogKind, depth: Int,
    spans: Vector[LogSpan], section: Option[LogSection] = None)
private[gamelog] object Posted:
  def headline(kind: LogKind, spans: Vector[LogSpan]): Posted =
    Posted(kind, 0, spans)
  def line(kind: LogKind, spans: Vector[LogSpan]): Posted =
    Posted(kind, 1, spans)
