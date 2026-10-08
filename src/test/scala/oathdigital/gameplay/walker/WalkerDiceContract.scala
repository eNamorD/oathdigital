package oathdigital.gameplay.walker

import oathdigital.application.CampaignDicePort
import oathdigital.gameplay.WalkerDiceFixture
import oathdigital.model.{AttackDieFace, DefenseDieFace, DiceKind, DieFace}
import oathdigital.testkit.Unwrap.*

/** One dice source under contract: a name for the test title, the source, and
  * the pool sizes it can answer (a stub that returns fixed faces answers one
  * size only). */
final case class DiceSubject(name: String, dice: WalkerDice,
    poolSizes: Vector[Int] = Vector(1, 2, 5))

/** What the walker asks of every `WalkerDice` that rolls (the trait's doc): a
  * pool of `n` attack dice rolls `n` attack faces, and a pool of `n` defense
  * dice rolls `n` defense faces. The production adapter over the campaign dice
  * port and the shared stubs of `WalkerDiceFixture` run it, so a stub cannot
  * hand a test a roll the real source never gives. */
trait WalkerDiceRollContract extends munit.FunSuite:
  /** The sources to hold to the contract; a `def`, so each test gets its own. */
  protected def rollers: Vector[DiceSubject]

  rollers.foreach { subject =>
    test(s"${subject.name}: a pool of n attack dice rolls n attack faces"):
      subject.poolSizes.foreach { count =>
        val faces: Vector[DieFace] =
          accepted(subject.dice.roll(DiceKind.Attack, count))
        assertEquals(faces.size, count, s"pool of $count")
        assertEquals(faces.collect { case face: AttackDieFace => face }, faces,
          s"pool of $count")
      }

    test(s"${subject.name}: a pool of n defense dice rolls n defense faces"):
      subject.poolSizes.foreach { count =>
        val faces: Vector[DieFace] =
          accepted(subject.dice.roll(DiceKind.Defense, count))
        assertEquals(faces.size, count, s"pool of $count")
        assertEquals(faces.collect { case face: DefenseDieFace => face }, faces,
          s"pool of $count")
      }
  }

/** What the walker asks of a `WalkerDice` that shuffles: an order for a pile
  * of `n` cards is a permutation of `0 until n` (the trait's doc). */
trait WalkerDiceShuffleContract extends munit.FunSuite:
  protected def shufflers: Vector[(String, WalkerDice)]

  shufflers.foreach { (name, dice) =>
    test(s"$name: a shuffle of n cards is a permutation of 0 until n"):
      Vector(2, 3, 10).foreach { count =>
        assertEquals(accepted(dice.shuffle(count)).sorted,
          (0 until count).toVector, s"pile of $count")
      }
  }

/** Production: the walker's dice backed by the real random campaign port. */
class CampaignWalkerDiceSuite
    extends WalkerDiceRollContract, WalkerDiceShuffleContract:
  private def production = CampaignDicePort.walkerDice(CampaignDicePort.random)
  protected def rollers = Vector(DiceSubject("random campaign dice", production))
  protected def shufflers = Vector("random campaign dice" -> production)

  test("random campaign dice show every face of each die over many rolls"):
    // The sample is large enough that a face with chance 1 in 6 is missed
    // with probability below 1e-30, so this cannot flake in practice.
    val attack = accepted(production.roll(DiceKind.Attack, 600)).toSet
    val defense = accepted(production.roll(DiceKind.Defense, 600)).toSet
    assertEquals(attack, AttackDieFace.values.toSet[DieFace])
    assertEquals(defense, DefenseDieFace.values.toSet[DieFace])

/** The placeholder source simulations use. */
class PlaceholderWalkerDiceSuite
    extends WalkerDiceRollContract, WalkerDiceShuffleContract:
  protected def rollers = Vector(DiceSubject("placeholder dice",
    WalkerDice.placeholder))
  protected def shufflers = Vector("placeholder dice" -> WalkerDice.placeholder)

/** The stubs the walker tests share. `dice(...)`, `scripted` and the
  * `shufflingBy` source answer one shape only, so they sit in the part of the
  * contract they can meet. */
class WalkerDiceFixtureSuite
    extends WalkerDiceRollContract, WalkerDiceShuffleContract:
  protected def rollers = Vector(
    DiceSubject("blanks", WalkerDiceFixture.blanks),
    DiceSubject("raid", WalkerDiceFixture.raid),
    DiceSubject("shields", WalkerDiceFixture.shields),
    DiceSubject("swords cycling defense", WalkerDiceFixture
      .swordsCyclingDefense(DefenseDieFace.Blank, DefenseDieFace.OneShield)),
    DiceSubject("swords, single then double shields",
      WalkerDiceFixture.swordsSingleThenDoubleShields),
    DiceSubject("skull then swords", WalkerDiceFixture.skullThenSwords),
    DiceSubject("exact faces", WalkerDiceFixture.dice(
      attack = Vector(AttackDieFace.OneSword, AttackDieFace.HollowSword),
      defense = Vector(DefenseDieFace.Doubler, DefenseDieFace.Blank)),
      poolSizes = Vector(2)))
  protected def shufflers = Vector(
    "reversing shuffle" -> WalkerDiceFixture.shufflingBy(count =>
      (0 until count).reverse.toVector),
    "unchanged shuffle" -> WalkerDiceFixture.shufflingBy(count =>
      (0 until count).toVector))
