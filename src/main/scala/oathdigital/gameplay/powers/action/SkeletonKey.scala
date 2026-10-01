package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.model._

/** Skeleton Key (relic R13), ACTION: place 1 secret on this relic and burn 1
  * secret, then, if your pawn is at a Hinterland site, take a relic from
  * your site.
  *
  * The peek, the choice, the take and their lines are Barbed Net's
  * ([[SiteRelicTake]]), reached only from a Hinterland site. Away from the
  * Hinterland, or with no relic at the site, the cost stays paid and nothing
  * else happens. Away from the Hinterland its own line says so.
  */
case object SkeletonKey extends PaidAction("relic.skeleton-key",
    Cost(secret = 1, secretBurnt = 1)):
  val decisionId: String = "power.skeleton-key.relic"
  /** "{Red} was not at a Hinterland site." */
  val away: NoteKey = NoteKey("used.away", Vector(NotePart.Arg(0),
    NotePart.Text(" was not at a Hinterland site.")))
  private val take = new SiteRelicTake(id, decisionId,
    "Skeleton Key: take a relic from your site", hinterland)
  override def noteKeys: Vector[NoteKey] = take.keys :+ away

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(
    Note(id, states =>
      if hinterland(states.now, player).isDefined then None
      else PowerSourceRef.of(source).map(away(_, NoteArg.Player(player)))) +:
      take.steps(player, source)))

  /** The player's pawn site, when it is in the Hinterland. */
  private def hinterland(ready: ReadyGame, player: PlayerId): Option[SiteId] =
    PowerAccess.pawnSite(ready, player).filter(site =>
      ready.game.current.map.regionOf(site).contains(Region.Hinterland))
