package oathdigital.testkit

import oathdigital.application.CampaignDicePort
import oathdigital.model.{AttackDieFace, DefenseDieFace}

/** The CampaignDicePort doubles for the tests that fix a roll through the
  * application service or a Situation, so one board comes up the same way
  * every run. Each stub says which faces it rolls; a spy counts the defense
  * rolls it was asked for, and its own doc says which faces it rolls. A
  * test that drives the rules directly hands them a WalkerDice from
  * `WalkerDiceFixture` instead, whose `blanks` and `raid` are the twins of
  * the two stubs of those names here. */
object Dice:
  /** Stub of CampaignDicePort: attack always HollowSword, defense always
    * Blank. A defense roll against it fails, so a Recover started with it
    * parks on the continue-or-stop choice. */
  val blanks: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.HollowSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.Blank)

  /** Stub of CampaignDicePort: attack always OneSword, defense always
    * TwoShields. */
  val steady: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.TwoShields)

  /** Stub of CampaignDicePort: attack always OneSword, defense always
    * Blank, dice that favour the attacker in a Raid. */
  val raid: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.OneSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.Blank)

  /** Stub of CampaignDicePort: attack always HollowSword, defense always
    * `faces`, whatever the count asked for. */
  def defending(faces: Vector[DefenseDieFace]): CampaignDicePort =
    new CampaignDicePort:
      def rollAttack(count: Int): Vector[AttackDieFace] =
        Vector.fill(count)(AttackDieFace.HollowSword)
      def rollDefense(count: Int): Vector[DefenseDieFace] = faces

  /** Stub of CampaignDicePort: rolls no dice, and shuffles every pile into
    * reverse order, so a test can see the adapter take the port's shuffle. */
  val reversingShuffle: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] = Vector.empty
    def rollDefense(count: Int): Vector[DefenseDieFace] = Vector.empty
    override def shuffle(count: Int): Vector[Int] =
      (0 until count).reverse.toVector

  /** Spy of CampaignDicePort: attack always HollowSword, defense always
    * `faces`; `calls` counts defense rolls. */
  final class CountingDefense(faces: Vector[DefenseDieFace])
      extends CampaignDicePort:
    var calls = 0
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.HollowSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      calls += 1
      faces

  /** Spy of CampaignDicePort: attack always HollowSword, the nth defense
    * roll shows `rolls(n)`; `calls` counts defense rolls. A roll past the
    * script throws. */
  final class ScriptedDefense(rolls: Vector[Vector[DefenseDieFace]])
      extends CampaignDicePort:
    var calls = 0
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.HollowSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      val faces = rolls(calls)
      calls += 1
      faces
