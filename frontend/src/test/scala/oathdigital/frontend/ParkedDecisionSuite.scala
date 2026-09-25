package oathdigital.frontend

import oathdigital.model.PlayerColor
import ParkedDecision.{DecisionForm, Routed, Surface}
import WalkerPanelSupport.RecoverWalkerStep

/** The route as a pure function of a viewer's projection: one case per form
  * and per surface, Recover's parks, pawn placement to the board, an
  * observer's deal summary beside the waiting notice, and the parks that
  * render nothing. No DOM: what a surface looks like is the panel suites'
  * business.
  */
class ParkedDecisionSuite extends munit.FunSuite {
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val blue = GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)
  private val controls = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None)
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
  private def query(form: String): DecisionQueryState =
    DecisionQueryState(form, Vector(option), heading = Some("Question"))
  private def parked(action: String, id: String, query: DecisionQueryState,
      kind: String = "decide"): WalkerDecisionState =
    WalkerDecisionState(action, id, kind, query = Some(query))

  private val editing = NegotiationEditingState(5, Vector.empty, Vector.empty,
    Vector.empty, canAccept = true)
  private val deal = NegotiationDealState(Vector("red", "blue"), Vector.empty,
    Vector.empty, Vector.empty, Some(editing))
  private val negotiation = parked("negotiation", "negotiation.deal",
    query("negotiate").copy(deal = Some(deal)))

  test("nothing parked routes to no surface and no notice") {
    assertEquals(ParkedDecision.route(projection(), controls),
      Routed(None, None))
  }

  test("each form routes to the surface that answers it") {
    def surface(form: String): Option[Surface] =
      routeOf(parked("muster", "muster.source", query(form))).surface
    val oneQuery = query("choose-one")
    assertEquals(surface("choose-one"), Some(Surface.ChooseOne(
      parked("muster", "muster.source", oneQuery), oneQuery)))
    assertEquals(surface("partition"), Some(Surface.Partition(
      parked("muster", "muster.source", query("partition")),
      query("partition"))))
    assertEquals(surface("distribute"), Some(Surface.Distribute(
      parked("muster", "muster.source", query("distribute")),
      query("distribute"))))
    assertEquals(surface("choose-many"), Some(Surface.Selection(
      parked("muster", "muster.source", query("choose-many")),
      query("choose-many"), DecisionForm.ChooseMany)))
    assertEquals(surface("choose-amount"), Some(Surface.Selection(
      parked("muster", "muster.source", query("choose-amount")),
      query("choose-amount"), DecisionForm.ChooseAmount)))
    assertEquals(routeOf(negotiation).surface, Some(Surface.Negotiate(deal,
      Some("negotiation.deal" -> editing))))
  }

  test("an unknown form routes to no surface and keeps its spelling") {
    assertEquals(routeOf(parked("muster", "muster.source",
      query("choose-two"))).surface, None)
    assertEquals(DecisionForm.parse("choose-two"),
      DecisionForm.Unknown("choose-two"))
  }

  test("a negotiate query without a deal routes to no surface") {
    assertEquals(routeOf(parked("negotiation", "negotiation.deal",
      query("negotiate"))).surface, None)
  }

  test("Recover's parks route to its own panel") {
    val roll = WalkerDecisionState("recover", "walker.recover.roll", "roll",
      pool = Some("recover"), count = Some(2))
    assertEquals(routeOf(roll).surface,
      Some(Surface.Recover(roll, RecoverWalkerStep.Roll("recover"))))
    val choice = parked("recover", WalkerPanelSupport.recoverChoiceDecisionId,
      query("choose-one"))
    assertEquals(routeOf(choice).surface,
      Some(Surface.Recover(choice, RecoverWalkerStep.Choice(query("choose-one")))))
    val relic = parked("recover", WalkerPanelSupport.recoverRelicDecisionId,
      query("choose-one"))
    assertEquals(routeOf(relic).surface,
      Some(Surface.Recover(relic, RecoverWalkerStep.Relic(query("choose-one")))))
  }

  test("a Recover park with nothing to render routes to no surface") {
    // A roll with no projected pool: no control could be built for it.
    assertEquals(routeOf(WalkerDecisionState("recover", "walker.recover.roll",
      "roll")).surface, None)
    // A decide park whose query was suppressed: no answer could be built.
    Vector(WalkerPanelSupport.recoverChoiceDecisionId,
      WalkerPanelSupport.recoverRelicDecisionId).foreach(id =>
      assertEquals(routeOf(WalkerDecisionState("recover", id, "decide"))
        .surface, None, s"$id must route nowhere without a query"))
    // A Recover choose-one at a decision id its panel does not know is
    // not handed to the generic panel either.
    assertEquals(routeOf(parked("recover", "recover.other",
      query("choose-one"))).surface, None)
  }

  test("a Roll park outside Recover routes to no surface") {
    assertEquals(routeOf(WalkerDecisionState("teleport", "walker.recover.roll",
      "roll", pool = Some("recover"))).surface, None)
  }

  test("a decide park whose query was suppressed routes to no surface") {
    assertEquals(routeOf(WalkerDecisionState("forge", "forge-9", "decide"))
      .surface, None)
  }

  test("a choose-one that is not a decide park routes to no surface") {
    assertEquals(routeOf(parked("muster", "muster.source",
      query("choose-one"), kind = "roll")).surface, None)
  }

  test("Setup's pawn placement routes to the board, not the button panel") {
    val site = DecisionOptionState("site", "site:ancient-city", "Ancient City")
    val pawnQuery = DecisionQueryState("choose-one", Vector(site),
      heading = Some("Choose your starting site"))
    val pawn = parked("setup", "setup.pawn-placement.p1", pawnQuery)
    assertEquals(routeOf(pawn).surface,
      Some(Surface.PawnPlacement(pawn, pawnQuery)))
  }

  test("a viewer without gameplay controls sees no control surface, only " +
      "a deal's summary") {
    assertEquals(routeOf(parked("muster", "muster.source",
      query("choose-one")), observer).surface, None)
    assertEquals(routeOf(parked("muster", "muster.source",
      query("partition")), observer).surface, None)
    assertEquals(routeOf(WalkerDecisionState("recover", "walker.recover.roll",
      "roll", pool = Some("recover")), observer).surface, None)
    assertEquals(routeOf(negotiation, observer).surface,
      Some(Surface.Negotiate(deal, None)))
  }

  test("an observer of a parked deal sees its summary beside the notice") {
    val waiting = WalkerWaitingState("blue", Some("Negotiation"),
      Vector("red"), Some(deal))
    assertEquals(ParkedDecision.route(projection(waiting = Some(waiting)),
      observer), Routed(Some(Surface.Negotiate(deal, None)),
        Some("Waiting for Blue: Negotiation")))
  }

  test("the waiting notice names the player and the question") {
    def notice(waiting: WalkerWaitingState): Option[String] =
      ParkedDecision.route(projection(waiting = Some(waiting)), observer).notice
    assertEquals(notice(WalkerWaitingState("blue", Some("Choose the Oathkeeper"))),
      Some("Waiting for Blue: Choose the Oathkeeper"))
    // A parked Roll asks nothing, so the notice has no question to name.
    assertEquals(notice(WalkerWaitingState("blue")), Some("Waiting for Blue"))
    // A player the projection does not list is named by id, not dropped.
    assertEquals(notice(WalkerWaitingState("green", Some("Question"))),
      Some("Waiting for green: Question"))
  }
}
