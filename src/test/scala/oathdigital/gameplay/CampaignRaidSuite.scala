package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready

class CampaignRaidSuite extends munit.FunSuite {
  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }
  private def player(state: OathState, id: PlayerId) =
    ready(state).game.current.players.find(_.player == id).get
  private def favorInBanks(state: OathState): Int =
    ready(state).banks.favor.values.sum
  private val raid = ChooseOneAnswer(DecisionOptionRef.Button("raid"))

  private def walk(b: Board, relic: RelicId, attack: Int, sacrifice: Int = 0) = {
    val printed = 2 + catalog.relics.find(_.id.value == relic.value).get.defense + 3
    val sword = Vector.fill(attack)(AttackDieFace.OneSword)
    val game = rules(dice(sword, Vector.fill(printed)(DefenseDieFace.Blank)))
    val started = game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor).toOption.get
    val kind = game.resolveWalker(started.state, b.actor, CampaignIds.kind, raid).toOption.get
    val targeted = game.resolveWalker(kind.state, b.actor, CampaignIds.targets,
      ChooseManyAnswer(Vector(DecisionOptionRef.Relic(relic),
        DecisionOptionRef.Banner(Banner.PeoplesFavor)))).toOption.get
    val forced = game.resolveWalker(targeted.state, b.actor, CampaignIds.force,
      ChooseAmountAnswer(attack)).toOption.get
    val sacrificed = game.resolveWalker(forced.state, b.actor, CampaignIds.sacrifice,
      ChooseAmountAnswer(sacrifice)).toOption.get
    (game, started, kind, targeted, forced, sacrificed)
  }

  test("the Raid's defense is the pawn, the targeted relic and banner, plus the board force") {
    val (b, relic) = raidBoard()
    val (_, _, _, _, _, sacrificed) = walk(b, relic, attack = 4)
    val result = ready(sacrificed.state).game.current.lastCampaignResult.get
    assertEquals(result.kind, CampaignKind.Raid)
    assertEquals(result.raidTargets, Vector[CampaignRaidTarget](
      CampaignRaidTarget.Pawn(b.other), CampaignRaidTarget.Relic(b.other, relic),
      CampaignRaidTarget.Banner(b.other, Banner.PeoplesFavor)))
    assertEquals(result.defenseScore, 3)
    assertEquals(result.attackerWins, true)
  }

  test("a Raid victory transfers in the printed order and then asks where the pawn goes") {
    val (b, relic) = raidBoard()
    val (game, _, _, _, _, sacrificed) = walk(b, relic, attack = 4)
    assertEquals(sacrificed.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.relocation)))
    val after = ready(sacrificed.state)
    // The transfer has happened; the pawn has not moved yet.
    assert(player(sacrificed.state, b.actor).relics.exists(_.id == relic))
    assertEquals(BannerRules.holder(after.game.current, Banner.PeoplesFavor),
      Some(b.actor))
    assertEquals(after.game.current.banners.peoplesFavor.favor, 0)
    assertEquals(favorInBanks(sacrificed.state) - favorInBanks(Ready(b.ready)), 3)
    assertEquals(player(sacrificed.state, b.other).advisers, Vector.empty)
    assertEquals(player(sacrificed.state, b.other).relics, Vector.empty)
    assertEquals(after.game.current.setAsideRelics,
      Vector(RelicId("raid-facedown-relic")))
    assertEquals(player(sacrificed.state, b.other).board.favor, 3)
    assertEquals(player(sacrificed.state, b.other).board.warbands, 2)
    assertEquals(player(sacrificed.state, b.other).pawnSite, Some(b.origin))
    val destination = after.game.current.map.inPlay.find(_ != b.origin).get
    val options = after.game.current.map.inPlay.filterNot(_ == b.origin)
      .map(site => DecisionOption.Site(DecisionOptionRef.Site(site)))
    val pending = after.game.current.walkerPending.get
    val tree = oathdigital.gameplay.actions.campaign.CampaignProcedure.rebuild(
      catalog, after, b.actor, Vector.empty).toOption.get
    assertEquals(oathdigital.gameplay.walker.ProcedureWalker.openDecisions(after,
      tree, pending, oathdigital.gameplay.walker.WalkerPowers.empty).head.query,
      DecisionQuery.ChooseOne(options, Some("Move the defeated pawn to another site")))
    val done = game.resolveWalker(sacrificed.state, b.actor, CampaignIds.relocation,
      ChooseOneAnswer(DecisionOptionRef.Site(destination))).toOption.get
    assertEquals(player(done.state, b.other).pawnSite, Some(destination))
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
    assertEquals(ready(done.state).game.current.walkerPending, None)
  }

  test("the pawn cannot be relocated to its own site") {
    val (b, relic) = raidBoard()
    val (game, _, _, _, _, sacrificed) = walk(b, relic, attack = 4)
    assert(game.resolveWalker(sacrificed.state, b.actor, CampaignIds.relocation,
      ChooseOneAnswer(DecisionOptionRef.Site(b.origin))).isLeft)
  }

  test("a Raid defeat transfers nothing, moves no pawn and kills half the survivors") {
    val (b, relic) = raidBoard(defenderWarbands = 9)
    val (_, _, _, _, _, done) = walk(b, relic, attack = 4)
    assertEquals(done.continue, OathContinue.ActActionSelection(b.actor))
    assertEquals(player(done.state, b.actor).relics.exists(_.id == relic), false)
    assertEquals(player(done.state, b.other).pawnSite, Some(b.origin))
    assertEquals(player(done.state, b.other).board.warbands, 9)
    assertEquals(player(done.state, b.actor).board.warbands, 2)
  }

  test("several enemy pawns at the site ask which to Raid") {
    val (b, relic) = raidBoard()
    val third = b.ready.game.current.players.map(_.player)
      .find(id => id != b.actor && id != b.other)
    assert(third.nonEmpty, "the fixture needs a third player")
    val crowded = b.copy(ready = b.ready.updateCurrent(current => current.copy(
      players = current.players.map(p =>
        if (third.contains(p.player)) p.copy(pawnSite = Some(b.origin)) else p))))
    val game = rules()
    val started = game.startWalker(Ready(crowded.ready), ActionRef.Campaign,
      b.actor).toOption.get
    val kind = game.resolveWalker(started.state, b.actor, CampaignIds.kind, raid)
      .toOption.get
    assertEquals(kind.continue, OathContinue.AwaitingCampaignDecision(b.actor,
      DecisionId(CampaignIds.defender)))
    assert(game.resolveWalker(kind.state, b.actor, CampaignIds.defender,
      ChooseOneAnswer(DecisionOptionRef.Player(b.other))).isRight)
  }

  test("the Raid's recorded events replay to the same state as the live walk") {
    val (b, relic) = raidBoard()
    val (game, started, kind, targeted, forced, sacrificed) =
      walk(b, relic, attack = 4)
    val destination = ready(sacrificed.state).game.current.map.inPlay
      .find(_ != b.origin).get
    val done = game.resolveWalker(sacrificed.state, b.actor, CampaignIds.relocation,
      ChooseOneAnswer(DecisionOptionRef.Site(destination))).toOption.get
    val events = Vector(started, kind, targeted, forced, sacrificed, done)
      .flatMap(_.events)
    val replayed = events.foldLeft[Either[OathViolation, OathState]](
      Right(Ready(b.ready))) {
      case (Right(state), event) => game.evolve(state, event)
      case (failure, _) => failure
    }
    assertEquals(replayed, Right(done.state))
  }
}
