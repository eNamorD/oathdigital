package oathdigital.gameplay.operations

import oathdigital.gameplay.ReadyGame
import oathdigital.model._

/** One shape violation against an operation, mirroring the code/detail of the
  * [[OperationError]] the mutation pipeline would reject with.
  */
sealed trait OperationReasonKind
object OperationReasonKind {
  case object Impossible extends OperationReasonKind
  case object Invalid extends OperationReasonKind
}

final case class OperationReason(code: String, detail: String,
    kind: OperationReasonKind = OperationReasonKind.Invalid)

/** Extension seam for per-query contextual restrictions beyond the static
  * allowlist. Restrictions report typed rule impossibility or invalidity.
  */
trait OperationRestriction {
  def reason(
      ready: ReadyGame,
      operation: CoreOperation
  ): Option[OperationReason]
}

/** Aggregated validator owned by [[OperationPipeline]] for one run:
  * `validateBatch` reports the whole batch against the initial state
  * (including cross-operation conflicts), and `validateOne` re-checks every
  * operation against the staged state during the fold. The pipeline's
  * authoritative rejection uses the staged `validateOne` reasons so that
  * trajectory-dependent batches stay legal.
  *
  * `validateBatch` and `validateOne` return every violation as an
  * [[OperationReason]] (never first-fail), so callers can inspect all of them.
  * Pipeline rejection stays first-fail: it takes the head reason.
  */
final class OperationValidator(
    allowlist: OperationPolicy,
    restrictions: Vector[OperationRestriction]
) {
  def validateBatch(
      ready: ReadyGame,
      operations: Vector[CoreOperation]
  ): Vector[OperationReason] =
    allowlistReasons(ready, operations) ++
      OperationShape.validateBatch(ready, operations) ++
      operations.flatMap(restrictionReasons(ready, _))

  /** Allowlist reasons first: the retired executor ran the per-action policy
    * before any shape/mutation check, so a both-fail operation rejects with
    * `RestrictedOperation` — mirrored here for byte-identical precedence.
    */
  def validateOne(
      ready: ReadyGame,
      operation: CoreOperation
  ): Vector[OperationReason] =
    allowlistReasons(ready, Vector(operation)) ++
      validateResolvedOne(ready, operation)

  /** Revalidation after a permitted operation shrinks must not re-run an
    * exact allowlist against a different amount.
    */
  def validateResolvedOne(ready: ReadyGame,
      operation: CoreOperation): Vector[OperationReason] =
    OperationShape.validate(ready, operation) ++
      restrictionReasons(ready, operation)

  private def restrictionReasons(ready: ReadyGame,
      operation: CoreOperation): Vector[OperationReason] =
    restrictions.flatMap(_.reason(ready, operation))

  private def allowlistReasons(
      ready: ReadyGame,
      operations: Vector[CoreOperation]
  ): Vector[OperationReason] =
    operations.flatMap { operation =>
      allowlist.validate(ready, operation) match {
        case Left(error) => Vector(OperationReason(error.code, error.detail))
        case Right(_) => Vector.empty
      }
    }
}

/** Pure shape partition of the checks [[OperationStateMutation]] runs while
  * applying one operation. It owns the *structural* validation of an operation
  * against a ready state — primitive position conventions, card source and
  * destination legality, counted-source sufficiency, pawn/banner
  * preconditions, and the guards of non-move primitives — aggregated instead
  * of first-fail so callers can inspect every violation of an operation or of
  * a whole batch before anything is executed.
  *
  * Every reason mirrors the exact [[OperationError]] instance the mutation
  * guards produce for the same condition, so [[first]] is byte-identical to
  * the typed rejection the executor observes today.
  */
object OperationShape {
  import OperationError._
  import OperationStateAdapter._

  /** All shape violations for one operation against `ready`, aggregated. */
  def validate(
      ready: ReadyGame,
      operation: CoreOperation
  ): Vector[OperationReason] =
    violations(ready, operation).map(reason(_, operation))

  /** All shape violations for a whole batch against the INITIAL state, plus
    * cross-operation violations (e.g. the same card moved by two operations).
    */
  def validateBatch(
      ready: ReadyGame,
      operations: Vector[CoreOperation]
  ): Vector[OperationReason] = {
    val perOperation = operations.flatMap(operation =>
      violations(ready, operation).map(reason(_, operation)))
    val movedById = operations.map(operation =>
      Operation.flatten(operation).iterator.collect {
        case Move(piece: Piece.Card, _, _, _) => piece.id
        case bury: Bury => bury.card.id
      }.toSet)
    val crossOperation = for {
      left <- movedById.indices
      right <- (left + 1) until movedById.size
      if movedById(left).intersect(movedById(right)).nonEmpty
    } yield ConflictingDeltas(
      "operation batch moves the same card more than once"): OperationError
    perOperation ++
      crossOperation.map(error => OperationReason(error.code, error.detail))
  }

  private def reason(error: OperationError,
      operation: CoreOperation): OperationReason = {
    val impossible = error match {
      case _: InsufficientSupply => true
      case InsufficientPieces(piece, _, _) => operation match {
        case _: Discard | _: PayCost => false
        case _ => piece.isInstanceOf[Piece.Counted]
      }
      case _ => false
    }
    OperationReason(error.code, error.detail,
      if (impossible) OperationReasonKind.Impossible
      else OperationReasonKind.Invalid)
  }

  /** First violation as an [[OperationError]]-compatible rejection, if any. */
  def first(
      ready: ReadyGame,
      operation: CoreOperation
  ): Option[OperationError] =
    violations(ready, operation).headOption

  private def violations(
      ready: ReadyGame,
      operation: CoreOperation
  ): Vector[OperationError] = {
    val leaves = Operation.flatten(operation)
    val favorMoves = leaves.collect {
      case move @ Move(_: Piece.Favor, _, _, _) => move
    }
    val secretMoves = leaves.collect {
      case move @ Move(_: Piece.Secrets, _, _, _) => move
    }
    val warbandMoves = leaves.collect {
      case move @ Move(_: Piece.Warbands, _, _, _) => move
    }
    val (secretReasons, plannedSecrets) = planSecrets(ready, secretMoves)

    val accumulated = Vector.newBuilder[OperationError]
    accumulated ++= positionViolations(leaves)
    accumulated ++= cardViolations(ready, leaves)
    accumulated ++= resourceDescriptionViolations(ready, operation)
    accumulated ++= countedSourceViolations(
      ready, favorMoves, warbandMoves, secretReasons)
    accumulated ++= countedDestinationViolations(ready, leaves)
    accumulated ++= pawnAndBannerViolations(ready, leaves)
    accumulated ++= nonMoveViolations(ready, leaves, plannedSecrets)
    accumulated.result()
  }

  private def resourceDescriptionViolations(ready: ReadyGame,
      operation: CoreOperation): Vector[OperationError] = {
    val described: Option[(CardId, Option[Int], Int)] = operation match {
      case value: Discard.Denizen =>
        Some((value.card, Some(value.favor), value.secrets))
      case value: Discard.RuinedEdifice =>
        Some((value.card, Some(value.favor), value.secrets))
      case value: Discard.Relic => Some((value.card, None, value.secrets))
      case _ => None
    }
    described.toVector.flatMap { case (cardId, favor, secrets) =>
      val at = Location.OnCard(cardId)
      val counts = for {
        actualFavor <- quantity(ready, Piece.Favor(1), at)
        actualSecrets <- quantity(ready, Piece.Secrets(1), at)
      } yield (actualFavor, actualSecrets)
      counts match {
        case Right((AvailableQuantity.Finite(actualFavor),
            AvailableQuantity.Finite(actualSecrets)))
            if favor.exists(_ != actualFavor) || actualSecrets != secrets =>
          Vector(InvalidDescription(
            s"discard resources on ${cardId.value} do not match the card"))
        case _ => Vector.empty
      }
    }
  }

  // ------------------------------------------------------------------
  // 1. Primitive position / stack-convention checks
  // ------------------------------------------------------------------

  private def positionViolations(
      leaves: Vector[Operation]
  ): Vector[OperationError] = leaves.iterator.flatMap {
    case move: Move => positionViolations(move.from, move.to)
    case bury: Bury => positionViolations(bury.from, bury.to)
    case _ => Vector.empty
  }.toVector

  private def positionViolations(
      from: PositionedLocation,
      to: PositionedLocation
  ): Vector[OperationError] =
    sourcePositionViolation(from).toVector ++
      destinationPositionViolation(to).toVector

  private def sourcePositionViolation(
      positioned: PositionedLocation
  ): Option[OperationError] =
    if (isStack(positioned.location)) None
    else if (positioned.position == StackPosition.Unspecified) None
    else Some(InvalidStackPosition(
      positioned.location,
      "non-stack source cannot specify top or bottom"
    ))

  private def destinationPositionViolation(
      positioned: PositionedLocation
  ): Option[OperationError] =
    if (isStack(positioned.location)) {
      if (positioned.position != StackPosition.Unspecified) None
      else Some(InvalidStackPosition(
        positioned.location,
        "stack destination must specify top or bottom"
      ))
    } else if (positioned.position == StackPosition.Unspecified) None
    else Some(InvalidStackPosition(
      positioned.location,
      "non-stack destination cannot specify top or bottom"
    ))

  private def isStack(location: Location): Boolean = location match {
    case _: Location.Deck | _: Location.RegionalDiscard => true
    case _ => false
  }

  // ------------------------------------------------------------------
  // 2. Card-source and destination checks
  // ------------------------------------------------------------------

  private final case class Transfer(
      piece: Piece.Card,
      from: PositionedLocation,
      to: PositionedLocation,
      resultingOrientation: Option[Orientation]
  )

  private final case class ResolvedTransfer(
      transfer: Transfer,
      located: LocatedCard
  )

  private def transfers(
      leaves: Vector[Operation]
  ): Vector[Transfer] = leaves.collect {
    case Move(piece: Piece.Card, from, to, orientation) =>
      Transfer(piece, from, to, orientation)
    case bury: Bury => Transfer(
      Piece.Card(bury.card.id),
      bury.from,
      bury.to,
      resultingOrientation = None
    )
  }

  private def cardViolations(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Vector[OperationError] = {
    val all = transfers(leaves)
    val duplicate: Vector[OperationError] =
      if (all.map(_.piece.id).groupBy(identity).exists {
        case (_, occurrences) => occurrences.size > 1
      }) Vector(ConflictingDeltas(
        "one operation moves the same card more than once"))
      else Vector.empty

    val resolved = all.map { transfer =>
      card(ready, transfer.piece.id, transfer.from.location)
        .map(ResolvedTransfer(transfer, _))
    }
    val sourceReasons = resolved.collect { case Left(error) => error }
    val successful = resolved.collect { case Right(value) => value }

    duplicate ++ sourceReasons ++
      cardSourceOrderViolations(ready, successful) ++
      successful.flatMap(value =>
        cardDestinationViolation(value.located, value.transfer).toVector)
  }

  private def cardSourceOrderViolations(
      ready: ReadyGame,
      transfers: Vector[ResolvedTransfer]
  ): Vector[OperationError] = {
    val grouped = transfers.groupBy(_.located.location.container)
    grouped.toVector.flatMap { case (container, values) =>
      stackCards(ready, container) match {
        case None => Vector.empty
        case Some(initial) =>
          val (failure, _) = values.foldLeft[(Option[OperationError],
            Vector[CardId])]((None, initial)) {
            case ((Some(error), cards), _) => (Some(error), cards)
            case ((None, cards), value) =>
              val id = value.located.id
              val valid = value.transfer.from.position match {
                case StackPosition.Unspecified => cards.contains(id)
                case StackPosition.Top => cards.headOption.contains(id)
                case StackPosition.Bottom => cards.lastOption.contains(id)
              }
              if (valid) (None, cards.filterNot(_ == id))
              else (Some(InvalidStackPosition(
                value.transfer.from.location,
                "card does not match requested stack position"
              )), cards)
          }
          failure.toVector
      }
    }
  }

  private def cardDestinationViolation(
      located: LocatedCard,
      transfer: Transfer
  ): Option[OperationError] = {
    val id = located.id
    val destination = transfer.to.location
    destination match {
      case Location.Deck(deck) =>
        if (cardDeck(id).contains(deck)) None
        else Some(InvalidDestination(transfer.piece, destination))
      case _: Location.RegionalDiscard =>
        if (id.isInstanceOf[WorldCardId]) None
        else Some(InvalidDestination(transfer.piece, destination))
      case _: Location.Hand =>
        if (id.isInstanceOf[WorldCardId]) None
        else Some(InvalidDestination(transfer.piece, destination))
      case Location.Reliquary | Location.SetAsideRelics =>
        if (id.isInstanceOf[RelicId]) None
        else Some(InvalidDestination(transfer.piece, destination))
      case Location.Dispossessed =>
        if (id.isInstanceOf[WorldCardId]) None
        else Some(InvalidDestination(transfer.piece, destination))
      case _: Location.Site => id match {
        case _: DenizenId | _: EdificeId | _: RelicId =>
          statefulMaterializationViolation(located, transfer)
        case _ => Some(InvalidDestination(transfer.piece, destination))
      }
      case _: Location.PlayArea => id match {
        case _: DenizenId | _: VisionId | _: RelicId =>
          statefulMaterializationViolation(located, transfer)
        case _ => Some(InvalidDestination(transfer.piece, destination))
      }
      case Location.Atlas => Some(AmbiguousLocation(
        Location.Atlas,
        "Atlas destination requires a stored-site identity"
      ))
      case _ => Some(InvalidDestination(transfer.piece, destination))
    }
  }

  private def statefulMaterializationViolation(
      located: LocatedCard,
      transfer: Transfer
  ): Option[OperationError] = located.id match {
    case id: EdificeId if transfer.resultingOrientation.nonEmpty =>
      Some(UnsupportedOrientation(id, transfer.to.location))
    case id: EdificeId if located.state.isEmpty =>
      Some(UnsupportedOrientation(id, transfer.to.location))
    case id if located.state.isEmpty &&
        transfer.resultingOrientation.isEmpty =>
      Some(MissingOrientation(id, transfer.to.location))
    case _ => None
  }

  private def cardDeck(id: CardId): Option[CardDeck] = id match {
    case _: DenizenId | _: VisionId => Some(CardDeck.World)
    case _: RelicId => Some(CardDeck.Relic)
    case _: EdificeId => Some(CardDeck.Edifice)
    case _: LegacyId => Some(CardDeck.Legacy)
  }

  private def stackCards(
      ready: ReadyGame,
      container: CardContainer
  ): Option[Vector[CardId]] = container match {
    case CardContainer.Deck(DeckKind.World) =>
      Some(ready.game.current.commonCards.worldDeck)
    case CardContainer.Deck(DeckKind.Relic) =>
      Some(ready.game.current.commonCards.relicDeck)
    case CardContainer.Deck(DeckKind.Edifice) =>
      Some(ready.game.current.commonCards.edificeDeck)
    case CardContainer.Deck(DeckKind.Legacy) =>
      Some(ready.game.current.commonCards.legacyDeck)
    case CardContainer.RegionalDiscard(region) =>
      Some(ready.game.current.commonCards.discard(region).reverse)
    case _ => None
  }

  // ------------------------------------------------------------------
  // 3. Counted-source sufficiency
  // ------------------------------------------------------------------

  private def planSecrets(
      ready: ReadyGame,
      moves: Vector[Move]
  ): (Vector[OperationError],
      Option[Vector[(Move, OperationSecretPlanner.SecretSplit)]]) =
    OperationSecretPlanner.plan(ready, moves) match {
      case Left(error) => (Vector(error), None)
      case Right(planned) => (Vector.empty, Some(planned))
    }

  private def countedSourceViolations(
      ready: ReadyGame,
      favorMoves: Vector[Move],
      warbandMoves: Vector[Move],
      secretReasons: Vector[OperationError]
  ): Vector[OperationError] =
    favorSourceViolations(ready, favorMoves) ++
      secretReasons ++
      warbandSourceViolations(ready, warbandMoves)

  private def countedDestinationViolations(ready: ReadyGame,
      leaves: Vector[Operation]): Vector[OperationError] =
    leaves.flatMap {
      case Move(piece: Piece.Counted, _, to, _)
          if piece.isInstanceOf[Piece.Favor] &&
            to.location == Location.SharedBank => Vector.empty
      case Move(piece: Piece.Counted, _, to, _) =>
        quantity(ready, piece, to.location).left.toOption.toVector
      case _ => Vector.empty
    }

  private def favorSourceViolations(
      ready: ReadyGame,
      moves: Vector[Move]
  ): Vector[OperationError] =
    moves.groupBy(_.from.location).toVector.flatMap {
      case (location, values) =>
        val amount = values.map(_.piece.asInstanceOf[Piece.Favor].amount).sum
        val piece = Piece.Favor(amount)
        quantity(ready, piece, location) match {
          case Left(error) => Vector(error)
          case Right(available) =>
            finiteSufficiency(piece, location, available, amount)
        }
    }

  private def warbandSourceViolations(
      ready: ReadyGame,
      moves: Vector[Move]
  ): Vector[OperationError] =
    moves.groupBy(move =>
      move.piece.asInstanceOf[Piece.Warbands].kind -> move.from.location)
      .toVector.flatMap { case ((kind, location), values) =>
        val amount = values.map(
          _.piece.asInstanceOf[Piece.Warbands].amount).sum
        val piece = Piece.Warbands(kind, amount)
        quantity(ready, piece, location) match {
          case Left(error) => Vector(error)
          case Right(available) =>
            finiteSufficiency(piece, location, available, amount)
        }
      }

  private def finiteSufficiency(
      piece: Piece,
      location: Location,
      available: AvailableQuantity,
      requested: Int
  ): Vector[OperationError] = available match {
    case AvailableQuantity.Unbounded => Vector.empty
    case AvailableQuantity.Finite(value) =>
      if (value >= requested) Vector.empty
      else Vector(InsufficientPieces(piece, location, value))
  }

  // ------------------------------------------------------------------
  // 4. Pawn and banner move preconditions
  // ------------------------------------------------------------------

  private final case class MovedPieces(
      pawnSites: Map[PlayerId, Option[SiteId]],
      bannerHolders: Map[Banner, Option[PlayerId]]
  )

  private def pawnAndBannerViolations(
      ready: ReadyGame,
      leaves: Vector[Operation]
  ): Vector[OperationError] = {
    val pawnSites = ready.game.current.players.iterator.map { player =>
      player.player -> player.pawnSite
    }.toMap
    val bannerHolders = Map(
      Banner.PeoplesFavor -> bannerHolder(ready, Banner.PeoplesFavor),
      Banner.DarkestSecret -> bannerHolder(ready, Banner.DarkestSecret)
    )
    val (reasons, _) = leaves.foldLeft[(Vector[OperationError],
      MovedPieces)]((Vector.empty, MovedPieces(pawnSites, bannerHolders))) {
      case ((result, state), Move(Piece.Pawn(player), from, to, _)) =>
        val (violations, updated) =
          pawnMoveViolation(ready, player, from.location, to.location, state)
        (result ++ violations, updated)
      case ((result, state), Move(Piece.Banner(banner), from, to, _)) =>
        val (violations, updated) =
          bannerMoveViolation(ready, banner, from.location, to.location, state)
        (result ++ violations, updated)
      case ((result, state), _) => (result, state)
    }
    reasons
  }

  private def pawnMoveViolation(
      ready: ReadyGame,
      player: PlayerId,
      from: Location,
      to: Location,
      state: MovedPieces
  ): (Vector[OperationError], MovedPieces) = (from, to) match {
    case (Location.Site(source), Location.Site(destination)) =>
      playerState(ready, player) match {
        case Left(error) => (Vector(error), state)
        case Right(_) =>
          val located = state.pawnSites.getOrElse(player, None)
          if (!located.contains(source))
            (Vector(MissingPiece(Piece.Pawn(player), from)), state)
          else siteState(ready, destination) match {
            case Left(error) => (Vector(error), state)
            case Right(_) => (Vector.empty, state.copy(
              pawnSites = state.pawnSites.updated(player, Some(destination))))
          }
      }
    case _ =>
      (Vector(IncompatibleLocation(Piece.Pawn(player), to)), state)
  }

  private def bannerMoveViolation(
      ready: ReadyGame,
      banner: Banner,
      from: Location,
      to: Location,
      state: MovedPieces
  ): (Vector[OperationError], MovedPieces) = (from, to) match {
    case (Location.PlayArea(source), Location.PlayArea(destination)) =>
      playerState(ready, destination) match {
        case Left(error) => (Vector(error), state)
        case Right(_) =>
          val holder = state.bannerHolders.getOrElse(banner, None)
          if (!holder.contains(source))
            (Vector(MissingPiece(Piece.Banner(banner), from)), state)
          else (Vector.empty, state.copy(
            bannerHolders = state.bannerHolders.updated(banner,
              Some(destination))))
      }
    case (Location.SharedBank, Location.PlayArea(destination)) =>
      playerState(ready, destination) match {
        case Left(error) => (Vector(error), state)
        case Right(_) =>
          val holder = state.bannerHolders.getOrElse(banner, None)
          if (holder.isEmpty)
            (Vector.empty, state.copy(
              bannerHolders = state.bannerHolders.updated(banner,
                Some(destination))))
          else (Vector(InsufficientPieces(Piece.Banner(banner), from, 0)),
            state)
      }
    case _ =>
      (Vector(IncompatibleLocation(Piece.Banner(banner), to)), state)
  }

  // ------------------------------------------------------------------
  // 5. Non-move primitive guards
  // ------------------------------------------------------------------

  private final case class RunningBoards(
      faceUp: Map[PlayerId, Int],
      faceDown: Map[PlayerId, Int],
      supply: Map[PlayerId, Int]
  )

  private object RunningBoards {
    def initial(
        ready: ReadyGame,
        planned: Option[Vector[(Move, OperationSecretPlanner.SecretSplit)]]
    ): RunningBoards = {
      val boards = ready.game.current.players.iterator.map { player =>
        (player.player, (player.board.faceUpSecrets,
          player.board.faceDownSecrets, player.board.supply.supply))
      }.toMap
      val deltas = planned match {
        case None => Map.empty[PlayerId, (Int, Int)]
        case Some(values) => values.foldLeft(Map.empty[PlayerId, (Int, Int)]) {
          case (accumulated, (move, split)) =>
            val fromAdjusted = move.from.location match {
              case Location.PlayArea(player) => addDelta(accumulated, player,
                -split.faceUp, -split.faceDown)
              case _ => accumulated
            }
            move.to.location match {
              case Location.PlayArea(player) => addDelta(fromAdjusted, player,
                split.faceUp, split.faceDown)
              case _ => fromAdjusted
            }
        }
      }
      RunningBoards(
        faceUp = boards.iterator.map { case (player, (up, _, _)) =>
          player -> (up + deltas.getOrElse(player, (0, 0))._1)
        }.toMap,
        faceDown = boards.iterator.map { case (player, (_, down, _)) =>
          player -> (down + deltas.getOrElse(player, (0, 0))._2)
        }.toMap,
        supply = boards.iterator.map { case (player, (_, _, value)) =>
          player -> value
        }.toMap
      )
    }

    private def addDelta(
        deltas: Map[PlayerId, (Int, Int)],
        player: PlayerId,
        faceUp: Int,
        faceDown: Int
    ): Map[PlayerId, (Int, Int)] = {
      val current = deltas.getOrElse(player, (0, 0))
      deltas.updated(player, (current._1 + faceUp, current._2 + faceDown))
    }
  }

  private def nonMoveViolations(
      ready: ReadyGame,
      leaves: Vector[Operation],
      plannedSecrets: Option[Vector[(Move,
        OperationSecretPlanner.SecretSplit)]]
  ): Vector[OperationError] = {
    val initial = RunningBoards.initial(ready, plannedSecrets)
    val (reasons, _) = leaves.foldLeft[(Vector[OperationError],
      RunningBoards)]((Vector.empty, initial)) {
      case ((result, state), Flip(id, at, _)) =>
        (result ++ flipViolation(ready, id, at), state)
      case ((result, state), FlipSecrets(player, amount, from, to)) =>
        val (violations, updated) =
          flipSecretsViolation(ready, player, amount, from, to, state)
        (result ++ violations, updated)
      case ((result, state), Peek(viewer, id, at)) =>
        (result ++ peekViolation(ready, viewer, id, at), state)
      case ((result, state), SpendSupply(player, amount, _)) =>
        val (violations, updated) =
          adjustSupplyViolation(ready, player, -amount, state)
        (result ++ violations, updated)
      case ((result, state), GainSupply(player, amount)) =>
        val (violations, updated) =
          adjustSupplyViolation(ready, player, amount, state)
        (result ++ violations, updated)
      case ((result, state), AdvanceVisionsDrawn) =>
        (result ++ Option.when(ready.game.current.tracks.visionsDrawn ==
          Int.MaxValue)(OperationError.VisionsDrawnOverflow), state)
      case ((result, state), _) => (result, state)
    }
    reasons
  }

  private def flipViolation(
      ready: ReadyGame,
      id: CardId,
      at: Location
  ): Vector[OperationError] = card(ready, id, at) match {
    case Left(error) => Vector(error)
    case Right(located) => located.state match {
      case Some(_: DenizenState) | Some(_: VisionState) |
          Some(_: RelicState) => Vector.empty
      case _ => Vector(UnsupportedOrientation(id, at))
    }
  }

  private def flipSecretsViolation(
      ready: ReadyGame,
      player: PlayerId,
      amount: Int,
      from: SecretSide,
      to: SecretSide,
      state: RunningBoards
  ): (Vector[OperationError], RunningBoards) =
    playerState(ready, player) match {
      case Left(error) => (Vector(error), state)
      case Right(_) =>
        val available = from match {
          case SecretSide.FaceUp => state.faceUp.getOrElse(player, 0)
          case SecretSide.FaceDown => state.faceDown.getOrElse(player, 0)
        }
        if (available < amount)
          (Vector(InsufficientPieces(Piece.Secrets(amount),
            Location.PlayArea(player), available)), state)
        else {
          val faceUpDelta = (from, to) match {
            case (SecretSide.FaceUp, SecretSide.FaceDown) => -amount
            case (SecretSide.FaceDown, SecretSide.FaceUp) => amount
            case _ => 0
          }
          val updated = state.copy(
            faceUp = state.faceUp.updated(player,
              state.faceUp.getOrElse(player, 0) + faceUpDelta),
            faceDown = state.faceDown.updated(player,
              state.faceDown.getOrElse(player, 0) - faceUpDelta)
          )
          (Vector.empty, updated)
        }
    }

  private def peekViolation(
      ready: ReadyGame,
      viewer: PlayerId,
      id: CardId,
      at: Location
  ): Vector[OperationError] = playerState(ready, viewer) match {
    case Left(error) => Vector(error)
    case Right(_) =>
      val kindViolation: Vector[OperationError] =
        if (id.isInstanceOf[WorldCardId] || id.isInstanceOf[RelicId])
          Vector.empty
        else Vector(IncompatibleLocation(Piece.Card(id), at))
      kindViolation ++ (card(ready, id, at) match {
        case Left(error) => Vector(error)
        case Right(_) => Vector.empty
      })
  }

  private def adjustSupplyViolation(
      ready: ReadyGame,
      player: PlayerId,
      amount: Int,
      state: RunningBoards
  ): (Vector[OperationError], RunningBoards) =
    playerState(ready, player) match {
      case Left(error) => (Vector(error), state)
      case Right(_) =>
        val current = state.supply.getOrElse(player, 0)
        if (amount < 0) {
          val required = -amount
          if (current >= required) (Vector.empty, state.copy(
            supply = state.supply.updated(player, current - required)))
          else (Vector(InsufficientSupply(required, current)), state)
        } else (Vector.empty, state.copy(
          supply = state.supply.updated(player,
            math.min(SupplyTrack.Maximum, current + amount))))
    }
}
