package oathdigital.gameplay.powers.search

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  OfferReason, PowerCtx, Transform}
import oathdigital.gameplay.powers.{PowerAnswers, SelectedModifier}
import oathdigital.model._

object PaintedTrumpetCard extends Relic(RelicId("R23"), "Painted Trumpet", value = 46, defense = 1):
  val power = PrintedPower(PowerId("relic.painted-trumpet"),
    persistent = false, cost = Cost.free,
    text = "Instead of drawing from one discard pile, you may draw the " +
      "top card from each discard pile. This draw amount " +
      "**cannot** be modified.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Painted Trumpet (relic R23), a free selected Search modifier (catalog
  * batch 6, slice 3). A Search of a discard pile draws the top card of
  * each discard pile instead: one `Draw` per pile that holds a card, in
  * region order. The Search costs what the pile picked costs.
  *
  * The pile picked may be empty while another holds a card. The draw
  * cannot be modified: it folds after the draw extensions
  * ([[PaintedTrumpet.Priority]]) and replaces what they drew. Truthful
  * Harp still reveals the cards drawn.
  *
  * A Search of the world deck is unchanged, and the world deck is warned
  * while it is picked. It cannot be picked with Mushrooms.
  *
  * Its line, "Drawing the top card of each discard pile instead", comes
  * before the draw, and only when the rewrite applies. The Search's own
  * line names the pile the player picked, which is the pile the Search was
  * priced at; this line says the cards came from all of them (procedures
  * journal their own notes, slice 3 plan-time ruling 5).
  */
final case class PaintedTrumpet private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: RelicId = PaintedTrumpetCard.id
  def id: PowerId = PaintedTrumpet.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def priority: Int = PaintedTrumpet.Priority
  override def noteKeys: Vector[NoteKey] = PaintedTrumpet.noteKeys

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchBeforeDraw -> Vector(Transform((ctx, operations) =>
      line +: operations.map {
        case node: BuildOps
            if node.window.contains(PowerWindow.SearchBeforeDraw) =>
          node.copy(build = (ready, pending) => picked(pending) match {
            case Some(pile: SearchSource.RegionalDiscard) =>
              tops(ready, ctx.activePlayer, pile)
            case _ => node.build(ready, pending)
          })
        case other => other
      })))

  override def warning(decide: Decide, option: DecisionOptionRef)
      : Option[OptionReason] = option match
    case DecisionOptionRef.Deck(CardDeck.World)
        if decide.decisionId == SearchProcedure.sourceDecisionId =>
      Some(OptionReason.Written(PaintedTrumpet.deckWarning))
    case _ => None

  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    Option.when(ModifierPicks.isPicked(ctx.answered, ctx.window,
      MushroomsCard.id))(OfferReason.Unavailable(
      PaintedTrumpet.notWithMushrooms))

  /** The line, written exactly when the rewrite below applies: it reads
    * the same `picked` the rewrite reads, so a Search of the world deck,
    * which this power leaves alone, says nothing. */
  private def line: Operation = Note(id, states =>
    picked(PendingTree(Vector.empty, states.answered)).collect {
      case _: SearchSource.RegionalDiscard =>
        PaintedTrumpet.drawing(PowerSourceRef.Card(cardId))
    })

  /** The pile the source decision picked. */
  private def picked(pending: PendingTree): Option[SearchSource] =
    PowerAnswers.one(pending, SearchProcedure.sourceDecisionId)
      .flatMap(SearchSource.fromRef)

  /** The top card of each discard pile that holds one, in region order. */
  private def tops(ready: ReadyGame, actor: PlayerId,
      pile: SearchSource): Either[OathViolation, Vector[CoreOperation]] =
    val draws = Region.all.flatMap(region =>
      ready.game.current.commonCards.discard(region).lastOption.map(card =>
        Draw(actor, Vector(card), Location.RegionalDiscard(region),
          Location.Hand(actor))))
    Either.cond(draws.nonEmpty, draws,
      OathViolation.SearchSourceUnavailable(pile))

object PaintedTrumpet:
  val id: PowerId = PaintedTrumpetCard.power.id
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Painted Trumpet: Drawing the top card of each discard pile
    * instead". */
  val drawing: NoteKey = notes.key("drawing", Vector(
    NotePart.Text("Drawing the top card of each discard pile instead")))
  /** After the draw extensions, which fold at 0, so that nothing modifies
    * its draw. */
  val Priority: Int = 1
  val deckWarning: String = "Painted Trumpet needs a discard pile"
  val notWithMushrooms: String = "Cannot be used with Mushrooms"

  def forCatalog(catalog: ExecutableCatalog): PaintedTrumpet =
    new PaintedTrumpet(catalog)
