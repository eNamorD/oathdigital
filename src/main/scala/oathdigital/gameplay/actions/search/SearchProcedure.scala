package oathdigital.gameplay.actions.search

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathLifecycle, OathState, OathViolation, ReadyGame}
import oathdigital.gameplay.actions.{CardPlay, SearchRules}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver.PowerWindow
import oathdigital.model._

/** Search's draw and card-selection tree; placement is the shared subtree. */
object SearchProcedure {
  val cardDecisionId: String = "search.cards"
  val keepKey: String = "keep"
  val discardKey: String = "discard"

  def build(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] = for {
    _ <- OathLifecycle.validateAct(OathState.Ready(ready), actor)
    source <- sourceOf(args)
    _ <- SearchRules.validateSupportedState(catalog, ready)
    _ <- Either.cond(ready.game.current.temporaryHands.valuesIterator
      .forall(_.isEmpty), (), OathViolation.SearchDrawMismatch(
      "a temporary card hand already exists"))
    player <- ready.game.current.players.find(_.player == actor)
      .toRight(OathViolation.InvalidEventOrder("Search actor is not in the game"))
    origin <- player.pawnSite.flatMap(ready.game.current.map.regionOf)
      .toRight(OathViolation.PawnSiteMissing(actor))
    cost <- SearchRules.cost(ready, source, origin)
    cards <- SearchRules.draw(ready, source, origin)
    _ <- Either.cond(cards.nonEmpty, (),
      OathViolation.SearchSourceUnavailable(source))
    _ <- Either.cond(player.board.supply.supply >= cost, (),
      OathViolation.InsufficientSupply(cost, player.board.supply.supply))
  } yield tree(catalog, actor, source)

  /** Resume never re-runs the start-only affordability and empty-hand gates. */
  def rebuild(catalog: ExecutableCatalog, ready: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    sourceOf(args).map(tree(catalog, actor, _))

  def sourceOf(args: Vector[DecisionOptionRef])
      : Either[OathViolation, SearchSource] = args match {
    case Vector(DecisionOptionRef.Button("search:world")) =>
      Right(SearchSource.WorldDeck)
    case Vector(DecisionOptionRef.Button(key))
        if key.startsWith("search:regional-discard:") =>
      Region.all.find(_.key == key.stripPrefix("search:regional-discard:"))
        .map(region => SearchSource.RegionalDiscard(region))
        .toRight(OathViolation.InvalidEventOrder("unknown Search region"))
    case _ => Left(OathViolation.InvalidEventOrder(
      "Search requires exactly one source selection"))
  }

  private def tree(catalog: ExecutableCatalog, actor: PlayerId,
      source: SearchSource): Operation = {
    val cost = BuildOps((ready, _) => for {
      origin <- actorRegion(ready, actor)
      amount <- SearchRules.cost(ready, source, origin)
    } yield Vector(SpendSupply(actor, amount)),
      window = Some(PowerWindow.SearchCost))

    val draw = BuildOps((ready, _) => for {
      origin <- actorRegion(ready, actor)
      cards <- SearchRules.draw(ready, source, origin)
      _ <- Either.cond(cards.nonEmpty, (),
        OathViolation.SearchSourceUnavailable(source))
    } yield {
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
      if (cards.size < 2) Vector.empty
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
              case (acc, card) => for {
                previous <- acc
                operations <- CardPlay.plannedOperations(catalog, state,
                  actor, card, SearchPlacement.Discard,
                  CardPlay.Origin.TemporaryHand)
              } yield previous ++ operations
            })
          val cardTree = CardPlayProcedure.build(catalog, ready, actor, kept,
            CardPlayProcedure.Origin.TemporaryHand)
          Vector(removeOthers) ++ cardTree.fold(error => Vector(fail(error)),
            value => Vector(value))
      }
    })

    Sequence(Vector(cost, draw, query, placement),
      Some(PowerWindow.SearchActionEligibility))
  }

  private def actorRegion(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, Region] =
    ready.game.current.players.find(_.player == actor).flatMap(_.pawnSite)
      .flatMap(ready.game.current.map.regionOf)
      .toRight(OathViolation.PawnSiteMissing(actor))

  private def option(card: WorldCardId): DecisionOption = card match {
    case id: DenizenId => DecisionOption.Denizen(DecisionOptionRef.Denizen(id))
    case id: VisionId => DecisionOption.Vision(DecisionOptionRef.Vision(id))
  }

  private def card(ref: DecisionOptionRef): Option[WorldCardId] = ref match {
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Vision(id) => Some(id)
    case _ => None
  }

  private def answered(pending: PendingTree): Option[Vector[DecisionPlacement]] =
    pending.answered.collectFirst {
      case Answered(`cardDecisionId`,
          DecisionAnswer.PartitionAnswer(placements), _) => placements
    }

  private def selectedCards(ready: ReadyGame, pending: PendingTree,
      actor: PlayerId): Vector[WorldCardId] =
    answered(pending).fold(ready.game.current.temporaryHands
      .getOrElse(actor, Vector.empty))(_.flatMap(value => card(value.option)))

  private def selected(ready: ReadyGame, pending: PendingTree,
      actor: PlayerId): Either[OathViolation,
        (WorldCardId, Vector[WorldCardId])] = {
    val cards = selectedCards(ready, pending, actor)
    if (cards.size == 1) Right(cards.head -> Vector.empty)
    else answered(pending).toRight(OathViolation.SearchChoiceMismatch(
      "Search card selection is missing")).flatMap { placements =>
      val kept = placements.filter(_.sectionKey == keepKey)
        .flatMap(value => card(value.option))
      val discarded = placements.filter(_.sectionKey == discardKey)
        .flatMap(value => card(value.option))
      kept match {
        case Vector(value) => Right(value -> discarded)
        case _ => Left(OathViolation.SearchChoiceMismatch(
          "Search must keep exactly one card"))
      }
    }
  }

  private def fail(error: OathViolation): Operation =
    BuildOps((_, _) => Left(error))
}
