package oathdigital.serialization

import oathdigital.model._
import oathdigital.serialization.WalkerWireEvents.recordedOperation
import oathdigital.testkit.Unwrap.accepted

class PayCostWireSuite extends munit.FunSuite:
  private val player = PlayerId("red")
  private def roundTrip(operation: CoreOperation): ujson.Value =
    val original = recordedOperation(operation)
    val encoded = accepted(GameEventWire.encodeEvent("walker", 0, original))
    assertEquals(GameEventWire.decode(encoded).map(_.event), Right(original))
    ujson.read(encoded)("payload")("step")("ops")(0)

  test("a default PayCost writes neither new field"):
    val op = roundTrip(PayCost(player, Location.OnCard(DenizenId("a")),
      Cost(secret = 1)))
    assert(!op.obj.contains("intoOccupied"))
    assert(!op.obj.contains("matchingBank"))

  test("intoOccupied and matchingBank round-trip"):
    val op = roundTrip(PayCost(player, Location.OnCard(DenizenId("a")),
      Cost(favor = 1), intoOccupied = true, matchingBank = Some(Suit.Order)))
    assertEquals(op("intoOccupied").bool, true)
    assertEquals(op("matchingBank").str, Suit.Order.key)

  test("a banner power use round-trips"):
    val original = RecordPowerUse(PowerUseRef(PowerTiming.Wake,
      PowerSourceRef.Banner(Banner.DarkestSecret), PowerId("banner.test")))
    val op = roundTrip(original)
    assertEquals(op("bannerKey").str, Banner.DarkestSecret.key)
