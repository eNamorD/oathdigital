package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.powerresolver.{ContributingPower, PowerCtx}
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Billowing Fog's and Traveling Doctor's shared rule: "If you're defeated,
  * kill no warbands in your force. Ignore powers that kill all of your
  * force." It acts only when its user is a player and was defeated.
  *
  *  - A defeated attacker still loses its skulls and its sacrifice, but not
  *    half of what survived.
  *  - A defeated Conquest defender's target warbands all die and all return
  *    to its board, not half of them.
  *  - A defeated Raid defender loses no warband from its board.
  *
  * The two powers that kill all of a force, Shield Wall and Sticky Fire, are
  * ignored at the losses through the collector's named ignore, so neither
  * asks nor kills. Fog and Doctor fold at priority 1: after those two (0),
  * and before Hospital (2) and Cursed Cauldron (3), which count what still
  * dies.
  *
  * The line, "{Blue}'s force was spared.", names the user, since either side
  * may use it. It is written when the defeat would have killed at least one
  * of their warbands for good.
  */
private[campaign] object SparedForce:
  val Priority: Int = 1

  /** The keys this object owns, which no one power owns. A power that
    * writes one includes this set. */
  val notes: NoteSet = NoteSet.unowned("SparedForce")
  /** "{Blue}'s force was spared." */
  val spared: NoteKey = notes.key("spared", Vector(NotePart.Arg(0),
    NotePart.Text("'s force was spared.")))

  /** The powers that kill all of a defeated force, by id, so that no plan
    * object depends on another at initialisation. */
  private val killAll: Set[PowerId] =
    Set(ShieldWallCard.power.id, StickyFireCard.power.id)

  /** Whether `plan`, chosen by a player who was defeated, ignores `other`:
    * only at the losses, and only a power that kills all of a force. */
  def ignores(ctx: PowerCtx, plan: BattlePlan, other: ContributingPower)
      : Boolean =
    ctx.window == PowerWindow.CampaignLosses && killAll(other.id) &&
      PlanUse.chosen(ctx.state, PendingTree(ctx.nodePath, ctx.answered),
        ctx.activePlayer, plan.cardRef, plan.sides, afterOutcome = true)
        .flatMap(defeated).nonEmpty

  /** The losses without the defeat's toll on the user's force, then the
    * line. Read when the losses window is folded, before the losses run. */
  def spare(ctx: PowerCtx, power: PowerId, card: DenizenId, use: PlanUse,
      losses: Vector[Operation]): Vector[Operation] = (for
    user <- defeated(use)
    result <- use.result
    kind <- PlayerFacts.forceKind(use.ready, user).toOption
  yield
    val atTargets = targetForce(use.ready, result, kind)
    val cancelled = use.side match
      case CampaignPlanSide.Attacker =>
        (result.force - result.skullLosses - result.sacrificed) / 2
      case CampaignPlanSide.Defender => result.kind match
        case CampaignKind.Conquest => atTargets / 2
        case CampaignKind.Raid => board(use.ready, user) / 2
    val line = Note(power, _ => Option.when(cancelled > 0)(
      SparedForce.spared(PowerSourceRef.Card(card), NoteArg.Player(user))))
    LossRewrite(ctx, losses)(kept(use.side, user, kind, result,
      atTargets)) :+ line
  ).getOrElse(losses)

  /** The plan's user, when they are a player and were defeated. */
  private def defeated(use: PlanUse): Option[PlayerId] =
    use.user.filter(_ => use.won.contains(false))

  /** Each of the losses' own operations on the user's force, as the rule
    * leaves it. The kill-all powers are ignored, so no other kill or return
    * of the user's warbands is in the losses. */
  private def kept(side: CampaignPlanSide, user: PlayerId, kind: ForceKind,
      result: CampaignResult, atTargets: Int)(operation: CoreOperation)
      : Vector[CoreOperation] = operation match
    case kill @ Kill(Piece.Warbands(`kind`, count),
        PositionedLocation(Location.PlayArea(`user`), _, _)) => side match
      case CampaignPlanSide.Attacker =>
        val toll = math.min(count, result.skullLosses + result.sacrificed)
        if toll > 0 then Vector(kill.copy(warbands = Piece.Warbands(kind, toll)))
        else Vector.empty
      case CampaignPlanSide.Defender => Vector.empty
    case move @ Move(Piece.Warbands(`kind`, _),
        PositionedLocation(Location.WarbandBank(_), _, _),
        PositionedLocation(Location.PlayArea(`user`), _, _), None, _) =>
      Vector(move.copy(piece = Piece.Warbands(kind, atTargets)))
    case other => Vector(other)

  /** The user's warbands at the Campaign's target sites. A Raid targets no
    * site. */
  private def targetForce(ready: ReadyGame, result: CampaignResult,
      kind: ForceKind): Int =
    result.targetSites.flatMap(ready.game.current.map.sites.get)
      .map(_.forces).collect {
        case SiteForces.Occupied(`kind`, count) => count }.sum

  private def board(ready: ReadyGame, player: PlayerId): Int =
    ready.game.current.players.find(_.player == player)
      .fold(0)(_.board.warbands)
