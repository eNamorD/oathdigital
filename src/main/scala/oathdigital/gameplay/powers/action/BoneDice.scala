package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}
import oathdigital.model._

/** Bone Dice (relic R24), ACTION: place 1 secret on this relic, roll 2 attack
  * dice, gain Supply equal to the sword score, then bury this relic if any
  * skull face rolled.
  *
  * The bury uses the standard returns, so the secret the cost just placed on
  * the relic goes back to its holder facedown. The tokens are read inside the
  * `BuildOps`, after the engine has paid the cost, and not in `build`, which
  * runs before it.
  */
case object BoneDice extends PaidAction("relic.bone-dice", Cost(secret = 1)):
  val Dice: Int = 2
  val pool: PoolKey = PoolKey("bone-dice")
  val gained: NoteKey = NoteSupport.gainedKey("gained")
  /** The bury a skull forces, in place of the generic Buried line. */
  val buried: NoteKey = NoteKey("buried", Vector(
    NotePart.Text("Buried after a skull.")))
  override def noteKeys: Vector[NoteKey] = Vector(RollResults.rolled, gained,
    buried)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = source match
    case DecisionOptionRef.Relic(relic) => Right(Sequence(Vector(
      ModifyDicePool(pool, Dice),
      Roll(pool, DiceSpec(DiceKind.Attack), RollMode.Automatic),
      Note(id, RollResults.rollNote(source, player, pool), covers = true),
      BuildOps((state, _) => settle(state, player, relic)),
      Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
        NoteSupport.supply)),
      Note(id, buryNote(_, player, source), covers = true))))
    case other => Left(OathViolation.InvalidEventOrder(
      s"${other.kind} is not a relic source"))

  /** The settle step buried the relic. It covers that step, whose only
    * generic line is the Buried line: `GainSupply` posts none. */
  private def buryNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    if NoteSupport.relicsLost(step, player).nonEmpty
  yield buried(card)

  private def settle(state: ReadyGame, player: PlayerId, id: RelicId)
      : Either[OathViolation, Vector[CoreOperation]] = for
    held <- PlayerFacts.player(state, player)
    relic <- held.relics.find(_.id == id).toRight(
      OathViolation.InvalidEventOrder(
        s"${id.value} is not held by ${player.value}"))
  yield
    val supply = RollResults.score(state, pool)
    val gain: Vector[CoreOperation] =
      if supply > 0 then Vector(GainSupply(player, supply)) else Vector.empty
    val bury: Vector[CoreOperation] =
      if RollResults.skulls(state, pool) > 0 then
        Bury.standard(BuryableCard.Relic(id),
          PositionedLocation(Location.PlayArea(player)), None, 0,
          relic.tokens.secrets, player)
      else Vector.empty
    gain ++ bury
