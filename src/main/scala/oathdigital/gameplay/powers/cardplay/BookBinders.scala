package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogResolution, NoteSupport}
import oathdigital.model._

object BookBindersCard extends Denizen(DenizenId("140"), "Book Binders", Suit.Hearth) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.book-binders"),
    persistent = true, cost = Cost.free,
    text = "After another player plays a Vision faceup, you gain " +
      "[favor] [favor] from any one favor bank.")
  val powers: Vector[PrintedPower] = Vector(power)

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
  *
  * Its line, "{Blue} gained 2 favor from the Order bank.", follows the take
  * inside the `Branch`, so the window's node count still does not depend on
  * live state.
  */
final case class BookBinders private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = BookBindersCard.id
  def id: PowerId = BookBinders.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.ActionCardPlayedFaceup -> Vector(Transform((ctx, children) =>
      reward(ctx).fold(children)(children :+ _))))

  override def applicable(ctx: PowerCtx): Boolean = reward(ctx).nonEmpty

  override def noteKeys: Vector[NoteKey] = Vector(BookBinders.gained)

  /** The `Branch` that takes the holder's reward, whenever another player
    * plays a Vision faceup and this card's holder is a different player.
    * One node whatever the take does to bank state, and whatever the banks
    * hold, even none: the walker refolds this window on every command, and
    * a sibling contribution at the same window (Conspiracy's own target
    * decision, sorted after this one) is addressed by index, so this must
    * add the same number of nodes regardless of live state -- including a
    * later refold where a bank this same take already drained leaves every
    * bank empty. Gating on whether a bank is stocked, at any fold, risks the
    * exact same shift one level up, so the gate is gone: `FavorBankChoice
    * .take` already yields nothing when no bank is stocked, and the `Branch`
    * carries that, not the fold. Crediting Book Binders on a play where no
    * bank held favor -- adding a `Branch` that then does nothing -- is
    * accepted for now (Power log lines phase).
    */
  private def reward(ctx: PowerCtx): Option[Operation] = for
    vision <- VisionPlay.played(ctx)
    holder <- holderOf(ctx.state).filter(_ != ctx.activePlayer)
  yield
    val decisionId = BookBinders.decisionId(ctx.state, holder, vision)
    Branch((state, _) => {
      val take = FavorBankChoice.take(state, holder, BookBinders.Favor,
        decisionId, "Book Binders: take two favor from a bank")
      if take.isEmpty then take
      else take :+ Note(id, NoteSupport.gainedFromNote(BookBinders.gained,
        PowerSourceRef.Card(cardId), holder), covers = true)
    })

  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    }).map(_.player)

object BookBinders:
  val id: PowerId = BookBindersCard.power.id
  val Favor: Int = 2
  val gained: NoteKey = NoteSupport.gainedFromKey("gained")

  def forCatalog(catalog: ExecutableCatalog): BookBinders =
    new BookBinders(catalog)

  /** A Vision is played faceup at most once a round. */
  def decisionId(ready: ReadyGame, holder: PlayerId, vision: VisionId): String =
    s"book-binders-${ready.game.current.tracks.round}-${holder.value}-" +
      vision.value
