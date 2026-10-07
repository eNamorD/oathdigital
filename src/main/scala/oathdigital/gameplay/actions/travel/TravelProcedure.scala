package oathdigital.gameplay.actions.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.TravelRules
import oathdigital.gameplay.walker.{WalkerPowers, WalkerSimulation}
import oathdigital.gameplay.OathLifecycle
import oathdigital.model._

/** Travel on the walker (travel destination design).
  *
  * {{{
  * Sequence(
  *   Decide(travel.destination),              // a board choose-one over sites
  *   Branch { after the answer:
  *     Sequence(                              // window = TravelActionEligibility
  *       Sequence(                            // window = TravelCost
  *         SpendSupply(actor, printedBase),
  *         Move(Pawn, source -> destination))) })
  * }}}
  *
  * **The destination is a decision.** A power that cares which site was
  * picked acts inside the walk and may ask its own question (Way Station).
  * The start carries no selection.
  *
  * **The windows sit inside the `Branch`.** Every Travel power finds the
  * route by flattening its windowed node to the `Move`, and a `Branch`
  * flattens to nothing, so a window above it would see no route. A selected
  * modifier's payment, prepended at the eligibility window, is therefore paid
  * after the pick, and nothing runs before the first decision.
  *
  * **The source is a start fact.** A resume after the pawn moved could not
  * read it from the pawn, so a fresh build reads the pawn and the start
  * stores it; a resume reads it back from `walkerStartFacts`.
  *
  * **What does not gate a start.** Supply, terrain, role and Foundation faces
  * are not build gates: the registry's playable-option gate previews every
  * destination and refuses a start none of them can complete.
  */
object TravelProcedure:
  val destinationDecisionId: String = "travel.destination"
  private val heading = "Choose a Travel destination"

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for
    _ <- OathLifecycle.validateAct(OathState.Ready(state), activePlayer)
    _ <- noSelection(args)
    source <- actorSite(state, activePlayer).toRight(
      OathViolation.PawnSiteMissing(activePlayer))
  yield tree(catalog, state, activePlayer, source)

  /** Resume rebuilds what the start built, from the source the start
    * stored. Once the pawn has moved the destination decision lists other
    * sites, but the walk is past it by then. */
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      activePlayer: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for
    _ <- noSelection(args)
    source <- sourceOf(state.game.current.walkerStartFacts)
  yield tree(catalog, state, activePlayer, source)

  /** The source site, the one fact a resumed Travel cannot read back. */
  def startFacts(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Vector[DecisionOptionRef] =
    actorSite(state, actor).map(DecisionOptionRef.Site(_)).toVector

  def actorSite(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)

  private def noSelection(args: Vector[DecisionOptionRef])
      : Either[OathViolation, Unit] = Either.cond(args.isEmpty, (),
    OathViolation.InvalidEventOrder("travel takes no start selection"))

  private def sourceOf(facts: Vector[DecisionOptionRef])
      : Either[OathViolation, SiteId] = facts match
    case Vector(DecisionOptionRef.Site(source)) => Right(source)
    case _ => Left(OathViolation.InvalidEventOrder(
      "a resumed Travel needs its source site as its one start fact"))

  private def tree(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, source: SiteId): Operation =
    val pick = Decide(destinationDecisionId, actor, DecisionQuery.ChooseOne(
      state.game.current.map.inPlay.filter(_ != source).map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some(heading)))
    val route = Branch((current, pending) =>
      pending.answered.collectFirst {
        case Answered(`destinationDecisionId`,
            DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)), _) =>
          site
      }.toVector.map(destination =>
        TravelRules.cost(catalog, current, source, destination).fold(
          error => BuildOps((_, _) => Left(error)),
          base => routeTree(actor, source, destination, base))))
    Sequence(Vector(pick, route))

  /** Closes only over command-stable actor, route, and printed base. */
  private def routeTree(actor: PlayerId, source: SiteId, destination: SiteId,
      base: Int): Operation =
    Sequence(Vector(
      Sequence(Vector(
        SpendSupply(actor, base),
        Move(Piece.Pawn(actor),
          PositionedLocation(Location.Site(source)),
          PositionedLocation(Location.Site(destination)))),
        Some(PowerWindow.TravelCost))),
      Some(PowerWindow.TravelActionEligibility))

  /** Whether a Travel could start now: some destination survives the same
    * preview a start runs. */
  def startable(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      powers: WalkerPowers): Boolean =
    build(catalog, state, actor, Vector.empty)
      .flatMap(WalkerSimulation.preview(_, state, powers))
      .exists(_.exists(_.outcome.isRight))
