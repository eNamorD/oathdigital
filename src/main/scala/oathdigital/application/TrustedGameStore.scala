package oathdigital.application

enum TrustedGameStoreFailure { case DuplicateGame, CodeCollision, InvalidInput, StorageFailure }

/** Commits the identity resource, seats and initial journal as one transaction. */
trait TrustedGameStore {
  def create(
      gameId: String,
      seats: Vector[(SeatCodeDigest, String)],
      preparedRecords: Vector[String],
      nowMillis: Long
  ): Either[TrustedGameStoreFailure, Unit]
}
