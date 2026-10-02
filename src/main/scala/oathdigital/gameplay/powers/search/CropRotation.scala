package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.PlacementRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object CropRotationCard extends Denizen(DenizenId("128"), "Crop Rotation", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.crop-rotation"),
    persistent = false, cost = Cost.free,
    text = "If playing to a site, you may discard a denizen there first.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Crop Rotation (card 128), a selected Search modifier with no cost: "If
  * playing to a site, you may discard a denizen there first."
  *
  * It permits exactly what the Mob face of the People's Favor permits, through
  * the same `SearchPlayAdviser` window, so the two compose and a full site
  * takes the play only with a discard. The Play-Facedown-Adviser action
  * selects its modifiers at the Search's window, so it applies there too. The
  * generic discard rules decide which cards may go: Crop Rotation itself,
  * being selected, is refused by the active-modifier restriction.
  *
  * Its line, "{Red} may discard a card at their site first.", travels in the
  * rules, and card play writes it after the discard answer. With the Mob as
  * well, the rules keep one line, so only one is written.
  */
final case class CropRotation private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = CropRotationCard.id
  def id: PowerId = CropRotation.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def noteKeys: Vector[NoteKey] = Vector(PlacementRules.discardFirst)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree =>
          tree.adjust(children)(_.withSiteDiscardFirstBy(note(ctx.activePlayer)))
        case _ => children
      })))

  private def note(actor: PlayerId): Note = Note(id, _ => Some(
    PlacementRules.discardFirst(PowerSourceRef.Card(cardId),
      NoteArg.Player(actor))))

object CropRotation:
  val id: PowerId = CropRotationCard.power.id

  def forCatalog(catalog: ExecutableCatalog): CropRotation =
    new CropRotation(catalog)
