package oathdigital.server

import java.net.URI
import java.security.SecureRandom
import scala.util.Try
import scala.util.control.NonFatal
import oathdigital.application._
import oathdigital.protocol._

sealed trait TrustedGameFailure extends Product with Serializable
object TrustedGameFailure {
  case object InvalidRequest extends TrustedGameFailure
  case object DuplicateGame extends TrustedGameFailure
  case object CodeCollision extends TrustedGameFailure
  case object StorageFailure extends TrustedGameFailure
}

final class TrustedGameProvisioning(
    service: GameApplicationService,
    planFactory: FirstGamePlanFactory,
    store: TrustedGameStore,
    generateCode: () => SeatCode = () => TrustedGameProvisioning.generateCode(),
    nowMillis: () => Long = () => System.currentTimeMillis()
) {
  import TrustedGameFailure._

  def create(request: TrustedGameCreateRequest, publicBaseUrl: String)
      : Either[TrustedGameFailure, TrustedGameCreateResponse] =
    try {
      for {
        valid <- TrustedGameCreateRequestCodec.decode(TrustedGameCreateRequestCodec.encode(request))
          .left.map(_ => InvalidRequest)
        origin <- validatedOrigin(publicBaseUrl)
        plan <- planFactory.build(FirstGameBootstrapMapper.map(FirstGameBootstrapRequest(
          0L, valid.participants, valid.firstPlayerId))).left.map(_ => InvalidRequest)
        prepared <- service.prepareBootstrap(valid.gameId, plan).left.map {
          case _: GameApplicationError.CommandRejected => InvalidRequest
          case _ => StorageFailure
        }
        codes <- generateSeats(valid.participants)
        _ <- store.create(valid.gameId, codes.map { case (player, code) =>
          code.digest -> player }, prepared.records, nowMillis()).left.map {
          case TrustedGameStoreFailure.DuplicateGame => DuplicateGame
          case TrustedGameStoreFailure.CodeCollision => CodeCollision
          case TrustedGameStoreFailure.InvalidInput => InvalidRequest
          case TrustedGameStoreFailure.StorageFailure => StorageFailure
        }
      } yield TrustedGameCreateResponse(valid.gameId, codes.map { case (player, code) =>
        TrustedSeatLink(player, s"$origin/s/${code.raw}") })
    } catch { case NonFatal(_) => Left(StorageFailure) }

  private def generateSeats(participants: Vector[BootstrapParticipantRequest])
      : Either[TrustedGameFailure, Vector[(String, SeatCode)]] = {
    var used = Set.empty[SeatCodeDigest]
    val seats = Vector.newBuilder[(String, SeatCode)]
    val remaining = participants.iterator
    while (remaining.hasNext) {
      val participant = remaining.next()
      var selected = Option.empty[SeatCode]
      var attempts = 0
      while (selected.isEmpty && attempts < 8) {
        val code = generateCode()
        attempts += 1
        if (!used.contains(code.digest)) selected = Some(code)
      }
      selected match {
        case None => return Left(CodeCollision)
        case Some(code) =>
          used += code.digest
          seats += participant.playerId -> code
      }
    }
    Right(seats.result())
  }

  private def validatedOrigin(value: String): Either[TrustedGameFailure, String] =
    Try(new URI(value)).toOption.filter { uri =>
      uri.isAbsolute && !uri.isOpaque &&
        Option(uri.getScheme).exists(scheme => Set("http", "https").contains(scheme.toLowerCase)) &&
        Option(uri.getHost).exists(_.nonEmpty) && uri.getPort >= -1 && uri.getPort <= 65535 &&
        uri.getRawUserInfo == null && uri.getRawQuery == null && uri.getRawFragment == null &&
        Option(uri.getRawPath).forall(_.isEmpty)
    }.map(_.toString).toRight(InvalidRequest)
}

object TrustedGameProvisioning {
  private lazy val random = new SecureRandom()
  private def generateCode(): SeatCode = SeatCode.generate(random)
}
