package oathdigital.gameplay.powers.recover

import oathdigital.application.gamelog.LogScripts
import oathdigital.application.gamelog.LogScripts._
import oathdigital.testkit.Table.p1

/** A Recover with a power hooked at `RecoverAfterRelic`, read as the Game
  * Log shows it. Relic Worship appends its Supply gain to the node the
  * window names, so the recovered line must sit inside that node: outside
  * it, every operation the power appends would run first and any line one
  * of them posted would precede the line saying what was recovered
  * (procedures journal their own notes design; slice 2 regression 2).
  *
  * Relic Worship's own gain posts no line today, so nothing here can
  * observe that order going wrong; what keeps it right is the shape of the
  * windowed node, asserted in `RecoverProcedureSuite`. This suite pins the
  * other half: the recovered line is still posted with such a power in the
  * walk, and it is the last thing the Recover says, so the day a
  * `RecoverAfterRelic` power does post a line, this fails.
  */
class RelicWorshipLogSuite extends munit.FunSuite:
  test("the recovered line is the last line of a worshipped Recover"):
    val entries = format(LogScripts.recoverWorship, Some(p1))
    val section = entries.filter(_.section.nonEmpty)
    val recovered = texts(section).indexWhere(_.startsWith("Recovered "))
    assert(recovered >= 0, texts(section))
    assertEquals(recovered, section.size - 1, texts(section))
