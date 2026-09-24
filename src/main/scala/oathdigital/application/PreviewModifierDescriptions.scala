package oathdigital.application

import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.model._
import oathdigital.protocol.PreviewModifier

/** What an offered modifier is, for the panel that orders them: the card it is
  * printed on and the major action it modifies.
  *
  * The route sites used to send `description = handlerId`, so the panel drew a
  * button reading `denizen.vow-of-peace`. Everything needed to do better is
  * already in hand at preview time -- the rule source names a card and the
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
    presentation: GamePresentationProjector) {

  def describe(ready: ReadyGame, powers: Vector[ContributingPower],
      options: Vector[OrderedRuleInvocation]): Vector[PreviewModifier] =
    options.map { option =>
      val power = powers.find(_.id.value == option.handlerId)
      val card = cardOf(option.source).map(id =>
        presentation.cardDetails(id, Some(Orientation.FaceUp), hidden = false))
      PreviewModifier(option.source.stableKey, option.handlerId,
        card.map(_.name).getOrElse(option.handlerId), card,
        modifies(power).map(_.key))
    }

  /** The major action a power modifies, read from the windows it hooks.
    * `Power.validate` (the legacy trait) requires every handler's window to
    * agree with the declared `modifier` when both exist; `ContributingPower`
    * has no separate declaration to agree with, so its first hooked window
    * naming a major action settles it.
    */
  private def modifies(power: Option[ContributingPower]): Option[MajorActionType] =
    power.flatMap(_.contributions.keys.flatMap(_.associatedMajorAction).headOption)

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
