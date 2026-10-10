package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OperationRewrite, PowerCtx, Transform}
import oathdigital.model._

object VowOfWanderingCard extends Denizen(DenizenId("247"), "Vow of Wandering", Suit.Nomad) with Locked with AdviserOnly:
  val muster = PrintedPower(PowerId("denizen.vow-of-wandering.muster"),
    persistent = false, cost = Cost.free,
    text = "Gain two more warbands.")
  val power = PrintedPower(PowerId("denizen.vow-of-wandering"),
    persistent = true, cost = Cost.free,
    text = "You **cannot** place warbands on sites.")
  val powers: Vector[PrintedPower] = Vector(muster, power)

/** Vow of Wandering's Muster power (card 247, adviser-only, locked), an
  * automatic rule of a faceup adviser: every Muster of its holder gains two
  * more warbands, best effort from the supply. They join the Muster's own
  * gain, as Vow of Beastkin's one does, and the line after the gain says how
  * many came ([[ExtraWarbands]]): "Gained {n} more warbands." The catalog
  * marks the power `persistent: false`, but it is not a choice, so it keeps
  * the default automatic resolution.
  */
case object VowOfWanderingMuster extends ContributingPower:
  val cardId: DenizenId = VowOfWanderingCard.id
  val id: PowerId = VowOfWanderingCard.muster.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  val Warbands: Int = 2

  override def noteKeys: Vector[NoteKey] = Vector(ExtraWarbands.gained)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      ExtraWarbands.added(ctx, id, cardId, Warbands, operations))))

  override def applicable(ctx: PowerCtx): Boolean =
    VowOfWandering.holds(ctx.state, ctx.activePlayer)

/** Vow of Wandering (card 247, adviser-only, locked), a persistent rule of a
  * faceup adviser: "You cannot place warbands on sites."
  *
  *  - **The restriction.** An operation restriction refuses any move that
  *    puts the holder's warbands on a site: from the board, the bank or
  *    another site. It reads the leaves of a composite, so a `Replace` is
  *    refused too. A move from a site to the board stays legal. Warbands are
  *    the holder's when they leave the holder's board, or when they are of
  *    the holder's kind. The Chancellor and the Citizens share Imperial
  *    warbands, so those count only while the holder is the active player.
  *    The refusal is `Impossible`: an optional placement is skipped, and no
  *    walk fails because of it.
  *  - **The powers that place warbands** read [[holds]] themselves, to write
  *    [[forbidden]] or to offer only what is legal. The minor action refuses
  *    with the restriction's reason.
  *  - **Conquest.** After a victory, the placement question is replaced by a
  *    line, and the survivors stay on the board: "Kept {n} surviving
  *    warbands on the board."
  */
case object VowOfWandering extends ContributingPower:
  val cardId: DenizenId = VowOfWanderingCard.id
  val id: PowerId = VowOfWanderingCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  /** The restriction's detail, which the minor action shows. */
  val Reason: String = "Vow of Wandering forbids placing warbands on sites"
  /** "Vow of Wandering forbids placing warbands on sites.", the line a power
    * writes under its own name when the Vow stops its placement. */
  val forbidden: NoteKey = NoteKey("vow-of-wandering",
    Vector(NotePart.Text(s"$Reason.")))
  /** "Kept {n} surviving warband on the board." */
  val kept: NoteKey = NoteKey("kept", Vector(NotePart.Text("Kept "),
    NotePart.Arg(0), NotePart.Plural(0, " surviving warband on the board.",
      " surviving warbands on the board.")))

  override def noteKeys: Vector[NoteKey] = Vector(kept)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignPlacement -> Vector(Transform((ctx, operations) =>
      OperationRewrite.nodes(ctx, operations)((_, _) => {
        case decide: Decide if decide.decisionId == CampaignIds.placement =>
          Vector(Note(id, _ => Some(kept(PowerSourceRef.Card(cardId),
            NoteArg.Number(survivors(decide.query))))))
      }))))

  override def applicable(ctx: PowerCtx): Boolean =
    holds(ctx.state, ctx.activePlayer)

  override def operationRestrictions: Vector[OperationRestriction] =
    Vector(placing)

  /** Whether `player` holds this card faceup. */
  def holds(ready: ReadyGame, player: PlayerId): Boolean =
    holderOf(ready).contains(player)

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

  /** The survivors the placement question offers. */
  private def survivors(query: DecisionQuery): Int = query match
    case amount: DecisionQuery.ChooseAmount => amount.max
    case spread: DecisionQuery.Distribute => spread.maxTotal
    case _ => 0

  private object placing extends OperationRestriction:
    def reason(ready: ReadyGame, operation: CoreOperation)
        : Option[OperationReason] = holderOf(ready)
      .filter(holder =>
        Operation.flatten(operation).exists(places(ready, holder, _)))
      .map(_ => OperationReason("vow-of-wandering", Reason,
        OperationReasonKind.Impossible))

  /** Whether `step` puts `holder`'s warbands on a site. */
  private def places(ready: ReadyGame, holder: PlayerId, step: Operation)
      : Boolean = step match
    case Move(Piece.Warbands(kind, _), from,
        PositionedLocation(Location.Site(_), _), _) =>
      from.location == Location.PlayArea(holder) ||
        holdersKind(ready, holder, kind)
    case _ => false

  /** Whether warbands of `kind` are `holder`'s wherever they come from. */
  private def holdersKind(ready: ReadyGame, holder: PlayerId,
      kind: ForceKind): Boolean =
    ready.game.current.players.find(_.player == holder)
      .flatMap(PlayerForceKind.of(ready, _)).contains(kind) &&
      (kind != ForceKind.Imperial ||
        ready.game.current.turn.activePlayer == holder)
