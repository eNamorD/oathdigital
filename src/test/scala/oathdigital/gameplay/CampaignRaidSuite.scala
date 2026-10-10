package oathdigital.gameplay

import oathdigital.testkit.MenuPlay
import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignProcedure}
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, Look, Outcomes}
import oathdigital.testkit.Unwrap.{accepted, present}

class CampaignRaidSuite extends munit.FunSuite:
  /** Every `rules(...)` call in this file builds the walker power catalog. */
  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  private def ready(state: OathState): ReadyGame = Outcomes.ready(state)
  private def favorInBanks(state: OathState): Int =
    ready(state).banks.favor.values.sum
  private val raid = ChooseOneAnswer(DecisionOptionRef.Button("raid"))

  /** Each step of a Raid on the rival's relic and People's Favor. */
  private final case class RaidWalk(game: OathRules, started: OathTransition,
      kind: OathTransition, targeted: OathTransition, forced: OathTransition,
      sacrificed: OathTransition):
    def events: Vector[OathEvent] =
      Vector(started, kind, targeted, forced, sacrificed).flatMap(_.events)

  /** Raids the rival with `attack` swords against blank defense dice,
    * sacrificing `sacrifice`. */
  private def walk(b: Board, relic: RelicId, attack: Int, sacrifice: Int = 0)
      : RaidWalk =
    // The printed defense: 2 dice for the pawn, the relic's own defense, and
    // 3 for the banner.
    val relicDefense =
      catalog.relics.find(_.id.value == relic.value).get.defense
    val printed = 2 + relicDefense + 3
    val sword = Vector.fill(attack)(AttackDieFace.OneSword)
    val game = rules(dice(sword, Vector.fill(printed)(DefenseDieFace.Blank)))
    val started = accepted(game.startWalker(Ready(b.ready), ActionRef.Campaign,
      b.actor))
    val kind = accepted(game.resolveWalker(started.state, b.actor,
      CampaignIds.kind, raid))
    val targeted = accepted(game.resolveWalker(kind.state, b.actor,
      CampaignIds.targets, ChooseManyAnswer(Vector(
        DecisionOptionRef.Relic(relic),
        DecisionOptionRef.Banner(Banner.PeoplesFavor)))))
    val forced = accepted(game.resolveWalker(targeted.state, b.actor,
      CampaignIds.force, ChooseAmountAnswer(attack)))
    val sacrificed = accepted(game.resolveWalker(forced.state, b.actor,
      CampaignIds.sacrifice, ChooseAmountAnswer(sacrifice)))
    RaidWalk(game, started, kind, targeted, forced, sacrificed)

  test("the Raid's defense is the pawn, the targeted relic and banner, plus " +
      "the board force"):
    val RaidBoard(b, relic) = raidBoard()
    val sacrificed = walk(b, relic, attack = 4).sacrificed
    val current = ready(sacrificed.state).game.current
    val result = present(current.lastCampaignResult, "a Campaign result")
    assertEquals(result.kind, CampaignKind.Raid)
    assertEquals(result.raidTargets, Vector[CampaignRaidTarget](
      CampaignRaidTarget.Pawn(b.rival),
      CampaignRaidTarget.Relic(b.rival, relic),
      CampaignRaidTarget.Banner(b.rival, Banner.PeoplesFavor)))
    // The rival's 3 board warbands; the blank dice score nothing.
    assertEquals(result.defenseScore, 3)
    assertEquals(result.attackerWins, true)

  test("a Raid victory transfers in the printed order and then asks where " +
      "the pawn goes"):
    val RaidBoard(b, relic) = raidBoard()
    val walked = walk(b, relic, attack = 4)
    val sacrificed = walked.sacrificed
    parked.assertParked(sacrificed.state, ActionRef.Campaign,
      CampaignIds.relocation, b.actor)
    val after = ready(sacrificed.state)
    val rival = Look(sacrificed.state).player(b.rival)
    // The transfer has happened; the pawn has not moved yet.
    assert(Look(sacrificed.state).player(b.actor).relics.exists(_.id == relic))
    assertEquals(BannerRules.holder(after.game.current, Banner.PeoplesFavor),
      Some(b.actor))
    // The banner's 3 favor go back to the banks.
    assertEquals(after.game.current.banners.peoplesFavor.favor, 0)
    assertEquals(favorInBanks(sacrificed.state),
      favorInBanks(Ready(b.ready)) + 3)
    assertEquals(rival.advisers, Vector.empty)
    assertEquals(rival.relics, Vector.empty)
    assertEquals(after.game.current.setAsideRelics,
      Vector(RelicId("raid-facedown-relic")))
    // The rival burns half of their 5 favor, rounded down, and loses half of
    // their 3 defending warbands, rounded down.
    assertEquals(rival.board.favor, 5 - 5 / 2)
    assertEquals(rival.board.warbands, 3 - 3 / 2)
    assertEquals(rival.pawnSite, Some(b.origin))
    val inPlay = after.game.current.map.inPlay
    val destination = present(inPlay.find(_ != b.origin),
      "another site in play")
    val options = after.game.current.map.inPlay.filterNot(_ == b.origin)
      .map(site => DecisionOption.Site(DecisionOptionRef.Site(site)))
    val pending = present(after.game.current.walkerPending,
      "a parked walker decision")
    val tree = accepted(CampaignProcedure.rebuild(catalog, after, b.actor,
      Vector.empty))
    val asked = present(ProcedureWalker.openDecisions(after, tree, pending,
      WalkerPowers.empty).headOption, "the relocation decision")
    assertEquals(asked.query, DecisionQuery.ChooseOne(options,
      Some("Move the defeated pawn to another site")))
    val moved = ChooseOneAnswer(DecisionOptionRef.Site(destination))
    val done = accepted(walked.game.resolveWalker(sacrificed.state, b.actor,
      CampaignIds.relocation, moved))
    assertEquals(Look(done.state).player(b.rival).pawnSite, Some(destination))
    parked.assertResumed(done.state, Phase.Act, b.actor)
    assertEquals(MenuPlay.pendingBeyondMenu(ready(done.state)), None)

  test("the pawn cannot be relocated to its own site"):
    val RaidBoard(b, relic) = raidBoard()
    val walked = walk(b, relic, attack = 4)
    val toOrigin = ChooseOneAnswer(DecisionOptionRef.Site(b.origin))
    val refused = walked.game.resolveWalker(walked.sacrificed.state, b.actor,
      CampaignIds.relocation, toOrigin)
    assertEquals(refused.left.toOption, Some(OathViolation.InvalidEventOrder(
      "decision campaign.relocation does not offer the selected option")))

  test("a Raid defeat transfers nothing, moves no pawn and kills half the " +
      "survivors"):
    val RaidBoard(b, relic) = raidBoard(defenderWarbands = 9)
    val done = walk(b, relic, attack = 4).sacrificed
    parked.assertResumed(done.state, Phase.Act, b.actor)
    val actor = Look(done.state).player(b.actor)
    val rival = Look(done.state).player(b.rival)
    assertEquals(actor.relics.exists(_.id == relic), false)
    assertEquals(rival.pawnSite, Some(b.origin))
    assertEquals(rival.board.warbands, 9)
    // p1 commits 4 warbands; half of the 4 survivors die.
    assertEquals(actor.board.warbands, 4 - 4 / 2)

  test("several enemy pawns at the site ask which to Raid"):
    // Only the board matters: the Raid never reaches the relic choice.
    val b = raidBoard().board
    val crowded = on(b)(_.pawn(b.bystander, at = b.origin))
    val game = rules()
    val started = accepted(game.startWalker(Ready(crowded.ready),
      ActionRef.Campaign, b.actor))
    val kind = accepted(game.resolveWalker(started.state, b.actor,
      CampaignIds.kind, raid))
    parked.assertParked(kind.state, ActionRef.Campaign, CampaignIds.defender,
      b.actor)
    val chosen = accepted(game.resolveWalker(kind.state, b.actor,
      CampaignIds.defender, ChooseOneAnswer(DecisionOptionRef.Player(b.rival))))
    parked.assertParked(chosen.state, ActionRef.Campaign, CampaignIds.targets,
      b.actor)

  test("the Raid's recorded events replay to the same state as the live walk"):
    val RaidBoard(b, relic) = raidBoard()
    val walked = walk(b, relic, attack = 4)
    val inPlay = ready(walked.sacrificed.state).game.current.map.inPlay
    val destination = present(inPlay.find(_ != b.origin),
      "another site in play")
    val done = accepted(walked.game.resolveWalker(walked.sacrificed.state,
      b.actor, CampaignIds.relocation,
      ChooseOneAnswer(DecisionOptionRef.Site(destination))))
    assertEquals(ActPowerHarness.replayed(walked.game, b.ready,
      walked.events ++ done.events), ready(done.state))
