package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** The kill of a defeated side's whole force, after the Campaign's losses
  * (R4, from Sticky Fire). Sticky Fire kills its enemy's force this way when
  * its user wins, and Shield Wall kills its own user's when they lose.
  *
  *  - A defeated attacker loses every warband on its board, committed to the
  *    Campaign or not, and every warband of theirs at a queued origin, whose
  *    warbands joined the force (catalog batch 8, slice 13).
  *  - A defeated Conquest defender keeps none of the warbands the victory
  *    returned to its board.
  *  - A defeated Raid defender loses every warband left on its board.
  *
  * Bandits lose nothing more: every targeted bandit already died. Each kill
  * reads the board when it runs, so when Sticky Fire and Shield Wall both
  * kill the same force, the second finds nothing left to kill.
  */
private[campaign] object KillWholeForce:
  /** A player defender's board before the losses run, and what a Conquest
    * the attacker won will return to it. */
  final case class Returns(board: Int, returned: Int)

  /** Read when the losses window is folded, which is before they run. */
  def returns(ready: ReadyGame, result: CampaignResult): Returns =
    (result.kind, result.defender) match
      case (CampaignKind.Conquest, CampaignDefender.Player(defender)) =>
        val total = result.targetSites.flatMap(ready.game.current.map.sites.get)
          .map(_.forces).collect {
            case SiteForces.Occupied(_, count) => count }.sum
        Returns(board(ready, defender), total - total / 2)
      case _ => Returns(0, 0)

  /** The defeated defender's whole force, as the losses left it. A Conquest
    * defender loses what the losses returned and is still on its board: the
    * smaller of the returned amount and how much the board has grown. */
  def ofDefender(ready: ReadyGame, result: CampaignResult, returns: Returns)
      : Either[OathViolation, Vector[CoreOperation]] = result.defender match
    case CampaignDefender.Player(defender) => result.kind match
      case CampaignKind.Conquest => killed(ready, defender,
        math.min(returns.returned, board(ready, defender) - returns.board))
      case CampaignKind.Raid => killed(ready, defender, board(ready, defender))
    case CampaignDefender.Bandits => Right(Vector.empty)

  /** The defeated attacker's whole board, and their warbands at a queued
    * origin. */
  def ofAttacker(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, Vector[CoreOperation]] =
    killed(ready, result.attacker, board(ready, result.attacker)).flatMap(
      onBoard => atOrigin(ready, result).map(onBoard ++ _))

  /** The attacker's warbands at the origin, as the losses left them. */
  private def atOrigin(ready: ReadyGame, result: CampaignResult)
      : Either[OathViolation, Vector[CoreOperation]] =
    result.originForce.fold(Right(Vector.empty))(origin =>
      PlayerFacts.forceKind(ready, result.attacker).map(kind =>
        ready.game.current.map.sites.get(origin.site).map(_.forces).toVector
          .collect { case SiteForces.Occupied(`kind`, count) if count > 0 =>
            Kill(Piece.Warbands(kind, count),
              PositionedLocation(Location.Site(origin.site))) }))

  private def board(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.board.warbands)

  private def killed(ready: ReadyGame, player: PlayerId, amount: Int)
      : Either[OathViolation, Vector[CoreOperation]] =
    if amount <= 0 then Right(Vector.empty)
    else PlayerFacts.forceKind(ready, player).map(kind => Vector(Kill(
      Piece.Warbands(kind, amount),
      PositionedLocation(Location.PlayArea(player)))))
