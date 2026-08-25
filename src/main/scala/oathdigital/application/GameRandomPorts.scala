package oathdigital.application

import oathdigital.gameplay.OathViolation
import oathdigital.gameplay.actions.SearchRules
import oathdigital.model._


trait DefenseDicePort {
  def rollTwo(): Vector[DefenseDieFace]
}
object DefenseDicePort {
  val random: DefenseDicePort = new DefenseDicePort {
    private val rng = new scala.util.Random()
    private val faces = Vector(DefenseDieFace.Blank, DefenseDieFace.Blank,
      DefenseDieFace.OneShield, DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields, DefenseDieFace.Doubler)
    def rollTwo(): Vector[DefenseDieFace] = Vector.fill(2)(faces(rng.nextInt(6)))
  }
}

trait CampaignDicePort {
  def rollAttack(count: Int): Vector[AttackDieFace]
  def rollDefense(count: Int): Vector[DefenseDieFace]
}
object CampaignDicePort {
  val random: CampaignDicePort = new CampaignDicePort {
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
  }
}

object CardDecisionIds {
  /** Stable across reload/replay and derived solely from authoritative setup progress. */
  def startingAdviser(playerId: PlayerId, placementIndex: Int): DecisionId =
    DecisionId(s"setup-adviser-${placementIndex}-${playerId.value}")
}

trait SearchDrawPort {
  def prepare(
      ready: oathdigital.gameplay.ReadyGame,
      source: SearchSource,
      origin: Region
  ): Either[OathViolation, Vector[WorldCardId]]
}

trait RelicDrawPort {
  def prepare(ready: oathdigital.gameplay.ReadyGame): Either[OathViolation, RelicId]
}
object RelicDrawPort {
  val authoritative: RelicDrawPort = new RelicDrawPort {
    def prepare(ready: oathdigital.gameplay.ReadyGame) =
      ready.game.current.commonCards.relicDeck.headOption
        .toRight(OathViolation.ForgeUnavailable("relic deck is empty"))
  }
}
object SearchDrawPort {
  val authoritative: SearchDrawPort = new SearchDrawPort {
    def prepare(ready: oathdigital.gameplay.ReadyGame, source: SearchSource,
        origin: Region) = SearchRules.draw(ready, source, origin)
  }
}
