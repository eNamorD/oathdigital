package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.{NoteSupport, SiteRulers}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, PayOrAsk}
import oathdigital.model._

object GraspingVinesCard extends Denizen(DenizenId("178"), "Grasping Vines", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.grasping-vines"),
    persistent = true, cost = Cost.free,
    text = "Enemies traveling from any site ruled by Grasping Vines' " +
      "ruler **must** kill one warband on their board if able.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Grasping Vines (card 178), a persistent rule of a faceup site card: an enemy
  * traveling from a site ruled by the Vines' ruler kills one warband on their
  * own board if able. The Vines' ruler is the ruler of the site it stands at,
  * and that ruler is exempt.
  *
  * The kill is not required: a traveller with no warband on their board owes
  * nothing and is not stopped. Otherwise it is a plain `Kill` placed after
  * the rest of the Travel's cost and before the pawn's move. When a player
  * rules the Vines, the traveller is asked first to kill a warband or to
  * travel without killing, which its ruler may consent to (consent slice 4).
  *
  * The kill writes "Killed 1 {Red} warband.", read from its step, so a
  * traveller with no warband reads nothing.
  * Travelling free writes "Killed nothing, with {Blue}'s consent."
  */
final case class GraspingVines private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = GraspingVinesCard.id
  def id: PowerId = GraspingVines.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = GraspingVines.noteKeys
  override def narratedDecisions: Set[String] = Set(GraspingVines.decisionId)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      charged(ctx, operations).getOrElse(operations))),
    PowerWindow.PayOrAsk -> Vector(GraspingVines.step.restriction))

  override def applicable(ctx: PowerCtx): Boolean =
    if ctx.window == PowerWindow.PayOrAsk then GraspingVines.step.asking(ctx)
    else charged(ctx, Vector.empty).nonEmpty

  /** `operations` with what this Travel owes, if it owes anything: the step
    * first when a player rules the Vines, the kill alone when bandits do.
    * Either way the kill runs after the rest of the cost and before the
    * pawn's move ([[TravelRoute.beforeMove]]). */
  private def charged(ctx: PowerCtx, operations: Vector[Operation])
      : Option[Vector[Operation]] = for
    route <- TravelRoute.pawnMove(ctx.operation)
    ruler <- SiteRulers.rulerOfCard(ctx.state, cardId)
    if SiteRule.enemies(ruler, SiteRuler.Player(route.player))
    if SiteRulers.rulerOf(ctx.state, route.source).contains(ruler)
    if ctx.state.game.current.players.find(_.player == route.player)
      .exists(_.board.warbands >= GraspingVines.Warbands)
    warband <- TravelPayments.ownWarband(ctx.state, route.player,
      GraspingVines.Warbands)
  yield
    val killed = Vector(Kill(warband,
      PositionedLocation(Location.PlayArea(route.player))),
      Note(id, NoteSupport.killedNote(GraspingVines.killed,
        PowerSourceRef.Card(cardId), route.player)))
    ruler match
      case SiteRuler.Player(owner) =>
        GraspingVines.step.question(route.player, route.source.value,
          "Grasping Vines: kill 1 warband to travel on?",
          DecisionOption.Button(PayOrAsk.pay, "Kill 1 warband"),
          "Travel without killing") +:
          TravelRoute.beforeMove(operations, Vector(GraspingVines.step.outcome(
            killed, Vector(Note(id, _ => Some(GraspingVines.spared(
              PowerSourceRef.Card(cardId), NoteArg.Player(owner))))))))
      case _ => TravelRoute.beforeMove(operations, killed)

object GraspingVines:
  val id: PowerId = GraspingVinesCard.power.id
  val Warbands: Int = 1
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  val killed: NoteKey = NoteSupport.killedKey(notes, "killed")
  val decisionId: String = "power.grasping-vines.kill"
  /** "Killed nothing, with {Blue}'s consent." */
  val spared: NoteKey = notes.key("spared", Vector(
    NotePart.Text("Killed nothing, with "), NotePart.Arg(0),
    NotePart.Text("'s consent.")))
  /** "Must kill a warband when traveling from its ruler's sites.": the
    * reason of the free option once its ruler refused. */
  val mustKill: NoteKey = notes.key("must-kill", Vector(NotePart.Text(
    "Must kill a warband when traveling from its ruler's sites.")))
  /** The free option's reason while its ruler may be asked. */
  val mustKillConsent: NoteKey = notes.key("must-kill-consent", Vector(
    NotePart.Text("Must kill a warband when traveling from its ruler's " +
      "sites, unless ruler consents.")))
  val step: PayOrAsk = PayOrAsk(id, GraspingVinesCard.id, decisionId,
    mustKill, mustKillConsent)

  /** The free option of a Travel out of `site`. */
  def free(site: SiteId): DecisionOptionRef.Button = PayOrAsk.free(site.value)

  def forCatalog(catalog: ExecutableCatalog): GraspingVines =
    new GraspingVines(catalog)
