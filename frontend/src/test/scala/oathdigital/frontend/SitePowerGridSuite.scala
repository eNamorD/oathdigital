package oathdigital.frontend

import oathdigital.frontend.Dom.all
import oathdigital.frontend.TestSites.quietSite
import oathdigital.model.PlayerColor
import oathdigital.protocol.{GameIntent => Intent}
import org.scalajs.dom

/** A site power's label opens the power, and its ruler's preferences grid
  * (consent design, user story step 7, rulings 16 and 18). Red rules the
  * Pass. */
class SitePowerGridSuite extends munit.FunSuite:
  private val red = GamePlayer("red", "Red", "Exile", PlayerColor.Red)
  private val blue = GamePlayer("blue", "Blue", "Exile", PlayerColor.Blue)
  private val passText = "If your pawn is outside this region, you cannot " +
    "travel to other sites in this region."
  private val pass = SitePower("pass", "Pass", Some(passText),
    "site.narrow-pass.pass")
  private val narrowPass = quietSite("site:narrow-pass", "Narrow Pass")
    .copy(powers = Vector(pass))
  private val table = TestProjections.actingProjection(
    players = Vector(red, blue),
    world = Vector(GameRegion("provinces", Vector(narrowPass))))
  private val ruling = table.copy(consentPreferences = Vector(
    ConsentPreferencesState("site.narrow-pass.pass", "Narrow Pass",
      Vector(ConsentPreferenceRowState("blue", "case-by-case")))))
  private var opened = Vector.empty[CardInspection.Request]

  override def beforeEach(context: BeforeEach): Unit =
    opened = Vector.empty
    CardInspection.onOpen(request => opened :+= request)

  override def afterEach(context: AfterEach): Unit = CardInspection.clear()

  private def power(using munit.Location): CardInspection.Request.Power =
    opened match
      case Vector(request: CardInspection.Request.Power) => request
      case other => fail(s"expected one power request, got $other")

  private def label(world: dom.Element): dom.html.Button =
    world.querySelector(".site-power").asInstanceOf[dom.html.Button]

  test("a site power's label is a button that opens its text, with no " +
      "grid for a player who does not rule it"):
    val world = WorldBoardRenderer.world(table, BoardOptions.none)
    assertEquals(label(world).textContent, "Pass")
    assertEquals(label(world).getAttribute("title"), s"Pass: $passText")
    label(world).click()
    assertEquals((power.title, power.lines, power.grid),
      ("Pass", Vector(passText), None))

  test("its ruler's grid lists the other players, and a choice sends the " +
      "preference"):
    var sent = Vector.empty[Intent]
    val world = WorldBoardRenderer.world(ruling, BoardOptions.none,
      Some(command => sent :+= command))
    label(world).click()
    val grid = present(power.grid)
    assertEquals(grid.rows, Vector(ConsentGridRow("blue", "Blue",
      "case-by-case")))
    grid.choose.foreach(_("blue", "always"))
    assertEquals(sent, Vector(Intent.SetConsentPreference(
      "site.narrow-pass.pass", "blue", "always")))

  test("without control the ruler's grid shows the settings and changes " +
      "none"):
    label(WorldBoardRenderer.world(ruling, BoardOptions.none)).click()
    val grid = present(power.grid)
    assertEquals(grid.choose, None)
    val drawn = ConsentGridView.render(grid)
    assert(all(drawn, ".consent-grid-choice")
      .forall(_.asInstanceOf[dom.html.Button].disabled))

  test("opening the power does not pick the site it stands on"):
    var picked = Vector.empty[DecisionOptionState]
    val site = DecisionOptionState("site", "site:narrow-pass", "Narrow Pass")
    val options = new BoardOptions(Map(BoardOptionViews.key(site) ->
      OptionView(site, OptionState.Available, None)), picked :+= _)
    label(WorldBoardRenderer.world(table, options)).click()
    assertEquals(picked, Vector.empty)
    assertEquals(opened.size, 1)

  test("the overlay draws the grid; a choice moves the pressed button and " +
      "is sent once"):
    val root = dom.document.createElement("div")
    dom.document.body.appendChild(root)
    val overlay = new CardInspectionOverlay(root)
    val opener = dom.document.createElement("button")
      .asInstanceOf[dom.html.Element]
    dom.document.body.appendChild(opener)
    var chosen = Vector.empty[(String, String)]
    overlay.showPower("Pass", Vector(passText), Some(ConsentGrid(
      Vector(ConsentGridRow("blue", "Blue", "case-by-case")),
      Some((player, setting) => chosen :+= (player -> setting)))), opener)
    assertEquals(root.querySelector(".consent-grid-heading").textContent,
      "Consent preferences")
    val choices = all(root, ".consent-grid-choice")
      .map(_.asInstanceOf[dom.html.Button])
    assertEquals(choices.map(_.textContent),
      Vector("Never Allow", "Case-by-case", "Always Allow"))
    assertEquals(choices.map(_.getAttribute("aria-pressed")),
      Vector("false", "true", "false"))
    choices(2).click()
    choices(2).click()
    assertEquals(choices.map(_.getAttribute("aria-pressed")),
      Vector("false", "false", "true"))
    assertEquals(chosen, Vector("blue" -> "always"))
    overlay.dispose()
    root.remove()
    opener.remove()

  private def present[A](value: Option[A])(using munit.Location): A =
    value.getOrElse(fail("expected a grid"))

  private val policeCard = CardDetails("113", "denizen", "Secret Police")
  private val policed = table.copy(world = Vector(GameRegion("provinces",
    Vector(narrowPass.copy(denizens = Vector(GameSiteCard("113",
      "Secret Police", Some(policeCard))))))))

  private def face(world: dom.Element): dom.html.Element =
    world.querySelector("[data-denizen-id='113']")
      .asInstanceOf[dom.html.Element]

  test("a site card whose power the viewer rules opens with their grid"):
    var sent = Vector.empty[Intent]
    val ruled = policed.copy(consentPreferences = Vector(
      ConsentPreferencesState("denizen.secret-police", "Secret Police",
        Vector(ConsentPreferenceRowState("blue", "case-by-case")),
        Some("113"))))
    face(WorldBoardRenderer.world(ruled, BoardOptions.none,
      Some(command => sent :+= command))).click()
    opened match
      case Vector(CardInspection.Request.RuledCard(card, grid, _)) =>
        assertEquals(card.cardId, "113")
        assertEquals(grid.rows, Vector(ConsentGridRow("blue", "Blue",
          "case-by-case")))
        grid.choose.foreach(_("blue", "never"))
      case other => fail(s"expected one ruled card, got $other")
    assertEquals(sent, Vector(Intent.SetConsentPreference(
      "denizen.secret-police", "blue", "never")))

  test("a site card whose power the viewer does not rule opens as a card"):
    face(WorldBoardRenderer.world(policed, BoardOptions.none)).click()
    assert(opened match
      case Vector(_: CardInspection.Request.Card) => true
      case _ => false)
