package oathdigital.frontend

import oathdigital.model.PlayerColor

import scala.concurrent.Future

/** The fixtures that the ServerMode* suites share: the log-filtering
  * transport and the two-seat projection.
  */
private[frontend] object ServerModeFixtures:
  /** Stub of `JsonTransport` over `inner`: answers every log request with an
    * empty page, a canned answer it does not record, and passes every other
    * request to `inner`, so a test that counts, lists or queues requests sees
    * only the requests it is about. */
  def withEmptyLogPage(inner: JsonTransport): JsonTransport =
    new JsonTransport:
      def request(method: String, url: String, body: Option[String])
          : Future[Either[GameClientFailure, TransportResponse]] =
        if url.contains("/log/") then
          Future.successful(Right(TransportResponse(200,
            """{"gameId":"quiet","after":0,"nextSequence":0,"entries":[]}""")))
        else inner.request(method, url, body)

  /** The fixed two-seat game "game-1" at sequence 8, at `phase` with exactly
    * `legalControls` legal. Its seats are "red-exile" (shown as "Red Exile",
    * Red) and "blue-exile" (shown as "Blue Exile", Blue);
    * `activeParticipantId` says whose turn it is, red-exile unless a test
    * names another. A test that depends on the phase passes it, because the
    * default, "wake", is only a default.
    */
  def twoSeatProjection(
      legalControls: Set[String],
      phase: String = "wake",
      activeParticipantId: String = "red-exile",
      ready: Boolean = true,
      phasePowers: Vector[PhasePowerState] = Vector.empty
  ): GameProjection =
    TestProjections.actingProjection(
      gameId = "game-1",
      nextSequence = 8L,
      phase = phase,
      activePlayer = Some(activeParticipantId),
      players = Vector(
        GamePlayer("red-exile", "Red Exile", "exile", PlayerColor.Red),
        GamePlayer("blue-exile", "Blue Exile", "exile", PlayerColor.Blue)),
      legalControls = legalControls.toVector.sorted,
      ready = ready
    ).copy(phasePowers = phasePowers)
