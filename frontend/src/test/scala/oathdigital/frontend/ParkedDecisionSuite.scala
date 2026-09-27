package oathdigital.frontend

import oathdigital.model.PlayerColor
import ParkedDecision.{RecoverStep, Routed, Surface}

/** The route as a pure function of a viewer's projection: one case per query
  * type and per surface, Recover's parks, pawn placement to the board, an
  * observer's deal summary beside the waiting notice, and the parks that
  * render nothing. No DOM: what a surface looks like is the panel suites'
  * business.
  */
class ParkedDecisionSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val blue = GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)
  private val controls = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None, playerId = "red")
  private val observer = controls.copy(showGameplayControls = false)

  private def projection(decision: Option[WalkerDecisionState] = None,
      waiting: Option[WalkerWaitingState] = None): GameProjection =
    GameProjection("game", 9L, "act", Some("red"), Vector(red, blue),
      Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = decision, walkerWaiting = waiting)

  private def routeOf(decision: WalkerDecisionState,
      presentation: ServerUiSupport.ViewerPresentation = controls): Routed =
    ParkedDecision.route(projection(Some(decision)), presentation)

  private val option = DecisionOptionState("player", "blue", "Blue")
  private val heading = Some("Question")
  // One constructor per form, where a `query(form: String)` helper used to
  // build every shape from one record. Each is a fixed value rather than a
  // method, so `parked(..., one)` and the expected surface hold the SAME
  // query and an equality failure cannot be a fixture difference.
  private val one = DecisionQueryState.ChooseOne(Vector(option), heading)
  private val many = DecisionQueryState.ChooseMany(Vector(option), 1, 1,
    heading)
  private val amount = DecisionQueryState.ChooseAmount(0, 2, None, "Place",
    heading)
  private val partition = DecisionQueryState.Partition(
    Vector(DecisionSectionState("keep", "Keep", 1, Some(1))), Vector(option),
    Some("Split"), heading)
  private val distribute = DecisionQueryState.Distribute(
    Vector(DecisionSlotState(option, 0, 2, None)), 0, 2, "Place", heading)
  private def parked(action: String, id: String, query: DecisionQueryState,
      kind: String = "decide"): WalkerDecisionState =
    WalkerDecisionState(action, id, kind, query = Some(query))

  private val editing = NegotiationEditingState(5, Vector.empty, Vector.empty,
    Vector.empty, canAccept = true)
  private val deal = NegotiationDealState(Vector("red", "blue"), Vector.empty,
    Vector.empty, Vector.empty, Some(editing))
  private val negotiation = parked("negotiation", "negotiation.deal",
    DecisionQueryState.Negotiate(deal, heading))

  test("nothing parked routes to no surface and no notice"):
    assertEquals(ParkedDecision.route(projection(), controls),
      Routed(None, None))

  test("each form routes to the surface that answers it"):
    def at(query: DecisionQueryState): WalkerDecisionState =
      parked("muster", "muster.source", query)
    assertEquals(routeOf(at(one)).surface,
      Some(Surface.ChooseOne(at(one), one)))
    assertEquals(routeOf(at(partition)).surface,
      Some(Surface.Partition(at(partition), partition)))
    assertEquals(routeOf(at(distribute)).surface,
      Some(Surface.Distribute(at(distribute), distribute)))
    assertEquals(routeOf(at(many)).surface,
      Some(Surface.Selection(at(many), many)))
    assertEquals(routeOf(at(amount)).surface,
      Some(Surface.Selection(at(amount), amount)))
    assertEquals(routeOf(negotiation).surface, Some(Surface.Negotiate(deal,
      Some("negotiation.deal" -> editing))))

  // A form outside the vocabulary no longer reaches the route: the codec
  // refuses it, which `ProjectionProtocolSuite` asserts. A negotiate query
  // without a deal is likewise unconstructable -- the deal is not optional.

  test("an inspect routes to its card list panel"):
    val inspect = DecisionQueryState.Inspect(Vector.empty,
      DecisionOptionState("button", "done", "Done"), heading)
    val at = parked("use-power", "power.scryer.inspect", inspect)
    assertEquals(routeOf(at).surface, Some(Surface.Inspect(at, inspect)))

  test("Recover's parks route to its own panel"):
    val roll = WalkerDecisionState("recover", "walker.recover.roll", "roll",
      pool = Some("recover"), count = Some(2))
    assertEquals(routeOf(roll).surface,
      Some(Surface.Recover(roll, RecoverStep.Roll("recover"))))
    val choice = parked("recover", ParkedDecision.recoverChoiceDecisionId, one)
    assertEquals(routeOf(choice).surface,
      Some(Surface.Recover(choice, RecoverStep.Choice(one))))
    val relic = parked("recover", ParkedDecision.recoverRelicDecisionId, one)
    assertEquals(routeOf(relic).surface,
      Some(Surface.Recover(relic, RecoverStep.Relic(one))))

  test("a Recover park with nothing to render routes to no surface"):
    // A roll with no projected pool: no control could be built for it.
    assertEquals(routeOf(WalkerDecisionState("recover", "walker.recover.roll",
      "roll")).surface, None)
    // A decide park whose query was suppressed: no answer could be built.
    Vector(ParkedDecision.recoverChoiceDecisionId,
      ParkedDecision.recoverRelicDecisionId).foreach(id =>
      assertEquals(routeOf(WalkerDecisionState("recover", id, "decide"))
        .surface, None, s"$id must route nowhere without a query"))
    // A Recover choose-one at a decision id its panel does not know is
    // not handed to the generic panel either.
    assertEquals(routeOf(parked("recover", "recover.other", one)).surface,
      None)

  test("a Roll park outside Recover routes to no surface"):
    assertEquals(routeOf(WalkerDecisionState("teleport", "walker.recover.roll",
      "roll", pool = Some("recover"))).surface, None)

  test("a decide park whose query was suppressed routes to no surface"):
    assertEquals(routeOf(WalkerDecisionState("forge", "forge-9", "decide"))
      .surface, None)

  test("a choose-one that is not a decide park routes to no surface"):
    assertEquals(routeOf(parked("muster", "muster.source", one,
      kind = "roll")).surface, None)

  test("Setup's pawn placement routes to the board, confirmed from the pane"):
    val site = DecisionOptionState("site", "site:ancient-city", "Ancient City")
    val pawnQuery = DecisionQueryState.ChooseOne(Vector(site),
      Some("Choose your starting site"))
    val pawn = parked("setup", "setup.pawn-placement.p1", pawnQuery)
    assertEquals(routeOf(pawn).surface,
      Some(Surface.Board(pawn, pawnQuery, confirm = true)))

  test("a viewer without gameplay controls sees no control surface, only " +
      "a deal's summary"):
    assertEquals(routeOf(parked("muster", "muster.source", one),
      observer).surface, None)
    assertEquals(routeOf(parked("muster", "muster.source", partition),
      observer).surface, None)
    assertEquals(routeOf(WalkerDecisionState("recover", "walker.recover.roll",
      "roll", pool = Some("recover")), observer).surface, None)
    assertEquals(routeOf(negotiation, observer).surface,
      Some(Surface.Negotiate(deal, None)))

  test("an observer of a parked deal sees its summary beside the notice"):
    val waiting = WalkerWaitingState("blue", Some("Negotiation"),
      Vector("red"), Some(deal))
    assertEquals(ParkedDecision.route(projection(waiting = Some(waiting)),
      observer), Routed(Some(Surface.Negotiate(deal, None)),
        Some("Waiting for Blue: Negotiation")))

  test("the waiting notice names the player and the question"):
    def notice(waiting: WalkerWaitingState): Option[String] =
      ParkedDecision.route(projection(waiting = Some(waiting)), observer).notice
    assertEquals(notice(WalkerWaitingState("blue", Some("Choose the Oathkeeper"))),
      Some("Waiting for Blue: Choose the Oathkeeper"))
    // A parked Roll asks nothing, so the notice has no question to name.
    assertEquals(notice(WalkerWaitingState("blue")), Some("Waiting for Blue"))
    // A player the projection does not list is named by id, not dropped.
    assertEquals(notice(WalkerWaitingState("green", Some("Question"))),
      Some("Waiting for green: Question"))
