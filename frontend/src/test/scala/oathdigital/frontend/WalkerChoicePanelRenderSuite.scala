package oathdigital.frontend

import org.scalajs.dom

/** The generic choose-one panel, at the DOM: every projected option is one
  * control, and the consequences the engine annotated on it show beside it.
  * Runs under jsdom, like `PartitionPanelRenderSuite`.
  */
class WalkerChoicePanelRenderSuite extends munit.FunSuite {
  private val oak = DecisionOptionState("denizen", "d1", "Old Oak", None,
    Vector("1 Supply", "+2 warbands"))
  private val pub = DecisionOptionState("denizen", "d2", "Rowdy Pub")
  private val query = DecisionQueryState("choose-one", Vector(oak, pub),
    heading = Some("Choose a card to Muster from"))
  private val parked = WalkerDecisionState("muster", "muster.source", "decide",
    query = Some(query))
  private val presentation = ServerUiSupport.ViewerPresentation(
    showGameplayControls = true, None, None)

  private def render(ui: RecordingView): dom.Element = {
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, ui)
    panel
  }

  private def all(root: dom.Element, selector: String): Vector[dom.Element] =
    root.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  test("each projected option is one control and its details show beside it") {
    val panel = render(new RecordingView("game", "red"))
    assertEquals(all(panel, ".walker-choice").map(_.textContent),
      Vector("Old Oak", "Rowdy Pub"))
    assertEquals(all(panel, ".walker-choice-details").map(_.textContent),
      Vector("1 Supply · +2 warbands"))
  }

  test("choosing an option submits the generic answer for its kind and id") {
    val ui = new RecordingView("game", "red")
    val panel = render(ui)
    all(panel, ".walker-choice").head.asInstanceOf[dom.html.Button].click()
    assertEquals(ui.submitted,
      Vector(WalkerPanelSupport.resolveChooseOneCommand(parked, oak)))
  }

  test("the card a placement is about is drawn above the buttons") {
    val subject = CardDetails("denizen:vow-of-peace", "denizen", "Vow of Peace",
      orientation = Some("face-down"))
    val place = WalkerDecisionState("play-facedown-adviser",
      "cardplay.place.denizen.denizen:vow-of-peace", "decide",
      query = Some(DecisionQueryState("choose-one", Vector(
        DecisionOptionState("button", "discard", "Discard"),
        DecisionOptionState("button", "adviser-faceup", "Play faceup")),
        heading = Some("Play or discard card"))),
      subjectCards = Vector(subject))
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(place))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, new RecordingView("game", "red"))
    assertEquals(all(panel, ".decision-subject .card-face").size, 1)
    assertEquals(all(panel, ".walker-choice").map(_.textContent),
      Vector("Discard", "Play faceup"))
  }

  test("Setup's pawn-placement decision renders no button panel -- it is " +
      "answered by clicking the site on the board instead") {
    val site = DecisionOptionState("site", "site:ancient-city", "Ancient City")
    val pawnQuery = DecisionQueryState("choose-one", Vector(site),
      heading = Some("Choose your starting site"))
    val pawnDecision = WalkerDecisionState("setup", "setup.pawn-placement.p1",
      "decide", query = Some(pawnQuery))
    assertEquals(WalkerPanelSupport.chooseOneStep(pawnDecision), None)
    assertEquals(WalkerPanelSupport.pawnPlacementStep(pawnDecision), Some(pawnQuery))
    assertEquals(WalkerPanelSupport.pawnPlacementStep(parked), None)
  }

  test("a battle-plan offer draws its card and its side as a chip") {
    val card = CardDetails("relic:sticky-fire", "relic", "Sticky Fire",
      orientation = Some("face-up"))
    val plan = DecisionOptionState("relic", "relic:sticky-fire", "Sticky Fire",
      Some(card), Vector("1 Favor"), Some("Battle Plan"))
    val query = DecisionQueryState("choose-one", Vector(plan),
      heading = Some("Choose a battle plan, or finish"))
    val parked = WalkerDecisionState("campaign", "campaign.attacker-plan",
      "decide", query = Some(query))
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, new RecordingView("game", "red"))
    assertEquals(all(panel, ".card-choice > .card-face").size, 1)
    assertEquals(all(panel, ".walker-choice .card-face").size, 0)
    assertEquals(all(panel, ".plan-side-both").map(_.textContent),
      Vector("Battle Plan"))
    assertEquals(all(panel, ".walker-choice").map(_.getAttribute("aria-label")),
      Vector("Sticky Fire, Battle Plan"))
    assertEquals(all(panel, ".walker-choice-details").map(_.textContent),
      Vector("1 Favor"))
  }

  test("the plans already played are listed above the remaining offers") {
    val played = DecisionOptionState("relic", "relic:sticky-fire", "Sticky Fire",
      None, Vector.empty, Some("Battle Plan"))
    val offer = DecisionOptionState("denizen", "denizen:longbows", "Longbows")
    val query = DecisionQueryState("choose-one", Vector(offer),
      heading = Some("Choose a battle plan, or finish"))
    val parked = WalkerDecisionState("campaign", "campaign.attacker-plan",
      "decide", query = Some(query), answeredOptions = Vector(played))
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, new RecordingView("game", "red"))
    assertEquals(all(panel, ".plans-played li").map(_.textContent),
      Vector("Sticky Fire"))
  }

  test("a card-shaped option with no badge stays a labelled text button, " +
      "not a card face -- Muster, Search, Forge and the like offer a card " +
      "but no badge") {
    val card = CardDetails("denizen:old-oak", "denizen", "Old Oak",
      orientation = Some("face-up"))
    val option = DecisionOptionState("denizen", "d1", "Old Oak", Some(card))
    val query = DecisionQueryState("choose-one", Vector(option),
      heading = Some("Choose a card to Muster from"))
    val parked = WalkerDecisionState("muster", "muster.source", "decide",
      query = Some(query))
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, new RecordingView("game", "red"))
    assertEquals(all(panel, ".walker-choice").map(_.textContent), Vector("Old Oak"))
    assertEquals(all(panel, ".card-face").size, 0)
  }

  test("an attacker-only plan draws its card and the attack chip") {
    val card = CardDetails("relic:sticky-fire", "relic", "Sticky Fire",
      orientation = Some("face-up"))
    val plan = DecisionOptionState("relic", "relic:sticky-fire", "Sticky Fire",
      Some(card), badge = Some("Attack Plan"))
    val query = DecisionQueryState("choose-one", Vector(plan),
      heading = Some("Choose a battle plan, or finish"))
    val parked = WalkerDecisionState("campaign", "campaign.attacker-plan",
      "decide", query = Some(query))
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, new RecordingView("game", "red"))
    assertEquals(all(panel, ".card-choice > .card-face").size, 1)
    assertEquals(all(panel, "span.option-badge.plan-side-attack").map(_.textContent),
      Vector("Attack Plan"))
  }

  /** A plan applies as soon as it is chosen, so reading its card must not
    * choose it: the face opens the inspector and nothing else.
    */
  test("clicking a battle plan's card submits nothing; its button chooses it") {
    val card = CardDetails("relic:sticky-fire", "relic", "Sticky Fire",
      orientation = Some("face-up"))
    val plan = DecisionOptionState("relic", "relic:sticky-fire", "Sticky Fire",
      Some(card), badge = Some("Attack Plan"))
    val query = DecisionQueryState("choose-one", Vector(plan),
      heading = Some("Choose a battle plan, or finish"))
    val parked = WalkerDecisionState("campaign", "campaign.attacker-plan",
      "decide", query = Some(query))
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    val ui = new RecordingView("game", "red")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, ui)
    var inspected = Vector.empty[CardInspection.Request]
    CardInspection.onOpen(request => inspected :+= request)
    try all(panel, ".card-face").head.asInstanceOf[dom.html.Button].click()
    finally CardInspection.clear()
    assertEquals(inspected.size, 1)
    assertEquals(ui.submitted.size, 0)
    all(panel, ".walker-choice").head.asInstanceOf[dom.html.Button].click()
    assertEquals(ui.submitted.size, 1)
  }

  test("a badged option with no card names its side in words") {
    val plan = DecisionOptionState("button", "title-plan", "Title plan",
      badge = Some("Defense Plan"))
    val query = DecisionQueryState("choose-one", Vector(plan),
      heading = Some("Choose a battle plan, or finish"))
    val parked = WalkerDecisionState("campaign", "campaign.defender-plan",
      "decide", query = Some(query))
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, new RecordingView("game", "red"))
    assertEquals(all(panel, ".walker-choice").map(_.getAttribute("aria-label")),
      Vector("Title plan, Defense Plan"))
    assertEquals(all(panel, ".card-choice").size, 0)
  }

  test("a choose-one asked after a roll draws the roll before the question") {
    val outcome = WalkerRollOutcomeState("campaign.defense",
      Vector("one-shield", "blank"), 1)
    val site = DecisionOptionState("site", "s1", "The Spire")
    val query = DecisionQueryState("choose-one", Vector(site),
      heading = Some("Move the defending warbands"))
    val parked = WalkerDecisionState("campaign", "campaign.relocation",
      "decide", query = Some(query), rollOutcome = Some(outcome))
    val projection = GameProjection("game", 9L, "act", Some("red"),
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
      completed = false, walkerDecision = Some(parked))
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(projection, presentation,
      canControl = true, panel, new RecordingView("game", "red"))
    assertEquals(all(panel, ".walker-roll-totals").map(_.textContent),
      Vector("Defense 1"))
    assertEquals(all(panel, ".walker-roll-faces").map(_.getAttribute("role")),
      Vector("img"))
    assertEquals(panel.firstElementChild.getAttribute("class"),
      "walker-roll-faces")
  }
}
