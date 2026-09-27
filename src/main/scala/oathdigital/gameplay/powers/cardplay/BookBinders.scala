package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, CatalogResolution}
import oathdigital.model._

/** Book Binders (card 140), a persistent rule of a faceup adviser: "After
  * another player plays a Vision faceup, you gain [favor] [favor] from any one
  * favor bank."
  *
  * A `Transform` on the card-play hook, as Gossip's is. The card says
  * "another player", not "an enemy", so any other player's faceup Vision
  * triggers it, the Conspiracy included, from Search or from the advisers.
  * The holder chooses the bank off turn, as League Treaty's ruler does. One
  * stocked bank is taken without asking, and a bank holding one favor gives
  * one.
  */
final case class BookBinders private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower:
  def id: PowerId = BookBinders.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      reward(ctx).fold(children)(children ++ _))))

  override def applicable(ctx: PowerCtx): Boolean = reward(ctx).nonEmpty

  /** The holder's take, when another player plays a Vision faceup. */
  private def reward(ctx: PowerCtx): Option[Vector[Operation]] = for
    vision <- VisionPlay.played(ctx)
    holder <- holderOf(ctx.state).filter(_ != ctx.activePlayer)
  yield FavorBankChoice.take(ctx.state, holder, BookBinders.Favor,
    BookBinders.decisionId(ctx.state, holder, vision),
    "Book Binders: take two favor from a bank")

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

object BookBinders:
  val id: PowerId = PowerId("denizen.book-binders")
  val Favor: Int = 2

  def forCatalog(catalog: ExecutableCatalog): Option[BookBinders] =
    CatalogCards.denizen(catalog, id).map(new BookBinders(_, catalog))

  /** A Vision is played faceup at most once a round. */
  def decisionId(ready: ReadyGame, holder: PlayerId, vision: VisionId): String =
    s"book-binders-${ready.game.current.tracks.round}-${holder.value}-" +
      vision.value
