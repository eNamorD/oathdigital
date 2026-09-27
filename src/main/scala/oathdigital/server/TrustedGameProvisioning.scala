package oathdigital.server

import java.net.URI
import java.security.SecureRandom
import scala.util.Try
import scala.util.control.NonFatal
import oathdigital.application._
import oathdigital.protocol._

enum TrustedGameFailure { case InvalidRequest, DuplicateGame, CodeCollision, StorageFailure }

final class TrustedGameProvisioning(
    service: GameApplicationService,
    planFactory: FirstGamePlanFactory,
    store: TrustedGameStore,
    generateCode: () => SeatCode = () => TrustedGameProvisioning.generateCode(),
    nowMillis: () => Long = () => System.currentTimeMillis(),
    generateGameId: () => String = () => TrustedGameProvisioning.generateGameId()
):
  import TrustedGameFailure._

  def create(request: TrustedGameCreateRequest, publicBaseUrl: String)
      : Either[TrustedGameFailure, TrustedGameCreateResponse] =
    try
      for
        valid <- TrustedGameCreateRequestCodec.decode(TrustedGameCreateRequestCodec.encode(request))
          .left.map(_ => InvalidRequest)
        origin <- validatedOrigin(publicBaseUrl)
        // The plan factory shuffles the seating and picks the first player,
        // so the first participant is only a placeholder.
        config = FirstGameBootstrapMapper.map(FirstGameBootstrapRequest(
          0L, valid.participants, valid.participants.head.playerId))
        plan <- planFactory.build(config).left.map(_ => InvalidRequest)
        placed <- place(valid.participants, plan, None, 1)
        (gameId, codes) = placed
      yield TrustedGameCreateResponse(gameId, codes.map { case (player, code) =>
        TrustedSeatLink(player, s"$origin/s/${code.raw}") })
    catch { case NonFatal(_) => Left(StorageFailure) }

  /** Stores the game under a freshly drawn ID. A taken ID is drawn again, up
    * to `MaxGameIdAttempts` draws, keeping the seat codes already generated.
    */
  private def place(participants: Vector[BootstrapParticipantRequest],
      plan: FirstGamePlan, seats: Option[Vector[(String, SeatCode)]], attempt: Int)
      : Either[TrustedGameFailure, (String, Vector[(String, SeatCode)])] =
    val gameId = generateGameId()
    for
      prepared <- service.prepareBootstrap(gameId, plan.chronicle,
        plan.resolvedConfig).left.map:
        case _: GameApplicationError.CommandRejected => InvalidRequest
        case _ => StorageFailure
      codes <- seats.fold(generateSeats(participants))(Right(_))
      placed <- store.create(gameId, codes.map { case (player, code) =>
          code.digest -> player }, prepared.records, nowMillis()) match
        case Left(TrustedGameStoreFailure.DuplicateGame)
            if attempt < TrustedGameProvisioning.MaxGameIdAttempts =>
          place(participants, plan, Some(codes), attempt + 1)
        case Left(TrustedGameStoreFailure.DuplicateGame) => Left(DuplicateGame)
        case Left(TrustedGameStoreFailure.CodeCollision) => Left(CodeCollision)
        case Left(TrustedGameStoreFailure.InvalidInput) => Left(InvalidRequest)
        case Left(TrustedGameStoreFailure.StorageFailure) => Left(StorageFailure)
        case Right(()) => Right(gameId -> codes)
    yield placed

  private def generateSeats(participants: Vector[BootstrapParticipantRequest])
      : Either[TrustedGameFailure, Vector[(String, SeatCode)]] =
    var used = Set.empty[SeatCodeDigest]
    val seats = Vector.newBuilder[(String, SeatCode)]
    val remaining = participants.iterator
    while remaining.hasNext do
      val participant = remaining.next()
      var selected = Option.empty[SeatCode]
      var attempts = 0
      while selected.isEmpty && attempts < 8 do
        val code = generateCode()
        attempts += 1
        if !used.contains(code.digest) then selected = Some(code)
      selected match
        case None => return Left(CodeCollision)
        case Some(code) =>
          used += code.digest
          seats += participant.playerId -> code
    Right(seats.result())

  private def validatedOrigin(value: String): Either[TrustedGameFailure, String] =
    Try(new URI(value)).toOption.filter { uri =>
      uri.isAbsolute && !uri.isOpaque &&
        Option(uri.getScheme).exists(scheme => Set("http", "https").contains(scheme.toLowerCase)) &&
        Option(uri.getHost).exists(_.nonEmpty) && uri.getPort >= -1 && uri.getPort <= 65535 &&
        uri.getRawUserInfo == null && uri.getRawQuery == null && uri.getRawFragment == null &&
        Option(uri.getRawPath).forall(_.isEmpty)
    }.map(_.toString).toRight(InvalidRequest)

object TrustedGameProvisioning:
  val MaxGameIdAttempts: Int = 8
  private val GameIdAlphabet = "abcdefghijklmnopqrstuvwxyz234567"
  private lazy val random = new SecureRandom()
  private def generateCode(): SeatCode = SeatCode.generate(random)

  /** "game-" and 12 random lowercase base32 characters: 60 bits, within the
    * identifier rule of `TrustedGameCodecFields.identifier`. */
  def generateGameId(): String =
    "game-" + Vector.fill(12)(GameIdAlphabet(random.nextInt(GameIdAlphabet.length))).mkString
