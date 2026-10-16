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
  /** Enough favor, or secrets, for any cost an action can owe, to learn what
    * a refused option needs. */
  private val Ample = 10

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
      case one: DecisionQuery.ChooseOne => one.options
      case _ => Vector.empty

  /** Whether the walk can go on from `outcome`: it finished, its further
    * question could not be previewed, or some answer to it can go on. */
  private[walker] def answerable(outcome: PreviewOutcome): Boolean =
    outcome.complete || outcome.next.isEmpty ||
      outcome.next.exists(_.outcome.exists(answerable))

  /** The operations along each way the walk goes on from `outcome`: its own,
    * then those of each answer to the further question from which the walk
    * can go on, a pay-or-ask step's included. Its own alone when no further
    * answer can. */
  private[walker] def paths(outcome: PreviewOutcome)
      : Vector[Vector[CoreOperation]] =
    outcome.next.collect { case PreviewedOption(_, Right(following))
        if answerable(following) => following }.flatMap(paths) match
      case Vector() => Vector(outcome.operations)
      case found => found.map(outcome.operations ++ _)

  /** What an answer takes from its actor: the Supply it spends, and the favor
    * and secrets that leave their play area, kept apart by whether they are
    * paid (onto a card, into a bank, to a player) or burnt to the shared
    * bank. */
  final case class Spent(supply: Int = 0, favor: Int = 0, favorBurnt: Int = 0,
      secrets: Int = 0, secretsBurnt: Int = 0):
    def +(other: Spent): Spent = Spent(supply + other.supply,
      favor + other.favor, favorBurnt + other.favorBurnt,
      secrets + other.secrets, secretsBurnt + other.secretsBurnt)

    /** All the favor, paid or burnt: what the actor must hold. */
    def allFavor: Int = favor + favorBurnt

    /** All the secrets, paid or burnt: what the actor must hold faceup. */
    def allSecrets: Int = secrets + secretsBurnt

    /** "1 Supply", "1 Supply · 1 [favor] · 1 [favor-burnt]", "1 [favor]": a
      * resource is its bracketed token, which a client draws as its glyph.
      * `more` marks with a "+" a part some other way spends more of. Supply
      * is named when it is spent, and when nothing else is, as "0 Supply". */
    def words(more: Spent.Part => Boolean = _ => false): String =
      val paid = Spent.parts.filter(part => part.of(this) > 0 || part.supply &&
        Spent.parts.forall(_.of(this) == 0))
      paid.map(part => s"${part.of(this)}${if more(part) then "+" else ""} " +
        part.unit).mkString(" · ")

  object Spent:
    /** One kind of thing spent, as `words` names it. */
    final case class Part(of: Spent => Int, unit: String,
        supply: Boolean = false)

    val parts: Vector[Part] = Vector(Part(_.supply, "Supply", supply = true),
      Part(_.favor, "[favor]"), Part(_.favorBurnt, "[favor-burnt]"),
      Part(_.secrets, "[secret]"), Part(_.secretsBurnt, "[secret-burnt]"))

  /** "2 Supply", "2 Supply · 1 [favor]", "1 [favor]", or each way on joined
    * with " or ": "2 Supply or 1 [favor]". */
  def badge(outcome: PreviewOutcome, actor: PlayerId): String =
    paths(outcome).map(spent(_, actor).words()).distinct.mkString(" or ")

  /** What an answer takes from `actor`. A `Give` and a `PayCost` are walked
    * as their moves, so the moves are read: favor or secrets that leave the
    * actor's play area for the shared bank are burnt, and for anywhere else,
    * a card included, paid. */
  private[walker] def spent(operations: Vector[CoreOperation],
      actor: PlayerId): Spent =
    operations.foldLeft(Spent()):
      case (total, SpendSupply(player, amount, _)) if player == actor =>
        total.copy(supply = total.supply + amount)
      case (total, Move(piece,
          PositionedLocation(Location.PlayArea(player), _, _),
          PositionedLocation(to, _, _), _, _)) if player == actor =>
        val burnt = to == Location.SharedBank
        piece match
          case Piece.Favor(amount) if burnt =>
            total.copy(favorBurnt = total.favorBurnt + amount)
          case Piece.Favor(amount) => total.copy(favor = total.favor + amount)
          case Piece.Secrets(amount) if burnt =>
            total.copy(secretsBurnt = total.secretsBurnt + amount)
          case Piece.Secrets(amount) =>
            total.copy(secrets = total.secrets + amount)
          case _ => total
      case (total, _) => total

  /** Why `ref` cannot be answered. The pipeline's refusal carries no amount,
    * so the amount is read from the same preview with the shortfall lifted:
    * Supply at the track's maximum, then favor and secrets as well. */
  private def shortfall(state: ReadyGame, tree: Operation,
      pending: PendingTree, powers: WalkerPowers, actor: PlayerId,
      ref: DecisionOptionRef, fallback: String): String =
    val board = state.game.current.players.find(_.player == actor).map(_.board)
    def lifted(resources: Boolean): Option[PreviewOutcome] =
      previews(lift(state, actor, resources), tree, pending, powers,
        followUp = false).get(ref).flatMap(_.toOption)
    (board, lifted(resources = false).orElse(lifted(resources = true))) match
      case (Some(board), Some(outcome)) =>
        needs(spent(paths(outcome).head, actor), board).getOrElse(fallback)
      case _ => fallback

  /** "Needs 2 Supply; you have 1", then the same for favor, then for faceup
    * secrets: the first of them `cost` asks more of than `board` holds. */
  private[walker] def needs(cost: Spent, board: PlayerBoardState)
      : Option[String] =
    if cost.supply > board.supply.supply then
      Some(s"Needs ${cost.supply} Supply; you have ${board.supply.supply}")
    else if cost.allFavor > board.favor then
      Some(s"Needs ${cost.allFavor} favor; you have ${board.favor}")
    else if cost.allSecrets > board.faceUpSecrets then
      Some(s"Needs ${cost.allSecrets} ${if cost.allSecrets == 1 then "secret"
        else "secrets"}; you have ${board.faceUpSecrets}")
    else None

  /** `state` with the actor's Supply at the track's maximum and, with
    * `resources`, ample favor and faceup secrets as well. */
  private[walker] def lift(state: ReadyGame, actor: PlayerId,
      resources: Boolean): ReadyGame = state.updateCurrent(current =>
    current.copy(players = current.players.map(player =>
      if player.player != actor then player
      else player.copy(board = player.board.copy(
        supply = SupplyTrack(SupplyTrack.Maximum),
        favor = player.board.favor + (if resources then Ample else 0),
        faceUpSecrets = player.board.faceUpSecrets +
          (if resources then Ample else 0))))))

  private def previews(state: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, followUp: Boolean)
      : Map[DecisionOptionRef, Either[OathViolation, PreviewOutcome]] =
    WalkerSimulation.previewParked(state, tree, pending, powers, followUp)
      .fold(_ => Map.empty, _.map(previewed =>
        previewed.option.ref -> previewed.outcome).toMap)
