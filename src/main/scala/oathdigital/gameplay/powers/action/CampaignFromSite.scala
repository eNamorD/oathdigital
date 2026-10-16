package oathdigital.gameplay.powers.action

import oathdigital.catalog.PrintedPower
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powerresolver.QueuedStarts
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** An ACTION that campaigns from a site other than the pawn's (catalog
  * batch 8, slice 13): Wild Allies and Captains. It pays its cost onto the
  * card, asks the site, then queues a Campaign `Run` at the front of the
  * queue with the site as its origin and the card as its source, so it
  * spends no Supply. The Campaign judges its targets and force from the
  * origin, and the actor's warbands there join the force.
  *
  * The site question lists `sites` in map order, a site whose Campaign
  * would not start unavailable with why. With none that would, the ACTION
  * is unavailable with `noSite`.
  */
abstract class CampaignFromSite(power: PrintedPower, card: DenizenId)
    extends PaidAction(power):
  /** The sites the card lets its user campaign from, in map order. */
  protected def sites(ready: ReadyGame, player: PlayerId): Vector[SiteId]
  /** The menu's reason when no site's Campaign would start. */
  protected def noSite: String
  /** The card's name, for the question's heading. */
  protected def name: String

  def siteDecisionId: String
  /** "Campaign at {site}." */
  def campaignAt: NoteKey

  override def narratedDecisions: Set[String] = Set(siteDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] =
    build(ready, player, source, QueuedStarts.unchecked)

  override def build(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef, starts: QueuedStarts)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => Vector(Decide(siteDecisionId, player,
      DecisionQuery.ChooseOne(options(live, player, starts),
        heading = Some(s"$name: choose the site to campaign at"),
        confirmVerb = Some("Campaign at")),
      passWhenEmpty = true))),
    Branch((_, pending) => PowerAnswers.one(pending, siteDecisionId)
      .toVector.collect { case DecisionOptionRef.Site(site) =>
        QueueProcedure(entry(player, site), atFront = true): Operation }),
    Note(id, states => NoteSupport.answer(states, siteDecisionId).collect {
      case DecisionOptionRef.Site(site) =>
        campaignAt(PowerSourceRef.Card(card), NoteArg.Site(site)) }))))

  override def refusal(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef, starts: QueuedStarts): Option[String] =
    Option.unless(sites(ready, player).exists(site =>
      starts.refusal(ready, entry(player, site)).isEmpty))(noSite)

  /** The queued Campaign from `site`. */
  def entry(player: PlayerId, site: SiteId): QueuedProcedure =
    QueuedProcedure(player, ActionRef.Campaign,
      Vector(DecisionOptionRef.Site(site)), Some(PowerSourceRef.Card(card)),
      QueueKind.Run)

  private def options(ready: ReadyGame, player: PlayerId,
      starts: QueuedStarts): Vector[DecisionOption] =
    sites(ready, player).map { site =>
      val shown = DecisionOption.Site(DecisionOptionRef.Site(site))
      starts.refusal(ready, entry(player, site)).fold[DecisionOption](shown)(
        violation => DecisionOption.Unavailable(shown,
          OptionReason.Written(CampaignFromSite.reason(violation))))
    }

object CampaignFromSite:
  /** Why a site's Campaign would not start, in the player's words, about
    * that site. */
  def reason(violation: OathViolation): String = violation match
    case OathViolation.CampaignUnavailable(detail)
        if detail == CampaignProcedure.NothingToAttack =>
      "Nothing to conquer and no enemy pawn to raid"
    case OathViolation.CampaignUnavailable(detail) => detail
    case OathViolation.StartUnavailable(text) => text
    case _ => "Cannot start now"
