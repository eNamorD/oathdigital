package oathdigital.gameplay.operations

import oathdigital.model._

/** Restriction icons govern how a card is played, moved and swapped
  * (catalog batch 4 design, "The restriction-icon rule"). A `Move` that
  * takes a card out of a site or a play area to another location may not
  * land an adviser-only card faceup at a site, or a site-only card faceup in
  * a player's advisers. A `Swap`, a `Take` and a `Give` flatten to such
  * moves, so one restriction judges them all.
  *
  * A card lands as the move's `resultingOrientation` says, or else as it
  * shows where it lies. A facedown adviser has no restrictions, so a card
  * landing facedown passes. Only denizens print these icons: edifices are
  * never advisers, and relics and Visions print none.
  *
  * Card play from a hand is not judged here, since `CardPlay` checks the
  * icons itself. Nor is a move inside one play area, which only turns a card
  * over.
  */
final case class RestrictionIcons(adviserOnly: Set[CardId],
    siteOnly: Set[CardId]) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    Operation.flatten(operation).iterator.collect {
      case move @ Move(Piece.Card(card), from, to, _, _)
          if from.location != to.location &&
            OperationRestrictions.inPlay(from.location) => (move, card)
    }.flatMap((move, card) => refusal(ready, move, card)).nextOption()

  private def refusal(ready: ReadyGame, move: Move, card: CardId)
      : Option[OperationReason] = move.to.location match
    case _: Location.Site
        if adviserOnly(card) && landsFaceUp(ready, move, card) =>
      Some(refused(s"${card.value} is adviser-only and cannot be moved " +
        "to a site"))
    case _: Location.PlayArea
        if siteOnly(card) && landsFaceUp(ready, move, card) =>
      Some(refused(s"${card.value} is site-only and cannot be a faceup " +
        "adviser"))
    case _ => None

  private def landsFaceUp(ready: ReadyGame, move: Move, card: CardId)
      : Boolean =
    move.resultingOrientation.orElse(shown(ready, card, move.from.location))
      .forall(_ == Orientation.FaceUp)

  /** The orientation `card` shows where it lies, if it lies there. */
  private def shown(ready: ReadyGame, card: CardId, from: Location)
      : Option[Orientation] =
    val current = ready.game.current
    from match
      case Location.PlayArea(owner) => current.players
        .find(_.player == owner).flatMap(_.advisers.collectFirst {
          case DenizenState(`card`, orientation, _) => orientation })
      case Location.Site(site) => current.map.sites.get(site)
        .flatMap(_.denizens.collectFirst {
          case DenizenState(`card`, orientation, _) => orientation })
      case _ => None

  private def refused(detail: String): OperationReason =
    OperationReason("restriction-icon", detail, OperationReasonKind.Impossible)
