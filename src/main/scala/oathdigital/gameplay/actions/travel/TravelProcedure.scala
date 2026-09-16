package oathdigital.gameplay.actions.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.TravelRules
import oathdigital.gameplay.operations.{SpendSupply, CoreOperation, Location,
  Move, Operation, Piece, PositionedLocation, Sequence}
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.{OathLifecycle, OathState, OathViolation, ReadyGame}
import oathdigital.model.{DecisionOptionRef, PlayerId, SiteId}

/** Declared Travel procedure tree for the walker (batch 1, Task 5).
  *
  * {{{
  * Sequence(                                  // window = TravelActionEligibility
  *   Sequence(                                // window = TravelCost
  *     SpendSupply(actor, printedBase),
  *     Move(Pawn, source -> destination)))
  * }}}
  *
  * **Why the cost node is a composite and not a windowed leaf.** A transform
  * hooked on a windowed leaf is handed `Vector(leaf)` and can only wrap or
  * replace that one operation; it cannot see the route. Travel's terrain
  * transforms need both children -- they rewrite the `SpendSupply` amount but
  * read the destination off the sibling `Move` -- so the window sits on a
  * `Sequence` that hands over both. The outer eligibility `Sequence` wraps the
  * complete cost node for the same reason: Narrow Pass's `Restriction` has to
  * see the same route.
  *
  * **Destination selection is not a decision.** Travel declares no `Decide`.
  * Choosing a destination is the parameter that selects a complete, atomic
  * Travel tree, not a persisted interruption inside one -- which is what keeps
  * Travel free of pending state, cancel semantics, and a route-less
  * eligibility window. The destination rides the `StartWalker` command as the
  * action's start selection, and [[destinationOf]] below is the only code
  * anywhere that knows a Travel start selection is one site.
  *
  * **What does not gate a start.** Supply is not a build gate: it is owned by
  * the transformed `SpendSupply` and `OperationValidator`, and because the
  * tree is flat a rejection there leaves neither a moved pawn nor pending
  * state. Player role and Foundation faces are not gates either -- neither is
  * a fact about the printed Travel action. Terrain is not a gate: it is the
  * transforms folding over this tree's own cost node.
  */
object TravelProcedure {

  /** Fresh start and resume build the same tree: Travel's gates are all facts
    * about the route, which a resume must re-check exactly as a start does.
    * (Recover and Forge differ because both spend Supply before they park.)
    */
  def build(catalog: ExecutableCatalog, state: ReadyGame, activePlayer: PlayerId,
      args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for {
    _ <- OathLifecycle.validateAct(OathState.Ready(state), activePlayer)
    destination <- destinationOf(args)
    source <- actorSite(state, activePlayer).toRight(
      OathViolation.PawnSiteMissing(activePlayer))
    base <- TravelRules.cost(catalog, state, source, destination)
  } yield tree(activePlayer, source, destination, base)

  /** The destination Travel's start selection names, or a typed rejection.
    *
    * The walker hands over an uninterpreted vector of references and knows
    * nothing about what Travel wants from it, so this is where the shape is
    * checked: exactly one reference, and a site. No selection, two, or a
    * relic are all the same failure from here -- a command that did not name
    * one destination is not a Travel, whatever else it named.
    */
  private def destinationOf(args: Vector[DecisionOptionRef])
      : Either[OathViolation, SiteId] = args match {
    case Vector(DecisionOptionRef.Site(destination)) => Right(destination)
    case Vector() => Left(OathViolation.InvalidEventOrder(
      "travel requires a destination site as its start selection"))
    case other => Left(OathViolation.InvalidEventOrder(
      "travel takes exactly one destination site as its start selection, " +
        s"got ${other.map(_.kind).mkString(", ")}"))
  }

  def actorSite(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)

  /** Tree closes only over command-stable actor, route, and printed base. */
  private def tree(actor: PlayerId, source: SiteId, destination: SiteId,
      base: Int): Operation =
    Sequence(Vector(
      Sequence(Vector(
        SpendSupply(actor, base),
        Move(Piece.Pawn(actor),
          PositionedLocation(Location.Site(source)),
          PositionedLocation(Location.Site(destination)))),
        Some(PowerWindow.TravelCost))),
      Some(PowerWindow.TravelActionEligibility))

  /** Every destination the actor may travel to right now, with the Supply it
    * would actually cost -- the single definition both the legal-action
    * projection and the major-action preview read.
    *
    * Each candidate is the SAME declared tree `StartWalker` walks, dry-run
    * through [[WalkerSimulation]] against immutable state: its restrictions
    * run, its transforms fold, its operations validate, and nothing is
    * persisted. A destination whose route is restricted, whose tree does not
    * build, or whose transformed cost the actor cannot afford is simply
    * absent, because the simulation of it failed for the same reason the
    * command would have. So an offered destination and its number are not a
    * second calculation that happens to agree with the command -- they are
    * the command, run without a journal.
    *
    * `powers` is the vector the caller's own command would use: the initial
    * projection passes the automatic catalog, the preview passes the exact
    * selected vector, and a player-selected power therefore changes a
    * projected cost only once it is actually selected.
    */
  def candidates(catalog: ExecutableCatalog, state: ReadyGame, activePlayer: PlayerId,
      powers: WalkerPowers): Vector[(SiteId, Int)] =
    actorSite(state, activePlayer).toVector.flatMap { source =>
      state.game.current.map.inPlay.filter(_ != source).flatMap { destination =>
        build(catalog, state, activePlayer,
          Vector(DecisionOptionRef.Site(destination)))
          .flatMap(WalkerSimulation.run(_, state, powers))
          .toOption.flatMap(supplySpent(_, activePlayer)).map(destination -> _)
      }
    }

  /** The Supply a completed Travel actually spent, read off the operations the
    * walk recorded rather than recomputed: after the fold, the pay node is
    * whatever the surviving transforms left it as.
    */
  private def supplySpent(operations: Vector[CoreOperation], actor: PlayerId)
      : Option[Int] = operations.collect {
    case SpendSupply(player, amount, _) if player == actor => amount
  }.lastOption
}
