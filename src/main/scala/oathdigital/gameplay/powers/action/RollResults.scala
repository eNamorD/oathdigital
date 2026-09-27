package oathdigital.gameplay.powers.action

import oathdigital.model._

/** Reads the outcome an automatic `Roll` wrote into state. A pool that never
  * rolled reads as zero, so a `BuildOps` that runs after a skipped roll needs
  * no case of its own.
  */
private[action] object RollResults:
  /** Shields for a defense roll, swords for an attack roll, scored by
    * `DefenseDieFace.score` and `AttackDieFace.score` when the roll was
    * recorded.
    */
  def score(ready: ReadyGame, pool: PoolKey): Int =
    ready.game.current.rollOutcomes.get(pool).fold(0)(_.score)

  /** Skull faces rolled in an attack pool. */
  def skulls(ready: ReadyGame, pool: PoolKey): Int =
    ready.game.current.rollOutcomes.get(pool).fold(0)(_.skulls)

  /** "{player} rolled {dice}, Total: {n}": a dice power's own line, in place
    * of the generic "Rolled" line it covers. */
  val rolled: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" rolled "), NotePart.Arg(1), NotePart.Text(", Total: "),
    NotePart.Arg(2)))

  /** The roll `pool` recorded, restated with its total. */
  def rollNote(source: DecisionOptionRef, player: PlayerId, pool: PoolKey)(
      states: NoteStates): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    outcome <- states.now.game.current.rollOutcomes.get(pool)
  yield rolled(card, NoteArg.Player(player), NoteArg.Dice(outcome.faces),
    NoteArg.Number(score(states.now, pool)))
