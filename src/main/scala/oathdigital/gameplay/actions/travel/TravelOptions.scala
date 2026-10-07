package oathdigital.gameplay.actions.travel

import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers,
  WalkerSimulation}
import oathdigital.gameplay.walker.WalkerSimulation.{PreviewOutcome,
  PreviewedOption}
import oathdigital.model._

/** The destination decision as its player is shown it (travel destination
  * design, rulings 3 and 4): each destination the preview accepts badged with
  * what its Travel costs, each it refuses unavailable with why.
  *
  * The walk's own decision has already lost every destination its look-ahead
  * found no way to finish, so the declared decision is read again for the
  * full list. A destination the look-ahead hid with a restriction's reason
  * keeps that reason, which wins over a shortfall; any other hidden one is
  * explained by what it needs.
  */
object TravelOptions:
  private val Fallback = "Cannot travel there now"
  /** Enough favor for any toll a Travel can owe, to learn what a refused one
    * needs. */
  private val AmpleFavor = 10

  def priced(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, decide: Decide): Vector[DecisionOption] =
    if decide.decisionId != TravelProcedure.destinationDecisionId then
      options(decide)
    else destinations(state, tree, pending, powers, decide)

  private def destinations(state: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, decide: Decide)
      : Vector[DecisionOption] =
    val actor = decide.owner
    val walked = options(decide)
    val declared = ProcedureWalker.parkedDecide(state, tree, pending, powers,
      probing = false).map(options).getOrElse(walked)
    val previewed = previews(state, tree, pending, powers, followUp = true)
    def refused(option: DecisionOption) = DecisionOption.Unavailable(option,
      OptionReason.Written(
        shortfall(state, tree, pending, powers, actor, option.ref)))
    declared.map(option =>
      walked.find(_.ref == option.ref) match
        case Some(kept) if !kept.available => kept
        case Some(kept) => previewed.get(kept.ref) match
          case Some(Right(outcome)) if answerable(outcome) =>
            DecisionOption.Badged(kept, badge(outcome, actor))
          case _ => refused(kept)
        case None => refused(option))

  /** Whether the walk can go on from `outcome`: it finished, its further
    * question could not be previewed, or some answer to it is accepted. */
  private def answerable(outcome: PreviewOutcome): Boolean =
    outcome.complete || outcome.next.isEmpty ||
      outcome.next.exists(_.outcome.isRight)

  private def options(decide: Decide): Vector[DecisionOption] =
    decide.query match
      case DecisionQuery.ChooseOne(offered, _) => offered
      case _ => Vector.empty

  /** "2 Supply", "2 Supply · 1 favor", "1 favor", or each answer of a
    * further question joined with " or ": "2 Supply or 1 favor". */
  def badge(outcome: PreviewOutcome, actor: PlayerId): String =
    val answers = outcome.next.collect {
      case PreviewedOption(_, Right(following)) =>
        outcome.operations ++ following.operations
    }
    if answers.isEmpty then words(outcome.operations, actor)
    else answers.map(words(_, actor)).distinct.mkString(" or ")

  private def words(operations: Vector[CoreOperation], actor: PlayerId)
      : String =
    val (supply, favor) = spent(operations, actor)
    Vector(Option.when(supply > 0 || favor == 0)(s"$supply Supply"),
      Option.when(favor > 0)(s"$favor favor")).flatten.mkString(" · ")

  /** The Supply and favor a Travel takes from `actor`. Favor counts when it
    * leaves their play area for anywhere but a card: a selected modifier's
    * payment onto its card is the same for every destination. A `Give` and a
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

  /** Why `ref` cannot be reached. The pipeline's refusal carries no amount,
    * so the amount is read from the same preview with the shortfall lifted:
    * Supply at the track's maximum, then favor as well. */
  private def shortfall(state: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, actor: PlayerId,
      ref: DecisionOptionRef): String =
    val board = state.game.current.players.find(_.player == actor).map(_.board)
    def lifted(favor: Boolean): Option[PreviewOutcome] =
      previews(lift(state, actor, favor), tree, pending, powers,
        followUp = false).get(ref).flatMap(_.toOption)
    (board, lifted(favor = false).orElse(lifted(favor = true))) match
      case (Some(board), Some(outcome)) =>
        val (supply, favor) = spent(outcome.operations, actor)
        if supply > board.supply.supply then
          s"Needs $supply Supply; you have ${board.supply.supply}"
        else if favor > board.favor then
          s"Needs $favor favor; you have ${board.favor}"
        else Fallback
      case _ => Fallback

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
