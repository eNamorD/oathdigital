package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.CampaignSetup
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Vow of Peace: "You cannot campaign. Attackers cannot sacrifice warbands to
  * increase their attack against you." A faceup copy held as an adviser does
  * both. A facedown copy is not active, and the card is adviser-only, so no
  * other location is considered.
  *
  * The first sentence is a `Restriction` at the Campaign's root, the same
  * shape as Narrow Pass blocking a Travel. The second is a `Transform` at
  * `CampaignSacrificeSelection` that removes the attacker's sacrifice decision
  * when the defender holds the Vow, so the battle reads a sacrifice of zero.
  * In a Conquest the defender is the ruler of the targets. The defender's own
  * sacrifices, such as Wrestlers', are not the attacker's and stay. The
  * removed decision leaves a note naming the defender, so the Game Log says
  * why nothing was asked.
  */
final case class VowOfPeaceContribution private (cardId: DenizenId)
    extends ContributingPower:
  def id: PowerId = VowOfPeaceContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def noteKeys: Vector[NoteKey] =
    Vector(VowOfPeaceContribution.noSacrifice)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignActionEligibility ->
      Vector(Restriction((ctx, _) => blocked(ctx))),
    PowerWindow.CampaignSacrificeSelection ->
      Vector(Transform(withoutSacrifice)))

  /** The sacrifice decision, removed when the defender holds the Vow. The
    * removal leaves a note, unless there was nothing to remove. */
  private def withoutSacrifice(ctx: PowerCtx, children: Vector[Operation])
      : Vector[Operation] = protectedDefender(ctx) match
    case Some(defender) if children.nonEmpty =>
      val note = VowOfPeaceContribution.noSacrifice(PowerSourceRef.Card(cardId),
        NoteArg.Player(defender))
      Vector(Note(id, _ => Some(note)))
    case Some(_) => Vector.empty
    case None => children

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case card: DenizenState =>
        card.id == cardId && card.orientation == Orientation.FaceUp
      case _ => false
    })

  private def blocked(ctx: PowerCtx): Option[OathViolation] =
    Option.when(holds(ctx.state, ctx.activePlayer))(
      OathViolation.CampaignUnavailable(
        "Vow of Peace prevents its ruler from campaigning"))

  /** This Campaign's defender, when that defender holds the Vow faceup. */
  private def protectedDefender(ctx: PowerCtx): Option[PlayerId] =
    CampaignSetup.setup(ctx.state, ctx.activePlayer,
      PendingTree(ctx.nodePath, ctx.answered)).flatMap(_.defender match
        case CampaignDefender.Player(player) if holds(ctx.state, player) =>
          Some(player)
        case _ => None)

object VowOfPeaceContribution:
  val id: PowerId = PowerId("denizen.vow-of-peace")

  /** Where its second sentence removed the attacker's sacrifice decision. */
  val noSacrifice: NoteKey = NoteKey("no-sacrifice", Vector(
    NotePart.Text("The attacker cannot sacrifice against "), NotePart.Arg(0),
    NotePart.Text(".")))

  /** `None` when the catalog has no such card, for example a test stub. */
  def forCatalog(catalog: ExecutableCatalog): Option[VowOfPeaceContribution] =
    catalog.denizenWithPower(id)
      .map(card => new VowOfPeaceContribution(DenizenId(card.id.value)))
