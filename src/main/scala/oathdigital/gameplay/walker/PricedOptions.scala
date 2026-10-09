package oathdigital.gameplay.walker

import oathdigital.gameplay.walker.WalkerSimulation.{PreviewOutcome,
  PreviewedOption}
import oathdigital.model._

/** A parked choose-one as its player is shown it, for a procedure whose
  * options each cost something different (travel destination design and
  * search source design, rulings 3 and 4): each option the preview accepts
  * badged with what answering it costs, each it refuses unavailable with why.
  *
  * The walk's own decision has already lost every option its look-ahead
  * found no way to finish, so the declared decision is read again for the
  * full list. An option the look-ahead hid with a restriction's reason keeps
  * that reason, which wins over every other. Any other refused option is
  * explained by the procedure's own `explain`, then by what it needs, then by
  * the procedure's `fallback`.
  */
object PricedOptions:
  /** Enough favor for any toll an action can owe, to learn what a refused
    * option needs. */
  private val AmpleFavor = 10

  def priced(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide, fallback: String,
      explain: DecisionOptionRef => Option[String] = _ => None)
      : Vector[DecisionOption] =
    val actor = decide.owner
    val walked = options(decide)
    val declared = ProcedureWalker.parkedDecide(state, tree, pending, powers,
      probing = false).map(options).getOrElse(walked)
    val previewed = previews(state, tree, pending, powers, followUp = true)
    def refused(option: DecisionOption) = DecisionOption.Unavailable(option,
      OptionReason.Written(explain(option.ref).getOrElse(shortfall(state,
        tree, pending, powers, actor, option.ref, fallback))))
    declared.map(option =>
      walked.find(_.ref == option.ref) match
        case Some(kept) if !kept.available => kept
        case Some(kept) => previewed.get(kept.ref) match
          case Some(Right(outcome)) if answerable(outcome) =>
            DecisionOption.Badged(kept, badge(outcome, actor))
          case _ => refused(kept)
        case None => refused(option))

  /** A choose-one's options as the decision lists them; none for any other
    * query. */
  def options(decide: Decide): Vector[DecisionOption] =
    decide.query match
      case DecisionQuery.ChooseOne(offered, _) => offered
      case _ => Vector.empty

  /** Whether the walk can go on from `outcome`: it finished, its further
    * question could not be previewed, or some answer to it can go on. */
  private def answerable(outcome: PreviewOutcome): Boolean =
    outcome.complete || outcome.next.isEmpty ||
      outcome.next.exists(_.outcome.exists(answerable))

  /** The operations along each way the walk goes on from `outcome`: its own,
    * then those of each accepted answer to the further question, a
    * pay-or-ask step's included. Its own alone when no further answer is
    * accepted. */
  private def paths(outcome: PreviewOutcome): Vector[Vector[CoreOperation]] =
    outcome.next.collect { case PreviewedOption(_, Right(following)) =>
      following }.flatMap(paths) match
      case Vector() => Vector(outcome.operations)
      case found => found.map(outcome.operations ++ _)

  /** "2 Supply", "2 Supply · 1 favor", "1 favor", or each way on joined
    * with " or ": "2 Supply or 1 favor". */
  def badge(outcome: PreviewOutcome, actor: PlayerId): String =
    paths(outcome).map(words(_, actor)).distinct.mkString(" or ")

  private def words(operations: Vector[CoreOperation], actor: PlayerId)
      : String =
    val (supply, favor) = spent(operations, actor)
    Vector(Option.when(supply > 0 || favor == 0)(s"$supply Supply"),
      Option.when(favor > 0)(s"$favor favor")).flatten.mkString(" · ")

  /** The Supply and favor an answer takes from `actor`. Favor counts when it
    * leaves their play area for anywhere but a card: a selected modifier's
    * payment onto its card is the same for every option. A `Give` and a
    * `PayCost` are walked as their moves, so the moves are read. */
  private def spent(operations: Vector[CoreOperation], actor: PlayerId)
      : (Int, Int) =
    val supply = operations.collect {
      case SpendSupply(player, amount, _) if player == actor => amount }.sum
    val favor = operations.collect {
      case Move(Piece.Favor(amount),
          PositionedLocation(Location.PlayArea(player), _),
          PositionedLocation(to, _), _)
          if player == actor && !to.isInstanceOf[Location.OnCard] => amount
    }.sum
    (supply, favor)

  /** Why `ref` cannot be answered. The pipeline's refusal carries no amount,
    * so the amount is read from the same preview with the shortfall lifted:
    * Supply at the track's maximum, then favor as well. */
  private def shortfall(state: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, actor: PlayerId,
      ref: DecisionOptionRef, fallback: String): String =
    val board = state.game.current.players.find(_.player == actor).map(_.board)
    def lifted(favor: Boolean): Option[PreviewOutcome] =
      previews(lift(state, actor, favor), tree, pending, powers,
        followUp = false).get(ref).flatMap(_.toOption)
    (board, lifted(favor = false).orElse(lifted(favor = true))) match
      case (Some(board), Some(outcome)) =>
        val (supply, favor) = spent(paths(outcome).head, actor)
        if supply > board.supply.supply then
          s"Needs $supply Supply; you have ${board.supply.supply}"
        else if favor > board.favor then
          s"Needs $favor favor; you have ${board.favor}"
        else fallback
      case _ => fallback

  private def lift(state: ReadyGame, actor: PlayerId, favor: Boolean)
      : ReadyGame = state.updateCurrent(current => current.copy(
    players = current.players.map(player =>
      if player.player != actor then player
      else player.copy(board = player.board.copy(
        supply = SupplyTrack(SupplyTrack.Maximum),
        favor = if favor then player.board.favor + AmpleFavor
          else player.board.favor)))))

  private def previews(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, followUp: Boolean)
      : Map[DecisionOptionRef, Either[OathViolation, PreviewOutcome]] =
    WalkerSimulation.previewParked(state, tree, pending, powers, followUp)
      .fold(_ => Map.empty, _.map(previewed =>
        previewed.option.ref -> previewed.outcome).toMap)
