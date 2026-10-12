package oathdigital.frontend

import oathdigital.frontend.Dom.all
import oathdigital.testkit.Unwrap.as
import ParkedDecision.Surface
import org.scalajs.dom

/** The generic choose-one panel, at the DOM: every projected option is one
  * control, and the consequences the engine annotated on it show beside it.
  * Runs under jsdom, like `PartitionPanelRenderSuite`.
  */
class WalkerChoicePanelRenderSuite extends munit.FunSuite:
  private val oak = DecisionOptionState("denizen", "d1", "Old Oak", None,
    Vector("1 Supply", "+2 warbands"))
  private val pub = DecisionOptionState("denizen", "d2", "Rowdy Pub")
  private val query = DecisionQueryState.ChooseOne(Vector(oak, pub),
    heading = Some("Choose a card to Muster from"))
  private val parked = WalkerDecisionState("muster", "muster.source", "decide",
    query = Some(query))

  /** The table the panel reads player names off; nothing here names one. */
  private val table = TestProjections.actingProjection(nextSequence = 9L)

  /** Draws the panel for a choose-one decision the route has already
    * matched: the surface carries the decision and its query.
    */
  private def draw(decision: WalkerDecisionState,
      ui: RecordingControls = new RecordingControls()): dom.Element =
    val query = decision.query match
      case Some(one: DecisionQueryState.ChooseOne) => one
      case other => fail(s"expected a choose-one, got $other")
    val panel = dom.document.createElement("div")
    WalkerPanelSupport.renderChooseOnePanel(
      Surface.ChooseOne(decision, query), table, canControl = true, panel, ui)
    panel

  test("each projected option is one control and its details show beside it"):
    val panel = draw(parked)
    assertEquals(all(panel, ".walker-choice").map(_.textContent),
      Vector("Old Oak", "Rowdy Pub"))
    assertEquals(all(panel, ".walker-choice-details").map(_.textContent),
      Vector("1 Supply · +2 warbands"))

  test("a card option shows its rules text on hover, and a reason wins"):
    val card = CardDetails("97", "denizen", "Insomnia",
      rulesText = Some("**REST:** Gain [secret]."))
    val power = DecisionOptionState("denizen", "97", "Insomnia", Some(card))
    val used = power.copy(state = "unavailable",
      reason = Some("Already used this turn"))
    def titles(option: DecisionOptionState) =
      all(draw(WalkerDecisionState("rest-menu", "phase.menu", "decide",
        query = Some(DecisionQueryState.ChooseOne(Vector(option))))),
        ".walker-choice").map(_.getAttribute("data-tip"))
    assertEquals(titles(power), Vector("**REST:** Gain [secret]."))
    assertEquals(titles(used), Vector("Already used this turn"))

  test("choosing an option submits the generic answer for its kind and id"):
    val ui = new RecordingControls()
    val panel = draw(parked, ui)
    as[dom.html.Button](all(panel, ".walker-choice").head).click()
    assertEquals(ui.submitted, Vector(oathdigital.protocol.GameIntent.ResolveWalker(
      "muster.source",
      oathdigital.protocol.DecisionAnswerWire.ChooseOneWire("denizen", "d1"))))

  test("a battle-plan offer draws its card and its side as a chip"):
    val card = CardDetails("relic:sticky-fire", "relic", "Sticky Fire",
      orientation = Some("face-up"))
    val plan = DecisionOptionState("relic", "relic:sticky-fire", "Sticky Fire",
      Some(card), Vector("1 Favor"), Some("Battle Plan"))
    val query = DecisionQueryState.ChooseOne(Vector(plan),
      heading = Some("Choose a battle plan, or finish"))
    val panel = draw(WalkerDecisionState("campaign", "campaign.attacker-plan",
      "decide", query = Some(query)))
    assertEquals(all(panel, ".card-choice > .card-face").size, 1)
    assertEquals(all(panel, ".walker-choice .card-face").size, 0)
    assertEquals(all(panel, ".plan-side-both").map(_.textContent),
      Vector("Battle Plan"))
    assertEquals(all(panel, ".walker-choice").map(_.getAttribute("aria-label")),
      Vector("Sticky Fire, Battle Plan"))
    assertEquals(all(panel, ".walker-choice-details").map(_.textContent),
      Vector("1 Favor"))

  test("the plans already played are listed above the remaining offers"):
    val played = DecisionOptionState("relic", "relic:sticky-fire", "Sticky Fire",
      None, Vector.empty, Some("Battle Plan"))
    val offer = DecisionOptionState("denizen", "denizen:longbows", "Longbows")
    val query = DecisionQueryState.ChooseOne(Vector(offer),
      heading = Some("Choose a battle plan, or finish"))
    val panel = draw(WalkerDecisionState("campaign", "campaign.attacker-plan",
      "decide", query = Some(query), answeredOptions = Vector(played)))
    assertEquals(all(panel, ".plans-played li").map(_.textContent),
      Vector("Sticky Fire"))
    val plansPlayed = Dom.one(panel, ".plans-played")
    val offerButton = Dom.one(panel, ".walker-choice")
    assertEquals(offerButton.textContent, "Longbows")
    assert((plansPlayed.compareDocumentPosition(offerButton) &
      dom.Node.DOCUMENT_POSITION_FOLLOWING) != 0,
      "the plans played should come before the offered choice")

  test("a card-shaped option with no badge stays a labelled text button, " +
      "not a card face -- Muster, Search, Forge and the like offer a card " +
      "but no badge"):
    val card = CardDetails("denizen:old-oak", "denizen", "Old Oak",
      orientation = Some("face-up"))
    val option = DecisionOptionState("denizen", "d1", "Old Oak", Some(card))
    val query = DecisionQueryState.ChooseOne(Vector(option),
      heading = Some("Choose a card to Muster from"))
    val panel = draw(WalkerDecisionState("muster", "muster.source", "decide",
      query = Some(query)))
    assertEquals(all(panel, ".walker-choice").map(_.textContent), Vector("Old Oak"))
    assertEquals(all(panel, ".card-face").size, 0)

  test("an attacker-only plan draws its card and the attack chip"):
    val card = CardDetails("relic:sticky-fire", "relic", "Sticky Fire",
      orientation = Some("face-up"))
    val plan = DecisionOptionState("relic", "relic:sticky-fire", "Sticky Fire",
      Some(card), badge = Some("Attack Plan"))
    val query = DecisionQueryState.ChooseOne(Vector(plan),
      heading = Some("Choose a battle plan, or finish"))
    val panel = draw(WalkerDecisionState("campaign", "campaign.attacker-plan",
      "decide", query = Some(query)))
    assertEquals(all(panel, ".card-choice > .card-face").size, 1)
    assertEquals(all(panel, "span.option-badge.plan-side-attack").map(_.textContent),
      Vector("Attack Plan"))

  /** A plan applies as soon as it is chosen, so reading its card must not
    * choose it: the face opens the inspector and nothing else.
    */
  test("clicking a battle plan's card submits nothing; its button chooses it"):
    val card = CardDetails("relic:sticky-fire", "relic", "Sticky Fire",
      orientation = Some("face-up"))
    val plan = DecisionOptionState("relic", "relic:sticky-fire", "Sticky Fire",
      Some(card), badge = Some("Attack Plan"))
    val query = DecisionQueryState.ChooseOne(Vector(plan),
      heading = Some("Choose a battle plan, or finish"))
    val ui = new RecordingControls()
    val panel = draw(WalkerDecisionState("campaign", "campaign.attacker-plan",
      "decide", query = Some(query)), ui)
    var inspected = Vector.empty[CardInspection.Request]
    CardInspection.onOpen(request => inspected :+= request)
    try as[dom.html.Button](all(panel, ".card-face").head).click()
    finally CardInspection.clear()
    assertEquals(inspected.size, 1)
    assertEquals(ui.submitted.size, 0)
    as[dom.html.Button](all(panel, ".walker-choice").head).click()
    assertEquals(ui.submitted.size, 1)

  test("a badged option with no card names its side in words"):
    val plan = DecisionOptionState("button", "title-plan", "Title plan",
      badge = Some("Defense Plan"))
    val query = DecisionQueryState.ChooseOne(Vector(plan),
      heading = Some("Choose a battle plan, or finish"))
    val panel = draw(WalkerDecisionState("campaign", "campaign.defender-plan",
      "decide", query = Some(query)))
    assertEquals(all(panel, ".walker-choice").map(_.getAttribute("aria-label")),
      Vector("Title plan, Defense Plan"))
    assertEquals(all(panel, ".card-choice").size, 0)

  test("a choose-one asked after a roll draws the roll before the question"):
    val outcome = WalkerRollOutcomeState("campaign.defense",
      Vector("one-shield", "blank"), 1)
    val site = DecisionOptionState("site", "s1", "The Spire")
    val query = DecisionQueryState.ChooseOne(Vector(site),
      heading = Some("Move the defending warbands"))
    val panel = draw(WalkerDecisionState("campaign", "campaign.relocation",
      "decide", query = Some(query), rollOutcome = Some(outcome)))
    assertEquals(all(panel, ".walker-roll-totals").map(_.textContent),
      Vector("Defense 1"))
    assertEquals(all(panel, ".walker-roll-faces").map(_.getAttribute("role")),
      Vector("img"))
    assertEquals(panel.firstElementChild.getAttribute("class"),
      "walker-roll-faces")
    // A Campaign's defense rolls the defense die.
    assertEquals(all(panel, ".die-face-defense").size, 2)

  test("an unavailable option is a withheld control that says why in its note"):
    val ui = new RecordingControls()
    val blocked = pub.copy(state = "unavailable",
      reason = Some("Lost Tongue: Red may not be targeted."))
    val panel = draw(parked.copy(query = Some(query.copy(
      options = Vector(oak, blocked)))), ui)
    val control = all(panel, ".walker-choice").last
      .asInstanceOf[dom.html.Button]
    assertEquals(control.getAttribute("aria-disabled"), "true")
    assertEquals(control.getAttribute("data-tip"),
      "Lost Tongue: Red may not be targeted.")
    assert(!panel.textContent.contains("Lost Tongue"))
    control.click()
    assertEquals(ui.submitted, Vector.empty)

  test("an option with a warning stays choosable and says why"):
    val ui = new RecordingControls()
    val warned = pub.copy(state = "warning",
      reason = Some("Hunting Party needs the world deck"))
    val panel = draw(parked.copy(query = Some(query.copy(
      options = Vector(oak, warned)))), ui)
    val control = all(panel, ".walker-choice").last
      .asInstanceOf[dom.html.Button]
    assert(!control.disabled)
    assertEquals(control.getAttribute("data-tip"),
      "Hunting Party needs the world deck")
    assert(!panel.textContent.contains("Hunting Party needs"))
    control.click()
    assertEquals(ui.submitted.size, 1)
