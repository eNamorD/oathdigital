package oathdigital.gameplay.operations

import oathdigital.catalog.{AdviserOnly, ExecutableCatalog, Locked, SiteOnly}
import oathdigital.model._

/** The operation restrictions that hold for a command, wherever its
  * operations run (global operation restrictions design, "The restriction
  * set").
  *
  * `printed` are the restrictions a catalog's cards print: [[LockedCards]],
  * holding every card whose `Locked` trait shows the lock icon and the cards
  * [[AncientBloodline]] locks for its holder's enemies, [[RestrictionIcons]], holding every adviser-only and site-only denizen,
  * and the [[HallOfMinisters]] and the [[GrandScepter]], which their cards
  * declare.
  * `active` adds the [[ActiveModifier]] rule for the modifiers picked for
  * the running action, and the restrictions the offered powers register.
  * Every one refuses as `Impossible`: an optional operation is skipped, a
  * required one rejects.
  * `active` reads each printed restriction and each power's through
  * [[ConsentedRestriction]], so a remembered grant lifts the refusal it
  * covers (consent design, slice 3).
  */
final class OperationRestrictions private (catalog: Option[ExecutableCatalog]):
  val printed: Vector[OperationRestriction] =
    catalog.fold(Vector.empty)(OperationRestrictions.printedBy)

  def active(powerRestrictions: Vector[OperationRestriction],
      modifiers: Vector[PowerId], picked: Vector[CardId] = Vector.empty)
      : Vector[OperationRestriction] =
    printed.map(ConsentedRestriction(_)) ++ catalog
      .filter(_ => modifiers.nonEmpty || picked.nonEmpty)
      .map(ActiveModifier(_, modifiers, picked)).toVector ++
      powerRestrictions.map(ConsentedRestriction(_))

object OperationRestrictions:
  /** No catalog: only the powers' own restrictions hold. For suites that
    * walk a hand-built power list. */
  val none: OperationRestrictions = new OperationRestrictions(None)

  def forCatalog(catalog: ExecutableCatalog): OperationRestrictions =
    new OperationRestrictions(Some(catalog))

  /** The printed powers an operation restriction implements outright,
    * with no walker power: they count as implemented
    * (`PowerImplementationStatus`). */
  val implementedRules: Set[PowerId] =
    Set(AncientBloodline.id, HallOfMinisters.id)

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
    val adviserOnly: Set[CardId] =
      catalog.denizens.collect { case d: AdviserOnly => d.id: CardId }.toSet
    val siteOnly: Set[CardId] =
      catalog.denizens.collect { case d: SiteOnly => d.id: CardId }.toSet
    Vector(LockedCards(locked, Vector(AncientBloodline)),
      RestrictionIcons(adviserOnly, siteOnly),
      HallOfMinisters, GrandScepter)

/** A card that prints a modifier picked for the running action cannot be
  * discarded while the action runs: a modifier the player picked in the
  * action's modifier decision stays in play until the action ends.
  * `modifiers` are the picked powers, as the walker holds them; `picked` are
  * their cards, for a check made outside the walk. */
final case class ActiveModifier(catalog: ExecutableCatalog,
    modifiers: Vector[PowerId], picked: Vector[CardId] = Vector.empty)
    extends OperationRestriction:
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
    Option.when(picked.contains(card) ||
        modifiers.exists(power => printedBy(power).contains(card)))(
      OperationReason("active-modifier", s"${card.value} is a modifier " +
        "selected for this action and cannot be discarded",
        OperationReasonKind.Impossible))

  private def printedBy(power: PowerId): Option[CardId] =
    catalog.denizenWithPower(power)
      .map(card => DenizenId(card.id.value): CardId)
      .orElse(catalog.relicWithPower(power)
        .map(card => RelicId(card.id.value): CardId))
