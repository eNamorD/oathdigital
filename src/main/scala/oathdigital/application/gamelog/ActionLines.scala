package oathdigital.application.gamelog

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerParked, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.PartitionAnswer
import oathdigital.protocol.projection.{LogKind, LogSpan}
import LogSpan.Text

/** The action line each procedure posts, at the event its facts complete
  * (spec, "Action lines"). A rule reads the event being formatted and the
  * run's events before it; a name alone may come from later in the same
  * segment. The subject is omitted: it is the actor's own action.
  */
private[gamelog] final class ActionLines(words: LogWords, choices: ChoiceWords,
    catalog: ExecutableCatalog):
  import ActionLines._
  private val campaigns = new CampaignLines(words, choices, catalog)

  def searchLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    search(journal, run, at, viewer)

  def campaignLines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    campaigns.lines(journal, run, at, viewer)

  /** The "Used {card}" fallback, for a power that writes no `used` note of
    * its own. */
  def usePowerLines(power: PowerId)(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    usedPower(journal, run, at, power,
      journal.event(at).isInstanceOf[WalkerCompleted], viewer)

  /** "Drew {cards} from the {source} and kept {cards}", at the keep/discard
    * answer, or at completion when only one card was drawn or none was
    * kept ("kept none"). Each card is
    * judged where its draw took it from and put it. Painted Trumpet draws
    * with one `Draw` per pile, read together ([[drawnFrom]]). */
  private def search(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val draws = journal.runOps(run, at).collect {
      case (_, OpStep(draw: Draw, before, after)) => draw -> draw.cards.map(
        card => card -> words.card(card, before, after, viewer))
    }
    Option.when(draws.nonEmpty)(
      (draws.flatMap(_._2), drawnFrom(draws.map(_._1))))
      .toVector.flatMap { case (drawn, from) =>
        val kept: Option[Vector[CardId]] = journal.event(at) match
          case WalkerStepRecorded(_, ChoicePayload(
              SearchProcedure.cardDecisionId, PartitionAnswer(placements), _),
              _, _) =>
            Some(placements.collect {
              case DecisionPlacement(ref, SearchProcedure.keepKey) => ref
            }.flatMap(worldCard))
          // One card drawn: its placement answer completes the line, so the
          // line precedes the placement line it leads to.
          case WalkerStepRecorded(_, ChoicePayload(id, _, _), _, _)
              if drawn.size == 1 && id.startsWith(PlacePrefix) =>
            Some(drawn.map(_._1))
          // Nothing kept: every drawn card went to a discard pile, and
          // nothing was asked (Spinning Bee drew no match).
          case _: WalkerCompleted if !journal.answers(run, at).exists(
              answer => answer.decisionId == SearchProcedure.cardDecisionId ||
                answer.decisionId.startsWith(PlacePrefix)) &&
              journal.readyAfter(at).exists(after => drawn.forall(
                (card, _) => inDiscardPile(after, card))) =>
            Some(Vector.empty)
          case _: WalkerCompleted if drawn.size == 1 && !journal.answers(run,
              at).exists(_.decisionId.startsWith(PlacePrefix)) =>
            Some(drawn.map(_._1))
          case _ => None
        kept.toVector.map(ids => action(Vector(Text("Drew ")) ++
          words.cards(drawn.map(_._2)) ++
          Vector(Text(s" from $from and kept ")) ++
          (if ids.isEmpty then Vector(Text("none"))
          else words.cards(ids.flatMap(id =>
            drawn.find(_._1 == id).map(_._2))))))
      }

  /** Where a Search drew from: "the World Deck", "the bottom of the Cradle
    * discard", or "each discard pile" for Painted Trumpet's draw of every
    * pile's top card. */
  private def drawnFrom(draws: Vector[Draw]): String = draws match
    case Vector(draw) if draw.position == StackPosition.Bottom =>
      s"the bottom of ${sourceName(draw.source)}"
    case Vector(draw) => sourceName(draw.source)
    case _ => "each discard pile"

  /** Whether `card` lies in a regional discard pile in `ready`. */
  private def inDiscardPile(ready: ReadyGame, card: CardId): Boolean =
    Region.all.exists(region =>
      ready.game.current.commonCards.discard(region).contains(card))

  /** "Used {card}" at the first step recording one of the power's own
    * effects: a step that is not only the payment onto its card or to the
    * bank, and not only the use record. At completion if it recorded none. */
  private def usedPower(journal: LogJournal, run: Run, at: Int, power: PowerId,
      completing: Boolean, viewer: Option[PlayerId]): Vector[Posted] =
    val postedEarlier = (run.first until at).exists(effect(journal, run, _))
    if postedEarlier || !(effect(journal, run, at) || completing) ||
        journal.notedUse(run, power, at) then Vector.empty
    else Vector(action(Text("Used ") +: powerSource(journal, run, at, power,
      viewer)))

  private def effect(journal: LogJournal, run: Run, index: Int): Boolean =
    journal.event(index) match
      case WalkerStepRecorded(_, _: WalkerStepPayload.DeltaRecorded, ops, _) =>
        !ops.forall {
          case RecordPowerUse(_) => true
          case QueueProcedure(_, _) => true
          case Move(Piece.Favor(_) | Piece.Secrets(_),
              PositionedLocation(Location.PlayArea(payer), _, _),
              PositionedLocation(Location.OnCard(_) | Location.SharedBank, _, _),
              _, _) => payer == run.actor
          case _ => false
        }
      // A power's own question: its source is already known, so "Used"
      // comes before the "Chose" line its answer posts.
      case WalkerStepRecorded(_, _: ChoicePayload, _, _) => true
      case _ => false

  /** The source the player named when starting (kept on every park), else
    * the use record's source, else the card the power is printed on. */
  private def powerSource(journal: LogJournal, run: Run, at: Int,
      power: PowerId, viewer: Option[PlayerId]): Vector[LogSpan] =
    val through = (run.first to journal.segmentEnd(at)).toVector
    val ready = journal.readyBefore(run.first)
    val started = through.map(journal.event).collectFirst {
      case parked: WalkerParked => parked.startArgs
    }.flatMap(_.headOption).flatMap(ref => ready.flatMap(state =>
      named(ref, state, viewer)))
    val used = through.flatMap(journal.ops).collectFirst {
      case OpStep(RecordPowerUse(PowerUseRef(_, source, _)), _, _) => source
    }.flatMap(source => ready.map(state => words.source(source, Vector(state), viewer)))
    started.orElse(used).orElse(ready.map(state =>
      words.power(state, run.actor, power, viewer)))
      .getOrElse(Vector(Text(power.value)))

  private def named(ref: DecisionOptionRef, ready: ReadyGame,
      viewer: Option[PlayerId]): Option[Vector[LogSpan]] = ref match
    case DecisionOptionRef.Denizen(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Relic(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Vision(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Edifice(id) =>
      Some(words.one(words.card(id, ready, ready, viewer)))
    case DecisionOptionRef.Banner(banner) => Some(Vector(words.banner(banner)))
    case _ => None

private[gamelog] object ActionLines:
  def action(spans: Vector[LogSpan]): Posted = Posted(LogKind.Action, spans)

  def plural(count: Int, one: String, many: String): String =
    if count == 1 then one else many

  /** "3 favor", "1 secret", "2 secrets". */
  def resource(piece: Piece): String = piece match
    case Piece.Favor(count) => s"$count favor"
    case Piece.Secrets(count) =>
      s"$count ${plural(count, "secret", "secrets")}"
    case _ => ""

  def isTradeGain(actor: PlayerId)(step: OpStep): Boolean = step match
    case OpStep(Move(Piece.Favor(_), PositionedLocation(Location.FavorBank(_), _, _),
        PositionedLocation(Location.PlayArea(taker), _, _),
        _, _), _, _) => taker == actor
    case OpStep(Move(Piece.Secrets(_), PositionedLocation(Location.SharedBank, _, _),
        PositionedLocation(Location.PlayArea(taker), _, _),
        _, _), _, _) => taker == actor
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
