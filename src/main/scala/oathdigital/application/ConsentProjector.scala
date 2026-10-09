package oathdigital.application

import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.powers.ConsentPowers
import oathdigital.model._
import oathdigital.protocol.projection._

/** A waivable power's name as the panels write it: its source's name, the
  * Narrow Pass for the Pass. */
private[application] final class ConsentNames(
    presentation: GamePresentationProjector, powers: ConsentPowers):
  def power(id: PowerId): String = powers.source(id) match
    case Some(PowerSourceRef.Site(site)) => presentation.siteLabel(site)
    // A waivable edifice power is printed on its intact face (the Hall of
    // Ministers), so the power is named for that face.
    case Some(PowerSourceRef.Card(edifice: EdificeId)) =>
      presentation.edificeLabel(edifice, EdificeSide.Intact)
    case Some(PowerSourceRef.Card(card)) =>
      presentation.cardDetails(card, None, hidden = false).name
    case Some(PowerSourceRef.Banner(banner)) => BannerRules.displayName(banner)
    case None => presentation.safeLabel(id.value)

/** The consent parts of a viewer's projection (consent design,
  * "Projection"): the requester's waiting notice in place of the decision,
  * each asked granter's prompt, and the viewer's own preferences. Nothing
  * here says whether an answer was automatic, and no other player's
  * preferences leave the server. */
private[application] final class ConsentProjector(
    presentation: GamePresentationProjector,
    decisions: WalkerDecisionProjector, powers: ConsentPowers):
  private val names = new ConsentNames(presentation, powers)

  def applied(context: ScopedProjectionContext, projection: GameProjection)
      : GameProjection =
    val ready = context.ready
    val request = ready.consent.pending
    val requesting = request.filter(waiting =>
      context.viewer.contains(waiting.requester))
    val prompt = for
      waiting <- request
      viewer <- context.viewer
      ask <- waiting.open.find(_.granter == viewer)
    yield ConsentPromptProjection(waiting.requester.value,
      ask.powers.map(names.power), target(context, waiting))
    projection.copy(
      walkerDecision =
        if requesting.nonEmpty then None else projection.walkerDecision,
      consentWaiting = requesting.map(waiting =>
        ConsentWaitingProjection(waiting.open.map(_.granter.value))),
      consentPrompt = prompt,
      consentPreferences = context.viewer.toVector.flatMap(
        preferences(ready, _)))

  private def preferences(ready: ReadyGame, viewer: PlayerId)
      : Vector[ConsentPreferencesProjection] =
    powers.ruledBy(ready, viewer).map(power => ConsentPreferencesProjection(
      power.value, names.power(power),
      ready.game.current.players.map(_.player).filter(_ != viewer).map(
        other => ConsentPreferenceRowProjection(other.value,
          ready.consent.preference(viewer, power, other)
            .fold(ConsentSetting.CaseByCase.key)(_.key))),
      powers.source(power).collect { case PowerSourceRef.Card(card) =>
        card.value }))

  /** The option asked about, named as its decision row names it. A button
    * has no name of its own, so its label is read from the parked
    * decision the request is about. */
  private def target(context: ScopedProjectionContext,
      request: ConsentRequest): String =
    val ref = request.target
    val option = ref match
      case button: DecisionOptionRef.Button => decisions
        .parkedDecide(context.ready)
        .filter(_.decisionId == request.decisionId).toVector
        .flatMap(offered).find(_.ref == button)
      case _ => DecisionOption.forRef(ref)
    option.flatMap(decisions.optionProjection(context.ready, context.viewer,
        context.ready.game.cardIndex.toOption, _))
      .fold(ref.wireId)(_.label)

  /** The options of a choose-one or a choose-many, as offered. */
  private def offered(decide: Decide): Vector[DecisionOption] =
    decide.query match
      case one: DecisionQuery.ChooseOne => one.options
      case many: DecisionQuery.ChooseMany => many.options
      case _ => Vector.empty

private[application] object ConsentProjector:
  val withdrawNote = "Withdraw your consent request"
