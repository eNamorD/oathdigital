package oathdigital.application.gamelog

import oathdigital.testkit.Table.p2
import LogScripts._
import PalanquinLogScripts._

/** Palanquin's line, and the target's Travel tagged with them (catalog
  * batch 8, slice 15, ruling 16). */
class GameLogPalanquinSuite extends munit.FunSuite:
  private def lines(script: Script) =
    texts(format(script, None).filter(_.depth == 1))

  test("Palanquin's run says where the user went and who follows"):
    val all = lines(palanquinSendsP2ToBuriedGiant)
    assert(all.contains(
      s"Palanquin: Moved to Buried Giant; ${playerLabel(p2)} travels " +
        "there."), all)

  test("the target's Travel is tagged with them and Palanquin, and reads " +
      "as their own"):
    val all = lines(palanquinSendsP2ToBuriedGiant)
    val tag = all.indexWhere(
      _.startsWith(s"${playerLabel(p2)} · Travel · Palanquin"))
    assert(tag >= 0, all)
    val after = all.drop(tag + 1)
    // The sent Travel journals its own line, as a picked one does
    // (procedures journal their own notes design, slice 2).
    assert(after.contains("Travelled to Buried Giant"), after)
    assert(!after.exists(_.startsWith(playerLabel(p2))), after)
