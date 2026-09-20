package oathdigital.gameplay

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.CampaignCommand
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer._
import oathdigital.model.OathState.Ready

/** The walker Campaign against the legacy Campaign, on the same boards with
  * the same dice. */
class CampaignParitySuite extends munit.FunSuite {
  private val old = new OathRules(catalog)
  private val decision = DecisionId("parity")

  private def ok[A](result: Either[OathViolation, A], step: String): A =
    result.fold(violation => fail(s"$step: $violation"), identity)

  private def legacy(state: ReadyGame, commands: CampaignCommand*): OathState =
    commands.foldLeft[OathState](Ready(state)) { (current, command) =>
      ok(old.handle(current, command), command.toString).state }

  private def walker(game: OathRules, b: Board,
      answers: (PlayerId, String, DecisionAnswer)*): OathState = {
    val started = ok(game.startWalker(Ready(b.ready), ActionRef.Campaign, b.actor),
      "start")
    answers.foldLeft(started.state) { case (state, (by, id, answer)) =>
      ok(game.resolveWalker(state, by, id, answer), id).state }
  }

  /** Everything but the machinery that differs by design. */
  private def normalized(state: OathState): ReadyGame = state match {
    case Ready(ready) => ready.updateCurrent(_.copy(pending = None,
      walkerPending = None, walkerProcedure = None, walkerModifiers = Vector.empty,
      walkerStartArgs = Vector.empty, rollPools = Map.empty,
      rollOutcomes = Map.empty, lastCampaignResult = None))
    case other => fail(s"expected a ready game, got $other")
  }

  private def same(legacyState: OathState, walkerState: OathState): Unit =
    assertEquals(normalized(walkerState), normalized(legacyState))

  private def printed(b: Board): Int = catalog.sites.find(_.id == b.origin).get.defense
  private def blanks(n: Int) = Vector.fill(n)(DefenseDieFace.Blank)
  private def swords(n: Int) = Vector.fill(n)(AttackDieFace.OneSword)
  private def site(id: SiteId) = DecisionOptionRef.Site(id)
  private val zero = ChooseAmountAnswer(0)
  private def amount(n: Int) = ChooseAmountAnswer(n)

  test("a Conquest victory over bandits places the survivors") {
    val b = board()
    val game = rules(dice(swords(4), blanks(printed(b))))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 4),
      CampaignCommand.FinishPlans(b.actor, decision, swords(4)),
      CampaignCommand.Sacrifice(b.actor, decision, 0, blanks(printed(b))),
      CampaignCommand.Place(b.actor, decision, Vector(
        CampaignForceAllocation(b.origin, 3)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(4)),
        (b.actor, CampaignIds.sacrifice, zero),
        (b.actor, CampaignIds.placement, amount(3))))
  }

  test("a Conquest defeat kills the skull and sacrifice losses and half the survivors") {
    val b = board()
    val game = rules(dice(swords(2), blanks(printed(b))))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 2),
      CampaignCommand.FinishPlans(b.actor, decision, swords(2)),
      CampaignCommand.Sacrifice(b.actor, decision, 0, blanks(printed(b)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(2)),
        (b.actor, CampaignIds.sacrifice, zero)))
  }

  test("a sacrifice that turns a defeat into a victory") {
    val b = board()
    val game = rules(dice(swords(2), blanks(printed(b))))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 2),
      CampaignCommand.FinishPlans(b.actor, decision, swords(2)),
      CampaignCommand.Sacrifice(b.actor, decision, 1, blanks(printed(b))),
      CampaignCommand.Place(b.actor, decision, Vector(
        CampaignForceAllocation(b.origin, 1)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(2)),
        (b.actor, CampaignIds.sacrifice, amount(1)),
        (b.actor, CampaignIds.placement, amount(1))))
  }

  // Five swords: four would only tie the defenders' four warbands, and a tie loses.
  test("two targets are placed with one allocation") {
    val b = board(extras = 1)
    val second = b.extras.head
    val defense = blanks(printed(b) + catalog.sites.find(_.id == second).get.defense)
    val game = rules(dice(swords(5), defense))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, Vector(b.origin, second), 5),
      CampaignCommand.FinishPlans(b.actor, decision, swords(5)),
      CampaignCommand.Sacrifice(b.actor, decision, 0, defense),
      CampaignCommand.Place(b.actor, decision, Vector(
        CampaignForceAllocation(b.origin, 2),
        CampaignForceAllocation(second, 1)))),
      walker(game, b,
        (b.actor, CampaignIds.targets, ChooseManyAnswer(Vector(site(second)))),
        (b.actor, CampaignIds.force, amount(5)),
        (b.actor, CampaignIds.sacrifice, zero),
        (b.actor, CampaignIds.placement, DistributeAnswer(Vector(
          DistributeAmount(site(b.origin), 2), DistributeAmount(site(second), 1))))))
  }

  test("Brass Army adds four attack dice for a secret") {
    val brass = relicWith("relic.brass-army.campaign")
    val b = withSecrets(withRelic(board(), brass), 2)
    val faces = swords(5)
    val game = rules(dice(faces, blanks(printed(b))))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 1),
      CampaignCommand.ChoosePlan(b.actor, decision,
        CampaignPlanSource.Relic(b.actor, RelicId(brass))),
      CampaignCommand.FinishPlans(b.actor, decision, faces),
      CampaignCommand.Sacrifice(b.actor, decision, 0, blanks(printed(b))),
      CampaignCommand.Place(b.actor, decision, Vector(
        CampaignForceAllocation(b.origin, 1)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(1)),
        (b.actor, CampaignIds.attackerPlan,
          ChooseOneAnswer(DecisionOptionRef.Relic(RelicId(brass)))),
        (b.actor, CampaignIds.sacrifice, zero),
        (b.actor, CampaignIds.placement, amount(1))))
  }

  test("Outriders ignores every skull, from a facedown adviser it reveals first") {
    val outriders = cardWith("denizen.outriders")
    Vector(Orientation.FaceUp, Orientation.FaceDown).foreach { orientation =>
      val b = withAdviser(board(), outriders, orientation)
      val faces = Vector[AttackDieFace](AttackDieFace.TwoSwordsSkull,
        AttackDieFace.TwoSwordsSkull)
      val game = rules(dice(faces, blanks(printed(b))))
      same(legacy(b.ready,
        CampaignCommand.Start(b.actor, decision, b.origin, 2),
        CampaignCommand.ChoosePlan(b.actor, decision,
          CampaignPlanSource.Adviser(b.actor, DenizenId(outriders))),
        CampaignCommand.FinishPlans(b.actor, decision, faces),
        CampaignCommand.Sacrifice(b.actor, decision, 0, blanks(printed(b))),
        CampaignCommand.Place(b.actor, decision, Vector(
          CampaignForceAllocation(b.origin, 2)))),
        walker(game, b, (b.actor, CampaignIds.force, amount(2)),
          (b.actor, CampaignIds.attackerPlan,
            ChooseOneAnswer(DecisionOptionRef.Denizen(DenizenId(outriders)))),
          (b.actor, CampaignIds.sacrifice, zero),
          (b.actor, CampaignIds.placement, amount(2))))
    }
  }

  test("a player defender with the title adds a die and keeps half the killed force") {
    val b = againstPlayer(board())
    val defense = blanks(printed(b) + 1)
    val game = rules(dice(swords(4), defense))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 4),
      CampaignCommand.FinishPlans(b.actor, decision, Vector.empty),
      CampaignCommand.ChoosePlan(b.other, decision,
        CampaignPlanSource.Title(b.other)),
      CampaignCommand.FinishPlans(b.other, decision, swords(4)),
      CampaignCommand.Sacrifice(b.actor, decision, 0, defense),
      CampaignCommand.Place(b.actor, decision, Vector(
        CampaignForceAllocation(b.origin, 2)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(4)),
        (b.other, CampaignIds.defenderPlan,
          ChooseOneAnswer(DecisionOptionRef.Button("title"))),
        (b.actor, CampaignIds.sacrifice, zero),
        (b.actor, CampaignIds.placement, amount(2))))
  }

  test("a Campaign with no force fights and loses nothing") {
    val b = board(warbands = 0)
    val game = rules(dice(Vector.empty, blanks(printed(b))))
    same(legacy(b.ready,
      CampaignCommand.Start(b.actor, decision, b.origin, 0),
      CampaignCommand.FinishPlans(b.actor, decision, Vector.empty),
      CampaignCommand.Sacrifice(b.actor, decision, 0, blanks(printed(b)))),
      walker(game, b, (b.actor, CampaignIds.force, amount(0))))
  }

  private def raidCase(defenderWarbands: Int, attack: Int, sacrifice: Int,
      relocate: Boolean): Unit = {
    val (b, relic) = raidBoard(defenderWarbands)
    val targets = Vector[CampaignRaidTarget](CampaignRaidTarget.Pawn(b.other),
      CampaignRaidTarget.Relic(b.other, relic),
      CampaignRaidTarget.Banner(b.other, Banner.PeoplesFavor))
    val defense = blanks(2 + catalog.relics.find(_.id.value == relic.value).get
      .defense + 3)
    val game = rules(dice(swords(attack), defense))
    val destination = b.ready.game.current.map.inPlay.find(_ != b.origin).get
    val legacyCommands = Vector[CampaignCommand](
      CampaignCommand.StartRaid(b.actor, decision, targets, attack),
      CampaignCommand.FinishPlans(b.actor, decision, Vector.empty),
      CampaignCommand.FinishPlans(b.other, decision, swords(attack)),
      CampaignCommand.Sacrifice(b.actor, decision, sacrifice, defense)) ++
      Option.when(relocate)(CampaignCommand.RelocateRaidPawn(b.actor, decision,
        destination))
    val walkerAnswers = Vector[(PlayerId, String, DecisionAnswer)](
      (b.actor, CampaignIds.kind, ChooseOneAnswer(DecisionOptionRef.Button("raid"))),
      (b.actor, CampaignIds.targets, ChooseManyAnswer(Vector(
        DecisionOptionRef.Relic(relic), DecisionOptionRef.Banner(Banner.PeoplesFavor)))),
      (b.actor, CampaignIds.force, amount(attack)),
      (b.actor, CampaignIds.sacrifice, amount(sacrifice))) ++
      Option.when(relocate)((b.actor, CampaignIds.relocation,
        ChooseOneAnswer(site(destination))))
    same(legacy(b.ready, legacyCommands: _*), walker(game, b, walkerAnswers: _*))
  }

  test("a Raid victory transfers and relocates") {
    raidCase(defenderWarbands = 3, attack = 4, sacrifice = 0, relocate = true)
  }

  test("a Raid defeat changes only the attacker's warbands") {
    raidCase(defenderWarbands = 9, attack = 4, sacrifice = 0, relocate = false)
  }

  test("an illegal start is refused by both paths and changes nothing") {
    val noSupply = board(supply = 1)
    assert(old.handle(Ready(noSupply.ready), CampaignCommand.Start(noSupply.actor,
      decision, noSupply.origin, 0)).isLeft)
    assert(rules().startWalker(Ready(noSupply.ready), ActionRef.Campaign,
      noSupply.actor).isLeft)
    val tooMany = board(warbands = 2)
    assert(old.handle(Ready(tooMany.ready), CampaignCommand.Start(tooMany.actor,
      decision, tooMany.origin, 3)).isLeft)
    val started = rules().startWalker(Ready(tooMany.ready), ActionRef.Campaign,
      tooMany.actor).toOption.get
    assert(rules().resolveWalker(started.state, tooMany.actor, CampaignIds.force,
      amount(3)).isLeft)
  }
}
