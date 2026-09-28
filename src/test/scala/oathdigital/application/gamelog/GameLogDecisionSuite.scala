package oathdigital.application.gamelog

import LogScripts._

class GameLogDecisionSuite extends munit.FunSuite:
  private def lines(script: Script): Vector[String] =
    texts(format(script, None).filter(_.depth == 1))

  test("a power's own decision posts one Chose line naming what was shown"):
    val all = texts(formatWithoutNotes(usePower, None).filter(_.depth == 1))
    val chose = all.filter(_.startsWith("Chose "))
    assertEquals(chose.size, 1, all)
    assert(!chose.head.contains("Button"), chose.head)
    // The line follows the power's own "Used" line.
    assert(all.indexWhere(_.startsWith("Used ")) < all.indexOf(chose.head), all)

  test("a power's own line follows the Chose line of the choice it restates"):
    val all = lines(usePower)
    val chose = all.indexWhere(_.startsWith("Chose "))
    assert(chose >= 0, all)
    assert(all(chose + 1).startsWith("Silver Tongue: "), all)
    assert(!all.exists(_.startsWith("Used ")), all)

  test("decisions an action line already tells post no Chose line"):
    Vector(search, facedownAdviser, muster, trade, recoverFailed,
      recoverSucceeded, negotiationDeclined, negotiationAgreed,
      oathkeeper, woken).foreach { script =>
      val all = lines(script)
      assert(!all.exists(_.startsWith("Chose ")), s"${script.name}: $all")
    }
