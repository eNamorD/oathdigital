package oathdigital.gameplay.powers.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.{CatalogCards, NoteSupport, PlayerFacts, SelectedModifier}
import oathdigital.model._

/** Dragonskin Drum (relic R20), a selected Travel modifier: after traveling,
  * gain one warband. The gain follows the pawn's move in the same cost node, so
  * a Travel that is rejected gains nothing. The gain is best-effort, so an
  * empty warband bank gives nothing.
  *
  * Its line, "{Red} gained 1 warband.", reads the gain's step.
  */
final case class DragonskinDrum private (cardId: RelicId,
    catalog: ExecutableCatalog) extends SelectedModifier:
  def id: PowerId = DragonskinDrum.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)

  override def noteKeys: Vector[NoteKey] = Vector(DragonskinDrum.gained)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      operations :+ gain(ctx.activePlayer) :+ Note(id,
        NoteSupport.gainedNote(DragonskinDrum.gained,
          PowerSourceRef.Card(cardId), ctx.activePlayer, NoteUnit.Warband,
          NoteSupport.warbands)))))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

  private def gain(actor: PlayerId): Operation = BuildOps((ready, _) =>
    PlayerFacts.forceKind(ready, actor).map(kind =>
      Vector(Gain.Warbands(actor, kind, DragonskinDrum.Warbands))))

object DragonskinDrum:
  val id: PowerId = PowerId("relic.dragonskin-drum")
  val Warbands: Int = 1
  val gained: NoteKey = NoteSupport.gainedKey("gained")

  def forCatalog(catalog: ExecutableCatalog): Option[DragonskinDrum] =
    CatalogCards.relic(catalog, id).map(new DragonskinDrum(_, catalog))
