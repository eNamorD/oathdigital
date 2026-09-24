package oathdigital.gameplay.operations

import oathdigital.model._

/** `Flip`, `Reveal` and `Peek`: what a card shows and who knows it. */
private[operations] object CardFaceOperations {
  import OperationError._
  import OperationStateAdapter.{card, isDiscardLook, playerState}
  import OperationStateWrites.updateCardState

  def flipViolation(
      ready: ReadyGame,
      id: CardId,
      at: Location,
      orientation: Orientation
  ): Vector[OperationError] = card(ready, id, at) match {
    case Left(error) => Vector(error)
    case Right(located) => located.state match {
      case Some(_: DenizenState) | Some(_: VisionState) |
          Some(_: RelicState) => Vector.empty
      // Looking at a discarded card: it has no orientation state (a discard is
      // always facedown), so revealing it changes nothing.
      case None if isDiscardLook(at, orientation) =>
        Vector.empty
      case _ => Vector(UnsupportedOrientation(id, at))
    }
  }

  def peekViolation(
      ready: ReadyGame,
      viewer: PlayerId,
      id: CardId,
      at: Location
  ): Vector[OperationError] = playerState(ready, viewer) match {
    case Left(error) => Vector(error)
    case Right(_) =>
      val kindViolation: Vector[OperationError] =
        if (id.isInstanceOf[WorldCardId] || id.isInstanceOf[RelicId])
          Vector.empty
        else Vector(IncompatibleLocation(Piece.Card(id), at))
      kindViolation ++ (card(ready, id, at) match {
        case Left(error) => Vector(error)
        case Right(_) => Vector.empty
      })
  }

  def flipCard(ready: ReadyGame, id: CardId, at: Location,
      orientation: Orientation): Either[OperationError, ReadyGame] = for {
    located <- card(ready, id, at)
    updated <- located.state match {
      case Some(_: DenizenState) | Some(_: VisionState) | Some(_: RelicState) =>
        updateCardState(ready, id) {
          case value: DenizenState => value.copy(orientation = orientation)
          case value: VisionState => value.copy(orientation = orientation)
          case value: RelicState => value.copy(orientation = orientation)
          case value => value
        }
      case None if isDiscardLook(at, orientation) => Right(ready)
      case _ => Left(UnsupportedOrientation(id, at))
    }
  } yield updated

  def peek(ready: ReadyGame, viewer: PlayerId, id: CardId,
      at: Location): Either[OperationError, ReadyGame] = for {
    _ <- playerState(ready, viewer)
    _ <- Either.cond(id.isInstanceOf[WorldCardId] || id.isInstanceOf[RelicId],
      (), IncompatibleLocation(Piece.Card(id), at))
    located <- card(ready, id, at)
  } yield located.location.container match {
    case CardContainer.Site(site, SiteCardArea.Relics) =>
      recordSiteRelicKnowledge(ready, viewer, site,
        id.asInstanceOf[RelicId])
    case CardContainer.AtlasSite(_, site, SiteCardArea.Relics) =>
      recordSiteRelicKnowledge(ready, viewer, site,
        id.asInstanceOf[RelicId])
    case _ if id.isInstanceOf[RelicId] => ready.copy(knowledge =
      ready.knowledge.copy(heldRelics = ready.knowledge.heldRelics.updated(viewer,
        appendDistinct(ready.knowledge.heldRelics.getOrElse(viewer, Vector.empty),
          id.asInstanceOf[RelicId]))))
    case _ if id.isInstanceOf[WorldCardId] => ready.copy(knowledge =
      ready.knowledge.copy(advisers = ready.knowledge.advisers.updated(viewer,
        appendDistinct(ready.knowledge.advisers.getOrElse(viewer, Vector.empty),
          id.asInstanceOf[WorldCardId]))))
    case _ => ready
  }

  private def appendDistinct[A](values: Vector[A], value: A): Vector[A] =
    if (values.contains(value)) values else values :+ value

  private def recordSiteRelicKnowledge(
      ready: ReadyGame,
      viewer: PlayerId,
      site: SiteId,
      relic: RelicId
  ): ReadyGame = {
    val sites = ready.knowledge.siteRelics.getOrElse(viewer, Map.empty)
    ready.copy(knowledge = ready.knowledge.copy(siteRelics =
      ready.knowledge.siteRelics.updated(viewer, sites.updated(site,
        appendDistinct(sites.getOrElse(site, Vector.empty), relic)))))
  }
}
