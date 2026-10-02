package oathdigital.gameplay.operations

import oathdigital.catalog.{ExecutableCatalog, Locked}
import oathdigital.model._

/** The operation restrictions that hold for a command, wherever its
  * operations run (global operation restrictions design, "The restriction
  * set").
  *
  * `printed` are the restrictions a catalog's cards print: [[LockedCards]],
  * holding every lock-icon card, the [[HallOfMinisters]], and one
  * [[GrandScepter]] per scepter relic. `active` adds the
  * [[ActiveModifier]] rule for the modifiers selected for the running action,
  * and the restrictions the offered powers register. Every one refuses as
  * `Impossible`: an optional operation is skipped, a required one rejects.
  *
  * `printed` reads the cards' `Locked` trait and the Grand Scepter's card.
  * Card classes slice 3 has the Grand Scepter declare its own restriction.
  */
final class OperationRestrictions private (catalog: Option[ExecutableCatalog]):
  val printed: Vector[OperationRestriction] =
    catalog.fold(Vector.empty)(OperationRestrictions.printedBy)

  def active(powerRestrictions: Vector[OperationRestriction],
      modifiers: Vector[PowerId]): Vector[OperationRestriction] =
    printed ++ catalog.filter(_ => modifiers.nonEmpty)
      .map(ActiveModifier(_, modifiers)).toVector ++ powerRestrictions

object OperationRestrictions:
  /** No catalog: only the powers' own restrictions hold. For suites that
    * walk a hand-built power list. */
  val none: OperationRestrictions = new OperationRestrictions(None)

  def forCatalog(catalog: ExecutableCatalog): OperationRestrictions =
    new OperationRestrictions(Some(catalog))

  /** Only a card at a site or in a play area is in play. A card drawn by a
    * Search and discarded from the temporary hand is not. */
  private[operations] def inPlay(from: Location): Boolean = from match
    case _: Location.Site | _: Location.PlayArea => true
    case _ => false

  private def printedBy(catalog: ExecutableCatalog): Vector[OperationRestriction] =
    val locked: Set[CardId] =
      (catalog.denizens.collect { case d: Locked => d.id: CardId } ++
        catalog.edifices.collect {
          case e if e.intact.isInstanceOf[Locked] => e.id: CardId }).toSet
    val scepter = catalog.relic(TheGrandScepterCard.id)
      .map(relic => GrandScepter(relic.id))
    Vector(LockedCards(locked), HallOfMinisters(catalog)) ++ scepter

/** A card that prints a power selected for the running action cannot be
  * discarded while the action runs: a modifier a player selected at the start
  * of an action stays in play until the action ends. */
final case class ActiveModifier(catalog: ExecutableCatalog,
    modifiers: Vector[PowerId]) extends OperationRestriction:
  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] = operation match
    case value: Discard.Denizen
        if OperationRestrictions.inPlay(value.from.location) =>
      refused(value.card)
    case value: Discard.Relic
        if OperationRestrictions.inPlay(value.from.location) =>
      refused(value.card)
    case _ => None

  private def refused(card: CardId): Option[OperationReason] =
    Option.when(modifiers.exists(power => printedBy(power).contains(card)))(
      OperationReason("active-modifier", s"${card.value} is a modifier " +
        "selected for this action and cannot be discarded",
        OperationReasonKind.Impossible))

  private def printedBy(power: PowerId): Option[CardId] =
    catalog.denizenWithPower(power)
      .map(card => DenizenId(card.id.value): CardId)
      .orElse(catalog.relicWithPower(power)
        .map(card => RelicId(card.id.value): CardId))
