package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{PrintedPower, Relic}
import oathdigital.gameplay.NoteSupport
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

object StickyFireCard extends Relic(RelicId("R01"), "Sticky Fire", value = 3, defense = 3):
  val power = PrintedPower(PowerId("relic.sticky-fire"),
    persistent = false, cost = Cost.free,
    text = "If you're victorious, you may kill all the warbands in your " +
      "enemy's force. If you do, you **must** give them [favor] if " +
      "able.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Sticky Fire (relic R01), a battle plan for either side: "If you're victorious,
  * you may kill all the warbands in your enemy's force. If you do, you must give
  * them [favor] if able."
  *
  * Choosing it costs nothing. When its user wins, the losses step asks them,
  * before anything dies, whether to burn the enemy's force. If they do:
  *
  *  - after a Conquest won by the attacker, the defender keeps none of the
  *    warbands the victory would have returned to their board;
  *  - after a Raid won by the attacker, every warband left on the defender's
  *    board dies, not half;
  *  - after a defense won by the defender, every warband on the attacker's board
  *    dies, committed to the Campaign or not, and every warband of theirs at a
  *    queued origin (slice 13).
  *
  * The kills are `KillWholeForce`'s, which Shield Wall shares.
  *
  * Then the winner gives the loser a favor if they can. A `Give` takes only what
  * its giver holds, and against bandits the favor is given to the shared bank,
  * which burns it.
  *
  * After the burn it writes what died and the favor given, read from the
  * burn's step: "Killed 3 {Blue} warbands, and {Blue} gained 1 favor.", or
  * either half alone. Against bandits it writes nothing: no player lost a
  * warband or gained the favor.
  */
case object StickyFire extends BattlePlan:
  val relicId: RelicId = StickyFireCard.id
  val id: PowerId = StickyFireCard.power.id
  /** Under the Campaign's prefix, so a parked question is a Campaign decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "sticky-fire"
  val yes: DecisionOptionRef.Button = DecisionOptionRef.Button("kill")
  val no: DecisionOptionRef.Button = DecisionOptionRef.Button("spare")

  private val notes = NoteSet(id)
  /** "Killed {n} {Blue} warband, and {Blue} gained {1 favor}." */
  val burned: NoteKey = notes.key("burned", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband, and ", " warbands, and "), NotePart.Arg(1),
    NotePart.Text(" gained "), NotePart.Arg(2), NotePart.Text(".")))
  /** The kill alone, when the winner had no favor to give. */
  val killed: NoteKey = NoteSupport.killedKey(notes, "killed")
  /** The favor alone, when the loser's board held no warband. */
  val gave: NoteKey = NoteSupport.gainedKey(notes, "gained")

  def cardRef: DecisionOptionRef = DecisionOptionRef.Relic(relicId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)

  override def noteKeys: Vector[NoteKey] = notes.declared

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.relic(relicId).map(source => CampaignPlanOffer(source,
      "Sticky Fire: kill the enemy's force if victorious", Vector.empty,
      Vector.empty))

  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] = Map(
    PowerWindow.CampaignLosses -> ((_, use, losses) => (for
      user <- use.user
      result <- use.result
      if use.won.contains(true)
    yield ask(user) +: (losses :+ burn(use, user, result) :+
      Note(id, burnNote(use.side, result)))).getOrElse(losses)))

  /** What the burn did to the loser, read from its step. */
  private def burnNote(side: CampaignPlanSide, result: CampaignResult)(
      states: NoteStates): Option[LogNote] =
    val card = PowerSourceRef.Card(relicId)
    for
      loser <- loserOf(side, result)
      if NoteSupport.answer(states, StickyFire.decisionId).contains(StickyFire.yes)
      step <- states.previous
      killed = -NoteSupport.warbands(step, loser) + atOrigin(step, result)
      gift = NoteSupport.favor(step, loser)
      note <- (killed > 0, gift > 0) match
        case (true, true) => Some(StickyFire.burned(card, NoteArg.Number(killed),
          NoteArg.Player(loser), NoteArg.Amount(gift, NoteUnit.Favor)))
        case (true, false) => Some(StickyFire.killed(card,
          NoteArg.Number(killed), NoteArg.Player(loser)))
        case (false, true) => Some(StickyFire.gave(card, NoteArg.Player(loser),
          NoteArg.Amount(gift, NoteUnit.Favor)))
        case _ => None
    yield note

  /** The warbands the step killed at a queued origin: only the attacker's
    * stand there. */
  private def atOrigin(step: (ReadyGame, ReadyGame), result: CampaignResult)
      : Int = result.originForce.fold(0) { origin =>
    def count(ready: ReadyGame) = ready.game.current.map.sites
      .get(origin.site).fold(0)(_.forces match
        case SiteForces.Occupied(_, n) => n
        case SiteForces.Empty => 0)
    count(step._1) - count(step._2)
  }

  /** The player the user beat; bandits are no player. */
  private def loserOf(side: CampaignPlanSide, result: CampaignResult)
      : Option[PlayerId] = (side, result.defender) match
    case (CampaignPlanSide.Defender, _) => Some(result.attacker)
    case (_, CampaignDefender.Player(defender)) => Some(defender)
    case (_, CampaignDefender.Bandits) => None

  private def ask(user: PlayerId): Operation = Decide(StickyFire.decisionId, user,
    DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(StickyFire.yes, "Kill the warbands in the enemy's force"),
      DecisionOption.Button(StickyFire.no, "Do not")),
      heading = Some("Sticky Fire: kill all the warbands in your enemy's force?")))

  /** The killing and the favor, when the user said yes. The returns are read
    * now, when the losses window is folded, before the losses run. */
  private def burn(use: PlanUse, user: PlayerId, result: CampaignResult)
      : Operation =
    val returns = KillWholeForce.returns(use.ready, result)
    BuildOps((ready, pending) =>
      if !PowerAnswers.one(pending, StickyFire.decisionId).contains(StickyFire.yes) then
        Right(Vector.empty)
      else
        val kills = use.side match
          case CampaignPlanSide.Defender => KillWholeForce.ofAttacker(ready, result)
          case CampaignPlanSide.Attacker =>
            KillWholeForce.ofDefender(ready, result, returns)
        kills.map(_ ++ gives(use.side, user, result)))

  private def gives(side: CampaignPlanSide, user: PlayerId,
      result: CampaignResult): Vector[CoreOperation] =
    val to: Location = (side, result.defender) match
      case (CampaignPlanSide.Defender, _) => Location.PlayArea(result.attacker)
      case (_, CampaignDefender.Player(defender)) => Location.PlayArea(defender)
      case (_, CampaignDefender.Bandits) => Location.SharedBank
    Vector(Give(Piece.Favor(1), user, Location.PlayArea(user), to))
