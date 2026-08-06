package oathdigital.server

class AuthenticatedFirstGameHttpWireSuite extends munit.FunSuite {
  test("authenticated command intents contain no actor field") {
    val valid =
      """{"expectedNextSequence":1,"intent":{"type":"placePawn","siteId":"S1"}}"""
    assert(AuthenticatedFirstGameHttpWire.decodeCommand(valid).isRight)

    val impersonation =
      """{"expectedNextSequence":1,"intent":{"type":"placePawn","playerId":"p2","siteId":"S1"}}"""
    assertEquals(
      AuthenticatedFirstGameHttpWire.decodeCommand(impersonation)
        .left.toOption.get.path,
      "$.intent.playerId"
    )
  }

  test("authenticated Wake intents derive their actor server-side") {
    val wealth =
      """{"expectedNextSequence":8,"intent":{"type":"takeWealth","resource":"secret"}}"""
    val end =
      """{"expectedNextSequence":9,"intent":{"type":"endWake"}}"""
    assertEquals(
      AuthenticatedFirstGameHttpWire.decodeCommand(wealth).toOption.get.intent,
      FirstGameIntent.TakeWealth(oathdigital.setup.WakeResource.Secret)
    )
    assertEquals(
      AuthenticatedFirstGameHttpWire.decodeCommand(end).toOption.get.intent,
      FirstGameIntent.EndWake
    )
    val impersonation =
      """{"expectedNextSequence":8,"intent":{"type":"endWake","playerId":"p2"}}"""
    assert(AuthenticatedFirstGameHttpWire.decodeCommand(impersonation).isLeft)
  }

  test("authenticated Travel intent is actor-free and exact") {
    val valid =
      """{"expectedNextSequence":10,"intent":{"type":"travel","destinationSiteId":"site:b"}}"""
    assertEquals(
      AuthenticatedFirstGameHttpWire.decodeCommand(valid).toOption.get.intent,
      FirstGameIntent.Travel(oathdigital.model.SiteId("site:b")))
    val impersonation =
      """{"expectedNextSequence":10,"intent":{"type":"travel","destinationSiteId":"site:b","playerId":"p2"}}"""
    assertEquals(AuthenticatedFirstGameHttpWire.decodeCommand(impersonation)
      .left.toOption.get.path, "$.intent.playerId")
  }

  test("authenticated bootstrap accepts only visible seat configuration") {
    val valid =
      """{"expectedNextSequence":0,"participants":[{"playerId":"p1","lineageId":"l1","color":"red"}],"firstPlayer":"p1"}"""
    val decoded = AuthenticatedFirstGameHttpWire.decodeBootstrap(valid)
      .toOption.get
    assertEquals(decoded.config.participants.map(_.playerId.value), Vector("p1"))

    Vector("userId", "actor", "worldDeckOrder", "denizenOrder", "relicOrder")
      .foreach { field =>
        val json = ujson.read(valid).obj
        json(field) = ujson.Str("not-accepted")
        assertEquals(
          AuthenticatedFirstGameHttpWire.decodeBootstrap(ujson.write(json))
            .left.toOption.get.path,
          s"$$.$field"
        )
      }

    val withIdentity = ujson.read(valid).obj
    withIdentity("participants").arr.head.obj("userId") = ujson.Str("user-1")
    assertEquals(
      AuthenticatedFirstGameHttpWire.decodeBootstrap(ujson.write(withIdentity))
        .left.toOption.get.path,
      "$.participants[0].userId"
    )
  }
}
