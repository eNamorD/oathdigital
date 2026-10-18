package oathdigital.application.gamelog

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.campaign.{CampaignAnswers, CampaignBattle,
  CampaignIds, CampaignPlans, CampaignSetup}
import oathdigital.gameplay.powers.campaign.AttackScore
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseOneAnswer}
import oathdigital.protocol.projection.{LogKind, LogSpan}
import ActionLines.{action, plural}
import LogSpan.Text

/** Every Campaign line after the start line (spec, "Campaign"), each posted
  * where its facts complete. The rolls themselves are detail lines, and the
  * attack line counts the skull faces and, when they differ, the warbands
  * they kill (item 13b). */
private[gamelog] final class CampaignLines(words: LogWords,
    choices: ChoiceWords, catalog: ExecutableCatalog):
  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    answered(journal, run, at, viewer) ++ recorded(journal, run, at, viewer) ++
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
        Posted(LogKind.Decision, Text("Targets: ") +:
          targets(setup, ready, viewer)),
        Posted(LogKind.Delta, Vector(Text(s"Attack Pool: ${setup.force}, " +
          s"Defense Pool: ${CampaignBattle.printedDefense(catalog, setup)}")))))
        .getOrElse(Vector.empty)
    case WalkerStepRecorded(_, ChoicePayload(id, ChooseOneAnswer(ref), by), _, _)
        if (id == CampaignIds.attackerPlan || id == CampaignIds.defenderPlan) &&
          ref != CampaignIds.finish =>
      (for
        before <- journal.readyBefore(at)
        after <- journal.readyAfter(at)
      yield Posted(LogKind.Decision, words.subject(by, run.actor,
        "activated") ++ choices.option(ref, before, after, viewer))).toVector
    case WalkerStepRecorded(_, ChoicePayload(CampaignIds.sacrifice,
        ChooseAmountAnswer(count), _), _, _) if count > 0 =>
      // Each warband sacrificed adds its worth to the attack; saying the new
      // total spares the reader the sum against the defense.
      val before = journal.readyBefore(at)
      val attack = before.flatMap(_.game.current.rollOutcomes
        .get(CampaignIds.attackPool)).fold(0)(_.score)
      val worth = before.fold(1)(CampaignBattle.sacrificeWorth)
      Vector(Posted(LogKind.Decision, Vector(Text(
        s"Sacrificed $count ${plural(count, "warband", "warbands")} " +
          s"for an attack of ${attack + count * worth}"))))
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
  private def recorded(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
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
        Posted(LogKind.Decision, Text("The bandits activated ") +:
          choices.option(CampaignPlans.markedRef(pool).get, before, after,
            viewer))
      case (OpStep(ModifyRollOutcome(CampaignIds.attackPool, _,
          Some(score)), _, after), index)
          if !rewritten(index, CampaignIds.attackPool) =>
        val kills = after.game.current.rollOutcomes
          .get(CampaignIds.attackPool).fold(0)(_.skulls)
        val skulls = AttackScore.skullFaces(
          CampaignBattle.attackFacesOf(after), AttackScore.rules(after,
            PendingTree(Vector.empty, journal.answers(run, at)), run.actor))
        Posted(LogKind.Roll, Vector(Text(
          CampaignLines.attack(score, skulls, kills))))
      case (OpStep(ModifyRollOutcome(CampaignIds.defensePool, _, Some(score)),
          _, _), index) if !rewritten(index, CampaignIds.defensePool) =>
        Posted(LogKind.Roll, Vector(Text(s"Defense: $score")))
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
    * no earlier line says, a power's covering line included. */
  private def closing(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] = journal.event(at) match
    case _: WalkerCompleted =>
      val stepped = journal.runOps(run, at)
      val ops = stepped.map(_._2)
      // A power's covering line already restates these steps.
      val restated = stepped.collect {
        case (index, step) if journal.covered(index) => step }
      val outcome = ops.dropWhile {
        case OpStep(_: RecordCampaignResult, _, _) => false
        case _ => true
      }
      val told = outcome.drop(1).filterNot(step =>
        restated.exists(_ eq step))
      outcome.headOption.collect {
        case OpStep(RecordCampaignResult(result), _, _) => result
      }.fold(ended(journal, run, at))(result =>
        gains(result, told, viewer) ++
          losses(result, run.actor, told, viewer))
    case _ => Vector.empty

  /** The line of a Campaign that ended before its battle: skipped by the
    * attacker, or left with nothing to attack. */
  private def ended(journal: LogJournal, run: Run, at: Int): Vector[Posted] =
    val skipped = CampaignAnswers.skipped(
      PendingTree(Vector.empty, journal.answers(run, at)))
    Vector(Posted(LogKind.Delta, Vector(Text(
      if skipped then "Skipped the Campaign."
      else "Nothing left to attack: the Campaign ended."))))

  private def gains(result: CampaignResult, ops: Vector[OpStep],
      viewer: Option[PlayerId]): Vector[Posted] =
    if !result.attackerWins then Vector.empty
    else result.kind match
      case CampaignKind.Raid =>
        val taken = ops.collect {
          case OpStep(Take(Piece.Card(id), _, _, _, _, _, _, _), before, after) =>
            words.one(words.card(id, before, after, viewer))
          case OpStep(Take(Piece.Banner(banner), _, _, _, _, _, _, _), _, _) =>
            Vector(words.banner(banner))
        }
        result.defender match
          case CampaignDefender.Player(defender) if taken.nonEmpty =>
            Vector(Posted(LogKind.Delta, Text("Took ") +:
              (LogWords.join(taken) ++ Vector(Text(" from "),
                words.player(defender)))))
          case _ => Vector.empty
      case CampaignKind.Conquest =>
        val placed = ops.collect {
          case OpStep(Move(Piece.Warbands(_, count),
              PositionedLocation(Location.PlayArea(player), _, _),
              PositionedLocation(Location.Site(site), _, _), _, _), _, _)
              if player == result.attacker =>
            Vector(Text(s"$count ${plural(count, "warband", "warbands")} on "),
              words.site(site))
        }
        if placed.isEmpty then Vector.empty
        else Vector(Posted(LogKind.Delta, Text("Placed ") +:
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
    // A site kill is the defender's in a Conquest, at its targets, and the
    // attacker's in a Raid, at a queued origin (slice 13).
    def fromSite(location: Location) = location match
      case Location.Site(site) => result.kind match
        case CampaignKind.Conquest => loser == result.defender
        case CampaignKind.Raid =>
          loser == CampaignDefender.Player(result.attacker) &&
            result.originForce.exists(_.site == site)
      case _ => false
    val killed = ops.collect {
      case OpStep(Kill(Piece.Warbands(_, count), PositionedLocation(place, _,
          _)), _, _) if from(place) || fromSite(place) => count
    }.sum
    val returned = ops.collect {
      case OpStep(Move(Piece.Warbands(_, count),
          PositionedLocation(Location.WarbandBank(_), _, _),
          PositionedLocation(place, _, _), _, _), _, _) if from(place) => count
    }.sum
    val lost = killed - returned
    val burned = ops.collect {
      case OpStep(burn: Burn, _, _) if from(burn.from.location) =>
        burn.resource match
          case Piece.Favor(count) => count
          case _ => 0
    }.sum
    val discarded = ops.collect {
      case OpStep(Move(Piece.Card(id), PositionedLocation(place, _, _),
          PositionedLocation(Location.RegionalDiscard(_), _, _),
          _, _), before, after)
          if from(place) => words.card(id, before, after, viewer)
    }
    val setAside = ops.collect {
      case OpStep(Move(Piece.Card(id), PositionedLocation(place, _, _),
          PositionedLocation(Location.SetAsideRelics, _, _),
          _, _), before, after)
          if from(place) => words.card(id, before, after, viewer)
    }
    val sent = ops.collect {
      case OpStep(Move(Piece.Pawn(player), _,
          PositionedLocation(Location.Site(site), _, _), _, _), _, _)
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
      Vector(Posted(LogKind.Delta, spans))

private[gamelog] object CampaignLines:
  /** "Attack: 5", "Attack: 5 with 2 skulls" or, when the skulls kill
    * another number of warbands, "Attack: 5 with 2 skulls, killing 4
    * warbands". A roll whose skulls kill nothing names none. */
  def attack(score: Int, skulls: Int, kills: Int): String =
    if kills == 0 then s"Attack: $score"
    else
      val killing =
        if kills == skulls then ""
        else s", killing $kills ${plural(kills, "warband", "warbands")}"
      s"Attack: $score with $skulls ${plural(skulls, "skull", "skulls")}" +
        killing
