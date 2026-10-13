package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{Look, MenuPlay}

/** A Campaign whose start offered the Skip: the attacker is asked the kind
  * even with one legal kind, and "Skip Campaign" ends it. */
class CampaignSkipSuite extends munit.FunSuite:
  /** Offers the Skip at every Campaign's start, as Vow of Union does after
    * it moves a warband. */
  private object OffersSkipAtCampaignStart extends ContributingPower:
    val id: PowerId = PowerId("test.offers-skip")
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.CampaignBeforeTargets -> Vector(Transform((_, children) =>
        BuildOps((_, _) => Right(Vector[CoreOperation](
          ModifyDicePool(CampaignIds.skipOffered, 1)))) +: children)))

  private val powers = WalkerPowers(Vector(OffersSkipAtCampaignStart))
  private val game = rulesWith(Vector(OffersSkipAtCampaignStart))

  /** p1, with 4 warbands and 7 Supply, can only conquer the Bandits at
    * Ancient City. Their Campaign is started under the Skip offer. */
  private def conquestOfferingSkip: (Board, Run) =
    val b = board(warbands = 4, supply = 7)
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign,
      b.actor).fold(error => fail(s"no start: $error"), identity)
    (b, Run(game, started, started.events, powers))

  test("an offered Skip asks the kind even with one legal kind, and Skip " +
      "ends the Campaign with its Supply spent"):
    val (b, asked) = conquestOfferingSkip
    assert(awaits(asked, b.actor, CampaignIds.kind))
    assertEquals(asked.options(b.actor, powers), Vector[DecisionOption](
      DecisionOption.Button(DecisionOptionRef.Button("conquest"),
        "Conquest"),
      DecisionOption.Button(CampaignIds.skip, "Skip Campaign")))
    val skipped = asked.pick(b.actor, CampaignIds.kind, CampaignIds.skip)
    val current = ready(skipped.state).game.current
    assertEquals(MenuPlay.procedureBeyondMenu(current), None)
    assertEquals(current.lastCampaignResult, None)
    assertEquals(Look(skipped.state).supply(b.actor),
      7 - CampaignIds.SupplyCost)
    assertEquals(Look(skipped.state).boardWarbands(b.actor), 4)
    assert(!skipped.ops.exists(_.isInstanceOf[RecordCampaignResult]))

  test("choosing the kind instead runs the Campaign on"):
    val (b, asked) = conquestOfferingSkip
    val conquest = asked.pick(b.actor, CampaignIds.kind,
      DecisionOptionRef.Button("conquest"))
    assert(awaits(conquest, b.actor, CampaignIds.force))
