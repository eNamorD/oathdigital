package oathdigital.gameplay.operations

import oathdigital.gameplay.OathViolation
import oathdigital.model._

sealed trait OperationError extends Product with Serializable {
  def code: String
  def detail: String

  final def toViolation: OathViolation =
    OathViolation.CoreOperationRejected(code, detail)
}

object OperationError {
  case object EmptyOperationBatch extends OperationError {
    override val code: String = "empty-operation-batch"
    override val detail: String = "operation batch must not be empty"
  }

  final case class InvalidDescription(detail: String) extends OperationError {
    override val code: String = "invalid-description"
  }

  final case class MissingPiece(piece: Piece, location: Location)
      extends OperationError {
    override val code: String = "missing-piece"
    override val detail: String =
      s"${pieceSummary(piece)} is not present at ${locationSummary(location)}"
  }

  final case class InsufficientPieces(
      piece: Piece,
      location: Location,
      available: Int
  ) extends OperationError {
    override val code: String = "insufficient-pieces"
    override val detail: String =
      s"${locationSummary(location)} contains $available of requested ${pieceSummary(piece)}"
  }

  final case class InsufficientSupply(required: Int, available: Int)
      extends OperationError {
    override val code: String = "insufficient-supply"
    override val detail: String =
      s"a supply spend of $required exceeds the $available available"
  }

  final case class IncompatibleLocation(piece: Piece, location: Location)
      extends OperationError {
    override val code: String = "incompatible-location"
    override val detail: String =
      s"${locationSummary(location)} cannot contain ${pieceSummary(piece)}"
  }

  final case class AmbiguousLocation(location: Location, detail: String)
      extends OperationError {
    override val code: String = "ambiguous-location"
  }

  final case class InvalidStackPosition(location: Location, detail: String)
      extends OperationError {
    override val code: String = "invalid-stack-position"
  }

  final case class InvalidDestination(piece: Piece, location: Location)
      extends OperationError {
    override val code: String = "invalid-destination"
    override val detail: String =
      s"${locationSummary(location)} cannot receive ${pieceSummary(piece)}"
  }

  final case class MissingOrientation(card: CardId, location: Location)
      extends OperationError {
    override val code: String = "missing-orientation"
    override val detail: String =
      s"card kind ${card.kind} requires orientation at $location"
  }

  final case class UnsupportedOrientation(card: CardId, location: Location)
      extends OperationError {
    override val code: String = "unsupported-orientation"
    override val detail: String =
      s"card kind ${card.kind} cannot use orientation at $location"
  }

  final case class AmbiguousSecretOrientation(location: Location, amount: Int)
      extends OperationError {
    override val code: String = "ambiguous-secret-orientation"
    override val detail: String =
      s"$amount secrets at $location do not have one valid orientation split"
  }

  final case class ConflictingDeltas(detail: String) extends OperationError {
    override val code: String = "conflicting-deltas"
  }

  final case class RestrictedOperation(detail: String) extends OperationError {
    override val code: String = "restricted-operation"
  }

  final case class InvalidPostState(problems: Vector[DomainProblem])
      extends OperationError {
    override val code: String = "invalid-post-state"
    override val detail: String =
      s"operation state failed ${problems.size} aggregate invariant checks"
  }

  final case class CardInventoryChanged(
      missing: Set[CardId],
      unexpected: Set[CardId]
  ) extends OperationError {
    override val code: String = "card-inventory-changed"
    override val detail: String =
      s"card inventory changed: ${missing.size} missing, ${unexpected.size} unexpected"
  }

  final case class UnknownCard(card: CardId) extends OperationError {
    override val code: String = "unknown-card"
    override val detail: String = s"requested ${card.kind} card has no location"
  }

  final case class UnknownPlayer(player: PlayerId) extends OperationError {
    override val code: String = "unknown-player"
    override val detail: String = s"player ${player.value} is not current"
  }

  final case class UnknownSite(site: SiteId) extends OperationError {
    override val code: String = "unknown-site"
    override val detail: String = s"site ${site.value} is not in play"
  }

  final case class InvalidCardIndex(problems: Vector[CardIndexProblem])
      extends OperationError {
    override val code: String = "invalid-card-index"
    override val detail: String =
      s"card index failed ${problems.size} structural checks"
  }

  final case class UnknownWarbandSupply(kind: ForceKind)
      extends OperationError {
    override val code: String = "unknown-warband-supply"
    override val detail: String = s"no bounded supply is defined for $kind"
  }

  final case class InvalidWarbandInventory(
      kind: ForceKind,
      supply: Int,
      inPlay: Int
  ) extends OperationError {
    override val code: String = "invalid-warband-inventory"
    override val detail: String =
      s"$kind has $inPlay warbands in play but bounded supply is $supply"
  }

  def describe[A](value: => A): Either[OperationError, A] =
    try Right(value)
    catch {
      case error: IllegalArgumentException =>
        val message = Option(error.getMessage).getOrElse("invalid core operation")
        Left(InvalidDescription(message.stripPrefix("requirement failed: ")))
    }

  private def pieceSummary(piece: Piece): String = piece match {
    case Piece.Card(id) => s"${id.kind} card"
    case Piece.Banner(_) => "banner"
    case Piece.Pawn(_) => "pawn"
    case Piece.Favor(_) => "favor"
    case Piece.Secrets(_) => "secrets"
    case Piece.Warbands(_, _) => "warbands"
  }

  private def locationSummary(location: Location): String = location match {
    case Location.Hand(_) => "temporary hand"
    case Location.PlayArea(_) => "player play area"
    case Location.Site(_) => "site"
    case Location.OnCard(_) => "card"
    case Location.OnBanner(_) => "banner"
    case Location.FavorBank(_) => "favor bank"
    case Location.WarbandBank(_) => "warband bank"
    case Location.Deck(_) => "deck"
    case Location.RegionalDiscard(_) => "regional discard"
    case Location.SharedBank => "shared bank"
    case Location.SetAsideRelics => "set-aside relics"
    case Location.Reliquary => "Reliquary"
    case Location.Atlas => "Atlas"
    case Location.Dispossessed => "Dispossessed"
  }

}
