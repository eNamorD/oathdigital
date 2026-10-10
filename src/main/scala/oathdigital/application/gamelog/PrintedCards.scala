package oathdigital.application.gamelog

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{IndexedRuleSource, RuleSourceFace, RuleSourceIndex}
import oathdigital.model._

/** The card a power is printed on, so the log can name a modifier or a used
  * power by its card.
  */
private[gamelog] final class PrintedCards(catalog: ExecutableCatalog):
  /** The card in play that carries `handlerId`. A walker power's source is
    * usually only a `GameRule` naming its own id, which a card in play
    * carries.
    *
    * Only a card the actor may already read is named: one of their own
    * advisers or relics first (they know those whichever way up), else a
    * faceup card on the board. A facedown card of anyone else is never a
    * candidate, so the lookup cannot reveal one. The log judges visibility
    * per viewer itself.
    */
  def printedOn(ready: ReadyGame, actor: PlayerId, handlerId: String)
      : Option[CardId] =
    val holders = RuleSourceIndex.enumerate(catalog, ready)
      .filter(_.powerIds.exists(_.value == handlerId))
    val own = holders.map(_.source).collectFirst:
      case RuleSourceRef.Adviser(`actor`, id) => id
      case RuleSourceRef.Relic(`actor`, id) => id: CardId
    def public = holders.filter(_.face != RuleSourceFace.FaceDown)
      .flatMap((holder: IndexedRuleSource) => cardOf(holder.source))
      .headOption
    own.orElse(public)

  /** The card a rule source is printed on. A banner face, a foundation and a
    * game rule have none.
    */
  private def cardOf(source: RuleSourceRef): Option[CardId] = source match
    case RuleSourceRef.Adviser(_, id) => Some(id)
    case RuleSourceRef.SiteCard(_, id) => Some(id)
    case RuleSourceRef.Relic(_, id) => Some(id)
    case RuleSourceRef.SiteRelic(_, id) => Some(id)
    case RuleSourceRef.Edifice(_, id) => Some(id)
    case _ => None
