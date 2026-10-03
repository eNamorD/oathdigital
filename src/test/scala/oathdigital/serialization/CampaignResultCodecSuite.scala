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
    assert(GameEventWire.decode(json.toString).isLeft)
