package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, PowerCtx}
import oathdigital.model._

object TrueNamesCard extends Denizen(DenizenId("41"), "True Names", Suit.Beast) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.true-names"),
    persistent = true, cost = Cost.free,
    text = "Your enemy **cannot** use battle plans that match any of " +
      "your advisers against you.")
  val powers: Vector[PrintedPower] = Vector(power)

/** True Names (card 41, adviser-only), a persistent rule of a faceup
  * adviser: "Your enemy cannot use battle plans that match any of your
  * advisers against you."
  *
  * While its holder is the attacker or the defender, every enemy plan whose
  * suit matches one of the holder's faceup advisers, True Names included,
  * is withheld with "True Names: No battle plans matching {Blue}'s
  * advisers.". Facedown advisers do not count, because their suits are
  * hidden. The suits are read at every pass of the window, so an adviser
  * turned faceup during plan selection counts from the next pass, and a
  * plan chosen before that stays chosen.
  */
final case class TrueNames private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = TrueNamesCard.id
  def id: PowerId = TrueNames.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] = TrueNames.noteKeys

  private val naming: OptionRestriction = OptionRestriction(
    (ctx, ref) => matched(ctx, ref).map(_ =>
      OathViolation.CampaignUnavailable(
        "True Names keeps its holder's enemy from matching plans")),
    (ctx, ref) => matched(ctx, ref).map(holder => TrueNames.named(
      PowerSourceRef.Card(cardId), NoteArg.Player(holder))))

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignAttackerBattlePlans -> Vector(naming),
    PowerWindow.CampaignDefenderBattlePlans -> Vector(naming))

  /** The holder, when `ref` is a plan of their enemy whose suit matches one
    * of the holder's faceup advisers. */
  private def matched(ctx: PowerCtx, ref: DecisionOptionRef)
      : Option[PlayerId] =
    EnemyPlans.holderAgainst(ctx, cardId).filter(holder =>
      EnemyPlans.suitOf(catalog, ctx.state, ref)
        .exists(suits(ctx.state, holder)))

  /** The suits of `holder`'s faceup advisers. */
  private def suits(ready: ReadyGame, holder: PlayerId): Set[Suit] =
    ready.game.current.players.find(_.player == holder).toVector
      .flatMap(_.advisers.collect {
        case DenizenState(held, Orientation.FaceUp, _) =>
          EffectiveSuit.of(catalog, ready, held)
      }.flatten).toSet

object TrueNames:
  val id: PowerId = TrueNamesCard.power.id
  /** "No battle plans matching {Blue}'s advisers." */
  private val notes = NoteSet(id)
  val named: NoteKey = notes.key("named", Vector(
    NotePart.Text("No battle plans matching "), NotePart.Arg(0),
    NotePart.Text("'s advisers.")))
  def noteKeys: Vector[NoteKey] = notes.declared

  def forCatalog(catalog: ExecutableCatalog): TrueNames =
    new TrueNames(catalog)
