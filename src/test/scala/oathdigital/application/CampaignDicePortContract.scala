package oathdigital.application

import oathdigital.model.{AttackDieFace, DefenseDieFace}
import oathdigital.testkit.Dice

/** What every `CampaignDicePort` promises (the trait's doc): a pool of `n`
  * dice rolls `n` faces, and a shuffle of `n` cards is a permutation of
  * `0 until n`. The real random port and the shared stubs of `Dice` run it, so
  * a stub cannot hand a test a roll or an order the real port never gives. */
trait CampaignDicePortContract extends munit.FunSuite:
  /** Ports that roll exactly the size asked for. */
  protected def rollers: Vector[(String, CampaignDicePort)]
  /** Ports whose shuffle is under contract. */
  protected def shufflers: Vector[(String, CampaignDicePort)]

  rollers.foreach { (name, port) =>
    test(s"$name: a pool of n attack dice rolls n faces"):
      Vector(0, 1, 2, 5).foreach { count =>
        assertEquals(port.rollAttack(count).size, count, s"pool of $count")
      }

    test(s"$name: a pool of n defense dice rolls n faces"):
      Vector(0, 1, 2, 5).foreach { count =>
        assertEquals(port.rollDefense(count).size, count, s"pool of $count")
      }
  }

  shufflers.foreach { (name, port) =>
    test(s"$name: a shuffle of n cards is a permutation of 0 until n"):
      Vector(2, 3, 10).foreach { count =>
        assertEquals(port.shuffle(count).sorted, (0 until count).toVector,
          s"pile of $count")
      }
  }

/** Production: the random port the application service uses. */
class RandomCampaignDicePortSuite extends CampaignDicePortContract:
  protected def rollers = Vector("random" -> CampaignDicePort.random)
  protected def shufflers = Vector("random" -> CampaignDicePort.random)

  test("random shows every face of each die over many rolls"):
    // The sample is large enough that a face with chance 1 in 6 is missed
    // with probability below 1e-30, so this cannot flake in practice.
    assertEquals(CampaignDicePort.random.rollAttack(600).toSet,
      AttackDieFace.values.toSet)
    assertEquals(CampaignDicePort.random.rollDefense(600).toSet,
      DefenseDieFace.values.toSet)

/** The stubs the service tests share. The stubs that return fixed faces
  * whatever the count (`defending`, the counting and scripted spies) cannot
  * meet the size promise and are left out; `reversingShuffle` rolls no dice
  * and meets only the shuffle promise. */
class DiceStubsSuite extends CampaignDicePortContract:
  protected def rollers = Vector(
    "blanks" -> Dice.blanks,
    "steady" -> Dice.steady,
    "raid" -> Dice.raid)
  protected def shufflers = Vector(
    "blanks" -> Dice.blanks,
    "reversing shuffle" -> Dice.reversingShuffle)

/** The defense pair the Recover and Raid defenders roll. */
class RandomDefenseDicePortSuite extends munit.FunSuite:
  test("a roll shows as many defense faces as the port's dice count"):
    val port = DefenseDicePort.random
    assertEquals(port.rollTwo().size, port.diceCount)

  test("random shows every defense face over many rolls"):
    // 300 rolls of two dice; a face with chance 1 in 6 is missed with
    // probability below 1e-30.
    val faces = Vector.fill(300)(DefenseDicePort.random.rollTwo()).flatten
    assertEquals(faces.toSet, DefenseDieFace.values.toSet)
