package oathdigital.serialization

import oathdigital.model._
import oathdigital.serialization.WalkerWireEvents.{encodedStreamThatRoundTrips,
  recordedOperation}
import oathdigital.testkit.Unwrap.accepted

class CampaignResultCodecSuite extends munit.FunSuite:
  private val conquest = CampaignResult(PlayerId("red"), CampaignKind.Conquest,
    CampaignDefender.Bandits, Vector(SiteId("site:a"), SiteId("site:b")),
    Vector.empty, force = 3,
    attackFaces = Vector(AttackDieFace.HollowSword, AttackDieFace.TwoSwordsSkull),
    attackScore = 2, skullLosses = 1, sacrificed = 1,
    defenseFaces = Vector(DefenseDieFace.OneShield, DefenseDieFace.Doubler),
    defenseScore = 4, attackerWins = false)
  private val raid = conquest.copy(kind = CampaignKind.Raid,
    defender = CampaignDefender.Player(PlayerId("blue")),
    targetSites = Vector.empty,
    raidTargets = Vector(CampaignRaidTarget.Pawn(PlayerId("blue")),
      CampaignRaidTarget.Relic(PlayerId("blue"), RelicId("r1")),
      CampaignRaidTarget.Banner(PlayerId("blue"), Banner.PeoplesFavor)),
    attackerWins = true)
  private val empty = conquest.copy(attackFaces = Vector.empty,
    defenseFaces = Vector.empty, attackScore = 0, skullLosses = 0,
    sacrificed = 0, force = 0)

  test("a recorded Campaign result round trips through the wire for both kinds"):
    val events = Vector(conquest, raid, empty).map(result =>
      recordedOperation("campaign", RecordCampaignResult(result)))
    val encoded = encodedStreamThatRoundTrips("campaign-result", events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("ops")(0)("kind").str).toVector.distinct,
      Vector("record-campaign-result"))

  test("a malformed recorded result is a typed decode failure, not an exception"):
    val encoded = accepted(GameEventWire.encodeEvent("campaign-result", 0,
      recordedOperation("campaign", RecordCampaignResult(conquest))))
    val json = ujson.read(encoded)
    json("payload")("ops")(0)("result")("attackFaces") = ujson.Arr("not-a-face")
    assertEquals(GameEventWire.decode(json), Left(WireError.InvalidValue(
      "$.payload.ops[0].result.attackFaces[0]",
      "unknown attack die face 'not-a-face'")))

  test("the sacrifice's worth round trips, and a record without it reads " +
      "one"):
    encodedStreamThatRoundTrips("campaign-result-worth", Vector(
      recordedOperation("campaign",
        RecordCampaignResult(conquest.copy(sacrificeWorth = 3)))))
    def encoded = ujson.read(accepted(GameEventWire.encodeEvent(
      "campaign-result", 0,
      recordedOperation("campaign", RecordCampaignResult(conquest)))))
    val older = encoded
    older("payload")("ops")(0)("result").obj.remove("sacrificeWorth")
    assertEquals(GameEventWire.decode(older), GameEventWire.decode(encoded))

  private val fromOrigin = raid.copy(originForce =
    Some(OriginForce(SiteId("site:a"), atSite = 2, onBoard = 1)))

  test("a result with an origin's force round trips through the wire"):
    val events = Vector(recordedOperation("campaign",
      RecordCampaignResult(fromOrigin)))
    val encoded = encodedStreamThatRoundTrips("campaign-origin", events)
    assertEquals(ujson.read(encoded).arr.head("payload")("ops")(0)(
      "result")("originForce")("atSite").num.toInt, 2)
