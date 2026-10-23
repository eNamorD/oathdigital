package oathdigital.frontend

import oathdigital.model.PlayerColor
import ParkedDecision.{RecoverStep, Routed, Surface}

/** The route as a pure function of a viewer's projection: one case per query
  * type and per surface, Recover's parks, board-presented decisions to the board, an
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
      Vector.empty, Vector.empty, ready = true,
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
  private def parked(action: String, id: String, query: DecisionQueryState)
      : WalkerDecisionState =
    WalkerDecisionState(action, id, query = Some(query))

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
    val choice = parked("recover", ParkedDecision.recoverChoiceDecisionId, one)
    assertEquals(routeOf(choice).surface,
      Some(Surface.Recover(choice, RecoverStep.Choice(one))))
    val relic = parked("recover", ParkedDecision.recoverRelicDecisionId, one)
    assertEquals(routeOf(relic).surface,
      Some(Surface.Recover(relic, RecoverStep.Relic(one))))

  test("a Recover park with nothing to render routes to no surface"):
    // A decide park whose query was suppressed: no answer could be built.
    Vector(ParkedDecision.recoverChoiceDecisionId,
      ParkedDecision.recoverRelicDecisionId).foreach(id =>
      assertEquals(routeOf(WalkerDecisionState("recover", id))
        .surface, None, s"$id must route nowhere without a query"))
    // A Recover choose-one at a decision id its panel does not know is
    // not handed to the generic panel either.
    assertEquals(routeOf(parked("recover", "recover.other", one)).surface,
      None)

  test("a decide park whose query was suppressed routes to no surface"):
    assertEquals(routeOf(WalkerDecisionState("forge", "forge-9"))
      .surface, None)

  test("a board-presented decision with an option on the table routes to " +
      "the board"):
    // Blue is a player at the table, so the option's home is Blue's name
    // and pawns.
    val atBoard = parked("muster", "muster.source", one)
      .copy(presentation = "board")
    assertEquals(routeOf(atBoard).surface,
      Some(Surface.BoardDecision(atBoard, one)))
    val manyAtBoard = parked("challenge", "challenge.ribbon-site", many)
      .copy(presentation = "board")
    assertEquals(routeOf(manyAtBoard).surface,
      Some(Surface.BoardDecision(manyAtBoard, many)))

  test("a board-presented decision with nothing on the table stays in the " +
      "panel"):
    val buttons = DecisionQueryState.ChooseOne(Vector(
      DecisionOptionState("button", "finish", "Finish")), heading)
    val decision = parked("campaign", "campaign.attacker-plan", buttons)
      .copy(presentation = "board")
    assertEquals(routeOf(decision).surface,
      Some(Surface.ChooseOne(decision, buttons)))

  test("a panel-presented decision stays in the panel"):
    val decision = parked("muster", "muster.source", one)
    assertEquals(decision.presentation, "panel")
    assertEquals(routeOf(decision).surface,
      Some(Surface.ChooseOne(decision, one)))

  test("a viewer without gameplay controls sees no control surface, only " +
      "a deal's summary"):
    assertEquals(routeOf(parked("muster", "muster.source", one),
      observer).surface, None)
    assertEquals(routeOf(parked("muster", "muster.source", partition),
      observer).surface, None)
    assertEquals(routeOf(negotiation, observer).surface,
      Some(Surface.Negotiate(deal, None)))

  test("a Recover modifier decision goes to the board, as any action's does"):
    val modifiers = parked("recover",
      ParkedDecision.recoverModifiersDecisionId, one)
      .copy(presentation = "board")
    assertEquals(routeOf(modifiers).surface,
      Some(Surface.BoardDecision(modifiers, one)))

  test("an observer of a parked deal sees its summary"):
    val waiting = WalkerWaitingState("blue", Some("Negotiation"),
      Vector("red"), Some(deal))
    assertEquals(ParkedDecision.route(projection(waiting = Some(waiting)),
      observer), Routed(Some(Surface.Negotiate(deal, None)), None))

  /** The status line above names who is waited on and the question, so a
    * notice here would say it twice.
    */
  test("a walker wait leaves the waiting line to the status"):
    def notice(waiting: WalkerWaitingState): Option[String] =
      ParkedDecision.route(projection(waiting = Some(waiting)), observer).notice
    assertEquals(notice(WalkerWaitingState("blue", Some("Choose the Oathkeeper"))),
      None)
    assertEquals(notice(WalkerWaitingState("blue")), None)
