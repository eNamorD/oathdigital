package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.model._

/** Whether a queued entry would start now, and what it would cost
  * (walker-driven game loop design, ruling 8; slice 4, plan-time rulings 1
  * to 3): the checks the driver's start of it runs, on the state that start
  * would see, then the search through its modifier decision, with nothing
  * persisted. An option shown available is a start the driver accepts.
  *
  * It builds through the production registry, so a suite that injects its
  * own tree source into `OathRules` does not reach it. It does not check that
  * the entry's actor is the active player, which the start does: until an
  * off-turn actor exists (Sneak Attack), every entry's actor is the active
  * player.
  */
object QueuedStart:
  /** What a start costs its actor by its cheapest way, and whether another
    * way costs more Supply or more favor. */
  final case class StartCost(supply: Int, favor: Int,
      moreSupply: Boolean = false, moreFavor: Boolean = false):
    /** "1 Supply", "2 Supply · 1 favor", "1+ Supply", "2+ Supply · 1+
      * favor": a "+" marks an amount another way spends more of. `None`
      * when the start is free. */
    def words: Option[String] =
      def part(amount: Int, more: Boolean, unit: String) =
        Option.when(amount > 0)(s"$amount${if more then "+" else ""} $unit")
      Option.when(supply > 0 || favor > 0)(Vector(
        part(supply, moreSupply, "Supply"), part(favor, moreFavor, "favor"))
        .flatten.mkString(" · "))

  def refusal(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): Option[OathViolation] =
    priced(catalog, ready, entry, powers, phasePowers).left.toOption

  /** What the entry's start would cost now, or why it would be refused. */
  def priced(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): Either[OathViolation, StartCost] =
    val state = startingFrom(ready, entry)
    for
      tree <- WalkerProcedureRegistry.build(entry.procedure, catalog, state,
        entry.actor, entry.startArgs, phasePowers)
      past <- WalkerSimulation.started(tree, state, powers)
      cost <- costOf(entry, tree, past, powers)
    yield cost

  /** "Needs 2 Supply; you have 1", or the same for favor, when the start
    * would open with the actor's Supply raised to the track's maximum, then
    * also their favor raised: a refusal carries no amount. */
  def needs(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): Option[String] =
    def lifted(favor: Boolean) = priced(catalog,
      PricedOptions.lift(ready, entry.actor, favor), entry, powers,
      phasePowers).toOption
    for
      board <- ready.game.current.players.find(_.player == entry.actor)
        .map(_.board)
      cost <- lifted(favor = false).orElse(lifted(favor = true))
      text <-
        if cost.supply > board.supply.supply then Some(
          s"Needs ${cost.supply} Supply; you have ${board.supply.supply}")
        else if cost.favor > board.favor then Some(
          s"Needs ${cost.favor} favor; you have ${board.favor}")
        else None
    yield text

  /** The state the entry's start runs on: no walk open, and the entry
    * dequeued, so the cost waiver and the phase exemption see it. */
  def startingFrom(ready: ReadyGame, entry: QueuedProcedure): ReadyGame =
    ready.updateCurrent(_.copy(walkerPending = None, walkerProcedure = None,
      walkerStartArgs = Vector.empty, walkerStartFacts = Vector.empty,
      walkerEntry = Some(entry)))

  /** What the walk spent before its first decision past the modifiers,
    * plus, for a procedure that previews that decision, its cheapest
    * answer. Such a procedure with no answer that can be completed is
    * refused, as the playable-option gate refuses it. */
  private def costOf(entry: QueuedProcedure, tree: Operation,
      past: WalkerSimulation.Started, powers: WalkerPowers)
      : Either[OathViolation, StartCost] =
    val actor = entry.actor
    val (supply, favor) = PricedOptions.spent(past.operations, actor)
    past.at match
      case Some((state, pending))
          if WalkerProcedureRegistry.requiresPlayableOption(
            entry.procedure) =>
        WalkerSimulation.previewParked(state, tree, pending, powers,
            followUp = true).flatMap { options =>
          val ways = options.collect {
            case WalkerSimulation.PreviewedOption(_, Right(outcome))
                if PricedOptions.answerable(outcome) => outcome
          }.flatMap(PricedOptions.paths).map(PricedOptions.spent(_, actor))
            .map((more, extra) => (supply + more, favor + extra)).distinct
          if ways.isEmpty then
            Left(OathViolation.NoPlayableOption(entry.procedure.key))
          else
            val (least, leastFavor) = ways.min
            Right(StartCost(least, leastFavor,
              moreSupply = ways.exists(_._1 > least),
              moreFavor = ways.exists(_._2 > leastFavor)))
        }
      case _ => Right(StartCost(supply, favor))
