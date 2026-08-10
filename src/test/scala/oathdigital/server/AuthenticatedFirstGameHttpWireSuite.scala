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

  test("authenticated Search intents are actor-free and reject hidden deck input") {
    val begin =
      """{"expectedNextSequence":10,"intent":{"type":"beginSearch","source":"world"}}"""
    assertEquals(AuthenticatedFirstGameHttpWire.decodeCommand(begin).toOption.get.intent,
      FirstGameIntent.BeginSearch(oathdigital.model.SearchSource.WorldDeck))
    val impersonation = ujson.read(begin).obj
    impersonation("intent").obj("playerId") = "p2"
    assert(AuthenticatedFirstGameHttpWire.decodeCommand(ujson.write(impersonation)).isLeft)
    val hiddenOrder = ujson.read(begin).obj
    hiddenOrder("intent").obj("drawn") = ujson.Arr("denizen:secret")
    assert(AuthenticatedFirstGameHttpWire.decodeCommand(ujson.write(hiddenOrder)).isLeft)

    val complete =
      """{"expectedNextSequence":11,"intent":{"type":"completeSearch","decisionId":"search-10","kept":{"kind":"denizen","id":"denizen:a"},"discardedInOrder":[{"kind":"vision","id":"vision:b"}],"placement":{"kind":"discard"}}}"""
    assert(AuthenticatedFirstGameHttpWire.decodeCommand(complete).isRight)
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
