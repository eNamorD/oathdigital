package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.gameplay.powers.economy.VowOfWandering
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
  *    Sticky Fire and Shield Wall add there, so Hospital folds at priority 2,
  *    after them and after Billowing Fog's and Traveling Doctor's changes.
  *    A Conquest defender's returned half comes back out of the count,
  *    because those warbands did not stay dead.
  *
  * When the Campaign ends, that many warbands move from the user's supply to
  * Hospital's site, if the user rules it then, and Hospital says so. When
  * its site was a Conquest target the attacker won, the user rules it no
  * longer and nothing comes back. Nor does it for a holder of Vow of
  * Wandering: their dead stay killed, and Hospital writes no line.
  */
case object Hospital extends BattlePlan:
  val cardId: DenizenId = HospitalCard.id
  val id: PowerId = HospitalCard.power.id

  /** Folds after Billowing Fog and Traveling Doctor (priority 1), which
    * change the losses, and after every plan at the default priority 0, so
    * its count sees what still dies: the kills Sticky Fire and Shield Wall
    * add there included. */
  val Priority: Int = 2

  /** The count of saved warbands. It is never rolled or shown, and the
    * walker clears it with every pool when the Campaign ends. */
  val Saved: PoolKey = PoolKey("campaign.hospital.saved")

  private val notes = NoteSet(id)
  /** "Placed {n} {Red} warband at {site} instead." */
  val placed: NoteKey = notes.key("placed", Vector(NotePart.Text("Placed "),
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
  override def noteKeys: Vector[NoteKey] = notes.declared

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.user.flatMap(_ => context.denizen(cardId)).map(source =>
      CampaignPlanOffer(source,
        "Hospital: save your warbands that would be killed", Vector.empty,
        Vector.empty))

  override def wrapping: Map[PowerWindow, BattlePlan.Wrap] = Map(
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
  private def counted(returns: Boolean)(ctx: PowerCtx, use: PlanUse,
      children: Vector[Operation]): Vector[Operation] = (for
    user <- use.user
    kind <- PlayerFacts.forceKind(use.ready, user).toOption
  yield LossRewrite.counted(Hospital.Saved, kind, Some(user), returns)(ctx,
    children)).getOrElse(children)

  /** How many saved warbands the placement moves to Hospital's site: none
    * unless `user` rules it now and holds no Vow of Wandering, and no more
    * than their supply holds. Cursed Cauldron reads it to leave out the
    * warbands that did not stay dead. */
  def placeable(ready: ReadyGame, user: PlayerId): Int = (for
    site <- SiteRulers.siteOf(ready, cardId)
    if SiteRulers.rulerOf(ready, site).contains(SiteRuler.Player(user))
    if !VowOfWandering.holds(ready, user)
    kind <- PlayerFacts.forceKind(ready, user).toOption
  yield Hospital.saved(ready)
    .min(ready.banks.warbandSupply.getOrElse(kind, 0))).getOrElse(0)

  /** The saved warbands, from the user's supply to Hospital's site, when the
    * user rules it now. */
  private def placement(ready: ReadyGame, user: PlayerId)
      : Option[CoreOperation] = for
    site <- SiteRulers.siteOf(ready, cardId)
    kind <- PlayerFacts.forceKind(ready, user).toOption
    count = placeable(ready, user)
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
