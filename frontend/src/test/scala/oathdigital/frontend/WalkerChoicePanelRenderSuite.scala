package oathdigital.frontend

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
  private val table = GameProjection("game", 9L, "act", Some("red"),
    Vector.empty, Vector.empty, Vector.empty, Vector.empty, ready = true,
    completed = false)

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

  private def all(root: dom.Element, selector: String): Vector[dom.Element] =
    root.querySelectorAll(selector).toVector.map(_.asInstanceOf[dom.Element])

  test("each projected option is one control and its details show beside it"):
    val panel = draw(parked)
    assertEquals(all(panel, ".walker-choice").map(_.textContent),
      Vector("Old Oak", "Rowdy Pub"))
    assertEquals(all(panel, ".walker-choice-details").map(_.textContent),
      Vector("1 Supply · +2 warbands"))

  test("choosing an option submits the generic answer for its kind and id"):
    val ui = new RecordingControls()
    val panel = draw(parked, ui)
    all(panel, ".walker-choice").head.asInstanceOf[dom.html.Button].click()
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
    try all(panel, ".card-face").head.asInstanceOf[dom.html.Button].click()
    finally CardInspection.clear()
    assertEquals(inspected.size, 1)
    assertEquals(ui.submitted.size, 0)
    all(panel, ".walker-choice").head.asInstanceOf[dom.html.Button].click()
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
