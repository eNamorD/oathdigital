package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.gameplay.powers.{NoteSupport, PlayerFacts}
import oathdigital.model._

object CursedCauldronCard extends Relic(RelicId("R02"), "Cursed Cauldron", value = 84, defense = 2):
  val power = PrintedPower(PowerId("relic.cursed-cauldron"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious, gain 1 warband per enemy warband " +
      "killed in this campaign.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Cursed Cauldron (relic R02), a battle plan for either side: "If you're
  * victorious, gain 1 warband per enemy warband killed in this campaign."
  *
  * Free; the relic must be faceup in its user's play area, so bandits never
  * use it. Once chosen, it counts the kills of its enemy's warbands with the
  * shared loss counter (`LossRewrite.counted`), in the plans applied after it
  * and in the losses, the kills Sticky Fire and Shield Wall add there
  * included. The count is net:
  *
  *  - a Conquest defender's returned warbands did not die, so they come back
  *    out of it;
  *  - warbands Billowing Fog or Traveling Doctor spare are never killed, since
  *    those fold first;
  *  - warbands the enemy's Hospital will place on its site did not stay dead
  *    (`Hospital.placeable`).
  *
  * When its user won, they gain that many warbands at the end of the losses,
  * up to their supply, and it says so: "{Blue} gained 2 warbands.". The gain
  * comes after Hospital's count, so Hospital never reads it as a return.
  */
case object CursedCauldron extends BattlePlan:
  val relicId: RelicId = CursedCauldronCard.id
  val id: PowerId = CursedCauldronCard.power.id

  /** Folds after Hospital (2), whose saves it subtracts, and after every plan
    * that changes the losses. */
  val Priority: Int = 3

  /** The enemy's dead since it was chosen. It is never rolled or shown, and
    * the walker clears it with every pool when the Campaign ends. */
  val Died: PoolKey = PoolKey("campaign.cursed-cauldron.died")

  /** "{Blue} gained {n warbands}." */
  val gained: NoteKey = NoteSupport.gainedKey("gained")

  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def priority: Int = CursedCauldron.Priority
  override def noteKeys: Vector[NoteKey] = Vector(CursedCauldron.gained)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Cursed Cauldron: gain a warband per enemy warband killed if victorious",
      Vector.empty, Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignPlanApplication -> ((use, children) =>
      counted(use, children, returns = false)),
    PowerWindow.CampaignLosses -> ((use, losses) => (for
      user <- use.user
      if use.won.contains(true)
    yield counted(use, losses, returns = true) ++ Vector(gain(use, user),
      Note(id, NoteSupport.gainNote(CursedCauldron.gained, cardRef, user,
        NoteUnit.Warband, NoteSupport.warbands)))).getOrElse(losses)))

  /** The other side of the Campaign. */
  private def enemy(use: PlanUse): Option[CampaignDefender] = use.side match
    case CampaignPlanSide.Attacker =>
      use.result.map(_.defender).orElse(use.defender)
    case CampaignPlanSide.Defender => Some(CampaignDefender.Player(use.actor))

  private def playerOf(side: CampaignDefender): Option[PlayerId] = side match
    case CampaignDefender.Player(player) => Some(player)
    case CampaignDefender.Bandits => None

  private def forceOf(ready: ReadyGame, side: CampaignDefender)
      : Option[ForceKind] = side match
    case CampaignDefender.Player(player) =>
      PlayerFacts.forceKind(ready, player).toOption
    case CampaignDefender.Bandits => Some(ForceKind.Bandit)

  private def counted(use: PlanUse, children: Vector[Operation],
      returns: Boolean): Vector[Operation] = (for
    foe <- enemy(use)
    kind <- forceOf(use.ready, foe)
  yield LossRewrite.counted(CursedCauldron.Died, kind, playerOf(foe),
    returns)(children)).getOrElse(children)

  /** The count, less what the enemy's Hospital will place, as warbands for
    * `user`. */
  private def gain(use: PlanUse, user: PlayerId): Operation =
    BuildOps((ready, _) => PlayerFacts.forceKind(ready, user).map { kind =>
      val saved = enemy(use).flatMap(playerOf)
        .fold(0)(Hospital.placeable(ready, _))
      val died = ready.game.current.rollPools.get(CursedCauldron.Died)
        .fold(0)(_.count) - saved
      if died > 0 then Vector[CoreOperation](Gain.Warbands(user, kind, died))
      else Vector.empty
    })
