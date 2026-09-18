package oathdigital.gameplay

import oathdigital.model.OathState.Ready
import oathdigital.gameplay.operations.{BuildOps, Operation}
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** A synthetic phase power on the active player's faceup adviser, shared
  * by the engine suite and the projection suite (Task 11).
  */
object PhasePowerFixture {
  final case class TestPower(id: PowerId, timing: PowerTiming,
      tree: PlayerId => Operation = _ => BuildOps((_, _) => Right(Vector.empty)))
      extends PhasePower {
    def usable(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef) = true
    def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef) =
      Right(tree(player))
  }

  /** The active player holds one faceup adviser with a catalog power, and a
    * site with capacity has no force, so the action boundary visibly refills
    * bandits.
    */
  val (base, actor, card, powerId) = {
    val Ready(ready) = execute(new FirstGameSetupRules(catalog))._1: @unchecked
    val current = ready.game.current
    val actor = current.turn.activePlayer
    val deck = current.commonCards.worldDeck.collect { case id: DenizenId => id }
    val card = catalog.denizens.collectFirst {
      case d if d.powers.nonEmpty && deck.contains(DenizenId(d.id.value)) =>
        DenizenId(d.id.value)
    }.get
    val empty = current.map.inPlay.find(id =>
      catalog.sites.find(_.id == id).exists(_.capacity > 0)).get
    val arranged = ready.copy(game = ready.game.copy(current = current.copy(
      players = current.players.map(p => if (p.player != actor) p else
        p.copy(advisers = Vector(DenizenState(card, Orientation.FaceUp,
          Tokens.empty)))),
      map = current.map.copy(sites = current.map.sites.updated(empty,
        current.map.sites(empty).copy(forces = SiteForces.Empty))),
      commonCards = current.commonCards.copy(worldDeck =
        current.commonCards.worldDeck.filterNot(_ == card)))))
    val powerId = RuleSourceIndex.enumerate(catalog, arranged).collectFirst {
      case IndexedRuleSource(RuleSourceRef.Adviser(`actor`, `card`), ids, _, _)
          if ids.nonEmpty => ids.head
    }.get
    (arranged, actor, card, powerId)
  }

  def inPhase(phase: Phase) = base.copy(game = base.game.copy(current =
    base.game.current.copy(turn = TurnState(actor, phase, Set.empty))))
  val source = DecisionOptionRef.Denizen(card)
}
