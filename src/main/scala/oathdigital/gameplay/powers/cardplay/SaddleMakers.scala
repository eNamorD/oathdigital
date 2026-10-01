package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution, NoteSupport}
import oathdigital.model._

/** Saddle Makers (card 142, adviser-only), a persistent rule of a faceup
  * adviser: "After another player plays a [nomad] or [order] card, you gain
  * [favor] [favor] from the matching favor bank."
  *
  * A `Transform` on the faceup card-play hook, as Gossip's is on the facedown
  * one. A card played faceup, to a site or as a faceup adviser, has its suit.
  * A facedown play has none and does not count, and neither does a Vision. A
  * facedown adviser turned faceup by the card-play procedure is played
  * faceup, as for Book Binders. A swap or a take is not a play. The gain is
  * best-effort, so a bank holding one favor gives one.
  *
  * The rule appends the same two nodes whatever the banks hold, so a refold
  * never shifts a parked sibling (Gossip, Book Binders). Its line, "{Blue}
  * gained 2 favor from the Nomad bank.", covers the generic gain line and
  * reads the gain's step, so an empty bank writes nothing.
  */
final case class SaddleMakers private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower:
  def id: PowerId = SaddleMakers.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def noteKeys: Vector[NoteKey] = Vector(SaddleMakers.gained)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      reward(ctx).fold(children)(children ++ _))))

  override def applicable(ctx: PowerCtx): Boolean = reward(ctx).nonEmpty

  /** The holder's gain and its line, when another player plays a nomad or
    * order denizen faceup. */
  private def reward(ctx: PowerCtx): Option[Vector[Operation]] =
    ctx.operation match
      case CardPlayedFaceup(card: DenizenId, _) =>
        for
          suit <- catalog.suitOf(card).filter(SaddleMakers.Suits)
          holder <- holderOf(ctx.state).filter(_ != ctx.activePlayer)
        yield Vector[Operation](Gain.Favor(holder, suit, SaddleMakers.Favor),
          Note(id, NoteSupport.gainedFromNote(SaddleMakers.gained,
            PowerSourceRef.Card(cardId), holder), covers = true))
      case _ => None

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

object SaddleMakers:
  val id: PowerId = PowerId("denizen.saddle-makers")
  val Favor: Int = 2
  val Suits: Set[Suit] = Set(Suit.Nomad, Suit.Order)
  val gained: NoteKey = NoteSupport.gainedFromKey("gained")

  def forCatalog(catalog: ExecutableCatalog): Option[SaddleMakers] =
    CatalogCards.denizen(catalog, id).map(new SaddleMakers(_, catalog))
