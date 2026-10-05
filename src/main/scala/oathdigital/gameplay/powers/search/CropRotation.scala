package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.{CardPlayRules, DiscardPermission}
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
  * It adds one site discard permission through the same
  * `SearchPlayAdviser` window as the Mob's, so the two stack with each other
  * and with the Homeland rule, each allowing one discard. The
  * Play-Facedown-Adviser action selects its modifiers at the Search's window,
  * so it applies there too. An edifice is a denizen for placement, so a
  * ruined one may go. The generic discard rules decide which cards may go:
  * an intact edifice is locked, and Crop Rotation itself, being selected, is
  * refused by the active-modifier restriction.
  *
  * Its line, "{Red} may discard a card at their site first.", travels in the
  * permission, and card play writes it for the discard it allowed.
  */
final case class CropRotation private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = CropRotationCard.id
  def id: PowerId = CropRotation.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def noteKeys: Vector[NoteKey] = Vector(CardPlayRules.discardFirst)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree =>
          tree.adjust(children)(_.permitSiteDiscard(permission(ctx.activePlayer)))
        case _ => children
      })))

  /** One discard of a site card, credited with Crop Rotation's line. An
    * edifice is a denizen for placement, so a ruined one may go. */
  private def permission(actor: PlayerId): DiscardPermission =
    DiscardPermission(id, Note(id, _ => Some(
      CardPlayRules.discardFirst(PowerSourceRef.Card(cardId),
        NoteArg.Player(actor)))))

object CropRotation:
  val id: PowerId = CropRotationCard.power.id

  def forCatalog(catalog: ExecutableCatalog): CropRotation =
    new CropRotation(catalog)
