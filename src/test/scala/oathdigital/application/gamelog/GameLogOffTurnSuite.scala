package oathdigital.application.gamelog

import oathdigital.testkit.Table.{p1, p2}
import LogScripts._
import OffTurnLogScripts._

/** An off-turn walk's lines read as an on-turn walk's, and its tag names
  * the actor (catalog batch 8, slice 14, ruling 12). */
class GameLogOffTurnSuite extends munit.FunSuite:
  private def lines(script: Script) =
    texts(format(script, None).filter(_.depth == 1))

  test("an off-turn Campaign's tag names its attacker and its source"):
    val all = lines(sneakAttackTaken)
    val tag = all.indexWhere(
      _.startsWith(s"${playerLabel(p2)} · Campaign · Sneak Attack"))
    assert(tag >= 0, all)
    assert(all(tag + 1).startsWith("Raid against "), all(tag + 1))

  test("an off-turn Campaign's lines omit its attacker"):
    val all = lines(sneakAttackTaken)
    val tag = all.indexWhere(_.contains(" · Campaign · Sneak Attack"))
    val after = all.drop(tag + 1)
    assert(after.exists(_.startsWith("Raid against ")), after)
    assert(!after.exists(_.startsWith(playerLabel(p2))), after)

  test("an on-turn Campaign's tag names no player"):
    val all = lines(sneakAttackTaken)
    assertEquals(all.headOption, Some("Campaign −2 Supply"))
    assert(!all.exists(_.startsWith(s"${playerLabel(p1)} · ")), all)

  test("a skipped off-turn offer names who skipped it"):
    val all = lines(sneakAttackSkipped)
    assert(all.contains(
      s"Sneak Attack: Campaign skipped by ${playerLabel(p2)}."), all)

  test("a defender's off-turn Travel posts its line under a tag naming " +
      "them, and the skipped Campaign names them too"):
    val all = lines(secondWindDefenderTravels)
    val tag = all.indexWhere(
      _.startsWith(s"${playerLabel(p2)} · Travel · Second Wind"))
    assert(tag >= 0, all)
    assert(all.drop(tag + 1).exists(_.contains("Buried Giant")), all)
    assert(all.contains(
      s"Second Wind: Campaign skipped by ${playerLabel(p2)}."), all)
