package oathdigital.application.gamelog

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignBattle, CampaignIds,
  CampaignPlans, CampaignSetup}
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseOneAnswer}
import ActionLines.{action, plural}
import LogSpan.Text

/** Every Campaign line after the start line (spec, "Campaign"), each posted
  * where its facts complete. The rolls themselves are detail lines. */
private[gamelog] final class CampaignLines(words: LogWords,
    choices: ChoiceWords, catalog: ExecutableCatalog):
  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    answered(journal, run, at, viewer) ++ recorded(journal, at, viewer) ++
      closing(journal, run, at, viewer)

  /** Targets and pools at the force answer; a battle plan at its choice; a
    * sacrifice at its amount. */
  private def answered(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] = journal.event(at) match
    case WalkerStepRecorded(_, ChoicePayload(CampaignIds.force, _, _), _, _) =>
      (for
        ready <- journal.readyAfter(at)
        setup <- CampaignSetup.setup(ready, run.actor,
          PendingTree(Vector.empty, journal.answers(run, at)))
      yield Vector(
        Posted.line(LogKind.Decision, Text("Targets: ") +:
          targets(setup, ready, viewer)),
        Posted.line(LogKind.Delta, Vector(Text(s"Attack Pool: ${setup.force}, " +
          s"Defense Pool: ${CampaignBattle.printedDefense(catalog, setup)}")))))
        .getOrElse(Vector.empty)
    case WalkerStepRecorded(_, ChoicePayload(id, ChooseOneAnswer(ref), by), _, _)
        if (id == CampaignIds.attackerPlan || id == CampaignIds.defenderPlan) &&
          ref != CampaignIds.finish =>
      (for
        before <- journal.readyBefore(at)
        after <- journal.readyAfter(at)
      yield Posted.line(LogKind.Decision, words.subject(by, run.actor,
        "activated") ++ choices.option(ref, before, after, viewer))).toVector
    case WalkerStepRecorded(_, ChoicePayload(CampaignIds.sacrifice,
        ChooseAmountAnswer(count), _), _, _) if count > 0 =>
      // Each warband sacrificed adds one to the attack; saying the new
      // total spares the reader the sum against the defense.
      val attack = journal.readyBefore(at).flatMap(_.game.current.rollOutcomes
        .get(CampaignIds.attackPool)).fold(0)(_.score)
      Vector(Posted.line(LogKind.Decision, Vector(Text(
        s"Sacrificed $count ${plural(count, "warband", "warbands")} " +
          s"for an attack of ${attack + count}"))))
    case _ => Vector.empty

  private def targets(setup: CampaignSetup, ready: ReadyGame,
      viewer: Option[PlayerId]): Vector[LogSpan] = setup.kind match
    case CampaignKind.Conquest =>
      LogWords.join(setup.targetSites.map(site => Vector(words.site(site))))
    case CampaignKind.Raid => LogWords.join(setup.raidTargets.map {
      case CampaignRaidTarget.Pawn(player) =>
        Vector(words.player(player), Text("'s pawn"))
      case CampaignRaidTarget.Relic(_, relic) =>
        words.one(words.card(relic, ready, ready, viewer))
      case CampaignRaidTarget.Banner(_, banner) => Vector(words.banner(banner))
    })

  /** The two totals as the result windows write them, each plan the bandits
    * applied, and the winner. */
  private def recorded(journal: LogJournal, at: Int, viewer: Option[PlayerId])
      : Vector[Posted] =
    val ops = journal.ops(at)
    val later = (at + 1 to journal.segmentEnd(at)).toVector.flatMap(journal.ops)
    // A plan that rewrites a total after the skull cap (Outriders) writes the
    // same pool again in this segment; only the final total is told.
    def rewritten(index: Int, pool: PoolKey): Boolean =
      (ops.drop(index + 1) ++ later).exists {
        case OpStep(ModifyRollOutcome(again, _, Some(_)), _, _) => again == pool
        case _ => false
      }
    ops.zipWithIndex.collect {
      case (OpStep(ModifyDicePool(pool, _, _), before, after), _)
          if CampaignPlans.markedRef(pool).nonEmpty =>
        Posted.line(LogKind.Decision, Text("The bandits activated ") +:
          choices.option(CampaignPlans.markedRef(pool).get, before, after,
            viewer))
      case (OpStep(ModifyRollOutcome(CampaignIds.attackPool, skulls,
          Some(score)), _, _), index)
          if !rewritten(index, CampaignIds.attackPool) =>
        val paid = skulls.filter(_ > 0).fold("")(count =>
          s" with $count ${plural(count, "skull", "skulls")}")
        Posted.line(LogKind.Roll, Vector(Text(s"Attack: $score$paid")))
      case (OpStep(ModifyRollOutcome(CampaignIds.defensePool, _, Some(score)),
          _, _), index) if !rewritten(index, CampaignIds.defensePool) =>
        Posted.line(LogKind.Roll, Vector(Text(s"Defense: $score")))
      case (OpStep(RecordCampaignResult(result), _, _), _) =>
        action(
          if result.attackerWins then
            Vector(words.player(result.attacker), Text(" wins!"))
          else result.defender match
            case CampaignDefender.Player(player) =>
              Vector(words.player(player), Text(" wins!"))
            case CampaignDefender.Bandits => Vector(Text("The bandits win!")))
    }

  /** At completion: what the winner gained, then what the loser lost that
    * no earlier line says. */
  private def closing(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] = journal.event(at) match
    case _: WalkerCompleted =>
      val ops = journal.runOps(run, at).map(_._2)
      val outcome = ops.dropWhile {
        case OpStep(_: RecordCampaignResult, _, _) => false
        case _ => true
      }
      outcome.headOption.collect {
        case OpStep(RecordCampaignResult(result), _, _) => result
      }.toVector.flatMap(result => gains(result, outcome.tail, viewer) ++
        losses(result, run.actor, outcome.tail, viewer))
    case _ => Vector.empty

  private def gains(result: CampaignResult, ops: Vector[OpStep],
      viewer: Option[PlayerId]): Vector[Posted] =
    if !result.attackerWins then Vector.empty
    else result.kind match
      case CampaignKind.Raid =>
        val taken = ops.collect {
          case OpStep(Take(Piece.Card(id), _, _, _, _, _, _), before, after) =>
            words.one(words.card(id, before, after, viewer))
          case OpStep(Take(Piece.Banner(banner), _, _, _, _, _, _), _, _) =>
            Vector(words.banner(banner))
        }
        result.defender match
          case CampaignDefender.Player(defender) if taken.nonEmpty =>
            Vector(Posted.line(LogKind.Delta, Text("Took ") +:
              (LogWords.join(taken) ++ Vector(Text(" from "),
                words.player(defender)))))
          case _ => Vector.empty
      case CampaignKind.Conquest =>
        val placed = ops.collect {
          case OpStep(Move(Piece.Warbands(_, count),
              PositionedLocation(Location.PlayArea(player), _),
              PositionedLocation(Location.Site(site), _), _), _, _)
              if player == result.attacker =>
            Vector(Text(s"$count ${plural(count, "warband", "warbands")} on "),
              words.site(site))
        }
        if placed.isEmpty then Vector.empty
        else Vector(Posted.line(LogKind.Delta, Text("Placed ") +:
          LogWords.join(placed)))

  private def losses(result: CampaignResult, actor: PlayerId,
      ops: Vector[OpStep], viewer: Option[PlayerId]): Vector[Posted] =
    val loser =
      if result.attackerWins then result.defender
      else CampaignDefender.Player(result.attacker)
    val owner = loser match
      case CampaignDefender.Player(player) => Some(player)
      case CampaignDefender.Bandits => None
    def from(location: Location) = owner.exists(player =>
      location == Location.PlayArea(player))
    val killed = ops.collect {
      case OpStep(Kill(Piece.Warbands(_, count), PositionedLocation(place, _)),
          _, _) if from(place) || (loser == result.defender &&
            place.isInstanceOf[Location.Site]) => count
    }.sum
    val returned = ops.collect {
      case OpStep(Move(Piece.Warbands(_, count),
          PositionedLocation(Location.WarbandBank(_), _),
          PositionedLocation(place, _), _), _, _) if from(place) => count
    }.sum
    val lost = killed - returned
    val burned = ops.collect {
      case OpStep(burn: Burn, _, _) if from(burn.from.location) =>
        burn.resource match
          case Piece.Favor(count) => count
          case _ => 0
    }.sum
    val discarded = ops.collect {
      case OpStep(Move(Piece.Card(id), PositionedLocation(place, _),
          PositionedLocation(Location.RegionalDiscard(_), _), _), before, after)
          if from(place) => words.card(id, before, after, viewer)
    }
    val setAside = ops.collect {
      case OpStep(Move(Piece.Card(id), PositionedLocation(place, _),
          PositionedLocation(Location.SetAsideRelics, _), _), before, after)
          if from(place) => words.card(id, before, after, viewer)
    }
    val sent = ops.collect {
      case OpStep(Move(Piece.Pawn(player), _,
          PositionedLocation(Location.Site(site), _), _), _, _)
          if owner.contains(player) => site
    }.lastOption
    val parts = Vector(
      Option.when(lost > 0)(Vector[LogSpan](Text(
        s"lost $lost ${plural(lost, "warband", "warbands")}"))),
      Option.when(burned > 0)(Vector[LogSpan](Text(s"burned $burned favor"))),
      Option.when(discarded.nonEmpty)(Text("discarded ") +:
        words.cards(discarded)),
      Option.when(setAside.nonEmpty)(Text("set aside ") +:
        words.cards(setAside)),
      sent.map(site => Vector[LogSpan](Text("was sent to "), words.site(site)))
    ).flatten
    if parts.isEmpty then Vector.empty
    else
      val said = LogWords.join(parts)
      val subject: Vector[LogSpan] = owner match
        case Some(player) if player == actor => Vector.empty
        case Some(player) => Vector(words.player(player), Text(" "))
        case None => Vector(Text("The bandits "))
      val spans = if subject.nonEmpty then subject ++ said else said match
        case Text(first) +: rest => Text(first.capitalize) +: rest
        case other => other
      Vector(Posted.line(LogKind.Delta, spans))
