package oathdigital.application

import oathdigital.model.{LineageId, PlayerColor, PlayerId}
import oathdigital.protocol.{BootstrapParticipantRequest, FirstGameBootstrapRequest}

class FirstGameBootstrapMapperSuite extends munit.FunSuite:
  test("each participant's lineage is derived from its color"):
    val config = FirstGameBootstrapMapper.map(FirstGameBootstrapRequest(0L, Vector(
      BootstrapParticipantRequest("p1", PlayerColor.Red),
      BootstrapParticipantRequest("p2", PlayerColor.Brown)), "p1"))
    assertEquals(config.participants.map(p => p.playerId -> p.lineageId), Vector(
      PlayerId("p1") -> LineageId("red-lineage"),
      PlayerId("p2") -> LineageId("brown-lineage")))
