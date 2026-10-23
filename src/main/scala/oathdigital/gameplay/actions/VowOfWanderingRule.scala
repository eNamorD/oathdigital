package oathdigital.gameplay.actions

import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Vow of Wandering's ban on placing warbands on sites (card 247). The
  * power that enforces it reads this, and so does Move Warbands, which asks
  * before it offers the move to a site. It sits outside `powers` so that a
  * procedure may read it.
  */
object VowOfWanderingRule:
  val cardId: DenizenId = DenizenId("247")

  /** Why a holder may not move warbands to a site. */
  val Reason: String = "Vow of Wandering forbids placing warbands on sites"

  /** Whether `player` holds the card faceup. */
  def holds(ready: ReadyGame, player: PlayerId): Boolean =
    PowerAccess.faceupAdviserHolder(ready, cardId).contains(player)
