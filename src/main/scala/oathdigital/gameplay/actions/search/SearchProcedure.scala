package oathdigital.gameplay.actions.search

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.actions.{CardPlay, SearchRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.model._

/** Search on the walker (search source design).
  *
  * {{{
  * Sequence(
  *   Decide(search.source),             // a board choose-one: the world deck
  *                                      // and the actor's region's discard
  *   Branch { after the answer:
  *     Sequence(cost, draw, query, placement) })  // window = SearchActionEligibility
  * }}}
  *
  * **The source is a decision.** It is picked after the modifiers, so the
  * parked decision shows what each source costs under them. The start
  * carries no selection.
  *
  * **The windows sit inside the `Branch`.** A selected modifier's payment,
  * prepended at the eligibility window, is paid after the pick, and nothing
  * runs before the first decision.
  *
  * **The region is read from the pawn.** The pawn does not move during a
  * Search, so a resume reads the same region and needs no start fact.
  *
  * **What does not gate a start.** Supply, tolls and empty piles are not
  * build gates: the registry's playable-option gate previews both sources
  * and refuses a start neither can complete. Placement is the shared subtree.
  */
object SearchProcedure:
  val sourceDecisionId: String = "search.source"
  val cardDecisionId: String = "search.cards"
  val keepKey: String = "keep"
  val discardKey: String = "discard"
  private val heading = "Choose a Search source"

  /** Whether a Search could start now: some source survives the same
    * preview a start runs. */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    build(catalog, state, actor, Vector.empty)
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .exists(_.exists(_.outcome.isRight))

  def build(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(ready), actor)
    _ <- noSelection(args)
    _ <- Either.cond(ready.game.current.temporaryHands.valuesIterator
      .forall(_.isEmpty), (), OathViolation.SearchDrawMismatch(
      "a temporary card hand already exists"))
    origin <- actorRegion(ready, actor)
  yield tree(catalog, actor, origin)

  /** Resume never re-runs the start-only gates. */
  def rebuild(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for
    _ <- noSelection(args)
    origin <- actorRegion(ready, actor)
  yield tree(catalog, actor, origin)

  /** The pile a source answer names. */
  def sourceOf(answer: DecisionAnswer): Either[OathViolation, SearchSource] =
    (answer match
      case DecisionAnswer.ChooseOneAnswer(ref) => SearchSource.fromRef(ref)
      case _ => None).toRight(OathViolation.InvalidEventOrder(
      "a Search source is the world deck or a discard pile"))

  def actorRegion(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Region] =
    ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(ready.game.current.map.regionOf)
      .toRight(OathViolation.PawnSiteMissing(actor))

  private def noSelection(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder("search takes no start selection"))

  private def tree(catalog: ExecutableCatalog, actor: PlayerId,
      origin: Region): Operation =
    val pick = Decide(sourceDecisionId, actor, DecisionQuery.ChooseOne(
      Vector(SearchSource.WorldDeck, SearchSource.RegionalDiscard(origin))
        .map(SearchSource.option), heading = Some(heading)))
    val search = Branch((_, pending) => pending.answered.collectFirst {
      case Answered(`sourceDecisionId`, answer, _) => answer
    }.toVector.map(answer =>
      sourceOf(answer).fold(fail, body(catalog, actor, _))))
    Sequence(Vector(pick, search))

  /** The Search from `source`, once it is picked. */
  private def body(catalog: ExecutableCatalog, actor: PlayerId,
      source: SearchSource): Operation =
    val cost = BuildOps((ready, _) => for
      origin <- actorRegion(ready, actor)
      amount <- SearchRules.cost(ready, source, origin)
    yield Vector(SpendSupply(actor, amount)),
      window = Some(PowerWindow.SearchCost))

    val draw = BuildOps((ready, _) => for
      origin <- actorRegion(ready, actor)
      cards <- SearchRules.draw(ready, source, origin)
      _ <- Either.cond(cards.nonEmpty, (),
        OathViolation.SearchSourceUnavailable(source))
    yield {
      val location = source match {
        case SearchSource.WorldDeck => Location.Deck(CardDeck.World)
        case SearchSource.RegionalDiscard(region) =>
          Location.RegionalDiscard(region)
      }
      Vector[CoreOperation](Draw(actor, cards, location, Location.Hand(actor))) ++
        Option.when(source == SearchSource.WorldDeck &&
          cards.exists(_.isInstanceOf[VisionId]))(AdvanceVisionsDrawn)
    }, window = Some(PowerWindow.SearchBeforeDraw))

    val query = Branch((ready, pending) => {
      val cards = selectedCards(ready, pending, actor)
      if cards.size < 2 then Vector.empty
      else Vector(Decide(cardDecisionId, actor, DecisionQuery.Partition(
        Vector(DecisionSection(keepKey, "Keep", 1, Some(1)),
          DecisionSection(discardKey, "Discard", 0)),
        cards.map(option), heading = Some("Choose a card to keep"),
        confirmLabel = Some("Continue Search"))))
    })

    val placement = Branch((ready, pending) => {
      val selection = selected(ready, pending, actor)
      selection match {
        case Left(error) => Vector(fail(error))
        case Right((kept, discarded)) =>
          val removeOthers = BuildOps((state, _) =>
            discarded.foldLeft[Either[OathViolation,
                Vector[CoreOperation]]](Right(Vector.empty)) {
              case (acc, card) => for
                previous <- acc
                operations <- CardPlay.operations(catalog, state,
                  actor, card, SearchPlacement.Discard,
                  CardPlay.Origin.TemporaryHand)
              yield previous ++ operations
            })
          val cardTree = CardPlayProcedure.unchecked(catalog, ready, actor, kept,
            CardPlayProcedure.Origin.TemporaryHand)
          Vector(removeOthers, cardTree)
      }
    })

    Sequence(Vector(cost, draw, query, placement),
      Some(PowerWindow.SearchActionEligibility))

  private def option(card: WorldCardId): DecisionOption = card match
    case id: DenizenId => DecisionOption.Denizen(DecisionOptionRef.Denizen(id))
    case id: VisionId => DecisionOption.Vision(DecisionOptionRef.Vision(id))

  private def card(ref: DecisionOptionRef): Option[WorldCardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Vision(id) => Some(id)
    case _ => None

  private def answered(pending: PendingTree): Option[Vector[DecisionPlacement]] =
    pending.answered.collectFirst:
      case Answered(`cardDecisionId`,
          DecisionAnswer.PartitionAnswer(placements), _) => placements

  /** When no Partition answer is recorded (a single-card draw never asks
    * one), the kept card is read from the live temporary hand. Once that
    * single kept card has been played, the hand is empty; the tree is then
    * settled from the answers instead, mirroring `CardPlayProcedure`'s own
    * `settled` path, and the placement decision it recorded names the card.
    */
  private def selectedCards(ready: ReadyGame, pending: PendingTree,
      actor: PlayerId): Vector[WorldCardId] =
    answered(pending).fold {
      val hand = ready.game.current.temporaryHands.getOrElse(actor, Vector.empty)
      if hand.nonEmpty then hand
      else CardPlayProcedure.placedCard(pending).toVector
    }(_.flatMap(value => card(value.option)))

  private def selected(ready: ReadyGame, pending: PendingTree,
      actor: PlayerId): Either[OathViolation,
        (WorldCardId, Vector[WorldCardId])] =
    val cards = selectedCards(ready, pending, actor)
    if cards.size == 1 then Right(cards.head -> Vector.empty)
    else answered(pending).toRight(OathViolation.SearchChoiceMismatch(
      "Search card selection is missing")).flatMap { placements =>
      val kept = placements.filter(_.sectionKey == keepKey)
        .flatMap(value => card(value.option))
      val discarded = placements.filter(_.sectionKey == discardKey)
        .flatMap(value => card(value.option))
      kept match
        case Vector(value) => Right(value -> discarded)
        case _ => Left(OathViolation.SearchChoiceMismatch(
          "Search must keep exactly one card"))
    }

  private def fail(error: OathViolation): Operation =
    BuildOps((_, _) => Left(error))
