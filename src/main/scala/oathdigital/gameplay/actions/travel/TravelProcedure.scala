package oathdigital.gameplay.actions.travel

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.actions.{ProcedureNotes, TravelRules}
import oathdigital.gameplay.walker.{ModifierChoice, WalkerPowers}
import oathdigital.model._

/** Travel on the walker (travel destination design; modifier walker design).
  *
  * {{{
  * Sequence(
  *   Repeat(ModifierChoice),                // the modifiers, one per pass,
  *                                          // then "Start Travel"
  *   Sequence(
  *     Decide(travel.destination),          // a board choose-one over sites
  *     Branch { after the answer:
  *       Sequence(                          // window = TravelActionEligibility
  *         Sequence(                        // window = TravelCost
  *           SpendSupply(actor, printedBase),
  *           Move(Pawn, source -> destination))) }))
  * }}}
  *
  * **The modifiers come first.** Each pick pays at once, so the destination
  * decision shows what each site costs under them.
  *
  * **The destination is a decision.** A power that cares which site was
  * picked acts inside the walk and may ask its own question (Way Station).
  * A menu start carries no selection. A power's queued Travel may carry its
  * destination as its one start argument, and then asks nothing: the route
  * to that site runs after the modifiers (catalog batch 8, slice 15).
  *
  * **The windows sit inside the `Branch`.** Every Travel power finds the
  * route by flattening its windowed node to the `Move`, and a `Branch`
  * flattens to nothing, so a window above it would see no route.
  *
  * **The source is a start fact.** A resume after the pawn moved could not
  * read it from the pawn, so a fresh build reads the pawn and the start
  * stores it; a resume reads it back from `walkerStartFacts`.
  *
  * **What does not gate a start.** Supply, terrain, role and Foundation faces
  * are not build gates: "Start Travel" previews every destination and says
  * why none can be reached, and with no modifier to pick, the registry's
  * playable-option gate refuses a start none of them can complete.
  */
object TravelProcedure:
  val destinationDecisionId: String = "travel.destination"
  private val heading = "Choose a Travel destination"
  private val startLabel = "Start Travel"

  private val notes = NoteSet(ProcedureNotes.id(ActionRef.Travel))
  /** "Travelled to Broken Peaks". */
  val travelled: NoteKey = notes.key("travelled", Vector(
    NotePart.Text("Travelled to "), NotePart.Arg(0)))
  def noteKeys: Vector[NoteKey] = notes.declared

  def build(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for
    _ <- startArgs(state, actor, args)
    source <- actorSite(state, actor).toRight(
      OathViolation.PawnSiteMissing(actor))
  yield tree(catalog, state, actor, source)

  /** Resume rebuilds what the start built, from the source the start
    * stored. Once the pawn has moved the destination decision lists other
    * sites, but the walk is past it by then. */
  def rebuild(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, args: Vector[DecisionOptionRef])
      : Either[OathViolation, Operation] = for
    _ <- startArgs(state, actor, args)
    source <- sourceOf(state.game.current.walkerStartFacts)
  yield tree(catalog, state, actor, source)

  /** The source site, the one fact a resumed Travel cannot read back. */
  def startFacts(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Vector[DecisionOptionRef] =
    actorSite(state, actor).map(DecisionOptionRef.Site(_)).toVector

  def actorSite(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.players.find(_.player == actor).flatMap(_.pawnSite)

  /** The destination a power's queued Travel names, while that Travel's
    * walk runs for `actor` (catalog batch 8, slice 15): its first start
    * argument, a site. Any argument after it is for the sending power's
    * own rules to read. Only a `Run` entry with a source carries one; a
    * menu Travel never does. */
  def queuedDestination(state: ReadyGame, actor: PlayerId): Option[SiteId] =
    state.game.current.walkerEntry.filter(entry =>
      entry.procedure == ActionRef.Travel && entry.actor == actor &&
        entry.source.nonEmpty && entry.kind == QueueKind.Run)
      .flatMap(_.startArgs match
        case DecisionOptionRef.Site(site) +: _ => Some(site)
        case _ => None)

  /** The queued destination as its actor's answer to the destination
    * decision, which the walk does not ask. A waivable restriction finds a
    * grant under it as it finds one under a destination the actor picked. */
  def queuedAnswers(state: ReadyGame): Vector[Answered] =
    state.game.current.walkerEntry.toVector.flatMap(entry =>
      queuedDestination(state, entry.actor).map(site => Answered(
        destinationDecisionId,
        DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)),
        entry.actor)))

  private def startArgs(state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Unit] =
    args match
      case Vector() => Right(())
      case DecisionOptionRef.Site(site) +: _
          if queuedDestination(state, actor).contains(site) &&
            state.game.current.walkerEntry.exists(_.startArgs == args) =>
        Right(())
      case _ => Left(OathViolation.InvalidEventOrder(
        "travel takes no start selection but a power's queued destination"))

  private def sourceOf(facts: Vector[DecisionOptionRef])
      : Either[OathViolation, SiteId] = facts match
    case Vector(DecisionOptionRef.Site(source)) => Right(source)
    case _ => Left(OathViolation.InvalidEventOrder(
      "a resumed Travel needs its source site as its one start fact"))

  /** The destination decision and the Travel to it: what a fresh start runs
    * after its modifier decision. The Travel suites preview it. */
  def afterModifiers(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId): Either[OathViolation, Operation] = for
    source <- actorSite(state, actor).toRight(
      OathViolation.PawnSiteMissing(actor))
  yield journey(catalog, state, actor, source)

  private def tree(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, source: SiteId): Operation =
    val rest = journey(catalog, state, actor, source)
    Sequence(Vector(ModifierChoice.loop(actor,
      PowerWindow.TravelModifierSelection, rest, startLabel,
      Some(TravelOptions.priced)), rest))

  /** The destination pick and the route to it, or only the route to a
    * queued destination. */
  private def journey(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, source: SiteId): Operation =
    queuedDestination(state, actor).fold(picked(catalog, state, actor,
      source))(destination => route(catalog, actor, source, destination))

  private def picked(catalog: ExecutableCatalog, state: ReadyGame,
      actor: PlayerId, source: SiteId): Operation =
    val pick = Decide(destinationDecisionId, actor, DecisionQuery.ChooseOne(
      state.game.current.map.inPlay.filter(_ != source).map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some(heading), confirmVerb = Some("Travel to")))
    val chosen = Branch((_, pending) => pending.answered.collectFirst {
      case Answered(`destinationDecisionId`,
          DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)), _) =>
        site
    }.toVector.map(route(catalog, actor, source, _)))
    Sequence(Vector(pick, chosen))

  /** The route to `destination`, priced on the state it is reached in. The
    * windows sit inside its `Branch`, as the class doc says. */
  private def route(catalog: ExecutableCatalog, actor: PlayerId,
      source: SiteId, destination: SiteId): Operation =
    Branch((current, _) => Vector(TravelRules.cost(catalog, current, source,
      destination).fold(error => BuildOps((_, _) => Left(error)),
      base => routeTree(actor, source, destination, base))))

  /** Closes only over command-stable actor, route, and printed base. */
  private def routeTree(actor: PlayerId, source: SiteId, destination: SiteId,
      base: Int): Operation =
    Sequence(Vector(
      Sequence(Vector(
        SpendSupply(actor, base),
        Move(Piece.Pawn(actor),
          PositionedLocation(Location.Site(source)),
          PositionedLocation(Location.Site(destination)))),
        Some(PowerWindow.TravelCost)),
      // Outside the cost window and after it. Every Travel power finds the
      // route by flattening that window to its `Move`, so a note inside it
      // would be a child those powers rewrite. What the note reads is the
      // arrival in the live state, not the step before it: see
      // `travelledNote`.
      Note(ProcedureNotes.id(ActionRef.Travel),
        travelledNote(actor, source))),
      Some(PowerWindow.TravelActionEligibility))

  /** Where the pawn stands once the cost window has run, when that is not
    * where it started. Read against the source rather than off
    * `states.previous`: four Travel powers append a leaf after the `Move`
    * inside the cost window, and every leaf resets `previous`, so a note
    * reading the last step would see the pawn already arrived and write
    * nothing. Reading the arrival from the state still names a power that
    * rerouted the pawn correctly, and a Travel whose `Move` a power removed
    * writes no line, as the rule this replaced wrote none without a
    * recorded pawn move. */
  private def travelledNote(actor: PlayerId, source: SiteId)(
      states: NoteStates): Option[LogNote] =
    actorSite(states.now, actor).filterNot(_ == source)
      .map(arrived => travelled.sourceless(NoteArg.Site(arrived)))
