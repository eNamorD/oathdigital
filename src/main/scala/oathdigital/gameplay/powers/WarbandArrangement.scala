package oathdigital.gameplay.powers

import oathdigital.model._

/** A player's warbands arranged again over their board and a set of sites
  * (P6): Warning Signals over the sites it defends, Messenger over the sites
  * its user rules. One exact distribution keeps the total and leaves each
  * site at least one warband.
  */
object WarbandArrangement:
  /** The player's board, and each of `sites` that holds warbands, with what
    * each holds now, in the order given. */
  def holdings(ready: ReadyGame, user: PlayerId, sites: Vector[SiteId])
      : (Int, Vector[(SiteId, Int)]) =
    val current = ready.game.current
    val board = current.players.find(_.player == user).fold(0)(_.board.warbands)
    (board, sites.flatMap(site => current.map.sites(site).forces match {
      case SiteForces.Occupied(_, count) if count > 0 => Some(site -> count)
      case _ => None
    }))

  /** Whether a warband can move: there is a site, and a warband beyond the
    * one each site keeps. */
  def movable(board: Int, sites: Vector[(SiteId, Int)]): Boolean =
    sites.nonEmpty && board + sites.map(_._2).sum > sites.size

  /** The exact distribution over the board and `sites`, each slot opening on
    * what it holds now. */
  def query(user: PlayerId, board: Int, sites: Vector[(SiteId, Int)],
      heading: String): DecisionQuery =
    val total = board + sites.map(_._2).sum
    val room = total - (sites.size - 1)
    val slots = DistributeSlot(DecisionOptionRef.Player(user), 0, total,
      Some(board)) +: sites.map { case (site, count) => DistributeSlot(
        DecisionOptionRef.Site(site), 1, room, Some(count)) }
    DecisionQuery.Distribute.exactly(slots, total, Some(heading),
      "Move warbands")

  /** The moves from what each of `sites` holds now to what the answer gives
    * it: every site that shrinks sends its extras to the board first, so the
    * board always holds what the sites that grow are given.
    */
  def moves(ready: ReadyGame, user: PlayerId, sites: Vector[SiteId],
      rows: Vector[DistributeAmount])
      : Either[OathViolation, Vector[CoreOperation]] =
    PlayerFacts.forceKind(ready, user).map { kind =>
      val (_, held) = holdings(ready, user, sites)
      val changes = held.flatMap { case (site, now) => rows.collectFirst {
        case DistributeAmount(DecisionOptionRef.Site(`site`), wanted) =>
          (site, wanted - now)
      }}
      def move(site: SiteId, count: Int, out: Boolean): CoreOperation =
        Move(Piece.Warbands(kind, count),
          PositionedLocation(if out then Location.Site(site)
            else Location.PlayArea(user)),
          PositionedLocation(if out then Location.PlayArea(user)
            else Location.Site(site)))
      changes.collect { case (site, delta) if delta < 0 =>
        move(site, -delta, out = true) } ++
        changes.collect { case (site, delta) if delta > 0 =>
          move(site, delta, out = false) }
    }
