package oathdigital.application

sealed trait TrustedGameStoreFailure extends Product with Serializable
object TrustedGameStoreFailure {
  case object DuplicateGame extends TrustedGameStoreFailure
  case object CodeCollision extends TrustedGameStoreFailure
  case object InvalidInput extends TrustedGameStoreFailure
  case object StorageFailure extends TrustedGameStoreFailure
}

/** Commits the identity resource, seats and initial journal as one transaction. */
trait TrustedGameStore {
  def create(
      gameId: String,
      seats: Vector[(SeatCodeDigest, String)],
      preparedRecords: Vector[String],
      nowMillis: Long
  ): Either[TrustedGameStoreFailure, Unit]
}
