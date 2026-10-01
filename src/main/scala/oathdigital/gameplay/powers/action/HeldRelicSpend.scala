package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts, PowerAnswers}
import oathdigital.model._

/** A relic the player holds, faceup or facedown, chosen and then spent:
  * Arcane Brokers and Bog discard it, Relic Breaker buries it (catalog batch
  * 3 rulings, slice 3a).
  *
  * Two steps. A live `Branch` asks whenever the player holds a relic, even
  * one. A required batch then spends the chosen relic and gains the reward
  * together, so the reward comes only with the spending ("X to gain Y"). The
  * Grand Scepter's restriction refuses its discard and its bury, and the
  * batch is required, so the search hides the scepter. The decision is passed
  * when the search leaves it empty (`Decide.passWhenEmpty`), so a player
  * holding only the scepter spends and gains nothing.
  *
  * @param spend the spending and the reward for the chosen relic, as it
  *   stands in the player's play area, built against the live state. A reward
  *   that takes from a bank is capped at what the bank holds, because a
  *   required batch refuses a gain its bank cannot give in full.
  */
final class HeldRelicSpend(decisionId: String, heading: String,
    spend: (ReadyGame, PlayerId, RelicState) => Vector[CoreOperation]):
  def steps(player: PlayerId): Vector[Operation] = Vector(
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => spent(live, player, pending), required = true))

  /** The relic the step before the note took from the player, with the
    * tokens it carried then. Nothing when no relic was spent. */
  def spentRelic(states: NoteStates, player: PlayerId)
      : Option[(RelicId, Tokens)] = for
    step <- states.previous
    relic <- NoteSupport.relicsLost(step, player).headOption
    before <- held(step._1, player).find(_.id == relic)
  yield (relic, before.tokens)

  /** Whether the player holds no relic at all now. */
  def emptyHanded(states: NoteStates, player: PlayerId): Boolean =
    held(states.now, player).isEmpty

  private def held(ready: ReadyGame, player: PlayerId): Vector[RelicState] =
    PlayerFacts.player(ready, player).toOption.toVector.flatMap(_.relics)

  private def ask(ready: ReadyGame, player: PlayerId): Vector[Operation] =
    held(ready, player) match
      case Vector() => Vector.empty
      case relics => Vector(Decide(decisionId, player, DecisionQuery.ChooseOne(
        relics.map(relic => DecisionOption.Relic(
          DecisionOptionRef.Relic(relic.id))), heading = Some(heading)),
        passWhenEmpty = true))

  /** No answer means the search left no relic to offer. */
  private def spent(ready: ReadyGame, player: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => held(ready, player)
        .find(relic => DecisionOptionRef.Relic(relic.id) == ref)
        .toRight(OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not a relic the player holds"))
        .map(spend(ready, player, _))
