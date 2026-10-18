package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower,
  SiteOnly}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.operations.SupplyStrip
import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  OfferReason, PowerCtx, Transform}
import oathdigital.gameplay.powers.{PowerAnswers, SelectedModifier}
import oathdigital.model._

object MushroomsCard extends Denizen(DenizenId("183"), "Mushrooms", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.mushrooms"),
    persistent = false, cost = Cost(secret = 1),
    text = "Spend no Supply, but draw only one card _(not three)_ from " +
      "the bottom of your region's discard pile.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Mushrooms (card 183, site-only), a selected Search modifier usable while
  * the actor's pawn is at its site, costing 1 secret placed on it (catalog
  * batch 6, slice 3):
  *
  *  - The world deck is unavailable at the source decision.
  *  - The Search spends no Supply. A toll such as Forced Labor's is a
  *    separate operation and is still paid, as with News from Afar.
  *  - It draws one card from the bottom of the discard pile picked, which
  *    may be another region's when Observatory or Errand Boy offers it. A
  *    draw extension such as Augury adds cards, still from the bottom: it
  *    folds first ([[BottomDraw.Priority]]).
  *  - It cannot be picked with Painted Trumpet, and is warned while Wine
  *    of Welcome is picked, which then does nothing.
  *
  * Its line, "Drawing one card from the bottom of the Cradle discard",
  * comes before the draw. The Search's own line names the pile but cannot
  * say which end the draw took, because that is this power's rewrite of
  * the draw operation and a note cannot read an operation (procedures
  * journal their own notes, slice 3 plan-time ruling 5).
  */
final case class Mushrooms private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = MushroomsCard.id
  def id: PowerId = Mushrooms.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def cost: Cost = Cost(secret = Mushrooms.Secrets)
  override def priority: Int = BottomDraw.Priority
  override def noteKeys: Vector[NoteKey] = Mushrooms.noteKeys

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchSourceSelection -> Vector(Transform((ctx, operations) =>
      SearchSources.withheld(ctx, operations,
        _ == SearchSource.ref(SearchSource.WorldDeck), Mushrooms.worldDeck))),
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      SupplyStrip(ctx, ctx.actor, operations))),
    PowerWindow.SearchBeforeDraw -> Vector(Transform((_, operations) =>
      line +: BottomDraw.reshaped(operations, Mushrooms.Cards,
        _.isInstanceOf[SearchSource.RegionalDiscard]))))

  /** The pile the source decision picked, when it picked a discard pile:
    * the actor's own, or another region's that Observatory or Errand Boy
    * offered. The world deck is unavailable while this is picked, so the
    * answer is always a discard pile and this always writes its line. */
  private def line: Operation = Note(id, states =>
    PowerAnswers.one(PendingTree(Vector.empty, states.answered),
      SearchProcedure.sourceDecisionId)
      .flatMap(SearchSource.fromRef).collect {
        case SearchSource.RegionalDiscard(region) =>
          Mushrooms.drawing(PowerSourceRef.Card(cardId),
            NoteArg.RegionName(region))
      })

  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    if ModifierPicks.isPicked(ctx.answered, ctx.window,
        PaintedTrumpetCard.id) then
      Some(OfferReason.Unavailable(Mushrooms.notWithTrumpet))
    else Option.when(ModifierPicks.isPicked(ctx.answered, ctx.window,
      WineOfWelcomeCard.id))(OfferReason.Warned(WineOfWelcome.withMushrooms))

object Mushrooms:
  val id: PowerId = MushroomsCard.power.id
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Mushrooms: Drawing one card from the bottom of the Cradle
    * discard". */
  val drawing: NoteKey = notes.key("drawing", Vector(
    NotePart.Text("Drawing one card from the bottom of the "),
    NotePart.Arg(0), NotePart.Text(" discard")))
  val Secrets: Int = 1
  /** The cards it draws, before any draw extension. */
  val Cards: Int = 1
  val worldDeck: String = "Mushrooms draws from a discard pile"
  val notWithTrumpet: String = "Cannot be used with Painted Trumpet"

  def forCatalog(catalog: ExecutableCatalog): Mushrooms =
    new Mushrooms(catalog)
