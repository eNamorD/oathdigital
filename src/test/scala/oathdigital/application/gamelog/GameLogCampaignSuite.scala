package oathdigital.application.gamelog

import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.walker.{ChoicePayload, DeltaMeaning,
  WalkerCompleted, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Table.p2
import LogScripts._

class GameLogCampaignSuite extends munit.FunSuite:
  private def lines(script: Script, viewer: Option[PlayerId] = None) =
    texts(format(script, viewer).filter(_.depth == 1))

  test("a Raid tells its targets, pools, dice, totals, winner and losses in order"):
    val script = raid
    val all = lines(script)
    // The raid script's p1 raids p2, who shares its site.
    val defender = p2
    val expected = Vector(
      "Campaign −",
      s"Raid against ${playerLabel(defender)}",
      s"Targets: ${playerLabel(defender)}'s pawn",
      "Attack Pool: ",
      "Rolled ",
      "Attack: ",
      "Sacrificed ",
      "Rolled ",
      "Defense: ",
      s"${playerLabel(script.actor)} wins!",
      s"${playerLabel(defender)} ")
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
    assert(all.last.startsWith(s"${playerLabel(defender)} ") &&
      all.last.contains(" was sent to "), all)

  test("a battle plan answer names who activated it; a flipped plan card names itself"):
    val script = recordedEventsBoard
    val steps = script.history.steps
    // The board's p2 holds Birdsong facedown; it defends with it as a plan.
    val defender = p2
    val plan = CatalogNames.denizen("Birdsong")
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
    val entries = formatAppended(script, tail)
    val shown = texts(entries.filter(_.sequence >= steps.size))
    assert(shown.contains(s"${playerLabel(defender)} activated a Denizen"),
      shown)
    val revealedPrefix = s"${playerLabel(defender)} revealed "
    assert(shown.exists(line => line.startsWith(revealedPrefix)
      && line != s"${playerLabel(defender)} revealed a Denizen"), shown)

  test("a plan the bandits apply is named like any other activation"):
    val script = recordedEventsBoard
    val steps = script.history.steps
    // The board's ruined Hiding Place at Deep Woods, a site card, which is
    // what a bandit plan's source is.
    val edifice = CatalogNames.edifice("Hiding Place")
    val tail = Vector[OathEvent](
      WalkerStepRecorded("bandit-plan", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("plan")), Vector(ModifyDicePool(
          CampaignPlans.appliedMarker(DecisionOptionRef.Edifice(edifice)), 1)),
        Vector.empty),
      WalkerCompleted(ActionRef.Campaign))
    val entries = formatAppended(script, tail)
    val shown = texts(entries.filter(_.sequence >= steps.size))
    // A site card is public, so even an observer reads its name.
    assert(shown.exists(line => line.startsWith("The bandits activated ") &&
      !line.endsWith("Edifice") && !line.endsWith("Denizen") &&
      !line.contains("campaign.plan-applied")), shown)

  test("a total a plan rewrites in the same segment is told once, as rewritten"):
    // Outriders ignores the skulls: it writes the attack again after the cap.
    // Only the board's last state is read.
    val script = recordedEventsBoard
    val steps = script.history.steps
    val tail = Vector[OathEvent](
      WalkerStepRecorded("attack-result", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("attack")), Vector(
          ModifyRollOutcome(CampaignIds.attackPool, Some(2), Some(6)),
          ModifyRollOutcome(CampaignIds.attackPool, Some(0), Some(8))),
        Vector.empty),
      WalkerCompleted(ActionRef.Campaign))
    val entries = formatAppended(script, tail)
    val attacks = texts(entries.filter(_.sequence >= steps.size))
      .filter(_.startsWith("Attack: "))
    assertEquals(attacks, Vector("Attack: 8"))
