package oathdigital.application.gamelog

import oathdigital.model._
import LogScripts._

class GameLogDetailSuite extends munit.FunSuite:
  private def lines(script: Script, viewer: Option[PlayerId] = None)
      : Vector[String] = texts(format(script, viewer).filter(_.depth == 1))

  test("each Recover roll posts a Rolled line whose dice travel as a dice span"):
    val entries = format(recoverFailed, None)
    val rolls = entries.filter(_.kind == LogKind.Roll)
    assertEquals(rolls.size, 2, texts(entries))
    rolls.foreach { roll =>
      assertEquals(roll.spans.head, LogSpan.Text("Rolled "))
      val dice = roll.spans.collect { case dice: LogSpan.Dice => dice }
      assertEquals(dice.map(_.die), Vector("defense"))
      assert(dice.head.faces.forall(_ == "blank"), dice)
      assertEquals(dice.head.text, dice.head.faces.map(_ => "blank")
        .mkString(", "))
    }
    // The roll sits between the spend it bought and the next choice.
    val all = lines(recoverFailed)
    assert(all.indexWhere(_.startsWith("Rolled ")) >
      all.indexWhere(_.startsWith("Started Recover")), all)
