package oathdigital.application.gamelog

import oathdigital.model._
import oathdigital.testkit.Table.{p2, p3}
import CampaignLogScripts.{banditPlanApplied, defenderChoseAPlan,
  raidUnderOutriders}
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
    // The loss sentence is three sentences now, one per node that applies
    // its clauses (slice 4). Half of a one-warband board is none, so
    // "lost" may be absent; the relocation never is, and it is last.
    assert(all.last.startsWith(s"${playerLabel(defender)} ") &&
      all.last.contains(" was sent to "), all)
    assert(!all.last.contains(" lost "), all.last)

  test("a plan a defender chose names the defender, and a bandit's names " +
      "the bandits"):
    val chosen = lines(defenderChoseAPlan)
    assert(chosen.exists(line =>
      line == s"${playerLabel(p2)} activated Towering Rampart"), chosen)
    val bandits = lines(banditPlanApplied)
    assert(bandits.contains("The bandits activated Towering Rampart"),
      bandits)

  test("a total a plan rewrites is told once, after the plan's own line"):
    val all = lines(raidUnderOutriders)
    val ignored = all.indexOf("Outriders: Skulls ignored.")
    val attacks = all.zipWithIndex.filter(_._1.startsWith("Attack: "))
    assertEquals(attacks.size, 1, all)
    assert(ignored >= 0 && attacks.head._2 > ignored, all)

  // The rule named a discarded card at the before and after of its own
  // Move; the note names it at the action's start, the restated leaf's
  // before-state and the state after the note. The two state sets must
  // agree for Campaign's cards, or the difference belongs in the ledger
  // (slice 4 plan-time question 7).
  test("a defeated Raid defender's cards read the same for every seat"):
    val script = CampaignLogScripts.raidTakingRelicAndBanner
    val forDefender = lines(script, Some(p2))
    val forAttacker = lines(script, Some(script.actor))
    val forThird = lines(script, Some(p3))
    def clause(from: Vector[String]): String =
      from.find(_.contains(" burned ")).getOrElse(fail(s"no clause: $from"))
    // The defender's own facedown adviser is named to them and to nobody
    // else, which is the one difference the visibility rule makes.
    assert(clause(forDefender).contains("Birdsong"), forDefender)
    assertEquals(clause(forAttacker), clause(forThird))
    assert(clause(forAttacker).contains("a Denizen"), forAttacker)

  test("under Careful Plans the defense is rolled and told before the " +
      "attack"):
    val script = raidUnderCarefulPlans
    val all = lines(script)
    val from = all.indexOf("Activated Careful Plans")
    assert(from >= 0, all)
    // Dice.raid rolls a sword on every attack die and a blank on every
    // defense die. The three warbands sacrificed are added to the attack
    // after it is rolled, as in the printed order.
    assertEquals(all.slice(from, from + 7), Vector(
      "Activated Careful Plans",
      "Rolled blank, blank for the defense",
      "Defense: 3",
      "Rolled sword, sword, sword for the attack",
      "Attack: 3",
      "Sacrificed 3 warbands for an attack of 6",
      s"${playerLabel(script.actor)} wins!"))

  // The loss note sits inside the losses node, right after the leaf that
  // applies the Campaign's own kills, so a power that appends more kills
  // in the same window (Sticky Fire) is not what it reads. Moving the
  // note after the node makes this count the burn instead.
  test("a Campaign's loss line counts its own kills, not a power's"):
    val all = lines(CampaignLogScripts.raidBurningWhatIsLeft)
    assert(all.contains(s"${playerLabel(p2)} lost 2 warbands"), all)
    assert(all.exists(line => line.startsWith("Sticky Fire: Killed 3 ")),
      all)
