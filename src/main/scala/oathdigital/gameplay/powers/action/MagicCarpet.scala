package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Magic Carpet (relic R39), ACTION, no cost: place your pawn at any site,
  * then discard the Carpet or give it to a player whose pawn is at a site
  * different from your new one.
  *
  * The first decision names the site, and the move is skipped when it is the
  * current site. The second is a live `Branch` after the move, so it reads the
  * new pawn site. It is not asked when nobody is eligible: the Carpet is then
  * discarded.
  */
case object MagicCarpet extends PaidAction("relic.magic-carpet", Cost.free) {
  val siteDecisionId: String = "power.magic-carpet.site"
  val fateDecisionId: String = "power.magic-carpet.fate"
  val discard: DecisionOptionRef.Button = DecisionOptionRef.Button("discard")

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match {
    case DecisionOptionRef.Relic(carpet) => Right(Sequence(Vector[Operation](
      PawnMoves.siteChoice(siteDecisionId, player,
        ready.game.current.map.inPlay,
        "Magic Carpet: choose the site to place your pawn at"),
      BuildOps((state, pending) => PawnMoves.chosenSite(pending,
        siteDecisionId).flatMap(PawnMoves.relocate(state, player, _))),
      Branch((state, _) => ask(state, player)),
      BuildOps((state, pending) => settle(state, player, carpet, pending)))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a relic source"))
  }

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] = {
    val takers = PawnMoves.atOtherSites(ready, player)
    if (takers.isEmpty) Vector.empty
    else Vector(Decide(fateDecisionId, player, DecisionQuery.ChooseOne(
      DecisionOption.Button(discard, "Discard Magic Carpet") +:
        takers.map(taker => DecisionOption.Player(
          DecisionOptionRef.Player(taker))),
      heading = Some("Magic Carpet: discard it, or give it to a player at " +
        "another site"))))
  }

  private def settle(ready: ReadyGame, player: PlayerId, carpet: RelicId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    for {
      held <- PlayerFacts.player(ready, player)
      relic <- held.relics.find(_.id == carpet).toRight(
        OathViolation.InvalidEventOrder(
          s"${carpet.value} is not held by ${player.value}"))
    } yield PowerAnswers.one(pending, fateDecisionId) match {
      case Some(DecisionOptionRef.Player(taker)) => Vector[CoreOperation](
        Give(Piece.Card(carpet), player, Location.PlayArea(player),
          Location.PlayArea(taker)))
      case _ => Vector[CoreOperation](Discard.Relic(carpet,
        PositionedLocation(Location.PlayArea(player)), relic.tokens.secrets,
        player))
    }
}
