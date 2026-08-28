package oathdigital.frontend

import oathdigital.protocol.{CardRef, GameIntent, Placement, WorldCard}

private[frontend] final case class FacedownAdviserDraft(
    context: BoardSelectionContext,
    advisers: Vector[MinorAdviser],
    selectedCardId: Option[String]) {
  def selected: Option[MinorAdviser] = advisers.find(adviser =>
    selectedCardId.contains(adviser.card.cardId))
  def choose(cardId: String): FacedownAdviserDraft =
    if (advisers.exists(_.card.cardId == cardId)) copy(selectedCardId = Some(cardId))
    else this
  def command(placement: MinorAdviserPlacement): Option[GameIntent] = selected.flatMap {
    adviser =>
      val card = WorldCard(adviser.card.cardKind, adviser.card.cardId)
      placement.kind match {
        case "discard" => Some(GameIntent.ResolveFacedownAdviser(card, None))
        case "play-adviser" => Some(GameIntent.ResolveFacedownAdviser(card,
          Some(Placement("adviser-face-up", None))))
        case "play-site" => Some(GameIntent.ResolveFacedownAdviser(card,
          Some(Placement("site", placement.replacement.map(value =>
            CardRef(value.cardKind, value.cardId))))))
        case _ => None
      }
  }
}

private[frontend] object FacedownAdviserDraft {
  def initial(context: BoardSelectionContext,
      minor: MinorActionsState): Option[FacedownAdviserDraft] = {
    val legal = minor.advisers.filter(_.placements.nonEmpty)
    Option.when(legal.nonEmpty)(FacedownAdviserDraft(context, legal,
      Option.when(legal.size == 1)(legal.head.card.cardId)))
  }
  def reconcile(previous: Option[FacedownAdviserDraft],
      context: BoardSelectionContext, minor: Option[MinorActionsState]) =
    previous.filter(_.context == context).flatMap { prior =>
      minor.flatMap(initial(context, _)).map { fresh =>
        prior.selectedCardId.filter(id => fresh.advisers.exists(
          _.card.cardId == id)).fold(fresh)(id => fresh.copy(selectedCardId = Some(id)))
      }
    }
}
