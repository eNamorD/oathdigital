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
  * own tree source into `OathRules` does not reach it. The simulated walk is
  * the entry's actor's, since `startingFrom` sets `walkerEntry`.
  */
object QueuedStart:
  /** What a start costs its actor by its cheapest way, `least`, and by every
    * other way it could go. */
  final case class StartCost(least: PricedOptions.Spent,
      others: Vector[PricedOptions.Spent] = Vector.empty):
    /** "1 Supply", "2 Supply · 1 [favor]", "1+ Supply", "1 Supply · 1
      * [favor] · 1 [favor-burnt]": a "+" marks an amount another way spends
      * more of. `None` when the start is free. */
    def words: Option[String] =
      Option.when(least != PricedOptions.Spent())(least.words(part =>
        others.exists(way => part.of(way) > part.of(least))))

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
        entry.actor, entry.startArgs, phasePowers, walkerPowers = powers)
      past <- WalkerSimulation.started(tree, state, powers)
      cost <- costOf(entry, tree, past, powers)
    yield cost

  /** "Needs 2 Supply; you have 1", or the same for favor or secrets, when
    * the start would open with the actor's Supply raised to the track's
    * maximum, then also their favor and secrets raised: a refusal carries no
    * amount. It is asked of a start `priced` refused, so a raise that
    * changes nothing, as for an actor whose Supply is full, is refused
    * again without pricing it. */
  def needs(catalog: ExecutableCatalog, ready: ReadyGame,
      entry: QueuedProcedure, powers: WalkerPowers,
      phasePowers: PhasePowers): Option[String] =
    def lifted(resources: Boolean) =
      val state = PricedOptions.lift(ready, entry.actor, resources)
      Option.when(state != ready)(priced(catalog, state, entry, powers,
        phasePowers).toOption).flatten
    for
      board <- ready.game.current.players.find(_.player == entry.actor)
        .map(_.board)
      cost <- lifted(resources = false).orElse(lifted(resources = true))
      text <- PricedOptions.needs(cost.least, board)
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
    val before = PricedOptions.spent(past.operations, actor)
    past.at match
      case Some((state, pending))
          if WalkerProcedureRegistry.gatesStart(entry.procedure,
            state) =>
        WalkerSimulation.previewParked(state, tree, pending, powers,
            followUp = true).flatMap { options =>
          val ways = options.collect {
            case WalkerSimulation.PreviewedOption(_, Right(outcome))
                if PricedOptions.answerable(outcome) => outcome
          }.flatMap(PricedOptions.paths).map(PricedOptions.spent(_, actor))
            .map(before + _).distinct
          if ways.isEmpty then
            Left(OathViolation.NoPlayableOption(entry.procedure.key))
          else
            // The cheapest way is the one that spends the least Supply,
            // then the least favor, then the fewest secrets.
            val least = ways.minBy(way =>
              (way.supply, way.allFavor, way.allSecrets, way.favorBurnt,
                way.secretsBurnt))
            Right(StartCost(least, ways.filter(_ != least)))
        }
      case _ => Right(StartCost(before))
