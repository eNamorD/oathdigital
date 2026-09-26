package oathdigital.application.gamelog

import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, PartitionAnswer}
import LogSpan.Text

/** The action line each procedure posts, at the event its facts complete
  * (spec, "Action lines"). A rule reads the event being formatted and the
  * run's events before it; a name alone may come from later in the same
  * segment. The subject is omitted: it is the actor's own action.
  */
private[gamelog] final class ActionLines(words: LogWords):
  import ActionLines._

  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val actor = run.actor
    val ops = journal.ops(at)
    val completing = journal.event(at).isInstanceOf[WalkerCompleted]
    run.procedure match
      case ActionRef.Travel => ops.collect {
        case OpStep(Move(Piece.Pawn(mover), _,
            PositionedLocation(Location.Site(to), _), _), _, _)
            if mover == actor =>
          action(Vector(Text("Travelled to "), words.site(to)))
      }
      case TriggeredProcedureRef.Oathkeeper => ops.collect {
        case OpStep(SetOathkeeper(holder), _, _) =>
          Posted.line(LogKind.Trigger, Vector(Text("Oathkeeper passed to "),
            holder.fold[LogSpan](Text("the bank"))(words.player)))
      }
      case PhaseTransitionRef.FinishRest => ops.collect {
        case OpStep(GainSupply(player, _), before, after) =>
          Posted.line(LogKind.Delta, Vector(Text(
            s"Increased supply from ${LogJournal.supply(before, player)} " +
              s"to ${LogJournal.supply(after, player)}")))
      }
      // The phase changes show in the lines around them.
      case PhaseTransitionRef.EndWake | PhaseTransitionRef.BeginRest =>
        Vector.empty
      // Setup lines: the second slice.
      case TriggeredProcedureRef.Setup => Vector.empty
      case ActionRef.Search => search(journal, run, at, viewer)
      case ActionRef.PlayFacedownAdviser =>
        playedAdviser(journal, run, at, viewer)
      case ActionRef.Muster => ops.collect {
        case OpStep(Move(Piece.Warbands(_, count),
            PositionedLocation(Location.WarbandBank(_), _),
            PositionedLocation(Location.PlayArea(taker), _), _), before, after)
            if taker == actor =>
          action(Vector(Text(
            s"Mustered $count ${plural(count, "warband", "warbands")} with ")) ++
            source(journal, run, at, MusterProcedure.decisionId, before, after,
              viewer))
      }
      case ActionRef.Trade =>
        val gained = ops.collect {
          case OpStep(Move(piece @ Piece.Favor(_),
              PositionedLocation(Location.FavorBank(_), _),
              PositionedLocation(Location.PlayArea(taker), _), _), before, after)
              if taker == actor =>
            traded(journal, run, at, resource(piece), before, after, viewer)
          case OpStep(Move(piece @ Piece.Secrets(_),
              PositionedLocation(Location.SharedBank, _),
              PositionedLocation(Location.PlayArea(taker), _), _), before, after)
              if taker == actor =>
            traded(journal, run, at, resource(piece), before, after, viewer)
        }
        // Trading favor for secrets with no matching adviser gains nothing:
        // the line is posted at completion instead.
        val gainedEarlier = (run.first until at).exists(index =>
          journal.ops(index).exists(isTradeGain(actor)))
        if gained.nonEmpty || !completing || gainedEarlier then gained
        else journal.readyBefore(at).toVector.map(ready =>
          traded(journal, run, at, "no secrets", ready, ready, viewer))
      case ActionRef.TakeWealth => ops.collect {
        case OpStep(Move(piece, PositionedLocation(Location.Site(site), _),
            PositionedLocation(Location.PlayArea(taker), _), _), _, _)
            if taker == actor && resource(piece).nonEmpty =>
          action(Vector(Text(s"Took ${resource(piece)} from "),
            words.site(site)))
      }
      // Filled by Tasks 5 to 7; Task 7 deletes this case, making the match
      // exhaustive over `ProcedureRef`.
      case _ => Vector.empty

  /** The card an economy action's source decision chose. */
  private def source(journal: LogJournal, run: Run, at: Int,
      decisionId: String, before: ReadyGame, after: ReadyGame,
      viewer: Option[PlayerId]): Vector[LogSpan] =
    journal.answers(run, at).collectFirst {
      case Answered(`decisionId`, ChooseOneAnswer(DecisionOptionRef.Denizen(id)), _) =>
        id: CardId
      case Answered(`decisionId`, ChooseOneAnswer(DecisionOptionRef.Edifice(id)), _) =>
        id: CardId
    }.fold(Vector.empty[LogSpan])(card =>
      words.one(words.card(card, before, after, viewer)))

  private def traded(journal: LogJournal, run: Run, at: Int, bought: String,
      before: ReadyGame, after: ReadyGame, viewer: Option[PlayerId]): Posted =
    action(Vector(Text("Traded with ")) ++ source(journal, run, at,
      TradeProcedure.decisionId, before, after, viewer) ++
      Vector(Text(s" for $bought")))

  /** "Drew {cards} from the {source} and kept {cards}", at the keep/discard
    * answer, or at completion when only one card was drawn. Each card is
    * judged where the draw took it from and put it. */
  private def search(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.runOps(run, at).collectFirst {
      case (_, OpStep(Draw(_, cards, from, _), before, after)) =>
        (cards.map(card => card -> words.card(card, before, after, viewer)), from)
    }.toVector.flatMap { case (drawn, from) =>
      val kept: Option[Vector[CardId]] = journal.event(at) match
        case WalkerStepRecorded(_, ChoicePayload(SearchProcedure.cardDecisionId,
            PartitionAnswer(placements), _), _, _) =>
          Some(placements.collect {
            case DecisionPlacement(ref, SearchProcedure.keepKey) => ref
          }.flatMap(worldCard))
        case _: WalkerCompleted if drawn.size == 1 => Some(drawn.map(_._1))
        case _ => None
      kept.toVector.map(ids => action(Vector(Text("Drew ")) ++
        words.cards(drawn.map(_._2)) ++
        Vector(Text(s" from ${sourceName(from)} and kept ")) ++
        words.cards(ids.flatMap(id => drawn.find(_._1 == id).map(_._2)))))
    }

  /** Where the played card went, at the first batch after the placement
    * answer. */
  private def playedAdviser(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.event(at) match
      case WalkerStepRecorded(_, _: WalkerStepPayload.DeltaRecorded, _, _) =>
        val placed = (run.first until at).reverse.map(index =>
          index -> journal.event(index)).collectFirst {
          case (index, WalkerStepRecorded(_, ChoicePayload(id,
              ChooseOneAnswer(DecisionOptionRef.Button(key)), _), _, _))
              if id.startsWith(PlacePrefix) =>
            (index, id.stripPrefix(PlacePrefix), key)
        }.filter { case (index, _, _) =>
          !(index + 1 until at).exists(between => isDelta(journal.event(between)))
        }
        (for
          (_, subject, key) <- placed
          card <- subjectCard(subject)
          before <- journal.readyBefore(at)
          after <- journal.readyAfter(at)
        yield played(card, key, before, after, run.actor, viewer)).toVector
      case _ => Vector.empty

  private def played(card: CardId, key: String, before: ReadyGame,
      after: ReadyGame, actor: PlayerId, viewer: Option[PlayerId]): Posted =
    val named = words.one(words.card(card, before, after, viewer))
    key match
      case "discard" => action(Vector(Text("Discarded ")) ++ named)
      case "site" => action(Vector(Text("Played ")) ++ named ++
        LogJournal.pawnSite(before, actor).toVector.flatMap(site =>
          Vector(Text(" to "), words.site(site))))
      case _ => action(Vector(Text("Played ")) ++ named ++
        Vector(Text(" as an adviser")))

private[gamelog] object ActionLines:
  def action(spans: Vector[LogSpan]): Posted = Posted.line(LogKind.Action, spans)

  def plural(count: Int, one: String, many: String): String =
    if count == 1 then one else many

  /** "3 favor", "1 secret", "2 secrets". */
  def resource(piece: Piece): String = piece match
    case Piece.Favor(count) => s"$count favor"
    case Piece.Secrets(count) =>
      s"$count ${plural(count, "secret", "secrets")}"
    case _ => ""

  def isTradeGain(actor: PlayerId)(step: OpStep): Boolean = step match
    case OpStep(Move(Piece.Favor(_), PositionedLocation(Location.FavorBank(_), _),
        PositionedLocation(Location.PlayArea(taker), _), _), _, _) => taker == actor
    case OpStep(Move(Piece.Secrets(_), PositionedLocation(Location.SharedBank, _),
        PositionedLocation(Location.PlayArea(taker), _), _), _, _) => taker == actor
    case _ => false

  /** `CardPlayProcedure`'s placement decision id prefix; the card follows as
    * `{kind}.{id}`, the same spelling `WalkerDecisionProjector.subjectCards`
    * parses. */
  val PlacePrefix = "cardplay.place."

  def subjectCard(subject: String): Option[CardId] =
    subject.split("\\.", 2).toVector match
      case Vector("denizen", value) => Some(DenizenId(value))
      case Vector("vision", value) => Some(VisionId(value))
      case _ => None

  def worldCard(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Vision(id) => Some(id)
    case _ => None

  def isDelta(event: OathEvent): Boolean = event match
    case WalkerStepRecorded(_, _: WalkerStepPayload.DeltaRecorded, _, _) => true
    case _ => false

  def sourceName(location: Location): String = location match
    case Location.Deck(CardDeck.World) => "the World Deck"
    case Location.RegionalDiscard(region) => s"the ${region.key.capitalize} discard"
    case _ => "the deck"
