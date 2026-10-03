package oathdigital.frontend

import oathdigital.model.PlayerColor

import scala.concurrent.Future

/** Fixtures the ServerModeUi suites share. */
private[frontend] object ServerModeFixtures:
  /** Answers every log request with an empty page and passes every other
    * request to `inner`, so a test that counts, lists or queues requests sees
    * only the requests it is about. */
  def quietLog(inner: JsonTransport): JsonTransport = new JsonTransport:
    def request(method: String, url: String, body: Option[String])
        : Future[Either[GameClientFailure, TransportResponse]] =
      if url.contains("/log/") then Future.successful(Right(TransportResponse(200,
        """{"gameId":"quiet","after":0,"nextSequence":0,"entries":[]}""")))
      else inner.request(method, url, body)

  def projection(
      legalControls: Set[String],
      phase: String = "wake",
      activeParticipantId: String = "red-exile",
      ready: Boolean = true,
      phasePowers: Vector[PhasePowerState] = Vector.empty
  ): GameProjection =
    GameProjection(
      gameId = "game-1",
      nextSequence = 8L,
      phase = phase,
      activeParticipantId = Some(activeParticipantId),
      players = Vector(
        GamePlayer(
          "red-exile",
          "Red Exile",
          "exile",
          PlayerColor.Red
        ),
        GamePlayer(
          "blue-exile",
          "Blue Exile",
          "exile",
          PlayerColor.Blue
        )
      ),
      world = Vector.empty,
      pawnLocations = Vector.empty,
      legalControls = legalControls.toVector.sorted,
      ready = ready,
      completed = false,
      phasePowers = phasePowers
    )

  /** Task 5: Forge is driven end to end through the shared two-zone
    * interaction. The sections carrying their own labels and minima, the
    * denizen options carrying their own references, and the answer is
    * assembled by generic code -- nothing below states Forge's printed
    * cost, and nothing names a resource.
    */
  val forgeQuery = DecisionQueryState.Partition(
    Vector(DecisionSectionState("pay-favor", "Pay Favor", 2),
      DecisionSectionState("pay-secret", "Pay Secret", 1)),
    Vector("1", "2", "3").map(id =>
      DecisionOptionState("denizen", s"denizen:$id", s"Denizen $id")))

  val forgeParked = WalkerDecisionState("forge", "forge-9", "decide",
    query = Some(forgeQuery))
