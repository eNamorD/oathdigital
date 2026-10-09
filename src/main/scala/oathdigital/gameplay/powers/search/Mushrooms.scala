package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower,
  SiteOnly}
import oathdigital.gameplay.operations.SupplyStrip
import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  OfferReason, PowerCtx, Transform}
import oathdigital.gameplay.powers.SelectedModifier
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
  * It writes no line: the start line names it, and the Search line says
  * the card came from the bottom.
  */
final case class Mushrooms private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = MushroomsCard.id
  def id: PowerId = Mushrooms.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def cost: Cost = Cost(secret = Mushrooms.Secrets)
  override def priority: Int = BottomDraw.Priority

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchSourceSelection -> Vector(Transform((_, operations) =>
      SearchSources.withheld(operations,
        _ == SearchSource.ref(SearchSource.WorldDeck), Mushrooms.worldDeck))),
    PowerWindow.SearchCost -> Vector(Transform((ctx, operations) =>
      SupplyStrip(ctx.activePlayer, operations))),
    PowerWindow.SearchBeforeDraw -> Vector(Transform((_, operations) =>
      BottomDraw.reshaped(operations, Mushrooms.Cards,
        _.isInstanceOf[SearchSource.RegionalDiscard]))))

  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    if ModifierPicks.isPicked(ctx.answered, ctx.window,
        PaintedTrumpetCard.id) then
      Some(OfferReason.Unavailable(Mushrooms.notWithTrumpet))
    else Option.when(ModifierPicks.isPicked(ctx.answered, ctx.window,
      WineOfWelcomeCard.id))(OfferReason.Warned(WineOfWelcome.withMushrooms))

object Mushrooms:
  val id: PowerId = MushroomsCard.power.id
  val Secrets: Int = 1
  /** The cards it draws, before any draw extension. */
  val Cards: Int = 1
  val worldDeck: String = "Mushrooms draws from a discard pile"
  val notWithTrumpet: String = "Cannot be used with Painted Trumpet"

  def forCatalog(catalog: ExecutableCatalog): Mushrooms =
    new Mushrooms(catalog)
