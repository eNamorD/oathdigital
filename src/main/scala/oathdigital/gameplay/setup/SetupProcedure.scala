package oathdigital.gameplay.setup

import oathdigital.catalog.{ExecutableCatalog, SiteOnly}
import oathdigital.model._
import oathdigital.model.OathViolation._

/**
 * Declared Setup procedure tree for the walker (2026-09-21 Chronicle
 * design, slice 2, "Setup on the walker"). Triggered once, right after
 * `GameStarted` evolves a fresh `Ready` game in `Phase.Setup`; no client
 * command starts it (`TriggeredProcedureRef.Setup`).
 *
 * {{{
 * Sequence(                                              // no window
 *   Sequence(                                            // per participant
 *     Decide(pawn-placement, participant),
 *     BuildOps(place the pawn)                           // window = SetupPawnPlaced
 *     Decide(adviser-choice, participant),
 *     BuildOps(keep one adviser, discard the other two),
 *     Branch(                                            // CR p. 13, Reveal Cards
 *       Decide(reveal, participant),                     // only when one can be
 *       BuildOps(reveal the chosen cards)))
 *   ...
 *   Sequence(Vector.empty)                                // window = SetupEnd
 *   BeginTurn(firstPlayer, Wake))
 * }}}
 *
 * Turn order and every hand are already fixed the moment `GameStarted`
 * evolves -- read off `ready.setup.firstPlayer`/`ready.game.current
 * .players`/`.temporaryHands`, never off a start selection -- so `build`
 * doubles as `rebuild`, like Oathkeeper and End Wake: nothing here is a
 * start-only gate, and a resume rebuilds the identical tree against live
 * state.
 *
 * The `SetupEnd` window is declared with no static children (unlike End
 * Wake's undeclared window, on purpose): slice 3's E02/E06/E22 SETUP
 * powers hook it with a `Transform` that adds their own operations: nothing
 * runs there yet.
 *
 * Reveal Cards lets a player turn any of their facedown advisers and relics
 * faceup, except site-only advisers. A `Reveal` does not trigger When Played
 * powers. The step asks nothing when the player has nothing to reveal.
 */
object SetupProcedure:
  val adviserKeepKey: String = "keep"
  val adviserDiscardKey: String = "discard"

  def pawnDecisionId(player: PlayerId): String =
    s"setup.pawn-placement.${player.value}"
  def adviserDecisionId(player: PlayerId): String =
    s"setup.adviser-choice.${player.value}"
  def revealDecisionId(player: PlayerId): String =
    s"setup.reveal.${player.value}"

  def build(catalog: ExecutableCatalog, ready: ReadyGame, activePlayer: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    Either.cond(args.isEmpty, (),
      InvalidEventOrder("Setup selects nothing")).map(_ => tree(catalog, ready))

  /** The cards `player` may reveal in Reveal Cards: facedown advisers that
    * are not site-only, then facedown relics. They are offered by slot, as
    * Hunger offers advisers, so the parked decision can be described to
    * every other viewer without naming a facedown card. */
  def revealable(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId)
      : Vector[DecisionOption] =
    ready.game.current.players.find(_.player == player).toVector.flatMap { state =>
      state.advisers.zipWithIndex.collect {
        case (DenizenState(id, Orientation.FaceDown, _), slot)
            if !catalog.denizen(id).exists(_.isInstanceOf[SiteOnly]) =>
          DecisionOption.AdviserSlot(DecisionOptionRef.AdviserSlot(player, slot))
      } ++ state.relics.zipWithIndex.collect {
        case (RelicState(_, Orientation.FaceDown, _), slot) =>
          DecisionOption.RelicSlot(DecisionOptionRef.RelicSlot(player, slot))
      }
    }

  private def tree(catalog: ExecutableCatalog, ready: ReadyGame): Operation =
    val participants = turnOrder(ready)
    val siteOptions = ready.game.current.map.inPlay.map(site =>
      DecisionOption.Site(DecisionOptionRef.Site(site)))
    val steps = participants.map(playerStep(catalog, ready, _, siteOptions))
    val setupEnd = Sequence(Vector.empty, Some(PowerWindow.SetupEnd))
    Sequence(steps :+ setupEnd :+
      BeginTurn(participants.head.playerId, Phase.Wake))

  private def turnOrder(ready: ReadyGame): Vector[FirstGameParticipant] =
    val order = ready.game.current.players.map(_.player)
    val start = order.indexOf(ready.setup.firstPlayer)
    (order.drop(start) ++ order.take(start)).map { playerId =>
      val player = ready.game.current.players.find(_.player == playerId).get
      FirstGameParticipant(playerId, player.lineage,
        ready.playerColors(playerId))
    }

  private def playerStep(catalog: ExecutableCatalog, ready: ReadyGame,
      participant: FirstGameParticipant,
      siteOptions: Vector[DecisionOption.Site]): Operation =
    val pawnId = pawnDecisionId(participant.playerId)
    val adviserId = adviserDecisionId(participant.playerId)
    val hand = ready.game.current.temporaryHands
      .getOrElse(participant.playerId, Vector.empty)
      .collect { case id: DenizenId => id }
    val handOptions = hand.map(id =>
      DecisionOption.Denizen(DecisionOptionRef.Denizen(id)))
    Sequence(Vector(
      Decide(pawnId, participant.playerId,
        DecisionQuery.ChooseOne(siteOptions,
          heading = Some("Choose your starting site"))),
      BuildOps(placePawn(participant.playerId, pawnId),
        window = Some(PowerWindow.SetupPawnPlaced)),
      Decide(adviserId, participant.playerId,
        DecisionQuery.Partition(
          Vector(DecisionSection(adviserKeepKey, "Keep", 1, Some(1)),
            DecisionSection(adviserDiscardKey, "Discard", 0)),
          handOptions, heading = Some("Choose your starting adviser"),
          confirmLabel = Some("Confirm Adviser"))),
      BuildOps(chooseAdviser(participant.playerId, adviserId)),
      revealCards(catalog, participant.playerId)))

  private def revealCards(catalog: ExecutableCatalog, player: PlayerId)
      : Operation =
    val decisionId = revealDecisionId(player)
    Branch((state, _) =>
      val options = revealable(catalog, state, player)
      if options.isEmpty then Vector.empty
      else Vector(
        Decide(decisionId, player, DecisionQuery.ChooseMany(0, options.size,
          options, heading = Some("Reveal cards"))),
        BuildOps(reveal(player, decisionId))))

  private def reveal(player: PlayerId, decisionId: String)
      : (ReadyGame, PendingTree) => Either[OathViolation, Vector[CoreOperation]] =
    (ready, pending) =>
      val held = ready.game.current.players.find(_.player == player)
      pending.answered.find(_.decisionId == decisionId).map(_.answer) match
        case Some(DecisionAnswer.ChooseManyAnswer(selected)) =>
          Right(selected.flatMap {
            case DecisionOptionRef.AdviserSlot(_, slot) =>
              held.flatMap(_.advisers.lift(slot)).map(_.id)
            case DecisionOptionRef.RelicSlot(_, slot) =>
              held.flatMap(_.relics.lift(slot)).map(_.id)
            case _ => None
          }.map(card => Reveal(card, Location.PlayArea(player))))
        case _ => Left(InvalidEventOrder(
          s"no reveal answer is recorded for $decisionId"))

  private def placePawn(player: PlayerId, decisionId: String)
      : (ReadyGame, PendingTree) => Either[OathViolation, Vector[CoreOperation]] =
    (_, pending) => siteAnswer(pending, decisionId).map(site => Vector(
      Move(Piece.Pawn(player), PositionedLocation(Location.PlayArea(player)),
        PositionedLocation(Location.Site(site)))))

  private def siteAnswer(pending: PendingTree, decisionId: String)
      : Either[OathViolation, SiteId] =
    pending.answered.find(_.decisionId == decisionId).map(_.answer) match
      case Some(DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site))) =>
        Right(site)
      case _ => Left(InvalidEventOrder(
        s"no pawn-placement answer is recorded for $decisionId"))

  private def chooseAdviser(player: PlayerId, decisionId: String)
      : (ReadyGame, PendingTree) => Either[OathViolation, Vector[CoreOperation]] =
    (ready, pending) => for
      selection <- adviserSelection(pending, decisionId)
      (chosen, rejected) = selection
      pawnSite <- ready.game.current.players.find(_.player == player)
        .flatMap(_.pawnSite).toRight(PawnSiteMissing(player))
      region <- ready.game.current.map.regionOf(pawnSite)
        .toRight(InvalidEventOrder(s"${pawnSite.value} is not in play"))
    yield Move(Piece.Card(chosen), PositionedLocation(Location.Hand(player)),
        PositionedLocation(Location.PlayArea(player)),
        resultingOrientation = Some(Orientation.FaceDown)) +:
      rejected.map(id => Move(Piece.Card(id),
        PositionedLocation(Location.Hand(player)),
        PositionedLocation(Location.RegionalDiscard(nextRegion(region)),
          StackPosition.Top)))

  private def adviserSelection(pending: PendingTree, decisionId: String)
      : Either[OathViolation, (DenizenId, Vector[DenizenId])] =
    pending.answered.find(_.decisionId == decisionId).map(_.answer) match
      case Some(DecisionAnswer.PartitionAnswer(placements)) =>
        val kept = placements.filter(_.sectionKey == adviserKeepKey)
          .flatMap(value => denizen(value.option))
        val rejected = placements.filter(_.sectionKey == adviserDiscardKey)
          .flatMap(value => denizen(value.option))
        kept match
          case Vector(id) => Right(id -> rejected)
          case _ => Left(InvalidEventOrder(
            s"adviser-choice must keep exactly one card for $decisionId"))
      case _ => Left(InvalidEventOrder(
        s"no adviser-choice answer is recorded for $decisionId"))

  private def denizen(ref: DecisionOptionRef): Option[DenizenId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case _ => None

  private def nextRegion(region: Region): Region = region match
    case Region.Cradle => Region.Provinces
    case Region.Provinces => Region.Hinterland
    case Region.Hinterland => Region.Cradle
