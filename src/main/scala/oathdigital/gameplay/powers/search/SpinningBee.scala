package oathdigital.gameplay.powers.search

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.{CardPlay, SearchRules}
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  OfferReason, OperationRewrite, OptionRestriction, PowerCtx, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object SpinningBeeCard extends Denizen(DenizenId("232"), "Spinning Bee", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.spinning-bee"),
    persistent = false, cost = Cost.free,
    text = "Declare a suit. Draw from your discard pile until you draw " +
      "a matching card. If you draw none, discard them all. You " +
      "**cannot** use other Search modifiers.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Spinning Bee (card 232), a free selected Search modifier (catalog batch
  * 6, slice 4):
  *
  *  - Once picked, every other Search modifier is unavailable, with its
  *    note. It is unavailable itself once another is picked, and while the
  *    actor's own discard pile is empty.
  *  - The source decision gives way to "Declare a suit", a choose-one of
  *    the six favor banks. With no source answer, the Search draws from the
  *    actor's own region's discard pile at the usual 2 Supply
  *    (`SearchProcedure.sourcePicked`).
  *  - The draw takes the pile from the top through the first card of the
  *    declared suit. A Vision has no suit. The keep choice then runs over
  *    the drawn cards as usual.
  *  - With no match it draws the whole pile, and every drawn card is
  *    discarded in draw order, where a Search discards the cards it does
  *    not keep. Nothing is kept.
  *
  * Its line, "Drawing from the Cradle discard until Hearth card is drawn",
  * comes before the draw.
  */
final case class SpinningBee private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = SpinningBeeCard.id
  def id: PowerId = SpinningBee.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Search)
  override def noteKeys: Vector[NoteKey] = SpinningBee.noteKeys
  /** The declared suit is told by Spinning Bee's own line. */
  override def narratedDecisions: Set[String] =
    Set(SpinningBee.suitDecisionId)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchModifierSelection -> Vector(OptionRestriction(
      (ctx, ref) => Option.when(excludes(ctx, ref))(
        OathViolation.InvalidEventOrder(
          "Spinning Bee keeps out every other Search modifier")),
      (ctx, ref) => Option.when(excludes(ctx, ref))(
        SpinningBee.alone(PowerSourceRef.Card(cardId))))),
    PowerWindow.SearchSourceSelection -> Vector(Transform((ctx, operations) =>
      OperationRewrite.nodes(ctx, operations)((_, _) => {
        case decide: Decide
            if decide.decisionId == SearchProcedure.sourceDecisionId =>
          Vector(SpinningBee.declaration(ctx.activePlayer))
      }))),
    PowerWindow.SearchBeforeDraw -> Vector(Transform((ctx, operations) =>
      operations.flatMap {
        case node: BuildOps
            if node.window.contains(PowerWindow.SearchBeforeDraw) =>
          Vector(line(ctx.activePlayer),
            node.copy(build = (ready, pending) => node.build(ready, pending)
              .flatMap(throughMatch(ready, pending))),
            discardedUnlessMatched(ctx.activePlayer))
        case other => Vector(other)
      })))

  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    if ModifierPicks.cards(ctx.answered).nonEmpty then
      Some(OfferReason.Unavailable(SpinningBee.notWithOthers))
    else SearchProcedure.actorRegion(ctx.state, ctx.activePlayer).toOption
      .map(SearchSource.RegionalDiscard(_))
      .filter(SearchRules.pile(ctx.state, _).isEmpty)
      .map(pile => OfferReason.Unavailable(
        s"The ${SearchSource.name(pile)} is empty"))

  /** Once picked, it keeps out every option but its own and "Start". */
  private def excludes(ctx: PowerCtx, ref: DecisionOptionRef): Boolean =
    ModifierPicks.isPicked(ctx.answered, ctx.window, cardId) &&
      ref != ModifierPicks.start &&
      !ModifierPicks.cardRef(cardId).contains(ref)

  private def matches(suit: Suit)(card: WorldCardId): Boolean =
    catalog.suitOf(card).contains(suit)

  /** The draw taken again from the top, through the first card of the
    * declared suit, or the whole pile when none matches. */
  private def throughMatch(ready: ReadyGame, pending: PendingTree)(
      operations: Vector[CoreOperation])
      : Either[OathViolation, Vector[CoreOperation]] =
    operations.collectFirst { case draw: Draw => draw } match
      case None => Right(operations)
      case Some(draw) =>
        for
          source <- DrawExtension.sourceOf(draw.source)
          suit <- SpinningBee.declared(pending.answered).toRight(
            OathViolation.InvalidEventOrder(
              "Spinning Bee draws once a suit is declared"))
        yield
          val pile = SearchRules.pile(ready, source)
          val index = pile.indexWhere(matches(suit))
          DrawExtension.redrawn(draw, source,
            if index < 0 then pile else pile.take(index + 1))

  /** Every drawn card discarded, in draw order, when none is of the
    * declared suit. */
  private def discardedUnlessMatched(actor: PlayerId): Operation =
    BuildOps((ready, pending) => {
      val hand = ready.game.current.temporaryHands
        .getOrElse(actor, Vector.empty)
      SpinningBee.declared(pending.answered) match
        case Some(suit) if hand.nonEmpty && !hand.exists(matches(suit)) =>
          hand.foldLeft[Either[OathViolation, Vector[CoreOperation]]](
              Right(Vector.empty)) { (done, card) =>
            for
              previous <- done
              operations <- CardPlay.operations(catalog, ready, actor, card,
                SearchPlacement.Discard, CardPlay.Origin.TemporaryHand)
            yield previous ++ operations
          }
        case _ => Right(Vector.empty)
    })

  /** "Drawing from the Cradle discard until Hearth card is drawn". */
  private def line(actor: PlayerId): Note = Note(id, states => for
    suit <- SpinningBee.declared(states.answered)
    region <- SearchProcedure.actorRegion(states.now, actor).toOption
  yield SpinningBee.drawing(PowerSourceRef.Card(cardId),
    NoteArg.RegionName(region), NoteArg.SuitName(suit)))

object SpinningBee:
  val id: PowerId = SpinningBeeCard.power.id
  /** The decision that takes the source decision's place. */
  val suitDecisionId: String = "search.suit"
  val heading: String = "Declare a suit"
  /** Why Spinning Bee is unavailable once another modifier is picked. */
  val notWithOthers: String = "Cannot be used with other Search modifiers"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "You cannot use other Search modifiers.": why every other modifier
    * is unavailable once Spinning Bee is picked. */
  val alone: NoteKey = notes.key("alone", Vector(
    NotePart.Text("You cannot use other Search modifiers.")))
  /** "Drawing from the {region} discard until {suit} card is drawn". */
  val drawing: NoteKey = notes.key("drawing", Vector(
    NotePart.Text("Drawing from the "), NotePart.Arg(0),
    NotePart.Text(" discard until "), NotePart.Arg(1),
    NotePart.Text(" card is drawn")))

  /** "Declare a suit", asked of `actor`: the six favor banks, in suit
    * order. */
  def declaration(actor: PlayerId): Decide = Decide(suitDecisionId, actor,
    DecisionQuery.ChooseOne(Suit.all.map(suit =>
      DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      heading = Some(heading)))

  /** The suit declared in `answered`. */
  def declared(answered: Vector[Answered]): Option[Suit] =
    answered.collectFirst {
      case Answered(`suitDecisionId`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.FavorBank(suit)), _) => suit
    }

  def forCatalog(catalog: ExecutableCatalog): SpinningBee =
    new SpinningBee(catalog)
