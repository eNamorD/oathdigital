package oathdigital.gameplay.phases.wake

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations.{Location, Operation, Piece,
  RecordPowerUse, Sequence, Take}
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.powers.wake.TakeWealthLimit
import oathdigital.gameplay.{OathLifecycle, OathState, OathViolation,
  ReadyGame, WakeResource}
import oathdigital.model._

/** Declared Take Wealth procedure tree for the walker (batch 1, Task 7).
  *
  * {{{
  * Sequence(                                     // window = WakeTakeWealth
  *   Take(Favor(1) | Secrets(1), site -> play area),
  *   RecordPowerUse(this site's take-wealth ref))
  * }}}
  *
  * **Why it is the first action outside Act.** Take Wealth runs in the Wake
  * phase, which is the reason Wake is in this batch: it proves the walker
  * itself carries no phase, since the only thing that changes here is which
  * gate `build` runs and which continuation the completed action produces.
  *
  * **The limit is not a gate here.** "Once per turn at each site" is a
  * `Restriction` declared by [[TakeWealthLimit]] at this tree's own window, so
  * it is gathered and run at command entry like any other power's cannot-rule.
  * `build` therefore gates on what the printed action requires and nothing
  * else: the phase, the actor's pawn site, no enemy pawn on it, and the
  * requested resource present there.
  *
  * **Both leaves are plain operations.** Neither needs deferring to walk
  * time: the site is the actor's pawn site and the resource is the start
  * selection, so the whole tree is determined when it is built. The plan
  * sketched both as `BuildOps`; deferring a value already in hand would only
  * hide it from anything that reads the tree.
  */
object TakeWealthProcedure {

  /** Fresh start and resume build the same tree. Take Wealth declares no
    * `Decide` and no `Roll`, so it finishes inside the command that starts it
    * and a resume never reaches this; the pair is kept identical rather than
    * one of them left to guess (Travel's case, for the same reason).
    */
  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    // Wake phase, the active player, a live game -- the same gate the legacy
    // Wake command ran, under the name it already had.
    ready <- OathLifecycle.validateReady(OathState.Ready(state), actor)
    resource <- resourceOf(args)
    site <- ready.game.current.players.find(_.player == actor)
      .flatMap(_.pawnSite).toRight(OathViolation.PawnSiteMissing(actor))
    tokens <- ready.game.current.map.sites.get(site).map(_.tokens)
      .toRight(OathViolation.SiteNotInPlay(site))
    _ <- noEnemyPawn(ready, actor, site)
    _ <- Either.cond(available(tokens, resource), (),
      OathViolation.ResourceUnavailable(site, resource))
  } yield tree(actor, site, resource)

  /** The resource Take Wealth's start selection names, or a typed rejection.
    *
    * A resource kind is not a game object, so it rides the reference
    * vocabulary's `Button` -- the variant for a choice with no game object
    * behind it. Interpreting it is this action's own job, exactly as reading
    * a destination out of one site reference is Travel's: the walker hands
    * over an uninterpreted vector and knows nothing about what Wake wants.
    */
  private def resourceOf(args: Vector[DecisionOptionRef])
      : Either[OathViolation, WakeResource] = args match {
    case Vector(DecisionOptionRef.Button("favor")) => Right(WakeResource.Favor)
    case Vector(DecisionOptionRef.Button("secret")) => Right(WakeResource.Secret)
    case Vector() => Left(OathViolation.InvalidEventOrder(
      "take wealth requires favor or secret as its start selection"))
    case other => Left(OathViolation.InvalidEventOrder(
      "take wealth takes exactly one resource as its start selection, got " +
        other.map(ref => s"${ref.kind}/${ref.wireId}").mkString(", ")))
  }

  private def noEnemyPawn(ready: ReadyGame, actor: PlayerId, site: SiteId)
      : Either[OathViolation, Unit] = {
    val enemies = ready.game.current.players.collect {
      case other if other.player != actor && other.pawnSite.contains(site) =>
        other.player
    }
    Either.cond(enemies.isEmpty, (),
      OathViolation.EnemyPawnBlocksTakeWealth(site, enemies))
  }

  private def available(tokens: Tokens, resource: WakeResource): Boolean =
    resource match {
      case WakeResource.Favor => tokens.favor > 0
      case WakeResource.Secret => tokens.secrets > 0
    }

  /** Tree closes only over command-stable actor, site and resource. The use
    * record is a leaf of the action rather than a side effect beside it, so
    * replay restores the limit from the same operations it restores the
    * moved token from.
    */
  private def tree(actor: PlayerId, site: SiteId,
      resource: WakeResource): Operation = Sequence(Vector(
    Take(piece(resource), actor, Location.Site(site),
      Location.PlayArea(actor)),
    RecordPowerUse(TakeWealthLimit.useRef(site))),
    Some(PowerWindow.WakeTakeWealth))

  private def piece(resource: WakeResource): Piece = resource match {
    case WakeResource.Favor => Piece.Favor(1)
    case WakeResource.Secret => Piece.Secrets(1)
  }
}
