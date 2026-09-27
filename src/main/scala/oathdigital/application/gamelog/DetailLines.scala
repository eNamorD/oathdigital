package oathdigital.application.gamelog

import oathdigital.application.GamePresentationProjector
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.challenge.{ChallengeProcedure,
  PlaceBannerResourceProcedure}
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.forge.ForgeProcedure
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.walker.{ChoicePayload, RollPayload,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseOneAnswer}

/** The detail lines of a walker run (spec, "Detail lines"): decisions, rolls
  * and deltas, between a start line and the action line that closes the
  * action. A rule here stays silent wherever an action line, a start line or
  * another procedure's own lines already say the same thing.
  */
private[gamelog] final class DetailLines(words: LogWords,
    choices: ChoiceWords):
  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    decision(journal, run, at, viewer) ++ (if journal.covered(at) then
      Vector.empty else roll(journal, at) ++ deltas(journal, run, at, viewer))

  private def roll(journal: LogJournal, at: Int): Vector[Posted] =
    journal.event(at) match
      case WalkerStepRecorded(_, RollPayload(pool, faces, _), _, _) =>
        LogWords.dice(faces).toVector.map(dice => Posted.line(LogKind.Roll,
          Vector(LogSpan.Text("Rolled "), dice) ++ DetailLines.forPool(pool)))
      case _ => Vector.empty

  private def decision(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] = journal.event(at) match
    case WalkerStepRecorded(_, ChoicePayload(id, answer, by), _, _)
        if !DetailLines.narrated(id) =>
      val refs = answer match
        // Pressing Done after looking at cards is not a choice (N5).
        case ChooseOneAnswer(DecisionQuery.Inspect.Done) => Vector.empty
        case ChooseOneAnswer(ref) => Vector(ref)
        case ChooseManyAnswer(selected) => selected
        case _ => Vector.empty
      (for
        before <- journal.readyBefore(at)
        after <- journal.readyAfter(at)
        if refs.nonEmpty
      yield Posted.line(LogKind.Decision, words.subject(by, run.actor, "chose")
        ++ choices.options(refs, before, after, viewer))).toVector
    case _ => Vector.empty

  /** Resource and card changes inside a run, in batch order. Consecutive
    * discards to the same pile by the same player read as one line. */
  private def deltas(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val placedDiscard = discardedPlacement(journal, run, at)
    val found = journal.ops(at).flatMap(step =>
      DetailLines.parts(step.operation).flatMap(part =>
        delta(run, OpStep(part, step.before, step.after), placedDiscard,
          viewer)))
    found.foldLeft(Vector.empty[DetailLines.Found]) {
      case (done :+ DetailLines.Discarded(who, region, cards),
          DetailLines.Discarded(next, again, more))
          if who == next && region == again =>
        done :+ DetailLines.Discarded(who, region, cards ++ more)
      case (done, next) => done :+ next
    }.map {
      case DetailLines.Line(posted) => posted
      case DetailLines.Discarded(who, region, cards) =>
        Posted.line(LogKind.Delta, who ++ words.cards(cards) ++ Vector(
          LogSpan.Text(s" to the ${region.key.capitalize} discard")))
    }

  private def delta(run: Run, step: OpStep, placedDiscard: Option[CardId],
      viewer: Option[PlayerId]): Vector[DetailLines.Found] =
    import DetailLines.{Discarded, Line}
    val actor = run.actor
    val OpStep(operation, before, after) = step
    def line(spans: Vector[LogSpan]) =
      Vector(Line(Posted.line(LogKind.Delta, spans)))
    def card(id: CardId) = words.card(id, before, after, viewer)
    def gainedFavor(player: PlayerId, suit: Suit, amount: Int) =
      if run.procedure == ActionRef.Trade && player == actor then Vector.empty
      else line(words.subject(player, actor, "gained") :+
        LogSpan.Text(s"$amount favor from the $suit bank"))
    def gainedSecrets(player: PlayerId, amount: Int) =
      if run.procedure == ActionRef.Trade && player == actor then Vector.empty
      else line(words.subject(player, actor, "gained") :+ LogSpan.Text(
        s"$amount ${ActionLines.plural(amount, "secret", "secrets")}"))
    operation match
      case Gain.Favor(player, suit, amount) => gainedFavor(player, suit, amount)
      case Move(Piece.Favor(amount),
          PositionedLocation(Location.FavorBank(suit), _),
          PositionedLocation(Location.PlayArea(player), _), _) =>
        gainedFavor(player, suit, amount)
      case Gain.Secrets(player, amount) => gainedSecrets(player, amount)
      case Move(Piece.Secrets(amount), PositionedLocation(Location.SharedBank, _),
          PositionedLocation(Location.PlayArea(player), _), _) =>
        gainedSecrets(player, amount)
      case Move(Piece.Warbands(kind, amount), _,
          PositionedLocation(Location.Site(site), _), _)
          if run.procedure != ActionRef.Campaign =>
        line(LogSpan.Text(s"Moved $amount ") +:
          (whose(kind, amount, actor, before) ++
            Vector(LogSpan.Text(" to "), words.site(site))))
      case Draw(player, cards, _, _) if run.procedure != ActionRef.Search =>
        line(words.subject(player, actor, "drew") ++
          words.cards(cards.map(card)))
      case discard: Discard.Denizen if !placedDiscard.contains(discard.card) =>
        Vector(Discarded(words.subject(discard.actingPlayer, actor,
          "discarded"), discard.to, Vector(card(discard.card))))
      case discard: Discard.Vision if !placedDiscard.contains(discard.card) =>
        Vector(Discarded(Vector(LogSpan.Text("Discarded ")), discard.to,
          Vector(card(discard.card))))
      case Bury(buried, _, _) =>
        line(LogSpan.Text("Buried ") +: words.one(card(buried.id)))
      case Peek(peeker, id, _) if run.procedure != ActionRef.Negotiation =>
        line(words.subject(peeker, actor, "peeked at") ++ words.one(card(id)))
      case Reveal(id, _) => line(LogSpan.Text("Revealed ") +: words.one(card(id)))
      case Move(Piece.Card(id), PositionedLocation(Location.PlayArea(owner), _),
          PositionedLocation(Location.PlayArea(same), _),
          Some(Orientation.FaceUp)) if owner == same && faceDown(before, id) =>
        line(words.subject(owner, actor, "revealed") ++ words.one(card(id)))
      case _ => Vector.empty

  /** Whose warbands moved: the actor's go unnamed, anyone else's are named
    * ("1 of Blue's warbands"), and bandits are bandits. */
  private def whose(kind: ForceKind, amount: Int, actor: PlayerId,
      ready: ReadyGame): Vector[LogSpan] =
    val noun = ActionLines.plural(amount, "warband", "warbands")
    kind match
      case ForceKind.Exile(lineage) => ready.game.current.players
          .find(_.lineage == lineage).map(_.player).filter(_ != actor) match
        // "1 of Blue's warbands": after "of" the noun stays plural.
        case Some(owner) => Vector(LogSpan.Text("of "), words.player(owner),
          LogSpan.Text("'s warbands"))
        case None => Vector(LogSpan.Text(noun))
      case ForceKind.Imperial => Vector(LogSpan.Text(s"Imperial $noun"))
      case ForceKind.Bandit =>
        Vector(LogSpan.Text(ActionLines.plural(amount, "bandit", "bandits")))

  private def faceDown(ready: ReadyGame, id: CardId): Boolean =
    CardIndex.from(ready.game).toOption.flatMap(_.get(id))
      .flatMap(located => GamePresentationProjector.orientationOf(located.state))
      .contains(Orientation.FaceDown)

  /** The card Card Play's placement answer sent to the discard, which the
    * "Discarded {card}" action line already tells. */
  private def discardedPlacement(journal: LogJournal, run: Run, at: Int)
      : Option[CardId] =
    journal.answers(run, at).reverse.collectFirst {
      case Answered(id, ChooseOneAnswer(DecisionOptionRef.Button("discard")), _)
          if id.startsWith(ActionLines.PlacePrefix) =>
        ActionLines.subjectCard(id.stripPrefix(ActionLines.PlacePrefix))
    }.flatten

private[gamelog] object DetailLines:
  private[gamelog] sealed trait Found
  private[gamelog] final case class Line(posted: Posted) extends Found
  private[gamelog] final case class Discarded(who: Vector[LogSpan],
      region: Region, cards: Vector[CardWord]) extends Found

  /** A recorded operation broken down only as far as a detail rule reads:
    * the composites the rules name stay whole; other composites open into
    * their children. */
  def parts(operation: CoreOperation): Vector[CoreOperation] = operation match
    case _: Gain.Favor | _: Gain.Secrets | _: Draw | _: Discard.Denizen |
        _: Discard.Vision | _: Reveal => Vector(operation)
    case _: PrimitiveOperation => Vector(operation)
    case composite => composite.children.collect {
      case child: CoreOperation => child }.flatMap(parts)

  /** Decisions other lines tell: Setup's own lines, Card Play's placement
    * line and the discard line of a replaced adviser, every Campaign and
    * Negotiation line, and each action line that names its choice. */
  private val NarratedPrefixes = Vector("setup.", ActionLines.PlacePrefix,
    "cardplay.replace.", "campaign.", "negotiation.")
  private val NarratedIds = Set(SearchProcedure.cardDecisionId,
    RecoverProcedure.choiceDecisionId, RecoverProcedure.relicDecisionId,
    MusterProcedure.decisionId, TradeProcedure.decisionId,
    ForgeProcedure.assignmentDecisionId, ChallengeProcedure.bannerDecisionId,
    ChallengeProcedure.amountDecisionId,
    PlaceBannerResourceProcedure.bannerDecisionId,
    PlaceBannerResourceProcedure.amountDecisionId,
    OathkeeperProcedure.recipientDecisionId)

  def forPool(pool: PoolKey): Vector[LogSpan] =
    if pool == CampaignIds.attackPool then Vector(LogSpan.Text(" for the attack"))
    else if pool == CampaignIds.defensePool then
      Vector(LogSpan.Text(" for the defense"))
    else Vector.empty

  def narrated(decisionId: String): Boolean =
    NarratedIds(decisionId) || NarratedPrefixes.exists(decisionId.startsWith)
