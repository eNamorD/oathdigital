package oathdigital.setup

import oathdigital.catalog.ExecutableCatalog
import oathdigital.engine.EventEvolution
import oathdigital.model.{CatalogRef, LineageId, PlayerId, SiteId}

final case class SetupParticipant(playerId: PlayerId, lineageId: LineageId)

final case class SetupLayout(
    cradle: Vector[SiteId],
    provinces: Vector[SiteId],
    hinterland: Vector[SiteId]
) {
  def sites: Vector[SiteId] = cradle ++ provinces ++ hinterland
  def contains(siteId: SiteId): Boolean = sites.contains(siteId)
}

object SetupLayout {
  def fromOrderedSites(sites: Vector[SiteId]): SetupLayout =
    SetupLayout(sites.take(2), sites.slice(2, 5), sites.slice(5, 8))
}

final case class PawnPlacement(playerId: PlayerId, siteId: SiteId)

sealed trait SetupState extends Product with Serializable
object SetupState {
  case object NotStarted extends SetupState

  final case class InProgress(
      participants: Vector[SetupParticipant],
      catalog: CatalogRef,
      layout: SetupLayout,
      pawnPlacements: Vector[PawnPlacement]
  ) extends SetupState

  final case class Completed(
      participants: Vector[SetupParticipant],
      catalog: CatalogRef,
      layout: SetupLayout,
      pawnPlacements: Vector[PawnPlacement]
  ) extends SetupState
}

sealed trait SetupCommand extends Product with Serializable
object SetupCommand {
  final case class BeginSetup(
      participants: Vector[SetupParticipant],
      catalog: CatalogRef,
      orderedSites: Vector[SiteId]
  ) extends SetupCommand

  final case class PlacePawn(playerId: PlayerId, siteId: SiteId)
      extends SetupCommand
}

sealed trait SetupEvent extends Product with Serializable
object SetupEvent {
  final case class SetupStarted(
      participants: Vector[SetupParticipant],
      catalog: CatalogRef,
      orderedSites: Vector[SiteId]
  ) extends SetupEvent

  final case class PawnPlaced(playerId: PlayerId, siteId: SiteId)
      extends SetupEvent

  case object SetupCompleted extends SetupEvent
}

sealed trait SetupContinue extends Product with Serializable
object SetupContinue {
  final case class AwaitingPawn(playerId: PlayerId) extends SetupContinue
  case object Finished extends SetupContinue
}

final case class SetupTransition(
    state: SetupState,
    events: Vector[SetupEvent],
    continue: SetupContinue
)

sealed trait SetupViolation extends Product with Serializable
object SetupViolation {
  case object AlreadyStarted extends SetupViolation
  case object NotStarted extends SetupViolation
  case object AlreadyCompleted extends SetupViolation
  final case class IncompatibleCatalog(
      expected: CatalogRef,
      actual: CatalogRef
  ) extends SetupViolation
  case object ParticipantsEmpty extends SetupViolation
  final case class DuplicatePlayer(playerId: PlayerId) extends SetupViolation
  final case class DuplicateLineage(lineageId: LineageId)
      extends SetupViolation
  final case class InvalidSiteCount(actual: Int) extends SetupViolation
  final case class DuplicateSite(siteId: SiteId) extends SetupViolation
  final case class UnknownSite(siteId: SiteId) extends SetupViolation
  final case class WrongPlayer(expected: PlayerId, actual: PlayerId)
      extends SetupViolation
  final case class SiteNotInPlay(siteId: SiteId) extends SetupViolation
  final case class InvalidCompletion(
      expectedPlacements: Int,
      actualPlacements: Int
  ) extends SetupViolation
}

final class SetupRules(catalog: ExecutableCatalog)
    extends EventEvolution[SetupState, SetupEvent, SetupViolation] {
  import SetupCommand._
  import SetupContinue._
  import SetupEvent._
  import SetupState._
  import SetupViolation._

  private val knownSites: Set[SiteId] = catalog.sites.map(_.id).toSet

  override val initialState: SetupState = SetupState.NotStarted

  def handle(
      state: SetupState,
      command: SetupCommand
  ): Either[SetupViolation, SetupTransition] =
    command match {
      case BeginSetup(participants, catalogRef, orderedSites) =>
        state match {
          case SetupState.NotStarted =>
            validateStart(participants, catalogRef, orderedSites).flatMap {
              _ =>
                applyEvents(
                  state,
                  Vector(
                    SetupStarted(participants, catalogRef, orderedSites)
                  )
                ).map { next =>
                  SetupTransition(
                    next,
                    Vector(
                      SetupStarted(participants, catalogRef, orderedSites)
                    ),
                    AwaitingPawn(participants.head.playerId)
                  )
                }
            }
          case _ => Left(AlreadyStarted)
        }

      case PlacePawn(playerId, siteId) =>
        state match {
          case SetupState.NotStarted => Left(SetupViolation.NotStarted)
          case _: Completed => Left(AlreadyCompleted)
          case progress: InProgress =>
            val placementIndex = progress.pawnPlacements.size
            if (placementIndex >= progress.participants.size)
              Left(
                InvalidCompletion(
                  progress.participants.size,
                  placementIndex
                )
              )
            else {
              val expected =
                progress.participants(placementIndex).playerId
              if (playerId != expected) Left(WrongPlayer(expected, playerId))
              else if (!knownSites.contains(siteId)) Left(UnknownSite(siteId))
              else if (!progress.layout.contains(siteId))
                Left(SiteNotInPlay(siteId))
              else {
                val placed = PawnPlaced(playerId, siteId)
                val isFinal =
                  progress.pawnPlacements.size + 1 ==
                    progress.participants.size
                val emitted =
                  if (isFinal) Vector(placed, SetupCompleted)
                  else Vector(placed)
                applyEvents(state, emitted).map { next =>
                  val continuation =
                    if (isFinal) Finished
                    else
                      AwaitingPawn(
                        progress
                          .participants(progress.pawnPlacements.size + 1)
                          .playerId
                      )
                  SetupTransition(next, emitted, continuation)
                }
              }
            }
        }
    }

  override def evolve(
      state: SetupState,
      event: SetupEvent
  ): Either[SetupViolation, SetupState] =
    event match {
      case SetupStarted(participants, catalogRef, orderedSites) =>
        state match {
          case SetupState.NotStarted =>
            validateStart(participants, catalogRef, orderedSites).map { _ =>
              InProgress(
                participants,
                catalogRef,
                SetupLayout.fromOrderedSites(orderedSites),
                Vector.empty
              )
            }
          case _ => Left(AlreadyStarted)
        }

      case PawnPlaced(playerId, siteId) =>
        state match {
          case SetupState.NotStarted => Left(SetupViolation.NotStarted)
          case _: Completed => Left(AlreadyCompleted)
          case progress: InProgress =>
            val placementIndex = progress.pawnPlacements.size
            if (placementIndex >= progress.participants.size)
              Left(
                InvalidCompletion(
                  progress.participants.size,
                  placementIndex
                )
              )
            else {
              val expected = progress.participants(placementIndex).playerId
              if (playerId != expected) Left(WrongPlayer(expected, playerId))
              else if (!knownSites.contains(siteId)) Left(UnknownSite(siteId))
              else if (!progress.layout.contains(siteId))
                Left(SiteNotInPlay(siteId))
              else
                Right(
                  progress.copy(
                    pawnPlacements =
                      progress.pawnPlacements :+ PawnPlacement(playerId, siteId)
                  )
                )
            }
        }

      case SetupCompleted =>
        state match {
          case SetupState.NotStarted => Left(SetupViolation.NotStarted)
          case _: Completed => Left(AlreadyCompleted)
          case progress: InProgress =>
            if (
              progress.pawnPlacements.size != progress.participants.size
            )
              Left(
                InvalidCompletion(
                  progress.participants.size,
                  progress.pawnPlacements.size
                )
              )
            else
              Right(
                Completed(
                  progress.participants,
                  progress.catalog,
                  progress.layout,
                  progress.pawnPlacements
                )
              )
        }
    }

  private def validateStart(
      participants: Vector[SetupParticipant],
      catalogRef: CatalogRef,
      orderedSites: Vector[SiteId]
  ): Either[SetupViolation, Unit] = {
    val duplicatePlayer = firstDuplicate(participants.map(_.playerId))
    val duplicateLineage = firstDuplicate(participants.map(_.lineageId))
    val duplicateSite = firstDuplicate(orderedSites)

    if (catalogRef != catalog.ref)
      Left(IncompatibleCatalog(catalog.ref, catalogRef))
    else if (participants.isEmpty) Left(ParticipantsEmpty)
    else if (duplicatePlayer.nonEmpty)
      Left(DuplicatePlayer(duplicatePlayer.get))
    else if (duplicateLineage.nonEmpty)
      Left(DuplicateLineage(duplicateLineage.get))
    else if (orderedSites.size != 8)
      Left(InvalidSiteCount(orderedSites.size))
    else if (duplicateSite.nonEmpty)
      Left(DuplicateSite(duplicateSite.get))
    else
      orderedSites.find(site => !knownSites.contains(site)) match {
        case Some(site) => Left(UnknownSite(site))
        case None => Right(())
      }
  }

  private def firstDuplicate[A](values: Vector[A]): Option[A] = {
    val seen = scala.collection.mutable.HashSet.empty[A]
    values.find(value => !seen.add(value))
  }

  private def applyEvents(
      state: SetupState,
      events: Vector[SetupEvent]
  ): Either[SetupViolation, SetupState] =
    events.foldLeft[Either[SetupViolation, SetupState]](Right(state)) {
      case (Right(current), event) => evolve(current, event)
      case (failure @ Left(_), _) => failure
    }
}
