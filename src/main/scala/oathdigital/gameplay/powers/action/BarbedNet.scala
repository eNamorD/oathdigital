package oathdigital.gameplay.powers.action

import oathdigital.catalog.holding.BarbedNetCard
import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Barbed Net (relic R36), ACTION: burn 3 secrets, then take a relic from
  * the player's site. The player may keep it facedown; the existing minor
  * action that reveals an owned relic covers turning it up later.
  *
  * The peek, the choice, the take and their lines are [[SiteRelicTake]]'s,
  * at the player's pawn site. With no relic there the cost stays paid and
  * nothing else happens.
  */
case object BarbedNet extends PaidAction(BarbedNetCard.power):
  val decisionId: String = "power.barbed-net.relic"
  private val take = new SiteRelicTake(id, decisionId,
    "Barbed Net: take a relic from your site", PowerAccess.pawnSite)
  /** "{Red} peeked at the relics at {site}: {relics}." */
  val peeked: NoteKey = take.peeked
  /** "{Red} took {relic} facedown from {site}." */
  val took: NoteKey = take.took
  /** "{site} held no relic." */
  val bare: NoteKey = take.bare
  override def noteKeys: Vector[NoteKey] = take.keys

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    Right(Sequence(take.steps(player, source)))
