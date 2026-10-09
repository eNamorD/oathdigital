package oathdigital.serialization

import oathdigital.model._
import oathdigital.model.OathEvent.{ConsentAnswered, ConsentPreferenceSet,
  ConsentPreferencesCleared, ConsentRequestWithdrawn, ConsentRequested}

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
