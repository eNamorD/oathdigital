package oathdigital.application

import oathdigital.gameplay.actions.SearchRules
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._


/** `diceCount` (I4) is the number of dice `rollTwo` produces -- named here
  * as the port's own declared capacity so a caller validating a parked
  * pool's requested count (e.g. `GameApplicationService`'s `RollWalker`
  * handling) checks against this instead of a bare literal `2` scattered at
  * the call site.
  */
trait DefenseDicePort:
  val diceCount: Int = 2
  def rollTwo(): Vector[DefenseDieFace]
object DefenseDicePort:
  val random: DefenseDicePort = new DefenseDicePort:
    private val rng = new scala.util.Random()
    private val faces = Vector(DefenseDieFace.Blank, DefenseDieFace.Blank,
      DefenseDieFace.OneShield, DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields, DefenseDieFace.Doubler)
    def rollTwo(): Vector[DefenseDieFace] = Vector.fill(diceCount)(faces(rng.nextInt(6)))

trait CampaignDicePort:
  def rollAttack(count: Int): Vector[AttackDieFace]
  def rollDefense(count: Int): Vector[DefenseDieFace]
  /** A uniform order for a pile of `count` cards, as a permutation of
    * `0 until count`. A test port may fix it. */
  def shuffle(count: Int): Vector[Int] = CampaignDicePort.uniform(count)
object CampaignDicePort:
  val random: CampaignDicePort = new CampaignDicePort:
    private val rng = new scala.util.Random()
    private val attack = Vector(
      AttackDieFace.HollowSword, AttackDieFace.HollowSword,
      AttackDieFace.HollowSword, AttackDieFace.OneSword,
      AttackDieFace.OneSword, AttackDieFace.TwoSwordsSkull)
    private val defense = Vector(DefenseDieFace.Blank, DefenseDieFace.Blank,
      DefenseDieFace.OneShield, DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields, DefenseDieFace.Doubler)
    def rollAttack(count: Int) = Vector.fill(count)(attack(rng.nextInt(6)))
    def rollDefense(count: Int) = Vector.fill(count)(defense(rng.nextInt(6)))

  /** The walker's dice source, backed by `port`: the same faces a legacy
    * Campaign rolled, now drawn by an automatic `Roll` node, and the port's
    * pile order for a `Shuffle`.
    */
  def walkerDice(port: CampaignDicePort): WalkerDice = new WalkerDice:
    def roll(kind: DiceKind, count: Int)
        : Either[OathViolation, Vector[DieFace]] = Right(kind match {
      case DiceKind.Attack => port.rollAttack(count)
      case DiceKind.Defense => port.rollDefense(count)
    })
    override def shuffle(count: Int): Either[OathViolation, Vector[Int]] =
      Right(port.shuffle(count))

  private val shuffler = new scala.util.Random()
  private def uniform(count: Int): Vector[Int] =
    shuffler.shuffle((0 until count).toVector)

object CardDecisionIds:
  /** Stable across reload/replay and derived solely from authoritative setup progress. */
  def startingAdviser(playerId: PlayerId, placementIndex: Int): DecisionId =
    DecisionId(s"setup-adviser-${placementIndex}-${playerId.value}")

trait SearchDrawPort:
  def prepare(
      ready: oathdigital.model.ReadyGame,
      source: SearchSource,
      origin: Region
  ): Either[OathViolation, Vector[WorldCardId]]

object SearchDrawPort:
  val authoritative: SearchDrawPort = new SearchDrawPort:
    def prepare(ready: oathdigital.model.ReadyGame, source: SearchSource,
        origin: Region) = SearchRules.draw(ready, source, origin)
