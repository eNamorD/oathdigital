package oathdigital.application.gamelog

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.walker.{ChoicePayload, WalkerCompleted,
  WalkerParked, WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseOneAnswer, PartitionAnswer}
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
  private val setup = new SetupLines(words)
  private val campaigns = new CampaignLines(words, choices, catalog)
  private val negotiations = new NegotiationLines(words)

  def lines(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    val actor = run.actor
    val ops = journal.ops(at)
    val completing = journal.event(at).isInstanceOf[WalkerCompleted]
    run.procedure match
      case ActionRef.Travel => ops.collect {
        case OpStep(Move(Piece.Pawn(mover), _,
            PositionedLocation(Location.Site(to), _, _), _), _, _)
            if mover == actor =>
          action(Vector(Text("Travelled to "), words.site(to)))
      }
      case TriggeredProcedureRef.Oathkeeper => ops.collect {
        case OpStep(SetOathkeeper(holder), _, _) =>
          Posted(LogKind.Trigger, Vector(Text("Oathkeeper passed to "),
            holder.fold[LogSpan](Text("the bank"))(words.player)))
      }
      case PhaseTransitionRef.FinishRest => ops.collect {
        case OpStep(GainSupply(player, _), before, after) =>
          Posted(LogKind.Delta, Vector(Text(
            s"Increased supply from ${LogJournal.supply(before, player)} " +
              s"to ${LogJournal.supply(after, player)}")))
      }
      // The phase changes show in the lines around them.
      case PhaseTransitionRef.EndWake | PhaseTransitionRef.BeginRest =>
        Vector.empty
      // Each forced power writes its own lines. The follow-up offer and the
      // phase menus write none: a pick starts its own walk, which writes
      // them, and a Skip is a line of its own (`FollowUpSkipped`).
      case TriggeredProcedureRef.ForcedWake |
          TriggeredProcedureRef.FollowUpOffer |
          TriggeredProcedureRef.WakeMenu |
          TriggeredProcedureRef.RestMenu |
          TriggeredProcedureRef.ActMenu => Vector.empty
      case TriggeredProcedureRef.Setup => setup.lines(journal, at, viewer)
      case ActionRef.Search =>
        search(journal, run, at, viewer) ++
          playedAdviser(journal, run, at, viewer)
      case ActionRef.PlayFacedownAdviser =>
        playedAdviser(journal, run, at, viewer)
      // A power's covering line restates a gain of its own, such as a
      // vow's warbands after the Muster's.
      case ActionRef.Muster if journal.covered(at) => Vector.empty
      case ActionRef.Muster => ops.collect {
        case OpStep(Move(Piece.Warbands(_, count),
            PositionedLocation(Location.WarbandBank(_), _, _),
            PositionedLocation(Location.PlayArea(taker), _, _), _), before, after)
            if taker == actor =>
          action(Vector(Text(
            s"Mustered $count ${plural(count, "warband", "warbands")} with ")) ++
            source(journal, run, at, MusterProcedure.decisionId, before, after,
              viewer))
      }
      case ActionRef.Trade =>
        val gained = ops.collect {
          case OpStep(Move(piece @ Piece.Favor(_),
              PositionedLocation(Location.FavorBank(_), _, _),
              PositionedLocation(Location.PlayArea(taker), _, _), _), before, after)
              if taker == actor =>
            traded(journal, run, at, resource(piece), before, after, viewer)
          case OpStep(Move(piece @ Piece.Secrets(_),
              PositionedLocation(Location.SharedBank, _, _),
              PositionedLocation(Location.PlayArea(taker), _, _), _), before, after)
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
        case OpStep(Move(piece, PositionedLocation(Location.Site(site), _, _),
            PositionedLocation(Location.PlayArea(taker), _, _), _), _, _)
            if taker == actor && resource(piece).nonEmpty =>
          action(Vector(Text(s"Took ${resource(piece)} from "),
            words.site(site)))
      }
      case ActionRef.Recover =>
        val recovered = ops.collect {
          case OpStep(Move(Piece.Card(relic: RelicId),
              PositionedLocation(Location.Site(site), _, _),
              PositionedLocation(Location.PlayArea(taker), _, _), _), before, after)
              if taker == actor =>
            action(Vector(Text("Recovered ")) ++
              words.one(words.card(relic, before, after, viewer)) ++
              Vector(Text(" at "), words.site(site)))
        }
        // A Recover that succeeds with no relic to take posts nothing.
        val failed =
          if completing && stopped(journal, run, at) then
            journal.readyBefore(run.first).flatMap(LogJournal.pawnSite(_, actor))
              .toVector.map(site =>
                action(Vector(Text("Failed to recover at "), words.site(site))))
          else Vector.empty
        recovered ++ failed
      case ActionRef.Forge =>
        val paid = ops.collect {
          case OpStep(PayCost(_, Location.OnCard(card), cost, _, _, _),
              before, after) => (card, cost, before, after)
        }
        val favor = paid.collect { case (card, cost, before, after)
          if cost.favor > 0 => words.card(card, before, after, viewer) }
        val secret = paid.collect { case (card, cost, before, after)
          if cost.secret > 0 => words.card(card, before, after, viewer) }
        val halves = Vector(
          Option.when(favor.nonEmpty)(Text("favor on ") +: words.cards(favor)),
          Option.when(secret.nonEmpty)(Text("secrets on ") +: words.cards(secret))
        ).flatten
        val payment = if halves.isEmpty then Vector.empty
          else Vector(action(Text("Placed ") +: LogWords.join(halves)))
        val forged = ops.collect {
          case OpStep(Play(relic: RelicId,
              PositionedLocation(Location.Deck(CardDeck.Relic), _, _), _, _, _),
              before, after) =>
            action(Vector(Text("Forged ")) ++
              words.one(words.card(relic, before, after, viewer)))
        }
        payment ++ forged
      case ActionRef.Campaign => campaigns.lines(journal, run, at, viewer)
      case ActionRef.Challenge => ops.collect {
        case OpStep(Move(Piece.Banner(banner), PositionedLocation(from, _, _),
            PositionedLocation(Location.PlayArea(taker), _, _), _), _, _)
            if taker == actor =>
          val holder = from match
            case Location.PlayArea(player) => words.player(player)
            case _ => Text("the bank")
          action(Vector(Text("Took "), words.banner(banner), Text(" from "),
            holder) ++ paidOnto(journal, run, at, banner).toVector.map(paid =>
              Text(s" with $paid")))
      }
      case ActionRef.PlaceBannerResource => ops.collect {
        case OpStep(Move(piece, PositionedLocation(Location.PlayArea(giver), _, _),
            PositionedLocation(Location.OnBanner(banner), _, _), _), _, _)
            if giver == actor && resource(piece).nonEmpty =>
          action(Vector(Text(s"Placed ${resource(piece)} on "),
            words.banner(banner)))
      }
      // The minor actions post their legacy lines, now as action lines.
      case ActionRef.PeekSiteRelics =>
        val peeked = ops.collect {
          case OpStep(Peek(_, relic, Location.Site(site)), before, after) =>
            site -> words.card(relic, before, after, viewer)
        }
        peeked.headOption.toVector.map((site, _) => action(
          Vector(Text("Peeked at ")) ++ words.cards(peeked.map(_._2)) ++
            Vector(Text(" at "), words.site(site))))
      case ActionRef.RevealOwnedRelic => ops.collect {
        case OpStep(Reveal(relic, Location.PlayArea(owner)), before, after)
            if owner == actor =>
          action(Text("Revealed ") +:
            words.one(words.card(relic, before, after, viewer)))
      }
      case ActionRef.MoveWarbands => ops.collect {
        case OpStep(Move(Piece.Warbands(_, count),
            PositionedLocation(Location.PlayArea(_), _, _),
            PositionedLocation(Location.Site(site), _, _), _), _, _) =>
          action(Vector(Text(s"Moved $count " +
            s"${plural(count, "warband", "warbands")} to "), words.site(site)))
        case OpStep(Move(Piece.Warbands(_, count),
            PositionedLocation(Location.Site(site), _, _),
            PositionedLocation(Location.PlayArea(_), _, _), _), _, _) =>
          action(Vector(Text(s"Moved $count " +
            s"${plural(count, "warband", "warbands")} from "),
            words.site(site)))
      }
      case ActionRef.Negotiation => negotiations.lines(journal, run, at, viewer)
      // A power that plays a card as if searched (Oracle) posts where the
      // card went, as a Search does. Only a run that answered a placement
      // decision has such a line.
      case ActionRef.UsePower(power) =>
        usedPower(journal, run, at, power, completing, viewer) ++
          playedAdviser(journal, run, at, viewer)

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

  /** Where the played card went, at the first batch after the placement
    * answer. */
  private def playedAdviser(journal: LogJournal, run: Run, at: Int,
      viewer: Option[PlayerId]): Vector[Posted] =
    journal.event(at) match
      case WalkerStepRecorded(_, _: WalkerStepPayload.DeltaRecorded, _, _) =>
        val placed = (run.first until at).reverse.map(index =>
          index -> journal.event(index)).collectFirst {
          case (index, WalkerStepRecorded(_, ChoicePayload(id,
              ChooseOneAnswer(ref), _), _, _))
              if id.startsWith(PlacePrefix) &&
                CardPlayProcedure.playedAs(ref).nonEmpty =>
            (index, id.stripPrefix(PlacePrefix), ref)
        }.filter { case (index, _, _) =>
          !(index + 1 until at).exists(between => isDelta(journal.event(between)))
        }
        (for
          (_, subject, ref) <- placed
          card <- subjectCard(subject)
          way <- CardPlayProcedure.playedAs(ref)
          before <- journal.readyBefore(at)
          after <- journal.readyAfter(at)
        yield played(card, way, before, after, run.actor, viewer)).toVector
      case _ => Vector.empty

  private def played(card: CardId, way: PlayedAs, before: ReadyGame,
      after: ReadyGame, actor: PlayerId, viewer: Option[PlayerId]): Posted =
    val named = words.one(words.card(card, before, after, viewer))
    way match
      // Named with its pile, as every other discard line is.
      case PlayedAs.Discarded => action(Vector(Text("Discarded ")) ++ named ++
        after.game.cardIndex.toOption.flatMap(_.get(card))
          .map(_.location.container).collect {
            case CardContainer.RegionalDiscard(region) =>
              Text(s" to the ${region.key.capitalize} discard")
          }.toVector)
      case PlayedAs.AtSite => action(Vector(Text("Played ")) ++ named ++
        LogJournal.pawnSite(before, actor).toVector.flatMap(site =>
          Vector(Text(" to "), words.site(site))))
      // A Vision played faceup takes the revealed Vision slot, not an
      // adviser's.
      case PlayedAs.Faceup if card.isInstanceOf[VisionId] =>
        action(Vector(Text("Revealed ")) ++ named)
      case _ => action(Vector(Text("Played ")) ++ named ++
        Vector(Text(" as an adviser")))

  /** The last continue-or-stop answer was "stop". */
  private def stopped(journal: LogJournal, run: Run, at: Int): Boolean =
    journal.answers(run, at).reverse.collectFirst {
      case Answered(RecoverProcedure.choiceDecisionId,
          ChooseOneAnswer(DecisionOptionRef.Button(key)), _) => key == "stop"
    }.contains(true)

  /** What the challenger put on the banner, earlier in the run. */
  private def paidOnto(journal: LogJournal, run: Run, at: Int, banner: Banner)
      : Option[String] =
    journal.runOps(run, at).collectFirst {
      case (_, OpStep(Move(piece, PositionedLocation(Location.PlayArea(giver), _, _),
          PositionedLocation(Location.OnBanner(onto), _, _), _), _, _))
          if giver == run.actor && onto == banner && resource(piece).nonEmpty =>
        resource(piece)
    }

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
              _) => payer == run.actor
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
        PositionedLocation(Location.PlayArea(taker), _, _), _), _, _) => taker == actor
    case OpStep(Move(Piece.Secrets(_), PositionedLocation(Location.SharedBank, _, _),
        PositionedLocation(Location.PlayArea(taker), _, _), _), _, _) => taker == actor
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
