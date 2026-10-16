package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.{SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, MenuPlay, ParkedNode, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Forest Council and City Wall in the modifier picker (catalog batch 8,
  * slice 10, part 2): Mushrooms' secret is paid onto it, so a ban refuses
  * both its use and its payment, and one ask covers both. */
class PlacementBanModifierSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val mushrooms = CatalogNames.denizen("Mushrooms")
  private val home = CatalogNames.site("Ancient City")
  private val mushroomsRef = DecisionOptionRef.Denizen(mushrooms)
  private val decisionId =
    ModifierPicks.decisionId(PowerWindow.SearchModifierSelection)

  /** p1 at Ancient City beside Mushrooms, with 5 Supply, a faceup secret
    * and a card in the Cradle discard pile for Mushrooms to draw, laid out
    * further by `steps`. */
  private def besideMushrooms(steps: Table => Table): ReadyGame =
    steps(Table.start.pawn(p1, home).denizen(mushrooms, at = home)
      .supply(p1, 5)
      .discarded(Region.Cradle, CatalogNames.denizen("Wrestlers"))).ready

  /** City Wall at Ancient City, ruled by p2. */
  private val insideCityWall: Table => Table =
    _.denizen(CatalogNames.denizen("City Wall"), at = home)
      .warbandsAt(home, p2, 1)

  /** Forest Council at Deep Woods, ruled by p2. */
  private val underForestCouncil: Table => Table = table =>
    val woods = CatalogNames.site("Deep Woods")
    table.denizen(CatalogNames.denizen("Forest Council"), at = woods)
      .warbandsAt(woods, p2, 1)

  /** The modifier decision's Mushrooms option, once p1 starts a Search. */
  private def mushroomsPick(ready: ReadyGame)(using munit.Location)
      : DecisionOption =
    val begun = accepted(MenuPlay.started(rules, Ready(ready), p1,
      ActionRef.Search))
    ParkedNode.of(begun.state, catalog, WalkerPowerCatalog.default(catalog))
      .toOption.flatten.collect {
        case ParkedNode.Decision(_, decide, _)
            if decide.decisionId == decisionId => decide.query
      }.collect { case one: DecisionQuery.ChooseOne => one.options }
      .flatMap(_.find(_.ref == mushroomsRef))
      .getOrElse(fail("the modifier decision does not list Mushrooms"))

  test("inside City Wall, Mushrooms is askable of its ruler"):
    assert(mushroomsPick(besideMushrooms(insideCityWall)).askable)

  test("under Forest Council, Mushrooms, a Beast card, is askable too"):
    assert(mushroomsPick(besideMushrooms(underForestCouncil)).askable)

  test("one grant lets p1 pick it and pay onto it"):
    val ready = besideMushrooms(insideCityWall)
    val granted = ready.copy(consent = ConsentState(answers = Map(
      ConsentKey(CityWall.id, p1, "power.use", mushroomsRef) ->
        ConsentAnswer.Granted.by(p2)), turn = Some(ConsentTurn.of(ready))))
    assert(mushroomsPick(granted).available)
