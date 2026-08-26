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
    val parts = value.sourceKey.split(':').toVector
    parts match {
      case Vector("site", id) => ModifierInvocation("site", id, None, value.handlerId)
      case Vector("site-card", site, _, id) =>
        ModifierInvocation("site-card", id, Some(site), value.handlerId)
      case Vector("adviser", _, _, id) =>
        ModifierInvocation("adviser", id, None, value.handlerId)
      case Vector("relic", _, id) => ModifierInvocation("relic", id, None, value.handlerId)
      case Vector("edifice", site, id) =>
        ModifierInvocation("edifice", id, Some(site), value.handlerId)
      case Vector("banner", id) => ModifierInvocation("banner", id, None, value.handlerId)
      case Vector("foundation", id) =>
        ModifierInvocation("foundation", id, None, value.handlerId)
      case Vector("legacy", lineage, id) =>
        ModifierInvocation("legacy", id, Some(lineage), value.handlerId)
      case _ => throw new IllegalStateException(s"unsupported modifier source ${value.sourceKey}")
    }
  }

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
