package oathdigital.frontend

import oathdigital.protocol.{ModifierInvocation, PreviewModifier}

private[frontend] final case class ModifierSelectionContext(
    gameId: String, playerId: String, sequence: Long, action: String)

/** Client-local draft. The server-authored candidates and preview identity are
  * part of reconciliation, so stale views cannot retain click order.
  */
private[frontend] final case class ModifierSelectionState(
    context: ModifierSelectionContext,
    candidates: Vector[PreviewModifier],
    previewFingerprint: String,
    selected: Vector[PreviewModifier]
) {
  def toggle(value: PreviewModifier): ModifierSelectionState =
    if (!candidates.contains(value)) this
    else if (selected.contains(value)) copy(selected = selected.filterNot(_ == value))
    else copy(selected = selected :+ value)

  def moveEarlier(value: PreviewModifier): ModifierSelectionState = move(value, -1)
  def moveLater(value: PreviewModifier): ModifierSelectionState = move(value, 1)
  def keyboard(value: PreviewModifier, key: String): ModifierSelectionState = key match {
    case "Enter" | " " | "Spacebar" => toggle(value)
    case "ArrowUp" => moveEarlier(value)
    case "ArrowDown" => moveLater(value)
    case _ => this
  }
  def ordinal(value: PreviewModifier): Option[Int] = selected.indexOf(value) match {
    case -1 => None
    case index => Some(index + 1)
  }
  def invocations: Vector[ModifierInvocation] = selected.map { value =>
    val parts = value.sourceKey.split(":", 4).toVector
    parts match {
      case Vector("adviser", _, _, id) =>
        ModifierInvocation("adviser", id, None, value.handlerId)
      case _ if value.sourceKey.startsWith("site-card:") =>
        typed(value.sourceKey.stripPrefix("site-card:"), Vector("denizen", "vision"))
          .map { case (site, id) => ModifierInvocation("site-card", id,
            Some(site), value.handlerId) }.getOrElse(unsupported(value.sourceKey))
      case _ if value.sourceKey.startsWith("site:") =>
        ModifierInvocation("site", value.sourceKey.stripPrefix("site:"), None,
          value.handlerId)
      case _ if value.sourceKey.startsWith("relic:") =>
        val fields = value.sourceKey.stripPrefix("relic:").split(":", 2)
        ModifierInvocation("relic", fields.last, None, value.handlerId)
      case _ if value.sourceKey.startsWith("edifice:") =>
        typed(value.sourceKey.stripPrefix("edifice:"), Vector("edifice"))
          .orElse(splitLast(value.sourceKey.stripPrefix("edifice:")))
          .map { case (site, id) => ModifierInvocation("edifice", id,
            Some(site), value.handlerId) }.getOrElse(unsupported(value.sourceKey))
      case _ if value.sourceKey.startsWith("banner:") => ModifierInvocation(
        "banner", value.sourceKey.stripPrefix("banner:"), None, value.handlerId)
      case Vector("foundation", id) =>
        ModifierInvocation("foundation", id, None, value.handlerId)
      case _ if value.sourceKey.startsWith("game:") => ModifierInvocation(
        "game", value.sourceKey.stripPrefix("game:"), None, value.handlerId)
      case _ if value.sourceKey.startsWith("legacy:") =>
        val fields = value.sourceKey.stripPrefix("legacy:").split(":", 2)
        ModifierInvocation("legacy", fields.last, Some(fields.head), value.handlerId)
      case _ => unsupported(value.sourceKey)
    }
  }

  private def typed(value: String, kinds: Vector[String]) = kinds.iterator
    .flatMap { kind =>
      val marker = s":$kind:"
      val at = value.indexOf(marker)
      Option.when(at >= 0)(value.take(at) -> value.drop(at + marker.length))
    }.toVector.headOption

  private def splitLast(value: String) = Option(value.lastIndexOf(':'))
    .filter(_ >= 0).map(at => value.take(at) -> value.drop(at + 1))

  private def unsupported(source: String): Nothing =
    throw new IllegalStateException(s"unsupported modifier source $source")

  private def move(value: PreviewModifier, delta: Int) = {
    val index = selected.indexOf(value)
    val next = index + delta
    if (index < 0 || next < 0 || next >= selected.size) this
    else copy(selected = selected.updated(index, selected(next)).updated(next, value))
  }
}

private[frontend] object ModifierSelectionState {
  def reconcile(previous: Option[ModifierSelectionState],
      context: ModifierSelectionContext, candidates: Vector[PreviewModifier],
      previewFingerprint: String): ModifierSelectionState = previous match {
    case Some(value) if value.context == context && value.candidates == candidates &&
        value.previewFingerprint == previewFingerprint => value
    case _ => ModifierSelectionState(context, candidates, previewFingerprint, Vector.empty)
  }
}
