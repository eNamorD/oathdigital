package oathdigital.gameplay.actions.search

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.OathLifecycle
import oathdigital.gameplay.actions.{CardPlay, SearchRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.walker.{ModifierChoice, WalkerPowers,
  WalkerSimulation}
import oathdigital.model._

/** Search on the walker (search source design; modifier walker design).
  *
  * {{{
  * Sequence(
  *   Repeat(ModifierChoice),            // the modifiers, one per pass, then
  *                                      // "Start Search"
  *   Decide(search.source),             // a board choose-one: the world deck
  *                                      // and the actor's region's discard;
  *                                      // window = SearchSourceSelection
  *                                      // (Spinning Bee replaces it, and
  *                                      // the actor's own pile is searched)
  *   Branch { after the answer:
  *     Sequence(cost, draw, query, placement) })
  *                                    // window = SearchActionEligibility;
  *                                    // query's window = SearchKeep
  * }}}
  *
  * **The modifiers come first.** Each pick pays at once, so the source
  * decision shows what each source costs under them. The start carries no
  * selection.
  *
  * **The region is read from the pawn.** The pawn does not move during a
  * Search, so a resume reads the same region and needs no start fact.
  *
  * **What does not gate a start.** Supply, tolls and empty piles are not
  * build gates: "Start Search" previews both sources and says why neither
  * can be searched, and with no modifier to pick, the registry's
  * playable-option gate refuses a start neither source can complete.
  * Placement is the shared subtree.
  */
object SearchProcedure:
  val sourceDecisionId: String = "search.source"
  val cardDecisionId: String = "search.cards"
  val keepKey: String = "keep"
  val discardKey: String = "discard"
  private val heading = "Choose a Search source"
  private val startLabel = "Start Search"

  /** Whether a Search could start now under `powers`: some source survives
    * the preview of what follows the modifier decision. The Act panel asks
    * it of each selection `ModifierOffers.selections` tries. */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    gated(state, actor, Vector.empty)
      .map(afterModifiers(catalog, actor, _))
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .exists(_.exists(_.outcome.isRight))

  def build(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    gated(ready, actor, args).map(tree(catalog, actor, _))

  /** The start-only gates, and the region searched from. */
  private def gated(ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Region] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(ready), actor)
    _ <- noSelection(args)
    _ <- Either.cond(ready.game.current.temporaryHands.valuesIterator
      .forall(_.isEmpty), (), OathViolation.SearchDrawMismatch(
      "a temporary card hand already exists"))
    origin <- actorRegion(ready, actor)
  yield origin

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

  /** The pile the walk searches: the source answer's, or, when a power
    * removed the source decision (Spinning Bee), the actor's own
    * region's discard pile (catalog batch 6, slice 4). */
  def sourcePicked(pending: PendingTree, origin: Region)
      : Either[OathViolation, SearchSource] =
    pending.answered.collectFirst {
      case Answered(`sourceDecisionId`, answer, _) => sourceOf(answer)
    }.getOrElse(Right(SearchSource.RegionalDiscard(origin)))

  /** The cards the keep answer in `answered` keeps, in the order kept;
    * empty before that answer. */
  def kept(answered: Vector[Answered]): Vector[WorldCardId] =
    answered.collectFirst {
      case Answered(`cardDecisionId`,
          DecisionAnswer.PartitionAnswer(placements), _) => placements
    }.toVector.flatten.filter(_.sectionKey == keepKey)
      .flatMap(value => card(value.option))

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
    val rest = afterModifiers(catalog, actor, origin)
    Sequence(Vector(ModifierChoice.loop(actor,
      PowerWindow.SearchModifierSelection, rest, startLabel,
      Some(SearchOptions.priced)), rest))

  /** The source pick and the Search from it. */
  private def afterModifiers(catalog: ExecutableCatalog, actor: PlayerId,
      origin: Region): Operation =
    val pick = Decide(sourceDecisionId, actor, DecisionQuery.ChooseOne(
      Vector(SearchSource.WorldDeck, SearchSource.RegionalDiscard(origin))
        .map(SearchSource.option), heading = Some(heading)),
      window = Some(PowerWindow.SearchSourceSelection))
    // The walk reaches this after the source answer, or after the
    // decision a power put in its place.
    val search = Branch((_, pending) => Vector(
      sourcePicked(pending, origin).fold(fail, body(catalog, actor, _))))
    Sequence(Vector(pick, search))

  /** The Search from `source`, once it is picked. */
  private def body(catalog: ExecutableCatalog, actor: PlayerId,
      source: SearchSource): Operation =
    val cost = BuildOps((ready, _) =>
      Right(Vector(SpendSupply(actor, SearchRules.cost(ready, source)))),
      window = Some(PowerWindow.SearchCost))

    val draw = BuildOps((ready, _) => {
      val cards = SearchRules.draw(ready, source)
      val location = source match {
        case SearchSource.WorldDeck => Location.Deck(CardDeck.World)
        case SearchSource.RegionalDiscard(region) =>
          Location.RegionalDiscard(region)
      }
      // Not `Either.cond`: it builds its value eagerly, and a `Draw`
      // of no card fails its `require`.
      if cards.isEmpty then
        Left(OathViolation.SearchSourceUnavailable(source))
      else Right(Vector[CoreOperation](Draw(actor, cards, location,
        Location.Hand(actor))) ++
        Option.when(source == SearchSource.WorldDeck &&
          cards.exists(_.isInstanceOf[VisionId]))(AdvanceVisionsDrawn))
    }, window = Some(PowerWindow.SearchBeforeDraw))

    val query = Branch((ready, pending) => {
      val cards = selectedCards(ready, pending, actor)
      if cards.size < 2 then Vector.empty
      else Vector(Decide(cardDecisionId, actor, DecisionQuery.Partition(
        Vector(DecisionSection(keepKey, "Keep", 1, Some(1)),
          DecisionSection(discardKey, "Discard", 0)),
        cards.map(option), heading = Some("Choose a card to keep"),
        confirmLabel = Some("Continue Search")),
        window = Some(PowerWindow.SearchKeep)))
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
          kept match
            case first +: later =>
              val cardTree = CardPlayProcedure.unchecked(catalog, ready,
                actor, first, CardPlayProcedure.Origin.TemporaryHand,
                ModifierPicks.cards(pending.answered))
              // A later kept card (Land Warden) is planned when the walk
              // reaches it, from the state the plays before it left.
              val laterTrees = later.map(card =>
                Branch((current, reached) => Vector(
                  CardPlayProcedure.unchecked(catalog, current, actor,
                    card, CardPlayProcedure.Origin.TemporaryHand,
                    ModifierPicks.cards(reached.answered)))))
              Vector(removeOthers, cardTree) ++ laterTrees
            case _ => Vector.empty
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

  /** The cards kept, in the order kept, and the cards discarded. One card
    * drawn is kept without a question. No card held keeps nothing:
    * Spinning Bee drew no match and discarded every card. */
  private def selected(ready: ReadyGame, pending: PendingTree,
      actor: PlayerId): Either[OathViolation,
        (Vector[WorldCardId], Vector[WorldCardId])] =
    val cards = selectedCards(ready, pending, actor)
    if cards.size < 2 then Right(cards -> Vector.empty)
    else answered(pending).toRight(OathViolation.SearchChoiceMismatch(
      "Search card selection is missing")).flatMap { placements =>
      val keeps = kept(pending.answered)
      val discarded = placements.filter(_.sectionKey == discardKey)
        .flatMap(value => card(value.option))
      Either.cond(keeps.nonEmpty, keeps -> discarded,
        OathViolation.SearchChoiceMismatch("Search must keep a card"))
    }

  private def fail(error: OathViolation): Operation =
    BuildOps((_, _) => Left(error))
