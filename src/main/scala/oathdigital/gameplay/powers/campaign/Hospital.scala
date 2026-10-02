package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

object HospitalCard extends Denizen(DenizenId("149"), "Hospital", Suit.Hearth) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.hospital"),
    persistent = false, cost = Cost.free,
    text = "If any of your warbands would be killed, place them on " +
      "Hospital's site instead if you still rule it.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Hospital (card 149, site-only), a battle plan for either side: "If any of
  * your warbands would be killed, place them on Hospital's site instead if
  * you still rule it." Its FAQ sets when: at the end of the Campaign, if its
  * user rules the site then.
  *
  * It is free and used by the player who rules Hospital's site; a bandit
  * defender never uses it. Once it is chosen, each kill of its user's
  * warbands stays a kill, and Hospital counts it in `Hospital.Saved`:
  *
  *  - the sacrifice of a plan its user applies afterwards, such as Wrestlers'
  *    (`CampaignPlanApplication`); a plan applied before Hospital was chosen
  *    has already killed its warband;
  *  - every kill in the losses (`CampaignLosses`), including the kills
  *    Sticky Fire adds there, so Hospital folds after every plan at the
  *    default priority. A Conquest defender's returned half comes back out of
  *    the count, because those warbands did not stay dead.
  *
  * When the Campaign ends, that many warbands move from the user's supply to
  * Hospital's site, if the user rules it then, and Hospital says so. When
  * its site was a Conquest target the attacker won, the user rules it no
  * longer and nothing comes back.
  */
case object Hospital extends BattlePlan:
  val cardId: DenizenId = HospitalCard.id
  val id: PowerId = HospitalCard.power.id

  /** Folds after every plan at the default priority 0, so its count sees the
    * kills Sticky Fire adds to the losses. */
  val Priority: Int = 1

  /** The count of saved warbands. It is never rolled or shown, and the
    * walker clears it with every pool when the Campaign ends. */
  val Saved: PoolKey = PoolKey("campaign.hospital.saved")

  /** "Placed {n} {Red} warband at {site} instead." */
  val placed: NoteKey = NoteKey("placed", Vector(NotePart.Text("Placed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband at ", " warbands at "), NotePart.Arg(2),
    NotePart.Text(" instead.")))

  private def saved(ready: ReadyGame): Int =
    ready.game.current.rollPools.get(Saved).fold(0)(_.count)

  /** The warbands of force `kind` at `site`. */
  private def at(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    ready.game.current.map.sites.get(site).map(_.forces).collect {
      case SiteForces.Occupied(`kind`, count) => count
    }.getOrElse(0)

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def priority: Int = Hospital.Priority
  override def noteKeys: Vector[NoteKey] = Vector(Hospital.placed)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.user.flatMap(_ => context.denizen(cardId)).map(source =>
      CampaignPlanOffer(source,
        "Hospital: save your warbands that would be killed", Vector.empty,
        Vector.empty))

  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] = Map(
    PowerWindow.CampaignPlanApplication -> counted(returns = false),
    PowerWindow.CampaignLosses -> counted(returns = true))

  override def later: Map[PowerWindow, PlanUse => Vector[Operation]] = Map(
    PowerWindow.CampaignActionEligibility -> (use =>
      use.user.toVector.flatMap(user => Vector(
        BuildOps((ready, _) => Right(placement(ready, user).toVector)),
        Note(id, placedNote(_, user))))))

  /** A window's children, with each kill of the user's warbands counted.
    * `returns` says whether the window can hand back a Conquest defender's
    * half, which only the losses do. */
  private def counted(returns: Boolean)(use: PlanUse,
      children: Vector[Operation]): Vector[Operation] = (for
    user <- use.user
    kind <- PlayerFacts.forceKind(use.ready, user).toOption
  yield children.map(Tally(user, kind, returns).rewrite)).getOrElse(children)

  /** The saved warbands, from the user's supply to Hospital's site, when the
    * user rules it now. */
  private def placement(ready: ReadyGame, user: PlayerId)
      : Option[CoreOperation] = for
    site <- SiteRulers.siteOf(ready, cardId)
    if SiteRulers.rulerOf(ready, site).contains(SiteRuler.Player(user))
    kind <- PlayerFacts.forceKind(ready, user).toOption
    count = Hospital.saved(ready)
      .min(ready.banks.warbandSupply.getOrElse(kind, 0))
    if count > 0
  yield Move(Piece.Warbands(kind, count),
    PositionedLocation(Location.WarbandBank(kind)),
    PositionedLocation(Location.Site(site)))

  /** What the placement just added to Hospital's site. */
  private def placedNote(states: NoteStates, user: PlayerId)
      : Option[PowerNote] = for
    (before, after) <- states.previous
    site <- SiteRulers.siteOf(after, cardId)
    kind <- PlayerFacts.forceKind(after, user).toOption
    count = Hospital.at(after, site, kind) - Hospital.at(before, site, kind)
    if count > 0
  yield Hospital.placed(PowerSourceRef.Card(cardId), NoteArg.Number(count),
    NoteArg.Player(user), NoteArg.Site(site))

  /** Counts the kills of `user`'s warbands, of force `kind`, in
    * `Hospital.Saved`. When `returns` is set, a warband returned from the
    * supply to the user's board did not stay dead, so it comes back out of
    * the count.
    *
    * It counts the amount each operation asks for, which the Campaign's kills
    * always execute in full because they read the board they kill from. It
    * reaches through `BuildOps`, `Sequence` and `Branch`, the only nodes these
    * windows hold. */
  private final class Tally(user: PlayerId, kind: ForceKind, returns: Boolean):
    def rewrite(operation: Operation): Operation = operation match
      case ops: BuildOps => ops.copy(build = (ready, pending) =>
        ops.build(ready, pending).map(_.flatMap(counted)))
      case sequence: Sequence =>
        sequence.copy(children = sequence.children.map(rewrite))
      case branch: Branch => Branch((ready, pending) =>
        branch.select(ready, pending).map(rewrite))
      case core: CoreOperation => counted(core) match
        case Vector(same) => same
        case several => Sequence(several)
      case other => other

    private def counted(operation: CoreOperation): Vector[CoreOperation] =
      operation match
        case Kill(Piece.Warbands(`kind`, count), _) =>
          Vector(operation, ModifyDicePool(Hospital.Saved, count))
        case Sacrifice(`user`, Piece.Warbands(`kind`, count), _) =>
          Vector(operation, ModifyDicePool(Hospital.Saved, count))
        case Move(Piece.Warbands(`kind`, count),
            PositionedLocation(Location.WarbandBank(_), _),
            PositionedLocation(Location.PlayArea(`user`), _), None) if returns =>
          Vector(operation, ModifyDicePool(Hospital.Saved, -count))
        case other => Vector(other)
