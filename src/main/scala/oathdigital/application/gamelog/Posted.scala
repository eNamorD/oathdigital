package oathdigital.application.gamelog

import oathdigital.protocol.projection.{LogKind, LogSection, LogSpan}

/** An entry before it has a key: what one event posts, in order. Its depth
  * is its kind's, so there is one constructor and no way to post a headline
  * as a line. */
private[gamelog] final case class Posted(kind: LogKind,
    spans: Vector[LogSpan], section: Option[LogSection] = None)
