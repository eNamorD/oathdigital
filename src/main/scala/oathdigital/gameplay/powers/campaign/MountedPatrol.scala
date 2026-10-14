package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.model._

object MountedPatrolCard extends Denizen(DenizenId("163"), "Mounted Patrol", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.mounted-patrol"),
    persistent = false, cost = Cost.free,
    text = "The attacker rolls only half the [attack-die] in their " +
      "attack pool, rounded down. At end, discard Mounted Patrol.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Mounted Patrol (card 163), a defender's battle plan: "The attacker rolls
  * only half the [attack-die] in their attack pool, rounded down. At end,
  * discard Mounted Patrol."
  *
  * It is free. The pool is halved just before the attack roll, from the pool
  * as it stands then, so defender plans chosen after it still count: a
  * `wrapping` at the roll's leaf adds the halving before the roll. It is
  * offered even when the pool holds 0 or 1 dice. A bandit defender applies
  * it at a site it rules. The card is discarded through the standard discard
  * once the Campaign has resolved, whoever won, as Horse Archers is.
  *
  * It writes "The attack pool was halved to {n} dice.", with the dice left.
  */
final case class MountedPatrol private (catalog: ExecutableCatalog)
    extends BattlePlan with DiscardedAtEnd:
  val cardId: DenizenId = MountedPatrolCard.id
  def id: PowerId = MountedPatrol.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] = Set(CampaignPlanSide.Defender)
  override def planNoteKeys: Vector[NoteKey] =
    Vector(MountedPatrol.halved)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Mounted Patrol: halve the attack pool", Vector.empty, Vector.empty))

  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] = Map(
    PowerWindow.CampaignAttackRoll -> ((_, _, children) => Vector(
      MountedPatrol.halve,
      Note(id, states => Some(MountedPatrol.halved(PowerSourceRef.Card(cardId),
        NoteArg.Number(MountedPatrol.pool(states.now)))))) ++ children))

object MountedPatrol:
  val id: PowerId = MountedPatrolCard.power.id
  /** "The attack pool was halved to {n} dice." */
  val halved: NoteKey = NoteKey("halved", Vector(
    NotePart.Text("The attack pool was halved to "), NotePart.Arg(0),
    NotePart.Plural(0, " die.", " dice.")))

  /** The dice the attack pool holds now. */
  def pool(ready: ReadyGame): Int =
    ready.game.current.rollPools.get(CampaignIds.attackPool).fold(0)(_.count)

  /** Removes the half of the pool that is not rolled, rounded so that the
    * half rolled is rounded down. Nothing is written when nothing is cut. */
  val halve: Operation = BuildOps((ready, _) => {
    val cut = pool(ready) - pool(ready) / 2
    Right(Option.when(cut > 0)(
      ModifyDicePool(CampaignIds.attackPool, -cut)).toVector)
  })

  def forCatalog(catalog: ExecutableCatalog): MountedPatrol =
    new MountedPatrol(catalog)
