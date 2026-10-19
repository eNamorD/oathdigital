package oathdigital.gameplay.walker

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.model._

/** A follow-up entry's question (walker-driven game loop design, ruling 6):
  * the action, for no Supply, or Skip. The driver starts it once it has
  * dequeued an `Offer` entry, which `walkerEntry` then holds.
  *
  * The action is unavailable, with its reason, when its start would be
  * refused (`QueuedStart`). Taking it puts the entry, as a `Run`, in front
  * of the queue (plan-time ruling 1), and the driver starts it when this
  * walk ends. Skip ends the walk; `OathRulesWalker` then writes
  * `FollowUpSkipped` for the Game Log. The tree runs nothing before its
  * decision, so a resume rebuilds the same question.
  */
object FollowUpOffer:
  val decisionId: String = "follow-up.offer"
  val take: DecisionOptionRef.Button =
    DecisionOptionRef.Button("follow-up.take")
  val skip: DecisionOptionRef.Button =
    DecisionOptionRef.Button("follow-up.skip")

  def build(phasePowers: PhasePowers, powers: WalkerPowers)(
      catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    for
      _ <- Either.cond(args.isEmpty, (), OathViolation.InvalidEventOrder(
        "the follow-up offer takes no start selection"))
      entry <- ready.game.current.walkerEntry
        .filter(_.kind == QueueKind.Offer)
        .toRight(OathViolation.InvalidEventOrder(
          "no follow-up offer was dequeued"))
    yield
      val run = entry.copy(kind = QueueKind.Run)
      val named = action(entry.procedure)
      val shown = DecisionOption.Button(take, s"$named for no Supply")
      val offered = QueuedStart.refusal(catalog, ready, run, powers,
          phasePowers)
        .fold[DecisionOption](shown)(violation => DecisionOption.Unavailable(
          shown, OptionReason.Written(reason(entry.procedure, violation))))
      Sequence(
        Decide(decisionId, entry.actor, DecisionQuery.ChooseOne(
          Vector(offered, DecisionOption.Button(skip, "Skip")),
          heading = Some(sourceName(catalog, entry.source)
            .fold(named)(name => s"$named · $name")))),
        Branch((_, pending) =>
          if picked(pending, take) then
            Vector(QueueProcedure(run, atFront = true))
          else Vector.empty))

  /** Whether `event` records the Skip answer. */
  def skips(event: OathEvent): Boolean = event match
    case WalkerStepRecorded(_, ChoicePayload(`decisionId`,
        DecisionAnswer.ChooseOneAnswer(`skip`), _), _) => true
    case _ => false

  private def picked(pending: PendingTree, ref: DecisionOptionRef): Boolean =
    pending.answered.exists:
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(answer), _) =>
        answer == ref
      case _ => false

  /** "Campaign", "Travel": the action as the option names it. */
  private def action(procedure: StartableRef): String =
    procedure.key.capitalize

  /** Why the entry cannot start, as each action words it. A Campaign shows
    * its refusal's own text, or names the missing target. */
  private def reason(procedure: StartableRef, violation: OathViolation)
      : String = (procedure, violation) match
    case (ActionRef.Campaign, OathViolation.CampaignUnavailable(detail))
        if detail == CampaignProcedure.NothingToAttack =>
      "No site to conquer and no enemy pawn to raid"
    case (ActionRef.Campaign, OathViolation.CampaignUnavailable(detail)) =>
      detail
    case (ActionRef.Travel, _) => "No site to travel to"
    case _ => "Cannot start now"

  private def sourceName(catalog: ExecutableCatalog,
      source: Option[PowerSourceRef]): Option[String] = source.flatMap:
    case PowerSourceRef.Card(id: DenizenId) => catalog.denizen(id).map(_.name)
    case PowerSourceRef.Card(id: RelicId) => catalog.relic(id).map(_.name)
    case PowerSourceRef.Site(id) => catalog.site(id).map(_.name)
    case PowerSourceRef.Banner(banner) =>
      Some(BannerRules.displayName(banner))
    case _ => None
