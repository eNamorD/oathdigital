package oathdigital.gameplay.phases.wake

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.operations.{Location, Operation, Piece,
  RecordPowerUse, Sequence, Take}
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.powers.wake.TakeWealthLimit
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.OathLifecycle
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
  def build(catalog: ExecutableCatalog, state: ReadyGame, activePlayer: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    // Wake phase, the active player, a live game -- the same gate the legacy
    // Wake command ran, under the name it already had.
    ready <- OathLifecycle.validateReady(OathState.Ready(state), activePlayer)
    resource <- resourceOf(args)
    site <- ready.game.current.players.find(_.player == activePlayer)
      .flatMap(_.pawnSite).toRight(OathViolation.PawnSiteMissing(activePlayer))
    tokens <- ready.game.current.map.sites.get(site).map(_.tokens)
      .toRight(OathViolation.SiteNotInPlay(site))
    _ <- noEnemyPawn(ready, activePlayer, site)
    _ <- Either.cond(available(tokens, resource), (),
      OathViolation.ResourceUnavailable(site, resource))
  } yield tree(activePlayer, site, resource)

  /** Every resource the actor could take right now -- the single definition
    * the legal-action projection reads, and the Take Wealth twin of
    * `TravelProcedure.candidates`.
    *
    * Each candidate is the SAME declared tree `StartWalker` walks, dry-run
    * through [[WalkerSimulation]] against immutable state: its restrictions
    * run, its transforms fold, its operations validate, and nothing is
    * persisted. A resource absent from this vector is absent because the
    * simulation of taking it failed for the reason the command would have
    * failed, so an offer is not a second calculation that happens to agree
    * with the command -- it is the command, run without a journal.
    *
    * Assembling the tree is deliberately this procedure's job rather than the
    * projector's. The once-per-turn limit is a `Restriction` rather than a
    * build gate, so a caller that only built the tree would keep offering a
    * site already taken from this turn; and a caller that spelled the start
    * selection itself would hold a second copy of what `resourceOf` reads.
    *
    * `powers` is the vector the caller's own command would use, for the same
    * reason Travel's candidates take one.
    */
  def candidates(catalog: ExecutableCatalog, state: ReadyGame, activePlayer: PlayerId,
      powers: WalkerPowers): Vector[WakeResource] =
    WakeResource.all.filter(resource =>
      build(catalog, state, activePlayer, selection(resource))
        .flatMap(WalkerSimulation.run(_, state, powers)).isRight)

  /** The start selection naming `resource`, and the inverse of `resourceOf`.
    *
    * A resource kind is not a game object, so it rides the reference
    * vocabulary's `Button` -- the variant for a choice with no game object
    * behind it. Both directions read [[WakeResource]]'s own `key`, so the
    * spelling a client must send and the spelling this action accepts are
    * one definition rather than two matches that agree today.
    */
  def selection(resource: WakeResource): Vector[DecisionOptionRef] =
    Vector(DecisionOptionRef.Button(resource.key))

  /** The resource Take Wealth's start selection names, or a typed rejection.
    *
    * Interpreting the selection is this action's own job, exactly as reading
    * a destination out of one site reference is Travel's: the walker hands
    * over an uninterpreted vector and knows nothing about what Wake wants.
    */
  private def resourceOf(args: Vector[DecisionOptionRef])
      : Either[OathViolation, WakeResource] = args match {
    case Vector(DecisionOptionRef.Button(key)) =>
      WakeResource.fromKey(key).toRight(OathViolation.InvalidEventOrder(
        s"take wealth does not recognise the resource '$key'"))
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
