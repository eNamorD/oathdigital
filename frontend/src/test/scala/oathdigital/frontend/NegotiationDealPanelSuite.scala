package oathdigital.frontend

import oathdigital.protocol.{DecisionAnswerWire, GameIntent => Intent,
  NegotiationTerms, NegotiationTransfer}
import oathdigital.testkit.Unwrap.as
import ParkedDecision.Surface
import org.scalajs.dom

/** The deal panel for a parked negotiation, as an owner and as a spectator. */
class NegotiationDealPanelSuite extends munit.FunSuite:
  // Faceup, so it is offered as a transfer only and not also as a disclosure.
  private val relic = CardDetails("r1", "relic", "Relic One",
    orientation = Some("face-up"))

  /** The deal red edits, in which red and blue take part and blue has
    * already accepted. Blue offers red 2 favor and no relic, and promises
    * red a held-relic disclosure. Red, while `editing`, may offer up to 5
    * favor and the relic `relic`; `canAccept` is whether the engine lets
    * red accept.
    */
  private def dealWhereBlueHasAccepted(canAccept: Boolean = true,
      editing: Boolean = true) =
    NegotiationDealState(
      participantPlayerIds = Vector("red", "blue"),
      acceptedPlayerIds = Vector("blue"),
      transfers = Vector(NegotiationTransferState(authorPlayerId = "blue",
        recipientPlayerId = "red", favor = 2, relicCount = 0,
        relics = Vector.empty)),
      disclosures = Vector(NegotiationDisclosureState(authorPlayerId = "blue",
        recipientPlayerId = "red", kind = "held-relic", card = None)),
      editing = Option.when(editing)(NegotiationEditingState(
        editableFavor = 5, editableRelics = Vector(relic),
        editableAdvisers = Vector.empty, editableSiteRelics = Vector.empty,
        canAccept = canAccept)))

  /** The surface the route hands a participant: the deal with its editor. */
  private def owner(value: NegotiationDealState): Surface.Negotiate =
    Surface.Negotiate(value, value.editing.map("negotiation.deal" -> _))

  /** The surface every other viewer gets: the deal, read-only. */
  private def spectator(value: NegotiationDealState): Surface.Negotiate =
    Surface.Negotiate(value, None)

  private def render(surface: Surface.Negotiate, ui: RecordingControls,
      canControl: Boolean = true, playerId: String = "red"): dom.Element =
    val panel = dom.document.createElement("div")
    NegotiationDealPanel.render(surface, playerId, canControl, panel, ui)
    panel

  private def button(root: dom.Element, selector: String): dom.html.Button =
    as[dom.html.Button](Dom.one(root, selector))

  private def input(root: dom.Element, selector: String): dom.html.Input =
    as[dom.html.Input](Dom.one(root, selector))

  test("every term the owner edits is labelled and on its own row"):
    val panel = render(owner(dealWhereBlueHasAccepted()),
      new RecordingControls())
    val rows = Dom.all(panel, ".negotiation-item")
    // One row for the favor box, one for the relic on offer.
    assertEquals(rows.length, 2)
    assert(rows.head.textContent.startsWith("Favor"),
      s"the number box says what it counts, got '${rows.head.textContent}'")
    assertEquals(rows.head.querySelectorAll("input[type=number]").length, 1)
    assertEquals(rows(1).textContent.trim, "Relic One")
    assertEquals(rows(1).querySelectorAll("input[type=checkbox]").length, 1)

  test("an owner sees the deal and can save, accept and decline"):
    val ui = new RecordingControls()
    val panel = render(owner(dealWhereBlueHasAccepted()), ui)
    assertEquals(Dom.one(panel, "h2").textContent, "Negotiation")
    assertEquals(Dom.one(panel, ".negotiation-status").textContent,
      "red: reviewing · blue: accepted")
    assertEquals(Dom.one(panel, ".negotiation-transfer").textContent,
      "blue gives red: 2 favor")
    assertEquals(Dom.one(panel, ".negotiation-disclosure").textContent,
      "blue promises to show red a held relic")
    input(panel, "input[type=number]").value = "3"
    input(panel, "input[type=checkbox]").checked = true
    button(panel, ".negotiation-save").click()
    button(panel, ".negotiation-accept").click()
    button(panel, ".negotiation-decline").click()
    // The owner is red, so the one transfer Save sends goes to blue, the
    // only other player.
    assertEquals(ui.submitted, Vector[Intent](
      Intent.ResolveWalker("negotiation.deal", DecisionAnswerWire.ProposeTermsWire(
        NegotiationTerms(Vector(NegotiationTransfer("blue", 3, Vector("r1"))),
          Vector.empty))),
      Intent.ResolveWalker("negotiation.deal", DecisionAnswerWire.AcceptDealWire),
      Intent.ResolveWalker("negotiation.deal", DecisionAnswerWire.DeclineDealWire)))

  test("accept is disabled until the engine says this player may accept"):
    val ui = new RecordingControls()
    val panel = render(owner(dealWhereBlueHasAccepted(canAccept = false)), ui)
    assert(button(panel, ".negotiation-accept").disabled)
    assert(!button(panel, ".negotiation-decline").disabled)

  test("a viewer who cannot control the game gets a disabled Save"):
    val blocked = render(owner(dealWhereBlueHasAccepted()),
      new RecordingControls(), canControl = false)
    assert(button(blocked, ".negotiation-save").disabled)

  test("a spectator sees the deal read-only, with no inputs or buttons"):
    val ui = new RecordingControls()
    val panel = render(spectator(dealWhereBlueHasAccepted(editing = false)),
      ui, playerId = "green")
    assertEquals(Dom.one(panel, ".negotiation-status").textContent,
      "red: reviewing · blue: accepted")
    assertEquals(panel.querySelectorAll("input").length, 0)
    assertEquals(panel.querySelectorAll("button").length, 0)

  private def threeWay(editing: NegotiationEditingState,
      transfers: Vector[NegotiationTransferState] = Vector.empty,
      disclosures: Vector[NegotiationDisclosureState] = Vector.empty) =
    NegotiationDealState(Vector("red", "blue", "yellow"), Vector.empty,
      transfers, disclosures, Some(editing))

  private def box(panel: dom.Element, label: String): dom.html.Input =
    input(panel, s"""input[aria-label="$label"]""")

  test("the editor restores only the viewer's own relic and disclosure selections"):
    val adviser = CardDetails("D1", "denizen", "Hidden Adviser")
    val deal = threeWay(NegotiationEditingState(3, Vector(relic),
      Vector(adviser), Vector.empty, canAccept = false),
      Vector(NegotiationTransferState("red", "blue", 0, 1, Vector(relic))),
      Vector(NegotiationDisclosureState("red", "yellow", "adviser", Some(adviser))))
    val panel = render(owner(deal), new RecordingControls())
    assert(box(panel, "Offer Relic One to blue").checked)
    assert(!box(panel, "Offer Relic One to yellow").checked)
    assert(box(panel,
      "Promise adviser disclosure of Hidden Adviser to yellow").checked)
    assert(!box(panel,
      "Promise adviser disclosure of Hidden Adviser to blue").checked)

  test("offering a relic to one recipient clears it for another"):
    val deal = threeWay(NegotiationEditingState(3, Vector(relic), Vector.empty,
      Vector.empty, canAccept = false))
    val panel = render(owner(deal), new RecordingControls())
    val blue = box(panel, "Offer Relic One to blue")
    val yellow = box(panel, "Offer Relic One to yellow")
    blue.checked = true
    yellow.checked = true
    yellow.dispatchEvent(new dom.Event("change"))
    assert(!blue.checked)
    assert(yellow.checked)

  /** Red's editor over a two-player deal with nothing agreed yet, offering
    * every kind of disclosure: a faceup relic (offered as a transfer only),
    * a facedown relic, an adviser, and a relic lying at the site
    * "site:broken-peaks".
    */
  private def dealWithEveryDisclosureKindOffered() =
    val faceUp = CardDetails("R1", "relic", "Public Relic",
      orientation = Some("face-up"))
    val faceDown = CardDetails("R2", "relic", "Secret Relic",
      orientation = Some("face-down"))
    val adviser = CardDetails("D1", "denizen", "Hidden Adviser")
    val siteRelic = CardDetails("R3", "relic", "Bone Dice")
    val editing = NegotiationEditingState(3, Vector(faceUp, faceDown),
      Vector(adviser), Vector(NegotiationSiteRelicState("site:broken-peaks",
        siteRelic)), canAccept = false)
    NegotiationDealState(Vector("red", "blue"), Vector.empty,
      Vector.empty, Vector.empty, Some(editing))

  test("disclosures are offered only for information the author can inspect"):
    val panel = render(owner(dealWithEveryDisclosureKindOffered()),
      new RecordingControls())
    val labels = panel.querySelectorAll("input[type=checkbox]").toVector
      .map(_.getAttribute("aria-label")).filter(_.startsWith("Promise"))
    assertEquals(labels, Vector(
      "Promise adviser disclosure of Hidden Adviser to blue",
      "Promise held-relic disclosure of Secret Relic to blue",
      "Promise site-relic disclosure of Bone Dice to blue"))

  test("a site-relic disclosure is submitted with its site and card id"):
    val ui = new RecordingControls()
    val panel = render(owner(dealWithEveryDisclosureKindOffered()), ui)
    box(panel, "Promise site-relic disclosure of Bone Dice to blue")
      .checked = true
    button(panel, ".negotiation-save").click()
    // Save always sends the favor box, here left at 0, as a transfer to each
    // other player: blue is the only one.
    assertEquals(ui.submitted, Vector[Intent](Intent.ResolveWalker(
      "negotiation.deal", DecisionAnswerWire.ProposeTermsWire(NegotiationTerms(
        Vector(NegotiationTransfer("blue", 0, Vector.empty)),
        Vector(oathdigital.protocol.NegotiationDisclosure("blue",
          oathdigital.protocol.NegotiationInformation.SiteRelic(
            "site:broken-peaks", "R3"))))))))

  private val secretRelic = CardDetails("r2", "relic", "Secret Relic",
    orientation = Some("face-down"))

  /** Red looks at blue's one transfer to red and one disclosure to red. */
  private def blueOffersRed(favor: Int, relicCount: Int,
      relics: Vector[CardDetails],
      disclosure: Option[CardDetails] = None) =
    NegotiationDealState(Vector("red", "blue"), Vector.empty,
      Vector(NegotiationTransferState("blue", "red", favor, relicCount,
        relics)),
      disclosure.toVector.map(card => NegotiationDisclosureState("blue", "red",
        "held-relic", Some(card))), None)

  private def transferLine(panel: dom.Element): String =
    Dom.one(panel, ".negotiation-transfer").textContent

  /** The names of the cards the overlay is asked to open while `act` runs. */
  private def cardsOpenedBy(act: => Unit): Vector[String] =
    var opened = Vector.empty[String]
    CardInspection.onOpen:
      case CardInspection.Request.Card(card, _) => opened = opened :+ card.name
      case _ => ()
    try act finally CardInspection.clear()
    opened

  test("a transfer names the relics the viewer can see and counts the rest"):
    val panel = render(spectator(blueOffersRed(favor = 1, relicCount = 3,
      relics = Vector(relic))), new RecordingControls(), playerId = "green")
    assertEquals(transferLine(panel),
      "blue gives red: 1 favor, Relic One, 2 facedown relics")

  test("a transfer of one hidden relic says so in the singular"):
    val panel = render(spectator(blueOffersRed(favor = 0, relicCount = 1,
      relics = Vector.empty)), new RecordingControls(), playerId = "green")
    assertEquals(transferLine(panel), "blue gives red: 1 facedown relic")

  test("a transfer of nothing says nothing rather than zeroes"):
    val panel = render(spectator(blueOffersRed(favor = 0, relicCount = 0,
      relics = Vector.empty)), new RecordingControls(), playerId = "green")
    assertEquals(transferLine(panel), "blue gives red: nothing")

  test("a disclosure names its card when the viewer is allowed to see it"):
    val panel = render(spectator(blueOffersRed(favor = 0, relicCount = 0,
      relics = Vector.empty, disclosure = Some(secretRelic))),
      new RecordingControls(), playerId = "blue")
    assertEquals(Dom.one(panel, ".negotiation-disclosure").textContent,
      "blue promises to show red Secret Relic")

  test("a relic named in the summary opens its card, and so does a disclosure's"):
    val panel = render(spectator(blueOffersRed(favor = 0, relicCount = 1,
      relics = Vector(relic), disclosure = Some(secretRelic))),
      new RecordingControls(), playerId = "blue")
    val opened = cardsOpenedBy:
      Dom.all(panel, "button.card-link").foreach(as[dom.html.Button](_).click())
    assertEquals(opened, Vector("Relic One", "Secret Relic"))

  test("an editor row's card name opens the card and leaves the offer unchanged"):
    val panel = render(owner(dealWhereBlueHasAccepted()),
      new RecordingControls())
    val offer = box(panel, "Offer Relic One to blue")
    val opened = cardsOpenedBy:
      button(panel, ".negotiation-item button.card-link").click()
    assertEquals(opened, Vector("Relic One"))
    assert(!offer.checked)

  test("a disclosure row in the editor opens the card it offers to show"):
    val panel = render(owner(dealWithEveryDisclosureKindOffered()),
      new RecordingControls())
    val row = Dom.all(panel, ".negotiation-item").find(
      _.textContent.trim == "Show Bone Dice").get
    val opened = cardsOpenedBy:
      button(row, "button.card-link").click()
    assertEquals(opened, Vector("Bone Dice"))
