package oathdigital.gameplay.operations

import oathdigital.catalog.{CardRestrictions, ExecutableCatalog}
import oathdigital.model._

/** Catalog-backed restrictions on discarding a card, for the acting player.
  * Every path that discards a card in play attaches one (see
  * `DiscardRestrictionsCoverageSuite`), so a card that cannot be discarded is
  * never offered as a choice and is refused if it is named anyway.
  *
  * A card cannot be discarded when:
  *
  *  - it is a locked denizen (`LockedAdviserOnly`) that is faceup: a locked
  *    card is locked only while it is faceup, and a facedown adviser can be
  *    discarded;
  *  - it is an intact edifice, which is locked (`Discard.RuinedEdifice` is the
  *    discard of an edifice, and only a ruined one can be discarded);
  *  - it prints a power that is selected for the action being run. A modifier
  *    a player selected at the start of an action stays in play until the
  *    action ends;
  *  - it is protected by the Hall of Ministers: an enemy of the ruler acts as if
  *    the denizens and relics at the ruler's sites were locked.
  *
  * `Bury` is not a discard and ignores locked, so it never reaches this class.
  * The selected modifiers are read from `walkerModifiers`, which the walker
  * records when an action first parks. An action's first walk, before its first
  * park, does not see them, so a choice offered at that first park may include
  * a card the next command refuses (a placement whose only replacement is an
  * active modifier); the refusal itself is exact.
  */
final class DiscardRestrictions(catalog: ExecutableCatalog,
    actor: PlayerId) extends OperationRestriction {
  private val hallPower = PowerId("edifice.e16.intact")

  override def reason(ready: ReadyGame,
      operation: CoreOperation): Option[OperationReason] =
    lockedReason(ready, operation).orElse(hallReason(ready, operation))

  private def refuse(code: String, detail: String): Option[OperationReason] =
    Some(OperationReason(code, detail, OperationReasonKind.Impossible))

  private def lockedReason(ready: ReadyGame, operation: CoreOperation)
      : Option[OperationReason] = operation match {
    case value: Discard.Denizen if inPlay(value.from.location) =>
      if (faceup(ready, value.card, value.from.location) &&
          catalog.denizens.find(_.id.value == value.card.value)
          .exists(d => d.restrictions == CardRestrictions.LockedAdviserOnly ||
            d.restrictions == CardRestrictions.Locked))
        refuse("locked", s"${value.card.value} is locked and cannot be discarded")
      else active(ready, value.card)
    case value: Discard.RuinedEdifice =>
      if (intact(ready, value.card, value.from.location))
        refuse("locked",
          s"${value.card.value} is an intact edifice and cannot be discarded")
      else None
    case value: Discard.Relic if inPlay(value.from.location) =>
      active(ready, value.card)
    case _ => None
  }

  /** Only a card in play is locked. A card drawn by a Search and discarded from
    * the temporary hand is not.
    */
  private def inPlay(from: Location): Boolean = from match {
    case _: Location.Site | _: Location.PlayArea => true
    case _ => false
  }

  /** A denizen at a site is always faceup. An adviser is as it is held. */
  private def faceup(ready: ReadyGame, card: DenizenId, from: Location)
      : Boolean = from match {
    case Location.PlayArea(owner) => ready.game.current.players
      .find(_.player == owner).exists(_.advisers.exists {
        case held: DenizenState =>
          held.id == card && held.orientation == Orientation.FaceUp
        case _ => false
      })
    case _ => true
  }

  /** `card` prints a power selected for the running action. */
  private def active(ready: ReadyGame, card: CardId): Option[OperationReason] =
    if (ready.game.current.walkerModifiers.exists(power =>
        printedBy(power).contains(card)))
      refuse("active-modifier", s"${card.value} is a modifier selected for " +
        "this action and cannot be discarded")
    else None

  private def printedBy(power: PowerId): Option[CardId] =
    catalog.denizens.find(_.powers.exists(_.id == power))
      .map(card => DenizenId(card.id.value): CardId)
      .orElse(catalog.relics.find(_.powers.exists(_.id == power))
        .map(card => RelicId(card.id.value): CardId))

  private def intact(ready: ReadyGame, card: EdificeId,
      from: Location): Boolean = from match {
    case Location.Site(site) => ready.game.current.map.sites.get(site)
      .exists(_.denizens.exists {
        case edifice: EdificeState =>
          edifice.id == card && edifice.side == EdificeSide.Intact
        case _ => false
      })
    case _ => false
  }

  private def hallReason(ready: ReadyGame, operation: CoreOperation)
      : Option[OperationReason] = {
    val source = operation match {
      case value: Discard.Denizen => Some(value.from.location)
      case value: Discard.Vision => Some(value.from.location)
      case value: Discard.RuinedEdifice => Some(value.from.location)
      case value: Discard.Relic => Some(value.from.location)
      case _ => None
    }
    source.collect { case Location.Site(site) => site }.flatMap { site =>
      val current = ready.game.current
      val actorSide = current.players.find(_.player == actor).flatMap { player =>
        ready.game.campaign.lineages.get(player.lineage).map { lineage =>
          if (lineage.role.isImperial) SiteRuler.Empire
          else SiteRuler.Player(actor)
        }
      }
      val targetRuler = current.map.sites.get(site).flatMap(state =>
        SiteRule.ruler(state.forces, current.players).toOption)
      for {
        sourceSide <- actorSide
        ruler <- targetRuler
        if SiteRule.enemies(sourceSide, ruler)
        if current.map.sites.exists { case (_, state) =>
          SiteRule.ruler(state.forces, current.players).toOption.contains(ruler) &&
            state.denizens.exists {
              case edifice: EdificeState if edifice.side == EdificeSide.Intact =>
                catalog.edifices.find(_.id.value == edifice.id.value)
                  .exists(_.intact.powers.exists(_.id == hallPower))
              case _ => false
            }
        }
      } yield OperationReason("discard-immune",
        s"cards at site ${site.value} cannot be discarded by ${actor.value}",
        OperationReasonKind.Impossible)
    }
  }
}
