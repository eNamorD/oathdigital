package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignSetup}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  Transform}
import oathdigital.gameplay.powers.CatalogResolution
import oathdigital.gameplay.PowerAccess
import oathdigital.model._

object SealingWardCard extends Denizen(DenizenId("72"), "Sealing Ward", Suit.Arcane) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.sealing-ward"),
    persistent = true, cost = Cost.free,
    text = "Your relics add one more [defense-die] when targeted.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Sealing Ward (card 72), a persistent rule of a faceup adviser: "Your
  * relics add one more defense die when targeted."
  *
  * A `Transform` at `CampaignGatherPools`, the first power to hook it. When
  * the pools are gathered for a Raid against the holder, each of the
  * holder's relics the Raid targets adds one defense die. A Conquest targets
  * sites, not relics, so it adds nothing.
  *
  * The window's own step gathers only the printed defense, which the Game
  * Log's "Defense Pool" line restates, so the added dice write their own
  * line: "Added 2 more defense dice." It adds one `Branch` whatever the board
  * holds, so the window's node count never depends on live state.
  */
final case class SealingWard private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = SealingWardCard.id
  def id: PowerId = SealingWard.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = SealingWard.noteKeys

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.CampaignGatherPools -> Vector(Transform((ctx, children) =>
      children :+ Branch(added(ctx.actor)))))

  /** The dice and their line, or nothing when no relic of the holder is
    * targeted. */
  private def added(actor: PlayerId)(ready: ReadyGame, pending: PendingTree)
      : Vector[Operation] = dice(ready, actor, pending) match
    case 0 => Vector.empty
    case count => Vector[Operation](
      ModifyDicePool(CampaignIds.defensePool, count),
      Note(id, _ => Some(SealingWard.added(PowerSourceRef.Card(cardId),
        NoteArg.Number(count)))))

  /** One die per relic of the holder this Raid targets. */
  private def dice(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Int = (for
    setup <- CampaignSetup.setup(ready, actor, pending)
    if setup.kind == CampaignKind.Raid
    holder <- playerOf(setup.defender)
    if holds(ready, holder)
  yield setup.raidTargets.count {
    case CampaignRaidTarget.Relic(`holder`, _) => true
    case _ => false
  }).getOrElse(0)

  private def playerOf(defender: CampaignDefender): Option[PlayerId] =
    defender match
      case CampaignDefender.Player(player) => Some(player)
      case CampaignDefender.Bandits => None

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    PowerAccess.holdsFaceupAdviser(ready, player, cardId)

object SealingWard:
  val id: PowerId = SealingWardCard.power.id
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Added {n} more defense dice." */
  val added: NoteKey = notes.key("added", Vector(NotePart.Text("Added "),
    NotePart.Arg(0),
    NotePart.Plural(0, " more defense die.", " more defense dice.")))

  def forCatalog(catalog: ExecutableCatalog): SealingWard =
    new SealingWard(catalog)
