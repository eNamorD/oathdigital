package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{EffectiveSuit, PledgeToDiscordCard}
import oathdigital.gameplay.actions.{AdviserFace, AdviserPlayNote,
  CardPlayRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{AdviserArrivals, ArrivalFollowUp,
  Contribution, ExecutedBatch, ExecutedTrigger, Transform}
import oathdigital.model._

/** A rule that its holder may have only faceup advisers of one suit, and
  * buries every other adviser they have or would gain: Family Wagon's
  * Nomads and Vow of Union's Beasts.
  *
  * While its holder has the card faceup:
  *  - card play notes each adviser play after which the rule would bury the
  *    played card (`buryWarning`), through a transform at
  *    `SearchPlayAdviser`. `playRules` adds the card's other card-play
  *    rules, such as Family Wagon's lifted limit.
  *  - an adviser arriving in the holder's advisers, by any route, makes the
  *    holder bury every adviser that is not a faceup card of `suit` as the
  *    rules see it (`EffectiveSuit`), in held order, Pledge to Discord
  *    aside (below) (`onArrival`). The card's own arrival counts. A facedown card
  *    has no suit, so it is buried, and Locked does not stop a bury.
  *
  * A card it buries on arrival was never gained, so its WHEN PLAYED does
  * not run (`WhenPlayedPower`). The bury returns the card's favor to its
  * suit bank and its secrets to the active player, facedown. The line names
  * the holder only when the holder is not the active player, and covers the
  * generic "Buried" lines.
  *
  * With Pledge to Discord faceup among the holder's advisers, every
  * adviser but the card is Discord, and the order of the burials decides
  * which stay (catalog batch 8 rulings, slice 8). The arrival buries what
  * goes whatever the order. When faceup advisers that keep the rule by
  * their printed suit remain, the trigger asks the active player, after
  * the batch, which of them go before Pledge (`onExecuted`). It buries
  * those, then Pledge, and the rest revert. Its question posts no "Chose"
  * line, and each bury posts its "Buried" line.
  */
trait SuitOnlyAdvisers extends ExecutedTrigger:
  def catalog: ExecutableCatalog
  /** The card that sets the rule, held as an adviser. */
  def cardId: DenizenId
  /** The suit of the advisers its holder keeps. */
  def suit: Suit
  /** The note on a card play the rule will bury. */
  def buryWarning: String
  /** The card-play rules the card sets besides its bury note. */
  protected def playRules(rules: CardPlayRules): CardPlayRules = rules
  /** The card's name, as its question names it. */
  def ruleName: String
  /** The question that orders Pledge to Discord's bury. */
  def pledgeDecisionId: String

  override def narratedDecisions: Set[String] = Set(pledgeDecisionId)

  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  private val notes = NoteSet(id)
  notes.include(SuitOnlyAdvisers.notes)
  override def noteKeys: Vector[NoteKey] = notes.declared

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      ctx.operation match {
        case tree: CardPlayProcedure.PlacementTree
            if tree.card == cardId || holds(ctx.state, ctx.activePlayer) =>
          tree.adjust(children)(rules => playRules(rules).noteAdviserPlay(
            AdviserPlayNote(OptionReason.Written(buryWarning),
              (after, played) => after.contains(faceUp) && !kept(played))))
        case _ => children
      })))

  override def onArrival(arrived: AdviserArrivals): Option[ArrivalFollowUp] =
    for
      holder <- holderOf(arrived.after)
      if arrived.arrivals.exists(_.player == holder)
      buriedHere = automatic(arrived.after, holder)
      if buriedHere.nonEmpty
    yield ArrivalFollowUp(
      buriedHere.flatMap(bury(arrived.after, _, holder, arrived.actor)),
      Note(id, _ => Some(line(holder, arrived.actor, buriedHere.map(_.id))),
        covers = true))

  /** After a batch that left Pledge to Discord faceup beside advisers that
    * keep the rule by their printed suit: the active player sorts those
    * advisers, then the ones placed to bury go, then Pledge. It reads the
    * state alone, not the batch: only an arrival leaves that state, and
    * the follow-up always buries Pledge. An arrival inside another
    * trigger's follow-up, which holds no batch, would be asked after the
    * next batch instead. */
  def onExecuted(batch: ExecutedBatch): Option[Operation] = for
    holder <- holderOf(batch.now)
    waiting = ordered(batch.now, holder)
    if waiting.nonEmpty
  yield Sequence(Vector[Operation](
    Decide(pledgeDecisionId, batch.actor, DecisionQuery.Partition(
      Vector(
        DecisionSection(SuitOnlyAdvisers.buryKey,
          "Bury before Pledge to Discord", 0),
        DecisionSection(SuitOnlyAdvisers.keepKey, "Keep in advisers", 0)),
      waiting.map(adviser =>
        DecisionOption.Denizen(DecisionOptionRef.Denizen(adviser.id))),
      heading = Some(SuitOnlyAdvisers.pledgeHeading(ruleName, suit,
        holder == batch.actor)),
      confirmLabel = Some("Bury"))),
    BuildOps((ready, pending) => Right(
      buriedFirst(ordered(ready, holder), pending.answered)
        .flatMap(bury(ready, _, holder, batch.actor)) ++
      pledgeOf(ready, holder).toVector
        .flatMap(bury(ready, _, holder, batch.actor)))),
    // The bury has run when the note is built, so the advisers are read
    // from the state before it.
    Note(id, states => states.previous.map((before, _) => line(holder,
      batch.actor, buriedFirst(ordered(before, holder), states.answered)
        .map(adviser => adviser.id: CardId) :+ PledgeToDiscordCard.id)),
      covers = true)))

  /** The advisers the arrival buries at once: every adviser but this card
    * that does not keep the rule. With Pledge to Discord faceup beside
    * advisers that keep it by their printed suit ([[ordered]]), Pledge and
    * those advisers wait for the trigger's question; the order of their
    * burials decides which stay. */
  private def automatic(ready: ReadyGame, holder: PlayerId)
      : Vector[AdviserState] =
    val others = SuitOnlyAdvisers.advisers(ready, holder)
      .filterNot(_.id == cardId)
    if ordered(ready, holder).isEmpty then
      others.filterNot(keeps(ready, _))
    else others.filterNot(adviser => adviser.id == PledgeToDiscordCard.id ||
      printedKeeps(adviser))

  /** With Pledge to Discord faceup among `holder`'s advisers, the faceup
    * advisers other than this card that keep the rule by their printed
    * suit, in held order: Pledge makes them Discord until it goes. */
  private def ordered(ready: ReadyGame, holder: PlayerId)
      : Vector[DenizenState] =
    if !SuitOnlyAdvisers.hasFaceUp(ready, holder, PledgeToDiscordCard.id)
    then Vector.empty
    else SuitOnlyAdvisers.advisers(ready, holder).collect {
      case adviser: DenizenState
          if adviser.id != cardId && printedKeeps(adviser) => adviser
    }

  /** The advisers among `waiting` the answer places to bury before
    * Pledge. */
  private def buriedFirst(waiting: Vector[DenizenState],
      answered: Vector[Answered]): Vector[DenizenState] =
    val placed = answered.reverseIterator.collectFirst {
      case Answered(decision, DecisionAnswer.PartitionAnswer(placements), _)
          if decision == pledgeDecisionId => placements
    }.getOrElse(Vector.empty)
    val first = placed.collect {
      case DecisionPlacement(DecisionOptionRef.Denizen(card), key)
          if key == SuitOnlyAdvisers.buryKey => card
    }.toSet
    waiting.filter(adviser => first(adviser.id))

  private def pledgeOf(ready: ReadyGame, holder: PlayerId)
      : Option[AdviserState] = SuitOnlyAdvisers.advisers(ready, holder)
    .find(_.id == PledgeToDiscordCard.id)

  /** Whether `adviser` stays under the rule now: a faceup card whose
    * effective suit is `suit`. */
  private def keeps(ready: ReadyGame, adviser: AdviserState): Boolean =
    adviser match
      case DenizenState(card, Orientation.FaceUp, _) =>
        EffectiveSuit.of(catalog, ready, card).contains(suit)
      case _ => false

  /** Whether `adviser` keeps the rule by its printed suit. */
  private def printedKeeps(adviser: AdviserState): Boolean =
    kept(AdviserFace.of(adviser))

  /** Whether `face` stays under the rule by its printed suit: a faceup card
    * of `suit`. The card-play warning reads it: a holder of the rule never
    * keeps Pledge to Discord, so the played card's printed suit decides. */
  private def kept(face: AdviserFace): Boolean =
    face.orientation == Orientation.FaceUp &&
      catalog.suitOf(face.card).contains(suit)

  /** The bury of `adviser` from `holder`'s advisers, returning its favor to
    * its suit bank as the rules see it (an adviser buried while Pledge to
    * Discord stands returns to Discord) and its secrets to `actor`,
    * facedown. */
  private def bury(ready: ReadyGame, adviser: AdviserState, holder: PlayerId,
      actor: PlayerId): Vector[CoreOperation] =
    val from = PositionedLocation(Location.PlayArea(holder))
    adviser match
      case DenizenState(card, _, tokens) => Bury.standard(
        BuryableCard.Denizen(card), from, EffectiveSuit.of(catalog, ready,
          card), tokens.favor, tokens.secrets, actor)
      case VisionState(card, _) => Bury.standard(BuryableCard.Vision(card),
        from, None, favor = 0, secrets = 0, actor)

  private def line(holder: PlayerId, actor: PlayerId, cards: Vector[CardId])
      : PowerNote =
    val card = PowerSourceRef.Card(cardId)
    if holder == actor then SuitOnlyAdvisers.buried(card, NoteArg.Cards(cards))
    else SuitOnlyAdvisers.buriedByHolder(card, NoteArg.Player(holder),
      NoteArg.Cards(cards))

  private def faceUp: AdviserFace = AdviserFace(cardId, Orientation.FaceUp)

  /** Whether `player` holds the card among their advisers, in either
    * face. */
  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    SuitOnlyAdvisers.advisers(ready, player).exists(_.id == cardId)

  /** The player who has the card faceup, if any. */
  private def holderOf(ready: ReadyGame): Option[PlayerId] =
    ready.game.current.players.map(_.player)
      .find(SuitOnlyAdvisers.hasFaceUp(ready, _, cardId))

object SuitOnlyAdvisers:
  /** The keys this object owns, which no one power owns. A power that
    * writes one includes this set. */
  val notes: NoteSet = NoteSet.unowned("SuitOnlyAdvisers")
  /** "Buried {cards}.", on the holder's turn. */
  val buried: NoteKey = notes.key("buried", Vector(NotePart.Text("Buried "),
    NotePart.Arg(0), NotePart.Text(".")))
  /** "{Blue} buried {cards}.", when the holder is not the active player. */
  val buriedByHolder: NoteKey = notes.key("buried.holder", Vector(
    NotePart.Arg(0), NotePart.Text(" buried "), NotePart.Arg(1),
    NotePart.Text(".")))

  /** The section of the cards buried before Pledge to Discord. */
  val buryKey: String = "bury"
  /** The section of the cards kept. */
  val keepKey: String = "keep"

  /** The heading of the question that orders Pledge to Discord's bury. */
  def pledgeHeading(ruleName: String, suit: Suit, holderAnswers: Boolean)
      : String =
    if holderAnswers then s"$ruleName: Depending on when you bury Pledge " +
      s"to Discord, you can keep all or none of your $suit advisers. " +
      s"Choose which $suit advisers you keep."
    else s"$ruleName: Depending on when Pledge to Discord is buried, its " +
      s"holder keeps all or none of their $suit advisers. Choose which " +
      "they keep."

  /** Whether `player` has `card` faceup among their advisers. */
  def hasFaceUp(ready: ReadyGame, player: PlayerId, card: DenizenId)
      : Boolean = advisers(ready, player).exists {
    case DenizenState(held, Orientation.FaceUp, _) => held == card
    case _ => false
  }

  private[cardplay] def advisers(ready: ReadyGame, player: PlayerId)
      : Vector[AdviserState] =
    ready.game.current.players.find(_.player == player).toVector
      .flatMap(_.advisers)
