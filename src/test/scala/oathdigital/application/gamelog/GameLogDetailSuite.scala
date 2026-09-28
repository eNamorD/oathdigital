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
  // Trade's favor is arranged in the first player's turn, before their
  // Rest, so its gain names the second player, whose turn it is not yet.
  test("an arranged favor gain reads as a gain from its bank"):
    val script = trade
    val all = lines(script)
    assert(all.contains(
      s"${name(script.actor)} gained 1 favor from the Discord bank"), all)

  test("Trade's own gain is its action line, not a second gain line"):
    val all = lines(trade)
    assertEquals(all.count(_.contains("ained 1 favor")), 1, all)

  test("Search: the kept card's placement posts after the draw line"):
    val all = lines(search)
    val drew = all.indexWhere(_.startsWith("Drew "))
    val placed = all.indexWhere(line => line.startsWith("Discarded ") ||
      line.startsWith("Played "))
    assert(drew >= 0 && placed > drew, all)

  test("Search: the cards not kept are discarded to a regional discard"):
    val all = lines(search)
    assert(all.exists(line =>
      "^Discarded .+ to the (Cradle|Provinces|Hinterland) discard$".r
        .matches(line)), all)

  test("a facedown adviser played faceup is told once, by the action line"):
    val all = lines(facedownAdviser("revealed-adviser", Some("adviser-faceup")))
    assert(all.exists(line => line.startsWith("Played ") &&
      line.endsWith(" as an adviser")), all)
    assert(!all.exists(_.startsWith("Revealed ")), all)

  test("the placement discard is told once, by the action line"):
    val all = lines(facedownAdviser)
    assertEquals(all.count(_.startsWith("Discarded ")), 1, all)
    // The action line names the pile, as every discard line does.
    assert(all.exists(line =>
      "^Discarded .+ to the (Cradle|Provinces|Hinterland) discard$".r
        .matches(line)), all)
