package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** Reads and writes shared by the movement relics (Whistle, Brass Horse,
  * Magic Carpet). A pawn relocation that is not Travel is a plain `Move`, and
  * nothing here runs a Travel window.
  */
object PawnMoves {
  def pawnSite(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, SiteId] =
    PowerAccess.pawnSite(ready, player).toRight(OathViolation.InvalidEventOrder(
      s"${player.value} has no pawn on the map"))

  /** Other players whose pawn is at a site other than `player`'s. */
  def atOtherSites(ready: ReadyGame, player: PlayerId): Vector[PlayerId] =
    PowerAccess.pawnSite(ready, player).toVector.flatMap(here =>
      ready.game.current.players.collect {
        case other if other.player != player &&
            other.pawnSite.exists(_ != here) => other.player
      })

  def siteChoice(decisionId: String, owner: PlayerId, sites: Vector[SiteId],
      heading: String): Decide = Decide(decisionId, owner,
    DecisionQuery.ChooseOne(sites.map(site =>
      DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some(heading)))

  /** The `Move` that puts `player`'s pawn at `to`. Nothing when the pawn is
    * already there, because a `Move` must change location.
    */
  def relocate(ready: ReadyGame, player: PlayerId, to: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] =
    pawnSite(ready, player).map(from =>
      if (from == to) Vector.empty
      else Vector(Move(Piece.Pawn(player),
        PositionedLocation(Location.Site(from)),
        PositionedLocation(Location.Site(to)))))

  def chosenSite(pending: PendingTree, decisionId: String)
      : Either[OathViolation, SiteId] = PowerAnswers.one(pending, decisionId)
    .collect { case DecisionOptionRef.Site(site) => site }
    .toRight(PowerAnswers.missing(decisionId))
}
