package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution, NoteSupport}
import oathdigital.model._

/** Gossip (card 99), a persistent rule of a faceup adviser: when any other
  * player places an adviser facedown, a denizen or a Vision, the holder gains 1
  * favor from the Discord bank. The holder's own facedown plays do not count.
  *
  * The rule is automatic, so it needs no selection. A facedown copy is not
  * active, and the card is adviser-only, so the holder is found among the
  * players' faceup advisers.
  *
  * Its line, "{Blue} gained 1 favor from the Discord bank.", covers the
  * generic gain line, and reads the gain's step, so an empty bank writes
  * nothing.
  */
final case class Gossip private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower:
  def id: PowerId = Gossip.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  override def noteKeys: Vector[NoteKey] = Vector(Gossip.gained)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFacedown -> Vector(Transform((ctx, children) =>
      holderOf(ctx).fold(children)(holder => children ++ Vector(
        Gain.Favor(holder, Suit.Discord, Gossip.Favor),
        Note(id, NoteSupport.gainedFromNote(Gossip.gained,
          PowerSourceRef.Card(cardId), holder), covers = true))))))

  override def applicable(ctx: PowerCtx): Boolean = holderOf(ctx).nonEmpty

  /** The holder, when the hooked play is another player's facedown play. */
  private def holderOf(ctx: PowerCtx): Option[PlayerId] = ctx.operation match
    case CardPlayedFacedown(_, player) => ctx.state.game.current.players
      .find(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == cardId
        case _ => false
      }).map(_.player).filter(_ != player)
    case _ => None

object Gossip:
  val id: PowerId = PowerId("denizen.gossip")
  val Favor: Int = 1
  val gained: NoteKey = NoteSupport.gainedFromKey("gained")

  def forCatalog(catalog: ExecutableCatalog): Option[Gossip] =
    CatalogCards.denizen(catalog, id).map(new Gossip(_, catalog))
