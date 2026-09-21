package oathdigital.serialization

import oathdigital.engine.RecordedEvent
import oathdigital.gameplay.walker.{WalkerStepPayload, DeltaMeaning, WalkerStepRecorded}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

class CampaignResultCodecSuite extends munit.FunSuite {
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

  test("a recorded Campaign result round trips through the wire for both kinds") {
    val events = Vector(conquest, raid, empty).map(result =>
      WalkerStepRecorded("0", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("campaign")),
        Vector(RecordCampaignResult(result)), Vector.empty): OathEvent)
    val encoded = GameEventWire.encodeStream("campaign-result", catalog.ref,
      events.zipWithIndex.map { case (event, index) =>
        RecordedEvent(index.toLong, event) }).toOption.get
    assertEquals(GameEventWire.decodeStream(encoded).toOption.get.map(_.event),
      events)
    assertEquals(ujson.read(encoded).arr.map(
      _("payload")("ops")(0)("kind").str).toVector.distinct,
      Vector("record-campaign-result"))
  }

  test("a malformed recorded result is a typed decode failure, not an exception") {
    val encoded = GameEventWire.encodeEvent("campaign-result", catalog.ref, 0,
      WalkerStepRecorded("0", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("campaign")),
        Vector(RecordCampaignResult(conquest)), Vector.empty)).toOption.get
    val json = ujson.read(encoded)
    json("payload")("ops")(0)("result")("attackFaces") = ujson.Arr("not-a-face")
    assert(GameEventWire.decode(json.toString).isLeft)
  }
}
