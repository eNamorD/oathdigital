package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Whistle (relic R08), ACTION: place 1 secret on this relic, take the pawn
  * of another player who is at a different site, place it at your site, and
  * move the secret from the Whistle to that player's board.
  *
  * The decision is a live `Branch` that is empty when no player qualifies. The
  * cost is paid whatever happens, so with nobody to pull the secret stays on
  * the Whistle, and the empty-card rule keeps it unusable until it is gone.
  */
case object Whistle extends PaidAction("relic.whistle", Cost(secret = 1)) {
  val decisionId: String = "power.whistle.target"

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match {
    case DecisionOptionRef.Relic(whistle) => Right(Sequence(Vector[Operation](
      Branch((state, _) => ask(state, player)),
      BuildOps((state, pending) => pull(state, player, whistle, pending)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a relic source"))
  }

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] = {
    val targets = PawnMoves.atOtherSites(ready, player)
    if (targets.isEmpty) Vector.empty
    else Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
      targets.map(target => DecisionOption.Player(
        DecisionOptionRef.Player(target))),
      heading = Some("Whistle: choose the player whose pawn you pull to " +
        "your site"))))
  }

  private def pull(ready: ReadyGame, player: PlayerId, whistle: RelicId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    if (PawnMoves.atOtherSites(ready, player).isEmpty) Right(Vector.empty)
    else for {
      here <- PawnMoves.pawnSite(ready, player)
      target <- PowerAnswers.one(pending, decisionId).collect {
        case DecisionOptionRef.Player(id) => id
      }.toRight(PowerAnswers.missing(decisionId))
      from <- PawnMoves.pawnSite(ready, target)
    } yield Vector[CoreOperation](
      Move(Piece.Pawn(target), PositionedLocation(Location.Site(from)),
        PositionedLocation(Location.Site(here))),
      Move(Piece.Secrets(1), PositionedLocation(Location.OnCard(whistle)),
        PositionedLocation(Location.PlayArea(target))))
}
