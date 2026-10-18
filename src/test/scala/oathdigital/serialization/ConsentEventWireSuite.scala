package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentPreferencesCleared, ConsentRequestWithdrawn, ConsentRequested}
import oathdigital.testkit.CatalogNames

/** The consent facts round-trip through the envelope (consent design,
  * "Events"). */
class ConsentEventWireSuite extends munit.FunSuite:
  private val p1 = PlayerId("p1")
  private val p2 = PlayerId("p2")
  private val pass = PowerId("site.narrow-pass.pass")
  private val request = ConsentRequest(p1, "travel.destination",
    DecisionOptionRef.Site(SiteId("site:dunes")),
    Vector(ConsentAsk(p2, Vector(pass))))

  private def roundTrip(event: OathEvent): Either[Any, OathEvent] =
    GameEventWire.encodeEvent("g", 7L, event).flatMap(encoded =>
      GameEventWire.decode(encoded)).map(_.event)

  private val events: Vector[(OathEvent, String)] = Vector(
    ConsentRequested(request) -> "consent.requested",
    ConsentAnswered(p2, granted = true) -> "consent.answered",
    ConsentAnswered(p2, granted = false) -> "consent.answered",
    ConsentRequestWithdrawn(p1) -> "consent.request-withdrawn",
    ConsentPreferenceSet(p2, pass, p1, ConsentSetting.CaseByCase) ->
      "consent.preference-set",
    ConsentPreferencesCleared(p2, pass) -> "consent.preferences-cleared")

  test("every consent fact round-trips"):
    events.foreach { case (event, _) =>
      assertEquals(roundTrip(event), Right(event)) }

  test("each consent fact has its own event type"):
    events.foreach { case (event, eventType) =>
      assertEquals(GameEventWire.encodeEvent("g", 0L, event)
        .map(_("eventType").str), Right(eventType)) }

  test("an unknown preference setting is refused"):
    val encoded = GameEventWire.encodeEvent("g", 0L,
      ConsentPreferenceSet(p2, pass, p1, ConsentSetting.Always))
      .map(value => { value("payload")("setting") = "sometimes"; value })
    assert(encoded.flatMap(GameEventWire.decode(_)).isLeft)

  test("a request's covers round-trip, and a request without them writes " +
      "none"):
    val covered = ConsentRequested(request.copy(covers = Vector(ConsentKey(
      PowerId("edifice.e16.intact"), p1, "operation.discard",
      DecisionOptionRef.Denizen(CatalogNames.denizen("Errand Boy"))))))
    assertEquals(roundTrip(covered), Right(covered))
    assertEquals(GameEventWire.encodeEvent("g", 0L, ConsentRequested(request))
      .map(_("payload").obj.contains("covers")), Right(false))

  test("an alternative ask round-trips, and a plain ask writes no " +
      "alternative"):
    val either = ConsentRequested(request.copy(asks = Vector(
      ConsentAsk(p2, Vector(pass), alternative = true),
      ConsentAsk(PlayerId("p3"), Vector(PowerId("test.ruler")),
        alternative = true))))
    assertEquals(roundTrip(either), Right(either))
    assertEquals(GameEventWire.encodeEvent("g", 0L, ConsentRequested(request))
      .map(_("payload")("asks")(0).obj.contains("alternative")), Right(false))

  test("an automatic power's use round-trips, and a request without one " +
      "writes none"):
    val used = ConsentRequested(request.copy(
      use = Some(ConsentUse(CatalogNames.denizen("Tyrant"), p1))))
    assertEquals(roundTrip(used), Right(used))
    assertEquals(GameEventWire.encodeEvent("g", 0L, ConsentRequested(request))
      .map(_("payload").obj.contains("use")), Right(false))
