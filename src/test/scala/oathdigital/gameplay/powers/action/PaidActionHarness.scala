package oathdigital.gameplay.powers.action

import oathdigital.gameplay.{CampaignFixture, OathRules}
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._
import oathdigital.serialization.GameEventWire
import oathdigital.model.OathState.Ready

/** Drives an Act phase power through the rules, the way `MagicWaterskinSuite`
  * does, with fixed dice and a journal replay check. Shared by the slice 1b
  * suites.
  */
object PaidActionHarness {
  import PowerFixture._

  /** Rules with every production phase power. Dice fail loudly unless given,
    * so a power that must not roll proves it by not asking.
    */
  def rules(dice: WalkerDice = WalkerDice.unavailable): OathRules =
    new OathRules(catalog,
      phasePowerCatalog = PhasePowerCatalog.default(catalog),
      walkerDice = dice)

  def defenseDice(faces: DefenseDieFace*): WalkerDice =
    CampaignFixture.dice(defense = faces.toVector)

  def attackDice(faces: AttackDieFace*): WalkerDice =
    CampaignFixture.dice(attack = faces.toVector)

  def act(ready: ReadyGame): ReadyGame = inPhase(ready, Phase.Act)

  def use(rules: OathRules, ready: ReadyGame, id: PowerId,
      source: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.startWalker(Ready(ready), ActionRef.UsePower(id), actor,
      Vector.empty, Vector(source))

  def answer(rules: OathRules, state: OathState, decisionId: String,
      ref: DecisionOptionRef): Either[OathViolation, OathTransition] =
    rules.resolveWalker(state, actor, decisionId,
      DecisionAnswer.ChooseOneAnswer(ref))

  def ready(state: OathState): ReadyGame = state.asInstanceOf[Ready].value

  /** Ids of the phase powers the actor can use now. */
  def usableIds(ready: ReadyGame): Vector[PowerId] =
    PhasePowerProcedure.usable(catalog, ready, actor,
      PhasePowerCatalog.default(catalog)).map(_.power.id)

  /** The state a journal replay of `events` reaches from `from`. */
  def replayed(rules: OathRules, from: ReadyGame, events: Vector[OathEvent])
      : ReadyGame = ready(events.foldLeft[Either[OathViolation, OathState]](
    Right(Ready(from))) {
    case (state, event) => state.flatMap(rules.evolve(_, event))
  }.toOption.get)

  /** Every event survives the journal wire: encoded, decoded and equal. */
  def wireRoundTrips(events: Vector[OathEvent]): Boolean =
    events.zipWithIndex.forall { case (event, index) =>
      GameEventWire.encodeEvent("g", catalog.ref, index.toLong, event).toOption
        .flatMap(GameEventWire.decode(_).toOption).map(_.event)
        .contains(event)
    }

  /** The tokens on card `id` wherever it sits. */
  def tokensOn(state: ReadyGame, id: CardId): Tokens = {
    val current = state.game.current
    val onSites = current.map.sites.values.flatMap(_.denizens.collect {
      case card: DenizenState if card.id == id => card.tokens
      case card: EdificeState if card.id == id => card.tokens
    })
    val held = current.players.flatMap(_.relics.collect {
      case relic if relic.id == id => relic.tokens
    })
    (onSites ++ held).head
  }

  /** The actor's secrets, faceup and facedown together. */
  def secrets(state: ReadyGame): Int =
    player(state).board.faceUpSecrets + player(state).board.faceDownSecrets
}
