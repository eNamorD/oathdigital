package oathdigital.application.gamelog

import oathdigital.engine.{RecordedEvent, ReplayStep}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.walker.{ChoicePayload, DeltaMeaning,
  WalkerCompleted, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import LogScripts._

class GameLogCampaignSuite extends munit.FunSuite:
  private def lines(script: Script, viewer: Option[PlayerId] = None) =
    texts(format(script, viewer).filter(_.depth == 1))

  test("a Raid tells its targets, pools, dice, totals, winner and losses in order"):
    val script = raid
    val all = lines(script)
    val defender = script.players.find(player => player != script.actor &&
      all.exists(_.startsWith(s"Started Campaign: Raid against ${name(player)}")))
      .get
    val expected = Vector(
      s"Started Campaign: Raid against ${name(defender)}",
      s"Targets: ${name(defender)}'s pawn",
      "Attack Pool: ",
      "Rolled ",
      "Attack: ",
      "Sacrificed ",
      "Rolled ",
      "Defense: ",
      s"${name(script.actor)} wins!",
      s"${name(defender)} ")
    val found = expected.foldLeft((0, Vector.empty[Int])) {
      case ((from, at), prefix) =>
        val index = all.indexWhere(_.startsWith(prefix), from)
        assert(index >= 0, s"no '$prefix' after line $from in $all")
        (index + 1, at :+ index)
    }._2
    assertEquals(found, found.sorted)
    assert(all.exists(_.endsWith(" for the attack")), all)
    assert(all.exists(_.endsWith(" for the defense")), all)
    // Half of a one-warband board is none, so "lost" may be absent; the
    // relocation never is.
    assert(all.last.startsWith(s"${name(defender)} ") &&
      all.last.contains(" was sent to "), all)

  test("a battle plan answer names who activated it; a flipped plan card names itself"):
    val script = woken
    val steps = script.history.steps
    val last = steps.last.after
    val ready = last match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    // Any other player whose starting adviser is a face-down Denizen.
    val (defender, plan) = ready.game.current.players
      .filter(_.player != script.actor).flatMap(player => player.advisers
        .collectFirst { case DenizenState(id, Orientation.FaceDown, _) =>
          player.player -> id }).head
    val tail = Vector[OathEvent](
      WalkerStepRecorded("plan", ChoicePayload(CampaignIds.defenderPlan,
        ChooseOneAnswer(DecisionOptionRef.Denizen(plan)), defender),
        Vector.empty, Vector.empty),
      WalkerStepRecorded("reveal", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("reveal")), Vector(Move(Piece.Card(plan),
          PositionedLocation(Location.PlayArea(defender)),
          PositionedLocation(Location.PlayArea(defender)),
          Some(Orientation.FaceUp))), Vector.empty),
      WalkerCompleted(ActionRef.Campaign))
    val entries = formatter.format(steps ++ tail.zipWithIndex.map {
      case (event, index) => ReplayStep(RecordedEvent(steps.size.toLong + index,
        event), last, last) }, None)
    val shown = texts(entries.filter(_.sequence >= steps.size))
    assert(shown.contains(s"${name(defender)} activated a Denizen"), shown)
    assert(shown.exists(line => line.startsWith(s"${name(defender)} revealed ")
      && line != s"${name(defender)} revealed a Denizen"), shown)

  test("a plan the bandits apply is named like any other activation"):
    val script = woken
    val steps = script.history.steps
    val last = steps.last.after
    val ready = last match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    // A first game deals each homeland site its edifice, which sits among
    // the site's cards; a site card is what a bandit plan's source is.
    val edifice = ready.game.current.map.sites.values.flatMap(_.denizens)
      .collectFirst { case held: EdificeState => held.id }.get
    val tail = Vector[OathEvent](
      WalkerStepRecorded("bandit-plan", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("plan")), Vector(ModifyDicePool(
          CampaignPlans.appliedMarker(DecisionOptionRef.Edifice(edifice)), 1)),
        Vector.empty),
      WalkerCompleted(ActionRef.Campaign))
    val entries = formatter.format(steps ++ tail.zipWithIndex.map {
      case (event, index) => ReplayStep(RecordedEvent(steps.size.toLong + index,
        event), last, last) }, None)
    val shown = texts(entries.filter(_.sequence >= steps.size))
    // A site card is public, so even an observer reads its name.
    assert(shown.exists(line => line.startsWith("The bandits activated ") &&
      !line.endsWith("Edifice") && !line.endsWith("Denizen") &&
      !line.contains("campaign.plan-applied")), shown)

  test("a total a plan rewrites in the same segment is told once, as rewritten"):
    // Outriders ignores the skulls: it writes the attack again after the cap.
    val script = woken
    val steps = script.history.steps
    val last = steps.last.after
    val tail = Vector[OathEvent](
      WalkerStepRecorded("attack-result", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("attack")), Vector(
          ModifyRollOutcome(CampaignIds.attackPool, Some(2), Some(6)),
          ModifyRollOutcome(CampaignIds.attackPool, Some(0), Some(8))),
        Vector.empty),
      WalkerCompleted(ActionRef.Campaign))
    val entries = formatter.format(steps ++ tail.zipWithIndex.map {
      case (event, index) => ReplayStep(RecordedEvent(steps.size.toLong + index,
        event), last, last) }, None)
    val attacks = texts(entries.filter(_.sequence >= steps.size))
      .filter(_.startsWith("Attack: "))
    assertEquals(attacks, Vector("Attack: 8"))
