package oathdigital.application

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{IndexedRuleSource, RuleSourceFace, RuleSourceIndex}
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.model._
import oathdigital.protocol.PreviewModifier

/** What an offered modifier is, for the panel that orders them: the card it is
  * printed on and the major action it modifies.
  *
  * The route sites used to send `description = handlerId`, so the panel drew a
  * button reading `denizen.vow-of-peace`. Everything needed to do better is
  * already in hand at preview time -- the rule source names a card (or, for
  * a walker power, only its own id, which a card in play carries) and the
  * power declares which windows it hooks -- so it is resolved here, once,
  * rather than three times at three route sites.
  *
  * `powers` is typed `ContributingPower` (the walker mechanism), not the
  * legacy `Power` trait: `GameApplicationService`'s walker branch is the only
  * branch that has power objects in hand at all (`offerableWalkerPowers`
  * returns `ContributingPower`s), and the legacy branch calls this with an
  * empty vector since `PowerRuntime.options` never constructs one. A
  * `ContributingPower` carries no `modifier` field of its own -- its
  * classification lives on the windows it hooks, exactly as
  * `ContributingPower.shouldIgnore`'s own doc comment states
  * (`PowerWindow.associatedMajorAction`) -- so `modifies` below reads it from
  * there rather than from a field the trait does not have.
  */
private[application] final class PreviewModifierDescriptions(
    catalog: ExecutableCatalog, presentation: GamePresentationProjector) {

  def describe(ready: ReadyGame, actor: PlayerId, action: ActionKind,
      powers: Vector[ContributingPower],
      options: Vector[OrderedRuleInvocation]): Vector[PreviewModifier] =
    options.map { option =>
      val power = powers.find(_.id.value == option.handlerId)
      val card = cardOf(option.source).orElse(option.source match {
        case RuleSourceRef.GameRule(_) =>
          printedOn(ready, actor, option.handlerId)
        case _ => None
      }).map(id =>
        presentation.cardDetails(id, Some(Orientation.FaceUp), hidden = false))
      PreviewModifier(option.source.stableKey, option.handlerId,
        card.map(_.name).getOrElse(option.handlerId), card,
        modifies(action, power).map(_.key))
    }

  /** The major action a power modifies, read from the windows it hooks.
    * `Power.validate` (the legacy trait) requires every handler's window to
    * agree with the declared `modifier` when both exist; `ContributingPower`
    * has no separate declaration to agree with, so the windows settle it.
    * The action being previewed wins when the power hooks it, since that is
    * the action it is offered for; otherwise the first in `MajorActionType`
    * order, so a power hooking two actions' windows reads the same every
    * time rather than in whatever order a `Set` iterates.
    */
  private def modifies(action: ActionKind, power: Option[ContributingPower])
      : Option[MajorActionType] = power.flatMap { power =>
    val hooked = power.contributions.keySet.flatMap(_.associatedMajorAction)
    hooked.find(_.key == action.key).orElse(
      MajorActionType.values.find(hooked.contains))
  }

  /** The card in play that carries `handlerId`, for a walker power whose
    * source is only a `GameRule` naming its own id -- which is nearly every
    * walker power, so without this no walker modifier would draw a card.
    *
    * Only a card the actor may already read is named: one of their own
    * advisers or relics first (they know those whichever way up), else a
    * faceup card on the board. A facedown card of anyone else is never a
    * candidate, so the lookup cannot reveal one.
    */
  private def printedOn(ready: ReadyGame, actor: PlayerId, handlerId: String)
      : Option[CardId] = {
    val holders = RuleSourceIndex.enumerate(catalog, ready)
      .filter(_.powerIds.exists(_.value == handlerId))
    val own = holders.map(_.source).collectFirst {
      case RuleSourceRef.Adviser(`actor`, id) => id
      case RuleSourceRef.Relic(`actor`, id) => id: CardId
    }
    def public = holders.filter(_.face != RuleSourceFace.FaceDown)
      .flatMap((holder: IndexedRuleSource) => cardOf(holder.source))
      .headOption
    own.orElse(public)
  }

  /** The card a rule source is printed on. A banner face, a foundation and a
    * game rule have none.
    */
  private def cardOf(source: RuleSourceRef): Option[CardId] = source match {
    case RuleSourceRef.Adviser(_, id) => Some(id)
    case RuleSourceRef.SiteCard(_, id) => Some(id)
    case RuleSourceRef.Relic(_, id) => Some(id)
    case RuleSourceRef.SiteRelic(_, id) => Some(id)
    case RuleSourceRef.Edifice(_, id) => Some(id)
    case _ => None
  }
}
