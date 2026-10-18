package oathdigital.gameplay.powers.search

import oathdigital.catalog.{ExecutableCatalog, PrintedPower, Relic}
import oathdigital.gameplay.actions.SearchRules
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  OfferReason, PowerCtx, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object WineOfWelcomeCard extends Relic(RelicId("R33"), "Wine of Welcome", value = 7, defense = 1):
  val power = PrintedPower(PowerId("relic.wine-of-welcome"),
    persistent = false, cost = Cost.free,
    text = "Draw from the bottom of the world deck instead of the top.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wine of Welcome (relic R33), a free selected Search modifier: a Search
  * of the world deck draws from its bottom, stopping after a Vision as
  * usual (catalog batch 6, slice 3). It folds before the draw extensions
  * ([[BottomDraw.Priority]]), so they add cards from the bottom too.
  *
  * While it is picked, each discard pile stays choosable, warned that it
  * needs the world deck. It is warned while Mushrooms is picked, which
  * makes the world deck unavailable.
  *
  * Its line, "Drawing from the bottom of the World Deck", comes before the
  * draw. The Search's own line names the pile but cannot say which end the
  * draw took, because that is this power's rewrite of the draw operation
  * and a note cannot read an operation (procedures journal their own notes,
  * slice 3 plan-time ruling 5).
  */
final case class WineOfWelcome private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: RelicId = WineOfWelcomeCard.id
  def id: PowerId = WineOfWelcome.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def priority: Int = BottomDraw.Priority
  override def noteKeys: Vector[NoteKey] = WineOfWelcome.noteKeys

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchBeforeDraw -> Vector(Transform((_, operations) =>
      Note(id, _ => Some(WineOfWelcome.drawing(
        PowerSourceRef.Card(cardId)))) +:
        BottomDraw.reshaped(operations, SearchRules.DrawSize,
          _ == SearchSource.WorldDeck))))

  override def warning(decide: Decide, option: DecisionOptionRef)
      : Option[OptionReason] = option match
    case _: DecisionOptionRef.DiscardPile
        if decide.decisionId == SearchProcedure.sourceDecisionId =>
      Some(OptionReason.Written(WineOfWelcome.discardWarning))
    case _ => None

  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    Option.when(ModifierPicks.isPicked(ctx.answered, ctx.window,
      MushroomsCard.id))(OfferReason.Warned(WineOfWelcome.withMushrooms))

object WineOfWelcome:
  val id: PowerId = WineOfWelcomeCard.power.id
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Wine of Welcome: Drawing from the bottom of the World Deck", before
    * the draw, as Spinning Bee's line is. The pile is always the world
    * deck, so the line takes no argument. */
  val drawing: NoteKey = notes.key("drawing", Vector(
    NotePart.Text("Drawing from the bottom of the World Deck")))
  val discardWarning: String = "Wine of Welcome needs the world deck"
  /** Both Wine of Welcome's and Mushrooms' warning, whichever is picked
    * first. */
  val withMushrooms: String = "Wine of Welcome does nothing with Mushrooms"

  def forCatalog(catalog: ExecutableCatalog): WineOfWelcome =
    new WineOfWelcome(catalog)
